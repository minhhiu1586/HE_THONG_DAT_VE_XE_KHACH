package com.datvexe.security;

import static org.junit.jupiter.api.Assertions.*;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.Inputs;
import com.datvexe.service.RouteTripService;
import com.datvexe.service.VehicleService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.*;

import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;

class AccessControlTest {
    private EntityManagerFactory factory;
    private final Map<String, CurrentUser> users = new HashMap<>();

    @BeforeEach
    void setup() {
        factory =
                Persistence.createEntityManagerFactory(
                        "datvexe",
                        Map.of(
                                "jakarta.persistence.jdbc.driver",
                                "org.h2.Driver",
                                "jakarta.persistence.jdbc.url",
                                "jdbc:h2:mem:rbac;MODE=PostgreSQL",
                                "jakarta.persistence.jdbc.user",
                                "sa",
                                "jakarta.persistence.jdbc.password",
                                "",
                                "hibernate.hbm2ddl.auto",
                                "create-drop"));
        tx(
                em -> {
                    NhaXe a = new NhaXe();
                    a.maNhaXe = "A";
                    a.tenNhaXe = "A";
                    em.persist(a);
                    NhaXe b = new NhaXe();
                    b.maNhaXe = "B";
                    b.tenNhaXe = "B";
                    em.persist(b);
                    NhanVien employee = new NhanVien();
                    employee.maNhanVien = "E";
                    employee.hoTen = "E";
                    employee.email = "e@example.test";
                    employee.soDienThoai = "0901234599";
                    employee.nhaXe = a;
                    employee.loaiNhanVien = LoaiNhanVien.PHU_XE;
                    em.persist(employee);
                    for (String id : List.of("KH1", "KH2", "NX", "NV", "AD")) {
                        NguoiDung user = new NguoiDung();
                        user.maNguoiDung = id;
                        user.hoTen = id;
                        user.email = id + "@example.test";
                        user.soDienThoai = id;
                        user.matKhau = "not-a-login-fixture";
                        user.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
                        user.loaiTaiKhoan =
                                switch (id) {
                                    case "NX" -> LoaiTaiKhoan.NHA_XE;
                                    case "NV" -> LoaiTaiKhoan.NHAN_VIEN;
                                    case "AD" -> LoaiTaiKhoan.ADMIN;
                                    default -> LoaiTaiKhoan.KHACH_HANG;
                                };
                        if (id.equals("NX")) user.nhaXe = a;
                        if (id.equals("NV")) user.nhanVien = employee;
                        em.persist(user);
                        AuthSession session = new AuthSession();
                        session.tokenHash = id;
                        session.nguoiDung = user;
                        session.csrfToken = "csrf";
                        session.taoLuc = Instant.now();
                        session.hetHan = Instant.now().plusSeconds(3600);
                        em.persist(session);
                        users.put(
                                id,
                                new CurrentUser(
                                        id,
                                        user.loaiTaiKhoan,
                                        user.nhaXe == null ? null : "A",
                                        user.nhanVien == null ? null : "E",
                                        id));
                    }
                    TinhThanh city = new TinhThanh();
                    city.maTinhThanh = "T";
                    city.tenTinhThanh = "T";
                    em.persist(city);
                    BenXe start = new BenXe();
                    start.maBenXe = "S";
                    start.tenBenXe = "S";
                    start.diaChi = "S";
                    start.tinhThanh = city;
                    em.persist(start);
                    BenXe end = new BenXe();
                    end.maBenXe = "D";
                    end.tenBenXe = "D";
                    end.diaChi = "D";
                    end.tinhThanh = city;
                    em.persist(end);
                    LoaiXe type = new LoaiXe();
                    type.maLoaiXe = "L";
                    type.tenLoaiXe = "Test";
                    type.soChoNgoi = 2;
                    em.persist(type);
                    for (NhaXe owner : List.of(a, b)) {
                        String id = owner.maNhaXe;
                        TuyenXe route = new TuyenXe();
                        route.maTuyen = "R" + id;
                        route.tenTuyen = id;
                        route.nhaXe = owner;
                        route.tramKhoiHanh = start;
                        route.tramDen = end;
                        route.giaCoBan = BigDecimal.TEN;
                        route.thoiGianDuKien = BigDecimal.ONE;
                        route.trangThai = TrangThaiTuyenXe.DANG_KHAI_THAC;
                        em.persist(route);
                        Xe vehicle = new Xe();
                        vehicle.maXe = "X" + id;
                        vehicle.bienSo = id;
                        vehicle.soChoNgoi = 2;
                        vehicle.nhaXe = owner;
                        vehicle.loaiXe = type;
                        vehicle.ngayDangKiem = LocalDate.of(2020, 1, 1);
                        vehicle.hanDangKiem = LocalDate.of(2100, 1, 1);
                        em.persist(vehicle);
                        if (id.equals("B"))
                            for (int i = 1; i <= 2; i++) {
                                var g = new Ghe();
                                g.maGhe = "GB" + i;
                                g.viTri = "G" + i;
                                g.xe = vehicle;
                                em.persist(g);
                            }
                        ChuyenXe trip = new ChuyenXe();
                        trip.maChuyen = "C" + id;
                        trip.tuyenXe = route;
                        trip.xe = vehicle;
                        trip.ngayKhoiHanh = LocalDate.of(2030, 1, 1);
                        trip.gioKhoiHanh = LocalTime.NOON;
                        trip.gioDenDuKien = trip.ngayKhoiHanh.atTime(13, 0);
                        em.persist(trip);
                    }
                    PhanCongChuyenXe assignment = new PhanCongChuyenXe();
                    assignment.maPhanCong = "P";
                    assignment.chuyenXe = em.find(ChuyenXe.class, "CA");
                    assignment.nhanVien = employee;
                    assignment.vaiTro = VaiTroChuyen.PHU_XE;
                    em.persist(assignment);
                    for (int i = 1; i <= 2; i++) {
                        Ghe seat = new Ghe();
                        seat.maGhe = "G" + i;
                        seat.viTri = "G" + i;
                        seat.xe = em.find(Xe.class, "XA");
                        em.persist(seat);
                        DonDatVe order = new DonDatVe();
                        order.maDon = "O" + i;
                        order.nguoiDat = em.find(NguoiDung.class, "KH" + i);
                        order.ngayDat = LocalDateTime.now();
                        order.khachDatTen = "KH" + i;
                        order.khachDatSoDienThoai = "0901234567";
                        em.persist(order);
                        VeXe ticket = new VeXe();
                        ticket.maVe = "V" + i;
                        ticket.donDatVe = order;
                        ticket.chuyenXe = em.find(ChuyenXe.class, "CA");
                        ticket.ghe = seat;
                        ticket.hoTenKhachHang = "KH" + i;
                        ticket.soDienThoaiKhachHang = "0901234567";
                        ticket.giaVe = BigDecimal.TEN;
                        em.persist(ticket);
                    }
                });
    }

