package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiPhuongThuc;

/** DTO container for payment-method requests and responses. */
public final class PhuongThucThanhToanDto {
    private PhuongThucThanhToanDto() {}

    /** Mã bắt buộc khi tạo; trạng thái dùng cho API /{ma}/trang-thai. */
    public record Request(
            String maPhuongThuc,
            String tenPhuongThuc,
            String moTa,
            TrangThaiPhuongThuc trangThai) {}

    public record Response(
            String maPhuongThuc,
            String tenPhuongThuc,
            String moTa,
            TrangThaiPhuongThuc trangThai) {}
}
