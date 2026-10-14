package com.datvexe.service;

import static org.junit.jupiter.api.Assertions.*;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.BookingDto;
import com.datvexe.dto.HoanVeDto;
import com.datvexe.payment.VNPayClient;
import com.datvexe.repository.HoanVeRepository;
import com.datvexe.repository.PaymentGatewayMetadataRepository;
import com.datvexe.security.AccountException;
import com.datvexe.security.CurrentUser;
import com.datvexe.security.RequestIdentity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** PostgreSQL-only workflow tests; run only against the explicitly configured refund test database. */
@EnabledIfEnvironmentVariable(named = "REFUND_TEST_JDBC_URL", matches = ".+")
class HoanVeServicePostgresTest {
    private static final Instant START = Instant.parse("2026-10-14T00:00:00Z");
    private static final Instant ORIGINAL_PAYMENT_CREATED = Instant.parse("2026-10-13T10:00:00Z");
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private EntityManagerFactory factory;
    private MutableClock clock;
    private FakeGateway gateway;
    private HoanVeService service;
    private String schema;
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPassword;
    private boolean schemaCreated;

    @BeforeEach
    void setUp() throws Exception {
        jdbcUrl = requiredEnvironment("REFUND_TEST_JDBC_URL");
        jdbcUser = requiredEnvironment("REFUND_TEST_JDBC_USER");
        jdbcPassword = System.getenv().getOrDefault("REFUND_TEST_JDBC_PASSWORD", "");
        schema = "refund_t19_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        createIsolatedSchema();

        String scopedUrl = schemaScopedUrl(jdbcUrl, schema);
        factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
                "jakarta.persistence.jdbc.driver", "org.postgresql.Driver",
                "jakarta.persistence.jdbc.url", scopedUrl,
                "jakarta.persistence.jdbc.user", jdbcUser,
                "jakarta.persistence.jdbc.password", jdbcPassword,
                "hibernate.default_schema", schema,
                "hibernate.hbm2ddl.auto", "create-drop",
                "hibernate.show_sql", "false"));
        createTechnicalTables();
        clock = new MutableClock(START);
        gateway = new FakeGateway("TESTCODE");
        service = new HoanVeService(factory, clock, gateway);
    }

    @AfterEach
    void tearDown() throws Exception {
        RequestIdentity.clear();
        System.clearProperty("trip.cancellation.refund.approved");
        if (!schemaCreated) return;
        verifyGeneratedSchemaName();
        try {
            if (factory != null) dropTechnicalTables();
        } finally {
            try {
                if (factory != null && factory.isOpen()) factory.close();
            } finally {
                dropGeneratedSchema();
                schemaCreated = false;
            }
        }
    }

    @Test
    void subsetRefundKeepsOtherTicketsOrderTotalsAndPromotionSnapshot() {
        Fixture fixture = seed(3, "9000", "80", "0", true, true, true);

        HoanVeDto.Response first = response(create(fixture, fixture.userId(),
                List.of(fixture.ticketIds().get(0)), "subset-first"));
        assertEquals("THANH_CONG", first.trangThai());
        assertEquals("7200", first.soTienHoan());
        assertEquals(List.of(fixture.ticketIds().get(0)), first.chiTiet().stream().map(HoanVeDto.Line::maVe).toList());

        HoanVeDto.Response second = response(create(fixture, fixture.userId(),
                List.of(fixture.ticketIds().get(1)), "subset-second"));
        assertEquals("THANH_CONG", second.trangThai());
        assertEquals("7200", second.soTienHoan());

        try (EntityManager em = factory.createEntityManager()) {
            DonDatVe order = em.find(DonDatVe.class, fixture.orderId());
            ThanhToan payment = em.find(ThanhToan.class, fixture.paymentId());
            assertEquals(TrangThaiDon.DA_THANH_TOAN, order.trangThai);
            assertEquals(new BigDecimal("30000.00"), order.tongTien);
            assertEquals(new BigDecimal("3000.00"), order.tienGiam);
            assertEquals("PROMO_T19", order.maKhuyenMaiDaApDung);
            assertEquals(new BigDecimal("27000.00"), payment.soTien);
            assertEquals(TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN, payment.trangThai);
            assertEquals(TrangThaiVe.DA_HOAN, em.find(VeXe.class, fixture.ticketIds().get(0)).trangThai);
            assertEquals(TrangThaiVe.DA_HOAN, em.find(VeXe.class, fixture.ticketIds().get(1)).trangThai);
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, fixture.ticketIds().get(2)).trangThai);
        }
        assertEquals(2, gateway.refundCalls.get());
    }

    @Test
    void eightyPercentOfOriginalPaymentLeavesPaymentPartiallyRefunded() {
        Fixture fixture = seed(1, "10000", "80", "0", true, true, false);

        HoanVeDto.Response result = response(create(fixture, fixture.userId(), fixture.ticketIds(), "eighty-percent"));

        assertEquals("THANH_CONG", result.trangThai());
        assertEquals("8000", result.soTienHoan());
        assertNotNull(gateway.lastRefund.get());
        assertFalse(gateway.lastRefund.get().fullRefund());
        try (EntityManager em = factory.createEntityManager()) {
            assertEquals(TrangThaiThanhToan.HOAN_TIEN_MOT_PHAN,
                    em.find(ThanhToan.class, fixture.paymentId()).trangThai);
        }
    }

    @Test
    void fullAmountRefundMarksPaymentFullyRefunded() {
        Fixture fixture = seed(1, "10000", "100", "0", true, true, false);

        HoanVeDto.Response result = response(create(fixture, fixture.userId(), fixture.ticketIds(), "full-amount"));

        assertEquals("THANH_CONG", result.trangThai());
        assertEquals("10000", result.soTienHoan());
        assertTrue(gateway.lastRefund.get().fullRefund());
        try (EntityManager em = factory.createEntityManager()) {
            assertEquals(TrangThaiThanhToan.DA_HOAN_TIEN,
                    em.find(ThanhToan.class, fixture.paymentId()).trangThai);
        }
    }

    @Test
    void zeroRefundCompletesLocallyWithoutCallingGateway() {
        Fixture fixture = seed(1, "10000", "0", "0", true, true, false);

        HoanVeDto.Response result = response(create(fixture, fixture.userId(), fixture.ticketIds(), "zero-refund"));

        assertEquals("THANH_CONG", result.trangThai());
        assertEquals("0", result.soTienHoan());
        assertEquals(0, gateway.refundCalls.get());
        try (EntityManager em = factory.createEntityManager()) {
            assertEquals(TrangThaiVe.DA_HOAN, em.find(VeXe.class, fixture.ticketIds().getFirst()).trangThai);
            assertEquals(TrangThaiThanhToan.THANH_CONG,
                    em.find(ThanhToan.class, fixture.paymentId()).trangThai);
        }
    }

    @Test
    void recoveryCompletesAcceptedZeroRefundWithoutMetadataOrProviderCall() {
        Fixture fixture = seed(1, "10000", "0", "0", true, true, false);
        HoanVeDto.Response completed = response(create(fixture, fixture.userId(), fixture.ticketIds(), "zero-refund-recovery"));

        edit(em -> {
            HoanVeRepository repository = new HoanVeRepository(em);
            HoanVeRepository.Entry entry = repository.find(completed.maHoanTien());
            assertNotNull(entry);
            assertEquals(0, BigDecimal.ZERO.compareTo(entry.amount()));
            repository.update(new HoanVeRepository.Entry(entry.id(), entry.orderId(), entry.paymentId(), entry.userId(),
                    entry.policyId(), entry.reason(), entry.amount(), "CHO_XU_LY", entry.lines(), List.of(),
                    entry.requestId(), null, entry.fullRefund(), null, entry.created(), null));
            em.find(VeXe.class, fixture.ticketIds().getFirst()).trangThai = TrangThaiVe.DA_XUAT_VE;
            em.createNativeQuery("delete from payment_gateway_metadata where ma_giao_dich=:id")
                    .setParameter("id", fixture.paymentId()).executeUpdate();
        });

        HoanVeService restarted = new HoanVeService(factory, clock, gateway);
        HoanVeDto.Response recovered = withIdentity(fixture, fixture.userId(),
                () -> restarted.verify(completed.maHoanTien(), "127.0.0.1"));

        assertEquals("THANH_CONG", recovered.trangThai());
        assertEquals("0", recovered.soTienHoan());
        assertEquals(0, gateway.refundCalls.get());
        assertEquals(0, gateway.queryCalls.get());
        assertEquals(TrangThaiVe.DA_HOAN, ticketState(fixture.ticketIds().getFirst()));
    }

    @Test
    void providerFailureLeavesTicketActiveForRetry() {
        Fixture fixture = seed(1, "10000", "100", "0", true, true, false);
        gateway.refundAction = command -> result(command, "95", "09", "RFAILED", true);

        HoanVeDto.Response result = response(create(fixture, fixture.userId(), fixture.ticketIds(), "provider-failure"));

        assertEquals("THAT_BAI", result.trangThai());
        try (EntityManager em = factory.createEntityManager()) {
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, fixture.ticketIds().getFirst()).trangThai);
            assertEquals(TrangThaiThanhToan.THANH_CONG,
                    em.find(ThanhToan.class, fixture.paymentId()).trangThai);
        }
    }

    @Test
    void onlyAdminCanRetryConfirmedFailureAndSameRetryKeyReplaysWithoutAnotherGatewayCall() {
        Fixture fixture = seed(1, "10000", "100", "0", true, true, false);
        gateway.refundAction = command -> gateway.refundCalls.get() == 1
                ? result(command, "95", "09", "REFUND-FAILED", true)
                : result(command, "00", "00", "REFUND-RETRIED", true);

        HoanVeDto.Response failed = response(create(fixture, fixture.userId(), fixture.ticketIds(), "initial-failure"));
        assertEquals("THAT_BAI", failed.trangThai());
        assertEquals(1, gateway.refundCalls.get());
        String failedRequestId = gateway.lastRefund.get().requestId();

        AccountException customerDenied = assertThrows(AccountException.class, () -> withRefundRetryIdentity(
                fixture, failed.maHoanTien(), fixture.userId(), LoaiTaiKhoan.KHACH_HANG,
                () -> service.retry(failed.maHoanTien(), "customer-retry", "127.0.0.1")));
        assertEquals(403, customerDenied.status());
        assertEquals(1, gateway.refundCalls.get());

        promoteOtherFixtureUserToAdmin(fixture);
        BookingDto.Result accepted = withRefundRetryIdentity(fixture, failed.maHoanTien(), fixture.otherUserId(),
                LoaiTaiKhoan.ADMIN, () -> service.retry(failed.maHoanTien(), "admin-retry", "127.0.0.1"));
        assertEquals(202, accepted.status());
        assertEquals("CHO_XU_LY", response(accepted).trangThai());
        String retryRequestId = gateway.lastRefund.get().requestId();
        assertNotEquals(failedRequestId, retryRequestId);
        assertEquals(2, gateway.refundCalls.get());

        HoanVeDto.Response current = withRefundRetryIdentity(fixture, failed.maHoanTien(), fixture.otherUserId(),
                LoaiTaiKhoan.ADMIN, () -> service.get(failed.maHoanTien()));
        assertEquals("THANH_CONG", current.trangThai());
        assertEquals(TrangThaiVe.DA_HOAN, ticketState(fixture.ticketIds().getFirst()));

        BookingDto.Result replay = withRefundRetryIdentity(fixture, failed.maHoanTien(), fixture.otherUserId(),
                LoaiTaiKhoan.ADMIN, () -> service.retry(failed.maHoanTien(), "admin-retry", "127.0.0.1"));
        assertEquals(202, replay.status());
        assertEquals(HoanVeRepository.json(accepted.body()), HoanVeRepository.json(replay.body()));
        assertEquals(2, gateway.refundCalls.get());
    }

    @Test
    void cannotRetryUncertainRefundUntilItIsVerified() {
        Fixture fixture = seed(1, "10000", "100", "0", true, true, false);
        gateway.refundAction = command -> { throw new IllegalStateException("simulated ambiguous reply"); };
        HoanVeDto.Response uncertain = response(create(fixture, fixture.userId(), fixture.ticketIds(), "ambiguous-refund"));
        assertEquals("CAN_DOI_SOAT", uncertain.trangThai());
        assertEquals(1, gateway.refundCalls.get());
        promoteOtherFixtureUserToAdmin(fixture);

        AccountException denied = assertThrows(AccountException.class, () -> withRefundRetryIdentity(
                fixture, uncertain.maHoanTien(), fixture.otherUserId(), LoaiTaiKhoan.ADMIN,
                () -> service.retry(uncertain.maHoanTien(), "unsafe-retry", "127.0.0.1")));

        assertEquals("REFUND_RETRY_NOT_ALLOWED", denied.code());
        assertEquals(1, gateway.refundCalls.get());
        assertEquals(TrangThaiVe.DA_XUAT_VE, ticketState(fixture.ticketIds().getFirst()));
    }

    @Test
    void timeoutLeavesReconciliationLockAndBlocksAnotherRequest() {
        Fixture fixture = seed(2, "10000", "80", "0", true, true, false);
        gateway.refundAction = command -> { throw new IllegalStateException("simulated timeout"); };

        HoanVeDto.Response result = response(create(fixture, fixture.userId(),
                List.of(fixture.ticketIds().get(0)), "timeout-first"));

        assertEquals("CAN_DOI_SOAT", result.trangThai());
        AccountException blocked = assertThrows(AccountException.class, () -> create(fixture, fixture.userId(),
                List.of(fixture.ticketIds().get(1)), "timeout-second"));
        assertEquals("REFUND_IN_PROGRESS", blocked.code());
        assertEquals(1, gateway.refundCalls.get());
        try (EntityManager em = factory.createEntityManager()) {
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, fixture.ticketIds().getFirst()).trangThai);
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, fixture.ticketIds().get(1)).trangThai);
        }
    }

    @Test
    void restartedServiceVerifiesStoredRefundTransactionCorrelation() {
        Fixture fixture = seed(2, "10000", "80", "0", true, true, false);
        String refundTransaction = "REFUND-TXN-41";
        gateway.refundAction = command -> result(command, "00", "01", refundTransaction, true);
        HoanVeDto.Response initial = response(create(fixture, fixture.userId(),
                List.of(fixture.ticketIds().get(0)), "verify-pending"));
        assertEquals("CAN_DOI_SOAT", initial.trangThai());
        assertEquals(refundTransaction, initial.maGiaoDichCong());

        clock.advance(Duration.ofSeconds(31));
        FakeGateway restartedGateway = new FakeGateway("TESTCODE");
        restartedGateway.queryAction = (command, expectedId) -> {
            assertEquals(refundTransaction, expectedId);
            return result(command, "00", "00", expectedId, true);
        };
        HoanVeService restarted = new HoanVeService(factory, clock, restartedGateway);

        HoanVeDto.Response verified = withIdentity(fixture, fixture.userId(),
                () -> restarted.verify(initial.maHoanTien(), "127.0.0.1"));

        assertEquals(1, restartedGateway.queryCalls.get());
        assertEquals("THANH_CONG", verified.trangThai());
        assertEquals(refundTransaction, verified.maGiaoDichCong());
        try (EntityManager em = factory.createEntityManager()) {
            assertEquals(TrangThaiVe.DA_HOAN, em.find(VeXe.class, fixture.ticketIds().getFirst()).trangThai);
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, fixture.ticketIds().get(1)).trangThai);
        }
    }

    @Test
    void verifiedRefundEvidenceRecoversAfterTicketFinalizationFailsWithoutAnotherProviderQuery() {
        Fixture fixture = seed(1, "10000", "100", "0", true, true, false);
        String refundTransaction = "REFUND-TXN-EVIDENCE";
        gateway.refundAction = command -> result(command, "00", "00", refundTransaction, true);
        installOneTimeTicketFinalizationFailure();

        HoanVeDto.Response pending = response(create(fixture, fixture.userId(), fixture.ticketIds(), "evidence-recovery"));

        assertEquals("CAN_DOI_SOAT", pending.trangThai());
        assertEquals(1, gateway.refundCalls.get());
        try (EntityManager em = factory.createEntityManager()) {
            HoanVeRepository.Entry saved = new HoanVeRepository(em).find(pending.maHoanTien());
            assertTrue(saved.history().stream().anyMatch(item -> "DA_XAC_MINH_CONG".equals(item.thaoTac())
                    && "00/00".equals(item.maKetQua()) && refundTransaction.equals(item.maGiaoDichCong())));
            assertEquals(TrangThaiVe.DA_XUAT_VE, em.find(VeXe.class, fixture.ticketIds().getFirst()).trangThai);
        }

        clock.advance(Duration.ofSeconds(31));
        HoanVeService restarted = new HoanVeService(factory, clock, gateway);
        HoanVeDto.Response recovered = withIdentity(fixture, fixture.userId(),
                () -> restarted.verify(pending.maHoanTien(), "127.0.0.1"));

        assertEquals("THANH_CONG", recovered.trangThai());
        assertEquals(refundTransaction, recovered.maGiaoDichCong());
        assertEquals(1, gateway.refundCalls.get());
        assertEquals(0, gateway.queryCalls.get());
        try (EntityManager em = factory.createEntityManager()) {
            assertEquals(TrangThaiVe.DA_HOAN, em.find(VeXe.class, fixture.ticketIds().getFirst()).trangThai);
        }
    }

    @Test
    void missingOrChangedMerchantMetadataIsRejectedBeforeProviderCall() {
        Fixture missing = seed(1, "10000", "100", "0", true, true, false);
        edit(em -> em.createNativeQuery("delete from payment_gateway_metadata where ma_giao_dich=:id")
                .setParameter("id", missing.paymentId()).executeUpdate());
        AccountException missingContext = assertThrows(AccountException.class,
                () -> create(missing, missing.userId(), missing.ticketIds(), "missing-payment-metadata"));
        assertEquals("REFUND_CONTEXT_MISSING", missingContext.code());

        Fixture changed = seed(1, "10000", "100", "0", true, true, false);
        edit(em -> em.createNativeQuery("update payment_gateway_metadata set merchant_code='OTHER' where ma_giao_dich=:id")
                .setParameter("id", changed.paymentId()).executeUpdate());
        AccountException merchantMismatch = assertThrows(AccountException.class,
                () -> create(changed, changed.userId(), changed.ticketIds(), "changed-merchant-metadata"));
        assertEquals("REFUND_CONTEXT_MISMATCH", merchantMismatch.code());
        assertEquals(0, gateway.refundCalls.get());
    }

    @Test
    void sameIdempotencyKeyReplaysAcceptedResponseWithoutAnotherProviderCall() {
        Fixture fixture = seed(1, "10000", "80", "0", true, true, false);
        BookingDto.Result initial = createAccepted(fixture, fixture.userId(), fixture.ticketIds(), "stable-refund-key");

        BookingDto.Result replay = createAccepted(fixture, fixture.userId(), fixture.ticketIds(), "stable-refund-key");

        assertEquals(202, initial.status());
        assertEquals(202, replay.status());
        assertEquals("CHO_XU_LY", response(initial).trangThai());
        assertEquals(HoanVeRepository.json(initial.body()), HoanVeRepository.json(replay.body()));
        assertEquals(1, gateway.refundCalls.get());
    }

    @Test
    void duplicateAndExcessRequestsAreRejectedWithoutAnotherGatewayCall() {
        Fixture fixture = seed(2, "10000", "80", "0", true, true, false);

        AccountException duplicate = assertThrows(AccountException.class, () -> create(fixture, fixture.userId(),
                List.of(fixture.ticketIds().getFirst(), fixture.ticketIds().getFirst()), "duplicate-ticket"));
        assertEquals(400, duplicate.status());
        HoanVeDto.Response first = response(create(fixture, fixture.userId(),
                List.of(fixture.ticketIds().getFirst()), "first-ticket"));
        assertEquals("8000", first.soTienHoan());

        // The client supplies ticket IDs only; an existing 8,000 VND refund leaves no room for an
        // invented amount or a second 8,000 VND refund against this 20,000 VND payment only if
        // the existing refund budget is raised to represent a prior provider-side refund.
        installPriorProviderRefund(fixture, "14000");
        AccountException excessive = assertThrows(AccountException.class, () -> create(fixture, fixture.userId(),
                List.of(fixture.ticketIds().get(1)), "excess-refund"));
        assertEquals("REFUND_AMOUNT_EXCEEDED", excessive.code());
        assertEquals(1, gateway.refundCalls.get());
    }

    @Test
    void wrongOwnerCannotRequestRefundForAnotherCustomersPayment() {
        Fixture fixture = seed(1, "10000", "100", "0", true, true, false);

        AccountException denied = assertThrows(AccountException.class, () -> create(fixture,
                fixture.otherUserId(), fixture.ticketIds(), "wrong-owner"));

        assertEquals(403, denied.status());
        assertEquals(0, gateway.refundCalls.get());
    }

    @Test
    void exactTwentyFourHourBoundaryIsAllowedButOneNanosecondLaterIsNot() {
        Fixture fixture = seed(1, "10000", "80", "0", true, true, false);
        Instant departure = fixture.departure();
        clock.set(departure.minus(Duration.ofHours(24)));
        HoanVeDto.Preview boundary = withIdentity(fixture, fixture.userId(), () -> service.preview(
                fixture.orderId(), new HoanVeDto.Request(fixture.ticketIds(), null)));
        assertEquals("8000", boundary.soTienHoan());

        clock.set(departure.minus(Duration.ofHours(24)).plusNanos(1));
        AccountException tooLate = assertThrows(AccountException.class, () -> withIdentity(fixture,
                fixture.userId(), () -> service.preview(fixture.orderId(),
                        new HoanVeDto.Request(fixture.ticketIds(), null))));
        assertEquals("REFUND_NOT_ALLOWED", tooLate.code());
    }

    @Test
    void missingPolicyAndAlreadyCheckedInTicketAreRejected() {
        Fixture noPolicy = seed(1, "10000", "80", "0", true, false, false);
        AccountException missing = assertThrows(AccountException.class, () -> withIdentity(noPolicy,
                noPolicy.userId(), () -> service.preview(noPolicy.orderId(),
                        new HoanVeDto.Request(noPolicy.ticketIds(), null))));
        assertEquals("REFUND_POLICY_MISSING", missing.code());

        Fixture checkedIn = seed(1, "10000", "80", "0", true, true, false);
        edit(em -> em.find(VeXe.class, checkedIn.ticketIds().getFirst()).trangThai = TrangThaiVe.DA_LEN_XE);
        AccountException denied = assertThrows(AccountException.class, () -> withIdentity(checkedIn,
                checkedIn.userId(), () -> service.preview(checkedIn.orderId(),
                        new HoanVeDto.Request(checkedIn.ticketIds(), null))));
        assertEquals("REFUND_NOT_ALLOWED", denied.code());
        assertEquals(0, gateway.refundCalls.get());
    }

    @Test
    void concurrentDuplicateAndOtherCustomerRequestsMakeOnlyOneGatewayCall() throws Exception {
        Fixture fixture = seed(2, "10000", "80", "0", true, true, false);
        gateway.blockRefund = true;
        CountDownLatch ready = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(3);
        try {
            var first = workers.submit(() -> withIdentity(fixture, fixture.userId(), () -> service.create(
                    fixture.orderId(), request(fixture.ticketIds().getFirst()), "race-same-key", "127.0.0.1")));
            assertTrue(gateway.refundEntered.await(10, TimeUnit.SECONDS));
            ready.countDown();
            var duplicate = workers.submit(() -> {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                return withIdentity(fixture, fixture.userId(), () -> service.create(fixture.orderId(),
                        request(fixture.ticketIds().getFirst()), "race-same-key", "127.0.0.1"));
            });
            var otherCustomer = workers.submit(() -> {
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                return assertThrows(AccountException.class, () -> withIdentity(fixture, fixture.otherUserId(),
                        () -> service.create(fixture.orderId(), request(fixture.ticketIds().get(1)),
                                "race-other-customer", "127.0.0.1")));
            });

            assertEquals(202, duplicate.get(10, TimeUnit.SECONDS).status());
            assertEquals(403, otherCustomer.get(10, TimeUnit.SECONDS).status());
            assertEquals(1, gateway.refundCalls.get());
            gateway.refundRelease.countDown();
            BookingDto.Result accepted = first.get(20, TimeUnit.SECONDS);
            HoanVeDto.Response acceptedBody = response(accepted);
            assertEquals("CHO_XU_LY", acceptedBody.trangThai());
            HoanVeDto.Response current = withIdentity(fixture, fixture.userId(),
                    () -> service.get(acceptedBody.maHoanTien()));
            assertEquals("THANH_CONG", current.trangThai());
            assertEquals(1, gateway.refundCalls.get());
        } finally {
            gateway.refundRelease.countDown();
            workers.shutdownNow();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test void t21CancelsPaidTripAndRefundsNetFareWithoutChangingOtherRefundRules() throws Exception {
        Fixture f=seed(2,"9000","80","0",true,true,true);
        String trip=prepareT21(f);
        withT21Admin(f,trip,()->new TripService(factory,clock).cancel(trip,"Xe gặp sự cố"));
        HoanVeDto.Response refunded=withT21Admin(f,trip,()->service.processTripCancellation(f.orderId(),trip,"Xe gặp sự cố","127.0.0.1"));
        assertEquals("THANH_CONG",refunded.trangThai());assertEquals("18000",refunded.soTienHoan());
        assertEquals(1,gateway.refundCalls.get());
        for(String id:f.ticketIds())assertEquals(TrangThaiVe.DA_HOAN,ticketState(id));
        assertEquals(409,assertThrows(DomainException.class,()->withT21Admin(f,trip,()->new TripService(factory,clock).cancel(trip,"Hủy lần hai"))).status());
        assertEquals(1,gateway.refundCalls.get());
    }
    @Test void t21TimeoutReconcilesWithoutSendingAnotherRefund() throws Exception {
        Fixture f=seed(1,"10000","100","0",true,true,false);String trip=prepareT21(f);
        withT21Admin(f,trip,()->new TripService(factory,clock).cancel(trip,"Sự cố"));
        gateway.refundAction=c->{throw new IllegalStateException("timeout");};
        HoanVeDto.Response pending=withT21Admin(f,trip,()->service.processTripCancellation(f.orderId(),trip,"Sự cố","127.0.0.1"));
        assertEquals("CAN_DOI_SOAT",pending.trangThai());assertEquals(TrangThaiVe.DA_XUAT_VE,ticketState(f.ticketIds().getFirst()));
        clock.advance(Duration.ofSeconds(31));
        withT21Admin(f,trip,()->service.processTripCancellation(f.orderId(),trip,"Sự cố","127.0.0.1"));
        assertEquals(1,gateway.refundCalls.get());
    }
    @Test void t21DoesNotCreateAnotherRefundWhileT19RequestIsUncertain() throws Exception {
        Fixture f=seed(1,"10000","100","0",true,true,false);
        gateway.refundAction=c->{throw new IllegalStateException("timeout");};
        assertEquals("CAN_DOI_SOAT",response(create(f,f.userId(),f.ticketIds(),"prior-refund")).trangThai());
        String trip=prepareT21(f);
        withT21Admin(f,trip,()->new TripService(factory,clock).cancel(trip,"Sự cố"));
        assertThrows(AccountException.class,()->withT21Admin(f,trip,()->service.processTripCancellation(f.orderId(),trip,"Sự cố","127.0.0.1")));
        assertEquals(1,gateway.refundCalls.get());
    }
    @Test void t21UnpaidOrderReleasesHoldsAndDoesNotRequestRefund() throws Exception {
        Fixture f=seed(1,"10000","100","0",true,true,false);String trip=prepareT21(f);
        edit(em->{
            VeXe v=em.find(VeXe.class,f.ticketIds().getFirst());
            GiuCho h=new GiuCho();h.maGiuCho="HOLD";h.chuyenXe=v.chuyenXe;h.ghe=v.ghe;h.donDatVe=v.donDatVe;
            h.ngayDat=LocalDateTime.ofInstant(START,VIETNAM);h.thoiDiemBatDau=h.ngayDat;h.thoiDiemHetHan=h.ngayDat.plusMinutes(10);em.persist(h);em.remove(v);
            ((DonDatVe)org.hibernate.Hibernate.unproxy(em.find(DonDatVe.class,f.orderId()))).trangThai=TrangThaiDon.CHO_THANH_TOAN;
            em.find(ThanhToan.class,f.paymentId()).trangThai=TrangThaiThanhToan.THAT_BAI;
        });
        withT21Admin(f,trip,()->new TripService(factory,clock).cancel(trip,"Sự cố"));
        try(EntityManager em=factory.createEntityManager()) {
            assertEquals(TrangThaiGiuCho.DA_HUY,em.find(GiuCho.class,"HOLD").trangThai);
            assertEquals(TrangThaiDon.DA_HUY,((DonDatVe)org.hibernate.Hibernate.unproxy(em.find(DonDatVe.class,f.orderId()))).trangThai);
            assertEquals("KHONG_CAN_HOAN",em.createNativeQuery("select trang_thai from trip_cancellation_work").getSingleResult());
            assertEquals(0L,((Number)em.createNativeQuery("select count(*) from refund_request").getSingleResult()).longValue());
        }
        assertEquals(0,gateway.refundCalls.get());
    }
    @Test void t21ConcurrentCancellationHasOnlyOneHistoryAndWorkItem() throws Exception {
        Fixture f=seed(1,"10000","100","0",true,true,false);String trip=prepareT21(f);
        ExecutorService workers=Executors.newFixedThreadPool(2);CountDownLatch go=new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Integer> task=()->{go.await();try{withT21Admin(f,trip,()->new TripService(factory,clock).cancel(trip,"Sự cố"));return 200;}catch(DomainException e){return e.status();}};
            var a=workers.submit(task);var b=workers.submit(task);go.countDown();
            assertEquals(java.util.Set.of(200,409),java.util.Set.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));
            try(EntityManager em=factory.createEntityManager()) {
                assertEquals(1L,((Number)em.createNativeQuery("select count(*) from trip_lifecycle_history").getSingleResult()).longValue());
                assertEquals(1L,((Number)em.createNativeQuery("select count(*) from trip_cancellation_work").getSingleResult()).longValue());
            }
        }finally{workers.shutdownNow();}
    }
    @Test void t21DoesNotSendRefundBeforePolicyApproval() throws Exception {
        Fixture f=seed(1,"10000","100","0",true,true,false);String trip=prepareT21(f);
        System.setProperty("trip.cancellation.refund.approved","false");
        withT21Admin(f,trip,()->new TripService(factory,clock).cancel(trip,"Sự cố"));
        AccountException error=assertThrows(AccountException.class,()->withT21Admin(f,trip,()->service.processTripCancellation(f.orderId(),trip,"Sự cố","127.0.0.1")));
        assertEquals("CANCELLATION_REFUND_POLICY_NOT_APPROVED",error.code());assertEquals(0,gateway.refundCalls.get());
        try(EntityManager em=factory.createEntityManager()) {assertEquals(TrangThaiChuyen.DA_HUY,em.find(ChuyenXe.class,trip).trangThai);}
    }
    @Test void t21CapturedPaymentWithoutTicketCanBeRefundedOnlyOnce() throws Exception {
        Fixture f=seed(1,"10000","100","0",true,true,false);String trip=prepareT21(f);
        edit(em->{VeXe v=em.find(VeXe.class,f.ticketIds().getFirst());GiuCho h=new GiuCho();h.maGiuCho="LATE_HOLD";h.chuyenXe=v.chuyenXe;h.ghe=v.ghe;h.donDatVe=v.donDatVe;h.ngayDat=LocalDateTime.ofInstant(START,VIETNAM);h.thoiDiemBatDau=h.ngayDat;h.thoiDiemHetHan=h.ngayDat.plusMinutes(10);em.persist(h);em.remove(v);});
        withT21Admin(f,trip,()->new TripService(factory,clock).cancel(trip,"Sự cố"));
        HoanVeDto.Response first=withT21Admin(f,trip,()->service.processTripCancellation(f.orderId(),trip,"Sự cố","127.0.0.1"));
        HoanVeDto.Response replay=withT21Admin(f,trip,()->service.processTripCancellation(f.orderId(),trip,"Sự cố","127.0.0.1"));
        assertEquals("THANH_CONG",first.trangThai());assertEquals(first.maHoanTien(),replay.maHoanTien());assertEquals(1,gateway.refundCalls.get());
        var report=withT21Admin(f,trip,()->new TripService(factory,clock).lifecycleReport(trip));
        assertEquals(TrangThaiChuyen.DA_HUY,report.get("trangThai"));
        try(EntityManager em=factory.createEntityManager()) {assertEquals("HOAN_TAT",em.createNativeQuery("select trang_thai from trip_cancellation_work").getSingleResult());}
    }
    private String prepareT21(Fixture f) throws Exception {
        System.setProperty("trip.cancellation.refund.approved","true");
        promoteOtherFixtureUserToAdmin(f);
        try(Connection c=DriverManager.getConnection(schemaScopedUrl(jdbcUrl,schema),jdbcUser,jdbcPassword);Statement st=c.createStatement()) {
            var migration=java.nio.file.Path.of("docs/database/schema/013_trip_lifecycle.sql");
            if(!java.nio.file.Files.exists(migration))migration=java.nio.file.Path.of("../../docs/database/schema/013_trip_lifecycle.sql");
            st.execute(java.nio.file.Files.readString(migration));
        }
        try(EntityManager em=factory.createEntityManager()) {return em.find(VeXe.class,f.ticketIds().getFirst()).chuyenXe.maChuyen;}
    }
    private <T>T withT21Admin(Fixture f,String trip,Supplier<T> action) {
        RequestIdentity.set(new RequestIdentity.Context(new CurrentUser(f.otherUserId(),LoaiTaiKhoan.ADMIN,null,null,f.otherSessionId()),"POST","/api/quan-ly/chuyen-xe/"+trip+"/huy",null));
        try{return action.get();}finally{RequestIdentity.clear();}
    }

    private Fixture seed(int ticketCount, String fareText, String rateText, String feeText,
            boolean allowPromotion, boolean includePolicy, boolean promotionApplied) {
        String key = "F" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String userId = key + "_USER";
        String otherUserId = key + "_OTHER";
        String orderId = key + "_ORDER";
        String paymentId = key + "_PAYMENT";
        String paymentMethodId = "VNPAY";
        String policyId = key + "_POLICY";
        String cityId = key + "_CITY";
        String originId = key + "_ORIGIN";
        String destinationId = key + "_DEST";
        String routeId = key + "_ROUTE";
        String vehicleId = key + "_VEHICLE";
        String tripId = key + "_TRIP";
        String sessionId = key + "_SESSION";
        String otherSessionId = key + "_OTHER_SESSION";
        BigDecimal fare = new BigDecimal(fareText);
        BigDecimal paymentAmount = fare.multiply(BigDecimal.valueOf(ticketCount));
        Instant departure = START.plus(Duration.ofDays(10));
        LocalDateTime departureLocal = LocalDateTime.ofInstant(departure, VIETNAM);
        List<String> ticketIds = new ArrayList<>();

        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            NguoiDung user = user(userId, key + "_A", key + "_A_PHONE");
            NguoiDung other = user(otherUserId, key + "_B", key + "_B_PHONE");
            em.persist(user);
            em.persist(other);

            AuthSession session = session(sessionId, user);
            AuthSession otherSession = session(otherSessionId, other);
            em.persist(session);
            em.persist(otherSession);

            ChinhSachHoanVe policy = null;
            if (includePolicy) {
                policy = new ChinhSachHoanVe();
                policy.maChinhSach = policyId;
                policy.tenChinhSach = "T19 PostgreSQL test policy";
                policy.soGioToiThieuTruocKhoiHanh = 24;
                policy.tyLeHoan = new BigDecimal(rateText);
                policy.phiHoan = new BigDecimal(feeText);
                policy.choPhepHoanVeKhuyenMai = allowPromotion;
                policy.trangThai = TrangThaiChinhSach.HOAT_DONG;
                em.persist(policy);
            }

            TinhThanh city = new TinhThanh();
            city.maTinhThanh = cityId;
            city.tenTinhThanh = "T19 City";
            em.persist(city);
            BenXe origin = station(originId, "T19 Origin", city);
            BenXe destination = station(destinationId, "T19 Destination", city);
            em.persist(origin);
            em.persist(destination);

            TuyenXe route = new TuyenXe();
            route.maTuyen = routeId;
            route.tenTuyen = "T19 Refund Route";
            route.tramKhoiHanh = origin;
            route.tramDen = destination;
            route.thoiGianKhoiHanh = departureLocal.toLocalTime();
            route.khoangCachKM = BigDecimal.valueOf(100);
            route.thoiGianDuKien = BigDecimal.valueOf(3);
            route.giaCoBan = fare;
            route.trangThai = TrangThaiTuyenXe.DANG_KHAI_THAC;
            em.persist(route);

            Xe vehicle = new Xe();
            vehicle.maXe = vehicleId;
            vehicle.bienSo = key + "-PLATE";
            vehicle.soChoNgoi = ticketCount;
            vehicle.soDoNgoi = 1;
            vehicle.trangThai = TrangThaiXe.SAN_SANG;
            em.persist(vehicle);

            ChuyenXe trip = new ChuyenXe();
            trip.maChuyen = tripId;
            trip.tuyenXe = route;
            trip.xe = vehicle;
            trip.ngayKhoiHanh = departureLocal.toLocalDate();
            trip.gioKhoiHanh = departureLocal.toLocalTime();
            trip.gioDenDuKien = departureLocal.plusHours(3);
            trip.trangThai = TrangThaiChuyen.CHUA_KHOI_HANH;
            em.persist(trip);

            DonDatVe order = new DonDatVe();
            order.maDon = orderId;
            order.nguoiDat = user;
            order.ngayDat = LocalDateTime.ofInstant(START.minus(Duration.ofDays(2)), VIETNAM);
            order.tongTien = paymentAmount.add(promotionApplied ? new BigDecimal("3000") : BigDecimal.ZERO);
            order.tienGiam = promotionApplied ? new BigDecimal("3000") : BigDecimal.ZERO;
            order.maKhuyenMaiDaApDung = promotionApplied ? "PROMO_T19" : null;
            order.chinhSachHoanVe = policy;
            order.khachDatTen = "T19 Customer";
            order.khachDatSoDienThoai = key + "_A_PHONE";
            order.khachDatEmail = user.email;
            order.kenhDat = KenhDat.WEB;
            order.trangThai = TrangThaiDon.DA_THANH_TOAN;
            em.persist(order);

            PhuongThucThanhToan paymentMethod = em.find(PhuongThucThanhToan.class, paymentMethodId);
            if (paymentMethod == null) {
                paymentMethod = new PhuongThucThanhToan();
                paymentMethod.maPhuongThuc = paymentMethodId;
                paymentMethod.tenPhuongThuc = "T19 VNPay";
                paymentMethod.trangThai = TrangThaiPhuongThuc.HOAT_DONG;
                em.persist(paymentMethod);
            }

            ThanhToan payment = new ThanhToan();
            payment.maGiaoDich = paymentId;
            payment.donDatVe = order;
            payment.phuongThucThanhToan = paymentMethod;
            payment.soTien = paymentAmount;
            payment.noiDungThanhToan = "T19 test payment";
            payment.thoiGianThanhToan = LocalDateTime.ofInstant(ORIGINAL_PAYMENT_CREATED, VIETNAM);
            payment.trangThai = TrangThaiThanhToan.THANH_CONG;
            em.persist(payment);
            em.flush();

            for (int i = 1; i <= ticketCount; i++) {
                String seatId = key + "_SEAT" + i;
                Ghe seat = new Ghe();
                seat.maGhe = seatId;
                seat.viTri = "G" + i;
                seat.tang = 1;
                seat.trangThai = TrangThaiGhe.HOAT_DONG;
                seat.xe = vehicle;
                em.persist(seat);
            }
            em.flush();

            for (int i = 1; i <= ticketCount; i++) {
                String seatId = key + "_SEAT" + i;
                Ghe seat = em.find(Ghe.class, seatId);
                String ticketId = key + "_TICKET" + i;
                VeXe ticket = new VeXe();
                ticket.maVe = ticketId;
                ticket.maTraCuu = key.substring(1, 5) + i;
                ticket.chuyenXe = trip;
                ticket.ghe = seat;
                ticket.donDatVe = order;
                ticket.hoTenKhachHang = "T19 Passenger " + i;
                ticket.soDienThoaiKhachHang = key + "_P" + i;
                ticket.giaVe = fare;
                ticket.ngayXuatVe = LocalDateTime.ofInstant(START.minus(Duration.ofDays(1)), VIETNAM);
                ticket.trangThai = TrangThaiVe.DA_XUAT_VE;
                em.persist(ticket);
                ticketIds.add(ticketId);
            }

            em.flush();
            var metadata = new PaymentGatewayMetadataRepository(em);
            metadata.save(paymentId, gateway.merchantCode(), ORIGINAL_PAYMENT_CREATED);
            metadata.confirmed(paymentId, "123456789");
            em.getTransaction().commit();
        }
        return new Fixture(userId, sessionId, otherUserId, otherSessionId, orderId, paymentId,
                policyId, List.copyOf(ticketIds), departure);
    }

    private void installPriorProviderRefund(Fixture fixture, String amount) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            new HoanVeRepository(em).insert(new HoanVeRepository.Entry(
                    fixture.orderId() + "_PRIOR", fixture.orderId(), fixture.paymentId(), fixture.userId(),
                    fixture.policyId(), "historical provider-side adjustment", new BigDecimal(amount), "THANH_CONG",
                    List.of(new HoanVeDto.Line(fixture.ticketIds().getFirst(), "10000", "100", "0", amount)),
                    List.of(), "A".repeat(32), "REFUND-OLD", false, null,
                    START.minus(Duration.ofDays(1)), START.minus(Duration.ofHours(12))));
            em.getTransaction().commit();
        }
    }

    private NguoiDung user(String id, String contact, String phone) {
        NguoiDung user = new NguoiDung();
        user.maNguoiDung = id;
        user.hoTen = "T19 Customer";
        user.soDienThoai = phone;
        user.email = contact.toLowerCase() + "@example.test";
        user.matKhau = "test-only-not-a-login";
        user.loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG;
        user.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
        return user;
    }

    private AuthSession session(String id, NguoiDung user) {
        AuthSession session = new AuthSession();
        session.tokenHash = id;
        session.nguoiDung = user;
        session.csrfToken = id;
        session.taoLuc = START.minus(Duration.ofHours(1));
        session.hetHan = START.plus(Duration.ofDays(30));
        return session;
    }

    private BenXe station(String id, String name, TinhThanh city) {
        BenXe station = new BenXe();
        station.maBenXe = id;
        station.tenBenXe = name;
        station.diaChi = name + " address";
        station.tinhThanh = city;
        station.trangThai = TrangThaiBenXe.HOAT_DONG;
        return station;
    }

    private BookingDto.Result create(Fixture fixture, String userId, List<String> ticketIds, String key) {
        return createAndReadCurrent(fixture, userId, ticketIds, key, "127.0.0.1");
    }

    private BookingDto.Result create(Fixture fixture, String userId, List<String> ticketIds, String key, String ip) {
        return createAndReadCurrent(fixture, userId, ticketIds, key, ip);
    }

    private BookingDto.Result createAccepted(Fixture fixture, String userId, List<String> ticketIds, String key) {
        return withIdentity(fixture, userId, () -> service.create(fixture.orderId(),
                new HoanVeDto.Request(ticketIds, "customer request"), key, "127.0.0.1"));
    }

    private BookingDto.Result createAndReadCurrent(Fixture fixture, String userId, List<String> ticketIds,
            String key, String ip) {
        return withIdentity(fixture, userId, () -> {
            BookingDto.Result accepted = service.create(fixture.orderId(),
                    new HoanVeDto.Request(ticketIds, "customer request"), key, ip);
            HoanVeDto.Response acceptedBody = response(accepted);
            return new BookingDto.Result(accepted.status(), service.get(acceptedBody.maHoanTien()));
        });
    }

    private HoanVeDto.Request request(String ticketId) {
        return new HoanVeDto.Request(List.of(ticketId), "customer request");
    }

    private <T> T withIdentity(Fixture fixture, String userId, Supplier<T> action) {
        String sessionId = userId.equals(fixture.userId()) ? fixture.sessionId() : fixture.otherSessionId();
        RequestIdentity.set(new RequestIdentity.Context(
                new CurrentUser(userId, LoaiTaiKhoan.KHACH_HANG, null, null, sessionId),
                "POST", "/api/booking/" + fixture.orderId() + "/hoan-tien", null));
        try {
            return action.get();
        } finally {
            RequestIdentity.clear();
        }
    }

    private <T> T withRefundRetryIdentity(Fixture fixture, String refundId, String userId,
            LoaiTaiKhoan role, Supplier<T> action) {
        String sessionId = userId.equals(fixture.userId()) ? fixture.sessionId() : fixture.otherSessionId();
        RequestIdentity.set(new RequestIdentity.Context(
                new CurrentUser(userId, role, null, null, sessionId), "POST",
                "/api/hoan-tien/" + refundId + "/thu-lai", null));
        try {
            return action.get();
        } finally {
            RequestIdentity.clear();
        }
    }

    private void promoteOtherFixtureUserToAdmin(Fixture fixture) {
        edit(em -> em.find(NguoiDung.class, fixture.otherUserId()).loaiTaiKhoan = LoaiTaiKhoan.ADMIN);
    }

    private TrangThaiVe ticketState(String ticketId) {
        try (EntityManager em = factory.createEntityManager()) {
            return em.find(VeXe.class, ticketId).trangThai;
        }
    }

    private HoanVeDto.Response response(BookingDto.Result result) {
        assertEquals(202, result.status());
        return (HoanVeDto.Response) result.body();
    }

    private void edit(java.util.function.Consumer<EntityManager> action) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            action.accept(em);
            em.getTransaction().commit();
        }
    }

    private VNPayClient.RefundResult result(VNPayClient.RefundCommand command, String responseCode,
            String transactionStatus, String transactionNo, boolean correlated) {
        return new VNPayClient.RefundResult(command.requestId(), command.originalReference(), command.amountVnd(),
                responseCode, transactionStatus, transactionNo, clock.instant(), correlated);
    }

    private void createIsolatedSchema() throws Exception {
        verifyGeneratedSchemaName();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            schemaCreated = true;
        }
    }

    private void createTechnicalTables() throws Exception {
        String q = schema + ".";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + q + "payment_gateway_metadata ("
                    + "ma_giao_dich varchar(80) PRIMARY KEY REFERENCES " + q + "thanh_toan(ma_giao_dich),"
                    + "merchant_code varchar(32) NOT NULL, request_created_at timestamptz NOT NULL,"
                    + "provider_transaction_no varchar(80))");
            statement.execute("CREATE TABLE " + q + "refund_request ("
                    + "ma_hoan_tien varchar(40) PRIMARY KEY, ma_don varchar(40) NOT NULL REFERENCES " + q + "don_dat_ve(ma_don),"
                    + "ma_giao_dich_goc varchar(80) NOT NULL REFERENCES " + q + "thanh_toan(ma_giao_dich),"
                    + "ma_nguoi_yeu_cau varchar(40) NOT NULL REFERENCES " + q + "nguoi_dung(ma_nguoi_dung),"
                    + "ma_chinh_sach varchar(40) NOT NULL REFERENCES " + q + "chinh_sach_hoan_ve(ma_chinh_sach),"
                    + "ly_do varchar(500), so_tien_hoan numeric(14,0) NOT NULL CHECK (so_tien_hoan >= 0),"
                    + "trang_thai varchar(24) NOT NULL CHECK (trang_thai IN ('CHO_XU_LY','THANH_CONG','THAT_BAI','CAN_DOI_SOAT')),"
                    + "chi_tiet jsonb NOT NULL, lich_su jsonb NOT NULL DEFAULT '[]'::jsonb,"
                    + "ma_request_cong varchar(32) NOT NULL, ma_giao_dich_cong varchar(80),"
                    + "loai_hoan_toan_phan boolean NOT NULL, ly_do_loi varchar(120),"
                    + "created_at timestamptz NOT NULL, confirmed_at timestamptz)");
            statement.execute("CREATE UNIQUE INDEX uq_refund_pending_payment ON " + q
                    + "refund_request(ma_giao_dich_goc) WHERE trang_thai IN ('CHO_XU_LY','CAN_DOI_SOAT')");
            statement.execute("CREATE INDEX ix_refund_order ON " + q + "refund_request(ma_don,created_at)");
            statement.execute("CREATE INDEX ix_refund_ticket_details ON " + q + "refund_request USING gin(chi_tiet)");
            statement.execute("CREATE TABLE " + q + "api_idempotency ("
                    + "user_id varchar(40) NOT NULL, method varchar(10) NOT NULL, path varchar(240) NOT NULL,"
                    + "request_key varchar(128) NOT NULL, request_hash varchar(64) NOT NULL, status integer NOT NULL,"
                    + "response_body text NOT NULL, created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL,"
                    + "PRIMARY KEY (user_id,method,path,request_key))");
            statement.execute("CREATE INDEX ix_api_idempotency_expires_at ON " + q + "api_idempotency(expires_at)");
        }
    }

    private void installOneTimeTicketFinalizationFailure() {
        String q = schema + ".";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE SEQUENCE " + q + "t19_refund_failure_once");
            statement.execute("CREATE FUNCTION " + q + "t19_reject_refund_once() RETURNS trigger AS $$ "
                    + "BEGIN IF NEW.trang_thai = 'DA_HOAN' AND nextval('" + schema
                    + ".t19_refund_failure_once') = 1 THEN RAISE EXCEPTION 'simulated one-time ticket finalization failure'; "
                    + "END IF; RETURN NEW; END; $$ LANGUAGE plpgsql");
            statement.execute("CREATE TRIGGER t19_reject_refund_once BEFORE UPDATE ON " + q
                    + "ve_xe FOR EACH ROW EXECUTE FUNCTION " + q + "t19_reject_refund_once()");
        } catch (Exception error) {
            throw new IllegalStateException("Cannot install isolated one-time refund test trigger.", error);
        }
    }

    private void dropTechnicalTables() throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + schema + ".refund_request");
            statement.execute("DROP TABLE IF EXISTS " + schema + ".payment_gateway_metadata");
            statement.execute("DROP TABLE IF EXISTS " + schema + ".api_idempotency");
        }
    }

    private void dropGeneratedSchema() throws Exception {
        verifyGeneratedSchemaName();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void verifyGeneratedSchemaName() {
        if (schema == null || !schema.matches("refund_t19_[a-f0-9]{12}")) {
            throw new IllegalStateException("Refusing cleanup outside the generated T19 test schema.");
        }
    }

    private static String schemaScopedUrl(String url, String schema) {
        int query = url.indexOf('?');
        String base = query < 0 ? url : url.substring(0, query);
        List<String> parameters = new ArrayList<>();
        if (query >= 0) {
            for (String parameter : url.substring(query + 1).split("&")) {
                if (!parameter.toLowerCase(java.util.Locale.ROOT).startsWith("currentschema=")) {
                    parameters.add(parameter);
                }
            }
        }
        parameters.add("currentSchema=" + schema);
        return base + "?" + String.join("&", parameters);
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name + " for opt-in PostgreSQL tests.");
        return value;
    }

    private record Fixture(String userId, String sessionId, String otherUserId, String otherSessionId,
            String orderId, String paymentId, String policyId, List<String> ticketIds, Instant departure) {}

    private static final class MutableClock extends Clock {
        private volatile Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        private void set(Instant value) { now = value; }
        private void advance(Duration duration) { now = now.plus(duration); }
    }

    private static final class FakeGateway implements HoanVeService.Gateway {
        private final String merchant;
        private final AtomicInteger refundCalls = new AtomicInteger();
        private final AtomicInteger queryCalls = new AtomicInteger();
        private final AtomicReference<VNPayClient.RefundCommand> lastRefund = new AtomicReference<>();
        private volatile Function<VNPayClient.RefundCommand, VNPayClient.RefundResult> refundAction;
        private volatile BiFunction<VNPayClient.RefundCommand, String, VNPayClient.RefundResult> queryAction;
        private volatile boolean blockRefund;
        private volatile CountDownLatch refundEntered = new CountDownLatch(1);
        private volatile CountDownLatch refundRelease = new CountDownLatch(1);

        private FakeGateway(String merchant) {
            this.merchant = merchant;
            this.refundAction = command -> new VNPayClient.RefundResult(command.requestId(), command.originalReference(),
                    command.amountVnd(), "00", "00", "REFUND-" + refundCalls.get(), START, true);
            this.queryAction = (command, expected) -> new VNPayClient.RefundResult(command.requestId(),
                    command.originalReference(), command.amountVnd(), "00", "00", expected, START, true);
        }

        @Override public String merchantCode() { return merchant; }
        @Override public VNPayClient.RefundResult refund(VNPayClient.RefundCommand command) {
            refundCalls.incrementAndGet();
            lastRefund.set(command);
            if (blockRefund) {
                refundEntered.countDown();
                try {
                    if (!refundRelease.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("fake gateway release timed out");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("fake gateway interrupted", error);
                }
            }
            return refundAction.apply(command);
        }
        @Override public VNPayClient.RefundResult query(VNPayClient.RefundCommand command, String expectedProviderId) {
            queryCalls.incrementAndGet();
            return queryAction.apply(command, expectedProviderId);
        }
    }
}
