package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiDon;
import com.datvexe.domain.States.TrangThaiGiuCho;
import com.datvexe.domain.States.TrangThaiVe;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tập hợp các DTO Request và Response cho module Đặt Vé (Booking API).
 * Định dạng cấu trúc thuộc tính và serialization tuân thủ theo chuẩn Cinema
 * DTO.
 */
public final class BookingDto {
        private BookingDto() {
        }

        // ── REQUEST DTOs ──────────────────────────────────────────────────────────

        /**
         * DTO thông tin một hành khách tương ứng với ghế đã giữ.
         */
        @JsonPropertyOrder({
                        "maGhe",
                        "hoTen",
                        "soDienThoai",
                        "cccd"
        })
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record PassengerRequest(
                        String maGhe,
                        String hoTen,
                        String soDienThoai,
                        String cccd) implements Serializable {
        }

        /**
         * DTO cập nhật danh sách hành khách cho đơn (tương ứng PUT
         * /api/booking/{maDon}/hanh-khach).
         */
        @JsonPropertyOrder({
                        "hanhKhach"
        })
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record PassengersRequest(List<PassengerRequest> hanhKhach) {
        }

        /**
         * DTO yêu cầu đổi ghế trong đơn (tương ứng PUT /api/booking/{maDon}/doi-ghe).
         */
        @JsonPropertyOrder({
                        "maGheCu",
                        "maGheMoi"
        })
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record ChangeSeatRequest(String maGheCu, String maGheMoi) {
        }

        /**
         * DTO áp dụng mã khuyến mãi (tương ứng PUT /api/booking/{maDon}/khuyen-mai).
         */
        @JsonPropertyOrder({
                        "maKhuyenMai"
        })
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record PromotionRequest(String maKhuyenMai) {
        }

        // ── RESPONSE DTOs ─────────────────────────────────────────────────────────

        /**
         * DTO thông tin chi tiết một chỗ giữ ghế.
         */
        @JsonPropertyOrder({
                        "maGiuCho",
                        "maGhe",
                        "trangThai",
                        "thoiDiemBatDau",
                        "thoiDiemHetHan"
        })
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record HoldResponse(
                        String maGiuCho,
                        String maGhe,
                        TrangThaiGiuCho trangThai,
                        OffsetDateTime thoiDiemBatDau,
                        OffsetDateTime thoiDiemHetHan) {
        }

        /**
         * DTO thông tin vé điện tử phát hành kèm theo đơn.
         */
        @JsonPropertyOrder({
                        "maVe",
                        "maTraCuu",
                        "qrImage",
                        "maGhe",
                        "hoTen",
                        "soDienThoai",
                        "cccd",
                        "giaVe",
                        "trangThai",
                        "ngayXuatVe",
                        "dangHoan"
        })
        @JsonInclude(JsonInclude.Include.NON_NULL)
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

        /**
         * DTO thông tin giá tạm tính và khuyến mãi áp dụng.
         */
        @JsonPropertyOrder({
                        "tongTienGoc",
                        "tienGiam",
                        "tongThanhToan",
                        "maKhuyenMaiDaChon",
                        "maKhuyenMaiApDung",
                        "lyDoKhongApDung"
        })
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record PricePreview(
                        String tongTienGoc,
                        String tienGiam,
                        String tongThanhToan,
                        String maKhuyenMaiDaChon,
                        String maKhuyenMaiApDung,
                        String lyDoKhongApDung) {
        }

        /**
         * DTO phản hồi đầy đủ chi tiết đơn đặt vé.
         */
        @JsonPropertyOrder({
                        "maDon",
                        "maChuyen",
                        "trangThaiDon",
                        "khachDatTen",
                        "khachDatSoDienThoai",
                        "khachDatEmail",
                        "ghiChu",
                        "serverTime",
                        "thoiDiemBatDau",
                        "thoiDiemHetHan",
                        "danhSachGiuCho",
                        "hanhKhach",
                        "danhSachVe",
                        "giaTamTinh",
                        "coTheChotGia",
                        "giaDaChot",
                        "thongBao",
                        "emailXacNhan",
                        "chinhSachHoanVe"
        })
        @JsonInclude(JsonInclude.Include.NON_NULL)
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

        /**
         * DTO trạng thái gửi email xác nhận cho đơn đặt vé.
         */
        @JsonPropertyOrder({
                        "trangThai",
                        "diaChi",
                        "thoiDiemGui"
        })
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record EmailStatus(String trangThai, String diaChi, OffsetDateTime thoiDiemGui) {
        }

        /**
         * DTO kết quả thực thi tầng Service chuyển giao cho Controller.
         */
        public record Result(int status, Object body) {
                public static Result ok(Object body) {
                        return new Result(200, body);
                }

                public static Result created(Object body) {
                        return new Result(201, body);
                }

                public static Result noContent() {
                        return new Result(204, null);
                }
        }
}
