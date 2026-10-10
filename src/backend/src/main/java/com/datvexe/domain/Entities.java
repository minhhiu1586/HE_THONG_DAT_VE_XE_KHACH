package com.datvexe.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import com.datvexe.domain.States.*;

/**
 * JPA mapping for the 20 entities in the team's approved class diagram.
 * Public fields intentionally follow the existing module conventions used by RouteTripService.
 */
public final class Entities {
    private Entities() {}

    @Entity(name = "TinhThanh") @Table(name = "tinh_thanh")
    public static class TinhThanh {
        @Id @Column(name = "ma_tinh_thanh", length = 40) public String maTinhThanh;
        @Column(name = "ten_tinh_thanh", nullable = false, length = 120) public String tenTinhThanh;
        @Column(name = "khu_vuc", length = 80) public String khuVuc;
        @OneToMany(mappedBy = "tinhThanh") public List<BenXe> danhSachBenXe = new ArrayList<>();
        public TinhThanh() {}
    }

    @Entity(name = "BenXe") @Table(name = "ben_xe")
    public static class BenXe {
        @Id @Column(name = "ma_ben_xe", length = 40) public String maBenXe;
        @Column(name = "ten_ben_xe", nullable = false, length = 160) public String tenBenXe;
        @Column(name = "dia_chi", nullable = false, length = 255) public String diaChi;
        @Column(name = "so_dien_thoai", length = 30) public String soDienThoai;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_tinh_thanh", nullable = false)
        public TinhThanh tinhThanh;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiBenXe trangThai = TrangThaiBenXe.HOAT_DONG;
        @OneToMany(mappedBy = "tramKhoiHanh") public List<TuyenXe> tuyenKhoiHanh = new ArrayList<>();
        @OneToMany(mappedBy = "tramDen") public List<TuyenXe> tuyenKetThuc = new ArrayList<>();
        public BenXe() {}
    }

    @Entity(name = "NhaXe") @Table(name = "nha_xe")
    public static class NhaXe {
        @Id @Column(name = "ma_nha_xe", length = 40) public String maNhaXe;
        @Column(name = "ten_nha_xe", nullable = false, length = 160) public String tenNhaXe;
        @Column(name = "hotline", length = 30) public String hotline;
        @Column(name = "email", length = 254) public String email;
        @Column(name = "dia_chi", length = 255) public String diaChi;
        @Column(name = "mo_ta", length = 1000) public String moTa;
        @Column(name = "logo_url", length = 500) public String logoUrl;
        @Column(name = "ngay_thanh_lap") public LocalDate ngayThanhLap;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiNhaXe trangThai = TrangThaiNhaXe.HOAT_DONG;
        @OneToMany(mappedBy = "nhaXe") public List<NhanVien> danhSachNhanVien = new ArrayList<>();
        @OneToMany(mappedBy = "nhaXe") public List<Xe> danhSachXe = new ArrayList<>();
        @OneToMany(mappedBy = "nhaXe") public List<TuyenXe> danhSachTuyenXe = new ArrayList<>();
        public NhaXe() {}
    }

    @Entity(name = "NhanVien") @Table(name = "nhan_vien", uniqueConstraints = {
        @UniqueConstraint(name = "uq_nhan_vien_email", columnNames = "email"),
        @UniqueConstraint(name = "uq_nhan_vien_dien_thoai", columnNames = "so_dien_thoai")
    })
    public static class NhanVien {
        @Id @Column(name = "ma_nhan_vien", length = 40) public String maNhanVien;
        @Column(name = "ho_ten", nullable = false, length = 160) public String hoTen;
        @Column(name = "so_dien_thoai", nullable = false, length = 30) public String soDienThoai;
        @Column(name = "email", nullable = false, length = 254) public String email;
        @Column(name = "ngay_sinh") public LocalDate ngaySinh;
        @Column(name = "ngay_het_han_bang_lai") public LocalDate ngayHetHanBangLai;
        @Column(name = "so_bang_lai", length = 80) public String soBangLai;
        @Enumerated(EnumType.STRING) @Column(name = "loai_nhan_vien", nullable = false, length = 30)
        public LoaiNhanVien loaiNhanVien;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiNhanVien trangThai = TrangThaiNhanVien.DANG_LAM_VIEC;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_nha_xe", nullable = false)
        public NhaXe nhaXe;
        @OneToMany(mappedBy = "nhanVien") public List<PhanCongChuyenXe> danhSachPhanCong = new ArrayList<>();
        public NhanVien() {}
    }

