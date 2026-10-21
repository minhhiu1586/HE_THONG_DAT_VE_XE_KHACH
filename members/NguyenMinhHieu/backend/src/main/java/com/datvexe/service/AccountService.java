package com.datvexe.service;

import static com.datvexe.service.AccountErrors.*;

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

public final class AccountService {
    private final AccountTransactions transactions;
    private final Clock clock;

    public AccountService(EntityManagerFactory factory) {
        this(factory, Clock.systemUTC());
    }

    public AccountService(EntityManagerFactory factory, Clock clock) {
        this.transactions = new AccountTransactions(factory);
        this.clock = clock;
    }

    public Profile profile(CurrentUser user) {
        return transactions.execute(
                em ->
                        AccountMapper.profile(
                                AccessControl.authenticated(em, user, clock.instant())));
    }

    public Profile update(CurrentUser user, AccountInputs.HoSo input) {
        String fullName = AccountValidation.name(input.hoTen()),
                address = AccountValidation.email(input.email()),
                number = AccountValidation.phone(input.soDienThoai()),
                identity = AccountValidation.cccd(input.cccd());
        AccountValidation.birth(input.ngaySinh(), clock);
        try {
            return transactions.execute(
                    em -> {
                        NguoiDung n = AccessControl.authenticated(em, user, clock.instant());
                        if (new NguoiDungRepository(em)
                                .duplicate(n.maNguoiDung, address, number, identity))
                            throw duplicate();
                        n.hoTen = fullName;
                        n.email = address;
                        n.soDienThoai = number;
                        n.cccd = identity;
                        n.gioiTinh = input.gioiTinh();
                        n.ngaySinh = input.ngaySinh();
                        em.flush();
                        return AccountMapper.profile(n);
                    });
        } catch (PersistenceException e) {
            if (constraint(e)) throw duplicate();
            throw e;
        }
    }

    public void changePassword(CurrentUser user, AccountInputs.DoiMatKhau input) {
        Passwords.validate(input.matKhauMoi(), "matKhauMoi");
        transactions.execute(
                em -> {
                    NguoiDung n = AccessControl.authenticated(em, user, clock.instant());
                    if (!Passwords.matches(input.matKhauHienTai(), n.matKhau))
                        throw AccountException.invalid(
                                "matKhauHienTai", "Mật khẩu hiện tại không đúng.");
                    if (Passwords.matches(input.matKhauMoi(), n.matKhau))
                        throw AccountException.invalid(
                                "matKhauMoi", "Mật khẩu mới phải khác mật khẩu hiện tại.");
                    n.matKhau = Passwords.hash(input.matKhauMoi());
                    new AuthSessionRepository(em).revokeAll(n);
                    return null;
                });
    }
}
