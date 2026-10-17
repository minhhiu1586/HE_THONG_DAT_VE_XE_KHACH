package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.TripPassengerDto.*;
import com.datvexe.repository.HoanVeRepository;
import com.datvexe.security.*;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;

/** Read-only passenger manifest, independent of ticket inspection. */
public final class TripPassengerService {
    private final EntityManagerFactory factory;
    public TripPassengerService(EntityManagerFactory factory) { this.factory=factory; }
    public PassengerListResponse list(String tripId) {
        if(tripId==null || tripId.isBlank()) throw new DomainException(400,"Mã chuyến là bắt buộc.");
        try(var em=factory.createEntityManager()) {
            var tx=em.getTransaction(); tx.begin();
            try {
                var identity=RequestIdentity.get();
                if(identity==null) throw new AccountException(401,"UNAUTHENTICATED","Vui lòng đăng nhập.");
                var user=AccessControl.authenticated(em,identity.user(),Instant.now());
                AccessControl.role(user,LoaiTaiKhoan.ADMIN,LoaiTaiKhoan.NHA_XE,LoaiTaiKhoan.NHAN_VIEN);
                if(user.loaiTaiKhoan==LoaiTaiKhoan.NHAN_VIEN) AccessControl.assigned(em,user,tripId);
                else AccessControl.trip(em,user,tripId);
                var trip=AccessControl.resource(em,ChuyenXe.class,tripId);
                var refunds=new HoanVeRepository(em);
                var tickets=em.createQuery("select v from VeXe v where v.chuyenXe.maChuyen=:trip order by v.ghe.maGhe",VeXe.class)
                    .setParameter("trip",tripId).getResultList();
                List<PassengerItem> passengers=tickets.stream().map(v->new PassengerItem(v.maVe,v.maTraCuu,v.maGhe,
                    v.hoTenKhachHang,v.soDienThoaiKhachHang,v.cccdHanhKhach,
                    v.trangThai==TrangThaiVe.DA_LEN_XE?TrangThaiVe.DA_XUAT_VE.name():v.trangThai.name(),
                    v.giaVe,refunds.ticketBlocked(v.maVe))).toList();
                int issued=(int)passengers.stream().filter(p->p.trangThai().equals("DA_XUAT_VE")).count();
                int refunded=(int)passengers.stream().filter(p->p.trangThai().equals("DA_HOAN")).count();
                int cancelled=(int)passengers.stream().filter(p->p.trangThai().equals("DA_HUY")).count();
                var result=new PassengerListResponse(trip.maChuyen,trip.tuyenXe.tenTuyen,trip.tuyenXe.nhaXe.tenNhaXe,
                    trip.xe==null?"Chưa gán xe":trip.xe.bienSo,trip.ngayKhoiHanh.toString(),trip.gioKhoiHanh.toString(),
                    passengers.size(),issued,refunded,cancelled,passengers);
                tx.commit();return result;
            } catch(RuntimeException e) { if(tx.isActive())tx.rollback();throw e; }
        }
    }
}
