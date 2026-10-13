package com.datvexe.dto;

import com.datvexe.domain.States.*;
import java.time.LocalDate;
import java.math.BigDecimal;

public final class VehicleInputs {
    private VehicleInputs() {}
    public record LoaiXe(String maLoaiXe, String tenLoaiXe, Integer soChoNgoi, Integer soTang, String moTa) {}
    public record Xe(String maXe, String bienSo, String maLoaiXe, String maNhaXe,
                     Integer namSanXuat, LocalDate ngayDangKiem, LocalDate hanDangKiem, TrangThaiXe trangThai,
                     BigDecimal tocDo, BigDecimal dungTichXang) {
        public Xe(String maXe, String bienSo, String maLoaiXe, String maNhaXe, Integer namSanXuat,
                  LocalDate ngayDangKiem, LocalDate hanDangKiem, TrangThaiXe trangThai) {
            this(maXe, bienSo, maLoaiXe, maNhaXe, namSanXuat, ngayDangKiem, hanDangKiem, trangThai, null, null);
        }
    }
    public record Ghe(String viTri, Integer tang, TrangThaiGhe trangThai) {}
    public record PhanCong(String maPhanCong, String maChuyen, String maNhanVien, VaiTroChuyen vaiTro, String ghiChu) {
        public PhanCong(String maPhanCong, String maChuyen, String maNhanVien, VaiTroChuyen vaiTro) {
            this(maPhanCong, maChuyen, maNhanVien, vaiTro, null);
        }
    }
}
