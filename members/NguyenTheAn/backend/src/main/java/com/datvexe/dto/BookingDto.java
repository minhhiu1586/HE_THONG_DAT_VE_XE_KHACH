package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiDon;
import com.datvexe.domain.States.TrangThaiGiuCho;
import com.datvexe.domain.States.TrangThaiVe;
import com.datvexe.dto.ChinhSachHoanVeDto;

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
            String maTraCuu,
            String qrImage,
            String maGhe,
            String hoTen,
            String soDienThoai,
            String cccd,
            String giaVe,
            TrangThaiVe trangThai,
            OffsetDateTime ngayXuatVe,
            boolean dangHoan) {
        public TicketResponse(
                String maVe,
                String maTraCuu,
                String qrImage,
                String maGhe,
                String hoTen,
                String soDienThoai,
                String cccd,
                String giaVe,
                TrangThaiVe trangThai,
                OffsetDateTime ngayXuatVe) {
            this(maVe, maTraCuu, qrImage, maGhe, hoTen, soDienThoai,
                    cccd, giaVe, trangThai, ngayXuatVe, false);
        }
    }

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
            String khachDatEmail,
            String ghiChu,
            OffsetDateTime serverTime,
            OffsetDateTime thoiDiemBatDau,
            OffsetDateTime thoiDiemHetHan,
            List<HoldResponse> danhSachGiuCho,
            List<PassengerRequest> hanhKhach,
            List<TicketResponse> danhSachVe,
            PricePreview giaTamTinh,
            boolean coTheChotGia,
            PricingDto.PriceSummary giaDaChot,
            List<String> thongBao,
            EmailStatus emailXacNhan,
            ChinhSachHoanVeDto.Response chinhSachHoanVe) {
        public OrderResponse(
                String maDon,
                String maChuyen,
                TrangThaiDon trangThaiDon,
                String khachDatTen,
                String khachDatSoDienThoai,
                String khachDatEmail,
                String ghiChu,
                OffsetDateTime serverTime,
                OffsetDateTime thoiDiemBatDau,
                OffsetDateTime thoiDiemHetHan,
                List<HoldResponse> danhSachGiuCho,
                List<PassengerRequest> hanhKhach,
                List<TicketResponse> danhSachVe,
                PricePreview giaTamTinh,
                boolean coTheChotGia,
                PricingDto.PriceSummary giaDaChot,
                List<String> thongBao,
                EmailStatus emailXacNhan) {
            this(maDon, maChuyen, trangThaiDon, khachDatTen, khachDatSoDienThoai,
                    khachDatEmail, ghiChu, serverTime, thoiDiemBatDau, thoiDiemHetHan,
                    danhSachGiuCho, hanhKhach, danhSachVe, giaTamTinh, coTheChotGia,
                    giaDaChot, thongBao, emailXacNhan, null);
        }
    }

    public record EmailStatus(String trangThai, String diaChi, OffsetDateTime thoiDiemGui) {}

    public record Result(int status, Object body) {}
}
