package com.datvexe.service;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.BookingDto.OrderResponse;
import com.datvexe.dto.BookingDto.PassengerRequest;
import com.datvexe.dto.BookingDto.PassengersRequest;
import com.datvexe.dto.BookingDto.TicketResponse;
import com.datvexe.dto.PricingDto.PriceSummary;
import com.datvexe.security.AccountException;
import com.datvexe.security.CurrentUser;
import com.datvexe.security.RequestIdentity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingServiceExpiryTest {
    private static final ZoneId ZONE = TripSaleRules.ZONE;
    private static final String USER_ID = "BUYER";
    private static final String SESSION_ID = "SESSION";
    private static final String ORDER_ID = "ORDER";
    private static final String TRIP_ID = "TRIP";
    private static final String HOLD_ID = "HOLD";

    private EntityManagerFactory factory;
    private LocalDateTime expiresAt;

    @BeforeEach
    void setUp() {
        String db = "booking_expiry_" + UUID.randomUUID().toString().replace("-", "");
        factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "jakarta.persistence.jdbc.url", "jdbc:h2:mem:" + db + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", "",
                "hibernate.hbm2ddl.auto", "create-drop"));
        expiresAt = LocalDateTime.of(2030, 1, 1, 10, 15);
        inTransaction(this::createBaseFixture);
    }

    @AfterEach
    void tearDown() {
        RequestIdentity.clear();
        if (factory != null) factory.close();
    }

    @Test
    void getOrderExpiresAtTheExactDeadlineAndCommitsTheRejectedTransition() {
        BookingService beforeDeadline = service(expiresAt.minusNanos(1));
        setRequest("GET", "/api/booking/" + ORDER_ID);
        assertEquals(200, beforeDeadline.getOrder(ORDER_ID, List.<PassengerRequest>of()).status());

        BookingService atDeadline = service(expiresAt);
        setRequest("GET", "/api/booking/" + ORDER_ID);
        AccountException expired = assertThrows(AccountException.class,
                () -> atDeadline.getOrder(ORDER_ID, List.<PassengerRequest>of()));
        assertEquals(409, expired.status());

        inTransaction(em -> {
            assertEquals(TrangThaiDon.HET_HAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(TrangThaiGiuCho.HET_HAN, em.find(GiuCho.class, HOLD_ID).trangThai);
        });

        BookingService afterDeadline = service(expiresAt.plusNanos(1));
        setRequest("GET", "/api/booking/" + ORDER_ID);
        assertEquals(409, assertThrows(AccountException.class,
                () -> afterDeadline.getOrder(ORDER_ID, List.<PassengerRequest>of())).status());
    }

    @Test
    void schedulerExpiresLegacyOrphanHoldAndLeavesPaidOrderUntouched() {
        inTransaction(em -> {
            DonDatVe paid = em.find(DonDatVe.class, ORDER_ID);
            paid.trangThai = TrangThaiDon.DA_THANH_TOAN;
            VeXe issued = new VeXe();
            issued.maVe = "ISSUED";
            issued.chuyenXe = em.find(ChuyenXe.class, TRIP_ID);
            issued.ghe = em.find(Ghe.class, "SEAT");
            issued.donDatVe = paid;
            issued.hoTenKhachHang = "Buyer";
            issued.soDienThoaiKhachHang = "0900000000";
            issued.giaVe = BigDecimal.valueOf(100000);
            issued.ngayXuatVe = expiresAt.minusMinutes(20);
            issued.trangThai = TrangThaiVe.DA_XUAT_VE;
            em.persist(issued);

            ChuyenXe orphanTrip = trip("ORPHAN_TRIP", em.find(TuyenXe.class, "ROUTE"),
                    em.find(Xe.class, "BUS"));
            em.persist(orphanTrip);
            GiuCho orphan = hold("ORPHAN_HOLD", orphanTrip, em.find(Ghe.class, "SEAT"), null);
            em.persist(orphan);
        });

        service(expiresAt).releaseExpiredHolds();

        inTransaction(em -> {
            assertEquals(TrangThaiGiuCho.HET_HAN, em.find(GiuCho.class, "ORPHAN_HOLD").trangThai);
            assertEquals(TrangThaiDon.DA_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(TrangThaiGiuCho.DANG_GIU, em.find(GiuCho.class, HOLD_ID).trangThai);
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, "ISSUED").trangThai);
        });
    }

    @Test
    void paidOrderWithoutHoldsLoadsTripAndIssuedTicketWithoutDraftPassengerRows() {
        inTransaction(em -> {
            DonDatVe paid = em.find(DonDatVe.class, ORDER_ID);
            paid.trangThai = TrangThaiDon.DA_THANH_TOAN;
            paid.tongTien = BigDecimal.valueOf(100000);
            em.remove(em.find(GiuCho.class, HOLD_ID));

            VeXe issued = new VeXe();
            issued.maVe = "ISSUED_WITHOUT_HOLD";
            issued.chuyenXe = em.find(ChuyenXe.class, TRIP_ID);
            issued.ghe = em.find(Ghe.class, "SEAT");
            issued.donDatVe = paid;
            issued.hoTenKhachHang = "Ticket passenger";
            issued.soDienThoaiKhachHang = "0912345678";
            issued.cccdHanhKhach = "012345678901";
            issued.giaVe = BigDecimal.valueOf(100000);
            issued.ngayXuatVe = expiresAt.minusMinutes(10);
            issued.trangThai = TrangThaiVe.DA_XUAT_VE;
            em.persist(issued);
        });

        setRequest("GET", "/api/booking/" + ORDER_ID);
        OrderResponse order = (OrderResponse) service(expiresAt).getOrder(
                ORDER_ID, List.<PassengerRequest>of()).body();

        assertEquals(ORDER_ID, order.maDon());
        assertEquals(TRIP_ID, order.maChuyen());
        assertEquals(TrangThaiDon.DA_THANH_TOAN, order.trangThaiDon());
        assertEquals(List.of(), order.danhSachGiuCho());
        assertEquals(List.of(), order.hanhKhach());
        assertEquals(1, order.danhSachVe().size());
        TicketResponse ticket = order.danhSachVe().getFirst();
        assertEquals("ISSUED_WITHOUT_HOLD", ticket.maVe());
        assertEquals("Ticket passenger", ticket.hoTen());
        assertEquals("012345678901", ticket.cccd());
        assertEquals(TrangThaiVe.DA_XUAT_VE, ticket.trangThai());
        assertNull(order.thoiDiemBatDau());
        assertNull(order.thoiDiemHetHan());
        assertFalse(order.coTheChotGia());

        inTransaction(em -> {
            assertEquals(0L, em.createQuery("select count(h) from GiuCho h where h.donDatVe.maDon = :id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
            assertEquals(1L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon = :id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, "ISSUED_WITHOUT_HOLD").trangThai);
        });
    }

    @Test
    void frozenOrderGetUsesPersistedPriceAndKeepsPassengersWhenRouteFareChanges() {
        seedFrozenOrderWithPromotion("HIST");
        inTransaction(em -> em.find(TuyenXe.class, "ROUTE").giaCoBan = BigDecimal.valueOf(125000));

        setRequest("GET", "/api/booking/" + ORDER_ID);
        OrderResponse order = (OrderResponse) service(expiresAt.minusMinutes(1))
                .getOrder(ORDER_ID, List.of(passenger())).body();

        assertEquals(TrangThaiDon.CHO_THANH_TOAN, order.trangThaiDon());
        assertEquals(List.of(passenger()), order.hanhKhach());
        PriceSummary frozen = order.giaDaChot();
        assertNotNull(frozen);
        assertEquals("100000", frozen.tongTienGoc());
        assertEquals("10000", frozen.tongTienGiam());
        assertEquals("90000", frozen.tongThanhToan());
        assertEquals("90000", frozen.chiTietGia().getFirst().thanhTien());
    }

    @Test
    void exactDeadlineExpiresFrozenOrderButPreservesFrozenAggregates() {
        seedFrozenOrderWithPromotion("HIST");

        setRequest("GET", "/api/booking/" + ORDER_ID);
        AccountException expired = assertThrows(AccountException.class,
                () -> service(expiresAt).getOrder(ORDER_ID, List.of(passenger())));
        assertEquals(409, expired.status());

        inTransaction(em -> {
            DonDatVe order = em.find(DonDatVe.class, ORDER_ID);
            assertEquals(TrangThaiDon.HET_HAN, order.trangThai);
            assertEquals(0, order.tongTien.compareTo(BigDecimal.valueOf(100000)));
            assertEquals(0, order.tienGiam.compareTo(BigDecimal.valueOf(10000)));
            assertEquals("HIST", order.maKhuyenMaiDaApDung);
            assertEquals(TrangThaiGiuCho.HET_HAN, em.find(GiuCho.class, HOLD_ID).trangThai);
        });
    }

    @Test
    void schedulerExpiresFrozenOrderWithoutClearingItsPriceHistory() {
        seedFrozenOrderWithPromotion("HIST");

        service(expiresAt).releaseExpiredHolds();

        inTransaction(em -> {
            DonDatVe order = em.find(DonDatVe.class, ORDER_ID);
            assertEquals(TrangThaiDon.HET_HAN, order.trangThai);
            assertEquals(0, order.tongTien.compareTo(BigDecimal.valueOf(100000)));
            assertEquals(0, order.tienGiam.compareTo(BigDecimal.valueOf(10000)));
            assertEquals("HIST", order.maKhuyenMaiDaApDung);
            assertEquals(TrangThaiGiuCho.HET_HAN, em.find(GiuCho.class, HOLD_ID).trangThai);
        });
    }

    @Test
    void invalidPassengerUpdateOnFrozenOrderRollsBackWithoutChangingPrice() {
        seedFrozenOrderWithPromotion("HIST");
        setRequest("PUT", "/api/booking/" + ORDER_ID + "/hanh-khach");

        AccountException invalid = assertThrows(AccountException.class,
                () -> service(expiresAt.minusMinutes(1)).savePassengers(
                        ORDER_ID, new PassengersRequest(List.of(
                                new PassengerRequest("OTHER", "Wrong", "0911111111", null)))));
        assertEquals(400, invalid.status());

        inTransaction(em -> {
            DonDatVe order = em.find(DonDatVe.class, ORDER_ID);
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, order.trangThai);
            assertEquals(0, order.tongTien.compareTo(BigDecimal.valueOf(100000)));
            assertEquals(0, order.tienGiam.compareTo(BigDecimal.valueOf(10000)));
            assertEquals("HIST", order.maKhuyenMaiDaApDung);
            assertEquals(TrangThaiGiuCho.DANG_GIU, em.find(GiuCho.class, HOLD_ID).trangThai);
        });
    }

    @Test
    void validPassengerUpdateThawsFrozenOrderAndKeepsOriginalDeadline() {
        seedFrozenOrderWithPromotion("HIST");

        setRequest("PUT", "/api/booking/" + ORDER_ID + "/hanh-khach");
        OrderResponse updated = (OrderResponse) service(expiresAt.minusMinutes(1))
                .savePassengers(ORDER_ID, new PassengersRequest(List.of(passenger()))).body();

        assertEquals(TrangThaiDon.CHO_XU_LY, updated.trangThaiDon());
        assertEquals("10000", updated.giaTamTinh().tienGiam());
        assertNull(updated.giaDaChot());
        inTransaction(em -> {
            DonDatVe order = em.find(DonDatVe.class, ORDER_ID);
            assertEquals(TrangThaiDon.CHO_XU_LY, order.trangThai);
            assertEquals(0, order.tienGiam.compareTo(BigDecimal.ZERO));
            assertEquals("HIST", order.maKhuyenMaiDaApDung);
            assertEquals(expiresAt, em.find(GiuCho.class, HOLD_ID).thoiDiemHetHan);
        });
    }

    @Test
    void schedulerAndGetAtExactDeadlineConvergeOnOneExpiredFrozenOrder() throws Exception {
        seedFrozenOrderWithPromotion("HIST");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier start = new CyclicBarrier(2);
        try {
            Future<?> scheduler = executor.submit(() -> {
                start.await();
                service(expiresAt).releaseExpiredHolds();
                return null;
            });
            Future<Integer> get = executor.submit(() -> {
                start.await();
                setRequest("GET", "/api/booking/" + ORDER_ID);
                try {
                    service(expiresAt).getOrder(ORDER_ID, List.of(passenger()));
                    return 200;
                } catch (AccountException error) {
                    assertEquals("HOLD_EXPIRED", error.code());
                    return error.status();
                } finally {
                    RequestIdentity.clear();
                }
            });
            assertEquals(409, get.get(10, TimeUnit.SECONDS));
            scheduler.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }

        inTransaction(em -> {
            DonDatVe order = em.find(DonDatVe.class, ORDER_ID);
            assertEquals(TrangThaiDon.HET_HAN, order.trangThai);
            assertEquals(0, order.tongTien.compareTo(BigDecimal.valueOf(100000)));
            assertEquals(0, order.tienGiam.compareTo(BigDecimal.valueOf(10000)));
            assertEquals("HIST", order.maKhuyenMaiDaApDung);
            assertEquals(TrangThaiGiuCho.HET_HAN, em.find(GiuCho.class, HOLD_ID).trangThai);
        });
    }

    private PassengerRequest passenger() {
        return new PassengerRequest("SEAT", "Passenger", "0912345678", null);
    }

    private void seedFrozenOrderWithPromotion(String promotionId) {
        createPromotion(promotionId);
        inTransaction(em -> {
            DonDatVe order = em.find(DonDatVe.class, ORDER_ID);
            order.trangThai = TrangThaiDon.CHO_THANH_TOAN;
            order.tongTien = BigDecimal.valueOf(100000);
            order.tienGiam = BigDecimal.valueOf(10000);
            order.maKhuyenMaiDaApDung = promotionId;
            em.find(GiuCho.class, HOLD_ID).trangThai = TrangThaiGiuCho.DANG_GIU;
        });
    }

    private void createPromotion(String id) {
        inTransaction(em -> {
            KhuyenMai promotion = new KhuyenMai();
            promotion.maKhuyenMai = id;
            promotion.tenKhuyenMai = "Historical discount";
            promotion.loaiGiam = LoaiGiam.SO_TIEN_CO_DINH;
            promotion.giaTriGiam = BigDecimal.valueOf(10000);
            promotion.donToiThieu = BigDecimal.ZERO;
            promotion.soLuong = 10;
            promotion.soLuongDaDung = 0;
            promotion.ngayBatDau = expiresAt.minusHours(1);
            promotion.ngayKetThuc = expiresAt.plusHours(1);
            promotion.trangThai = TrangThaiKhuyenMai.HOAT_DONG;
            em.persist(promotion);
        });
    }

    private void createBaseFixture(EntityManager em) {
        NguoiDung buyer = new NguoiDung();
        buyer.maNguoiDung = USER_ID;
        buyer.hoTen = "Buyer";
        buyer.soDienThoai = "0900000000";
        buyer.email = "buyer@example.test";
        buyer.matKhau = "test-hash";
        buyer.loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG;
        buyer.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
        em.persist(buyer);

        AuthSession session = new AuthSession();
        session.tokenHash = SESSION_ID;
        session.csrfToken = "CSRF";
        session.nguoiDung = buyer;
        session.taoLuc = expiresAt.minusDays(1).atZone(ZONE).toInstant();
        session.hetHan = Instant.parse("2100-01-01T00:00:00Z");
        em.persist(session);

        TinhThanh city = new TinhThanh(); city.maTinhThanh = "CITY"; city.tenTinhThanh = "City"; em.persist(city);
        BenXe start = station("START", city), end = station("END", city); em.persist(start); em.persist(end);
        NhaXe operator = new NhaXe(); operator.maNhaXe = "OP"; operator.tenNhaXe = "Operator"; em.persist(operator);
        TuyenXe route = new TuyenXe();
        route.maTuyen = "ROUTE"; route.tenTuyen = "Route"; route.tramKhoiHanh = start; route.tramDen = end;
        route.nhaXe = operator; route.giaCoBan = BigDecimal.valueOf(100000); em.persist(route);
        Xe vehicle = new Xe(); vehicle.maXe = "BUS"; vehicle.bienSo = "51B-EXPIRY";
        vehicle.soChoNgoi = 1; vehicle.nhaXe = operator; em.persist(vehicle);
        Ghe seat = new Ghe(); seat.maGhe = "SEAT"; seat.viTri = "A1"; seat.xe = vehicle; em.persist(seat);
        ChuyenXe trip = trip(TRIP_ID, route, vehicle); em.persist(trip);

        DonDatVe order = new DonDatVe();
        order.maDon = ORDER_ID; order.nguoiDat = buyer; order.ngayDat = expiresAt.minusMinutes(15);
        order.khachDatTen = "Buyer"; order.khachDatSoDienThoai = "0900000000";
        order.trangThai = TrangThaiDon.CHO_XU_LY; em.persist(order);
        em.persist(hold(HOLD_ID, trip, seat, order));
    }

    private static BenXe station(String id, TinhThanh city) {
        BenXe station = new BenXe(); station.maBenXe = id; station.tenBenXe = id;
        station.diaChi = id; station.tinhThanh = city; return station;
    }

    private ChuyenXe trip(String id, TuyenXe route, Xe vehicle) {
        ChuyenXe trip = new ChuyenXe(); trip.maChuyen = id; trip.tuyenXe = route; trip.xe = vehicle;
        trip.ngayKhoiHanh = expiresAt.toLocalDate().plusDays(1); trip.gioKhoiHanh = LocalTime.NOON;
        trip.gioDenDuKien = trip.ngayKhoiHanh.atTime(18, 0); return trip;
    }

    private GiuCho hold(String id, ChuyenXe trip, Ghe seat, DonDatVe order) {
        GiuCho hold = new GiuCho(); hold.maGiuCho = id; hold.chuyenXe = trip; hold.ghe = seat;
        hold.donDatVe = order; hold.ngayDat = expiresAt.minusMinutes(15);
        hold.thoiDiemBatDau = hold.ngayDat; hold.thoiDiemHetHan = expiresAt;
        hold.trangThai = TrangThaiGiuCho.DANG_GIU; return hold;
    }

    private BookingService service(LocalDateTime at) {
        return new BookingService(factory, Clock.fixed(at.atZone(ZONE).toInstant(), ZONE));
    }

    private void setRequest(String method, String path) {
        RequestIdentity.set(new RequestIdentity.Context(
                new CurrentUser(USER_ID, LoaiTaiKhoan.KHACH_HANG, null, null, SESSION_ID), method, path, null));
    }

    private void inTransaction(java.util.function.Consumer<EntityManager> work) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction();
            tx.begin();
            try { work.accept(em); tx.commit(); }
            catch (RuntimeException error) { if (tx.isActive()) tx.rollback(); throw error; }
        }
    }
}
