package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.security.AccessControl;

import jakarta.persistence.*;

import java.time.*;
import java.util.*;

/** T10 integration contract: callers use their current transaction and lock the vehicle. */
public final class VehicleRules {
    private VehicleRules() {}

    public static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
    }

    public static boolean inspection(Xe vehicle, LocalDate start, LocalDate end) {
        return vehicle.ngayDangKiem != null
                && vehicle.hanDangKiem != null
                && !vehicle.ngayDangKiem.isAfter(start)
                && !vehicle.hanDangKiem.isBefore(end);
    }

    public static boolean layoutValid(Xe vehicle) {
        if (vehicle.loaiXe == null
                || vehicle.soChoNgoi <= 0
                || vehicle.soChoNgoi != vehicle.loaiXe.soChoNgoi
                || vehicle.danhSachGhe.size() != vehicle.soChoNgoi) {
            return false;
        }
        Set<String> codes = new HashSet<>();
        for (Ghe seat : vehicle.danhSachGhe) {
            if (seat.viTri == null
                    || seat.viTri.isBlank()
                    || seat.tang < 1
                    || seat.tang > vehicle.loaiXe.soTang
                    || seat.trangThai == null
                    || !codes.add(seat.viTri.strip().toUpperCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    public static Xe ready(EntityManager em, String vehicleId, ChuyenXe trip) {
        Xe vehicle = em.find(Xe.class, vehicleId, LockModeType.PESSIMISTIC_WRITE);
        if (vehicle == null) {
            throw new DomainException(404, "Không tìm thấy xe.");
        }
        em.refresh(vehicle, LockModeType.PESSIMISTIC_WRITE);
        vehicle = (Xe) org.hibernate.Hibernate.unproxy(vehicle);
        if (vehicle.trangThai != TrangThaiXe.SAN_SANG) {
            throw new DomainException(409, "Xe không sẵn sàng hoặc đang bảo trì.");
        }
        if (!inspection(vehicle, trip.ngayKhoiHanh, trip.gioDenDuKien.toLocalDate())) {
            throw new DomainException(409, "Đăng kiểm của xe không bao phủ toàn bộ chuyến.");
        }
        if (!layoutValid(vehicle)) {
            throw new DomainException(409, "Sơ đồ ghế chưa hợp lệ hoặc không khớp sức chứa xe.");
        }
        if (vehicle.nhaXe == null || vehicle.nhaXe.trangThai != TrangThaiNhaXe.HOAT_DONG) {
            throw new DomainException(409, "Nhà xe của phương tiện chưa hoạt động.");
        }
        if (trip.tuyenXe.nhaXe != null
                && !trip.tuyenXe.nhaXe.maNhaXe.equals(vehicle.nhaXe.maNhaXe)) {
            throw new DomainException(409, "Xe không thuộc nhà xe của tuyến.");
        }
        return vehicle;
    }

    public static boolean hasTicketsOrHolds(EntityManager em, ChuyenXe trip) {
        return em.createQuery("select count(v) from VeXe v where v.chuyenXe = :trip", Long.class)
                                .setParameter("trip", trip)
                                .getSingleResult()
                        > 0
                || em.createQuery(
                                        "select count(h) from GiuCho h where h.chuyenXe = :trip",
                                        Long.class)
                                .setParameter("trip", trip)
                                .getSingleResult()
                        > 0;
    }

    public static String availability(
            EntityManager em, ChuyenXe trip, Ghe seat, LocalDateTime now) {
        if (seat.trangThai != TrangThaiGhe.HOAT_DONG) {
            return "KHONG_BAN";
        }
        if (em.createQuery(
                                "select count(v) from VeXe v where v.chuyenXe = :trip and v.ghe ="
                                        + " :seat and v.trangThai in (:issued, :boarded)",
                                Long.class)
                        .setParameter("trip", trip)
                        .setParameter("seat", seat)
                        .setParameter("issued", TrangThaiVe.DA_XUAT_VE)
                        .setParameter("boarded", TrangThaiVe.DA_LEN_XE)
                        .getSingleResult()
                > 0) {
            return "DA_BAN";
        }
        if (em.createQuery(
                                "select count(h) from GiuCho h where h.chuyenXe = :trip and h.ghe ="
                                        + " :seat and h.trangThai = :holding and h.thoiDiemHetHan >"
                                        + " :now",
                                Long.class)
                        .setParameter("trip", trip)
                        .setParameter("seat", seat)
                        .setParameter("holding", TrangThaiGiuCho.DANG_GIU)
                        .setParameter("now", now)
                        .getSingleResult()
                > 0) {
            return "DANG_GIU";
        }
        return "TRONG";
    }

    /** Booking modules must call this before persisting holds/tickets in this transaction. */
    public static Ghe seatSellable(
            EntityManager em, String tripId, String seatId, LocalDateTime now) {
        AccessControl.saleAllowed(em, tripId);
        ChuyenXe trip = AccessControl.resource(em, ChuyenXe.class, tripId);
        Ghe seat = AccessControl.resource(em, Ghe.class, seatId);
        if (!seat.xe.maXe.equals(trip.xe.maXe)) {
            throw new DomainException(409, "Ghế không thuộc xe của chuyến.");
        }
        if (!availability(em, trip, seat, now).equals("TRONG")) {
            throw new DomainException(409, "Ghế đang bảo trì, đã bán hoặc đang được giữ.");
        }
        return seat;
    }
}
