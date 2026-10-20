package com.datvexe.service;

import static com.datvexe.validation.OperatorValidation.*;

import com.datvexe.domain.Entities.NguoiDung;
import com.datvexe.domain.Entities.NhaXe;
import com.datvexe.domain.States.*;
import com.datvexe.dto.OperatorInputs;
import com.datvexe.repository.AuthSessionRepository;
import com.datvexe.repository.NhaXeRepository;
import com.datvexe.security.*;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;

public final class OperatorService {
    private final EntityManagerFactory factory;

    public OperatorService(EntityManagerFactory factory) {
        this.factory = factory;
    }

    public record OperatorView(
            String maNhaXe,
            String tenNhaXe,
            String hotline,
            String email,
            String diaChi,
            String moTa,
            String logoUrl,
            LocalDate ngayThanhLap,
            TrangThaiNhaXe trangThai) {}

    public record ManagerView(
            String maNguoiDung,
            String hoTen,
            String email,
            String soDienThoai,
            TrangThaiTaiKhoan trangThai) {}

    public record Page<T>(List<T> items, int page, int size, long totalItems, long totalPages) {}

    public record Impact(
            long chuyenSapKhoiHanh,
            long chuyenDangChay,
            long giuChoDangGiu,
            long veDaPhatHanh,
            long veCoThanhToanThanhCong) {}

    public record StateChange(OperatorView nhaXe, Impact anhHuong) {}

