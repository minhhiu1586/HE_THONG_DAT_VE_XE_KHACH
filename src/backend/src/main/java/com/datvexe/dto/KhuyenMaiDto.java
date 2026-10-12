package com.datvexe.dto;

import com.datvexe.domain.States.LoaiGiam;
import com.datvexe.domain.States.TrangThaiKhuyenMai;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;

/** DTO container for promotion requests and responses. */
public final class KhuyenMaiDto {
    private KhuyenMaiDto() {}

    /**
     * Mã bắt buộc khi tạo; khi sửa, mã lấy từ URL.
     * giaTriGiam: JSON number nếu PHAN_TRAM, chuỗi VND nếu SO_TIEN_CO_DINH.
     * JsonNode giữ đúng kiểu JSON để service kiểm tra trước khi đổi sang BigDecimal.
     */
    public record Request(
            String maKhuyenMai,
            String tenKhuyenMai,
            String moTa,
            LoaiGiam loaiGiam,
            JsonNode giaTriGiam,
            String giamToiDa,
            String donToiThieu,
            Integer soLuong,
            OffsetDateTime ngayBatDau,
            OffsetDateTime ngayKetThuc) {}

    public record Response(
            String maKhuyenMai,
            String tenKhuyenMai,
            String moTa,
            LoaiGiam loaiGiam,
            JsonNode giaTriGiam,
            String giamToiDa,
            String donToiThieu,
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
