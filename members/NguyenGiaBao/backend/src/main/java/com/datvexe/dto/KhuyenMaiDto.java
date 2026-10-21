package com.datvexe.dto;

import com.datvexe.domain.States.LoaiGiam;
import com.datvexe.domain.States.TrangThaiKhuyenMai;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** DTO container for promotion requests and responses. */
public final class KhuyenMaiDto {
    private KhuyenMaiDto() {}

    /** Mã bắt buộc khi tạo; khi sửa, mã lấy từ URL. */
    public record Request(
            String maKhuyenMai,
            String tenKhuyenMai,
            String moTa,
            LoaiGiam loaiGiam,
            BigDecimal giaTriGiam,
            BigDecimal giamToiDa,
            BigDecimal donToiThieu,
            Integer soLuong,
            OffsetDateTime ngayBatDau,
            OffsetDateTime ngayKetThuc) {}

    public record Response(
            String maKhuyenMai,
            String tenKhuyenMai,
            String moTa,
            LoaiGiam loaiGiam,
            BigDecimal giaTriGiam,
            BigDecimal giamToiDa,
            BigDecimal donToiThieu,
            Integer soLuong,
            OffsetDateTime ngayBatDau,
            OffsetDateTime ngayKetThuc,
            Integer soLuongDaDung,
            TrangThaiKhuyenMai trangThai) {}

    public record PageResponse(
            List<Response> items,
            int page,
            int size,
            long totalItems,
            int totalPages) {}
}
