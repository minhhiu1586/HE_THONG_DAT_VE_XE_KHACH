package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.TripPassengerDto.*;
import com.datvexe.repository.HoanVeRepository;
import com.datvexe.security.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Xem danh sách hành khách và vé; không cập nhật trạng thái vé. */
public final class TripPassengerService {
    private final EntityManagerFactory factory;

    public TripPassengerService(EntityManagerFactory factory) {
        this.factory = factory;
    }

    // Kiểm tra quyền xem chuyến và trả danh sách hành khách, thông tin vé cùng số lượng theo trạng thái.
    public PassengerListResponse list(String tripId) {
        if (tripId == null || tripId.isBlank()) {
            throw new DomainException(400, "Mã chuyến là bắt buộc.");
        }
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                RequestIdentity.Context identity = RequestIdentity.get();
                if (identity == null) {
                    throw new AccountException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
                }
                NguoiDung user = AccessControl.authenticated(em, identity.user(), Instant.now());
                AccessControl.role(user, LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE, LoaiTaiKhoan.NHAN_VIEN);
                if (user.loaiTaiKhoan == LoaiTaiKhoan.NHAN_VIEN) {
                    AccessControl.assigned(em, user, tripId);
                } else {
                    AccessControl.trip(em, user, tripId);
                }
                ChuyenXe trip = AccessControl.resource(em, ChuyenXe.class, tripId);
                HoanVeRepository refunds = new HoanVeRepository(em);
                List<VeXe> tickets = em.createQuery("select v from VeXe v where v.chuyenXe.maChuyen=:trip order" + " by v.ghe.maGhe",
                    VeXe.class)
                    .setParameter("trip", tripId)
                    .getResultList();
                List<PassengerItem> passengers = new ArrayList<>();
                int issued = 0;
                int refunded = 0;
                int cancelled = 0;
                for (VeXe ticket : tickets) {
                    String status = ticket.trangThai.name();
                    // Giữ cách hiển thị vé cũ sau khi module kiểm vé được bỏ khỏi hệ thống.
                    if (ticket.trangThai == TrangThaiVe.DA_LEN_XE) {
                        status = TrangThaiVe.DA_XUAT_VE.name();
                    }
                    PassengerItem passenger = new PassengerItem(ticket.maVe, ticket.maTraCuu, ticket.maGhe, ticket.hoTenKhachHang,
                        ticket.soDienThoaiKhachHang, ticket.cccdHanhKhach, status, ticket.giaVe, refunds.ticketBlocked(ticket.maVe));
                    passengers.add(passenger);
                    if (status.equals("DA_XUAT_VE")) {
                        issued++;
                    }
                    if (status.equals("DA_HOAN")) {
                        refunded++;
                    }
                    if (status.equals("DA_HUY")) {
                        cancelled++;
                    }
                }
                String licensePlate = "Chưa gán xe";
                if (trip.xe != null) {
                    licensePlate = trip.xe.bienSo;
                }
                PassengerListResponse result = new PassengerListResponse(trip.maChuyen, trip.tuyenXe.tenTuyen, trip.tuyenXe.nhaXe.tenNhaXe,
                    licensePlate, trip.ngayKhoiHanh.toString(), trip.gioKhoiHanh.toString(), passengers.size(), issued, refunded, cancelled,
                    passengers);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw e;
            }
        }
    }
}