    @AfterEach
    void close() {
        RequestIdentity.clear();
        factory.close();
    }

    private void tx(Consumer<EntityManager> action) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            try {
                action.accept(em);
                em.getTransaction().commit();
            } catch (RuntimeException e) {
                em.getTransaction().rollback();
                throw e;
            }
        }
    }

    private NguoiDung user(EntityManager em, String id) {
        return AccessControl.authenticated(em, users.get(id), Instant.now());
    }

    private void forbidden(Runnable action) {
        var error = assertThrows(AccountException.class, action::run);
        assertEquals(403, error.status());
        assertEquals("FORBIDDEN", error.code());
    }

    private void context(String user, String method, String path, String body) throws Exception {
        RequestIdentity.set(
                new RequestIdentity.Context(
                        users.get(user),
                        method,
                        path,
                        body == null ? null : new ObjectMapper().readTree(body)));
    }

    @Test
    void roleAndAuthenticationCannotBeForged() {
        tx(
                em -> {
                    assertEquals(
                            401,
                            assertThrows(
                                            AccountException.class,
                                            () ->
                                                    AccessControl.authenticated(
                                                            em, null, Instant.now()))
                                    .status());
                    forbidden(() -> AccessControl.role(user(em, "KH1"), LoaiTaiKhoan.ADMIN));
                    AccessControl.role(user(em, "AD"), LoaiTaiKhoan.ADMIN);
                    CurrentUser forged =
                            new CurrentUser("KH1", LoaiTaiKhoan.ADMIN, "B", null, "KH1");
                    forbidden(
                            () ->
                                    AccessControl.role(
                                            AccessControl.authenticated(em, forged, Instant.now()),
                                            LoaiTaiKhoan.ADMIN));
                });
    }

    @Test
    void customersOwnTheirOrdersAndTickets() {
        tx(
                em -> {
                    var caller = user(em, "KH1");
                    AccessControl.orderOwner(em, caller, "O1");
                    AccessControl.ticketOwner(em, caller, "V1");
                    forbidden(() -> AccessControl.orderOwner(em, caller, "O2"));
                    forbidden(() -> AccessControl.ticketOwner(em, caller, "V2"));
                    forbidden(() -> AccessControl.profileOwner(caller, "KH2"));
                    AccessControl.profileOwner(caller, "KH1");
                });
    }

    @Test
    void operatorScopeUsesActualDatabaseRelationships() {
        tx(
                em -> {
                    var caller = user(em, "NX");
                    AccessControl.route(em, caller, "RA");
                    AccessControl.trip(em, caller, "CA");
                    AccessControl.vehicle(em, caller, "XA");
                    forbidden(() -> AccessControl.route(em, caller, "RB"));
                    forbidden(() -> AccessControl.trip(em, caller, "CB"));
                    forbidden(() -> AccessControl.vehicle(em, caller, "XB"));
                    AccessControl.trip(em, user(em, "AD"), "CB");
                });
    }

    @Test
    void suspendedOperatorCanReadOwnHistoryButCannotWriteOrSell() throws Exception {
        com.datvexe.service.PublishedTripFixture.open(factory, "CB");
        tx(em -> em.find(NhaXe.class, "A").trangThai = TrangThaiNhaXe.TAM_NGUNG);
        context("NX", "GET", "/api/tuyen-xe/RA", null);
        tx(em -> AccessPolicy.guard(em));
        context("NX", "PUT", "/api/tuyen-xe/RA", "{}");
        tx(em -> forbidden(() -> AccessPolicy.guard(em)));
        tx(
                em -> {
                    assertEquals(
                            409,
                            assertThrows(
                                            AccountException.class,
                                            () -> AccessControl.saleAllowed(em, "CA"))
                                    .status());
                    AccessControl.saleAllowed(em, "CB");
                });
    }

    @Test
    void saleRequiresAnAssignedOperator() {
        tx(
                em -> {
                    TuyenXe route = new TuyenXe();
                    route.maTuyen = "RU";
                    route.tenTuyen = "Chung";
                    route.tramKhoiHanh = em.find(BenXe.class, "S");
                    route.tramDen = em.find(BenXe.class, "D");
                    route.giaCoBan = BigDecimal.TEN;
                    route.thoiGianDuKien = BigDecimal.ONE;
                    em.persist(route);
                    ChuyenXe trip = new ChuyenXe();
                    trip.maChuyen = "CU";
                    trip.tuyenXe = route;
                    trip.ngayKhoiHanh = LocalDate.of(2030, 1, 1);
                    trip.gioKhoiHanh = LocalTime.NOON;
                    trip.gioDenDuKien = trip.ngayKhoiHanh.atTime(13, 0);
                    em.persist(trip);
                });
        tx(
                em -> {
                    AccountException error =
                            assertThrows(
                                    AccountException.class,
                                    () -> AccessControl.saleAllowed(em, "CU"));
                    assertEquals(409, error.status());
                    assertEquals("OPERATOR_UNASSIGNED", error.code());
                });
    }

    @Test
    void employeeMustHaveCurrentAssignmentAndActiveEmployment() {
        tx(
                em -> {
                    AccessControl.assigned(em, user(em, "NV"), "CA");
                    forbidden(() -> AccessControl.assigned(em, user(em, "NV"), "CB"));
                });
        tx(em -> em.remove(em.find(PhanCongChuyenXe.class, "P")));
        tx(em -> forbidden(() -> AccessControl.assigned(em, user(em, "NV"), "CA")));
    }

    @Test
    void employeeStateAndTicketAccessAreChecked() {
        tx(
                em -> {
                    AccessControl.ticketAccess(em, user(em, "NV"), "V1");
                    AccessControl.orderAccess(em, user(em, "NX"), "O1");
                    forbidden(() -> AccessControl.orderAccess(em, user(em, "NV"), "O1"));
                });
        tx(em -> em.find(NhanVien.class, "E").trangThai = TrangThaiNhanVien.TAM_NGHI);
        tx(em -> forbidden(() -> AccessControl.assigned(em, user(em, "NV"), "CA")));
    }

    @Test
    void retiredStaffCannotAuthenticateOrAccessAssignedTrips() {
        tx(
                em -> {
                    var employee = em.find(NhanVien.class, "E");
                    employee.nhaXe = null;
                    employee.loaiNhanVien = LoaiNhanVien.KIEM_VE;
                    assertTrue(AccessControl.retiredEmployee(em.find(NguoiDung.class, "NV")));
                });
        tx(
                em ->
                        assertEquals(
                                401,
                                assertThrows(AccountException.class, () -> user(em, "NV"))
                                        .status()));
        tx(em -> forbidden(() -> AccessControl.assigned(em, em.find(NguoiDung.class, "NV"), "CB")));
    }

    @Test
    void passengerManifestKeepsOperatorAndCustomerScope() throws Exception {
        tx(em -> com.datvexe.service.RefundSchemaFixture.create(em));
        var manifest = new com.datvexe.service.TripPassengerService(factory);
        context("NX", "GET", "/api/chuyen-xe/CA/hanh-khach", "{}");
        assertEquals(2, manifest.list("CA").tongVe());
        assertEquals(403, assertThrows(AccountException.class, () -> manifest.list("CB")).status());
        context("KH1", "GET", "/api/chuyen-xe/CA/hanh-khach", "{}");
        assertEquals(403, assertThrows(AccountException.class, () -> manifest.list("CA")).status());
    }

    @Test
    void changingRoleScopeOrRevokingSessionIsEffectiveImmediately() {
        tx(em -> em.find(NguoiDung.class, "NX").nhaXe = em.find(NhaXe.class, "B"));
        tx(
                em -> {
                    forbidden(() -> AccessControl.trip(em, user(em, "NX"), "CA"));
                    AccessControl.trip(em, user(em, "NX"), "CB");
                });
        tx(em -> em.remove(em.find(AuthSession.class, "NX")));
        tx(
                em ->
                        assertEquals(
                                401,
                                assertThrows(AccountException.class, () -> user(em, "NX"))
                                        .status()));
    }

    @Test
    void serviceGuardRejectsCrossOperatorUrlAndBodyBeforeWriting() throws Exception {
        var routes = new RouteTripService(factory);
        context("NX", "PUT", "/api/chuyen-xe/CB", "{}");
        forbidden(
                () ->
                        routes.updateChuyenXe(
                                "CB",
                                new Inputs.CapNhatChuyen(
                                        LocalDate.of(2031, 1, 1), LocalTime.NOON)));
        context("NX", "PUT", "/api/chuyen-xe/CA/xe", "{\"maXe\":\"XB\"}");
        forbidden(() -> routes.assignVehicle("CA", "XB"));
        context("NX", "POST", "/api/tuyen-xe", "{\"maNhaXe\":\"B\"}");
        forbidden(
                () ->
                        routes.createTuyenXe(
                                new Inputs.TuyenXe(
                                        "NEW",
                                        "New",
                                        "S",
                                        "D",
                                        LocalTime.NOON,
                                        null,
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        null,
                                        TrangThaiTuyenXe.DANG_KHAI_THAC,
                                        "B")));
        RequestIdentity.clear();
        assertEquals("XA", routes.getChuyenXe("CA").get("maXe"));
    }

    @Test
    void operatorCanCreateRouteOnlyInsideItsAuthenticatedDatabaseScope() throws Exception {
        var routes = new RouteTripService(factory);
        context("NX", "POST", "/api/tuyen-xe", "{\"maTuyen\":\"NEW\",\"maNhaXe\":\"A\"}");
        var created =
                routes.createTuyenXe(
                        new Inputs.TuyenXe(
                                "NEW",
                                "Tuyến mới",
                                "S",
                                "D",
                                LocalTime.NOON,
                                BigDecimal.ONE,
                                BigDecimal.ONE,
                                BigDecimal.TEN,
                                null,
                                TrangThaiTuyenXe.DANG_KHAI_THAC,
                                null));
        assertEquals("A", created.get("maNhaXe"));
        context("AD", "PUT", "/api/tuyen-xe/NEW", "{\"maTuyen\":\"NEW\"}");
        assertEquals(
                "A",
                routes.updateTuyenXe(
                                "NEW",
                                new Inputs.TuyenXe(
                                        "NEW",
                                        "Tên sửa",
                                        "S",
                                        "D",
                                        LocalTime.NOON,
                                        BigDecimal.ONE,
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        null,
                                        null,
                                        null))
                        .get("maNhaXe"));
        context("NX", "POST", "/api/tuyen-xe", "{\"maTuyen\":\"NEW2\",\"maNhaXe\":\"B\"}");
        forbidden(
                () ->
                        routes.createTuyenXe(
                                new Inputs.TuyenXe(
                                        "NEW2",
                                        "Tuyến khác",
                                        "S",
                                        "D",
                                        LocalTime.NOON,
                                        BigDecimal.ONE,
                                        BigDecimal.ONE,
                                        BigDecimal.TEN,
                                        null,
                                        TrangThaiTuyenXe.DANG_KHAI_THAC,
                                        "B")));
    }

    @Test
    void onlyAdminCanChangeSharedGeographicCatalogs() throws Exception {
        var routes = new RouteTripService(factory);
        context("KH1", "POST", "/api/tinh-thanh", "{\"maTinhThanh\":\"NEW\"}");
        forbidden(() -> routes.createTinhThanh(new Inputs.TinhThanh("NEW", "Tỉnh mới", null)));
        context("NX", "POST", "/api/ben-xe", "{\"maBenXe\":\"NEW\"}");
        forbidden(
                () ->
                        routes.createBenXe(
                                new Inputs.BenXe(
                                        "NEW",
                                        "Bến mới",
                                        "Địa chỉ",
                                        null,
                                        "S",
                                        TrangThaiBenXe.HOAT_DONG)));
        context("KH1", "GET", "/api/tinh-thanh", null);
        assertEquals(1, routes.listTinhThanh(null).size());
    }

    @Test
    void numericResourceIdCannotSkipTheScopeGuardThroughDtoCoercion() throws Exception {
        tx(
                em -> {
                    Xe vehicle = new Xe();
                    vehicle.maXe = "1";
                    vehicle.bienSo = "NUMERIC";
                    vehicle.soChoNgoi = 1;
                    vehicle.nhaXe = em.find(NhaXe.class, "B");
                    em.persist(vehicle);
                });
        context("NX", "PUT", "/api/chuyen-xe/CA/xe", "{\"maXe\":1}");
        var error =
                assertThrows(
                        AccountException.class,
                        () -> new RouteTripService(factory).assignVehicle("CA", "1"));
        assertEquals(400, error.status());
        assertEquals("VALIDATION_ERROR", error.code());
        context("NX", "PUT", "/api/chuyen-xe/CA/xe", "{\"maXe\":\"1\"}");
        forbidden(() -> new RouteTripService(factory).assignVehicle("CA", "1"));
        RequestIdentity.clear();
        assertEquals("XA", new RouteTripService(factory).getChuyenXe("CA").get("maXe"));
    }

    @Test
    void listsRefreshScopeChangedAfterFilterAuthentication() throws Exception {
        tx(em -> em.find(NguoiDung.class, "NX").nhaXe = em.find(NhaXe.class, "B"));
        context("NX", "GET", "/api/xe", null);
        assertEquals(
                List.of("XB"),
                new VehicleService(factory).list("xe").stream().map(v -> v.get("maXe")).toList());
        context("NX", "GET", "/api/tuyen-xe", null);
        assertEquals(
                List.of("RB"),
                new RouteTripService(factory)
                        .listTuyenXe().stream().map(v -> v.get("maTuyen")).toList());
    }

    @Test
    void privateListsAreFilteredAndPublicSearchStaysPublic() throws Exception {
        context("NX", "GET", "/api/xe", null);
        assertEquals(
                List.of("XA"),
                new VehicleService(factory).list("xe").stream().map(v -> v.get("maXe")).toList());
        context("NX", "GET", "/api/tuyen-xe", null);
        assertEquals(
                List.of("RA"),
                new RouteTripService(factory)
                        .listTuyenXe().stream().map(v -> v.get("maTuyen")).toList());
        assertTrue(AccessPolicy.publicEndpoint("GET", "/api/chuyen-xe/CA"));
        assertFalse(AccessPolicy.publicEndpoint("PUT", "/api/chuyen-xe/CA"));
        assertFalse(AccessPolicy.publicEndpoint("GET", "/api/tai-khoan/me"));
    }
}
