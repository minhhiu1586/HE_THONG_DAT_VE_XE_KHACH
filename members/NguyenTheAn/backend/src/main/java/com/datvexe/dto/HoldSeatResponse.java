package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiDon;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO phản hồi khởi tạo đơn giữ ghế thành công (HTTP 201 Created).
 * Định dạng cấu trúc thuộc tính theo chuẩn Cinema Response DTO.
 */
@JsonPropertyOrder({
        "maDon",
        "maChuyen",
        "trangThaiDon",
        "serverTime",
        "thoiDiemBatDau",
        "thoiDiemHetHan",
        "danhSachGheGiu",
        "danhSachGiuCho"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HoldSeatResponse(
        String maDon,
        String maChuyen,
        TrangThaiDon trangThaiDon,
        OffsetDateTime serverTime,
        OffsetDateTime thoiDiemBatDau,
        OffsetDateTime thoiDiemHetHan,
        List<String> danhSachGheGiu,
        List<BookingDto.HoldResponse> danhSachGiuCho
) {}
