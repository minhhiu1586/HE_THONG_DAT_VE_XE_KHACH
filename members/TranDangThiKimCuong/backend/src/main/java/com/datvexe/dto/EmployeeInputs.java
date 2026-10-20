package com.datvexe.dto;

import com.datvexe.domain.States.LoaiNhanVien;
import com.datvexe.domain.States.TrangThaiNhanVien;
import java.time.LocalDate;

public final class EmployeeInputs {
    private EmployeeInputs() {}

    public record Employee(
        String maNhanVien,
        String hoTen,
        String soDienThoai,
        String email,
        LocalDate ngaySinh,
        String maNhaXe,
        LoaiNhanVien loaiNhanVien,
        TrangThaiNhanVien trangThai,
        String soBangLai,
        LocalDate ngayHetHanBangLai
    ) {}
}
