package com.datvexe.service;

import com.datvexe.domain.Entities.PhuongThucThanhToan;
import com.datvexe.domain.States.TrangThaiPhuongThuc;
import com.datvexe.dto.PhuongThucThanhToanDto;
import com.datvexe.repository.PhuongThucThanhToanRepository;
import com.datvexe.security.AccessPolicy;
import com.datvexe.security.AccountException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;
import java.util.List;

/** Business rules and transactions for payment methods. */
public final class PhuongThucThanhToanService {
    private static final String PRIMARY_KEY = "phuong_thuc_thanh_toan_pkey";
    private static final String RESOURCE_CODE = "^[A-Za-z0-9_-]{1,40}$";

    private final EntityManagerFactory factory;

    public PhuongThucThanhToanService(EntityManagerFactory factory) {
        this.factory = factory;
    }

    public List<PhuongThucThanhToanDto.Response> danhSach() {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                List<PhuongThucThanhToanDto.Response> result =
                        new PhuongThucThanhToanRepository(em).list().stream()
                                .map(PhuongThucThanhToanService::toResponse)
                                .toList();
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public List<PhuongThucThanhToanDto.Response> danhSachKhaDung() {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                List<PhuongThucThanhToanDto.Response> result =
                        new PhuongThucThanhToanRepository(em).danhSachKhaDung().stream()
                                .map(PhuongThucThanhToanService::toResponse)
                                .toList();
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public PhuongThucThanhToanDto.Response chiTiet(String maPhuongThuc) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                String id = requiredCode(maPhuongThuc, "maPhuongThuc");
                PhuongThucThanhToan paymentMethod = found(
                        new PhuongThucThanhToanRepository(em).find(id));
                PhuongThucThanhToanDto.Response result = toResponse(paymentMethod);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public PhuongThucThanhToanDto.Response tao(PhuongThucThanhToanDto.Request request) {
        try {
            try (EntityManager em = factory.createEntityManager()) {
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                try {
                    AccessPolicy.guard(em);
                    if (request == null) {
                        throw AccountException.invalid("body", "Body không được để trống.");
                    }

                    String id = requiredCode(request.maPhuongThuc(), "maPhuongThuc");
                    String name = requiredText(request.tenPhuongThuc(), "tenPhuongThuc", 120);
                    String description = optionalText(request.moTa(), "moTa", 1000);

                    PhuongThucThanhToanRepository repository =
                            new PhuongThucThanhToanRepository(em);
                    if (repository.find(id) != null) {
                        throw conflict();
                    }

                    PhuongThucThanhToan paymentMethod = new PhuongThucThanhToan();
                    paymentMethod.maPhuongThuc = id;
                    paymentMethod.tenPhuongThuc = name;
                    paymentMethod.moTa = description;
                    repository.save(paymentMethod);
                    em.flush();

                    PhuongThucThanhToanDto.Response result = toResponse(paymentMethod);
                    tx.commit();
                    return result;
                } catch (RuntimeException e) {
                    if (tx.isActive()) tx.rollback();
                    throw e;
                }
            }
        } catch (PersistenceException e) {
            if (isPaymentMethodPrimaryKeyViolation(e)) throw conflict();
            throw e;
        }
    }

    public PhuongThucThanhToanDto.Response capNhat(
            String maPhuongThuc,
            PhuongThucThanhToanDto.Request request) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                String id = requiredCode(maPhuongThuc, "maPhuongThuc");
                if (request == null) {
                    throw AccountException.invalid("body", "Body không được để trống.");
                }
                if (request.maPhuongThuc() != null) {
                    throw AccountException.invalid(
                            "maPhuongThuc", "Khi sửa, mã chỉ lấy từ URL.");
                }

                String name = requiredText(request.tenPhuongThuc(), "tenPhuongThuc", 120);
                String description = optionalText(request.moTa(), "moTa", 1000);

                PhuongThucThanhToanRepository repository =
                        new PhuongThucThanhToanRepository(em);
                PhuongThucThanhToan paymentMethod = found(repository.lock(id));
                paymentMethod.tenPhuongThuc = name;
                paymentMethod.moTa = description;

                PhuongThucThanhToanDto.Response result = toResponse(paymentMethod);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public PhuongThucThanhToanDto.Response capNhatTrangThai(
            String maPhuongThuc,
            TrangThaiPhuongThuc trangThai) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                String id = requiredCode(maPhuongThuc, "maPhuongThuc");
                if (trangThai == null) {
                    throw AccountException.invalid("trangThai", "Trạng thái là bắt buộc.");
                }

                PhuongThucThanhToanRepository repository =
                        new PhuongThucThanhToanRepository(em);
                PhuongThucThanhToan paymentMethod = found(repository.lock(id));
                paymentMethod.capNhatTrangThai(trangThai);

                PhuongThucThanhToanDto.Response result = toResponse(paymentMethod);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.strip().isEmpty()) {
            throw AccountException.invalid(field, "Trường này là bắt buộc.");
        }

        String result = value.strip();
        if (result.length() > maxLength) {
            throw AccountException.invalid(
                    field, "Trường này tối đa " + maxLength + " ký tự.");
        }
        return result;
    }

    private static String requiredCode(String value, String field) {
        String result = requiredText(value, field, 40);
        if (!value.matches(RESOURCE_CODE)) {
            throw AccountException.invalid(
                    field,
                    "Mã chỉ được gồm chữ cái ASCII, chữ số, dấu gạch dưới hoặc dấu gạch ngang, "
                            + "dài từ 1 đến 40 ký tự.");
        }
        return result;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null) return null;

        String result = value.strip();
        if (result.length() > maxLength) {
            throw AccountException.invalid(
                    field, "Trường này tối đa " + maxLength + " ký tự.");
        }
        return result;
    }

    private static PhuongThucThanhToan found(PhuongThucThanhToan paymentMethod) {
        if (paymentMethod == null) {
            throw new AccountException(
                    404, "RESOURCE_NOT_FOUND", "Không tìm thấy phương thức thanh toán.");
        }
        return paymentMethod;
    }

    private static PhuongThucThanhToanDto.Response toResponse(
            PhuongThucThanhToan paymentMethod) {
        return new PhuongThucThanhToanDto.Response(
                paymentMethod.maPhuongThuc,
                paymentMethod.tenPhuongThuc,
                paymentMethod.moTa,
                paymentMethod.trangThai);
    }

    private static AccountException conflict() {
        return new AccountException(
                409, "CONFLICT", "Mã phương thức thanh toán đã tồn tại.");
    }

    private static boolean isPaymentMethodPrimaryKeyViolation(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "23505".equals(violation.getSQLState())
                    && PRIMARY_KEY.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
