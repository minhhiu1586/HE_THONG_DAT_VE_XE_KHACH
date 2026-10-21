package com.datvexe.validation;

import com.datvexe.domain.Entities.NhaXe;
import com.datvexe.dto.OperatorInputs;
import com.datvexe.security.AccountException;

import java.time.LocalDate;
import java.time.ZoneId;

public final class OperatorValidation {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private OperatorValidation() {}

    public static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.strip().length() > max)
            throw AccountException.invalid(field, "Trường bắt buộc, tối đa " + max + " ký tự.");
        return value.strip();
    }

    public static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        if (value.strip().length() > max)
            throw AccountException.invalid(field, "Tối đa " + max + " ký tự.");
        return value.strip();
    }

    public static String id(String value) {
        return required(value, "maNhaXe", 40);
    }

    public static void apply(NhaXe x, OperatorInputs.NhaXe input) {
        x.tenNhaXe = required(input.tenNhaXe(), "tenNhaXe", 160);
        x.hotline = optional(input.hotline(), "hotline", 30);
        x.email =
                input.email() == null || input.email().isBlank()
                        ? null
                        : com.datvexe.validation.AccountValidation.email(input.email());
        x.diaChi = optional(input.diaChi(), "diaChi", 255);
        x.moTa = optional(input.moTa(), "moTa", 1000);
        x.logoUrl = optional(input.logoUrl(), "logoUrl", 500);
        if (input.ngayThanhLap() != null
                && input.ngayThanhLap().isAfter(LocalDate.now(BUSINESS_ZONE)))
            throw AccountException.invalid(
                    "ngayThanhLap", "Ngày thành lập không được ở tương lai.");
        x.ngayThanhLap = input.ngayThanhLap();
    }
}
