package com.datvexe.dto;

import com.datvexe.domain.States.TrangThaiVe;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

public final class VeXeDto {
    private VeXeDto() {}

    public record Response(
            String maVe,
            String maTraCuu,
            String maDon,
            String maChuyen,
            String maGhe,
            String hoTenHanhKhach,
            String soDienThoaiHanhKhach,
            String cccdHanhKhach,
            String giaGoc,
            String tienGiam,
            String thanhTien,
            String donViTienTe,
            String qrToken,
            OffsetDateTime thoiDiemPhatHanh,
            TrangThaiVe trangThai,
            boolean dangHoan) implements Serializable {
        @Serial private static final long serialVersionUID = 1L;
    }
}
