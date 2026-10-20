package com.datvexe.service;

import static org.junit.jupiter.api.Assertions.*;

import com.datvexe.domain.Entities.*;
import com.datvexe.domain.States.*;
import com.datvexe.dto.AccountInputs;
import com.datvexe.security.*;

import jakarta.persistence.*;

import org.junit.jupiter.api.*;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;

class AccountServiceTest {
    private EntityManagerFactory factory;
    private AccountService accounts;
    private AuthService auth;
    private AdminAccountService adminAccounts;
    private final Instant start = Instant.parse("2026-10-11T00:00:00Z");
    private MutableClock clock;
    private static final String PASSWORD = "ValidPassword123";

    private static class MutableClock extends Clock {
        Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return now;
        }
    }

    @BeforeEach
    void setup() {
        factory =
                Persistence.createEntityManagerFactory(
                        "datvexe",
                        Map.of(
                                "jakarta.persistence.jdbc.driver",
                                "org.h2.Driver",
                                "jakarta.persistence.jdbc.url",
                                "jdbc:h2:mem:accounts;MODE=PostgreSQL",
                                "jakarta.persistence.jdbc.user",
                                "sa",
                                "jakarta.persistence.jdbc.password",
                                "",
                                "hibernate.hbm2ddl.auto",
                                "create-drop"));
        clock = new MutableClock(start);
        accounts = new AccountService(factory, clock);
        auth = new AuthService(factory, clock, Duration.ofHours(1));
        adminAccounts = new AdminAccountService(factory, clock);
    }

    @AfterEach
    void close() {
        RequestIdentity.clear();
        factory.close();
    }

    private AccountInputs.DangKy registration(String email, String phone) {
        return new AccountInputs.DangKy("Nguyễn Văn A", email, phone, PASSWORD, null, null, null);
    }

    private com.datvexe.dto.AccountResponses.Profile register(String email, String phone) {
        return auth.register(registration(email, phone));
    }

    private com.datvexe.dto.AccountResponses.Login login(String email) {
        return auth.login(new AccountInputs.DangNhap(email, PASSWORD));
    }

    private void edit(Consumer<EntityManager> action) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            action.accept(em);
            em.getTransaction().commit();
        }
    }

    private void status(int expected, Runnable action) {
        assertEquals(expected, assertThrows(AccountException.class, action::run).status());
    }

    @Test
    void registrationNormalizesIdentifiersAndStoresOnlySafeHash() {
        var profile = register(" A@EXAMPLE.TEST ", "+84901234567");
        assertEquals("a@example.test", profile.email());
        assertEquals("0901234567", profile.soDienThoai());
        assertEquals(LoaiTaiKhoan.KHACH_HANG, profile.loaiTaiKhoan());
        assertEquals(TrangThaiTaiKhoan.HOAT_DONG, profile.trangThai());
        edit(
                em -> {
                    String hash = em.find(NguoiDung.class, profile.maNguoiDung()).matKhau;
                    assertNotEquals(PASSWORD, hash);
                    assertTrue(Passwords.matches(PASSWORD, hash));
                    assertFalse(Passwords.matches("WrongPassword123", hash));
                });
        assertNotEquals(Passwords.hash(PASSWORD), Passwords.hash(PASSWORD));
    }

    @Test
    void duplicateEmailPhoneAndIdentityAreRejected() {
        auth.register(
                new AccountInputs.DangKy(
                        "A", "a@example.test", "0901234567", PASSWORD, null, null, "012345678901"));
        status(409, () -> register("A@EXAMPLE.TEST", "0901234568"));
        status(409, () -> register("b@example.test", "+84901234567"));
        status(
                409,
                () ->
                        auth.register(
                                new AccountInputs.DangKy(
                                        "B",
                                        "b@example.test",
                                        "0901234568",
                                        PASSWORD,
                                        null,
                                        null,
                                        "012345678901")));
    }

    @Test
    void missingInvalidFieldsAndWeakPasswordsFailValidation() {
        for (var input :
                List.of(
                        registration(null, "0901234567"),
                        registration("bad", "0901234567"),
                        registration("a@example.test", "123"),
                        new AccountInputs.DangKy(
                                " ", "a@example.test", "0901234567", PASSWORD, null, null, null),
                        new AccountInputs.DangKy(
                                "A",
                                "a@example.test",
                                "0901234567",
                                "1234567890",
                                null,
                                null,
                                null),
                        new AccountInputs.DangKy(
                                "A",
                                "a@example.test",
                                "0901234567",
                                PASSWORD,
                                null,
                                LocalDate.of(2030, 1, 1),
                                null),
                        new AccountInputs.DangKy(
                                "A", "a@example.test", "0901234567", PASSWORD, null, null, "bad")))
            status(400, () -> auth.register(input));
    }

    @Test
    void loginErrorsDoNotRevealWhetherAccountExistsOrIsLocked() {
        var p = register("a@example.test", "0901234567");
        var wrong =
                assertThrows(
                        AccountException.class,
                        () ->
                                auth.login(
                                        new AccountInputs.DangNhap(
                                                "a@example.test", "WrongPassword123")));
        var missing = assertThrows(AccountException.class, () -> login("missing@example.test"));
        edit(em -> em.find(NguoiDung.class, p.maNguoiDung()).trangThai = TrangThaiTaiKhoan.KHOA);
        var locked = assertThrows(AccountException.class, () -> login("a@example.test"));
        assertEquals(401, wrong.status());
        assertEquals(wrong.getMessage(), missing.getMessage());
        assertEquals(wrong.getMessage(), locked.getMessage());
        edit(
                em ->
                        em.find(NguoiDung.class, p.maNguoiDung()).trangThai =
                                TrangThaiTaiKhoan.CHO_XAC_THUC);
        status(401, () -> login("a@example.test"));
    }

    @Test
    void logoutRevokesOnlyCurrentSessionAndRejectsReplay() {
        register("a@example.test", "0901234567");
        var first = login("a@example.test");
        var second = login("a@example.test");
        assertNotEquals(first.token(), second.token());
        auth.logout(first.token(), auth.authenticate(first.token()).user());
        status(401, () -> auth.authenticate(first.token()));
        assertNotNull(auth.authenticate(second.token()));
        status(401, () -> auth.authenticate("forged"));
    }

    @Test
    void exactExpiryBoundaryAndLockedLiveAccountRejectProtectedOperations() {
        var p = register("a@example.test", "0901234567");
        var login = login("a@example.test");
        var user = auth.authenticate(login.token()).user();
        clock.now = start.plus(Duration.ofHours(1));
        status(401, () -> auth.authenticate(login.token()));
        status(401, () -> accounts.profile(user));
        clock.now = start;
        edit(em -> em.find(NguoiDung.class, p.maNguoiDung()).trangThai = TrangThaiTaiKhoan.KHOA);
        status(401, () -> auth.authenticate(login.token()));
        status(401, () -> accounts.profile(user));
    }

    @Test
    void profileUpdatesOnlyCallerAndChecksDuplicateIdentifiers() {
        var a = register("a@example.test", "0901234567");
        var b = register("b@example.test", "0901234568");
        var caller = auth.authenticate(login("a@example.test").token()).user();
        var updated =
                accounts.update(
                        caller,
                        new AccountInputs.HoSo(
                                "Tên mới",
                                "new@example.test",
                                "0901234569",
                                GioiTinh.NU,
                                LocalDate.of(2000, 1, 1),
                                "012345678901"));
        assertEquals("Tên mới", updated.hoTen());
        assertEquals(a.maNguoiDung(), updated.maNguoiDung());
        status(
                409,
                () ->
                        accounts.update(
                                caller,
                                new AccountInputs.HoSo(
                                        "A", b.email(), "0901234569", null, null, null)));
        edit(
                em -> {
                    assertEquals("Nguyễn Văn A", em.find(NguoiDung.class, b.maNguoiDung()).hoTen);
                    status(
                            403,
                            () ->
                                    AccessControl.profileOwner(
                                            em.find(NguoiDung.class, a.maNguoiDung()),
                                            b.maNguoiDung()));
                });
    }

    @Test
    void passwordChangeVerifiesOldPasswordAndRevokesEverySession() {
        register("a@example.test", "0901234567");
        var first = login("a@example.test");
        var second = login("a@example.test");
        var caller = auth.authenticate(first.token()).user();
        status(
                400,
                () ->
                        accounts.changePassword(
                                caller,
                                new AccountInputs.DoiMatKhau(
                                        "WrongPassword123", "NewPassword123")));
        assertNotNull(auth.authenticate(second.token()));
        accounts.changePassword(caller, new AccountInputs.DoiMatKhau(PASSWORD, "NewPassword123"));
        status(401, () -> auth.authenticate(first.token()));
        status(401, () -> auth.authenticate(second.token()));
        status(401, () -> login("a@example.test"));
        assertNotNull(auth.login(new AccountInputs.DangNhap("a@example.test", "NewPassword123")));
    }

    @Test
    void onlyAdminMayLockUnlockAndAllSessionsStayRevokedAfterUnlock() {
        var admin = register("admin@example.test", "0901234567");
        var customer = register("a@example.test", "0901234568");
        edit(em -> em.find(NguoiDung.class, admin.maNguoiDung()).loaiTaiKhoan = LoaiTaiKhoan.ADMIN);
        var adminUser = auth.authenticate(login("admin@example.test").token()).user();
        assertEquals(
                List.of(), adminAccounts.accounts(adminUser, Integer.MAX_VALUE, 100).get("items"));
        var session = login("a@example.test");
        var customerUser = auth.authenticate(session.token()).user();
        status(
                403,
                () ->
                        adminAccounts.status(
                                customerUser,
                                admin.maNguoiDung(),
                                new AccountInputs.TrangThai(TrangThaiTaiKhoan.KHOA)));
        status(
                409,
                () ->
                        adminAccounts.status(
                                adminUser,
                                admin.maNguoiDung(),
                                new AccountInputs.TrangThai(TrangThaiTaiKhoan.KHOA)));
        adminAccounts.status(
                adminUser,
                customer.maNguoiDung(),
                new AccountInputs.TrangThai(TrangThaiTaiKhoan.KHOA));
        status(401, () -> auth.authenticate(session.token()));
        adminAccounts.status(
                adminUser,
                customer.maNguoiDung(),
                new AccountInputs.TrangThai(TrangThaiTaiKhoan.HOAT_DONG));
        status(401, () -> auth.authenticate(session.token()));
        assertNotNull(login("a@example.test"));
    }

    @Test
    void changedRoleIsTakenFromDatabaseInsteadOfCachedIdentity() {
        var p = register("admin@example.test", "0901234567");
        edit(em -> em.find(NguoiDung.class, p.maNguoiDung()).loaiTaiKhoan = LoaiTaiKhoan.ADMIN);
        var cached = auth.authenticate(login("admin@example.test").token()).user();
        edit(
                em ->
                        em.find(NguoiDung.class, p.maNguoiDung()).loaiTaiKhoan =
                                LoaiTaiKhoan.KHACH_HANG);
        status(403, () -> adminAccounts.accounts(cached, 1, 20));
    }
}
