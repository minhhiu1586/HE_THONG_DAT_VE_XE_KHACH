package com.datvexe.email;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;

/** Durable, retryable customer notices for trips cancelled by the operator. */
public final class TripCancellationEmailDispatcher {
    private final EntityManagerFactory factory;private final EmailDispatcher.Sender sender;private final Clock clock;
    public TripCancellationEmailDispatcher(EntityManagerFactory factory,EmailDispatcher.Sender sender,Clock clock){this.factory=factory;this.sender=sender;this.clock=clock;}
    private Instant now(){return clock.instant();}
    public void runOnce(){List<Object[]> ids;
        try(var em=factory.createEntityManager()){var tx=em.getTransaction();tx.begin();
            em.createNativeQuery("update trip_cancellation_notification set status='UNKNOWN',last_error='WORKER_INTERRUPTED' where status='SENDING' and started_at<:stale")
                    .setParameter("stale",now().minus(Duration.ofMinutes(2))).executeUpdate();tx.commit();
            ids=em.createNativeQuery("select ma_don,ma_chuyen from trip_cancellation_notification where status='PENDING' and next_attempt_at<=:now order by created_at")
                .setParameter("now",now()).setMaxResults(5).getResultList();}
        for(Object[] key:ids)dispatch(key[0].toString(),key[1].toString());
    }
    private void dispatch(String order,String tripId){String trip,recipient,name,reason,operator,departure,tickets;int attempt;
        try(var em=factory.createEntityManager()){var tx=em.getTransaction();tx.begin();
            Object[] row=(Object[])em.createNativeQuery("select n.ma_chuyen,n.recipient,n.recipient_name,w.ly_do,n.attempts from trip_cancellation_notification n left join trip_cancellation_work w on w.ma_don=n.ma_don and w.ma_chuyen=n.ma_chuyen where n.ma_don=:order and n.ma_chuyen=:trip for update of n")
                    .setParameter("order",order).setParameter("trip",tripId).getSingleResult();
            if(!"PENDING".equals(em.createNativeQuery("select status from trip_cancellation_notification where ma_don=:order and ma_chuyen=:trip").setParameter("order",order).setParameter("trip",tripId).getSingleResult())){tx.commit();return;}
            trip=row[0].toString();recipient=row[1].toString();name=row[2].toString();reason=Objects.toString(row[3],"Đơn vị vận hành đã hủy chuyến.");attempt=((Number)row[4]).intValue()+1;
            var journey=em.find(com.datvexe.domain.Entities.ChuyenXe.class,trip);
            operator=journey.tuyenXe.nhaXe==null?"Đơn vị vận hành":journey.tuyenXe.nhaXe.tenNhaXe;
            departure=journey.ngayKhoiHanh+" "+journey.gioKhoiHanh+" (giờ Việt Nam)";
            tickets=String.join(", ",em.createQuery("select v.maVe from VeXe v where v.donDatVe.maDon=:order and v.chuyenXe.maChuyen=:trip",String.class)
                    .setParameter("order",order).setParameter("trip",tripId).getResultList());
            em.createNativeQuery("update trip_cancellation_notification set status='SENDING',attempts=:attempt,started_at=:now where ma_don=:order and ma_chuyen=:trip").setParameter("attempt",attempt).setParameter("now",now()).setParameter("order",order).setParameter("trip",tripId).executeUpdate();tx.commit();
        }
        String status="SENT",error=null,messageId=null;
        String text="Xin chào "+name+", chuyến "+trip+" trong đơn "+order+" của "+operator+", khởi hành "+departure+" đã bị hủy. Vé bị ảnh hưởng: "+tickets+". Lý do: "+reason+". Trạng thái xử lý vé/hoàn tiền được cập nhật trong lịch sử đặt vé; email này không xác nhận hoàn tiền thành công.";
        String html="<p>"+escape(text)+"</p><p>Đăng nhập và mở Tài khoản → Lịch sử đặt vé để tra cứu đơn.</p>";
        try{messageId=sender.send(new BrevoClient.Message("CANCEL-"+order+"-"+trip,recipient,name,"Thông báo chuyến xe đã hủy · "+trip,html,text));}
        catch(BrevoClient.SendFailure failure){error=failure.code;status=failure.uncertain?"UNKNOWN":failure.retry&&attempt<5?"PENDING":"FAILED";}
        catch(RuntimeException failure){error="EMAIL_DELIVERY_UNKNOWN";status="UNKNOWN";}
        try(var em=factory.createEntityManager()){var tx=em.getTransaction();tx.begin();
            em.createNativeQuery("update trip_cancellation_notification set status=:status,last_error=:error,provider_message_id=:messageId,sent_at=:sent,next_attempt_at=:next where ma_don=:order and ma_chuyen=:trip and status='SENDING'")
                    .setParameter("status",status).setParameter("error",error).setParameter("sent",status.equals("SENT")?now():null)
                    .setParameter("messageId",messageId).setParameter("next",now().plusSeconds(20L*(1L<<Math.min(attempt,4)))).setParameter("order",order).setParameter("trip",tripId).executeUpdate();tx.commit();}
    }
    private static String escape(String value){return value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
}