    @Entity(name = "LoaiXe") @Table(name = "loai_xe")
    public static class LoaiXe {
        @Id @Column(name = "ma_loai_xe", length = 40) public String maLoaiXe;
        @Column(name = "ten_loai_xe", nullable = false, length = 120) public String tenLoaiXe;
        @Column(name = "so_cho_ngoi", nullable = false) public int soChoNgoi;
        @Column(name = "so_tang", nullable = false) public int soTang = 1;
        @Column(name = "mo_ta", length = 1000) public String moTa;
        @OneToMany(mappedBy = "loaiXe") public List<Xe> danhSachXe = new ArrayList<>();
        public LoaiXe() {}
    }

    @Entity(name = "Xe") @Table(name = "xe")
    public static class Xe {
        @Id @Column(name = "ma_xe", length = 40) public String maXe;
        @Column(name = "bien_so", nullable = false, unique = true, length = 30) public String bienSo;
        @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "ma_loai_xe") public LoaiXe loaiXe;
        @Column(name = "so_cho_ngoi", nullable = false) public int soChoNgoi;
        @Column(name = "so_do_ngoi", nullable = false) public int soDoNgoi = 1;
        @Column(name = "toc_do_gio", precision = 10, scale = 2) public BigDecimal tocDoGio;
        @Column(name = "dung_tich_xang", precision = 10, scale = 2) public BigDecimal dungTichXang;
        @Column(name = "nam_san_xuat") public Integer namSanXuat;
        @Column(name = "ngay_dang_kiem") public LocalDate ngayDangKiem;
        @Column(name = "han_dang_kiem") public LocalDate hanDangKiem;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiXe trangThai = TrangThaiXe.SAN_SANG;
        @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "ma_nha_xe") public NhaXe nhaXe;
        @OneToMany(mappedBy = "xe", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("tang ASC, viTri ASC") public List<Ghe> danhSachGhe = new ArrayList<>();
        @OneToMany(mappedBy = "xe") public List<ChuyenXe> danhSachChuyenXe = new ArrayList<>();
        public Xe() {}
    }

    @Entity(name = "Ghe") @Table(name = "ghe", uniqueConstraints =
        @UniqueConstraint(name = "uq_ghe_xe_vi_tri", columnNames = {"ma_xe", "vi_tri"}))
    public static class Ghe {
        @Id @Column(name = "ma_ghe", length = 40) public String maGhe;
        @Column(name = "vi_tri", nullable = false, length = 20) public String viTri;
        @Column(name = "tang", nullable = false) public int tang = 1;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiGhe trangThai = TrangThaiGhe.HOAT_DONG;
        @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "ma_xe", nullable = false)
        public Xe xe;
        @OneToMany(mappedBy = "ghe") public List<GiuCho> danhSachGiuCho = new ArrayList<>();
        @OneToMany(mappedBy = "ghe") public List<VeXe> danhSachVe = new ArrayList<>();
        public Ghe() {}
    }

    @Entity(name = "TuyenXe") @Table(name = "tuyen_xe")
    public static class TuyenXe {
        @Id @Column(name = "ma_tuyen", length = 40) public String maTuyen;
        @Column(name = "ten_tuyen", nullable = false, length = 160) public String tenTuyen;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "tram_khoi_hanh", nullable = false)
        public BenXe tramKhoiHanh;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "tram_den", nullable = false)
        public BenXe tramDen;
        @OneToMany(mappedBy = "tuyenXe", cascade = CascadeType.ALL, orphanRemoval = true)
        @OrderBy("thuTu ASC") public List<DiemDung> tramTrungGian = new ArrayList<>();
        @Column(name = "thoi_gian_khoi_hanh") public LocalTime thoiGianKhoiHanh;
        @Column(name = "khoang_cach_km", precision = 12, scale = 2) public BigDecimal khoangCachKM;
        @Column(name = "thoi_gian_du_kien", precision = 10, scale = 2) public BigDecimal thoiGianDuKien;
        @Column(name = "gia_co_ban", nullable = false, precision = 14, scale = 2) public BigDecimal giaCoBan;
        @Column(name = "mo_ta", length = 1000) public String moTa;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiTuyenXe trangThai = TrangThaiTuyenXe.SAP_KHAI_THAC;
        @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "ma_nha_xe") public NhaXe nhaXe;
        @OneToMany(mappedBy = "tuyenXe") public List<ChuyenXe> danhSachChuyenXe = new ArrayList<>();
        public TuyenXe() {}
    }

    @Entity(name = "DiemDung") @Table(name = "diem_dung", uniqueConstraints =
        @UniqueConstraint(name = "uq_diem_dung_thu_tu", columnNames = {"ma_tuyen", "thu_tu"}))
    public static class DiemDung {
        @Id @Column(name = "ma_diem_dung", length = 40) public String maDiemDung;
        @Column(name = "ten_diem_dung", nullable = false, length = 160) public String tenDiemDung;
        @Column(name = "dia_chi", nullable = false, length = 255) public String diaChi;
        @Column(name = "thu_tu", nullable = false) public int thuTu;
        @Column(name = "thoi_gian_dung", nullable = false) public int thoiGianDung;
        @Enumerated(EnumType.STRING) @Column(name = "loai_diem_dung", nullable = false, length = 20)
        public LoaiDiemDung loaiDiemDung;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_tuyen", nullable = false)
        public TuyenXe tuyenXe;
        public DiemDung() {}
    }

    @Entity(name = "ChuyenXe") @Table(name = "chuyen_xe")
    public static class ChuyenXe {
        @Id @Column(name = "ma_chuyen", length = 40) public String maChuyen;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_tuyen", nullable = false)
        public TuyenXe tuyenXe;
        @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "ma_xe") public Xe xe;
        @Column(name = "ngay_khoi_hanh", nullable = false) public LocalDate ngayKhoiHanh;
        @Column(name = "gio_khoi_hanh", nullable = false) public LocalTime gioKhoiHanh;
        @Column(name = "gio_den_du_kien", nullable = false) public LocalDateTime gioDenDuKien;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiChuyen trangThai = TrangThaiChuyen.CHUA_KHOI_HANH;
        @OneToMany(mappedBy = "chuyenXe", cascade = CascadeType.ALL, orphanRemoval = true)
        public List<PhanCongChuyenXe> danhSachPhanCong = new ArrayList<>();
        @OneToMany(mappedBy = "chuyenXe", cascade = CascadeType.ALL, orphanRemoval = true)
        public List<GiuCho> danhSachGiuCho = new ArrayList<>();
        @OneToMany(mappedBy = "chuyenXe") public List<VeXe> danhSachVe = new ArrayList<>();
        @OneToMany(mappedBy = "chuyenXe") public List<DanhGia> danhSachDanhGia = new ArrayList<>();
        public ChuyenXe() {}
    }

    @Entity(name = "PhanCongChuyenXe") @Table(name = "phan_cong_chuyen_xe", uniqueConstraints =
        @UniqueConstraint(name = "uq_phan_cong_chuyen_nhan_vien_vai_tro", columnNames = {"ma_chuyen", "ma_nhan_vien", "vai_tro"}))
    public static class PhanCongChuyenXe {
        @Id @Column(name = "ma_phan_cong", length = 40) public String maPhanCong;
        @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "ma_chuyen", nullable = false)
        public ChuyenXe chuyenXe;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_nhan_vien", nullable = false)
        public NhanVien nhanVien;
        @Enumerated(EnumType.STRING) @Column(name = "vai_tro", nullable = false, length = 30)
        public VaiTroChuyen vaiTro;
        @Column(name = "ghi_chu", length = 1000) public String ghiChu;
        public PhanCongChuyenXe() {}
    }

    @Entity(name = "GiuCho") @Table(name = "giu_cho", indexes = {
        @Index(name = "ix_giu_cho_chuyen_ghe", columnList = "ma_chuyen,ma_ghe"),
        @Index(name = "ix_giu_cho_het_han", columnList = "thoi_diem_het_han")
    })
    public static class GiuCho {
        @Id @Column(name = "ma_giu_cho", length = 40) public String maGiuCho;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_chuyen", nullable = false)
        public ChuyenXe chuyenXe;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_ghe", nullable = false)
        public Ghe ghe;
        @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ma_don") public DonDatVe donDatVe;
        @Column(name = "ngay_dat", nullable = false) public LocalDateTime ngayDat;
        @Column(name = "thoi_diem_bat_dau", nullable = false) public LocalDateTime thoiDiemBatDau;
        @Column(name = "thoi_diem_het_han", nullable = false) public LocalDateTime thoiDiemHetHan;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiGiuCho trangThai = TrangThaiGiuCho.DANG_GIU;
        public GiuCho() {}
    }

    @Entity(name = "NguoiDung") @Table(name = "nguoi_dung", uniqueConstraints = {
        @UniqueConstraint(name = "uq_nguoi_dung_email", columnNames = "email"),
        @UniqueConstraint(name = "uq_nguoi_dung_dien_thoai", columnNames = "so_dien_thoai"),
        @UniqueConstraint(name = "uq_nguoi_dung_cccd", columnNames = "cccd")
    })
    public static class NguoiDung {
        @Id @Column(name = "ma_nguoi_dung", length = 40) public String maNguoiDung;
        @Column(name = "ho_ten", nullable = false, length = 160) public String hoTen;
        @Enumerated(EnumType.STRING) @Column(name = "gioi_tinh", length = 20) public GioiTinh gioiTinh;
        @Column(name = "ngay_sinh") public LocalDate ngaySinh;
        @Column(name = "so_dien_thoai", nullable = false, length = 30) public String soDienThoai;
        @Column(name = "email", nullable = false, length = 254) public String email;
        @Column(name = "mat_khau", nullable = false, length = 255) public String matKhau;
        @Column(name = "cccd", length = 20) public String cccd;
        @Enumerated(EnumType.STRING) @Column(name = "loai_tai_khoan", nullable = false, length = 30)
        public LoaiTaiKhoan loaiTaiKhoan = LoaiTaiKhoan.KHACH_HANG;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiTaiKhoan trangThai = TrangThaiTaiKhoan.CHO_XAC_THUC;
        @OneToMany(mappedBy = "nguoiDat") public List<DonDatVe> danhSachDonDat = new ArrayList<>();
        @OneToMany(mappedBy = "nguoiDung") public List<DanhGia> danhSachDanhGia = new ArrayList<>();
        public NguoiDung() {}
    }

    @Entity(name = "DonDatVe") @Table(name = "don_dat_ve", indexes = {
        @Index(name = "ix_don_dat_ve_nguoi_dung", columnList = "ma_nguoi_dung"),
        @Index(name = "ix_don_dat_ve_ngay_dat", columnList = "ngay_dat")
    })
    public static class DonDatVe {
        @Id @Column(name = "ma_don", length = 40) public String maDon;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_nguoi_dung", nullable = false)
        public NguoiDung nguoiDat;
        @OneToMany(mappedBy = "donDatVe", cascade = CascadeType.ALL, orphanRemoval = true)
        public List<VeXe> danhSachVe = new ArrayList<>();
        @Column(name = "ngay_dat", nullable = false) public LocalDateTime ngayDat;
        @Column(name = "tong_tien", nullable = false, precision = 14, scale = 2) public BigDecimal tongTien = BigDecimal.ZERO;
        @Column(name = "tien_giam", nullable = false, precision = 14, scale = 2) public BigDecimal tienGiam = BigDecimal.ZERO;
        @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "ma_khuyen_mai") public KhuyenMai khuyenMaiDaApDung;
        @Column(name = "ma_khuyen_mai", insertable = false, updatable = false, length = 40)
        public String maKhuyenMaiDaApDung;
        @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "ma_chinh_sach_hoan_ve") public ChinhSachHoanVe chinhSachHoanVe;
        @Column(name = "khach_dat_ten", nullable = false, length = 160) public String khachDatTen;
        @Column(name = "khach_dat_so_dien_thoai", nullable = false, length = 30) public String khachDatSoDienThoai;
        @Column(name = "ghi_chu", length = 1000) public String ghiChu;
        @Enumerated(EnumType.STRING) @Column(name = "kenh_dat", nullable = false, length = 30)
        public KenhDat kenhDat = KenhDat.WEB;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiDon trangThai = TrangThaiDon.CHO_XU_LY;
        @OneToMany(mappedBy = "donDatVe") public List<ThanhToan> danhSachThanhToan = new ArrayList<>();
        @OneToMany(mappedBy = "donDatVe") public List<GiuCho> danhSachGiuCho = new ArrayList<>();
        public DonDatVe() {}
    }

    @Entity(name = "VeXe") @Table(name = "ve_xe", uniqueConstraints = {
        @UniqueConstraint(name = "uq_ve_xe_ma_ghe_chuyen", columnNames = {"ma_ghe", "ma_chuyen"}),
        @UniqueConstraint(name = "uq_ve_xe_ma_ve", columnNames = "ma_ve")
    }, indexes = @Index(name = "ix_ve_xe_don", columnList = "ma_don"))
    public static class VeXe {
        @Id @Column(name = "ma_ve", length = 40) public String maVe;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_chuyen", nullable = false)
        public ChuyenXe chuyenXe;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_ghe", nullable = false)
        public Ghe ghe;
        @Column(name = "ma_ghe", insertable = false, updatable = false, length = 40) public String maGhe;
        @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "ma_don", nullable = false)
        public DonDatVe donDatVe;
        @Column(name = "ma_don", insertable = false, updatable = false, length = 40) public String maDonDatVe;
        @Column(name = "ho_ten_khach_hang", nullable = false, length = 160) public String hoTenKhachHang;
        @Column(name = "so_dien_thoai_khach_hang", nullable = false, length = 30) public String soDienThoaiKhachHang;
        @Column(name = "cccd_hanh_khach", length = 20) public String cccdHanhKhach;
        @Column(name = "gia_ve", nullable = false, precision = 14, scale = 2) public BigDecimal giaVe;
        @Column(name = "ngay_xuat_ve") public LocalDateTime ngayXuatVe;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiVe trangThai = TrangThaiVe.DA_XUAT_VE;
        public VeXe() {}
    }

    @Entity(name = "DanhGia") @Table(name = "danh_gia", uniqueConstraints =
        @UniqueConstraint(name = "uq_danh_gia_nguoi_dung_chuyen", columnNames = {"ma_nguoi_dung", "ma_chuyen"}))
    public static class DanhGia {
        @Id @Column(name = "ma_danh_gia", length = 40) public String maDanhGia;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_nguoi_dung", nullable = false)
        public NguoiDung nguoiDung;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_chuyen", nullable = false)
        public ChuyenXe chuyenXe;
        @Column(name = "so_sao", nullable = false) public int soSao;
        @Column(name = "binh_luan", length = 2000) public String binhLuan;
        @Column(name = "ngay_danh_gia", nullable = false) public LocalDateTime ngayDanhGia;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiDanhGia trangThai = TrangThaiDanhGia.CHO_DUYET;
        public DanhGia() {}
    }

    @Entity(name = "ThanhToan") @Table(name = "thanh_toan", indexes =
        @Index(name = "ix_thanh_toan_don", columnList = "ma_don"))
    public static class ThanhToan {
        @Id @Column(name = "ma_giao_dich", length = 80) public String maGiaoDich;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_don", nullable = false)
        public DonDatVe donDatVe;
        @ManyToOne(optional = false, fetch = FetchType.EAGER) @JoinColumn(name = "ma_phuong_thuc", nullable = false)
        public PhuongThucThanhToan phuongThucThanhToan;
        @Column(name = "so_tien", nullable = false, precision = 14, scale = 2) public BigDecimal soTien;
        @Column(name = "noi_dung_thanh_toan", length = 1000) public String noiDungThanhToan;
        @Column(name = "thoi_gian_thanh_toan") public LocalDateTime thoiGianThanhToan;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiThanhToan trangThai = TrangThaiThanhToan.CHO_XU_LY;
        public ThanhToan() {}
    }

    @Entity(name = "PhuongThucThanhToan") @Table(name = "phuong_thuc_thanh_toan")
    public static class PhuongThucThanhToan {
        @Id @Column(name = "ma_phuong_thuc", length = 40) public String maPhuongThuc;
        @Column(name = "ten_phuong_thuc", nullable = false, length = 120) public String tenPhuongThuc;
        @Column(name = "mo_ta", length = 1000) public String moTa;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiPhuongThuc trangThai = TrangThaiPhuongThuc.HOAT_DONG;
        @OneToMany(mappedBy = "phuongThucThanhToan") public List<ThanhToan> danhSachThanhToan = new ArrayList<>();
        public PhuongThucThanhToan() {}
    }

    @Entity(name = "ChinhSachHoanVe") @Table(name = "chinh_sach_hoan_ve")
    public static class ChinhSachHoanVe {
        @Id @Column(name = "ma_chinh_sach", length = 40) public String maChinhSach;
        @Column(name = "ten_chinh_sach", nullable = false, length = 160) public String tenChinhSach;
        @Column(name = "so_gio_toi_thieu_truoc_khoi_hanh", nullable = false) public int soGioToiThieuTruocKhoiHanh;
        @Column(name = "ty_le_hoan", nullable = false, precision = 5, scale = 2) public BigDecimal tyLeHoan;
        @Column(name = "phi_hoan", nullable = false, precision = 14, scale = 2) public BigDecimal phiHoan = BigDecimal.ZERO;
        @Column(name = "cho_phep_hoan_huy_ve", nullable = false) public boolean choPhepHoanHuyVe;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiChinhSach trangThai = TrangThaiChinhSach.HOAT_DONG;
        @OneToMany(mappedBy = "chinhSachHoanVe") public List<DonDatVe> danhSachDonDat = new ArrayList<>();
        public ChinhSachHoanVe() {}
    }

    @Entity(name = "KhuyenMai") @Table(name = "khuyen_mai", indexes =
        @Index(name = "ix_khuyen_mai_hieu_luc", columnList = "ngay_bat_dau,ngay_ket_thuc,trang_thai"))
    public static class KhuyenMai {
        @Id @Column(name = "ma_khuyen_mai", length = 40) public String maKhuyenMai;
        @Column(name = "ten_khuyen_mai", nullable = false, length = 160) public String tenKhuyenMai;
        @Column(name = "mo_ta", length = 1000) public String moTa;
        @Enumerated(EnumType.STRING) @Column(name = "loai_giam", nullable = false, length = 30)
        public LoaiGiam loaiGiam;
        @Column(name = "gia_tri_giam", nullable = false, precision = 14, scale = 2) public BigDecimal giaTriGiam;
        @Column(name = "gia_tri_toi_da", precision = 14, scale = 2) public BigDecimal giaTriToiDa;
        @Column(name = "don_toi_thieu", nullable = false, precision = 14, scale = 2) public BigDecimal donToiThieu = BigDecimal.ZERO;
        @Column(name = "so_luong", nullable = false) public int soLuong;
        @Column(name = "so_luong_da_dung", nullable = false) public int soLuongDaDung;
        @Column(name = "ngay_bat_dau", nullable = false) public LocalDateTime ngayBatDau;
        @Column(name = "ngay_ket_thuc", nullable = false) public LocalDateTime ngayKetThuc;
        @Enumerated(EnumType.STRING) @Column(name = "trang_thai", nullable = false, length = 30)
        public TrangThaiKhuyenMai trangThai = TrangThaiKhuyenMai.HOAT_DONG;
        @OneToMany(mappedBy = "khuyenMaiDaApDung") public List<DonDatVe> danhSachDonDat = new ArrayList<>();
        public KhuyenMai() {}
    }
}
