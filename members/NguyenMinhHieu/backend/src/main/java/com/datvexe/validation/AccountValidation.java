package com.datvexe.validation;

import com.datvexe.security.AccountException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

public final class AccountValidation {
    private AccountValidation() {}

    public static String name(String value) {
        if (value == null || value.isBlank() || value.strip().length() > 160)
            throw AccountException.invalid("hoTen", "Họ tên là bắt buộc, tối đa 160 ký tự.");
        return value.strip();
    }

    public static String email(String value) {
        if (value == null
                || value.strip().length() > 254
                || !value.strip().matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+"))
            throw AccountException.invalid("email", "Email không hợp lệ.");
        return value.strip().toLowerCase(Locale.ROOT);
    }

    public static String phone(String value) {
        if (value == null)
            throw AccountException.invalid("soDienThoai", "Số điện thoại là bắt buộc.");
        String result = value.strip();
        if (result.startsWith("+84")) result = "0" + result.substring(3);
        if (!result.matches("0[35789][0-9]{8}"))
            throw AccountException.invalid(
                    "soDienThoai", "Số điện thoại Việt Nam cần 10 chữ số hoặc dạng +84.");
        return result;
    }

    public static String cccd(String value) {
        if (value == null || value.isBlank()) return null;
        String result = value.strip().toUpperCase(Locale.ROOT);
        if (!result.matches("(?:[0-9]{12}|[A-Z][0-9]{7})"))
            throw AccountException.invalid(
                    "cccd", "CCCD cần 12 chữ số; hộ chiếu cần một chữ và 7 số.");
        return result;
    }

    public static void birth(LocalDate value, Clock clock) {
        if (value != null
                && (value.isAfter(LocalDate.now(clock))
                        || value.isBefore(LocalDate.of(1900, 1, 1))))
            throw AccountException.invalid("ngaySinh", "Ngày sinh không hợp lệ.");
    }
}
