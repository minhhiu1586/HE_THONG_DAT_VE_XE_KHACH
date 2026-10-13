package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiDon;
import com.datvexe.domain.States.TrangThaiGiuCho;
import com.datvexe.domain.States.TrangThaiVe;

import java.time.OffsetDateTime;
import java.io.Serializable;
import java.util.List;

/** Request and response shapes for the booking API. */
public final class BookingDto {
    private BookingDto() {}

    public record PassengerRequest(
            String maGhe,
            String hoTen,
            String soDienThoai,
            String cccd) implements Serializable {}

    public record PassengersRequest(List<PassengerRequest> hanhKhach) {}

    public record ChangeSeatRequest(String maGheCu, String maGheMoi) {}

    public record PromotionRequest(String maKhuyenMai) {}

    public record HoldResponse(
            String maGiuCho,
            String maGhe,
            TrangThaiGiuCho trangThai,
            OffsetDateTime thoiDiemBatDau,
            OffsetDateTime thoiDiemHetHan) {}

    public record TicketResponse(
            String maVe,
            String maGhe,
            String hoTen,
            String soDienThoai,
            String cccd,
            String giaVe,
            TrangThaiVe trangThai,
            OffsetDateTime ngayXuatVe) {}

    public record PricePreview(
            String tongTienGoc,
            String tienGiam,
            String tongThanhToan,
            String maKhuyenMaiDaChon,
            String maKhuyenMaiApDung,
            String lyDoKhongApDung) {}

    public record OrderResponse(
            String maDon,
            String maChuyen,
            TrangThaiDon trangThaiDon,
            String khachDatTen,
            String khachDatSoDienThoai,
            String ghiChu,
            OffsetDateTime serverTime,
            OffsetDateTime thoiDiemBatDau,
            OffsetDateTime thoiDiemHetHan,
            List<HoldResponse> danhSachGiuCho,
            List<PassengerRequest> hanhKhach,
            List<TicketResponse> danhSachVe,
            PricePreview giaTamTinh,
            boolean coTheChotGia,
            Object giaDaChot,
            List<String> thongBao) {}

    public record Result(int status, Object body) {}
}
