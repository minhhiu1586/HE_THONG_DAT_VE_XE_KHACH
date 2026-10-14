package com.datvexe.service;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.BookingDto.PassengerRequest;
import com.datvexe.dto.PricingDto.PriceLine;
import com.datvexe.dto.ThanhToanDto;
import com.datvexe.payment.PaymentContextStore;
import com.datvexe.payment.VNPayClient;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ThanhToanServiceTest {
    private static final ZoneId ZONE = TripSaleRules.ZONE;
    private static final String USER_ID = "BUYER";
    private static final String SESSION_ID = "SESSION";
    private static final String ORDER_ID = "ORDER";
    private static final String PAYMENT_ID = "PAYMENT";
    private static final String TRIP_ID = "TRIP";
    private static final LocalDateTime CREATED = LocalDateTime.of(2030, 1, 1, 10, 0);
    private static final LocalDateTime EXPIRES = CREATED.plusMinutes(15);

    private EntityManagerFactory factory;
    private PaymentContextStore contexts;

    @BeforeEach
    void setUp() {
        String db = "payment_service_" + UUID.randomUUID().toString().replace("-", "");
        factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "jakarta.persistence.jdbc.url", "jdbc:h2:mem:" + db + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", "",
                "hibernate.hbm2ddl.auto", "create-drop"));
        contexts = new PaymentContextStore(Clock.fixed(instant(CREATED), ZONE));
        inTransaction(RefundSchemaFixture::create);
        inTransaction(em -> em.createNativeQuery("CREATE SEQUENCE ticket_public_code_seq MINVALUE 1 MAXVALUE 33554431 NO CYCLE").executeUpdate());
        inTransaction(this::createFixture);
    }

    @AfterEach
    void tearDown() {
        RequestIdentity.clear();
        if (factory != null) factory.close();
    }

    @Test
    void successfulPaymentIssuesAllTicketsWithFrozenAllocationAndConsumesPromotionOnce() {
        seedPromotion();
        inTransaction(em -> em.find(DonDatVe.class, ORDER_ID).khachDatEmail = "receipt@gmail.com");
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));

        ThanhToanService.ProcessingResult result = service.receive(success());

        assertFalse(result.duplicate());
        assertEquals(TrangThaiThanhToan.THANH_CONG, result.payment().trangThai());
        assertFalse(result.payment().canDoiSoat());
        inTransaction(em -> {
            DonDatVe order = em.find(DonDatVe.class, ORDER_ID);
            assertEquals(TrangThaiDon.DA_THANH_TOAN, order.trangThai);
            // The fixture has one payment but three holds; all three seats must issue.
            assertEquals(3, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
            List<VeXe> tickets = em.createQuery("select v from VeXe v where v.donDatVe.maDon=:id order by v.ghe.maGhe", VeXe.class)
                    .setParameter("id", ORDER_ID).getResultList();
            assertEquals(List.of("96666", "96667", "96667"), tickets.stream().map(v -> v.giaVe.setScale(0).toPlainString()).toList());
            assertTrue(tickets.stream().allMatch(v -> v.maQR != null && !v.maQR.isBlank()));
            assertTrue(tickets.stream().allMatch(v -> v.maTraCuu.matches("[0-9A-HJKMNP-TV-Z]{5}")));
            assertEquals(3L, tickets.stream().map(v -> v.maTraCuu).distinct().count());
            assertEquals(1, em.find(KhuyenMai.class, "PROMO").soLuongDaDung);
            assertEquals(3L, em.createQuery("select count(h) from GiuCho h where h.donDatVe.maDon=:id and h.trangThai=:state", Long.class)
                    .setParameter("id", ORDER_ID).setParameter("state", TrangThaiGiuCho.DA_XAC_NHAN).getSingleResult());
        });

        ThanhToanService.ProcessingResult replay = service.receive(success());
        assertTrue(replay.duplicate());
        inTransaction(em -> {
            assertEquals(1, em.find(KhuyenMai.class, "PROMO").soLuongDaDung);
            var email = em.find(com.datvexe.domain.EmailOutbox.class, ORDER_ID);
            assertNotNull(email);
            assertEquals("PENDING", email.status);
            assertEquals("receipt@gmail.com", email.recipient);
            assertTrue(email.htmlContent.contains("96666") || email.htmlContent.contains("96.666"));
            assertEquals(1L, em.createQuery("select count(e) from EmailOutbox e", Long.class).getSingleResult());
        });
    }

    @Test
    void latePaymentCallbackCannotReverseRefundStatesOrIssueTicketsAgain() {
        seedPromotion();
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));
        service.receive(success());
        for (TrangThaiThanhToan refunded : List.of(TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN, TrangThaiThanhToan.DA_HOAN_TIEN)) {
            inTransaction(em -> {
                em.find(ThanhToan.class, PAYMENT_ID).trangThai = refunded;
                em.createQuery("update VeXe v set v.trangThai=:state where v.donDatVe.maDon=:id")
                        .setParameter("state", TrangThaiVe.DA_HOAN).setParameter("id", ORDER_ID).executeUpdate();
            });
            assertTrue(service.receive(success()).duplicate());
            inTransaction(em -> {
                assertEquals(refunded, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
                assertEquals(TrangThaiDon.DA_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
                assertEquals(3L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id and v.trangThai=:state", Long.class)
                        .setParameter("id", ORDER_ID).setParameter("state", TrangThaiVe.DA_HOAN).getSingleResult());
                assertEquals(1, em.find(KhuyenMai.class, "PROMO").soLuongDaDung);
            });
        }
    }

    @Test
    void serverDeadlineWinsWhenProviderPaidBeforeExpiry() {
        inTransaction(em -> em.find(DonDatVe.class, ORDER_ID).khachDatEmail = "receipt@gmail.com");
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        ThanhToanService service = serviceAt(EXPIRES);

        ThanhToanService.ProcessingResult result = service.receive(success(CREATED.plusMinutes(14)));

        assertTrue(result.payment().canDoiSoat());
        inTransaction(em -> {
            assertEquals(TrangThaiThanhToan.THANH_CONG, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(TrangThaiDon.HET_HAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertNull(em.find(com.datvexe.domain.EmailOutbox.class, ORDER_ID));
            assertEquals(0L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
            assertEquals(3L, em.createQuery("select count(h) from GiuCho h where h.donDatVe.maDon=:id and h.trangThai=:state", Long.class)
                    .setParameter("id", ORDER_ID).setParameter("state", TrangThaiGiuCho.HET_HAN).getSingleResult());
        });
    }

    @Test
    void successfulPaymentAfterVolatileContextLossKeepsFinancialTruthWithoutIssuingTickets() {
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));

        ThanhToanService.ProcessingResult result = service.receive(success());

        assertTrue(result.payment().canDoiSoat());
        inTransaction(em -> {
            assertEquals(TrangThaiThanhToan.THANH_CONG, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
        });
    }

    @Test
    void pendingProviderResultDoesNotMovePaymentAndTerminalFailureDoes() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));

        ThanhToanService.ProcessingResult pending = service.receive(new VNPayClient.VerifiedPayment(
                PAYMENT_ID, 290000, "00", "01", CREATED.plusMinutes(1).atZone(ZONE).toInstant(), "VN-PENDING"));
        assertFalse(pending.payment().ketQuaCongDaXacDinh());
        assertEquals(TrangThaiThanhToan.CHO_XU_LY, pending.payment().trangThai());

        ThanhToanService.ProcessingResult uncertain = service.receive(new VNPayClient.VerifiedPayment(
                PAYMENT_ID, 290000, "24", "01", null, "VN-UNCERTAIN"));
        assertEquals(TrangThaiThanhToan.CHO_XU_LY, uncertain.payment().trangThai());

        ThanhToanService.ProcessingResult failed = service.receive(new VNPayClient.VerifiedPayment(
                PAYMENT_ID, 290000, "24", "02", CREATED.plusMinutes(2).atZone(ZONE).toInstant(), "VN-FAILED"));
        assertTrue(failed.payment().ketQuaCongDaXacDinh());
        assertEquals(TrangThaiThanhToan.THAT_BAI, failed.payment().trangThai());
        inTransaction(em -> assertEquals(0L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                .setParameter("id", ORDER_ID).getSingleResult()));
    }

    @Test
    void invalidPassengerSnapshotRollsBackPaymentAndTicketIssuance() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"))));
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));

        assertThrows(AccountException.class, () -> service.receive(success()));

        inTransaction(em -> {
            assertEquals(TrangThaiThanhToan.CHO_XU_LY, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
            assertEquals(3L, em.createQuery("select count(h) from GiuCho h where h.donDatVe.maDon=:id and h.trangThai=:state", Long.class)
                    .setParameter("id", ORDER_ID).setParameter("state", TrangThaiGiuCho.DANG_GIU).getSingleResult());
        });
    }

    @Test
    void verifyRequiresAuthenticatedOwnerBeforeUsingMissingVolatileContext() {
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));

        AccountException error = assertThrows(AccountException.class, () -> service.verify(PAYMENT_ID, "127.0.0.1"));

        assertEquals(401, error.status());
        assertEquals("UNAUTHENTICATED", error.code());
    }

    @Test
    void concurrentCallbacksIssueOnceAndIncrementPromotionOnce() throws Exception {
        seedPromotion();
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Callable<ThanhToanService.ProcessingResult> callback = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return service.receive(success());
            };
            Future<ThanhToanService.ProcessingResult> first = executor.submit(callback);
            Future<ThanhToanService.ProcessingResult> second = executor.submit(callback);
            ThanhToanService.ProcessingResult one = first.get(10, TimeUnit.SECONDS);
            ThanhToanService.ProcessingResult two = second.get(10, TimeUnit.SECONDS);
            assertNotEquals(one.duplicate(), two.duplicate());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        inTransaction(em -> {
            assertEquals(3L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
            assertEquals(1, em.find(KhuyenMai.class, "PROMO").soLuongDaDung);
        });
    }

    @Test
    void issuedPaymentRemainsReconciledAfterStoreClear() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));
        service.receive(success());
        contexts.remove(PAYMENT_ID);
        setRequest("GET", "/api/thanh-toan/" + PAYMENT_ID);

        ThanhToanDto.PaymentResponse response = service.get(PAYMENT_ID);

        assertEquals(TrangThaiThanhToan.THANH_CONG, response.trangThai());
        assertFalse(response.canDoiSoat());
    }

    @Test
    void ticketReadKeepsOriginalFareAfterRoutePriceChangesAndCountsRefundedTicket() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));
        service.receive(success());
        String ticketId = firstTicketId();

        inTransaction(em -> {
            TuyenXe route = em.find(TuyenXe.class, "ROUTE");
            route.giaCoBan = BigDecimal.valueOf(125000);
            VeXe ticket = em.find(VeXe.class, ticketId);
            ticket.trangThai = TrangThaiVe.DA_HOAN;
        });
        setRequest("GET", "/api/ve-xe/" + ticketId);

        ThanhToanDto.TicketResponse response = service.ticket(ticketId);

        assertEquals("100000", response.giaGoc());
        assertEquals("96666", response.thanhTien());
        assertEquals("3334", response.tienGiam());
        assertEquals(TrangThaiVe.DA_HOAN, response.trangThai());
        assertNull(response.qrToken(), "Refunded tickets must not expose a usable QR token");
        assertFalse(response.dangHoan());
    }

    @Test
    void verifyUsesOriginalCreateTimeAndAmountForQueryDr() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        FakeGateway gateway = new FakeGateway();
        ThanhToanService service = new ThanhToanService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), contexts, gateway);
        setRequest("POST", "/api/thanh-toan/" + PAYMENT_ID + "/xac-minh");

        ThanhToanDto.PaymentResponse response = service.verify(PAYMENT_ID, "127.0.0.1");

        assertEquals(instant(CREATED), gateway.queryCreatedAt);
        assertEquals(BigDecimal.valueOf(290000), gateway.queryAmount);
        assertEquals(TrangThaiThanhToan.THANH_CONG, response.trangThai());
        assertFalse(response.canDoiSoat());
    }

    @Test
    void queryDrTimeoutLeavesPaymentPending() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        FakeGateway gateway = new FakeGateway();
        gateway.timeout = true;
        ThanhToanService service = new ThanhToanService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), contexts, gateway);
        setRequest("POST", "/api/thanh-toan/" + PAYMENT_ID + "/xac-minh");

        AccountException error = assertThrows(AccountException.class,
                () -> service.verify(PAYMENT_ID, "127.0.0.1"));

        assertEquals(502, error.status());
        inTransaction(em -> {
            assertEquals(TrangThaiThanhToan.CHO_XU_LY, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
        });
    }

    @Test
    void successfulCallbackForCanceledOrderKeepsFinancialTruthWithoutTickets() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        inTransaction(em -> em.find(DonDatVe.class, ORDER_ID).trangThai = TrangThaiDon.DA_HUY);

        ThanhToanService.ProcessingResult result = serviceAt(CREATED.plusMinutes(5)).receive(success());

        assertEquals(TrangThaiThanhToan.THANH_CONG, result.payment().trangThai());
        assertTrue(result.payment().canDoiSoat());
        inTransaction(em -> {
            assertEquals(TrangThaiDon.DA_HUY, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
        });
    }

    @Test
    void exhaustedPromotionAfterCheckoutKeepsFinancialTruthWithoutTickets() {
        seedPromotion();
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        inTransaction(em -> em.find(KhuyenMai.class, "PROMO").soLuongDaDung = 1);

        ThanhToanService.ProcessingResult result = serviceAt(CREATED.plusMinutes(5)).receive(success());

        assertEquals(TrangThaiThanhToan.THANH_CONG, result.payment().trangThai());
        assertTrue(result.payment().canDoiSoat());
        inTransaction(em -> {
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(1, em.find(KhuyenMai.class, "PROMO").soLuongDaDung);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
        });
    }

    @Test
    void sameAmountButWrongContextRollsBackPaymentFinalization() {
        PaymentContextStore.Attempt wrongOrderContext = new PaymentContextStore.Attempt(
                PAYMENT_ID, "OTHER_ORDER", USER_ID, "VNPAY", BigDecimal.valueOf(290000),
                instant(CREATED), instant(EXPIRES), instant(EXPIRES.plusHours(24)),
                "127.0.0.1", null, List.of("A", "B", "C"),
                List.of(passenger("A"), passenger("B"), passenger("C")),
                attempt(List.of(passenger("A"), passenger("B"), passenger("C"))).chiTietGia());
        contexts.put(wrongOrderContext);

        assertThrows(AccountException.class, () -> serviceAt(CREATED.plusMinutes(5)).receive(success()));

        inTransaction(em -> {
            assertEquals(TrangThaiThanhToan.CHO_XU_LY, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
        });
    }

    private ThanhToanService serviceAt(LocalDateTime time) {
        Clock clock = Clock.fixed(time.atZone(ZONE).toInstant(), ZONE);
        return new ThanhToanService(factory, clock, contexts, null);
    }

    @Test
    void ipnCompletesWhileQueryDrIsInFlightWithoutIssuingTwice() throws Exception {
        seedPromotion();
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        CountDownLatch queryStarted = new CountDownLatch(1);
        CountDownLatch ipnCommitted = new CountDownLatch(1);
        ThanhToanService.Gateway gateway = new ThanhToanService.Gateway() {
            @Override public String payUrl(String ref, BigDecimal amount, Instant created, Instant expiry, String ip) {
                throw new UnsupportedOperationException();
            }
            @Override public VNPayClient.VerifiedPayment verifyCallback(Map<String, String> parameters) {
                throw new UnsupportedOperationException();
            }
            @Override public VNPayClient.VerifiedPayment queryDr(String ref, BigDecimal amount, Instant created, String ip) {
                queryStarted.countDown();
                try { assertTrue(ipnCommitted.await(10, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
                return success();
            }
        };
        ThanhToanService service = new ThanhToanService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), contexts, gateway);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ThanhToanDto.PaymentResponse> query = pool.submit(() -> {
                setRequest("POST", "/api/thanh-toan/" + PAYMENT_ID + "/xac-minh");
                try { return service.verify(PAYMENT_ID, "127.0.0.1"); }
                finally { RequestIdentity.clear(); }
            });
            Future<?> ipn = pool.submit(() -> {
                assertTrue(queryStarted.await(10, TimeUnit.SECONDS));
                try { return service.receive(success()); }
                finally { ipnCommitted.countDown(); }
            });
            ipn.get(15, TimeUnit.SECONDS);
            assertEquals(TrangThaiThanhToan.THANH_CONG, query.get(15, TimeUnit.SECONDS).trangThai());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        inTransaction(em -> {
            assertEquals(3L, em.createQuery("select count(v) from VeXe v", Long.class).getSingleResult());
            assertEquals(1, em.find(KhuyenMai.class, "PROMO").soLuongDaDung);
        });
    }

    @Test
    void databaseFailureDuringTicketFlushRollsBackTheEntireIssuance() {
        seedPromotion();
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        inTransaction(em -> em.createNativeQuery(
                "ALTER TABLE ve_xe ADD CONSTRAINT test_reject_second_seat CHECK (ma_ghe <> 'B')").executeUpdate());
        assertThrows(jakarta.persistence.PersistenceException.class,
                () -> serviceAt(CREATED.plusMinutes(5)).receive(success()));
        inTransaction(em -> {
            assertEquals(TrangThaiThanhToan.CHO_XU_LY, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(0, em.find(KhuyenMai.class, "PROMO").soLuongDaDung);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v", Long.class).getSingleResult());
            assertEquals(3L, em.createQuery("select count(h) from GiuCho h where h.trangThai=:state", Long.class)
                    .setParameter("state", TrangThaiGiuCho.DANG_GIU).getSingleResult());
        });
    }

    @Test
    void expirySchedulerAndCallbackAtDeadlineLeaveNoIssuedSeats() throws Exception {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        Clock deadline = Clock.fixed(instant(EXPIRES), ZONE);
        ThanhToanService payments = new ThanhToanService(factory, deadline, contexts, null);
        BookingService bookings = new BookingService(factory, deadline);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<?> sweep = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                bookings.releaseExpiredHolds();
                return null;
            });
            Future<?> callback = pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return payments.receive(success());
            });
            sweep.get(10, TimeUnit.SECONDS);
            callback.get(10, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        inTransaction(em -> {
            assertEquals(TrangThaiDon.HET_HAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(TrangThaiThanhToan.THANH_CONG, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v", Long.class).getSingleResult());
            assertEquals(3L, em.createQuery("select count(h) from GiuCho h where h.trangThai=:state", Long.class)
                    .setParameter("state", TrangThaiGiuCho.HET_HAN).getSingleResult());
        });
    }

    private String firstTicketId() {
        try (EntityManager em = factory.createEntityManager()) {
            return em.createQuery("select v.maVe from VeXe v where v.donDatVe.maDon=:id order by v.ghe.maGhe", String.class)
                    .setParameter("id", ORDER_ID).setMaxResults(1).getSingleResult();
        }
    }

    private PaymentContextStore.Attempt attempt(List<PassengerRequest> passengers) {
        List<PriceLine> lines = PriceCalculator.calculate(
                List.of(new PriceCalculator.SeatPrice("A", BigDecimal.valueOf(100000)),
                        new PriceCalculator.SeatPrice("B", BigDecimal.valueOf(100000)),
                        new PriceCalculator.SeatPrice("C", BigDecimal.valueOf(100000))),
                BigDecimal.valueOf(10000)).chiTietGia();
        return new PaymentContextStore.Attempt(PAYMENT_ID, ORDER_ID, USER_ID, "VNPAY",
                BigDecimal.valueOf(290000), instant(CREATED), instant(EXPIRES), instant(EXPIRES.plusHours(24)),
                "127.0.0.1", null, List.of("A", "B", "C"), passengers, lines);
    }

    private static PassengerRequest passenger(String seat) {
        return new PassengerRequest(seat, "Passenger " + seat, "090000000" + seat.charAt(0), null);
    }

    private VNPayClient.VerifiedPayment success() { return success(CREATED.plusMinutes(1)); }

    private VNPayClient.VerifiedPayment success(LocalDateTime paidAt) {
        return new VNPayClient.VerifiedPayment(PAYMENT_ID, 290000, "00", "00", instant(paidAt), "VN-SUCCESS");
    }

    private void seedPromotion() {
        inTransaction(em -> {
            DonDatVe order = em.find(DonDatVe.class, ORDER_ID);
            order.maKhuyenMaiDaApDung = "PROMO";
            KhuyenMai promotion = new KhuyenMai();
            promotion.maKhuyenMai = "PROMO";
            promotion.tenKhuyenMai = "Test promotion";
            promotion.loaiGiam = LoaiGiam.SO_TIEN_CO_DINH;
            promotion.giaTriGiam = BigDecimal.valueOf(10000);
            promotion.donToiThieu = BigDecimal.ZERO;
            promotion.soLuong = 1;
            promotion.soLuongDaDung = 0;
            promotion.ngayBatDau = CREATED.minusHours(1);
            promotion.ngayKetThuc = EXPIRES.plusHours(1);
            promotion.trangThai = TrangThaiKhuyenMai.HOAT_DONG;
            em.persist(promotion);
        });
    }

    private void createFixture(EntityManager em) {
        NguoiDung buyer = new NguoiDung();
        buyer.maNguoiDung = USER_ID; buyer.hoTen = "Buyer"; buyer.soDienThoai = "0900000000";
        buyer.email = "buyer@example.test"; buyer.matKhau = "hash";
        buyer.loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG; buyer.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
        em.persist(buyer);

        AuthSession session = new AuthSession();
        session.tokenHash = SESSION_ID; session.csrfToken = "CSRF"; session.nguoiDung = buyer;
        session.taoLuc = instant(CREATED.minusDays(1)); session.hetHan = instant(CREATED.plusDays(1));
        em.persist(session);

        TinhThanh city = new TinhThanh(); city.maTinhThanh = "CITY"; city.tenTinhThanh = "City"; em.persist(city);
        BenXe start = station("START", city), end = station("END", city); em.persist(start); em.persist(end);
        NhaXe operator = new NhaXe(); operator.maNhaXe = "OP"; operator.tenNhaXe = "Operator"; em.persist(operator);
        TuyenXe route = new TuyenXe(); route.maTuyen = "ROUTE"; route.tenTuyen = "Route";
        route.tramKhoiHanh = start; route.tramDen = end; route.nhaXe = operator;
        route.thoiGianKhoiHanh = LocalTime.NOON; route.giaCoBan = BigDecimal.valueOf(100000);
        route.trangThai = TrangThaiTuyenXe.DANG_KHAI_THAC; em.persist(route);
        Xe bus = new Xe(); bus.maXe = "BUS"; bus.bienSo = "51B-PAY"; bus.soChoNgoi = 3; bus.soDoNgoi = 1; bus.nhaXe = operator; em.persist(bus);
        List<Ghe> seats = new ArrayList<>();
        for (String id : List.of("A", "B", "C")) {
            Ghe seat = new Ghe(); seat.maGhe = id; seat.viTri = id; seat.xe = bus; em.persist(seat); seats.add(seat);
        }
        ChuyenXe trip = new ChuyenXe(); trip.maChuyen = TRIP_ID; trip.tuyenXe = route; trip.xe = bus;
        trip.ngayKhoiHanh = CREATED.toLocalDate().plusDays(1); trip.gioKhoiHanh = LocalTime.NOON;
        trip.gioDenDuKien = trip.ngayKhoiHanh.atTime(18, 0); trip.trangThai = TrangThaiChuyen.DANG_MO_BAN; em.persist(trip);

        DonDatVe order = new DonDatVe(); order.maDon = ORDER_ID; order.nguoiDat = buyer;
        order.ngayDat = CREATED.minusMinutes(15); order.khachDatTen = "Buyer"; order.khachDatSoDienThoai = "0900000000";
        order.tongTien = BigDecimal.valueOf(300000); order.tienGiam = BigDecimal.valueOf(10000);
        order.trangThai = TrangThaiDon.CHO_THANH_TOAN; em.persist(order);
        for (Ghe seat : seats) {
            GiuCho hold = new GiuCho(); hold.maGiuCho = "HOLD-" + seat.maGhe; hold.chuyenXe = trip; hold.ghe = seat; hold.donDatVe = order;
            hold.ngayDat = CREATED.minusMinutes(15); hold.thoiDiemBatDau = CREATED; hold.thoiDiemHetHan = EXPIRES;
            hold.trangThai = TrangThaiGiuCho.DANG_GIU; em.persist(hold);
        }
        PhuongThucThanhToan method = new PhuongThucThanhToan(); method.maPhuongThuc = "VNPAY"; method.tenPhuongThuc = "VNPay";
        method.trangThai = TrangThaiPhuongThuc.HOAT_DONG; em.persist(method);
        ThanhToan payment = new ThanhToan(); payment.maGiaoDich = PAYMENT_ID; payment.donDatVe = order;
        payment.phuongThucThanhToan = method; payment.soTien = BigDecimal.valueOf(290000); payment.trangThai = TrangThaiThanhToan.CHO_XU_LY; em.persist(payment);
    }

    private static BenXe station(String id, TinhThanh city) {
        BenXe station = new BenXe(); station.maBenXe = id; station.tenBenXe = id; station.diaChi = id; station.tinhThanh = city; return station;
    }

    private void setRequest(String method, String path) {
        RequestIdentity.set(new RequestIdentity.Context(
                new CurrentUser(USER_ID, LoaiTaiKhoan.KHACH_HANG, null, null, SESSION_ID), method, path, null));
    }

    private static Instant instant(LocalDateTime value) { return value.atZone(ZONE).toInstant(); }

    private static final class FakeGateway implements ThanhToanService.Gateway {
        private Instant queryCreatedAt;
        private BigDecimal queryAmount;
        private boolean timeout;

        @Override
        public String payUrl(String reference, BigDecimal amount, Instant created, Instant expiry, String ip) {
            return "https://payment.example.test/" + reference;
        }

        @Override
        public VNPayClient.VerifiedPayment queryDr(String reference, BigDecimal expectedAmount,
                                                   Instant originalCreateDate, String ip) {
            queryCreatedAt = originalCreateDate;
            queryAmount = expectedAmount;
            if (timeout) throw new AccountException(502, "PAYMENT_PROVIDER_UNAVAILABLE", "Cổng thanh toán tạm thời không phản hồi.");
            return new VNPayClient.VerifiedPayment(reference, expectedAmount.longValueExact(),
                    "00", "00", instant(CREATED.plusMinutes(1)), "VN-QUERY");
        }

        @Override
        public VNPayClient.VerifiedPayment verifyCallback(Map<String, String> parameters) {
            throw new UnsupportedOperationException("Callback verification is not used by these service tests.");
        }
    }

    private void inTransaction(java.util.function.Consumer<EntityManager> work) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction(); tx.begin();
            try { work.accept(em); tx.commit(); }
            catch (RuntimeException error) { if (tx.isActive()) tx.rollback(); throw error; }
        }
    }
}
