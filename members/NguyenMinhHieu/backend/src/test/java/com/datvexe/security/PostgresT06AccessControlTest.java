package com.datvexe.security;

import static org.junit.jupiter.api.Assertions.*;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;

import jakarta.persistence.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Instant;
import java.util.*;

@EnabledIfSystemProperty(named = "t07.t06.enabled", matches = "true")
class PostgresT06AccessControlTest {
    private EntityManagerFactory factory;
    private EntityManager em;

    @BeforeEach
    void setup() {
        factory =
                Persistence.createEntityManagerFactory(
                        "datvexe",
                        Map.of(
                                "jakarta.persistence.jdbc.driver", "org.postgresql.Driver",
                                "jakarta.persistence.jdbc.url",
                                        System.getProperty("vehicle.pg.url"),
                                "jakarta.persistence.jdbc.user",
                                        System.getProperty("vehicle.pg.user"),
                                "jakarta.persistence.jdbc.password",
                                        System.getProperty("vehicle.pg.password", ""),
                                "hibernate.hbm2ddl.auto", "validate"));
        em = factory.createEntityManager();
        em.getTransaction().begin();
    }

    @AfterEach
    void cleanup() {
        try {
            if (em.getTransaction().isActive()) em.getTransaction().rollback();
        } finally {
            em.close();
            factory.close();
        }
    }

    private NguoiDung caller(String id) {
        var n = em.find(NguoiDung.class, id);
        assertNotNull(n);

        n.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
        String hash = UUID.randomUUID().toString();
        var s = new AuthSession();
        s.tokenHash = hash;
        s.nguoiDung = n;
        s.csrfToken = hash;
        s.taoLuc = Instant.now();
        s.hetHan = Instant.now().plusSeconds(60);
        em.persist(s);
        em.flush();
        return AccessControl.authenticated(
                em, new CurrentUser(id, n.loaiTaiKhoan, null, null, hash), Instant.now());
    }

    private void forbidden(Runnable operation) {
        assertEquals(403, assertThrows(AccountException.class, operation::run).status());
    }

    @Test
    void allSeedAccountsRemainLockedAndCannotUseAnOtherwiseValidSession() {
        var accounts =
                em.createQuery(
                                "select n from NguoiDung n where n.maNguoiDung like 'T06_U_%'",
                                NguoiDung.class)
                        .getResultList();
        assertEquals(25, accounts.size());
        for (var n : accounts) {
            assertEquals(TrangThaiTaiKhoan.KHOA, n.trangThai);
            assertEquals("!T06_DISABLED_NO_AUTH!", n.matKhau);
            String hash = UUID.randomUUID().toString();
            var s = new AuthSession();
            s.tokenHash = hash;
            s.nguoiDung = n;
            s.csrfToken = hash;
            s.taoLuc = Instant.now();
            s.hetHan = Instant.now().plusSeconds(60);
            em.persist(s);
            em.flush();
            var error =
                    assertThrows(
                            AccountException.class,
                            () ->
                                    AccessControl.authenticated(
                                            em,
                                            new CurrentUser(
                                                    n.maNguoiDung,
                                                    n.loaiTaiKhoan,
                                                    null,
                                                    null,
                                                    hash),
                                            Instant.now()));
            assertEquals(401, error.status());
        }
    }

    @Test
    void realSeedOrdersAndTicketsRejectOtherCustomers() {
        var owner = caller("T06_U_1");
        var other = caller("T06_U_2");
        AccessControl.orderOwner(em, owner, "T06_O_1");
        AccessControl.ticketOwner(em, owner, "T06_V_1_1");
        forbidden(() -> AccessControl.orderOwner(em, other, "T06_O_1"));
        forbidden(() -> AccessControl.ticketOwner(em, other, "T06_V_1_1"));
        forbidden(() -> AccessControl.profileOwner(other, owner.maNguoiDung));
    }

    @Test
    void realSeedOperatorRelationsRejectCrossOperatorIds() {
        var n = em.find(NguoiDung.class, "T06_U_NX_A");
        n.nhaXe = em.find(NhaXe.class, "T06_NX_A");
        var operator = caller(n.maNguoiDung);
        AccessControl.route(em, operator, "T06_R_1");
        AccessControl.trip(em, operator, "T06_C_1_1");
        AccessControl.vehicle(em, operator, "T06_X_1");
        AccessControl.employee(em, operator, "T06_TX_1");
        forbidden(() -> AccessControl.route(em, operator, "T06_R_3"));
        forbidden(() -> AccessControl.trip(em, operator, "T06_C_7_1"));
        forbidden(() -> AccessControl.vehicle(em, operator, "T06_X_7"));
        forbidden(() -> AccessControl.employee(em, operator, "T06_TX_7"));
    }

    @Test
    void realSeedAssignmentIsRequiredAndRevocationTakesEffect() {
        var n = em.find(NguoiDung.class, "T06_U_NV_A");
        n.nhanVien = em.find(NhanVien.class, "T06_TX_1");
        var employee = caller(n.maNguoiDung);
        AccessControl.assigned(em, employee, "T06_C_1_1");
        forbidden(() -> AccessControl.assigned(em, employee, "T06_C_7_1"));
        em.remove(em.find(PhanCongChuyenXe.class, "T06_PC_1_1_TX"));
        em.flush();
        forbidden(() -> AccessControl.assigned(em, employee, "T06_C_1_1"));
    }
}
