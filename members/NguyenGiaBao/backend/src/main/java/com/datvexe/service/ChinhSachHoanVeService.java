package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.domain.Entities.ChinhSachHoanVe;
import com.datvexe.domain.States.TrangThaiChinhSach;
import com.datvexe.dto.ChinhSachHoanVeDto;
import com.datvexe.repository.ChinhSachHoanVeRepository;
import com.datvexe.security.AccessPolicy;
import com.datvexe.security.AccountException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.math.BigDecimal;
import java.util.List;

/** Admin operations for versioned, globally active refund policies. */
public final class ChinhSachHoanVeService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_FEE = new BigDecimal("999999999999");
    private static final String RESOURCE_CODE = "^[A-Za-z0-9_-]{1,40}$";

    private final EntityManagerFactory factory;

    public ChinhSachHoanVeService() {
        this(Jpa.factory());
    }

    ChinhSachHoanVeService(EntityManagerFactory factory) {
        this.factory = factory;
    }

    /** Page numbers start at 1; the default size is set by the controller to 20. */
    public ChinhSachHoanVeDto.PageResponse danhSach(int page, int size) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                if (page < 1) {
                    throw new DomainException(400, "page phải từ 1.");
                }
                if (size < 1 || size > 100) {
                    throw new DomainException(400, "size phải từ 1 đến 100.");
                }

                ChinhSachHoanVeRepository repository = new ChinhSachHoanVeRepository(em);
                long total = repository.count();
                long offset = (page - 1L) * size;
                List<ChinhSachHoanVeDto.Response> items;
                if (offset >= total) {
                    items = List.of();
                } else {
                    if (offset > Integer.MAX_VALUE) {
                        throw new DomainException(400, "Vị trí trang vượt quá giới hạn truy vấn.");
                    }
                    items = repository.list((int) offset, size).stream()
                            .map(ChinhSachHoanVeDto::view)
                            .toList();
                }
                long pages = total == 0 ? 0 : 1 + (total - 1) / size;
                ChinhSachHoanVeDto.PageResponse result = new ChinhSachHoanVeDto.PageResponse(
                        items, page, size, total, Math.toIntExact(pages));
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public ChinhSachHoanVeDto.Response chiTiet(String maChinhSach) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                String id = kiemTraMa(maChinhSach);
                ChinhSachHoanVe policy = new ChinhSachHoanVeRepository(em).find(id);
                if (policy == null) {
                    throw new AccountException(
                            404, "RESOURCE_NOT_FOUND", "Không tìm thấy chính sách hoàn vé.");
                }
                ChinhSachHoanVeDto.Response result = ChinhSachHoanVeDto.view(policy);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public ChinhSachHoanVeDto.Response tao(ChinhSachHoanVeDto.Request request) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                kiemTraDuLieu(request);
                String id = kiemTraMa(request.maChinhSach());

                ChinhSachHoanVeRepository repository = new ChinhSachHoanVeRepository(em);
                repository.lockConfiguration();
                if (repository.find(id) != null) {
                    throw new AccountException(409, "CONFLICT", "Mã chính sách đã tồn tại.");
                }

                ChinhSachHoanVe policy = new ChinhSachHoanVe();
                policy.maChinhSach = id;
                ganDuLieu(policy, request);
                policy.trangThai = request.trangThai() == null
                        ? TrangThaiChinhSach.TAM_NGUNG
                        : request.trangThai();
                if (policy.trangThai == TrangThaiChinhSach.HOAT_DONG) {
                    repository.deactivateActiveExcept(null);
                }
                repository.save(policy);
                em.flush();

                ChinhSachHoanVeDto.Response result = ChinhSachHoanVeDto.view(policy);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public ChinhSachHoanVeDto.Response capNhat(
            String maChinhSach,
            ChinhSachHoanVeDto.Request request) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                String id = kiemTraMa(maChinhSach);
                kiemTraDuLieu(request);
                if (request.maChinhSach() != null) {
                    throw new DomainException(400, "Khi sửa, mã chỉ lấy từ URL.");
                }

                ChinhSachHoanVeRepository repository = new ChinhSachHoanVeRepository(em);
                repository.lockConfiguration();
                ChinhSachHoanVe policy = repository.lock(id);
                if (policy == null) {
                    throw new AccountException(
                            404, "RESOURCE_NOT_FOUND", "Không tìm thấy chính sách hoàn vé.");
                }
                if (repository.used(id)) {
                    throw new AccountException(
                            409,
                            "INVALID_STATE_TRANSITION",
                            "Chính sách đã được đơn đặt vé tham chiếu nên không thể sửa nội dung.");
                }

                TrangThaiChinhSach nextStatus = request.trangThai() == null
                        ? policy.trangThai
                        : request.trangThai();
                if (nextStatus == TrangThaiChinhSach.HOAT_DONG) {
                    repository.deactivateActiveExcept(id);
                    policy = repository.lock(id);
                    if (policy == null) {
                        throw new AccountException(
                                404, "RESOURCE_NOT_FOUND", "Không tìm thấy chính sách hoàn vé.");
                    }
                }
                ganDuLieu(policy, request);
                policy.trangThai = nextStatus;
                em.flush();

                ChinhSachHoanVeDto.Response result = ChinhSachHoanVeDto.view(policy);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public ChinhSachHoanVeDto.Response capNhatTrangThai(
            String maChinhSach,
            TrangThaiChinhSach trangThai) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                String id = kiemTraMa(maChinhSach);
                if (trangThai == null) {
                    throw new DomainException(400, "Trạng thái là bắt buộc.");
                }

                ChinhSachHoanVeRepository repository = new ChinhSachHoanVeRepository(em);
                repository.lockConfiguration();
                ChinhSachHoanVe policy = repository.lock(id);
                if (policy == null) {
                    throw new AccountException(
                            404, "RESOURCE_NOT_FOUND", "Không tìm thấy chính sách hoàn vé.");
                }
                if (trangThai == TrangThaiChinhSach.HOAT_DONG) {
                    repository.deactivateActiveExcept(id);
                    policy = repository.lock(id);
                    if (policy == null) {
                        throw new AccountException(
                                404, "RESOURCE_NOT_FOUND", "Không tìm thấy chính sách hoàn vé.");
                    }
                }
                policy.trangThai = trangThai;
                em.flush();

                ChinhSachHoanVeDto.Response result = ChinhSachHoanVeDto.view(policy);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    private static String kiemTraMa(String value) {
        if (value == null || value.strip().isEmpty()) {
            throw new DomainException(400, "Mã chính sách là bắt buộc.");
        }
        String id = value.strip();
        if (!id.matches(RESOURCE_CODE)) {
            throw new DomainException(
                    400,
                    "Mã chỉ được gồm chữ cái ASCII, chữ số, dấu gạch dưới hoặc dấu gạch ngang, "
                            + "dài từ 1 đến 40 ký tự.");
        }
        return id;
    }

    private static void kiemTraDuLieu(ChinhSachHoanVeDto.Request request) {
        if (request == null) {
            throw new DomainException(400, "Body không được để trống.");
        }
        String name = request.tenChinhSach() == null ? null : request.tenChinhSach().strip();
        if (name == null || name.isEmpty()) {
            throw new DomainException(400, "Tên chính sách là bắt buộc.");
        }
        if (name.length() > 160) {
            throw new DomainException(400, "Tên chính sách tối đa 160 ký tự.");
        }
        Integer hours = request.soGioToiThieuTruocKhoiHanh();
        if (hours == null || hours < 0) {
            throw new DomainException(400, "Số giờ phải là số nguyên không âm.");
        }
        BigDecimal rate = request.tyLeHoan();
        if (rate == null || rate.compareTo(ZERO) < 0 || rate.compareTo(ONE_HUNDRED) > 0) {
            throw new DomainException(400, "Tỷ lệ hoàn phải từ 0 đến 100.");
        }
        if (rate.stripTrailingZeros().scale() > 2) {
            throw new DomainException(400, "Tỷ lệ hoàn chỉ hỗ trợ tối đa 2 chữ số thập phân.");
        }
        BigDecimal fee = request.phiHoan();
        if (fee == null || fee.compareTo(ZERO) < 0
                || fee.compareTo(MAX_FEE) > 0
                || fee.stripTrailingZeros().scale() > 0) {
            throw new DomainException(
                    400, "Phí hoàn phải là số nguyên VND không âm, tối đa 12 chữ số.");
        }
        if (request.choPhepHoanVeKhuyenMai() == null) {
            throw new DomainException(400, "Trường choPhepHoanVeKhuyenMai là bắt buộc.");
        }
    }

    private static void ganDuLieu(
            ChinhSachHoanVe policy,
            ChinhSachHoanVeDto.Request request) {
        policy.tenChinhSach = request.tenChinhSach().strip();
        policy.soGioToiThieuTruocKhoiHanh = request.soGioToiThieuTruocKhoiHanh();
        policy.tyLeHoan = request.tyLeHoan();
        policy.phiHoan = request.phiHoan();
        policy.choPhepHoanVeKhuyenMai = request.choPhepHoanVeKhuyenMai();
    }
}
