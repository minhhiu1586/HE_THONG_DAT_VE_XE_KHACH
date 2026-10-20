package com.datvexe.service;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.ReviewInputs;
import com.datvexe.repository.ReviewRepository;
import com.datvexe.security.*;
import com.datvexe.validation.ReviewValidation;

import jakarta.persistence.*;

import java.time.*;
import java.util.*;
import java.util.function.Function;

public final class ReviewService {
    private final EntityManagerFactory factory;
    private final Clock clock;

    public ReviewService(EntityManagerFactory factory) {
        this(factory, Clock.systemUTC());
    }

    public ReviewService(EntityManagerFactory factory, Clock clock) {
        this.factory = factory;
        this.clock = clock;
    }

    private <T> T tx(Function<EntityManager, T> action) {
        try (var em = factory.createEntityManager()) {
            var t = em.getTransaction();
            t.begin();
            try {
                AccessPolicy.guard(em);
                T result = action.apply(em);
                t.commit();
                return result;
            } catch (RuntimeException e) {
                if (t.isActive()) t.rollback();
                throw e;
            }
        }
    }

    private NguoiDung caller(EntityManager em) {
        var request = RequestIdentity.get();
        return AccessControl.authenticated(
                em, request == null ? null : request.user(), clock.instant());
    }

    private static AccountException error(int status, String code, String message) {
        return new AccountException(status, code, message);
    }

    private static void eligible(EntityManager em, NguoiDung user, ChuyenXe trip) {
        if (trip.trangThai != TrangThaiChuyen.HOAN_TAT)
            throw error(409, "TRIP_NOT_COMPLETED", "Chỉ đánh giá chuyến đã hoàn tất.");
        if (!new ReviewRepository(em).hasEligibleTicket(user.maNguoiDung, trip.maChuyen))
            throw error(
                    403,
                    "REVIEW_NOT_ELIGIBLE",
                    "Bạn chưa có vé đã thanh toán còn hợp lệ trên chuyến này.");
    }

    public Map<String, Object> eligibility(String tripId) {
        return tx(
                em -> {
                    var user = caller(em);
                    AccessControl.role(user, LoaiTaiKhoan.KHACH_HANG);
                    var trip =
                            AccessControl.resource(
                                    em, ChuyenXe.class, ReviewValidation.resourceId(tripId));
                    var existing =
                            new ReviewRepository(em)
                                    .forAccountTrip(user.maNguoiDung, trip.maChuyen);
                    boolean allowed = true;
                    String reason = "";
                    try {
                        eligible(em, user, trip);
                    } catch (AccountException e) {
                        allowed = false;
                        reason = e.getMessage();
                    }
                    boolean canEdit = allowed && existing != null && !existing.daXoa;
                    if (existing != null) {
                        allowed = false;
                        reason =
                                existing.daXoa
                                        ? "Đánh giá đã xóa; không tạo lại đánh giá cho cùng chuyến."
                                        : "Bạn đã đánh giá chuyến này.";
                    }
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("coTheDanhGia", allowed);
                    result.put("coTheSua", canEdit);
                    result.put("lyDo", reason);
                    result.put(
                            "danhGia",
                            existing == null ? null : ReviewMapper.view(existing, false));
                    return result;
                });
    }

    public Map<String, Object> create(ReviewInputs.Create input) {
        return tx(
                em -> {
                    var user = caller(em);
                    AccessControl.role(user, LoaiTaiKhoan.KHACH_HANG);
                    if (input == null)
                        throw error(400, "INVALID_REVIEW_CONTENT", "Thiếu nội dung đánh giá.");
                    String text = ReviewValidation.content(input.soSao(), input.binhLuan());
                    var trip =
                            AccessControl.resource(
                                    em,
                                    ChuyenXe.class,
                                    ReviewValidation.resourceId(input.maChuyen()));
                    eligible(em, user, trip);
                    if (new ReviewRepository(em).forAccountTrip(user.maNguoiDung, trip.maChuyen)
                            != null)
                        throw error(
                                409,
                                "REVIEW_ALREADY_EXISTS",
                                "Mỗi tài khoản chỉ được đánh giá một lần cho mỗi chuyến.");
                    var review = new DanhGia();
                    review.maDanhGia = "DG-" + UUID.randomUUID();
                    review.nguoiDung = user;
                    review.chuyenXe = trip;
                    review.soSao = input.soSao();
                    review.binhLuan = text;
                    review.ngayDanhGia =
                            LocalDateTime.ofInstant(clock.instant(), TripSaleRules.ZONE);
                    em.persist(review);
                    em.flush();
                    audit(em, review, user, "TAO", null, null, null, null);
                    return ReviewMapper.view(review, false);
                });
    }

    private DanhGia lockedReview(EntityManager em, String id) {
        var initial = em.find(DanhGia.class, ReviewValidation.resourceId(id));
        if (initial == null) throw error(404, "REVIEW_NOT_FOUND", "Không tìm thấy đánh giá.");
        AccessControl.resource(em, ChuyenXe.class, initial.chuyenXe.maChuyen);
        return AccessControl.resource(em, DanhGia.class, id);
    }

    private static void owner(NguoiDung user, DanhGia review) {
        AccessControl.role(user, LoaiTaiKhoan.KHACH_HANG);
        if (!review.nguoiDung.maNguoiDung.equals(user.maNguoiDung)) throw AccessControl.denied();
    }

    private static void moderator(EntityManager em, NguoiDung user, DanhGia review) {
        AccessControl.role(user, LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE);
        AccessControl.operator(user, review.chuyenXe.tuyenXe.nhaXe);
        AccessControl.activeOperator(em, user);
    }

