package com.datvexe.service;

import com.datvexe.domain.Entities.ChinhSachHoanVe;
import com.datvexe.domain.Entities.DonDatVe;
import com.datvexe.domain.Entities.NguoiDung;
import com.datvexe.domain.States.TrangThaiChinhSach;
import com.datvexe.domain.States.TrangThaiDon;
import com.datvexe.dto.ChinhSachHoanVeDto;
import com.datvexe.security.AccountException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public final class ChinhSachHoanVeServiceTest {
    private static final AtomicInteger DATABASE_ID = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    private EntityManagerFactory factory;
    private ChinhSachHoanVeService service;

    @BeforeEach
    void setUp() {
        String database = "refund_policy_service_" + DATABASE_ID.incrementAndGet();
        factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
                "jakarta.persistence.jdbc.driver", "org.h2.Driver",
                "jakarta.persistence.jdbc.url", "jdbc:h2:mem:" + database
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "jakarta.persistence.jdbc.user", "sa",
                "jakarta.persistence.jdbc.password", "",
                "hibernate.hbm2ddl.auto", "create-drop"));
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            em.createNativeQuery("CREATE ALIAS PG_ADVISORY_XACT_LOCK FOR "
                    + "'com.datvexe.service.ChinhSachHoanVeServiceTest.advisoryLock'")
                    .executeUpdate();
            em.getTransaction().commit();
        }
        service = new ChinhSachHoanVeService(factory);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) factory.close();
    }

    @Test
    void newPolicyDefaultsPausedAndActivatingAnotherPolicySwitchesGlobally() {
        var first = service.tao(request("CS_FIRST", null, "0", "80"));
        assertEquals(TrangThaiChinhSach.TAM_NGUNG, first.trangThai());

        var second = service.tao(request("CS_SECOND", TrangThaiChinhSach.HOAT_DONG, "0", "75.50"));
        assertEquals(TrangThaiChinhSach.HOAT_DONG, second.trangThai());
        assertEquals(TrangThaiChinhSach.TAM_NGUNG, service.chiTiet("CS_FIRST").trangThai());
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            com.datvexe.repository.ChinhSachHoanVeRepository repository =
                    new com.datvexe.repository.ChinhSachHoanVeRepository(em);
            repository.lockConfiguration();
            assertEquals("CS_SECOND", repository.active().maChinhSach);
            em.getTransaction().commit();
        }

        var page = service.danhSach(1, 1);
        assertEquals(2, page.totalItems());
        assertEquals(2, page.totalPages());
        assertEquals(1, page.items().size());
    }

    @Test
    void pendingOrderFreezesTermsButAllowsStatusChanges() {
        service.tao(request("CS_USED", TrangThaiChinhSach.HOAT_DONG, "1500", "80"));
        addPendingOrder("CS_USED");

        AccountException error = assertThrows(AccountException.class, () ->
                service.capNhat("CS_USED", request(null, null, "0", "50")));
        assertEquals(409, error.status());

        assertEquals(
                TrangThaiChinhSach.TAM_NGUNG,
                service.capNhatTrangThai("CS_USED", TrangThaiChinhSach.TAM_NGUNG).trangThai());
        assertEquals("1500", service.chiTiet("CS_USED").phiHoan());
    }

    @Test
    void rejectsInvalidPolicyValuesWithAccountErrors() {
        assertEquals(400, assertThrows(AccountException.class,
                () -> service.tao(request("BAD_RATE", null, "0", "100.001"))).status());
        assertEquals(400, assertThrows(AccountException.class,
                () -> service.tao(request("BAD_FEE", null, "1.5", "80"))).status());
        assertEquals(400, assertThrows(AccountException.class,
                () -> service.danhSach(1, 101)).status());
    }

    private void addPendingOrder(String policyId) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            NguoiDung user = new NguoiDung();
            user.maNguoiDung = "REFUND_BUYER";
            user.hoTen = "Buyer";
            user.soDienThoai = "0900000000";
            user.email = "buyer@refund.test";
            user.matKhau = "test-hash";
            em.persist(user);

            DonDatVe order = new DonDatVe();
            order.maDon = "REFUND_PENDING_ORDER";
            order.nguoiDat = user;
            order.ngayDat = LocalDateTime.of(2030, 1, 1, 10, 0);
            order.khachDatTen = "Buyer";
            order.khachDatSoDienThoai = "0900000000";
            order.trangThai = TrangThaiDon.CHO_XU_LY;
            order.chinhSachHoanVe = em.find(ChinhSachHoanVe.class, policyId);
            em.persist(order);
            em.getTransaction().commit();
        }
    }

    private static ChinhSachHoanVeDto.Request request(
            String id,
            TrangThaiChinhSach status,
            String fee,
            String rate) {
        return new ChinhSachHoanVeDto.Request(
                id,
                "Hoàn vé 24 giờ",
                24,
                decimal(rate),
                fee,
                true,
                status);
    }

    private static JsonNode decimal(String value) {
        try {
            return JSON.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Long advisoryLock(Long key) {
        return key;
    }

}
