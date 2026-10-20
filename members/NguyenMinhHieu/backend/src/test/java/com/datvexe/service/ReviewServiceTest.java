package com.datvexe.service;

import static org.junit.jupiter.api.Assertions.*;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.ReviewInputs;
import com.datvexe.security.*;

import jakarta.persistence.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

class ReviewServiceTest {
    EntityManagerFactory factory;
    ReviewService reviews;

    @BeforeEach
    void setup() {
        factory = createFactory();
        reviews = new ReviewService(factory);
        tx(
                em -> {
                    em.createNativeQuery(
                                    "create table review_history(id varchar(40) primary"
                                        + " key,ma_danh_gia varchar(40),ma_nguoi_thao_tac"
                                        + " varchar(40),thao_tac varchar(24),trang_thai_cu"
                                        + " varchar(30),trang_thai_moi varchar(30),so_sao_cu"
                                        + " integer,so_sao_moi integer,binh_luan_cu"
                                        + " varchar(2000),binh_luan_moi varchar(2000),ly_do"
                                        + " varchar(1000),created_at timestamp with time zone)")
                            .executeUpdate();
                    for (String code : List.of("A", "B")) {
                        var n = new NhaXe();
                        n.maNhaXe = code;
                        n.tenNhaXe = code;
                        em.persist(n);
                    }
                    for (String id : List.of("U", "V", "AD", "NX", "STAFF")) {
                        var u = new NguoiDung();
                        u.maNguoiDung = id;
                        u.hoTen = id;
                        u.email = id + "@test.local";
                        u.soDienThoai = id;
                        u.matKhau = "test";
                        u.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
                        u.loaiTaiKhoan =
                                id.equals("AD")
                                        ? LoaiTaiKhoan.ADMIN
                                        : id.equals("NX")
                                                ? LoaiTaiKhoan.NHA_XE
                                                : id.equals("STAFF")
                                                        ? LoaiTaiKhoan.NHAN_VIEN
                                                        : LoaiTaiKhoan.KHACH_HANG;
                        if (id.equals("NX")) u.nhaXe = em.find(NhaXe.class, "B");
                        em.persist(u);
                        var auth = new AuthSession();
                        auth.tokenHash = id;
                        auth.csrfToken = id;
                        auth.nguoiDung = u;
                        auth.taoLuc = Instant.now();
                        auth.hetHan = Instant.now().plusSeconds(3600);
                        em.persist(auth);
                    }
                    var city = new TinhThanh();
                    city.maTinhThanh = "CITY";
                    city.tenTinhThanh = "City";
                    em.persist(city);
                    var s = new BenXe();
                    s.maBenXe = "S";
                    s.tenBenXe = "S";
                    s.diaChi = "S";
                    s.tinhThanh = city;
                    em.persist(s);
                    var r = new TuyenXe();
                    r.maTuyen = "R";
                    r.tenTuyen = "Route";
                    r.tramKhoiHanh = s;
                    r.tramDen = s;
                    r.nhaXe = em.find(NhaXe.class, "A");
                    r.giaCoBan = BigDecimal.TEN;
                    em.persist(r);
                    var x = new Xe();
                    x.maXe = "X";
                    x.bienSo = "51B-TEST";
                    x.soChoNgoi = 4;
                    em.persist(x);
                    for (String id : List.of("C", "D")) {
                        var c = new ChuyenXe();
                        c.maChuyen = id;
                        c.tuyenXe = r;
                        c.xe = x;
                        c.ngayKhoiHanh = LocalDate.now().minusDays(1);
                        c.gioKhoiHanh = LocalTime.of(8, 0);
                        c.gioDenDuKien = c.ngayKhoiHanh.atTime(10, 0);
                        c.trangThai = TrangThaiChuyen.HOAN_TAT;
                        em.persist(c);
                    }
                    var method = new PhuongThucThanhToan();
                    method.maPhuongThuc = "VNPAY";
                    method.tenPhuongThuc = "VNPay";
                    em.persist(method);
                });
        ticket("T1", "O1", "U", "C", TrangThaiVe.DA_XUAT_VE);
    }

    EntityManagerFactory createFactory() {
        return Persistence.createEntityManagerFactory(
                "datvexe",
                Map.of(
                        "jakarta.persistence.jdbc.driver",
                        "org.h2.Driver",
                        "jakarta.persistence.jdbc.url",
                        "jdbc:h2:mem:review_" + UUID.randomUUID() + ";MODE=PostgreSQL",
                        "jakarta.persistence.jdbc.user",
                        "sa",
                        "jakarta.persistence.jdbc.password",
                        "",
                        "hibernate.hbm2ddl.auto",
                        "create-drop"));
    }

