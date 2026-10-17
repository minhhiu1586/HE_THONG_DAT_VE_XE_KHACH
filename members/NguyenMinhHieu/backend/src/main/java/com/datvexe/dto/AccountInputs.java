package com.datvexe.dto;

import com.datvexe.domain.States.*;
import java.time.LocalDate;

/** Only editable fields are accepted. Roles, status and resource ownership are server controlled. */
public final class AccountInputs {
    private AccountInputs() {}
    public record DangKy(String hoTen, String email, String soDienThoai, String matKhau,
                         GioiTinh gioiTinh, LocalDate ngaySinh, String cccd) {}
    public record DangNhap(String email, String matKhau) {}
    public record HoSo(String hoTen, String email, String soDienThoai, GioiTinh gioiTinh,
                       LocalDate ngaySinh, String cccd) {}
    public record DoiMatKhau(String matKhauHienTai, String matKhauMoi) {}
    public record TrangThai(TrangThaiTaiKhoan trangThai) {}
}
