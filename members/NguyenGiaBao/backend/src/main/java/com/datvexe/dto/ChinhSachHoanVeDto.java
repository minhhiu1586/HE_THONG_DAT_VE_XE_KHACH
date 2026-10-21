package com.datvexe.dto;

import com.datvexe.domain.Entities.ChinhSachHoanVe;
import com.datvexe.domain.States.TrangThaiChinhSach;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Request and response shapes for refund policy administration and snapshots. */
public final class ChinhSachHoanVeDto {
    private ChinhSachHoanVeDto() {}

    /** Percentage and whole-VND fee are JSON numbers. */
    public record Request(
            String maChinhSach,
            String tenChinhSach,
            Integer soGioToiThieuTruocKhoiHanh,
            BigDecimal tyLeHoan,
            BigDecimal phiHoan,
            Boolean choPhepHoanVeKhuyenMai,
            TrangThaiChinhSach trangThai) {}

    public record Response(
            String maChinhSach,
            String tenChinhSach,
            int soGioToiThieuTruocKhoiHanh,
            BigDecimal tyLeHoan,
            String phiHoan,
            boolean choPhepHoanVeKhuyenMai,
            TrangThaiChinhSach trangThai) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public record PageResponse(
            List<Response> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {}

    /** Stable snapshot view shared by pricing and booking responses. */
    public static Response view(ChinhSachHoanVe policy) {
        Objects.requireNonNull(policy, "policy");
        return new Response(
                policy.maChinhSach,
                policy.tenChinhSach,
                policy.soGioToiThieuTruocKhoiHanh,
                policy.tyLeHoan,
                policy.phiHoan.toBigIntegerExact().toString(),
                policy.choPhepHoanVeKhuyenMai,
                policy.trangThai);
    }
}