    @AfterEach
    void close() {
        RequestIdentity.clear();
        factory.close();
    }

    void tx(Consumer<EntityManager> action) {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            action.accept(em);
            em.getTransaction().commit();
        }
    }

    void ticket(String id, String orderId, String user, String trip, TrangThaiVe state) {
        tx(
                em -> {
                    var order = em.find(DonDatVe.class, orderId);
                    if (order == null) {
                        order = new DonDatVe();
                        order.maDon = orderId;
                        order.nguoiDat = em.find(NguoiDung.class, user);
                        order.ngayDat = LocalDateTime.now();
                        order.khachDatTen = "Passenger";
                        order.khachDatSoDienThoai = "0900000000";
                        order.trangThai = TrangThaiDon.DA_THANH_TOAN;
                        em.persist(order);
                        var payment = new ThanhToan();
                        payment.maGiaoDich = "P" + orderId;
                        payment.donDatVe = order;
                        payment.phuongThucThanhToan = em.find(PhuongThucThanhToan.class, "VNPAY");
                        payment.soTien = BigDecimal.TEN;
                        payment.trangThai = TrangThaiThanhToan.THANH_CONG;
                        em.persist(payment);
                    }
                    var seat = new Ghe();
                    seat.maGhe = "G" + id;
                    seat.viTri = id;
                    seat.xe = em.find(Xe.class, "X");
                    em.persist(seat);
                    var v = new VeXe();
                    v.maVe = id;
                    v.chuyenXe = em.find(ChuyenXe.class, trip);
                    v.donDatVe = order;
                    v.ghe = seat;
                    v.giaVe = BigDecimal.TEN;
                    v.hoTenKhachHang = "Passenger";
                    v.soDienThoaiKhachHang = "0900000000";
                    v.trangThai = state;
                    em.persist(v);
                });
    }

    void identity(String user, String method, String path) {
        var role =
                user.equals("AD")
                        ? LoaiTaiKhoan.ADMIN
                        : user.equals("NX")
                                ? LoaiTaiKhoan.NHA_XE
                                : user.equals("STAFF")
                                        ? LoaiTaiKhoan.NHAN_VIEN
                                        : LoaiTaiKhoan.KHACH_HANG;
        RequestIdentity.set(
                new RequestIdentity.Context(
                        new CurrentUser(user, role, user.equals("NX") ? "B" : null, null, user),
                        method,
                        path,
                        null));
    }

    Map<String, Object> create(String user, int stars) {
        identity(user, "POST", "/api/danh-gia");
        return reviews.create(new ReviewInputs.Create("C", stars, "Nhận xét"));
    }

    Map<String, Object> publicData() {
        var before = RequestIdentity.get();
        RequestIdentity.clear();
        try {
            return reviews.publicReviews("C", 1, 10);
        } finally {
            RequestIdentity.set(before);
        }
    }

    long total() {
        return ((Number) publicData().get("totalItems")).longValue();
    }

    void approve(String id) {
        identity("AD", "POST", "/api/danh-gia/" + id + "/duyet");
        reviews.moderate(id, true, null);
    }

    @Test
    void completedTripWithPaidTicketCreatesPendingAndPublicDataHasNoPrivateDetails() {
        String id = create("U", 5).get("maDanhGia").toString();
        assertEquals(0, total());
        assertNull(publicData().get("rating"));
        approve(id);
        assertEquals(1, total());
        assertEquals(5.0, ((Number) publicData().get("rating")).doubleValue());
        var json = publicData().toString();
        assertFalse(json.contains("@test.local"));
        assertFalse(json.contains("maNguoiDung"));
        assertFalse(json.contains("maNguoiThaoTac"));
        assertFalse(json.contains("0900000000"));
    }

    @Test
    void anonymousAndNonOwnerAndStaffCannotCreate() {
        RequestIdentity.set(new RequestIdentity.Context(null, "POST", "/api/danh-gia", null));
        assertEquals(
                401,
                assertThrows(
                                AccountException.class,
                                () -> reviews.create(new ReviewInputs.Create("C", 5, "")))
                        .status());
        assertEquals(403, assertThrows(AccountException.class, () -> create("V", 5)).status());
        assertEquals(403, assertThrows(AccountException.class, () -> create("STAFF", 5)).status());
    }

    @ParameterizedTest
    @EnumSource(
            value = TrangThaiChuyen.class,
            names = {"HOAN_TAT"},
            mode = EnumSource.Mode.EXCLUDE)
    void incompleteAndCanceledTripsCannotBeReviewed(TrangThaiChuyen state) {
        tx(em -> em.find(ChuyenXe.class, "C").trangThai = state);
        assertEquals(409, assertThrows(AccountException.class, () -> create("U", 5)).status());
    }

    @ParameterizedTest
    @EnumSource(
            value = TrangThaiDon.class,
            names = {"DA_THANH_TOAN"},
            mode = EnumSource.Mode.EXCLUDE)
    void unpaidOrdersCannotGrantReviewRights(TrangThaiDon state) {
        tx(em -> em.find(DonDatVe.class, "O1").trangThai = state);
        assertEquals(403, assertThrows(AccountException.class, () -> create("U", 5)).status());
    }

    @ParameterizedTest
    @EnumSource(
            value = TrangThaiVe.class,
            names = {"DA_HOAN", "DA_HUY"})
    void allRefundedOrCanceledTicketsAreRejected(TrangThaiVe state) {
        tx(em -> em.find(VeXe.class, "T1").trangThai = state);
        assertEquals(403, assertThrows(AccountException.class, () -> create("U", 5)).status());
    }

    @Test
    void holdsWithoutIssuedTicketsCannotGrantReviewRights() {
        tx(em -> em.remove(em.find(VeXe.class, "T1")));
        assertEquals(403, assertThrows(AccountException.class, () -> create("U", 5)).status());
    }

    @Test
    void mixedTicketsAndMultipleOrdersStillGrantExactlyOneReview() {
        ticket("T2", "O2", "U", "C", TrangThaiVe.DA_HOAN);
        ticket("T3", "O3", "U", "C", TrangThaiVe.DA_LEN_XE);
        create("U", 4);
        assertEquals(409, assertThrows(AccountException.class, () -> create("U", 4)).status());
        tx(
                em ->
                        assertEquals(
                                1L,
                                em.createQuery("select count(d) from DanhGia d", Long.class)
                                        .getSingleResult()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1})
    void invalidStarsAreRejected(int stars) {
        assertEquals(400, assertThrows(AccountException.class, () -> create("U", stars)).status());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5})
    void boundaryStarsAreValid(int stars) {
        assertEquals(stars, create("U", stars).get("soSao"));
    }

    @Test
    void overlongContentIsRejectedAndScriptRemainsPlainText() {
        identity("U", "POST", "/api/danh-gia");
        assertThrows(
                AccountException.class,
                () -> reviews.create(new ReviewInputs.Create("C", 5, "x".repeat(2001))));
        var result = reviews.create(new ReviewInputs.Create("C", 5, "<script>alert(1)</script>"));
        assertEquals("<script>alert(1)</script>", result.get("binhLuan"));
    }

    @Test
    void editingApprovedReviewRemovesRatingAndOnlyOwnerCanEdit() {
        String id = create("U", 5).get("maDanhGia").toString();
        approve(id);
        identity("V", "PUT", "/api/danh-gia/" + id);
        assertEquals(
                403,
                assertThrows(
                                AccountException.class,
                                () -> reviews.edit(id, new ReviewInputs.Edit(1, "")))
                        .status());
        identity("U", "PUT", "/api/danh-gia/" + id);
        assertEquals(
                "CHO_DUYET",
                reviews.edit(id, new ReviewInputs.Edit(4, "Sửa nhận xét")).get("trangThai"));
        assertEquals(0, total());
        approve(id);
        assertEquals(4.0, ((Number) publicData().get("rating")).doubleValue());
    }

    @Test
    void approvalAndHideReplayDoNotChangeRatingTwice() {
        String a = create("U", 5).get("maDanhGia").toString();
        ticket("T2", "O2", "V", "C", TrangThaiVe.DA_XUAT_VE);
        String b = create("V", 4).get("maDanhGia").toString();
        approve(a);
        approve(b);
        approve(a);
        assertEquals(4.5, ((Number) publicData().get("rating")).doubleValue());
        identity("AD", "POST", "/api/danh-gia/" + a + "/an");
        reviews.moderate(a, false, "Không phù hợp");
        reviews.moderate(a, false, "Không phù hợp");
        assertEquals(4.0, ((Number) publicData().get("rating")).doubleValue());
        assertEquals(1, total());
    }

    @Test
    void softDeletionPreservesUniquenessAndRejectsModeration() {
        String id = create("U", 5).get("maDanhGia").toString();
        approve(id);
        identity("U", "DELETE", "/api/danh-gia/" + id);
        reviews.delete(id);
        reviews.delete(id);
        assertEquals(0, total());
        assertThrows(AccountException.class, () -> create("U", 5));
        identity("U", "PUT", "/api/danh-gia/" + id);
        assertThrows(AccountException.class, () -> reviews.edit(id, new ReviewInputs.Edit(3, "")));
        identity("AD", "POST", "/api/danh-gia/" + id + "/duyet");
        assertThrows(AccountException.class, () -> reviews.moderate(id, true, null));
    }

    @Test
    void unauthorizedAndOtherOperatorCannotModerate() {
        String id = create("U", 5).get("maDanhGia").toString();
        identity("U", "POST", "/api/danh-gia/" + id + "/duyet");
        assertEquals(
                403,
                assertThrows(AccountException.class, () -> reviews.moderate(id, true, null))
                        .status());
        identity("NX", "POST", "/api/danh-gia/" + id + "/duyet");
        assertEquals(
                403,
                assertThrows(AccountException.class, () -> reviews.moderate(id, true, null))
                        .status());
        identity("NX", "GET", "/api/quan-ly/danh-gia");
        assertEquals(0L, reviews.management(null, null, 1, 20).get("totalItems"));
    }

    @Test
    void databaseAuditFailureRollsBackModerationAndRating() {
        String id = create("U", 5).get("maDanhGia").toString();
        tx(em -> em.createNativeQuery("drop table review_history").executeUpdate());
        identity("AD", "POST", "/api/danh-gia/" + id + "/duyet");
        assertThrows(PersistenceException.class, () -> reviews.moderate(id, true, null));
        assertEquals(0, total());
        tx(em -> assertEquals(TrangThaiDanhGia.CHO_DUYET, em.find(DanhGia.class, id).trangThai));
    }

    @Test
    void approvedReviewReturnsToPendingAfterEditAndImmutableFieldsAreRejectedByDto()
            throws Exception {
        var field = com.datvexe.controller.ReviewController.class.getDeclaredField("json");
        field.setAccessible(true);
        var json =
                (com.fasterxml.jackson.databind.ObjectMapper)
                        field.get(new com.datvexe.controller.ReviewController());
        for (String invalid :
                List.of(
                        "{\"soSao\":2.5}",
                        "{\"soSao\":\"5\"}",
                        "{\"soSao\":true}",
                        "{\"soSao\":5,\"maChuyen\":\"D\"}",
                        "{\"soSao\":5,\"maNguoiDung\":\"V\"}",
                        "{\"soSao\":5,\"trangThai\":\"DA_DUYET\"}"))
            assertThrows(
                    com.fasterxml.jackson.core.JsonProcessingException.class,
                    () -> json.readValue(invalid, ReviewInputs.Edit.class));
        identity("U", "POST", "/api/danh-gia");
        assertThrows(
                AccountException.class,
                () -> reviews.create(new ReviewInputs.Create("C", null, "")));
    }

    @Test
    void completedTripRequiresCapturedPaymentNotJustPaidOrderLabel() {
        tx(em -> em.find(ThanhToan.class, "PO1").trangThai = TrangThaiThanhToan.CHO_XU_LY);
        assertEquals(403, assertThrows(AccountException.class, () -> create("U", 5)).status());
    }

    @Test
    void t21CompletionGrantsReviewWithoutInventingBoardingEvents() {
        tx(
                em -> {
                    em.createNativeQuery(
                                    "create table trip_lifecycle_history(id varchar(40),ma_chuyen"
                                        + " varchar(40),trang_thai_cu varchar(30),trang_thai_moi"
                                        + " varchar(30),ma_nguoi_thao_tac varchar(40),vai_tro"
                                        + " varchar(30),ly_do varchar(1000),created_at timestamp"
                                        + " with time zone)")
                            .executeUpdate();
                    em.find(ChuyenXe.class, "C").trangThai = TrangThaiChuyen.DANG_CHAY;
                });
        assertEquals(409, assertThrows(AccountException.class, () -> create("U", 5)).status());
        identity("AD", "POST", "/api/quan-ly/chuyen-xe/C/hoan-thanh");
        new TripService(factory).transition("C", TrangThaiChuyen.HOAN_TAT, null);
        assertEquals("CHO_DUYET", create("U", 5).get("trangThai"));
        tx(em -> assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, "T1").trangThai));
    }

    @Test
    void invalidDatabaseCharactersAreRejectedBeforePersistence() {
        identity("U", "POST", "/api/danh-gia");
        for (String invalid :
                List.of("Nội dung" + (char) 0, "Sai" + (char) 0xD800, "Sai" + (char) 0xDC00))
            assertEquals(
                    400,
                    assertThrows(
                                    AccountException.class,
                                    () -> reviews.create(new ReviewInputs.Create("C", 5, invalid)))
                            .status());
        tx(
                em ->
                        assertEquals(
                                0L,
                                em.createQuery("select count(d) from DanhGia d", Long.class)
                                        .getSingleResult()));
    }

    @Test
    void eligibleOwnerCanEditButCannotDeleteAnotherAccountsReview() {
        String id = create("U", 5).get("maDanhGia").toString();
        identity("U", "GET", "/api/danh-gia/dieu-kien");
        assertEquals(true, reviews.eligibility("C").get("coTheSua"));
        identity("V", "DELETE", "/api/danh-gia/" + id);
        assertEquals(403, assertThrows(AccountException.class, () -> reviews.delete(id)).status());
        tx(em -> em.find(VeXe.class, "T1").trangThai = TrangThaiVe.DA_HOAN);
        identity("U", "GET", "/api/danh-gia/dieu-kien");
        assertEquals(false, reviews.eligibility("C").get("coTheSua"));
        identity("U", "PUT", "/api/danh-gia/" + id);
        assertEquals(
                403,
                assertThrows(
                                AccountException.class,
                                () -> reviews.edit(id, new ReviewInputs.Edit(4, "Sửa")))
                        .status());
    }

    @Test
    void operatorCanModerateOnlyOwnFleetAndHistoryIsRecorded() {
        String id = create("U", 5).get("maDanhGia").toString();
        tx(em -> em.find(NguoiDung.class, "NX").nhaXe = em.find(NhaXe.class, "A"));
        identity("NX", "POST", "/api/danh-gia/" + id + "/duyet");
        reviews.moderate(id, true, null);
        assertEquals(1, total());
        identity("NX", "GET", "/api/danh-gia/" + id + "/lich-su");
        assertTrue(
                reviews.history(id).stream()
                        .anyMatch(
                                row ->
                                        row.get("maNguoiThaoTac").equals("NX")
                                                && row.get("thaoTac").equals("DUYET")));
        identity("NX", "GET", "/api/quan-ly/danh-gia");
        assertEquals(1L, reviews.management("C", "DA_DUYET", 1, 20).get("totalItems"));
    }

    @Test
    void threeValidTicketsInOneOrderStillCreateOneReview() {
        ticket("T2", "O1", "U", "C", TrangThaiVe.DA_XUAT_VE);
        ticket("T3", "O1", "U", "C", TrangThaiVe.DA_LEN_XE);
        create("U", 5);
        assertEquals(409, assertThrows(AccountException.class, () -> create("U", 5)).status());
    }

    @Test
    void emptyContentAndExactLengthAreValidAndHideRequiresReason() {
        identity("U", "POST", "/api/danh-gia");
        String id =
                reviews.create(new ReviewInputs.Create("C", 1, null)).get("maDanhGia").toString();
        identity("U", "PUT", "/api/danh-gia/" + id);
        assertEquals(
                2000,
                reviews.edit(id, new ReviewInputs.Edit(5, "x".repeat(2000)))
                        .get("binhLuan")
                        .toString()
                        .length());
        identity("AD", "POST", "/api/danh-gia/" + id + "/an");
        assertEquals(
                400,
                assertThrows(AccountException.class, () -> reviews.moderate(id, false, " "))
                        .status());
    }

    @Test
    void concurrentCreateInsertsOnlyOneRecord() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var go = new CountDownLatch(1);
        Callable<Integer> create =
                () -> {
                    try {
                        go.await();
                        create("U", 5);
                        return 201;
                    } catch (AccountException e) {
                        return e.status();
                    } finally {
                        RequestIdentity.clear();
                    }
                };
        try {
            var a = pool.submit(create);
            var b = pool.submit(create);
            go.countDown();
            assertEquals(
                    Set.of(201, 409),
                    Set.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)));
        } finally {
            pool.shutdownNow();
        }
        tx(
                em ->
                        assertEquals(
                                1L,
                                em.createQuery("select count(d) from DanhGia d", Long.class)
                                        .getSingleResult()));
    }
}