    public Map<String, Object> edit(String id, ReviewInputs.Edit input) {
        return tx(
                em -> {
                    var user = caller(em);
                    var review = lockedReview(em, id);
                    owner(user, review);
                    if (review.daXoa)
                        throw error(409, "REVIEW_DELETED", "Đánh giá đã xóa không thể sửa.");
                    if (input == null)
                        throw error(400, "INVALID_REVIEW_CONTENT", "Thiếu nội dung đánh giá.");
                    String text = ReviewValidation.content(input.soSao(), input.binhLuan());
                    eligible(em, user, review.chuyenXe);
                    if (review.soSao == input.soSao() && Objects.equals(review.binhLuan, text))
                        return ReviewMapper.view(review, false);
                    var before = review.trangThai;
                    int oldStars = review.soSao;
                    String oldText = review.binhLuan;
                    review.soSao = input.soSao();
                    review.binhLuan = text;
                    review.trangThai = TrangThaiDanhGia.CHO_DUYET;
                    audit(em, review, user, "SUA", before, oldStars, oldText, null);
                    return ReviewMapper.view(review, false);
                });
    }

    public Map<String, Object> moderate(String id, boolean approve, String reason) {
        return tx(
                em -> {
                    var user = caller(em);
                    var review = lockedReview(em, id);
                    moderator(em, user, review);
                    if (review.daXoa)
                        throw error(409, "REVIEW_DELETED", "Đánh giá đã xóa không thể kiểm duyệt.");
                    if (!approve && (reason == null || reason.isBlank()))
                        throw error(
                                400, "REVIEW_REASON_REQUIRED", "Vui lòng nhập lý do ẩn đánh giá.");
                    if (reason != null && reason.length() > 1000)
                        throw error(
                                400,
                                "INVALID_REVIEW_CONTENT",
                                "Lý do không được vượt quá 1000 ký tự.");
                    ReviewValidation.text(reason);
                    var target = approve ? TrangThaiDanhGia.DA_DUYET : TrangThaiDanhGia.AN;
                    if (review.trangThai == target) return ReviewMapper.view(review, false);
                    var before = review.trangThai;
                    review.trangThai = target;
                    audit(
                            em,
                            review,
                            user,
                            approve ? "DUYET" : "AN",
                            before,
                            review.soSao,
                            review.binhLuan,
                            reason);
                    return ReviewMapper.view(review, false);
                });
    }

    public Map<String, Object> delete(String id) {
        return tx(
                em -> {
                    var user = caller(em);
                    var review = lockedReview(em, id);
                    if (user.loaiTaiKhoan == LoaiTaiKhoan.KHACH_HANG) owner(user, review);
                    else moderator(em, user, review);
                    if (!review.daXoa) {
                        review.daXoa = true;
                        audit(
                                em,
                                review,
                                user,
                                "XOA_MEM",
                                review.trangThai,
                                review.soSao,
                                review.binhLuan,
                                null);
                    }
                    return ReviewMapper.view(review, false);
                });
    }

    public Map<String, Object> publicReviews(String tripId, int page, int size) {
        return tx(
                em -> {
                    ReviewValidation.pagination(page, size);
                    var trip =
                            AccessControl.resource(
                                    em, ChuyenXe.class, ReviewValidation.resourceId(tripId));
                    var repository = new ReviewRepository(em);
                    Object[] summary = repository.summary(trip);
                    var rows = repository.publicPage(trip, page, size);
                    Map<String, Object> result =
                            new LinkedHashMap<>(
                                    ReviewMapper.page(
                                            rows,
                                            page,
                                            size,
                                            ((Number) summary[0]).longValue(),
                                            true));
                    result.put("rating", summary[1]);
                    return result;
                });
    }

    public Map<String, Object> management(String tripId, String state, int page, int size) {
        return tx(
                em -> {
                    var user = caller(em);
                    AccessControl.role(user, LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE);
                    AccessControl.activeOperator(em, user);
                    ReviewValidation.pagination(page, size);
                    NhaXe owner = null;
                    if (user.loaiTaiKhoan == LoaiTaiKhoan.NHA_XE) {
                        AccessControl.operator(user, user.nhaXe);
                        owner = user.nhaXe;
                    }
                    String trip =
                            tripId == null || tripId.isBlank()
                                    ? null
                                    : ReviewValidation.resourceId(tripId);
                    var result =
                            new ReviewRepository(em).management(owner, trip, state, page, size);
                    return ReviewMapper.page(result.items(), page, size, result.total(), false);
                });
    }

    public List<Map<String, Object>> history(String id) {
        return tx(
                em -> {
                    var user = caller(em);
                    var review = lockedReview(em, id);
                    moderator(em, user, review);
                    List<Object[]> rows = new ReviewRepository(em).history(id);
                    return rows.stream()
                            .map(
                                    r ->
                                            Map.<String, Object>of(
                                                    "maNguoiThaoTac",
                                                    r[0],
                                                    "thaoTac",
                                                    r[1],
                                                    "trangThaiCu",
                                                    Objects.toString(r[2], ""),
                                                    "trangThaiMoi",
                                                    r[3],
                                                    "lyDo",
                                                    Objects.toString(r[4], ""),
                                                    "thoiDiem",
                                                    r[5].toString()))
                            .toList();
                });
    }

    private void audit(
            EntityManager em,
            DanhGia review,
            NguoiDung actor,
            String action,
            TrangThaiDanhGia before,
            Integer stars,
            String text,
            String reason) {
        new ReviewRepository(em)
                .audit(review, actor, action, before, stars, text, reason, clock.instant());
    }
}
