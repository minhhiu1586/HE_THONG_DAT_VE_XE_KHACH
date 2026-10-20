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

    // Đăng kiểm phải bao phủ từ ngày khởi hành đến ngày đến, kể cả chuyến đi qua nửa đêm.
    public static boolean inspection(Xe vehicle, LocalDate start, LocalDate end) {
        if (vehicle.ngayDangKiem == null || vehicle.hanDangKiem == null) {
            return false;
        }
        boolean startsOnTime = !vehicle.ngayDangKiem.isAfter(start);
        boolean coversArrival = !vehicle.hanDangKiem.isBefore(end);
        return startsOnTime && coversArrival;
    }

    // Số ghế phải khớp sức chứa; từng vị trí phải duy nhất và thuộc tầng hợp lệ của loại xe.
    public static boolean layoutValid(Xe vehicle) {
        if (vehicle.loaiXe == null || vehicle.soChoNgoi <= 0 || vehicle.soChoNgoi != vehicle.loaiXe.soChoNgoi
            || vehicle.danhSachGhe.size() != vehicle.soChoNgoi) {
            return false;
        }
        Set<String> codes = new HashSet<>();
        for (Ghe seat : vehicle.danhSachGhe) {
            if (seat.viTri == null || seat.viTri.isBlank()) {
                return false;
            }
            if (seat.tang < 1 || seat.tang > vehicle.loaiXe.soTang || seat.trangThai == null) {
                return false;
            }
            String positionCode = seat.viTri.strip().toUpperCase(Locale.ROOT);
            if (!codes.add(positionCode)) {
                return false;
            }
        }
        return true;
    }

    // Khóa và kiểm tra xe về trạng thái, đăng kiểm, sơ đồ ghế và nhà xe trước khi phục vụ chuyến.
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
        if (trip.tuyenXe.nhaXe != null && !trip.tuyenXe.nhaXe.maNhaXe.equals(vehicle.nhaXe.maNhaXe)) {
            throw new DomainException(409, "Xe không thuộc nhà xe của tuyến.");
        }
        return vehicle;
    }

    // Tính cả vé/giữ chỗ đã kết thúc để bảo vệ lịch sử khi sửa hoặc xóa chuyến.
    public static boolean hasTicketsOrHolds(EntityManager em, ChuyenXe trip) {
        long ticketCount = em.createQuery("select count(v) from VeXe v where v.chuyenXe = :trip", Long.class)
            .setParameter("trip", trip)
            .getSingleResult();
        if (ticketCount > 0) {
            return true;
        }
        long holdCount = em.createQuery("select count(h) from GiuCho h where h.chuyenXe = :trip", Long.class)
            .setParameter("trip", trip)
            .getSingleResult();
        return holdCount > 0;
    }

    // Xét theo thứ tự ghế ngừng bán, vé hiệu lực, giữ chỗ còn hạn rồi mới kết luận còn trống.
    public static String availability(EntityManager em, ChuyenXe trip, Ghe seat, LocalDateTime now) {
        if (seat.trangThai != TrangThaiGhe.HOAT_DONG) {
            return "KHONG_BAN";
        }
        long soldTickets = em.createQuery("select count(v) from VeXe v where v.chuyenXe = :trip and v.ghe ="
            + " :seat and v.trangThai in (:issued, :boarded)", Long.class)
            .setParameter("trip", trip)
            .setParameter("seat", seat)
            .setParameter("issued", TrangThaiVe.DA_XUAT_VE)
            .setParameter("boarded", TrangThaiVe.DA_LEN_XE)
            .getSingleResult();
        if (soldTickets > 0) {
            return "DA_BAN";
        }
        long activeHolds = em.createQuery("select count(h) from GiuCho h where h.chuyenXe = :trip and h.ghe ="
            + " :seat and h.trangThai = :holding and h.thoiDiemHetHan >" + " :now", Long.class)
            .setParameter("trip", trip)
            .setParameter("seat", seat)
            .setParameter("holding", TrangThaiGiuCho.DANG_GIU)
            .setParameter("now", now)
            .getSingleResult();
        if (activeHolds > 0) {
            return "DANG_GIU";
        }
        return "TRONG";
    }

    // Kiểm tra quyền bán và tình trạng ghế trước khi cho phép giữ chỗ hoặc xuất vé trong giao dịch hiện tại.
    public static Ghe seatSellable(EntityManager em, String tripId, String seatId, LocalDateTime now) {
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
