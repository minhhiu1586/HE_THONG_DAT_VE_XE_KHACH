package com.datvexe.dto;

import com.datvexe.domain.States.*;
import com.datvexe.security.CurrentUser;

import java.time.Instant;
import java.time.LocalDate;

public final class AccountResponses {
    private AccountResponses() {}

    public record Profile(
            String maNguoiDung,
            String hoTen,
            String email,
            String soDienThoai,
            GioiTinh gioiTinh,
            LocalDate ngaySinh,
            String cccd,
            LoaiTaiKhoan loaiTaiKhoan,
            TrangThaiTaiKhoan trangThai) {}

    public record Login(String token, String csrfToken, Instant hetHan, Profile nguoiDung) {}

    public record Identity(CurrentUser user, String csrfToken, Instant hetHan) {}

    public record Session(Profile nguoiDung, String csrfToken, Instant hetHan) {}
}
