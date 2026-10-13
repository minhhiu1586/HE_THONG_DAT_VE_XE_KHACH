package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiDon;
import java.time.OffsetDateTime;
import java.util.List;

/** Initial response after creating a booking and its temporary seat holds. */
public record HoldSeatResponse(
        String maDon,
        String maChuyen,
        TrangThaiDon trangThaiDon,
        OffsetDateTime serverTime,
        OffsetDateTime thoiDiemBatDau,
        OffsetDateTime thoiDiemHetHan,
        List<String> danhSachGheGiu,
        List<BookingDto.HoldResponse> danhSachGiuCho) {}
