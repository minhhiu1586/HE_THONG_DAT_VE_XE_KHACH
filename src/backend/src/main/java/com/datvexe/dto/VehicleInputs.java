package com.datvexe.dto;

import com.datvexe.domain.States.*;
import java.time.LocalDate;
import java.math.BigDecimal;

public final class VehicleInputs {
    private VehicleInputs() {}
    public record LoaiXe(String maLoaiXe, String tenLoaiXe, Integer soChoNgoi, Integer soTang, String moTa) {}
    public record Xe(String maXe, String bienSo, String maLoaiXe, String maNhaXe,
                     Integer namSanXuat, LocalDate ngayDangKiem, LocalDate hanDangKiem, TrangThaiXe trangThai,
                     BigDecimal tocDo, BigDecimal dungTichXang, Integer soChoNgoi) {
        public Xe(String maXe, String bienSo, String maLoaiXe, String maNhaXe, Integer namSanXuat,
                  LocalDate ngayDangKiem, LocalDate hanDangKiem, TrangThaiXe trangThai, BigDecimal tocDo, BigDecimal dungTichXang) {
            this(maXe, bienSo, maLoaiXe, maNhaXe, namSanXuat, ngayDangKiem, hanDangKiem, trangThai, tocDo, dungTichXang, null);
        }
        public Xe(String maXe, String bienSo, String maLoaiXe, String maNhaXe, Integer namSanXuat,
                  LocalDate ngayDangKiem, LocalDate hanDangKiem, TrangThaiXe trangThai) {
            this(maXe, bienSo, maLoaiXe, maNhaXe, namSanXuat, ngayDangKiem, hanDangKiem, trangThai, null, null, null);
        }
    }
    public record Ghe(String viTri, Integer tang, TrangThaiGhe trangThai) {}
    public record ViTriGhe(String maGhe, String viTri, Integer tang, TrangThaiGhe trangThai) {}
    public record SoDoGhe(java.util.List<ViTriGhe> danhSachGhe) {}
    public record PhanCong(String maPhanCong, String maChuyen, String maNhanVien, VaiTroChuyen vaiTro, String ghiChu, Boolean xacNhanKeHoachLaiNghi) {
        public PhanCong(String maPhanCong,String maChuyen,String maNhanVien,VaiTroChuyen vaiTro,String ghiChu) {
            this(maPhanCong,maChuyen,maNhanVien,vaiTro,ghiChu,null);
        }
        public PhanCong(String maPhanCong, String maChuyen, String maNhanVien, VaiTroChuyen vaiTro) {
            this(maPhanCong, maChuyen, maNhanVien, vaiTro, null, null);
        }
    }
}
