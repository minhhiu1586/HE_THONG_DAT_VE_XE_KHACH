package com.datvexe.service;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.BookingDto.PassengerRequest;
import com.datvexe.dto.PricingDto.PriceLine;
import com.datvexe.dto.ThanhToanDto;
import com.datvexe.dto.VeXeDto;
import com.datvexe.dto.HoanVeDto;
import com.datvexe.repository.HoanVeRepository;
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
                // H2 dùng cột text; chỉ thay cast JSONB trong fixture giả lập PostgreSQL.
                "hibernate.session_factory.statement_inspector",
                (org.hibernate.resource.jdbc.spi.StatementInspector) sql -> sql.replace("cast(? as jsonb)", "?"),
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
    void freePaymentCommitsTicketsAndReplaysWithoutIssuingAgain() {
        prepareCreateFixture();
        inTransaction(em -> {
            PhuongThucThanhToan method = new PhuongThucThanhToan();
            method.maPhuongThuc = "MIEN_PHI";
            method.tenPhuongThuc = "Miễn phí";
            method.trangThai = TrangThaiPhuongThuc.HOAT_DONG;
            em.persist(method);
            em.find(DonDatVe.class, ORDER_ID).tienGiam = BigDecimal.valueOf(300000);
        });
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));
        var request = new ThanhToanDto.CreateRequest("MIEN_PHI");
        List<PassengerRequest> passengers = List.of(passenger("A"), passenger("B"), passenger("C"));
        var first = service.create(ORDER_ID, request, "free-key", passengers, "127.0.0.1");
        var payment = (ThanhToanDto.PaymentResponse) first.body();
        assertEquals(201, first.status());
        assertEquals(TrangThaiThanhToan.THANH_CONG, payment.trangThai());
        assertNotNull(contexts.get(payment.maGiaoDich()));
        assertEquals(201, service.create(ORDER_ID, request, "free-key", passengers, "127.0.0.1").status());
        inTransaction(em -> {
            assertEquals(1L, em.createQuery("select count(t) from ThanhToan t", Long.class).getSingleResult());
            assertEquals(3L, em.createQuery("select count(v) from VeXe v", Long.class).getSingleResult());
            assertEquals(TrangThaiDon.DA_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
        });
    }

    @Test
    void createAtDeadlineCommitsExpiryBeforeReturningError() {
        prepareCreateFixture();
        AccountException error = assertThrows(AccountException.class, () -> serviceAt(EXPIRES).create(
                ORDER_ID, new ThanhToanDto.CreateRequest("VNPAY"), "expired-key",
                List.of(passenger("A"), passenger("B"), passenger("C")), "127.0.0.1"));
        assertEquals("HOLD_EXPIRED", error.code());
        inTransaction(em -> {
            assertEquals(TrangThaiDon.HET_HAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(3L, em.createQuery("select count(h) from GiuCho h where h.trangThai=:state", Long.class)
                    .setParameter("state", TrangThaiGiuCho.HET_HAN).getSingleResult());
            assertEquals(0L, em.createQuery("select count(t) from ThanhToan t", Long.class).getSingleResult());
        });
    }

    @Test
    void failedIdempotencySaveRollsBackFreePaymentAndTickets() {
        prepareCreateFixture();
        inTransaction(em -> {
            PhuongThucThanhToan method = new PhuongThucThanhToan();
            method.maPhuongThuc = "MIEN_PHI";
            method.tenPhuongThuc = "Miễn phí";
            method.trangThai = TrangThaiPhuongThuc.HOAT_DONG;
            em.persist(method);
            em.find(DonDatVe.class, ORDER_ID).tienGiam = BigDecimal.valueOf(300000);
            em.createNativeQuery("ALTER TABLE api_idempotency ADD CONSTRAINT reject_result CHECK (status < 0)").executeUpdate();
        });
        assertThrows(jakarta.persistence.PersistenceException.class, () -> serviceAt(CREATED.plusMinutes(5)).create(
                ORDER_ID, new ThanhToanDto.CreateRequest("MIEN_PHI"), "rollback-key",
                List.of(passenger("A"), passenger("B"), passenger("C")), "127.0.0.1"));
        inTransaction(em -> {
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(0L, em.createQuery("select count(t) from ThanhToan t", Long.class).getSingleResult());
            assertEquals(0L, em.createQuery("select count(v) from VeXe v", Long.class).getSingleResult());
            assertEquals(3L, em.createQuery("select count(h) from GiuCho h where h.trangThai=:state", Long.class)
                    .setParameter("state", TrangThaiGiuCho.DANG_GIU).getSingleResult());
        });
    }

    private void prepareCreateFixture() {
        inTransaction(em -> {
            em.remove(em.find(ThanhToan.class, PAYMENT_ID));
            createIdempotencyTable(em);
        });
        setRequest("POST", "/api/booking/" + ORDER_ID + "/thanh-toan");
    }

    private void createIdempotencyTable(EntityManager em) {
            em.createNativeQuery("CREATE ALIAS PG_TRY_ADVISORY_XACT_LOCK FOR 'com.datvexe.service.ThanhToanServiceTest$TestLocks.tryLock'").executeUpdate();
            em.createNativeQuery("""
                    CREATE TABLE api_idempotency (
                        user_id varchar(40), method varchar(10), path varchar(255), request_key varchar(128),
                        request_hash varchar(64), status integer, response_body text,
                        created_at timestamp with time zone, expires_at timestamp with time zone,
                        PRIMARY KEY(user_id, method, path, request_key))
                    """).executeUpdate();
    }

    private void prepareRefundFixture() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        serviceAt(CREATED.plusMinutes(5)).receive(success());
        inTransaction(em -> {
            ChinhSachHoanVe policy = new ChinhSachHoanVe();
            policy.maChinhSach = "REFUND_POLICY";
            policy.tenChinhSach = "Hoàn 80% trước 24 giờ";
            policy.soGioToiThieuTruocKhoiHanh = 24;
            policy.tyLeHoan = new BigDecimal("80");
            policy.phiHoan = BigDecimal.ZERO;
            policy.choPhepHoanVeKhuyenMai = true;
            em.persist(policy);
            em.find(DonDatVe.class, ORDER_ID).chinhSachHoanVe = policy;
            var metadata = new com.datvexe.repository.PaymentGatewayMetadataRepository(em);
            metadata.save(PAYMENT_ID, "TESTCODE", instant(CREATED));
            metadata.confirmed(PAYMENT_ID, "ORIGINAL-VNPAY");
            createIdempotencyTable(em);
        });
        setRequest("POST", "/api/booking/" + ORDER_ID + "/hoan-tien");
    }

    /** H2 thay thế hàm PostgreSQL; các ca này không kiểm thử tranh khóa. */
    public static final class TestLocks {
        public static boolean tryLock(long ignored) { return true; }
    }

    @Test
    void paymentHistorySupportsOptionalStateFilterAndPagination() {
        inTransaction(em -> {
            ThanhToan failed = new ThanhToan();
            failed.maGiaoDich = "PAYMENT_FAILED";
            failed.donDatVe = em.find(DonDatVe.class, ORDER_ID);
            failed.phuongThucThanhToan = em.find(PhuongThucThanhToan.class, "VNPAY");
            failed.soTien = BigDecimal.valueOf(290000);
            failed.trangThai = TrangThaiThanhToan.THAT_BAI;
            em.persist(failed);
        });
        setRequest("GET", "/api/booking/" + ORDER_ID + "/thanh-toan");
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));

        var firstPage = service.history(ORDER_ID, 1, 1, null, "maGiaoDich,asc");
        assertEquals(2, firstPage.totalItems());
        assertEquals(2, firstPage.totalPages());
        assertEquals(PAYMENT_ID, firstPage.items().getFirst().maGiaoDich());

        var secondPage = service.history(ORDER_ID, 2, 1, null, "maGiaoDich,asc");
        assertEquals("PAYMENT_FAILED", secondPage.items().getFirst().maGiaoDich());

        var filtered = service.history(ORDER_ID, 1, 20, " that_bai ", "maGiaoDich,desc");
        assertEquals(1, filtered.totalItems());
        assertEquals(1, filtered.items().size());
        assertEquals("PAYMENT_FAILED", filtered.items().getFirst().maGiaoDich());
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
    void missingPassengerSnapshotKeepsCollectedPaymentForReconciliation() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"))));
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));

        var result = service.receive(success());
        assertTrue(result.payment().canDoiSoat());
        assertEquals(TrangThaiThanhToan.THANH_CONG, result.payment().trangThai());

        inTransaction(em -> {
            assertEquals(TrangThaiThanhToan.THANH_CONG, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
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
    void refundCommitsSendMarkerBeforeGatewayAndReplaysWithoutSendingTwice() {
        prepareRefundFixture();
        List<String> ticketIds;
        try (EntityManager em = factory.createEntityManager()) {
            ticketIds = em.createQuery("select v.maVe from VeXe v order by v.maVe", String.class)
                    .setMaxResults(2).getResultList();
        }
        int[] calls = {0};
        HoanVeService.Gateway gateway = new HoanVeService.Gateway() {
            public String merchantCode() { return "TESTCODE"; }
            public VNPayClient.RefundResult refund(VNPayClient.RefundCommand command) {
                calls[0]++;
                try (EntityManager em = factory.createEntityManager()) {
                    var requests = new HoanVeRepository(em).forOrder(ORDER_ID);
                    assertEquals(1, requests.size());
                    assertTrue(requests.getFirst().history().stream()
                            .anyMatch(history -> "GUI_CONG".equals(history.thaoTac())));
                    assertEquals(command.requestId(), requests.getFirst().requestId());
                }
                return new VNPayClient.RefundResult(command.requestId(), command.originalReference(),
                        command.amountVnd(), "00", "00", "REFUND-ONE", instant(CREATED), true);
            }
            public VNPayClient.RefundResult query(VNPayClient.RefundCommand command, String expected) {
                throw new AssertionError("Không cần QueryDR trong luồng này.");
            }
        };
        HoanVeService refunds = new HoanVeService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), gateway);
        var request = new HoanVeDto.Request(ticketIds, "Test hoàn nhiều vé");
        var accepted = refunds.create(ORDER_ID, request, "refund-once", "127.0.0.1");
        assertEquals(202, accepted.status());
        HoanVeDto.Response snapshot = (HoanVeDto.Response) accepted.body();
        assertEquals("CHO_XU_LY", snapshot.trangThai());
        assertEquals("THANH_CONG", refunds.get(snapshot.maHoanTien()).trangThai());
        var replay = refunds.create(ORDER_ID, request, "refund-once", "127.0.0.1");
        assertEquals(HoanVeRepository.json(accepted.body()), HoanVeRepository.json(replay.body()));
        assertEquals(1, calls[0]);
        inTransaction(em -> {
            DonDatVe order = (DonDatVe) org.hibernate.Hibernate.unproxy(em.find(DonDatVe.class, ORDER_ID));
            for (String ticketId : ticketIds) {
                assertEquals(TrangThaiVe.DA_HOAN, em.find(VeXe.class, ticketId).trangThai);
            }
            assertEquals(1L, em.createQuery("select count(v) from VeXe v where v.trangThai=:state", Long.class)
                    .setParameter("state", TrangThaiVe.DA_XUAT_VE).getSingleResult());
            assertEquals(TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(0, new BigDecimal("300000").compareTo(order.tongTien));
            assertEquals(0, new BigDecimal("10000").compareTo(order.tienGiam));
        });
    }

    @Test
    void zeroRefundCompletesWithoutGatewayOrPaymentMetadata() {
        prepareRefundFixture();
        String ticketId = firstTicketId();
        inTransaction(em -> {
            em.find(ChinhSachHoanVe.class, "REFUND_POLICY").tyLeHoan = BigDecimal.ZERO;
            em.createNativeQuery("delete from payment_gateway_metadata").executeUpdate();
        });
        HoanVeService refunds = new HoanVeService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), null);
        var accepted = refunds.create(ORDER_ID, new HoanVeDto.Request(List.of(ticketId), "Hoàn 0 đồng"),
                "refund-zero", "127.0.0.1");
        String id = ((HoanVeDto.Response) accepted.body()).maHoanTien();
        assertEquals("THANH_CONG", refunds.get(id).trangThai());
        assertEquals("0", refunds.get(id).soTienHoan());
        inTransaction(em -> {
            assertEquals(TrangThaiVe.DA_HOAN, em.find(VeXe.class, ticketId).trangThai);
            assertEquals(TrangThaiThanhToan.THANH_CONG, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
        });
    }

    @Test
    void refundRejectsDuplicateAndBoardedTicketsAndPreservesDeadlineBoundary() {
        prepareRefundFixture();
        String ticketId = firstTicketId();
        HoanVeService refunds = new HoanVeService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), null);
        var request = new HoanVeDto.Request(List.of(ticketId), "Kiểm tra điều kiện");
        assertEquals(400, assertThrows(AccountException.class, () -> refunds.create(ORDER_ID,
                new HoanVeDto.Request(List.of(ticketId, ticketId), "Trùng vé"),
                "refund-duplicate", "127.0.0.1")).status());
        inTransaction(em -> em.find(VeXe.class, ticketId).trangThai = TrangThaiVe.DA_LEN_XE);
        assertEquals("REFUND_NOT_ALLOWED", assertThrows(AccountException.class,
                () -> refunds.create(ORDER_ID, request, "refund-boarded", "127.0.0.1")).code());
        inTransaction(em -> em.find(VeXe.class, ticketId).trangThai = TrangThaiVe.DA_XUAT_VE);
        Instant deadline;
        try (EntityManager em = factory.createEntityManager()) {
            ChuyenXe trip = em.find(ChuyenXe.class, TRIP_ID);
            deadline = LocalDateTime.of(trip.ngayKhoiHanh, trip.gioKhoiHanh)
                    .atZone(ZONE).toInstant().minus(Duration.ofHours(24));
        }
        HoanVeService atDeadline = new HoanVeService(factory, Clock.fixed(deadline, ZONE), null);
        assertFalse(atDeadline.preview(ORDER_ID, request).chiTiet().isEmpty());
        HoanVeService tooLate = new HoanVeService(factory, Clock.fixed(deadline.plusNanos(1), ZONE), null);
        assertEquals("REFUND_NOT_ALLOWED", assertThrows(AccountException.class,
                () -> tooLate.create(ORDER_ID, request, "refund-too-late", "127.0.0.1")).code());
        try (EntityManager em = factory.createEntityManager()) {
            assertTrue(new HoanVeRepository(em).forOrder(ORDER_ID).isEmpty());
        }
    }

    @Test
    void confirmedRefundFailureKeepsTicketActiveAndAdminRetrySendsOnlyOnce() {
        prepareRefundFixture();
        String ticketId = firstTicketId();
        int[] calls = {0};
        HoanVeService.Gateway gateway = new HoanVeService.Gateway() {
            public String merchantCode() { return "TESTCODE"; }
            public VNPayClient.RefundResult refund(VNPayClient.RefundCommand command) {
                calls[0]++;
                try (EntityManager em = factory.createEntityManager()) {
                    var entry = new HoanVeRepository(em).forOrder(ORDER_ID).getFirst();
                    assertEquals(command.requestId(), entry.requestId());
                    assertTrue(entry.history().stream().anyMatch(history ->
                            "GUI_CONG".equals(history.thaoTac())
                            && command.requestId().equals(history.maRequestCong())));
                }
                if (calls[0] == 1) {
                    return new VNPayClient.RefundResult(command.requestId(), command.originalReference(),
                            command.amountVnd(), "95", "09", "REFUND-FAILED", instant(CREATED), true);
                }
                return new VNPayClient.RefundResult(command.requestId(), command.originalReference(),
                        command.amountVnd(), "00", "00", "REFUND-RETRY", instant(CREATED), true);
            }
            public VNPayClient.RefundResult query(VNPayClient.RefundCommand command, String expected) {
                throw new AssertionError("Thất bại xác định không cần truy vấn lại.");
            }
        };
        HoanVeService refunds = new HoanVeService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), gateway);
        var accepted = refunds.create(ORDER_ID, new HoanVeDto.Request(List.of(ticketId), "Thất bại rồi thử lại"),
                "refund-failed", "127.0.0.1");
        String id = ((HoanVeDto.Response) accepted.body()).maHoanTien();
        assertEquals("THAT_BAI", refunds.get(id).trangThai());
        inTransaction(em -> {
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, ticketId).trangThai);
            assertEquals(TrangThaiThanhToan.THANH_CONG, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
        });
        assertEquals(403, assertThrows(AccountException.class,
                () -> refunds.retry(id, "customer-retry", "127.0.0.1")).status());
        inTransaction(em -> em.find(NguoiDung.class, USER_ID).loaiTaiKhoan = LoaiTaiKhoan.ADMIN);
        RequestIdentity.set(new RequestIdentity.Context(
                new CurrentUser(USER_ID, LoaiTaiKhoan.ADMIN, null, null, SESSION_ID),
                "POST", "/api/hoan-tien/" + id + "/thu-lai", null));
        var retried = refunds.retry(id, "admin-retry", "127.0.0.1");
        assertEquals("THANH_CONG", refunds.get(id).trangThai());
        var replay = refunds.retry(id, "admin-retry", "127.0.0.1");
        assertEquals(HoanVeRepository.json(retried.body()), HoanVeRepository.json(replay.body()));
        assertEquals(2, calls[0]);
    }

    @Test
    void uncertainRefundVerifiesWithoutSendingAnotherRefund() {
        prepareRefundFixture();
        String ticketId = firstTicketId();
        int[] refundsAndQueries = {0, 0};
        HoanVeService.Gateway gateway = new HoanVeService.Gateway() {
            public String merchantCode() { return "TESTCODE"; }
            public VNPayClient.RefundResult refund(VNPayClient.RefundCommand command) {
                refundsAndQueries[0]++;
                throw new AccountException(502, "PAYMENT_PROVIDER_UNAVAILABLE", "Timeout");
            }
            public VNPayClient.RefundResult query(VNPayClient.RefundCommand command, String expected) {
                refundsAndQueries[1]++;
                return new VNPayClient.RefundResult(command.requestId(), command.originalReference(),
                        command.amountVnd(), "00", "00", "REFUND-RECOVERED", instant(CREATED), true);
            }
        };
        HoanVeService service = new HoanVeService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), gateway);
        var accepted = service.create(ORDER_ID, new HoanVeDto.Request(List.of(ticketId), "Timeout"),
                "refund-timeout", "127.0.0.1");
        String id = ((HoanVeDto.Response) accepted.body()).maHoanTien();
        assertEquals("CAN_DOI_SOAT", service.get(id).trangThai());
        HoanVeService restarted = new HoanVeService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5).plusSeconds(31)), ZONE), gateway);
        assertEquals("THANH_CONG", restarted.verify(id, "127.0.0.1").trangThai());
        assertEquals(1, refundsAndQueries[0]);
        assertEquals(1, refundsAndQueries[1]);
    }

    @Test
    void savedRefundEvidenceRecoversAfterTicketUpdateFailureWithoutCallingGatewayAgain() {
        prepareRefundFixture();
        String ticketId = firstTicketId();
        inTransaction(em -> em.createNativeQuery(
                "ALTER TABLE ve_xe ADD CONSTRAINT reject_refunded_ticket CHECK (trang_thai <> 'DA_HOAN')")
                .executeUpdate());
        int[] calls = {0};
        HoanVeService.Gateway gateway = new HoanVeService.Gateway() {
            public String merchantCode() { return "TESTCODE"; }
            public VNPayClient.RefundResult refund(VNPayClient.RefundCommand command) {
                calls[0]++;
                return new VNPayClient.RefundResult(command.requestId(), command.originalReference(),
                        command.amountVnd(), "00", "00", "REFUND-EVIDENCE", instant(CREATED), true);
            }
            public VNPayClient.RefundResult query(VNPayClient.RefundCommand command, String expected) {
                throw new AssertionError("Đã có bằng chứng thành công, không gọi cổng lại.");
            }
        };
        HoanVeService service = new HoanVeService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), gateway);
        var accepted = service.create(ORDER_ID, new HoanVeDto.Request(List.of(ticketId), "DB failure"),
                "refund-evidence", "127.0.0.1");
        String id = ((HoanVeDto.Response) accepted.body()).maHoanTien();
        assertEquals("CAN_DOI_SOAT", service.get(id).trangThai());
        inTransaction(em -> {
            var entry = new HoanVeRepository(em).find(id);
            assertTrue(entry.history().stream().anyMatch(history -> "DA_XAC_MINH_CONG".equals(history.thaoTac())));
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, ticketId).trangThai);
            em.createNativeQuery("ALTER TABLE ve_xe DROP CONSTRAINT reject_refunded_ticket").executeUpdate();
        });
        HoanVeService restarted = new HoanVeService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5).plusSeconds(31)), ZONE), gateway);
        assertEquals("THANH_CONG", restarted.verify(id, "127.0.0.1").trangThai());
        assertEquals(1, calls[0]);
    }

    @Test
    void refundReadsKeepHistoryPolicyAndQrBlocking() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        serviceAt(CREATED.plusMinutes(5)).receive(success());
        String ticketId = firstTicketId();
        inTransaction(em -> em.createNativeQuery("""
                        insert into refund_request (
                            ma_hoan_tien, ma_don, ma_giao_dich_goc, ma_nguoi_yeu_cau,
                            so_tien_hoan, trang_thai, chi_tiet, lich_su, loai_hoan_toan_phan, created_at)
                        values ('REFUND_READ', :order, :payment, :user, 100, 'THAT_BAI',
                            :lines, :history, false, :created)
                        """)
                .setParameter("order", ORDER_ID)
                .setParameter("payment", PAYMENT_ID)
                .setParameter("user", USER_ID)
                .setParameter("lines", HoanVeRepository.json(List.of(
                        new HoanVeDto.Line(ticketId, "96666", "80", "0", "100"))))
                .setParameter("history", "[]")
                .setParameter("created", instant(CREATED))
                .executeUpdate());
        Clock clock = Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE);
        HoanVeService refunds = new HoanVeService(factory, clock, null);
        setRequest("GET", "/api/hoan-tien/REFUND_READ");
        HoanVeDto.Response detail = refunds.get("REFUND_READ");
        assertEquals("THAT_BAI", detail.trangThai());
        assertEquals("100", detail.soTienHoan());
        assertNotNull(detail.thoiDiemTao());
        var history = refunds.history(ticketId, 1, 20, null);
        assertEquals(1, history.totalItems());
        assertEquals("REFUND_READ", history.items().getFirst().maHoanTien());
        assertTrue(refunds.history(ticketId, 2, 20, null).items().isEmpty());
        assertFalse(refunds.policy(ticketId).duocYeuCauHoan());
        AccountException missingPolicy = assertThrows(AccountException.class, () -> refunds.preview(
                ORDER_ID, new HoanVeDto.Request(List.of(ticketId), "Test")));
        assertEquals("REFUND_POLICY_MISSING", missingPolicy.code());

        inTransaction(em -> em.createNativeQuery(
                        "update refund_request set trang_thai='CAN_DOI_SOAT' where ma_hoan_tien='REFUND_READ'")
                .executeUpdate());
        VeXeDto.Response ticket = new VeXeService(factory, clock).chiTiet(ticketId);
        assertTrue(ticket.dangHoan());
        assertNull(ticket.qrToken());
        RequestIdentity.clear();
        assertEquals(401, assertThrows(AccountException.class, () -> refunds.get("REFUND_READ")).status());
    }

    @Test
    void ticketOwnerCanReadActiveQrAndAnonymousRequestIsRejected() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        serviceAt(CREATED.plusMinutes(5)).receive(success());
        String ticketId = firstTicketId();
        VeXeService tickets = new VeXeService(factory, Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE));
        setRequest("GET", "/api/ve-xe/" + ticketId);
        VeXeDto.Response result = tickets.chiTiet(ticketId);
        assertEquals(ticketId, result.maVe());
        assertNotNull(result.qrToken());
        assertEquals("100000", result.giaGoc());
        RequestIdentity.clear();
        AccountException error = assertThrows(AccountException.class, () -> tickets.chiTiet(ticketId));
        assertEquals(401, error.status());
    }

    @Test
    void anotherCustomerCannotReadTicket() {
        contexts.put(attempt(List.of(passenger("A"), passenger("B"), passenger("C"))));
        serviceAt(CREATED.plusMinutes(5)).receive(success());
        String ticketId = firstTicketId();
        inTransaction(em -> {
            NguoiDung other = new NguoiDung();
            other.maNguoiDung = "OTHER";
            other.hoTen = "Other customer";
            other.email = "other@example.test";
            other.soDienThoai = "0900000001";
            other.matKhau = "hash";
            other.loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG;
            other.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
            em.persist(other);
            AuthSession session = new AuthSession();
            session.tokenHash = "OTHER_SESSION";
            session.csrfToken = "CSRF";
            session.nguoiDung = other;
            session.taoLuc = instant(CREATED);
            session.hetHan = instant(CREATED.plusDays(1));
            em.persist(session);
        });
        RequestIdentity.set(new RequestIdentity.Context(
                new CurrentUser("OTHER", LoaiTaiKhoan.KHACH_HANG, null, null, "OTHER_SESSION"),
                "GET", "/api/ve-xe/" + ticketId, null));
        VeXeService tickets = new VeXeService(factory, Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE));
        AccountException error = assertThrows(AccountException.class, () -> tickets.chiTiet(ticketId));
        assertEquals(403, error.status());
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

        VeXeDto.Response response = new VeXeService(
                factory, Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE)).chiTiet(ticketId);

        assertEquals("100000", response.giaGoc());
        assertEquals("96666", response.thanhTien());
        assertEquals("3334", response.tienGiam());
        assertEquals(TrangThaiVe.DA_HOAN, response.trangThai());
        assertNull(response.qrToken(), "Refunded tickets must not expose a usable QR token");
        assertFalse(response.dangHoan());
    }

    @Test
    void adminAndOperatorCanReadPaymentButCannotVerifyIt() {
        FakeGateway gateway = new FakeGateway();
        ThanhToanService service = new ThanhToanService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), contexts, gateway);

        for (LoaiTaiKhoan role : List.of(LoaiTaiKhoan.ADMIN, LoaiTaiKhoan.NHA_XE)) {
            inTransaction(em -> {
                NguoiDung user = em.find(NguoiDung.class, USER_ID);
                user.loaiTaiKhoan = role;
                user.nhaXe = em.find(NhaXe.class, "OP");
            });
            RequestIdentity.set(new RequestIdentity.Context(
                    new CurrentUser(USER_ID, role, "OP", null, SESSION_ID),
                    "POST", "/api/thanh-toan/" + PAYMENT_ID + "/xac-minh", null));

            assertEquals(PAYMENT_ID, service.get(PAYMENT_ID).maGiaoDich());
            assertEquals(403, assertThrows(AccountException.class,
                    () -> service.verify(PAYMENT_ID, "127.0.0.1")).status());
        }
        assertNull(gateway.queryCreatedAt, "Không gọi cổng khi người dùng sai quyền xác minh.");
    }

    @Test
    void paymentReadAndVerifyKeepOwnerAuthenticationAndNotFoundChecks() {
        FakeGateway gateway = new FakeGateway();
        ThanhToanService service = new ThanhToanService(factory,
                Clock.fixed(instant(CREATED.plusMinutes(5)), ZONE), contexts, gateway);
        setRequest("GET", "/api/thanh-toan/" + PAYMENT_ID);
        assertEquals(PAYMENT_ID, service.get(PAYMENT_ID).maGiaoDich());

        inTransaction(em -> {
            NguoiDung owner = new NguoiDung();
            owner.maNguoiDung = "OTHER_OWNER";
            owner.hoTen = "Other owner";
            owner.email = "owner@example.test";
            owner.soDienThoai = "0900000001";
            owner.matKhau = "hash";
            owner.loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG;
            owner.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
            em.persist(owner);
            em.find(DonDatVe.class, ORDER_ID).nguoiDat = owner;
        });
        assertEquals(403, assertThrows(AccountException.class, () -> service.get(PAYMENT_ID)).status());
        assertEquals(403, assertThrows(AccountException.class,
                () -> service.verify(PAYMENT_ID, "127.0.0.1")).status());
        assertEquals(404, assertThrows(AccountException.class, () -> service.get("MISSING")).status());
        assertEquals(404, assertThrows(AccountException.class,
                () -> service.verify("MISSING", "127.0.0.1")).status());

        RequestIdentity.clear();
        assertEquals(401, assertThrows(AccountException.class, () -> service.get(PAYMENT_ID)).status());
        assertEquals(401, assertThrows(AccountException.class,
                () -> service.verify(PAYMENT_ID, "127.0.0.1")).status());
        assertNull(gateway.queryCreatedAt);
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
    void wrongContextKeepsCollectedPaymentForReconciliation() {
        PaymentContextStore.Attempt wrongOrderContext = new PaymentContextStore.Attempt(
                PAYMENT_ID, "OTHER_ORDER", USER_ID, "VNPAY", BigDecimal.valueOf(290000),
                instant(CREATED), instant(EXPIRES), instant(EXPIRES.plusHours(24)),
                "127.0.0.1", null, List.of("A", "B", "C"),
                List.of(passenger("A"), passenger("B"), passenger("C")),
                attempt(List.of(passenger("A"), passenger("B"), passenger("C"))).chiTietGia());
        contexts.put(wrongOrderContext);

        var result = serviceAt(CREATED.plusMinutes(5)).receive(success());
        assertTrue(result.payment().canDoiSoat());
        assertEquals(TrangThaiThanhToan.THANH_CONG, result.payment().trangThai());

        inTransaction(em -> {
            assertEquals(TrangThaiThanhToan.THANH_CONG, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v where v.donDatVe.maDon=:id", Long.class)
                    .setParameter("id", ORDER_ID).getSingleResult());
        });
    }

    @Test
    void missingPriceSnapshotKeepsPaymentForReconciliationWithoutIssuingTickets() {
        List<PassengerRequest> passengers = List.of(passenger("A"), passenger("B"), passenger("C"));
        PaymentContextStore.Attempt original = attempt(passengers);
        contexts.put(new PaymentContextStore.Attempt(
                original.maGiaoDich(), original.maDon(), original.userId(), original.maPhuongThuc(),
                original.soTien(), original.createdAt(), original.expiresAt(), original.storedUntil(),
                original.clientIp(), original.urlThanhToan(), original.maGhe(), passengers, List.of()));
        ThanhToanService service = serviceAt(CREATED.plusMinutes(5));
        var first = service.receive(success());
        assertTrue(first.payment().canDoiSoat());
        assertEquals(TrangThaiThanhToan.THANH_CONG, first.payment().trangThai());
        assertTrue(service.receive(success()).duplicate());
        inTransaction(em -> {
            assertEquals(TrangThaiThanhToan.THANH_CONG, em.find(ThanhToan.class, PAYMENT_ID).trangThai);
            assertEquals(TrangThaiDon.CHO_THANH_TOAN, em.find(DonDatVe.class, ORDER_ID).trangThai);
            assertEquals(0L, em.createQuery("select count(v) from VeXe v", Long.class).getSingleResult());
        });
    }

    @Test
    void issuanceUsesSavedAllocationInsteadOfRecalculatingRemainder() {
        List<PassengerRequest> passengers = List.of(passenger("A"), passenger("B"), passenger("C"));
        PaymentContextStore.Attempt original = attempt(passengers);
        List<PriceLine> savedPrices = List.of(
                new PriceLine("A", "100000", "3333", "96667"),
                new PriceLine("B", "100000", "3334", "96666"),
                new PriceLine("C", "100000", "3333", "96667"));
        contexts.put(new PaymentContextStore.Attempt(
                original.maGiaoDich(), original.maDon(), original.userId(), original.maPhuongThuc(),
                original.soTien(), original.createdAt(), original.expiresAt(), original.storedUntil(),
                original.clientIp(), original.urlThanhToan(), original.maGhe(), passengers, savedPrices));
        var result = serviceAt(CREATED.plusMinutes(5)).receive(success());
        assertFalse(result.payment().canDoiSoat());
        inTransaction(em -> {
            DonDatVe order = em.find(DonDatVe.class, ORDER_ID);
            assertEquals(TrangThaiDon.DA_THANH_TOAN, order.trangThai);
            List<VeXe> tickets = em.createQuery("select v from VeXe v order by v.ghe.maGhe", VeXe.class)
                    .getResultList();
            assertEquals(3, tickets.size());
            assertEquals(List.of("96667", "96666", "96667"), tickets.stream()
                    .map(ticket -> ticket.giaVe.setScale(0).toPlainString()).toList());
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

    }

    private void inTransaction(java.util.function.Consumer<EntityManager> work) {
        try (EntityManager em = factory.createEntityManager()) {
            EntityTransaction tx = em.getTransaction(); tx.begin();
            try { work.accept(em); tx.commit(); }
            catch (RuntimeException error) { if (tx.isActive()) tx.rollback(); throw error; }
        }
    }
}
