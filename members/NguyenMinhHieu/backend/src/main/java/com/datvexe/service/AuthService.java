package com.datvexe.service;

import static com.datvexe.service.AccountErrors.*;

import com.datvexe.domain.AuthSession;
import com.datvexe.domain.Entities.NguoiDung;
import com.datvexe.domain.States.*;
import com.datvexe.dto.AccountInputs;
import com.datvexe.dto.AccountResponses.*;
import com.datvexe.repository.AuthSessionRepository;
import com.datvexe.repository.NguoiDungRepository;
import com.datvexe.security.*;
import com.datvexe.validation.AccountValidation;

import jakarta.persistence.*;

import java.time.*;
import java.util.*;

public final class AuthService {
    private static final String DUMMY_HASH = Passwords.hash("DummyPasswordForTiming123");
    private final AccountTransactions transactions;
    private final Clock clock;
    private final Duration lifetime;

    public AuthService(EntityManagerFactory factory) {
        this(factory, Clock.systemUTC(), Duration.ofHours(1));
    }

    public AuthService(EntityManagerFactory factory, Clock clock, Duration lifetime) {
        if (lifetime.isNegative() || lifetime.isZero())
            throw new IllegalArgumentException("Session lifetime must be positive");
        this.transactions = new AccountTransactions(factory);
        this.clock = clock;
        this.lifetime = lifetime;
    }

    private static NguoiDung active(EntityManager em, String id) {
        NguoiDung n = new NguoiDungRepository(em).lock(id);
        if (n == null
                || n.trangThai != TrangThaiTaiKhoan.HOAT_DONG
                || AccessControl.retiredEmployee(n)) throw unauthenticated();
        return n;
    }

    public Profile register(AccountInputs.DangKy input) {
        String fullName = AccountValidation.name(input.hoTen()),
                address = AccountValidation.email(input.email()),
                number = AccountValidation.phone(input.soDienThoai()),
                identity = AccountValidation.cccd(input.cccd());
        AccountValidation.birth(input.ngaySinh(), clock);
        Passwords.validate(input.matKhau(), "matKhau");
        String passwordHash = Passwords.hash(input.matKhau());
        try {
            return transactions.execute(
                    em -> {
                        NguoiDungRepository repo = new NguoiDungRepository(em);
                        if (repo.duplicate("", address, number, identity)) throw duplicate();
                        NguoiDung n = new NguoiDung();
                        n.maNguoiDung = UUID.randomUUID().toString();
                        n.hoTen = fullName;
                        n.email = address;
                        n.soDienThoai = number;
                        n.cccd = identity;
                        n.gioiTinh = input.gioiTinh();
                        n.ngaySinh = input.ngaySinh();
                        n.matKhau = passwordHash;
                        n.loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG;
                        n.trangThai = TrangThaiTaiKhoan.HOAT_DONG;
                        repo.save(n);
                        em.flush();
                        return AccountMapper.profile(n);
                    });
        } catch (PersistenceException e) {
            if (constraint(e)) throw duplicate();
            throw e;
        }
    }

    public Login login(AccountInputs.DangNhap input) {
        String address;
        try {
            address = AccountValidation.email(input.email());
        } catch (AccountException e) {
            address = "invalid@invalid.test";
        }
        final String normalized = address;
        return transactions.execute(
                em -> {
                    NguoiDung n = new NguoiDungRepository(em).byEmail(normalized);
                    if (n != null) n = new NguoiDungRepository(em).lock(n.maNguoiDung);
                    boolean valid =
                            Passwords.matches(input.matKhau(), n == null ? DUMMY_HASH : n.matKhau);
                    if (!valid
                            || n == null
                            || n.trangThai != TrangThaiTaiKhoan.HOAT_DONG
                            || AccessControl.retiredEmployee(n))
                        throw new AccountException(
                                401,
                                "UNAUTHENTICATED",
                                "Email hoặc mật khẩu không đúng, hoặc tài khoản không thể đăng"
                                    + " nhập.");
                    new AuthSessionRepository(em).deleteExpired(clock.instant());
                    String token = SessionTokens.create();
                    AuthSession session = new AuthSession();
                    session.tokenHash = SessionTokens.hash(token);
                    session.nguoiDung = n;
                    session.csrfToken = SessionTokens.create();
                    session.taoLuc = clock.instant();
                    session.hetHan = session.taoLuc.plus(lifetime);
                    new AuthSessionRepository(em).save(session);
                    return new Login(
                            token, session.csrfToken, session.hetHan, AccountMapper.profile(n));
                });
    }

    public Identity authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw unauthenticated();
        return transactions.execute(
                em -> {
                    AuthSession s = new AuthSessionRepository(em).find(SessionTokens.hash(token));
                    if (s == null || !clock.instant().isBefore(s.hetHan)) throw unauthenticated();
                    NguoiDung n = active(em, s.nguoiDung.maNguoiDung);

                    try {
                        new AuthSessionRepository(em).refresh(s);
                    } catch (EntityNotFoundException e) {
                        throw unauthenticated();
                    }
                    if (!clock.instant().isBefore(s.hetHan)) throw unauthenticated();
                    return new Identity(
                            new CurrentUser(
                                    n.maNguoiDung,
                                    n.loaiTaiKhoan,
                                    n.nhaXe == null ? null : n.nhaXe.maNhaXe,
                                    n.nhanVien == null ? null : n.nhanVien.maNhanVien,
                                    s.tokenHash),
                            s.csrfToken,
                            s.hetHan);
                });
    }

    public void logout(String token, CurrentUser user) {
        transactions.execute(
                em -> {
                    AccessControl.authenticated(em, user, clock.instant());
                    new AuthSessionRepository(em)
                            .delete(SessionTokens.hash(token), user.maNguoiDung());
                    return null;
                });
    }
}
