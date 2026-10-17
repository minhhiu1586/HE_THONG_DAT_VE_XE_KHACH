package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.ReviewInputs;
import com.datvexe.repository.ReviewRepository;
import com.datvexe.security.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/** T22: serialize changes with the trip; calculate rating directly from approved rows. */
public final class ReviewService {
    private final EntityManagerFactory factory;
    private final Clock clock;
    public ReviewService(EntityManagerFactory factory){this(factory,Clock.systemUTC());}
    public ReviewService(EntityManagerFactory factory,Clock clock){this.factory=factory;this.clock=clock;}
    private <T>T tx(Function<EntityManager,T> action){
        try(var em=factory.createEntityManager()){
            var t=em.getTransaction();t.begin();
            try{AccessPolicy.guard(em);T result=action.apply(em);t.commit();return result;}
            catch(RuntimeException e){if(t.isActive())t.rollback();throw e;}
        }
    }
    private NguoiDung caller(EntityManager em){
        var request=RequestIdentity.get();
        return AccessControl.authenticated(em,request==null?null:request.user(),clock.instant());
    }
    private static AccountException error(int status,String code,String message){return new AccountException(status,code,message);}
    private static String code(String id){
        if(id==null||!id.matches("[A-Za-z0-9_-]{1,40}"))throw error(400,"INVALID_REVIEW_ID","Mã tài nguyên không hợp lệ.");
        return id;
    }
    private static void validText(String text){
        if(text==null)return;
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(Character.isHighSurrogate(c)){
                if(i+1<text.length()&&Character.isLowSurrogate(text.charAt(i+1))){i++;continue;}
                throw error(400,"INVALID_REVIEW_CONTENT","Nội dung chứa ký tự không hợp lệ.");
            }
            if(Character.isLowSurrogate(c)||(Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))
                throw error(400,"INVALID_REVIEW_CONTENT","Nội dung chứa ký tự không hợp lệ.");
        }
    }
    private static String validate(Integer stars,String text){
        if(stars==null||stars<1||stars>5)throw error(400,"INVALID_REVIEW_STARS","Điểm phải là số nguyên từ 1 đến 5.");
        if(text!=null&&text.length()>2000)throw error(400,"INVALID_REVIEW_CONTENT","Nhận xét không được vượt quá 2000 ký tự.");
        validText(text);
        return text==null?"":text.strip();
    }
    private static void eligible(EntityManager em,NguoiDung user,ChuyenXe trip){
        if(trip.trangThai!=TrangThaiChuyen.HOAN_TAT)throw error(409,"TRIP_NOT_COMPLETED","Chỉ đánh giá chuyến đã hoàn tất.");
        if(!new ReviewRepository(em).hasEligibleTicket(user.maNguoiDung,trip.maChuyen))
            throw error(403,"REVIEW_NOT_ELIGIBLE","Bạn chưa có vé đã thanh toán còn hợp lệ trên chuyến này.");
    }
    public Map<String,Object> eligibility(String tripId){return tx(em->{
        var user=caller(em);AccessControl.role(user,LoaiTaiKhoan.KHACH_HANG);
        var trip=AccessControl.resource(em,ChuyenXe.class,code(tripId));
        var existing=new ReviewRepository(em).forAccountTrip(user.maNguoiDung,trip.maChuyen);
        boolean allowed=true;String reason="";
        try{eligible(em,user,trip);}catch(AccountException e){allowed=false;reason=e.getMessage();}
        boolean canEdit=allowed&&existing!=null&&!existing.daXoa;
        if(existing!=null){allowed=false;reason=existing.daXoa?"Đánh giá đã xóa; không tạo lại đánh giá cho cùng chuyến.":"Bạn đã đánh giá chuyến này.";}
        Map<String,Object> result=new LinkedHashMap<>();result.put("coTheDanhGia",allowed);result.put("coTheSua",canEdit);result.put("lyDo",reason);
        result.put("danhGia",existing==null?null:view(existing,false));return result;
    });}
    public Map<String,Object> create(ReviewInputs.Create input){return tx(em->{
        var user=caller(em);AccessControl.role(user,LoaiTaiKhoan.KHACH_HANG);
        if(input==null)throw error(400,"INVALID_REVIEW_CONTENT","Thiếu nội dung đánh giá.");
        String text=validate(input.soSao(),input.binhLuan());
        var trip=AccessControl.resource(em,ChuyenXe.class,code(input.maChuyen()));eligible(em,user,trip);
        if(new ReviewRepository(em).forAccountTrip(user.maNguoiDung,trip.maChuyen)!=null)
            throw error(409,"REVIEW_ALREADY_EXISTS","Mỗi tài khoản chỉ được đánh giá một lần cho mỗi chuyến.");
        var review=new DanhGia();review.maDanhGia="DG-"+UUID.randomUUID();review.nguoiDung=user;review.chuyenXe=trip;
        review.soSao=input.soSao();review.binhLuan=text;review.ngayDanhGia=LocalDateTime.ofInstant(clock.instant(),TripSaleRules.ZONE);
        em.persist(review);em.flush();audit(em,review,user,"TAO",null,null,null,null);return view(review,false);
    });}
    private DanhGia lockedReview(EntityManager em,String id){
        var initial=em.find(DanhGia.class,code(id));
        if(initial==null)throw error(404,"REVIEW_NOT_FOUND","Không tìm thấy đánh giá.");
        AccessControl.resource(em,ChuyenXe.class,initial.chuyenXe.maChuyen);
        return AccessControl.resource(em,DanhGia.class,id);
    }
    private static void owner(NguoiDung user,DanhGia review){
        AccessControl.role(user,LoaiTaiKhoan.KHACH_HANG);
        if(!review.nguoiDung.maNguoiDung.equals(user.maNguoiDung))throw AccessControl.denied();
    }
    private static void moderator(EntityManager em,NguoiDung user,DanhGia review){
        AccessControl.role(user,LoaiTaiKhoan.ADMIN,LoaiTaiKhoan.NHA_XE);
        AccessControl.operator(user,review.chuyenXe.tuyenXe.nhaXe);AccessControl.activeOperator(em,user);
    }
    public Map<String,Object> edit(String id,ReviewInputs.Edit input){return tx(em->{
        var user=caller(em);var review=lockedReview(em,id);owner(user,review);
        if(review.daXoa)throw error(409,"REVIEW_DELETED","Đánh giá đã xóa không thể sửa.");
        if(input==null)throw error(400,"INVALID_REVIEW_CONTENT","Thiếu nội dung đánh giá.");
        String text=validate(input.soSao(),input.binhLuan());eligible(em,user,review.chuyenXe);
        if(review.soSao==input.soSao()&&Objects.equals(review.binhLuan,text))return view(review,false);
        var before=review.trangThai;int oldStars=review.soSao;String oldText=review.binhLuan;
        review.soSao=input.soSao();review.binhLuan=text;review.trangThai=TrangThaiDanhGia.CHO_DUYET;
        audit(em,review,user,"SUA",before,oldStars,oldText,null);return view(review,false);
    });}
    public Map<String,Object> moderate(String id,boolean approve,String reason){return tx(em->{
        var user=caller(em);var review=lockedReview(em,id);moderator(em,user,review);
        if(review.daXoa)throw error(409,"REVIEW_DELETED","Đánh giá đã xóa không thể kiểm duyệt.");
        if(!approve&&(reason==null||reason.isBlank()))throw error(400,"REVIEW_REASON_REQUIRED","Vui lòng nhập lý do ẩn đánh giá.");
        if(reason!=null&&reason.length()>1000)throw error(400,"INVALID_REVIEW_CONTENT","Lý do không được vượt quá 1000 ký tự.");
        validText(reason);
        var target=approve?TrangThaiDanhGia.DA_DUYET:TrangThaiDanhGia.AN;
        if(review.trangThai==target)return view(review,false);
        var before=review.trangThai;review.trangThai=target;
        audit(em,review,user,approve?"DUYET":"AN",before,review.soSao,review.binhLuan,reason);return view(review,false);
    });}
    public Map<String,Object> delete(String id){return tx(em->{
        var user=caller(em);var review=lockedReview(em,id);
        if(user.loaiTaiKhoan==LoaiTaiKhoan.KHACH_HANG)owner(user,review);else moderator(em,user,review);
        if(!review.daXoa){review.daXoa=true;audit(em,review,user,"XOA_MEM",review.trangThai,review.soSao,review.binhLuan,null);}
        return view(review,false);
    });}
    public Map<String,Object> publicReviews(String tripId,int page,int size){return tx(em->{
        pagination(page,size);var trip=AccessControl.resource(em,ChuyenXe.class,code(tripId));
        String where=" from DanhGia d where d.chuyenXe=:trip and d.trangThai=:approved and d.daXoa=false";
        Object[] summary=em.createQuery("select count(d),avg(d.soSao)"+where,Object[].class)
                .setParameter("trip",trip).setParameter("approved",TrangThaiDanhGia.DA_DUYET).getSingleResult();
        var rows=em.createQuery("select d"+where+" order by d.ngayDanhGia desc,d.maDanhGia",DanhGia.class)
                .setParameter("trip",trip).setParameter("approved",TrangThaiDanhGia.DA_DUYET).setFirstResult((page-1)*size).setMaxResults(size).getResultList();
        Map<String,Object> result=new LinkedHashMap<>(page(rows,page,size,((Number)summary[0]).longValue(),true));
        result.put("rating",summary[1]);return result;
    });}
    public Map<String,Object> management(String tripId,String state,int page,int size){return tx(em->{
        var user=caller(em);AccessControl.role(user,LoaiTaiKhoan.ADMIN,LoaiTaiKhoan.NHA_XE);
        AccessControl.activeOperator(em,user);pagination(page,size);
        String where=" from DanhGia d where 1=1";
        Map<String,Object> params=new LinkedHashMap<>();
        if(user.loaiTaiKhoan==LoaiTaiKhoan.NHA_XE){AccessControl.operator(user,user.nhaXe);where+=" and d.chuyenXe.tuyenXe.nhaXe=:owner";params.put("owner",user.nhaXe);}
        if(tripId!=null&&!tripId.isBlank()){where+=" and d.chuyenXe.maChuyen=:trip";params.put("trip",code(tripId));}
        if("DA_XOA".equals(state))where+=" and d.daXoa=true";
        else {where+=" and d.daXoa=false";if(state!=null&&!state.isBlank()){where+=" and d.trangThai=:state";params.put("state",TrangThaiDanhGia.valueOf(state));}}
        var count=em.createQuery("select count(d)"+where,Long.class);
        var query=em.createQuery("select d"+where+" order by d.ngayDanhGia desc,d.maDanhGia",DanhGia.class);
        params.forEach((k,v)->{count.setParameter(k,v);query.setParameter(k,v);});
        return page(query.setFirstResult((page-1)*size).setMaxResults(size).getResultList(),page,size,count.getSingleResult(),false);
    });}
    public List<Map<String,Object>> history(String id){return tx(em->{
        var user=caller(em);var review=lockedReview(em,id);moderator(em,user,review);
        List<Object[]> rows=em.createNativeQuery("select ma_nguoi_thao_tac,thao_tac,trang_thai_cu,trang_thai_moi,ly_do,created_at from review_history where ma_danh_gia=:id order by created_at,id")
                .setParameter("id",id).getResultList();
        return rows.stream().map(r->Map.<String,Object>of("maNguoiThaoTac",r[0],"thaoTac",r[1],"trangThaiCu",Objects.toString(r[2],""),"trangThaiMoi",r[3],"lyDo",Objects.toString(r[4],""),"thoiDiem",r[5].toString())).toList();
    });}
    private static void pagination(int page,int size){if(page<1||size<1||size>100||((long)page-1)*size>Integer.MAX_VALUE)throw error(400,"INVALID_PAGINATION","Phân trang không hợp lệ.");}
    private static Map<String,Object> page(List<DanhGia> rows,int page,int size,long total,boolean publicView){
        return Map.of("items",rows.stream().map(d->view(d,publicView)).toList(),"page",page,"totalItems",total,"totalPages",(total+size-1)/size);
    }
    private static Map<String,Object> view(DanhGia review,boolean publicView){
        Map<String,Object> result=new LinkedHashMap<>();result.put("maDanhGia",review.maDanhGia);result.put("maChuyen",review.chuyenXe.maChuyen);
        result.put("soSao",review.soSao);result.put("binhLuan",Objects.toString(review.binhLuan,""));result.put("ngayDanhGia",review.ngayDanhGia.toString());
        result.put("tenHienThi","Khách đã đặt vé");
        if(!publicView){result.put("maNguoiDung",review.nguoiDung.maNguoiDung);result.put("trangThai",review.daXoa?"DA_XOA":review.trangThai.name());}
        return result;
    }
    private void audit(EntityManager em,DanhGia review,NguoiDung actor,String action,TrangThaiDanhGia before,Integer stars,String text,String reason){
        em.flush();
        em.createNativeQuery("insert into review_history(id,ma_danh_gia,ma_nguoi_thao_tac,thao_tac,trang_thai_cu,trang_thai_moi,so_sao_cu,so_sao_moi,binh_luan_cu,binh_luan_moi,ly_do,created_at) values (:id,:review,:actor,:action,:before,:after,:oldStars,:stars,:oldText,:text,:reason,:now)")
                .setParameter("id",UUID.randomUUID().toString()).setParameter("review",review.maDanhGia).setParameter("actor",actor.maNguoiDung).setParameter("action",action)
                .setParameter("before",before==null?null:before.name()).setParameter("after",review.daXoa?"DA_XOA":review.trangThai.name())
                .setParameter("oldStars",stars).setParameter("stars",review.soSao).setParameter("oldText",text).setParameter("text",review.binhLuan)
                .setParameter("reason",reason).setParameter("now",clock.instant()).executeUpdate();
    }
}
