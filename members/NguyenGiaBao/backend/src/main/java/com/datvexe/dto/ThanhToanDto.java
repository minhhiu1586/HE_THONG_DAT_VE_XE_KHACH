package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiThanhToan;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/** Contracts for creating, checking and listing payment attempts. */
public final class ThanhToanDto {
    private ThanhToanDto() {}

    public record CreateRequest(String maPhuongThuc) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    public record PaymentResponse(
            String maGiaoDich,
            String maDon,
            String maPhuongThuc,
            String donViTienTe,
            String soTien,
            TrangThaiThanhToan trangThai,
            boolean ketQuaCongDaXacDinh,
            OffsetDateTime thoiDiemTao,
            String urlThanhToan,
            OffsetDateTime thoiDiemHetHan,
            OffsetDateTime thoiGianThanhToan,
            boolean canDoiSoat) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

    public record PageResponse(
            List<PaymentResponse> items,
            int page,
            int size,
            long totalItems,
            int totalPages) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
        public PageResponse { items = List.copyOf(items == null ? List.of() : items); }
    }

    public record ReturnResponse(
            String maGiaoDich,
            boolean chuKyHopLe,
            String responseCode,
            String transactionStatus,
            String message) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }

}
