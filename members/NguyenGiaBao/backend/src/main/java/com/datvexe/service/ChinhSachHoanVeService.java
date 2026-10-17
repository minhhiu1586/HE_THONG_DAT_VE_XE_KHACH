package com.datvexe.service;

import com.datvexe.domain.Entities.ChinhSachHoanVe;
import com.datvexe.domain.States.TrangThaiChinhSach;
import com.datvexe.dto.ChinhSachHoanVeDto;
import com.datvexe.repository.ChinhSachHoanVeRepository;
import com.datvexe.security.AccessPolicy;
import com.datvexe.security.AccountException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Admin operations for versioned, globally active refund policies. */
public final class ChinhSachHoanVeService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String RESOURCE_CODE = "^[A-Za-z0-9_-]{1,40}$";
    private static final String WHOLE_VND = "^(0|[1-9][0-9]*)$";

    private final EntityManagerFactory factory;

    public ChinhSachHoanVeService(EntityManagerFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    /** Page numbers start at 1; the default size is set by the controller to 20. */
    public ChinhSachHoanVeDto.PageResponse danhSach(int page, int size) {
        return tx(em -> {
            validatePage(page, size);
            ChinhSachHoanVeRepository repository = new ChinhSachHoanVeRepository(em);
            long total = repository.count();
            long offset = (page - 1L) * size;
            List<ChinhSachHoanVeDto.Response> items;
            if (offset >= total) {
                items = List.of();
            } else {
                if (offset > Integer.MAX_VALUE) {
                    throw AccountException.invalid("page", "Vị trí trang vượt quá giới hạn truy vấn.");
                }
                items = repository.list((int) offset, size).stream()
                        .map(ChinhSachHoanVeDto::view)
                        .toList();
            }
            long pages = total == 0 ? 0 : 1 + (total - 1) / size;
            return new ChinhSachHoanVeDto.PageResponse(items, page, size, total, Math.toIntExact(pages));
        });
    }

    public ChinhSachHoanVeDto.Response chiTiet(String maChinhSach) {
        return tx(em -> {
            String id = requiredCode(maChinhSach, "maChinhSach");
            return ChinhSachHoanVeDto.view(found(new ChinhSachHoanVeRepository(em).find(id)));
        });
    }

    public ChinhSachHoanVeDto.Response tao(ChinhSachHoanVeDto.Request request) {
        return tx(em -> {
            if (request == null) throw AccountException.invalid("body", "Body không được để trống.");
            PolicyValues values = validate(request, true);
            ChinhSachHoanVeRepository repository = new ChinhSachHoanVeRepository(em);
            repository.lockConfiguration();
            if (repository.find(values.maChinhSach()) != null) throw duplicateCode();

            ChinhSachHoanVe policy = new ChinhSachHoanVe();
            apply(policy, values);
            policy.trangThai = values.trangThai() == null
                    ? TrangThaiChinhSach.TAM_NGUNG
                    : values.trangThai();
            if (policy.trangThai == TrangThaiChinhSach.HOAT_DONG) {
                repository.deactivateActive();
                repository.save(policy);
            } else {
                repository.save(policy);
            }
            em.flush();
            return ChinhSachHoanVeDto.view(policy);
        });
    }

    public ChinhSachHoanVeDto.Response capNhat(
            String maChinhSach,
            ChinhSachHoanVeDto.Request request) {
        return tx(em -> {
            String id = requiredCode(maChinhSach, "maChinhSach");
            if (request == null) throw AccountException.invalid("body", "Body không được để trống.");
            PolicyValues values = validate(request, false);
            ChinhSachHoanVeRepository repository = new ChinhSachHoanVeRepository(em);
            repository.lockConfiguration();
            ChinhSachHoanVe policy = found(repository.lock(id));
            if (repository.used(id)) {
                throw new AccountException(
                        409,
                        "INVALID_STATE_TRANSITION",
                        "Chính sách đã được đơn đặt vé tham chiếu nên không thể sửa nội dung.");
            }

            TrangThaiChinhSach nextStatus = values.trangThai() == null
                    ? policy.trangThai
                    : values.trangThai();
            if (nextStatus == TrangThaiChinhSach.HOAT_DONG) {
                repository.deactivateActiveExcept(id);
                policy = found(repository.lock(id));
            }
            apply(policy, values);
            policy.trangThai = nextStatus;
            em.flush();
            return ChinhSachHoanVeDto.view(policy);
        });
    }

    public ChinhSachHoanVeDto.Response capNhatTrangThai(
            String maChinhSach,
            TrangThaiChinhSach trangThai) {
        return tx(em -> {
            String id = requiredCode(maChinhSach, "maChinhSach");
            if (trangThai == null) {
                throw AccountException.invalid("trangThai", "Trạng thái là bắt buộc.");
            }
            ChinhSachHoanVeRepository repository = new ChinhSachHoanVeRepository(em);
            repository.lockConfiguration();
            ChinhSachHoanVe policy = found(repository.lock(id));
            if (trangThai == TrangThaiChinhSach.HOAT_DONG) {
                repository.deactivateActiveExcept(id);
                policy = found(repository.lock(id));
            }
            policy.trangThai = trangThai;
            em.flush();
            return ChinhSachHoanVeDto.view(policy);
        });
    }

    private <T> T tx(Function<EntityManager, T> action) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                AccessPolicy.guard(em);
                T result = action.apply(em);
                transaction.commit();
                return result;
            } catch (RuntimeException e) {
                if (transaction.isActive()) transaction.rollback();
                throw e;
            }
        }
    }

    private static void validatePage(int page, int size) {
        if (page < 1) throw AccountException.invalid("page", "page phải từ 1.");
        if (size < 1 || size > 100) {
            throw AccountException.invalid("size", "size phải từ 1 đến 100.");
        }
    }

    private static PolicyValues validate(ChinhSachHoanVeDto.Request request, boolean creating) {
        String id = creating ? requiredCode(request.maChinhSach(), "maChinhSach") : null;
        if (!creating && request.maChinhSach() != null) {
            throw AccountException.invalid("maChinhSach", "Khi sửa, mã chỉ lấy từ URL.");
        }
        String name = requiredText(request.tenChinhSach(), "tenChinhSach", 160);
        Integer hours = request.soGioToiThieuTruocKhoiHanh();
        if (hours == null || hours < 0) {
            throw AccountException.invalid(
                    "soGioToiThieuTruocKhoiHanh", "Số giờ phải là số nguyên không âm.");
        }
        BigDecimal rate = parseRate(request.tyLeHoan());
        BigDecimal fee = parseFee(request.phiHoan());
        Boolean allowPromotion = request.choPhepHoanVeKhuyenMai();
        if (allowPromotion == null) {
            throw AccountException.invalid("choPhepHoanVeKhuyenMai", "Trường này là bắt buộc.");
        }
        return new PolicyValues(
                id, name, hours, rate, fee, allowPromotion, request.trangThai());
    }

    private static BigDecimal parseRate(JsonNode value) {
        if (value == null || value.isNull() || !value.isNumber()) {
            throw AccountException.invalid("tyLeHoan", "Tỷ lệ hoàn phải là JSON number.");
        }
        BigDecimal rate = value.decimalValue();
        if (rate.compareTo(ZERO) < 0 || rate.compareTo(ONE_HUNDRED) > 0) {
            throw AccountException.invalid("tyLeHoan", "Tỷ lệ hoàn phải từ 0 đến 100.");
        }
        if (Math.max(0, rate.stripTrailingZeros().scale()) > 2) {
            throw AccountException.invalid("tyLeHoan", "Tỷ lệ hoàn chỉ hỗ trợ tối đa 2 chữ số thập phân.");
        }
        return rate;
    }

    private static BigDecimal parseFee(String value) {
        if (value == null || !value.matches(WHOLE_VND) || value.length() > 12) {
            throw AccountException.invalid(
                    "phiHoan", "Phí hoàn phải là số nguyên VND không âm, tối đa 12 chữ số.");
        }
        return new BigDecimal(value);
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.strip().isEmpty()) {
            throw AccountException.invalid(field, "Trường này là bắt buộc.");
        }
        String result = value.strip();
        if (result.length() > maxLength) {
            throw AccountException.invalid(field, "Trường này tối đa " + maxLength + " ký tự.");
        }
        return result;
    }

    private static String requiredCode(String value, String field) {
        String result = requiredText(value, field, 40);
        if (!result.matches(RESOURCE_CODE)) {
            throw AccountException.invalid(
                    field,
                    "Mã chỉ được gồm chữ cái ASCII, chữ số, dấu gạch dưới hoặc dấu gạch ngang, dài từ 1 đến 40 ký tự.");
        }
        return result;
    }

    private static void apply(ChinhSachHoanVe policy, PolicyValues values) {
        if (values.maChinhSach() != null) policy.maChinhSach = values.maChinhSach();
        policy.tenChinhSach = values.tenChinhSach();
        policy.soGioToiThieuTruocKhoiHanh = values.soGioToiThieuTruocKhoiHanh();
        policy.tyLeHoan = values.tyLeHoan();
        policy.phiHoan = values.phiHoan();
        policy.choPhepHoanVeKhuyenMai = values.choPhepHoanVeKhuyenMai();
    }

    private static ChinhSachHoanVe found(ChinhSachHoanVe policy) {
        if (policy == null) {
            throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy chính sách hoàn vé.");
        }
        return policy;
    }

    private static AccountException duplicateCode() {
        return new AccountException(409, "CONFLICT", "Mã chính sách đã tồn tại.");
    }

    private record PolicyValues(
            String maChinhSach,
            String tenChinhSach,
            int soGioToiThieuTruocKhoiHanh,
            BigDecimal tyLeHoan,
            BigDecimal phiHoan,
            boolean choPhepHoanVeKhuyenMai,
            TrangThaiChinhSach trangThai) {}
}
