package com.datvexe.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import java.time.LocalDateTime;

@Entity
@Table(name = "giu_cho")
public class GiuCho {

    @Id
    @Column(name = "ma_giu_cho", length = 40)
    private String maGiuCho;

    @Column(name = "ma_chuyen", length = 40, nullable = false)
    private String maChuyen;

    @Column(name = "ma_ghe", length = 40, nullable = false)
    private String maGhe;

    @Column(name = "ma_don", length = 40)
    private String maDon;

    @Column(name = "ngay_dat", nullable = false)
    private LocalDateTime ngayDat;

    @Column(name = "thoi_diem_bat_dau", nullable = false)
    private LocalDateTime thoiDiemBatDau;

    @Column(name = "thoi_diem_het_han", nullable = false)
    private LocalDateTime thoiDiemHetHan;

    @Column(name = "trang_thai", length = 30, nullable = false)
    private String trangThai; // VD: "DANG_GIU", "DA_HUY", "DA_MUA"

    // Getters và Setters
    public String getMaGiuCho() {
        return maGiuCho;
    }

    public void setMaGiuCho(String maGiuCho) {
        this.maGiuCho = maGiuCho;
    }

    public String getMaChuyen() {
        return maChuyen;
    }

    public void setMaChuyen(String maChuyen) {
        this.maChuyen = maChuyen;
    }

    public String getMaGhe() {
        return maGhe;
    }

    public void setMaGhe(String maGhe) {
        this.maGhe = maGhe;
    }

    public String getMaDon() {
        return maDon;
    }

    public void setMaDon(String maDon) {
        this.maDon = maDon;
    }

    public LocalDateTime getNgayDat() {
        return ngayDat;
    }

    public void setNgayDat(LocalDateTime ngayDat) {
        this.ngayDat = ngayDat;
    }

    public LocalDateTime getThoiDiemBatDau() {
        return thoiDiemBatDau;
    }

    public void setThoiDiemBatDau(LocalDateTime thoiDiemBatDau) {
        this.thoiDiemBatDau = thoiDiemBatDau;
    }

    public LocalDateTime getThoiDiemHetHan() {
        return thoiDiemHetHan;
    }

    public void setThoiDiemHetHan(LocalDateTime thoiDiemHetHan) {
        this.thoiDiemHetHan = thoiDiemHetHan;
    }

    public String getTrangThai() {
        return trangThai;
    }

    public void setTrangThai(String trangThai) {
        this.trangThai = trangThai;
    }
}