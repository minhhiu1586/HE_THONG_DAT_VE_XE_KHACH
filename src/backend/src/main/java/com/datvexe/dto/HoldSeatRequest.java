package com.datvexe.dto;

import java.util.List;

public class HoldSeatRequest {
    private String maChuyen;
    private List<String> danhSachMaGhe;

    public HoldSeatRequest() {
    }

    public String getMaChuyen() {
        return maChuyen;
    }

    public void setMaChuyen(String maChuyen) {
        this.maChuyen = maChuyen;
    }

    public List<String> getDanhSachMaGhe() {
        return danhSachMaGhe;
    }

    public void setDanhSachMaGhe(List<String> danhSachMaGhe) {
        this.danhSachMaGhe = danhSachMaGhe;
    }
}