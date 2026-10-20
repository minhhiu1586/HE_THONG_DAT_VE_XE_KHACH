package com.datvexe.dto;

import com.datvexe.domain.States.VaiTroChuyen;

public final class AssignmentInputs {
    private AssignmentInputs() {}

    public record PhanCong(
        String maPhanCong,
        String maChuyen,
        String maNhanVien,
        VaiTroChuyen vaiTro,
        String ghiChu,
        Boolean xacNhanKeHoachLaiNghi
    ) {
        public PhanCong(String maPhanCong, String maChuyen, String maNhanVien,
                       VaiTroChuyen vaiTro, String ghiChu) {
            this(maPhanCong, maChuyen, maNhanVien, vaiTro, ghiChu, null);
        }

        public PhanCong(String maPhanCong, String maChuyen, String maNhanVien, VaiTroChuyen vaiTro) {
            this(maPhanCong, maChuyen, maNhanVien, vaiTro, null, null);
        }
    }
}
