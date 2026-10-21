package com.datvexe.service;

import com.datvexe.config.Jpa;
import com.datvexe.domain.Entities.KhuyenMai;
import com.datvexe.domain.States.LoaiGiam;
import com.datvexe.domain.States.TrangThaiKhuyenMai;
import com.datvexe.dto.KhuyenMaiDto;
import com.datvexe.repository.KhuyenMaiRepository;
import com.datvexe.security.AccessPolicy;
import com.datvexe.security.AccountException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/** Business rules and resource-local transactions for promotions. */
public final class KhuyenMaiService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_WHOLE_VND = new BigDecimal("999999999999");
    private static final String RESOURCE_CODE = "^[A-Za-z0-9_-]{1,40}$";

    private final EntityManagerFactory factory = Jpa.factory();

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
                if (page < 1) {
                    throw new DomainException(400, "page phải từ 1.");
                }
                if (size < 1 || size > 100) {
                    throw new DomainException(400, "size phải từ 1 đến 100.");
                }
                if (tuNgay != null && denNgay != null && tuNgay.isAfter(denNgay)) {
                    throw new DomainException(400, "denNgay phải từ tuNgay trở đi.");
                }

                String keyword = tuKhoa == null ? null : tuKhoa.strip();
                if (keyword != null && keyword.length() > 200) {
                    throw new DomainException(400, "tuKhoa tối đa 200 ký tự.");
                }
                if (keyword != null && keyword.isEmpty()) keyword = null;

                LocalDateTime capturedNow = now();
                KhuyenMaiRepository repository = new KhuyenMaiRepository(em, capturedNow);
                long total = repository.dem(keyword, trangThai, tuNgay, denNgay);
                long offset = (page - 1L) * size;

                // Response
                List<KhuyenMaiDto.Response> items;
                if (offset >= total) {
                    items = List.of();
                } else {
                    if (offset > Integer.MAX_VALUE) {
                        throw new DomainException(400, "Vị trí trang vượt quá giới hạn truy vấn.");
                    }
                    items = repository.timKiem(
                                    keyword, trangThai, tuNgay, denNgay, (int) offset, size)
                            .stream()
                            .map(promotion -> toResponse(promotion, capturedNow))
                            .toList();
                }

                // Page Response
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

    /** trả ds mã km công khai */
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
                String id = kiemTraMa(maKhuyenMai);
                KhuyenMai promotion = new KhuyenMaiRepository(em).find(id);
                if (promotion == null) {
                    throw new AccountException(
                            404, "RESOURCE_NOT_FOUND", "Không tìm thấy khuyến mãi.");
                }
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
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                if (request == null) {
                    throw new DomainException(400, "Body không được để trống.");
                }

                String id = kiemTraMa(request.maKhuyenMai());
                kiemTraDuLieu(request);

                KhuyenMaiRepository repository = new KhuyenMaiRepository(em);
                if (repository.find(id) != null) {
                    throw new AccountException(409, "CONFLICT", "Mã khuyến mãi đã tồn tại.");
                }

                KhuyenMai promotion = new KhuyenMai();
                promotion.maKhuyenMai = id;
                ganDuLieu(promotion, request);
                promotion.soLuongDaDung = 0;
                LocalDateTime capturedNow = now();
                promotion.trangThai = promotion.xacDinhTrangThai(capturedNow);
                repository.save(promotion);

                KhuyenMaiDto.Response result = toResponse(promotion, capturedNow);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
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
                String id = kiemTraMa(maKhuyenMai);
                if (request == null) {
                    throw new DomainException(400, "Body không được để trống.");
                }
                if (request.maKhuyenMai() != null) {
                    throw new DomainException(400, "Khi sửa, mã chỉ lấy từ URL.");
                }

                KhuyenMaiRepository repository = new KhuyenMaiRepository(em);
                KhuyenMai promotion = repository.lock(id);
                if (promotion == null) {
                    throw new AccountException(
                            404, "RESOURCE_NOT_FOUND", "Không tìm thấy khuyến mãi.");
                }
                if (promotion.soLuongDaDung > 0 || repository.daDuocSuDung(id)) {
                    throw new AccountException(
                            409,
                            "CONFLICT",
                            "Không thể thay đổi khuyến mãi đã được dùng trong lịch sử đặt vé.");
                }

                kiemTraDuLieu(request);
                ganDuLieu(promotion, request);
                LocalDateTime capturedNow = now();
                promotion.trangThai = promotion.xacDinhTrangThai(capturedNow);

                KhuyenMaiDto.Response result = toResponse(promotion, capturedNow);
                tx.commit();
                return result;
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    public void xoa(String maKhuyenMai) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try {
                AccessPolicy.guard(em);
                String id = kiemTraMa(maKhuyenMai);
                KhuyenMaiRepository repository = new KhuyenMaiRepository(em);
                KhuyenMai promotion = repository.lock(id);
                if (promotion == null) {
                    throw new AccountException(
                            404, "RESOURCE_NOT_FOUND", "Không tìm thấy khuyến mãi.");
                }
                if (promotion.soLuongDaDung > 0 || repository.daDuocSuDung(id)) {
                    throw new AccountException(
                            409,
                            "INVALID_STATE_TRANSITION",
                            "Không thể xóa khuyến mãi đã được sử dụng.");
                }

                repository.delete(promotion);
                tx.commit();
            } catch (RuntimeException e) {
                if (tx.isActive()) tx.rollback();
                throw e;
            }
        }
    }

    private static String kiemTraMa(String value) {
        if (value == null || value.strip().isEmpty()) {
            throw new DomainException(400, "Mã khuyến mãi là bắt buộc.");
        }
        if (!value.matches(RESOURCE_CODE)) {
            throw new DomainException(
                    400,
                    "Mã khuyến mãi chỉ được gồm chữ cái ASCII, chữ số, dấu gạch dưới hoặc dấu gạch ngang, "
                            + "dài từ 1 đến 40 ký tự.");
        }
        return value;
    }

    private static void kiemTraDuLieu(KhuyenMaiDto.Request request) {
        String name = request.tenKhuyenMai();
        if (name == null || name.strip().isEmpty()) {
            throw new DomainException(400, "tenKhuyenMai là bắt buộc.");
        }
        if (name.strip().length() > 160) {
            throw new DomainException(400, "tenKhuyenMai tối đa 160 ký tự.");
        }

        String description = request.moTa();
        if (description != null && description.strip().length() > 1000) {
            throw new DomainException(400, "moTa tối đa 1000 ký tự.");
        }

        LoaiGiam type = request.loaiGiam();
        if (type == null) {
            throw new DomainException(400, "Loại giảm là bắt buộc.");
        }

        BigDecimal discount = request.giaTriGiam();
        if (discount == null || discount.signum() <= 0) {
            throw new DomainException(400, "giaTriGiam phải lớn hơn 0.");
        }
        if (type == LoaiGiam.PHAN_TRAM) {
            if (discount.compareTo(ONE_HUNDRED) > 0) {
                throw new DomainException(400, "Phần trăm giảm không được quá 100.");
            }
            if (discount.stripTrailingZeros().scale() > 2) {
                throw new DomainException(400, "Phần trăm giảm chỉ hỗ trợ tối đa 2 chữ số thập phân.");
            }
        } else if (discount.stripTrailingZeros().scale() > 0
                || discount.compareTo(MAX_WHOLE_VND) > 0) {
            throw new DomainException(
                    400, "Giảm số tiền cố định phải là số nguyên VND dương, tối đa 12 chữ số.");
        }

        BigDecimal maxDiscount = request.giamToiDa();
        if (maxDiscount != null
                && (maxDiscount.signum() <= 0
                        || maxDiscount.stripTrailingZeros().scale() > 0
                        || maxDiscount.compareTo(MAX_WHOLE_VND) > 0)) {
            throw new DomainException(
                    400, "giamToiDa phải là số nguyên VND dương, tối đa 12 chữ số.");
        }

        BigDecimal minimumOrder = request.donToiThieu();
        if (minimumOrder == null
                || minimumOrder.signum() < 0
                || minimumOrder.stripTrailingZeros().scale() > 0
                || minimumOrder.compareTo(MAX_WHOLE_VND) > 0) {
            throw new DomainException(
                    400, "donToiThieu phải là số nguyên VND không âm, tối đa 12 chữ số.");
        }

        if (request.soLuong() == null || request.soLuong() < 0) {
            throw new DomainException(400, "soLuong phải là số nguyên không âm.");
        }
        if (request.ngayBatDau() == null) {
            throw new DomainException(400, "ngayBatDau là bắt buộc.");
        }
        if (request.ngayKetThuc() == null) {
            throw new DomainException(400, "ngayKetThuc là bắt buộc.");
        }
        if (!request.ngayKetThuc().isAfter(request.ngayBatDau())) {
            throw new DomainException(400, "ngayKetThuc phải sau ngayBatDau.");
        }
    }

    private static void ganDuLieu(KhuyenMai promotion, KhuyenMaiDto.Request request) {
        promotion.tenKhuyenMai = request.tenKhuyenMai().strip();
        promotion.moTa = request.moTa() == null ? null : request.moTa().strip();
        promotion.loaiGiam = request.loaiGiam();
        promotion.giaTriGiam = request.loaiGiam() == LoaiGiam.PHAN_TRAM
                ? request.giaTriGiam()
                : request.giaTriGiam().setScale(0);
        promotion.giaTriToiDa = request.giamToiDa() == null
                ? null
                : request.giamToiDa().setScale(0);
        promotion.donToiThieu = request.donToiThieu().setScale(0);
        promotion.soLuong = request.soLuong();
        promotion.ngayBatDau = request.ngayBatDau()
                .atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
        promotion.ngayKetThuc = request.ngayKetThuc()
                .atZoneSameInstant(BUSINESS_ZONE).toLocalDateTime();
    }

    private static KhuyenMaiDto.Response toResponse(KhuyenMai promotion, LocalDateTime now) {
        return new KhuyenMaiDto.Response(
                promotion.maKhuyenMai,
                promotion.tenKhuyenMai,
                promotion.moTa,
                promotion.loaiGiam,
                promotion.giaTriGiam,
                promotion.giaTriToiDa,
                promotion.donToiThieu,
                promotion.soLuong,
                promotion.ngayBatDau.atZone(BUSINESS_ZONE).toOffsetDateTime(),
                promotion.ngayKetThuc.atZone(BUSINESS_ZONE).toOffsetDateTime(),
                promotion.soLuongDaDung,
                promotion.xacDinhTrangThai(now));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(BUSINESS_ZONE);
    }
}
