package com.datvexe.dto;

import java.math.BigDecimal;
import java.util.List;

public final class TripPassengerDto {
    private TripPassengerDto() {}

    public record PassengerItem(
            String maVe,
            String maTraCuu,
            String maGhe,
            String hoTenHanhKhach,
            String soDienThoaiHanhKhach,
            String cccdHanhKhach,
            String trangThai,
            BigDecimal giaVe,
            boolean dangHoan) {}

    public record PassengerListResponse(
            String maChuyen,
            String tenTuyen,
            String tenNhaXe,
            String bienSo,
            String ngayKhoiHanh,
            String gioKhoiHanh,
            int tongVe,
            int daXuatVe,
            int daHoan,
            int daHuy,
            List<PassengerItem> danhSachHanhKhach) {}
}
