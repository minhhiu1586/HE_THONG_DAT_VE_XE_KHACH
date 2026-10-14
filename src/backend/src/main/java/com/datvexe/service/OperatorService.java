package com.datvexe.service;

import com.datvexe.domain.Entities.NguoiDung;
import com.datvexe.domain.Entities.NhaXe;
import com.datvexe.domain.States.*;
import com.datvexe.dto.OperatorInputs;
import com.datvexe.security.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;

public final class OperatorService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final EntityManagerFactory factory;
    public OperatorService(EntityManagerFactory factory) { this.factory = factory; }

    public record OperatorView(String maNhaXe, String tenNhaXe, String hotline, String email,
                               String diaChi, String moTa, String logoUrl, LocalDate ngayThanhLap,
                               TrangThaiNhaXe trangThai) {}
    public record ManagerView(String maNguoiDung, String hoTen, String email, String soDienThoai,
                              TrangThaiTaiKhoan trangThai) {}
    public record Page<T>(List<T> items, int page, int size, long totalItems, long totalPages) {}
    public record Impact(long chuyenSapKhoiHanh, long chuyenDangChay, long giuChoDangGiu,
                         long veDaPhatHanh, long veCoThanhToanThanhCong) {}
    public record StateChange(OperatorView nhaXe, Impact anhHuong) {}

    private <T> T tx(CurrentUser identity, Function<EntityManager,T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction(); transaction.begin();
            try {
                AccessControl.role(AccessControl.authenticated(em, identity, Instant.now()), LoaiTaiKhoan.ADMIN);
                T result = action.apply(em); transaction.commit(); return result;
            } catch (RuntimeException error) {
                if (transaction.isActive()) transaction.rollback(); throw error;
            }
        }
    }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw AccountException.invalid(field, "Trường bắt buộc, tối đa " + max + " ký tự.");
        return value.strip();
    }
    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.strip().length() > max) throw AccountException.invalid(field, "Tối đa " + max + " ký tự.");
        return value.strip();
    }
    private static String id(String value) { return required(value, "maNhaXe", 40); }
    private static NhaXe found(EntityManager em, String value, boolean lock) {
        NhaXe operator = em.find(NhaXe.class, id(value), lock ? LockModeType.PESSIMISTIC_WRITE : LockModeType.NONE);
        if (operator == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy nhà xe.");
        return operator;
    }
    private static OperatorView view(NhaXe x) {
        return new OperatorView(x.maNhaXe, x.tenNhaXe, x.hotline, x.email, x.diaChi,
            x.moTa, x.logoUrl, x.ngayThanhLap, x.trangThai);
    }
    private static ManagerView view(NguoiDung x) {
        return new ManagerView(x.maNguoiDung, x.hoTen, x.email, x.soDienThoai, x.trangThai);
    }
    private static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw AccountException.invalid("page", "Phân trang không hợp lệ.");
    }
    public Page<OperatorView> list(CurrentUser user, String search, TrangThaiNhaXe state, int page, int size) {
        page(page, size);
        return tx(user, em -> {
            String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
            if (term.length() > 160) throw AccountException.invalid("q", "Từ khóa quá dài.");
            String where = " where 1 = 1" + (state == null ? "" : " and x.trangThai = :state")
                + (term.isEmpty() ? "" : " and (lower(x.maNhaXe) like :pattern or lower(x.tenNhaXe) like :pattern)");
            TypedQuery<Long> countQuery = em.createQuery("select count(x) from NhaXe x" + where, Long.class);
            if (state != null) countQuery.setParameter("state", state);
            if (!term.isEmpty()) countQuery.setParameter("pattern", "%" + term + "%");
            long count = countQuery.getSingleResult();
            long offset = ((long) page - 1) * size;
            List<OperatorView> items;
            if (offset >= count) items = List.of();
            else {
                TypedQuery<NhaXe> itemQuery = em.createQuery(
                    "select x from NhaXe x" + where + " order by x.maNhaXe", NhaXe.class);
                if (state != null) itemQuery.setParameter("state", state);
                if (!term.isEmpty()) itemQuery.setParameter("pattern", "%" + term + "%");
                items = itemQuery.setFirstResult(Math.toIntExact(offset)).setMaxResults(size).getResultList()
                    .stream().map(OperatorService::view).toList();
            }
            return new Page<>(items, page, size, count, (count + size - 1) / size);
        });
    }
    public OperatorView get(CurrentUser user, String operatorId) {
        return tx(user, em -> view(found(em, operatorId, false)));
    }
    private static long count(EntityManager em, String query, NhaXe operator, String parameter, Object value) {
        TypedQuery<Long> statement = em.createQuery(query, Long.class).setParameter("operator", operator);
        if (parameter != null) statement.setParameter(parameter, value);
        return statement.getSingleResult();
    }
    private static Impact impact(EntityManager em, NhaXe operator) {
        long upcoming = em.createQuery("select count(c) from ChuyenXe c where c.tuyenXe.nhaXe = :operator "
                + "and c.trangThai in (:draft,:legacy,:sale) and c.ngayKhoiHanh >= :today", Long.class)
            .setParameter("operator", operator).setParameter("draft", TrangThaiChuyen.NHAP)
            .setParameter("legacy", TrangThaiChuyen.CHUA_KHOI_HANH).setParameter("sale", TrangThaiChuyen.DANG_MO_BAN)
            .setParameter("today", LocalDate.now(BUSINESS_ZONE)).getSingleResult();
        long running = count(em, "select count(c) from ChuyenXe c where c.tuyenXe.nhaXe = :operator and c.trangThai = :state",
            operator, "state", TrangThaiChuyen.DANG_CHAY);
        long holds = em.createQuery("select count(g) from GiuCho g where g.chuyenXe.tuyenXe.nhaXe = :operator "
                + "and g.trangThai = :state and g.thoiDiemHetHan > :now", Long.class)
            .setParameter("operator", operator).setParameter("state", TrangThaiGiuCho.DANG_GIU)
            .setParameter("now", LocalDateTime.now(BUSINESS_ZONE)).getSingleResult();
        long tickets = count(em, "select count(v) from VeXe v where v.chuyenXe.tuyenXe.nhaXe = :operator and v.trangThai = :state",
            operator, "state", TrangThaiVe.DA_XUAT_VE);
        long paid = count(em, "select count(distinct v) from VeXe v join v.donDatVe d join d.danhSachThanhToan t "
            + "where v.chuyenXe.tuyenXe.nhaXe = :operator and t.trangThai in :states",
            operator, "states", java.util.List.of(TrangThaiThanhToan.THANH_CONG,TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN,TrangThaiThanhToan.DA_HOAN_TIEN));
        return new Impact(upcoming, running, holds, tickets, paid);
    }
    public Impact impact(CurrentUser user, String operatorId) {
        return tx(user, em -> impact(em, found(em, operatorId, false)));
    }
    private static void apply(NhaXe x, OperatorInputs.NhaXe input) {
        x.tenNhaXe = required(input.tenNhaXe(), "tenNhaXe", 160);
        x.hotline = optional(input.hotline(), "hotline", 30);
        x.email = input.email() == null || input.email().isBlank() ? null : AccountService.email(input.email());
        x.diaChi = optional(input.diaChi(), "diaChi", 255);
        x.moTa = optional(input.moTa(), "moTa", 1000);
        x.logoUrl = optional(input.logoUrl(), "logoUrl", 500);
        if (input.ngayThanhLap() != null && input.ngayThanhLap().isAfter(LocalDate.now(BUSINESS_ZONE)))
            throw AccountException.invalid("ngayThanhLap", "Ngày thành lập không được ở tương lai.");
        x.ngayThanhLap = input.ngayThanhLap();
    }
    public OperatorView create(CurrentUser user, OperatorInputs.NhaXe input) {
        String code = id(input.maNhaXe());
        if (!code.matches("[A-Za-z0-9_-]{1,40}"))
            throw AccountException.invalid("maNhaXe", "Mã nhà xe chỉ gồm chữ không dấu, số, dấu gạch ngang hoặc gạch dưới.");
        try { return tx(user, em -> {
            if (em.find(NhaXe.class, code) != null)
                throw new AccountException(409, "OPERATOR_ALREADY_EXISTS", "Mã nhà xe đã tồn tại.");
            NhaXe x = new NhaXe(); x.maNhaXe = code; x.trangThai = TrangThaiNhaXe.CHO_PHE_DUYET;
            apply(x, input); em.persist(x); em.flush(); return view(x);
        }); } catch (PersistenceException error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause())
                if (cause instanceof org.hibernate.exception.ConstraintViolationException)
                    throw new AccountException(409, "OPERATOR_ALREADY_EXISTS", "Mã nhà xe đã tồn tại.");
            throw error;
        }
    }
    public OperatorView update(CurrentUser user, String operatorId, OperatorInputs.NhaXe input) {
        return tx(user, em -> {
            NhaXe x = found(em, operatorId, true);
            if (input.maNhaXe() != null && !x.maNhaXe.equals(input.maNhaXe()))
                throw AccountException.invalid("maNhaXe", "Không thể đổi mã nhà xe.");
            apply(x, input); return view(x);
        });
    }
    public StateChange state(CurrentUser user, String operatorId, OperatorInputs.TrangThai input) {
        if (input.trangThai() == null) throw AccountException.invalid("trangThai", "Trạng thái là bắt buộc.");
        return tx(user, em -> {
            NhaXe x = found(em, operatorId, true);
            Impact related = impact(em, x);
            x.trangThai = input.trangThai(); return new StateChange(view(x), related);
        });
    }
    public void delete(CurrentUser user, String operatorId) {
        tx(user, em -> {
            NhaXe x = found(em, operatorId, true);
            for (String entity : List.of("NguoiDung", "NhanVien", "Xe", "TuyenXe")) {
                long references = em.createQuery("select count(item) from " + entity + " item where item.nhaXe = :operator", Long.class)
                    .setParameter("operator", x).getSingleResult();
                if (references > 0) throw new AccountException(409, "OPERATOR_IN_USE",
                    "Nhà xe đã có dữ liệu liên quan; hãy chuyển trạng thái thay vì xóa.");
            }
            em.remove(x); return null;
        });
    }
    public List<ManagerView> managers(CurrentUser user, String operatorId) {
        return tx(user, em -> {
            NhaXe x = found(em, operatorId, false);
            return em.createQuery("select n from NguoiDung n where n.nhaXe = :operator and n.loaiTaiKhoan = :role order by n.hoTen", NguoiDung.class)
                .setParameter("operator", x).setParameter("role", LoaiTaiKhoan.NHA_XE).getResultList()
                .stream().map(OperatorService::view).toList();
        });
    }
    public ManagerView grant(CurrentUser user, String operatorId, OperatorInputs.QuanLy input) {
        return tx(user, em -> {
            NhaXe x = found(em, operatorId, true);
            String accountId = required(input.maNguoiDung(), "maNguoiDung", 40);
            NguoiDung account = em.find(NguoiDung.class, accountId, LockModeType.PESSIMISTIC_WRITE);
            if (account == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài khoản.");
            if (account.loaiTaiKhoan != LoaiTaiKhoan.KHACH_HANG || account.nhaXe != null || account.nhanVien != null
                    || account.trangThai != TrangThaiTaiKhoan.HOAT_DONG)
                throw new AccountException(409, "INVALID_STATE_TRANSITION", "Tài khoản không đủ điều kiện nhận quyền quản lý nhà xe.");
            account.loaiTaiKhoan = LoaiTaiKhoan.NHA_XE; account.nhaXe = x;
            revoke(em, account); return view(account);
        });
    }
    public void revoke(CurrentUser user, String operatorId, String accountId) {
        tx(user, em -> {
            NhaXe x = found(em, operatorId, true);
            NguoiDung account = em.find(NguoiDung.class, required(accountId, "maNguoiDung", 40), LockModeType.PESSIMISTIC_WRITE);
            if (account == null) throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài khoản.");
            if (account.loaiTaiKhoan != LoaiTaiKhoan.NHA_XE || account.nhaXe == null
                    || !account.nhaXe.maNhaXe.equals(x.maNhaXe)) throw AccessControl.denied();
            account.nhaXe = null; account.loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG;
            revoke(em, account); return null;
        });
    }
    private static void revoke(EntityManager em, NguoiDung account) {
        em.createQuery("delete from AuthSession s where s.nguoiDung = :account")
            .setParameter("account", account).executeUpdate();
    }
}
