package com.datvexe.service;

import com.datvexe.domain.Entities.KhuyenMai;
import com.datvexe.domain.States.LoaiGiam;
import com.datvexe.domain.States.TrangThaiKhuyenMai;
import com.datvexe.dto.KhuyenMaiDto;
import com.datvexe.repository.KhuyenMaiRepository;
import com.datvexe.security.AccessPolicy;
import com.datvexe.security.AccountException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.PersistenceException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Business rules and resource-local transactions for promotions. */
public final class KhuyenMaiService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String PRIMARY_KEY = "khuyen_mai_pkey";
    private static final String ORDER_PROMOTION_FOREIGN_KEY = "don_dat_ve_ma_khuyen_mai_fkey";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String NON_NEGATIVE_INTEGER = "^(0|[1-9][0-9]*)$";
    private static final String POSITIVE_INTEGER = "^[1-9][0-9]*$";
    private static final String RESOURCE_CODE = "^[A-Za-z0-9_-]{1,40}$";

    private final EntityManagerFactory factory;
    private final Clock clock;

    public KhuyenMaiService(EntityManagerFactory factory) {
        this(factory, Clock.system(BUSINESS_ZONE));
    }

    /** Clock is injectable so promotion rules can be checked at a fixed instant. */
    public KhuyenMaiService(EntityManagerFactory factory, Clock clock) {
        this.factory = Objects.requireNonNull(factory, "factory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** page bắt đầu từ 1; size mặc định 20, tối đa 100. */
    public KhuyenMaiDto.PageResponse danhSach(
            String tuKhoa,
            TrangThaiKhuyenMai trangThai,
            LocalDate tuNgay,
            LocalDate denNgay,
            int page,
            int size) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                validatePage(page, size);
                validateDateRange(tuNgay, denNgay);
                String keyword = optionalQuery(tuKhoa, "tuKhoa", 200);
                LocalDateTime now = now();
                KhuyenMaiRepository repository = new KhuyenMaiRepository(em, now);
                long total = repository.dem(keyword, trangThai, tuNgay, denNgay);
                long offset = (page - 1L) * size;

                List<KhuyenMaiDto.Response> items;
                if (offset >= total) {
                    items = List.of();
                } else {
                    if (offset > Integer.MAX_VALUE) {
                        throw AccountException.invalid(
                                "page", "Vị trí trang vượt quá giới hạn truy vấn.");
                    }
                    items = repository.timKiem(
                                    keyword, trangThai, tuNgay, denNgay, (int) offset, size)
                            .stream()
                            .map(promotion -> toResponse(promotion, now))
                            .toList();
                }

                long pageCount = total == 0 ? 0 : 1 + (total - 1) / size;
                int totalPages = Math.toIntExact(pageCount);
                KhuyenMaiDto.PageResponse result =
                        new KhuyenMaiDto.PageResponse(items, page, size, total, totalPages);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    /** Returns only promotions that are currently usable, for the public storefront. */
    public List<KhuyenMaiDto.Response> danhSachCongKhai() {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                LocalDateTime capturedNow = now();
                List<KhuyenMaiDto.Response> result = new KhuyenMaiRepository(em, capturedNow)
                        .dangHieuLuc(0, 100).stream()
                        .map(promotion -> toResponse(promotion, capturedNow))
                        .toList();
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public KhuyenMaiDto.Response chiTiet(String maKhuyenMai) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                String id = requiredCode(maKhuyenMai, "maKhuyenMai");
                KhuyenMai promotion = found(new KhuyenMaiRepository(em).find(id));
                KhuyenMaiDto.Response result = toResponse(promotion, now());
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public KhuyenMaiDto.Response tao(KhuyenMaiDto.Request request) {
        try {
            try (EntityManager em = factory.createEntityManager()) {
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                try {
                    AccessPolicy.guard(em);
                    if (request == null) {
                        throw AccountException.invalid("body", "Body không được để trống.");
                    }

                    PromotionValues values = validatedRequest(request, true, 0);
                    KhuyenMaiRepository repository = new KhuyenMaiRepository(em);
                    if (repository.find(values.maKhuyenMai()) != null) {
                        throw duplicateCode();
                    }

                    KhuyenMai promotion = new KhuyenMai();
                    apply(promotion, values);
                    promotion.soLuongDaDung = 0;
                    LocalDateTime now = now();
                    promotion.trangThai = promotion.xacDinhTrangThai(now);
                    repository.save(promotion);
                    em.flush();

                    KhuyenMaiDto.Response result = toResponse(promotion, now);
                    tx.commit();
                    return result;
                } catch (RuntimeException e) {
                    if (tx.isActive()) tx.rollback();
                    throw e;
                }
            }
        } catch (PersistenceException e) {
            if (isPostgresConstraint(e, "23505", PRIMARY_KEY)) throw duplicateCode();
            throw e;
        }
    }

    public KhuyenMaiDto.Response capNhat(
            String maKhuyenMai,
            KhuyenMaiDto.Request request) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                String id = requiredCode(maKhuyenMai, "maKhuyenMai");
                if (request == null) {
                    throw AccountException.invalid("body", "Body không được để trống.");
                }
                if (request.maKhuyenMai() != null) {
                    throw AccountException.invalid(
                            "maKhuyenMai", "Khi sửa, mã chỉ lấy từ URL.");
                }

                KhuyenMaiRepository repository = new KhuyenMaiRepository(em);
                KhuyenMai promotion = found(repository.lock(id));
                if (promotion.soLuongDaDung > 0 || repository.daDuocSuDung(id)) {
                    throw new AccountException(
                            409,
                            "CONFLICT",
                            "Không thể thay đổi khuyến mãi đã được dùng trong lịch sử đặt vé.");
                }

                PromotionValues values = validatedRequest(request, false, promotion.soLuongDaDung);
                apply(promotion, values);
                LocalDateTime now = now();
                promotion.trangThai = promotion.xacDinhTrangThai(now);

                KhuyenMaiDto.Response result = toResponse(promotion, now);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public void xoa(String maKhuyenMai) {
        try {
            try (EntityManager em = factory.createEntityManager()) {
                EntityTransaction tx = em.getTransaction();
                tx.begin();
                try {
                    AccessPolicy.guard(em);
                    String id = requiredCode(maKhuyenMai, "maKhuyenMai");
                    KhuyenMaiRepository repository = new KhuyenMaiRepository(em);
                    KhuyenMai promotion = found(repository.lock(id));
                    if (promotion.soLuongDaDung > 0 || repository.daDuocSuDung(id)) {
                        throw new AccountException(
                                409,
                                "INVALID_STATE_TRANSITION",
                                "Không thể xóa khuyến mãi đã được sử dụng.");
                    }

                    repository.delete(promotion);
                    em.flush();
                    tx.commit();
                } catch (RuntimeException e) {
                    if (tx.isActive()) tx.rollback();
                    throw e;
                }
            }
        } catch (PersistenceException e) {
            if (isPostgresConstraint(e, "23503", ORDER_PROMOTION_FOREIGN_KEY)) {
                throw new AccountException(
                        409,
                        "INVALID_STATE_TRANSITION",
                        "Không thể xóa khuyến mãi đã được sử dụng.");
            }
            throw e;
        }
    }

    public boolean kiemTraHieuLuc(KhuyenMai khuyenMai) {
        return khuyenMai != null && khuyenMai.kiemTraHieuLuc(now());
    }

    public boolean kiemTraDieuKien(KhuyenMai khuyenMai, BigDecimal tongTien) {
        return khuyenMai != null
                && isValidAmount(tongTien)
                && khuyenMai.kiemTraDieuKien(tongTien, now());
    }

    public BigDecimal tinhTienGiam(KhuyenMai khuyenMai, BigDecimal tongTien) {
        validateAmount(tongTien, "tongTien");
        if (khuyenMai == null) return ZERO;
        return khuyenMai.tinhTienGiam(tongTien, now());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), BUSINESS_ZONE);
    }

    private static void validatePage(int page, int size) {
        if (page < 1) {
            throw AccountException.invalid("page", "page phải từ 1.");
        }
        if (size < 1 || size > 100) {
            throw AccountException.invalid("size", "size phải từ 1 đến 100.");
        }
    }

    private static void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw AccountException.invalid("denNgay", "denNgay phải từ tuNgay trở đi.");
        }
    }

    private static String optionalQuery(String value, String field, int maxLength) {
        if (value == null) return null;
        String result = value.strip();
        if (result.length() > maxLength) {
            throw AccountException.invalid(field, "Trường này tối đa " + maxLength + " ký tự.");
        }
        return result.isEmpty() ? null : result;
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
            throw AccountException.invalid(field, "Trường này tối đa " + maxLength + " ký tự.");
        }
        return result;
    }

    private static PromotionValues validatedRequest(
            KhuyenMaiDto.Request request,
            boolean creating,
            int usedCount) {
        String id = creating
                ? requiredCode(request.maKhuyenMai(), "maKhuyenMai")
                : null;
        String name = requiredText(request.tenKhuyenMai(), "tenKhuyenMai", 160);
        String description = optionalText(request.moTa(), "moTa", 1000);
        LoaiGiam type = request.loaiGiam();
        if (type == null) {
            throw AccountException.invalid("loaiGiam", "Loại giảm là bắt buộc.");
        }

        BigDecimal discount = parseDiscount(request.giaTriGiam(), type);
        BigDecimal maxDiscount = parseOptionalPositiveInteger(request.giamToiDa(), "giamToiDa");
        BigDecimal minimumOrder = parseNonNegativeInteger(request.donToiThieu(), "donToiThieu");

        Integer quantity = request.soLuong();
        if (quantity == null || quantity < 0) {
            throw AccountException.invalid("soLuong", "Số lượng phải là số nguyên không âm.");
        }
        if (quantity < usedCount) {
            throw AccountException.invalid(
                    "soLuong", "Số lượng không được nhỏ hơn số lượt đã sử dụng.");
        }

        LocalDateTime startsAt = toBusinessLocalDateTime(request.ngayBatDau(), "ngayBatDau");
        LocalDateTime endsAt = toBusinessLocalDateTime(request.ngayKetThuc(), "ngayKetThuc");
        if (!endsAt.isAfter(startsAt)) {
            throw AccountException.invalid(
                    "ngayKetThuc", "ngayKetThuc phải sau ngayBatDau.");
        }

        return new PromotionValues(
                id, name, description, type, discount, maxDiscount, minimumOrder,
                quantity, startsAt, endsAt);
    }

    private static BigDecimal parseDiscount(JsonNode value, LoaiGiam type) {
        if (value == null || value.isNull()) {
            throw AccountException.invalid("giaTriGiam", "Giá trị giảm là bắt buộc.");
        }

        if (type == LoaiGiam.PHAN_TRAM) {
            if (!value.isNumber()) {
                throw AccountException.invalid(
                        "giaTriGiam", "Giảm theo phần trăm phải là JSON number.");
            }
            BigDecimal result = value.decimalValue();
            if (result.signum() <= 0 || result.compareTo(ONE_HUNDRED) > 0) {
                throw AccountException.invalid(
                        "giaTriGiam", "Phần trăm giảm phải lớn hơn 0 và không quá 100.");
            }
            if (effectiveScale(result) > 2) {
                throw AccountException.invalid(
                        "giaTriGiam", "Phần trăm giảm chỉ hỗ trợ tối đa 2 chữ số thập phân.");
            }
            return result;
        }

        if (!value.isTextual()) {
            throw AccountException.invalid(
                    "giaTriGiam", "Giảm số tiền cố định phải là chuỗi số nguyên VND.");
        }
        return parsePositiveInteger(value.textValue(), "giaTriGiam");
    }

    private static BigDecimal parseOptionalPositiveInteger(String value, String field) {
        return value == null ? null : parsePositiveInteger(value, field);
    }

    private static BigDecimal parsePositiveInteger(String value, String field) {
        if (value == null || !value.matches(POSITIVE_INTEGER) || value.length() > 12) {
            throw AccountException.invalid(
                    field, "Trường này phải là số nguyên VND dương, tối đa 12 chữ số.");
        }
        return new BigDecimal(value);
    }

    private static BigDecimal parseNonNegativeInteger(String value, String field) {
        if (value == null || !value.matches(NON_NEGATIVE_INTEGER) || value.length() > 12) {
            throw AccountException.invalid(
                    field, "Trường này phải là số nguyên VND không âm, tối đa 12 chữ số.");
        }
        return new BigDecimal(value);
    }

    private static LocalDateTime toBusinessLocalDateTime(OffsetDateTime value, String field) {
        if (value == null) {
            throw AccountException.invalid(field, "Thời điểm là bắt buộc.");
        }
        return value.atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
    }

    private static void validateAmount(BigDecimal amount, String field) {
        if (!isValidAmount(amount)) {
            throw AccountException.invalid(
                    field, "Số tiền phải không âm, tối đa 12 chữ số nguyên và 2 chữ số thập phân.");
        }
    }

    private static boolean isValidAmount(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) return false;
        BigDecimal normalized = amount.stripTrailingZeros();
        return Math.max(normalized.scale(), 0) <= 2
                && normalized.precision() - normalized.scale() <= 12;
    }

    private static int effectiveScale(BigDecimal amount) {
        return Math.max(0, amount.stripTrailingZeros().scale());
    }

    private static void apply(KhuyenMai promotion, PromotionValues values) {
        if (values.maKhuyenMai() != null) promotion.maKhuyenMai = values.maKhuyenMai();
        promotion.tenKhuyenMai = values.tenKhuyenMai();
        promotion.moTa = values.moTa();
        promotion.loaiGiam = values.loaiGiam();
        promotion.giaTriGiam = values.giaTriGiam();
        promotion.giaTriToiDa = values.giamToiDa();
        promotion.donToiThieu = values.donToiThieu();
        promotion.soLuong = values.soLuong();
        promotion.ngayBatDau = values.ngayBatDau();
        promotion.ngayKetThuc = values.ngayKetThuc();
    }

    private static KhuyenMai found(KhuyenMai promotion) {
        if (promotion == null) {
            throw new AccountException(404, "RESOURCE_NOT_FOUND", "Không tìm thấy khuyến mãi.");
        }
        return promotion;
    }

    private static KhuyenMaiDto.Response toResponse(KhuyenMai promotion, LocalDateTime now) {
        JsonNode discount = promotion.loaiGiam == LoaiGiam.PHAN_TRAM
                ? DecimalNode.valueOf(promotion.giaTriGiam)
                : TextNode.valueOf(vndString(promotion.giaTriGiam));
        return new KhuyenMaiDto.Response(
                promotion.maKhuyenMai,
                promotion.tenKhuyenMai,
                promotion.moTa,
                promotion.loaiGiam,
                discount,
                promotion.giaTriToiDa == null ? null : vndString(promotion.giaTriToiDa),
                vndString(promotion.donToiThieu),
                promotion.soLuong,
                toOffsetDateTime(promotion.ngayBatDau),
                toOffsetDateTime(promotion.ngayKetThuc),
                promotion.soLuongDaDung,
                promotion.xacDinhTrangThai(now));
    }

    private static String vndString(BigDecimal value) {
        return value.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    private static AccountException duplicateCode() {
        return new AccountException(409, "CONFLICT", "Mã khuyến mãi đã tồn tại.");
    }

    private static boolean isPostgresConstraint(
            Throwable error,
            String sqlState,
            String constraintName) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && sqlState.equals(violation.getSQLState())
                    && constraintName.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }

    private record PromotionValues(
            String maKhuyenMai,
            String tenKhuyenMai,
            String moTa,
            LoaiGiam loaiGiam,
            BigDecimal giaTriGiam,
            BigDecimal giamToiDa,
            BigDecimal donToiThieu,
            int soLuong,
            LocalDateTime ngayBatDau,
            LocalDateTime ngayKetThuc) {}
}
