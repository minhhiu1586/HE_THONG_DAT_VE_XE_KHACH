package com.datvexe.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Refund tracking is technical data; no new business entity or ticket state. */
public final class HoanVeDto {
    private HoanVeDto() {}
    public record Request(List<String> danhSachMaVe, String lyDo) {}
    public record SingleRequest(String lyDo) {}
    public record Line(String maVe, String giaThucTra, String tyLeHoan, String phiHoan, String soTienHoan) {}
    public record PolicyResponse(String maVe, ChinhSachHoanVeDto.Response chinhSach,
            boolean duocYeuCauHoan, String soTienDuKienHoan, String donViTienTe,
            String lyDoKhongDuocHoan, OffsetDateTime thoiDiemTinh) {}
    public record Preview(String maDon, String maGiaoDichGoc, String maChinhSach,
            String soTienHoan, String donViTienTe, List<Line> chiTiet, OffsetDateTime thoiDiemTinh) {}
    public record History(String thaoTac, String maRequestCong, String maKetQua,
            String maGiaoDichCong, OffsetDateTime thoiDiem) {}
    public record Response(String maHoanTien, String maDon, String maGiaoDichGoc,
            String maChinhSach, String soTienHoan, String donViTienTe, String trangThai,
            List<Line> chiTiet, OffsetDateTime thoiDiemTao, OffsetDateTime thoiDiemXacNhan,
            String maGiaoDichCong, String lyDoLoi, List<History> lichSu) {}
    public record Page(List<Response> items, int page, int size, long totalItems, long totalPages) {}
}
