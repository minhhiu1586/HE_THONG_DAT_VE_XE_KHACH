package com.datvexe.security;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import jakarta.persistence.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real row-lock races against a disposable database; creates/deletes only unique fixtures. */
@EnabledIfSystemProperty(named = "vehicle.pg.url", matches = ".+")
class PostgresAccessControlTest {
    private EntityManagerFactory factory;
    private String id;
    private CurrentUser identity;
    @BeforeEach void setup() {
        factory = Persistence.createEntityManagerFactory("datvexe", Map.of(
            "jakarta.persistence.jdbc.driver", "org.postgresql.Driver", "jakarta.persistence.jdbc.url", System.getProperty("vehicle.pg.url"),
            "jakarta.persistence.jdbc.user", System.getProperty("vehicle.pg.user"), "jakarta.persistence.jdbc.password", System.getProperty("vehicle.pg.password", ""),
            "hibernate.hbm2ddl.auto", "validate"));
        id = "qa_" + UUID.randomUUID().toString().substring(0, 20);
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var n = new NguoiDung(); n.maNguoiDung = id; n.hoTen = id; n.email = id + "@example.test";
            n.soDienThoai = id; n.matKhau = "not-a-login-fixture"; n.loaiTaiKhoan = LoaiTaiKhoan.NHAN_VIEN;
            n.trangThai = TrangThaiTaiKhoan.HOAT_DONG; n.nhanVien = em.find(NhanVien.class, "DEMO_TX"); em.persist(n);
            var session = new AuthSession(); session.tokenHash = id; session.nguoiDung = n; session.csrfToken = id;
            session.taoLuc = Instant.now(); session.hetHan = Instant.now().plusSeconds(3600); em.persist(session);
            var p = new PhanCongChuyenXe(); p.maPhanCong = id; p.chuyenXe = em.find(ChuyenXe.class, "DEMO_TRUNG");
            p.nhanVien = n.nhanVien; p.vaiTro = VaiTroChuyen.TAI_XE_CHINH; em.persist(p);
            em.getTransaction().commit(); identity = new CurrentUser(id, n.loaiTaiKhoan, null, "DEMO_TX", id);
        }
    }
    @AfterEach void cleanup() {
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            em.createQuery("delete from PhanCongChuyenXe p where p.maPhanCong=:id").setParameter("id", id).executeUpdate();
            em.createQuery("delete from AuthSession s where s.tokenHash=:id").setParameter("id", id).executeUpdate();
            em.createQuery("delete from NguoiDung n where n.maNguoiDung=:id").setParameter("id", id).executeUpdate();
            em.getTransaction().commit();
        } finally { factory.close(); }
    }
    private void race(int operation) throws Exception {
        try (var authorized = factory.createEntityManager(); var executor = Executors.newSingleThreadExecutor()) {
            authorized.getTransaction().begin();
            var caller = AccessControl.authenticated(authorized, identity, Instant.now());
            AccessControl.assigned(authorized, caller, "DEMO_TRUNG");
            var started = new CountDownLatch(1);
            var revoke = executor.submit(() -> {
                try (var em = factory.createEntityManager()) {
                    em.getTransaction().begin(); started.countDown();
                    if (operation == 1) em.createQuery("delete from PhanCongChuyenXe p where p.maPhanCong=:id").setParameter("id",id).executeUpdate();
                    else if (operation == 2) em.createQuery("update PhanCongChuyenXe p set p.chuyenXe=:trip where p.maPhanCong=:id")
                        .setParameter("trip", em.find(ChuyenXe.class, "DEMO_SAU")).setParameter("id",id).executeUpdate();
                    else em.createQuery("update NguoiDung n set n.trangThai=:state where n.maNguoiDung=:id")
                        .setParameter("state", TrangThaiTaiKhoan.KHOA).setParameter("id", id).executeUpdate();
                    em.getTransaction().commit();
                }
            });
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> revoke.get(200, TimeUnit.MILLISECONDS));
            } finally { authorized.getTransaction().commit(); }
            revoke.get(10, TimeUnit.SECONDS);
            try (var em = factory.createEntityManager()) {
                em.getTransaction().begin();
                try {
                    var error = assertThrows(AccountException.class, () -> {
                        var current = AccessControl.authenticated(em, identity, Instant.now());
                        AccessControl.assigned(em, current, "DEMO_TRUNG");
                    });
                    assertEquals(operation != 0 ? 403 : 401, error.status());
                } finally { em.getTransaction().rollback(); }
            }
        }
    }
    @Test void concurrentAccountLockWaitsForAuthorizedOperationThenRejectsNewRequest() throws Exception { race(0); }
    @Test void concurrentAssignmentRevokeWaitsForAuthorizedOperationThenRejectsNewRequest() throws Exception { race(1); }
    @Test void concurrentReassignmentRejectsAccessToTheOldTrip() throws Exception { race(2); }
}
