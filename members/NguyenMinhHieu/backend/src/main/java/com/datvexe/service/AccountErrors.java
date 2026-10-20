package com.datvexe.service;

import com.datvexe.security.AccountException;

final class AccountErrors {
    private AccountErrors() {}

    static AccountException unauthenticated() {
        return new AccountException(
                401,
                "UNAUTHENTICATED",
                "Phiên đăng nhập không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
    }

    static AccountException duplicate() {
        return new AccountException(
                409, "ACCOUNT_ALREADY_EXISTS", "Thông tin định danh đã được sử dụng.");
    }

    static boolean constraint(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause())
            if (c instanceof org.hibernate.exception.ConstraintViolationException) return true;
        return false;
    }
}
