package com.datvexe.service;

import static com.datvexe.service.AccountErrors.*;

import com.datvexe.domain.Entities.NguoiDung;
import com.datvexe.domain.States.*;
import com.datvexe.dto.AccountInputs;
import com.datvexe.dto.AccountResponses.*;
import com.datvexe.repository.AuthSessionRepository;
import com.datvexe.repository.NguoiDungRepository;
import com.datvexe.security.*;

import jakarta.persistence.*;

import java.time.*;
import java.util.*;

public final class AdminAccountService {
    private final AccountTransactions transactions;
    private final Clock clock;

    public AdminAccountService(EntityManagerFactory factory) {
        this(factory, Clock.systemUTC());
    }

    public AdminAccountService(EntityManagerFactory factory, Clock clock) {
        this.transactions = new AccountTransactions(factory);
        this.clock = clock;
    }

    public Map<String, Object> accounts(CurrentUser user, int page, int size) {
        if (page < 1 || size < 1 || size > 100)
            throw AccountException.invalid("page", "Phân trang không hợp lệ.");
        return transactions.execute(
                em -> {
                    AccessControl.role(
                            AccessControl.authenticated(em, user, clock.instant()),
                            LoaiTaiKhoan.ADMIN);
                    long count = new NguoiDungRepository(em).count();
                    long offset = ((long) page - 1) * size;
                    var items =
                            offset >= count
                                    ? List.<Profile>of()
                                    : new NguoiDungRepository(em)
                                            .page(Math.toIntExact(offset), size).stream()
                                                    .map(AccountMapper::profile)
                                                    .toList();
                    return Map.of(
                            "items",
                            items,
                            "page",
                            page,
                            "size",
                            size,
                            "totalItems",
                            count,
                            "totalPages",
                            (count + size - 1) / size);
                });
    }

    public Profile status(CurrentUser user, String id, AccountInputs.TrangThai input) {
        if (input.trangThai() != TrangThaiTaiKhoan.KHOA
                && input.trangThai() != TrangThaiTaiKhoan.HOAT_DONG)
            throw AccountException.invalid(
                    "trangThai", "Chỉ cho phép khóa hoặc mở khóa tài khoản.");
        return transactions.execute(
                em -> {
                    AccessControl.role(
                            AccessControl.authenticated(em, user, clock.instant()),
                            LoaiTaiKhoan.ADMIN);
                    if (id.equals(user.maNguoiDung()))
                        throw new AccountException(
                                409,
                                "INVALID_STATE_TRANSITION",
                                "Không tự thay đổi trạng thái tài khoản quản trị.");
                    NguoiDung n = new NguoiDungRepository(em).lock(id);
                    if (n == null)
                        throw new AccountException(
                                404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài khoản.");
                    n.trangThai = input.trangThai();
                    new AuthSessionRepository(em).revokeAll(n);
                    return AccountMapper.profile(n);
                });
    }
}
