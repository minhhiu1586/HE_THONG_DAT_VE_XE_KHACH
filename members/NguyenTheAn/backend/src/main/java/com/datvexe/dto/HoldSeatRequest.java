package com.datvexe.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * DTO yêu cầu giữ ghế và khởi tạo đơn đặt vé (tương ứng POST /api/booking/hold).
 * Định dạng chuẩn theo phong cách Cinema DTO.
 */
@JsonPropertyOrder({
        "maChuyen",
        "danhSachMaGhe"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HoldSeatRequest {

    private String maChuyen;
    private List<String> danhSachMaGhe;

    public HoldSeatRequest() {
    }

    public HoldSeatRequest(String maChuyen, List<String> danhSachMaGhe) {
        this.maChuyen = maChuyen;
        this.danhSachMaGhe = danhSachMaGhe;
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

    @Override
    public String toString() {
        return "HoldSeatRequest{" +
                "maChuyen='" + maChuyen + '\'' +
                ", danhSachMaGhe=" + danhSachMaGhe +
                '}';
    }
}