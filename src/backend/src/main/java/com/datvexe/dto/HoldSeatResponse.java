package com.datvexe.dto;

import java.time.LocalDateTime;
import java.util.List;

public class HoldSeatResponse {
    private String maDon;
    private LocalDateTime thoiDiemHetHan;
    private List<String> danhSachGheGiu;

    public HoldSeatResponse() {
    }

    public HoldSeatResponse(String maDon, LocalDateTime thoiDiemHetHan, List<String> danhSachGheGiu) {
        this.maDon = maDon;
        this.thoiDiemHetHan = thoiDiemHetHan;
        this.danhSachGheGiu = danhSachGheGiu;
    }

    public String getMaDon() {
        return maDon;
    }

    public void setMaDon(String maDon) {
        this.maDon = maDon;
    }

    public LocalDateTime getThoiDiemHetHan() {
        return thoiDiemHetHan;
    }

    public void setThoiDiemHetHan(LocalDateTime thoiDiemHetHan) {
        this.thoiDiemHetHan = thoiDiemHetHan;
    }

    public List<String> getDanhSachGheGiu() {
        return danhSachGheGiu;
    }

    public void setDanhSachGheGiu(List<String> danhSachGheGiu) {
        this.danhSachGheGiu = danhSachGheGiu;
    }
}