package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.domain.Entities.PhuongThucThanhToan;
import com.datvexe.domain.States.TrangThaiPhuongThuc;
import com.datvexe.dto.PhuongThucThanhToanDto;
import com.datvexe.repository.PhuongThucThanhToanRepository;
import com.datvexe.security.AccessPolicy;
import com.datvexe.security.AccountException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.util.List;

/** Business rules and transactions for payment methods. */
public final class PhuongThucThanhToanService {
    private static final String RESOURCE_CODE = "^[A-Za-z0-9_-]{1,40}$";

    private final EntityManagerFactory factory = Jpa.factory();

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
                kiemTraMa(maPhuongThuc);
                PhuongThucThanhToan paymentMethod =
                        new PhuongThucThanhToanRepository(em).find(maPhuongThuc);
                if (paymentMethod == null) {
                    throw new AccountException(
                            404, "RESOURCE_NOT_FOUND", "Không tìm thấy phương thức thanh toán.");
                }
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
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                kiemTraDuLieu(request);
                kiemTraMa(request.maPhuongThuc());

                PhuongThucThanhToanRepository repository =
                        new PhuongThucThanhToanRepository(em);
                String id = request.maPhuongThuc();
                if (repository.find(id) != null) {
                    throw new AccountException(
                            409, "CONFLICT", "Mã phương thức thanh toán đã tồn tại.");
                }

                PhuongThucThanhToan paymentMethod = new PhuongThucThanhToan();
                paymentMethod.maPhuongThuc = id;
                paymentMethod.tenPhuongThuc = request.tenPhuongThuc().strip();
                paymentMethod.moTa = request.moTa() == null ? null : request.moTa().strip();
                repository.save(paymentMethod);

                PhuongThucThanhToanDto.Response result = toResponse(paymentMethod);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
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
                kiemTraMa(maPhuongThuc);
                kiemTraDuLieu(request);
                if (request.maPhuongThuc() != null) {
                    throw new DomainException(400, "Khi sửa, mã chỉ lấy từ URL.");
                }

                PhuongThucThanhToanRepository repository =
                        new PhuongThucThanhToanRepository(em);
                PhuongThucThanhToan paymentMethod = repository.lock(maPhuongThuc);
                if (paymentMethod == null) {
                    throw new AccountException(
                            404, "RESOURCE_NOT_FOUND", "Không tìm thấy phương thức thanh toán.");
                }
                paymentMethod.tenPhuongThuc = request.tenPhuongThuc().strip();
                paymentMethod.moTa = request.moTa() == null ? null : request.moTa().strip();

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
                kiemTraMa(maPhuongThuc);
                if (trangThai == null) {
                    throw new DomainException(400, "Trạng thái là bắt buộc.");
                }

                PhuongThucThanhToanRepository repository =
                        new PhuongThucThanhToanRepository(em);
                PhuongThucThanhToan paymentMethod = repository.lock(maPhuongThuc);
                if (paymentMethod == null) {
                    throw new AccountException(
                            404, "RESOURCE_NOT_FOUND", "Không tìm thấy phương thức thanh toán.");
                }
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

    private static void kiemTraMa(String value) {
        if (value == null || value.strip().isEmpty()) {
            throw new DomainException(400, "Mã phương thức thanh toán là bắt buộc.");
        }
        if (!value.matches(RESOURCE_CODE)) {
            throw new DomainException(
                    400,
                    "Mã chỉ được gồm chữ cái ASCII, chữ số, dấu gạch dưới hoặc dấu gạch ngang, "
                            + "dài từ 1 đến 40 ký tự.");
        }
    }

    private static void kiemTraDuLieu(PhuongThucThanhToanDto.Request request) {
        if (request == null) {
            throw new DomainException(400, "Body không được để trống.");
        }
        if (request.tenPhuongThuc() == null || request.tenPhuongThuc().strip().isEmpty()) {
            throw new DomainException(400, "Trường tenPhuongThuc là bắt buộc.");
        }
        if (request.tenPhuongThuc().strip().length() > 120) {
            throw new DomainException(400, "Trường tenPhuongThuc tối đa 120 ký tự.");
        }
        if (request.moTa() != null && request.moTa().strip().length() > 1000) {
            throw new DomainException(400, "Trường moTa tối đa 1000 ký tự.");
        }
    }

    private static PhuongThucThanhToanDto.Response toResponse(
            PhuongThucThanhToan paymentMethod) {
        return new PhuongThucThanhToanDto.Response(
                paymentMethod.maPhuongThuc,
                paymentMethod.tenPhuongThuc,
                paymentMethod.moTa,
                paymentMethod.trangThai);
    }
}