    private <T> T tx(CurrentUser identity, Function<EntityManager, T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessControl.role(
                        AccessControl.authenticated(em, identity, Instant.now()),
                        LoaiTaiKhoan.ADMIN);
                T result = action.apply(em);
                transaction.commit();
                return result;
            } catch (RuntimeException error) {
                if (transaction.isActive()) transaction.rollback();
                throw error;
            }
        }
    }

    private static NhaXe found(EntityManager em, String value, boolean lock) {
        NhaXe operator = new NhaXeRepository(em).find(id(value), lock);
        if (operator == null)
            throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy nhà xe.");
        return operator;
    }

    private static OperatorView view(NhaXe x) {
        return new OperatorView(
                x.maNhaXe,
                x.tenNhaXe,
                x.hotline,
                x.email,
                x.diaChi,
                x.moTa,
                x.logoUrl,
                x.ngayThanhLap,
                x.trangThai);
    }

    private static ManagerView view(NguoiDung x) {
        return new ManagerView(x.maNguoiDung, x.hoTen, x.email, x.soDienThoai, x.trangThai);
    }

    private static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw AccountException.invalid("page", "Phân trang không hợp lệ.");
    }

    public Page<OperatorView> list(
            CurrentUser user, String search, TrangThaiNhaXe state, int page, int size) {
        page(page, size);
        return tx(
                user,
                em -> {
                    String term = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
                    if (term.length() > 160)
                        throw AccountException.invalid("q", "Từ khóa quá dài.");
                    var result = new NhaXeRepository(em).search(term, state, page, size);
                    var items = result.items().stream().map(OperatorService::view).toList();
                    return new Page<>(
                            items, page, size, result.total(), (result.total() + size - 1) / size);
                });
    }

    public OperatorView get(CurrentUser user, String operatorId) {
        return tx(user, em -> view(found(em, operatorId, false)));
    }

    private static Impact impact(EntityManager em, NhaXe operator) {
        var counts = new NhaXeRepository(em).impact(operator);
        return new Impact(
                counts.chuyenSapKhoiHanh(),
                counts.chuyenDangChay(),
                counts.giuChoDangGiu(),
                counts.veDaPhatHanh(),
                counts.veCoThanhToanThanhCong());
    }

    public Impact impact(CurrentUser user, String operatorId) {
        return tx(user, em -> impact(em, found(em, operatorId, false)));
    }

    public OperatorView create(CurrentUser user, OperatorInputs.NhaXe input) {
        String code = id(input.maNhaXe());
        if (!code.matches("[A-Za-z0-9_-]{1,40}"))
            throw AccountException.invalid(
                    "maNhaXe",
                    "Mã nhà xe chỉ gồm chữ không dấu, số, dấu gạch ngang hoặc gạch dưới.");
        try {
            return tx(
                    user,
                    em -> {
                        if (em.find(NhaXe.class, code) != null)
                            throw new AccountException(
                                    409, "OPERATOR_ALREADY_EXISTS", "Mã nhà xe đã tồn tại.");
                        NhaXe x = new NhaXe();
                        x.maNhaXe = code;
                        x.trangThai = TrangThaiNhaXe.CHO_PHE_DUYET;
                        apply(x, input);
                        em.persist(x);
                        em.flush();
                        return view(x);
                    });
        } catch (PersistenceException error) {
            for (Throwable cause = error; cause != null; cause = cause.getCause())
                if (cause instanceof org.hibernate.exception.ConstraintViolationException)
                    throw new AccountException(
                            409, "OPERATOR_ALREADY_EXISTS", "Mã nhà xe đã tồn tại.");
            throw error;
        }
    }

    public OperatorView update(CurrentUser user, String operatorId, OperatorInputs.NhaXe input) {
        return tx(
                user,
                em -> {
                    NhaXe x = found(em, operatorId, true);
                    if (input.maNhaXe() != null && !x.maNhaXe.equals(input.maNhaXe()))
                        throw AccountException.invalid("maNhaXe", "Không thể đổi mã nhà xe.");
                    apply(x, input);
                    return view(x);
                });
    }

    public StateChange state(CurrentUser user, String operatorId, OperatorInputs.TrangThai input) {
        if (input.trangThai() == null)
            throw AccountException.invalid("trangThai", "Trạng thái là bắt buộc.");
        return tx(
                user,
                em -> {
                    NhaXe x = found(em, operatorId, true);
                    Impact related = impact(em, x);
                    x.trangThai = input.trangThai();
                    return new StateChange(view(x), related);
                });
    }

    public void delete(CurrentUser user, String operatorId) {
        tx(
                user,
                em -> {
                    NhaXe operator = found(em, operatorId, true);
                    if (new NhaXeRepository(em).hasReferences(operator))
                        throw new AccountException(
                                409,
                                "OPERATOR_IN_USE",
                                "Nhà xe đã có dữ liệu liên quan; hãy chuyển trạng thái thay vì"
                                    + " xóa.");
                    em.remove(operator);
                    return null;
                });
    }

    public List<ManagerView> managers(CurrentUser user, String operatorId) {
        return tx(
                user,
                em -> {
                    NhaXe x = found(em, operatorId, false);
                    return new NhaXeRepository(em)
                            .managers(x).stream().map(OperatorService::view).toList();
                });
    }

    public ManagerView grant(CurrentUser user, String operatorId, OperatorInputs.QuanLy input) {
        return tx(
                user,
                em -> {
                    NhaXe x = found(em, operatorId, true);
                    String accountId = required(input.maNguoiDung(), "maNguoiDung", 40);
                    NguoiDung account =
                            em.find(NguoiDung.class, accountId, LockModeType.PESSIMISTIC_WRITE);
                    if (account == null)
                        throw new AccountException(
                                404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài khoản.");
                    if (account.loaiTaiKhoan != LoaiTaiKhoan.KHACH_HANG
                            || account.nhaXe != null
                            || account.nhanVien != null
                            || account.trangThai != TrangThaiTaiKhoan.HOAT_DONG)
                        throw new AccountException(
                                409,
                                "INVALID_STATE_TRANSITION",
                                "Tài khoản không đủ điều kiện nhận quyền quản lý nhà xe.");
                    account.loaiTaiKhoan = LoaiTaiKhoan.NHA_XE;
                    account.nhaXe = x;
                    revoke(em, account);
                    return view(account);
                });
    }

    public void revoke(CurrentUser user, String operatorId, String accountId) {
        tx(
                user,
                em -> {
                    NhaXe x = found(em, operatorId, true);
                    NguoiDung account =
                            em.find(
                                    NguoiDung.class,
                                    required(accountId, "maNguoiDung", 40),
                                    LockModeType.PESSIMISTIC_WRITE);
                    if (account == null)
                        throw new AccountException(
                                404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài khoản.");
                    if (account.loaiTaiKhoan != LoaiTaiKhoan.NHA_XE
                            || account.nhaXe == null
                            || !account.nhaXe.maNhaXe.equals(x.maNhaXe))
                        throw AccessControl.denied();
                    account.nhaXe = null;
                    account.loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG;
                    revoke(em, account);
                    return null;
                });
    }

    private static void revoke(EntityManager em, NguoiDung account) {
        new AuthSessionRepository(em).revokeAll(account);
    }
}
