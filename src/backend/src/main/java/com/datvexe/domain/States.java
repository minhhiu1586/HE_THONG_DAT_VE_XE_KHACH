package com.datvexe.domain;

/** Enumerations from the agreed transport booking class diagram. */
public final class States {
    private States() {}

    public enum TrangThaiBenXe { HOAT_DONG, TAM_NGUNG, DONG_CUA, BAO_TRI }
    public enum TrangThaiTuyenXe { DANG_KHAI_THAC, TAM_NGUNG, NGUNG_KHAI_THAC, SAP_KHAI_THAC }
    public enum LoaiDiemDung { DIEM_DON, DIEM_TRA, CA_HAI }
    public enum TrangThaiChuyen { CHUA_KHOI_HANH, DANG_CHAY, HOAN_TAT, DA_HUY }
    public enum TrangThaiXe { SAN_SANG, DANG_CHAY, BAO_TRI, HONG, NGUNG_HOAT_DONG }
    public enum TrangThaiGhe { HOAT_DONG, BAO_TRI, NGUNG_SU_DUNG }
    public enum TrangThaiGiuCho { DANG_GIU, DA_XAC_NHAN, HET_HAN, DA_HUY }
    public enum LoaiTaiKhoan { KHACH_HANG, ADMIN, NHA_XE, NHAN_VIEN }
    public enum GioiTinh { NAM, NU, KHAC }
    public enum TrangThaiTaiKhoan { HOAT_DONG, KHOA, CHO_XAC_THUC }
    public enum TrangThaiNhanVien { DANG_LAM_VIEC, TAM_NGHI, DA_NGHI_VIEC }
    public enum LoaiNhanVien { TAI_XE, PHU_XE, DIEU_HANH }
    public enum VaiTroChuyen {
        TAI_XE_CHINH, TAI_XE_PHU, PHU_XE,
        /** Legacy value retained so existing Java clients and stored assignments still work. */
        @Deprecated TAI_XE
    }
    public enum TrangThaiDanhGia { CHO_DUYET, DA_DUYET, AN }
    public enum TrangThaiDon { CHO_XU_LY, DA_XAC_NHAN, CHO_THANH_TOAN, DA_THANH_TOAN, DA_HUY }
    public enum KenhDat { WEB, APP, TONG_DAI, TAI_QUAY }
    public enum TrangThaiVe { DA_XUAT_VE, DA_LEN_XE, DA_HOAN, DA_HUY }
    public enum TrangThaiThanhToan { CHO_XU_LY, THANH_CONG, THAT_BAI, DA_HOAN_TIEN, HOAN_TIEN_MOT_PHAN }
    public enum TrangThaiPhuongThuc { HOAT_DONG, TAM_NGUNG, NGUNG_HOAT_DONG }
    public enum TrangThaiChinhSach { HOAT_DONG, TAM_NGUNG, NGUNG_AP_DUNG }
    public enum TrangThaiKhuyenMai { HOAT_DONG, HET_HAN, HET_LUOT }
    public enum LoaiGiam { PHAN_TRAM, SO_TIEN_CO_DINH }
    public enum TrangThaiNhaXe { HOAT_DONG, TAM_NGUNG, NGUNG_HOAT_DONG, CHO_PHE_DUYET }
}
