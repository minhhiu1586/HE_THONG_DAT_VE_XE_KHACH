package com.datvexe.dto;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import com.datvexe.domain.States.*;

public final class Inputs {
    private Inputs() {}

    public record TinhThanh(String maTinhThanh, String tenTinhThanh, String khuVuc) {}
    public record BenXe(String maBenXe, String tenBenXe, String diaChi, String soDienThoai,
                        String maTinhThanh, TrangThaiBenXe trangThai) {}
    public record TuyenXe(String maTuyen, String tenTuyen, String tramKhoiHanh, String tramDen,
                          LocalTime thoiGianKhoiHanh, BigDecimal khoangCachKM,
                          BigDecimal thoiGianDuKien, BigDecimal giaCoBan, String moTa,
                          TrangThaiTuyenXe trangThai, String maNhaXe) {}
    public record DiemDung(String maDiemDung, String tenDiemDung, String diaChi,
                           Integer thuTu, Integer thoiGianDung, LoaiDiemDung loaiDiemDung) {}
    public record SapXepDiemDung(List<String> maDiemDungTheoThuTu) {}
    public record TramDauCuoi(String tramKhoiHanh, String tramDen) {}
    public record ChuyenXe(String maChuyen, String maTuyen, LocalDate ngayKhoiHanh,
                           LocalTime gioKhoiHanh, String maXe) {}
    public record CapNhatChuyen(LocalDate ngayKhoiHanh, LocalTime gioKhoiHanh) {}
    public record GanXe(String maXe) {}
}
