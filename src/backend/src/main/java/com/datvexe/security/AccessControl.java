package com.datvexe.security;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;

/** T07.1: all helpers use the caller's transaction and authoritative DB relationships. */
public final class AccessControl {
    private AccessControl() {}
    public static AccountException denied() { return new AccountException(403, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này."); }
    // KIỂM TRA TRONG TRANSACTION: đọc lại/lock user và phiên; phải hoạt động, còn hạn và thuộc đúng user.
    // Bổ sung cho kiểm tra Filter để không chỉ dựa vào danh tính được chụp trước đó.
    public static NguoiDung authenticated(EntityManager em, CurrentUser identity, Instant now) {
        if (identity == null) throw new AccountException(401, "UNAUTHENTICATED", "Vui lòng đăng nhập.");
        NguoiDung n = em.find(NguoiDung.class, identity.maNguoiDung(), LockModeType.PESSIMISTIC_WRITE);
        if (n != null) em.refresh(n, LockModeType.PESSIMISTIC_WRITE);
        AuthSession s = identity.sessionHash() == null ? null : em.find(AuthSession.class, identity.sessionHash(), LockModeType.PESSIMISTIC_WRITE);
        if (s != null) em.refresh(s, LockModeType.PESSIMISTIC_WRITE);
        if (n == null || n.trangThai != TrangThaiTaiKhoan.HOAT_DONG || s == null || !now.isBefore(s.hetHan)
                || !s.nguoiDung.maNguoiDung.equals(n.maNguoiDung))
            throw new AccountException(401, "UNAUTHENTICATED", "Phiên đăng nhập không còn hiệu lực. Vui lòng đăng nhập lại.");
        return n;
    }
    // Kiểm tra vai trò của entity vừa đọc từ DB; không nằm trong danh sách allowed thì ném 403.
    public static void role(NguoiDung n, LoaiTaiKhoan... allowed) {
        if (Arrays.stream(allowed).noneMatch(r -> r == n.loaiTaiKhoan)) throw denied();
    }
    public static void profileOwner(NguoiDung caller, String target) {
        if (!caller.maNguoiDung.equals(target)) throw denied();
    }
    // Giới hạn phạm vi nhà xe: NHA_XE chỉ thao tác dữ liệu nhà xe mình; ADMIN được qua kiểm tra chủ sở hữu này.
    public static void operator(NguoiDung caller, NhaXe owner) {
        role(caller, LoaiTaiKhoan.NHA_XE, LoaiTaiKhoan.ADMIN);
        if (caller.loaiTaiKhoan == LoaiTaiKhoan.ADMIN) return;
        if (owner == null || caller.nhaXe == null || !owner.maNhaXe.equals(caller.nhaXe.maNhaXe)) throw denied();
    }
    public static void activeOperator(NguoiDung caller) {
        if (caller.loaiTaiKhoan == LoaiTaiKhoan.NHA_XE &&
                (caller.nhaXe == null || caller.nhaXe.trangThai != TrangThaiNhaXe.HOAT_DONG)) throw denied();
    }
    public static void activeOperator(EntityManager em, NguoiDung caller) {
        if (caller.loaiTaiKhoan != LoaiTaiKhoan.NHA_XE) return;
        if (caller.nhaXe == null) throw denied();
        NhaXe owner = resource(em, NhaXe.class, caller.nhaXe.maNhaXe);
        if (owner.trangThai != TrangThaiNhaXe.HOAT_DONG) throw denied();
    }
    public static <T> T resource(EntityManager em, Class<T> type, String id) {
        if (id == null || id.isBlank()) throw AccountException.invalid("id", "Mã tài nguyên là bắt buộc.");
        T item = em.find(type, id, LockModeType.PESSIMISTIC_WRITE);
        if (item == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên.");
        em.refresh(item, LockModeType.PESSIMISTIC_WRITE);
        return type.cast(org.hibernate.Hibernate.unproxy(item));
    }
    public static void route(EntityManager em, NguoiDung caller, String id) { operator(caller, resource(em, TuyenXe.class, id).nhaXe); }
    public static void trip(EntityManager em, NguoiDung caller, String id) { operator(caller, resource(em, ChuyenXe.class, id).tuyenXe.nhaXe); }
    public static void vehicle(EntityManager em, NguoiDung caller, String id) { operator(caller, resource(em, Xe.class, id).nhaXe); }
    public static void employee(EntityManager em, NguoiDung caller, String id) { operator(caller, resource(em, NhanVien.class, id).nhaXe); }
    public static void saleAllowed(EntityManager em, String tripId) {
        ChuyenXe trip = resource(em, ChuyenXe.class, tripId);
        if (trip.tuyenXe.nhaXe == null)
            throw new AccountException(409, "OPERATOR_UNASSIGNED", "Chuyến chưa có nhà xe vận hành; không thể bán vé.");
        businessAllowed(em, trip.tuyenXe.nhaXe);
        com.datvexe.service.TripSaleRules.ensureSellable(em,trip,java.time.LocalDateTime.now(com.datvexe.service.TripSaleRules.ZONE));
        if(!java.time.LocalDateTime.now(com.datvexe.service.TripSaleRules.ZONE).isBefore(com.datvexe.service.TripSaleRules.cutoff(trip)))
            throw new AccountException(409,"INVALID_STATE_TRANSITION","Đã đến thời điểm ngừng nhận đặt vé.");
    }
    public static void businessAllowed(EntityManager em, NhaXe operator) {
        if (operator != null && resource(em, NhaXe.class, operator.maNhaXe).trangThai != TrangThaiNhaXe.HOAT_DONG)
            throw new AccountException(409, "OPERATOR_SUSPENDED", "Nhà xe chưa hoạt động; không thể bán vé mới.");
    }
    // Giới hạn theo chủ đơn: cùng role KHACH_HANG không có nghĩa được truy cập đơn của người khác.
    public static void orderOwner(EntityManager em, NguoiDung caller, String id) {
        role(caller, LoaiTaiKhoan.KHACH_HANG);
        DonDatVe order = resource(em, DonDatVe.class, id);
        if (!order.nguoiDat.maNguoiDung.equals(caller.maNguoiDung)) throw denied();
    }
    public static void ticketOwner(EntityManager em, NguoiDung caller, String id) {
        role(caller, LoaiTaiKhoan.KHACH_HANG);
        VeXe ticket = resource(em, VeXe.class, id);
        if (!ticket.donDatVe.nguoiDat.maNguoiDung.equals(caller.maNguoiDung)) throw denied();
    }
    /** Call under the normal trip/order/ticket locks before committing a boarding action. */
    public static void ticketNotRefunding(EntityManager em, String id) {
        if (new com.datvexe.repository.HoanVeRepository(em).ticketBlocked(id))
            throw new AccountException(409,"REFUND_IN_PROGRESS","Vé đang khóa hoàn tiền, không được kiểm vé/lên xe.");
    }
    public static void assigned(EntityManager em, NguoiDung caller, String tripId) {
        role(caller, LoaiTaiKhoan.NHAN_VIEN);
        if (caller.nhanVien == null) throw denied();
        NhanVien employee = resource(em, NhanVien.class, caller.nhanVien.maNhanVien);
        ChuyenXe trip = resource(em, ChuyenXe.class, tripId);
        if (employee.trangThai != TrangThaiNhanVien.DANG_LAM_VIEC || trip.tuyenXe.nhaXe == null
                || trip.tuyenXe.nhaXe.trangThai != TrangThaiNhaXe.HOAT_DONG
                || !systemInspector(employee) && (employee.nhaXe == null
                    || employee.nhaXe.trangThai != TrangThaiNhaXe.HOAT_DONG
                    || !employee.nhaXe.maNhaXe.equals(trip.tuyenXe.nhaXe.maNhaXe))) throw denied();
        if (systemInspector(employee)) return;
        var assignments = em.createQuery("select p from PhanCongChuyenXe p where p.nhanVien = :n and p.chuyenXe = :c", PhanCongChuyenXe.class)
            .setParameter("n", employee).setParameter("c", trip).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if (assignments.isEmpty()) throw denied();
        try {
            for (var assignment : assignments) {
                em.refresh(assignment, LockModeType.PESSIMISTIC_WRITE);
                if (!assignment.nhanVien.maNhanVien.equals(employee.maNhanVien)
                        || !assignment.chuyenXe.maChuyen.equals(trip.maChuyen)) throw denied();
            }
        }
        catch (EntityNotFoundException e) { throw denied(); }
    }
    public static boolean systemInspector(NhanVien employee) {
        return employee.loaiNhanVien == LoaiNhanVien.KIEM_VE && employee.nhaXe == null;
    }
    public static void ticketAccess(EntityManager em, NguoiDung caller, String id) {
        VeXe ticket = resource(em, VeXe.class, id);
        switch (caller.loaiTaiKhoan) {
            case KHACH_HANG -> ticketOwner(em, caller, id);
            case NHA_XE -> operator(caller, ticket.chuyenXe.tuyenXe.nhaXe);
            case NHAN_VIEN -> assigned(em, caller, ticket.chuyenXe.maChuyen);
            case ADMIN -> { }
        }
    }
    public static void orderAccess(EntityManager em, NguoiDung caller, String id) {
        DonDatVe order = resource(em, DonDatVe.class, id);
        switch (caller.loaiTaiKhoan) {
            case KHACH_HANG -> orderOwner(em, caller, id);
            case ADMIN -> { }
            case NHA_XE -> {
                if (order.danhSachVe.isEmpty() && order.danhSachGiuCho.isEmpty()) throw denied();
                order.danhSachVe.forEach(v -> operator(caller, v.chuyenXe.tuyenXe.nhaXe));
                order.danhSachGiuCho.forEach(h -> operator(caller, h.chuyenXe.tuyenXe.nhaXe));
            }
            default -> throw denied();
        }
    }
}
