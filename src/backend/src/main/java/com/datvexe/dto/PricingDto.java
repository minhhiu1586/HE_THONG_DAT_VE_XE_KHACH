package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiDon;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/** Request, quote and frozen-price contracts for T17. */
public final class PricingDto {
    private PricingDto() {}

    public record QuoteRequest(String maKhuyenMai) {}

    public record FreezeRequest(String maBaoGia) {}

    public record PriceLine(
            String maGhe,
            String giaGoc,
            String tienGiam,
            String thanhTien) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record PromotionResult(
            boolean hopLe,
            String duocGiam) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record PriceSummary(
            String donViTienTe,
            String tongTienGoc,
            String tongTienGiam,
            String tongThanhToan,
            List<PriceLine> chiTietGia) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public PriceSummary {
            chiTietGia = List.copyOf(chiTietGia);
        }
    }

    public record QuoteResponse(
            String maBaoGia,
            String maDon,
            String donViTienTe,
            OffsetDateTime thoiDiemBaoGia,
            OffsetDateTime thoiDiemHetHan,
            String tongTienGoc,
            String tongTienGiam,
            String tongThanhToan,
            List<PriceLine> chiTietGia,
            PromotionResult ketQuaKhuyenMai) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public QuoteResponse {
            chiTietGia = List.copyOf(chiTietGia);
        }
    }

    public record QuoteSnapshot(
            String userId,
            String maDon,
            String maKhuyenMai,
            String fingerprint,
            QuoteResponse response) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
    }

    public record FreezePriceResponse(
            String maBaoGia,
            String maDon,
            TrangThaiDon trangThaiDon,
            String donViTienTe,
            OffsetDateTime thoiDiemHetHan,
            String tongTienGoc,
            String tongTienGiam,
            String tongThanhToan,
            List<PriceLine> chiTietGia,
            PromotionResult ketQuaKhuyenMai) implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public FreezePriceResponse {
            chiTietGia = List.copyOf(chiTietGia);
        }
    }
}
