package com.datvexe.dto;

import java.math.BigDecimal;
import java.util.List;

public final class KiemVeDto {
    private KiemVeDto() {}

    public record VerifyRequest(
        String maChuyen,
        String maVe,
        String qrToken
    ) {}

    public record VerifyResponse(
        boolean hopLe,
        String maVe,
        String maChuyen,
        String maGhe,
        String maLyDo,
        String lyDo,
        String hoTenHanhKhach,
        String soDienThoaiHanhKhach,
        String cccdHanhKhach,
        String maTraCuu,
        BigDecimal giaVe,
        String tenTuyen,
        String bienSoXe
    ) {}

    public record CheckInRequest(
        String maChuyen
    ) {}

    public record CheckInResponse(
        String maVe,
        String maChuyen,
        String maGhe,
        String trangThai,
        String thoiDiemLenXe
    ) {}

    public record CheckLog(
        String maVe,
        String maChuyen,
        String maGhe,
        String ketQua,
        String thoiDiemKiem
    ) {}

    public record CheckLogPage(
        List<CheckLog> items,
        int page,
        int size,
        long totalItems,
        int totalPages
    ) {}

    public record PassengerItem(
        String maVe,
        String maTraCuu,
        String maGhe,
        String hoTenHanhKhach,
        String soDienThoaiHanhKhach,
        String cccdHanhKhach,
        String trangThai,
        BigDecimal giaVe,
        String thoiDiemLenXe,
        boolean dangHoan
    ) {}

    public record PassengerListResponse(
        String maChuyen,
        String tenTuyen,
        String tenNhaXe,
        String bienSo,
        String ngayKhoiHanh,
        String gioKhoiHanh,
        int tongVe,
        int daLenXe,
        int chuaLenXe,
        int daHoan,
        int daHuy,
        List<PassengerItem> danhSachHanhKhach
    ) {}
}
