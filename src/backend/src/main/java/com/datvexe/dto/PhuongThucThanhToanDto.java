package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiPhuongThuc;

/** DTO container for payment-method requests and responses. */
public final class PhuongThucThanhToanDto {
    private PhuongThucThanhToanDto() {}

    /** Mã bắt buộc khi tạo; khi cập nhật, mã chỉ lấy từ URL và không gửi trong body. */
    public record Request(String maPhuongThuc, String tenPhuongThuc, String moTa) {}

    public record CapNhatTrangThai(TrangThaiPhuongThuc trangThai) {}

    public record Response(
            String maPhuongThuc,
            String tenPhuongThuc,
            String moTa,
            TrangThaiPhuongThuc trangThai) {}
}
