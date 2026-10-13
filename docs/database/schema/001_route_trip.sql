-- Full database schema for all 20 entities in the approved class diagram.
-- This file works on a new database and upgrades the previous route-trip schema.

CREATE TABLE IF NOT EXISTS tinh_thanh (
  ma_tinh_thanh varchar(40) PRIMARY KEY,
  ten_tinh_thanh varchar(120) NOT NULL,
  khu_vuc varchar(80)
);

CREATE TABLE IF NOT EXISTS ben_xe (
  ma_ben_xe varchar(40) PRIMARY KEY,
  ten_ben_xe varchar(160) NOT NULL,
  dia_chi varchar(255) NOT NULL,
  so_dien_thoai varchar(30),
  ma_tinh_thanh varchar(40) NOT NULL REFERENCES tinh_thanh(ma_tinh_thanh),
  trang_thai varchar(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS nha_xe (
  ma_nha_xe varchar(40) PRIMARY KEY,
  ten_nha_xe varchar(160) NOT NULL,
  hotline varchar(30),
  email varchar(254),
  dia_chi varchar(255),
  mo_ta varchar(1000),
  logo_url varchar(500),
  ngay_thanh_lap date,
  trang_thai varchar(30) NOT NULL DEFAULT 'HOAT_DONG'
);
-- Additive upgrade for databases created by the earlier route-only version.
ALTER TABLE nha_xe ADD COLUMN IF NOT EXISTS hotline varchar(30);
ALTER TABLE nha_xe ADD COLUMN IF NOT EXISTS email varchar(254);
ALTER TABLE nha_xe ADD COLUMN IF NOT EXISTS dia_chi varchar(255);
ALTER TABLE nha_xe ADD COLUMN IF NOT EXISTS mo_ta varchar(1000);
ALTER TABLE nha_xe ADD COLUMN IF NOT EXISTS logo_url varchar(500);
ALTER TABLE nha_xe ADD COLUMN IF NOT EXISTS ngay_thanh_lap date;
ALTER TABLE nha_xe ADD COLUMN IF NOT EXISTS trang_thai varchar(30) NOT NULL DEFAULT 'HOAT_DONG';

CREATE TABLE IF NOT EXISTS nhan_vien (
  ma_nhan_vien varchar(40) PRIMARY KEY,
  ho_ten varchar(160) NOT NULL,
  so_dien_thoai varchar(30) NOT NULL,
  email varchar(254) NOT NULL,
  ngay_sinh date,
  ngay_het_han_bang_lai date,
  loai_nhan_vien varchar(30) NOT NULL,
  trang_thai varchar(30) NOT NULL,
  ma_nha_xe varchar(40) NOT NULL REFERENCES nha_xe(ma_nha_xe),
  CONSTRAINT uq_nhan_vien_email UNIQUE (email),
  CONSTRAINT uq_nhan_vien_dien_thoai UNIQUE (so_dien_thoai)
);

CREATE TABLE IF NOT EXISTS loai_xe (
  ma_loai_xe varchar(40) PRIMARY KEY,
  ten_loai_xe varchar(120) NOT NULL,
  so_cho_ngoi integer NOT NULL CHECK (so_cho_ngoi > 0),
  so_tang integer NOT NULL DEFAULT 1 CHECK (so_tang > 0),
  mo_ta varchar(1000)
);

CREATE TABLE IF NOT EXISTS xe (
  ma_xe varchar(40) PRIMARY KEY,
  bien_so varchar(30) NOT NULL UNIQUE,
  ma_loai_xe varchar(40) REFERENCES loai_xe(ma_loai_xe),
  so_cho_ngoi integer NOT NULL CHECK (so_cho_ngoi > 0),
  so_do_ngoi integer NOT NULL DEFAULT 1,
  toc_do_gio numeric(10,2),
  nam_san_xuat integer,
  ngay_dang_kiem date,
  han_dang_kiem date,
  trang_thai varchar(30) NOT NULL,
  ma_nha_xe varchar(40) REFERENCES nha_xe(ma_nha_xe)
);
-- Additive upgrade for databases created by the earlier route-only version.
ALTER TABLE xe ADD COLUMN IF NOT EXISTS ma_loai_xe varchar(40) REFERENCES loai_xe(ma_loai_xe);
ALTER TABLE xe ADD COLUMN IF NOT EXISTS so_do_ngoi integer NOT NULL DEFAULT 1;
ALTER TABLE xe ADD COLUMN IF NOT EXISTS toc_do_gio numeric(10,2);
ALTER TABLE xe ADD COLUMN IF NOT EXISTS nam_san_xuat integer;
ALTER TABLE xe ADD COLUMN IF NOT EXISTS ngay_dang_kiem date;
ALTER TABLE xe ADD COLUMN IF NOT EXISTS han_dang_kiem date;

CREATE TABLE IF NOT EXISTS ghe (
  ma_ghe varchar(40) PRIMARY KEY,
  vi_tri varchar(20) NOT NULL,
  tang integer NOT NULL DEFAULT 1,
  trang_thai varchar(30) NOT NULL,
  ma_xe varchar(40) NOT NULL REFERENCES xe(ma_xe),
  CONSTRAINT uq_ghe_xe_vi_tri UNIQUE (ma_xe, vi_tri),
  CONSTRAINT ck_ghe_tang CHECK (tang > 0)
);

CREATE TABLE IF NOT EXISTS tuyen_xe (
  ma_tuyen varchar(40) PRIMARY KEY,
  ten_tuyen varchar(160) NOT NULL,
  tram_khoi_hanh varchar(40) NOT NULL REFERENCES ben_xe(ma_ben_xe),
  tram_den varchar(40) NOT NULL REFERENCES ben_xe(ma_ben_xe),
  thoi_gian_khoi_hanh time,
  khoang_cach_km numeric(12,2),
  thoi_gian_du_kien numeric(10,2),
  gia_co_ban numeric(14,2) NOT NULL,
  mo_ta varchar(1000),
  trang_thai varchar(30) NOT NULL,
  ma_nha_xe varchar(40) REFERENCES nha_xe(ma_nha_xe),
  CONSTRAINT ck_tram_khac_nhau CHECK (tram_khoi_hanh <> tram_den),
  CONSTRAINT ck_tuyen_gia CHECK (gia_co_ban >= 0),
  CONSTRAINT ck_tuyen_km CHECK (khoang_cach_km IS NULL OR khoang_cach_km > 0),
  CONSTRAINT ck_tuyen_thoi_gian CHECK (thoi_gian_du_kien IS NULL OR thoi_gian_du_kien > 0)
);

CREATE TABLE IF NOT EXISTS diem_dung (
  ma_diem_dung varchar(40) PRIMARY KEY,
  ma_tuyen varchar(40) NOT NULL REFERENCES tuyen_xe(ma_tuyen),
  ten_diem_dung varchar(160) NOT NULL,
  dia_chi varchar(255) NOT NULL,
  thu_tu integer NOT NULL CHECK (thu_tu > 0),
  thoi_gian_dung integer NOT NULL CHECK (thoi_gian_dung >= 0),
  loai_diem_dung varchar(20) NOT NULL,
  CONSTRAINT uq_diem_dung_thu_tu UNIQUE (ma_tuyen, thu_tu) DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE IF NOT EXISTS chuyen_xe (
  ma_chuyen varchar(40) PRIMARY KEY,
  ma_tuyen varchar(40) NOT NULL REFERENCES tuyen_xe(ma_tuyen),
  ma_xe varchar(40) REFERENCES xe(ma_xe),
  ngay_khoi_hanh date NOT NULL,
  gio_khoi_hanh time NOT NULL,
  gio_den_du_kien timestamp NOT NULL,
  trang_thai varchar(30) NOT NULL,
  CONSTRAINT ck_chuyen_gio_den CHECK (gio_den_du_kien > (ngay_khoi_hanh + gio_khoi_hanh))
);

CREATE TABLE IF NOT EXISTS phan_cong_chuyen_xe (
  ma_phan_cong varchar(40) PRIMARY KEY,
  ma_chuyen varchar(40) NOT NULL REFERENCES chuyen_xe(ma_chuyen),
  ma_nhan_vien varchar(40) NOT NULL REFERENCES nhan_vien(ma_nhan_vien),
  vai_tro varchar(30) NOT NULL,
  CONSTRAINT uq_phan_cong_chuyen_nhan_vien_vai_tro UNIQUE (ma_chuyen, ma_nhan_vien, vai_tro)
);

CREATE TABLE IF NOT EXISTS nguoi_dung (
  ma_nguoi_dung varchar(40) PRIMARY KEY,
  ho_ten varchar(160) NOT NULL,
  gioi_tinh varchar(20),
  ngay_sinh date,
  so_dien_thoai varchar(30) NOT NULL,
  email varchar(254) NOT NULL,
  mat_khau varchar(255) NOT NULL,
  cccd varchar(20),
  loai_tai_khoan varchar(30) NOT NULL,
  trang_thai varchar(30) NOT NULL,
  CONSTRAINT uq_nguoi_dung_email UNIQUE (email),
  CONSTRAINT uq_nguoi_dung_dien_thoai UNIQUE (so_dien_thoai),
  CONSTRAINT uq_nguoi_dung_cccd UNIQUE (cccd)
);

CREATE TABLE IF NOT EXISTS khuyen_mai (
  ma_khuyen_mai varchar(40) PRIMARY KEY,
  ten_khuyen_mai varchar(160) NOT NULL,
  mo_ta varchar(1000),
  loai_giam varchar(30) NOT NULL,
  gia_tri_giam numeric(14,2) NOT NULL CHECK (gia_tri_giam > 0),
  gia_tri_toi_da numeric(14,2),
  don_toi_thieu numeric(14,2) NOT NULL DEFAULT 0,
  so_luong integer NOT NULL CHECK (so_luong >= 0),
  so_luong_da_dung integer NOT NULL DEFAULT 0 CHECK (so_luong_da_dung >= 0),
  ngay_bat_dau timestamp NOT NULL,
  ngay_ket_thuc timestamp NOT NULL,
  trang_thai varchar(30) NOT NULL,
  CONSTRAINT ck_khuyen_mai_thoi_gian CHECK (ngay_ket_thuc > ngay_bat_dau)
);

CREATE TABLE IF NOT EXISTS chinh_sach_hoan_ve (
  ma_chinh_sach varchar(40) PRIMARY KEY,
  ten_chinh_sach varchar(160) NOT NULL,
  so_gio_toi_thieu_truoc_khoi_hanh integer NOT NULL CHECK (so_gio_toi_thieu_truoc_khoi_hanh >= 0),
  ty_le_hoan numeric(5,2) NOT NULL CHECK (ty_le_hoan >= 0 AND ty_le_hoan <= 100),
  phi_hoan numeric(14,2) NOT NULL DEFAULT 0 CHECK (phi_hoan >= 0),
  cho_phep_hoan_huy_ve boolean NOT NULL DEFAULT false,
  trang_thai varchar(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS don_dat_ve (
  ma_don varchar(40) PRIMARY KEY,
  ma_nguoi_dung varchar(40) NOT NULL REFERENCES nguoi_dung(ma_nguoi_dung),
  ngay_dat timestamp NOT NULL,
  tong_tien numeric(14,2) NOT NULL DEFAULT 0 CHECK (tong_tien >= 0),
  tien_giam numeric(14,2) NOT NULL DEFAULT 0 CHECK (tien_giam >= 0),
  ma_khuyen_mai varchar(40) REFERENCES khuyen_mai(ma_khuyen_mai),
  ma_chinh_sach_hoan_ve varchar(40) REFERENCES chinh_sach_hoan_ve(ma_chinh_sach),
  khach_dat_ten varchar(160) NOT NULL,
  khach_dat_so_dien_thoai varchar(30) NOT NULL,
  ghi_chu varchar(1000),
  kenh_dat varchar(30) NOT NULL,
  trang_thai varchar(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS giu_cho (
  ma_giu_cho varchar(40) PRIMARY KEY,
  ma_chuyen varchar(40) NOT NULL REFERENCES chuyen_xe(ma_chuyen),
  ma_ghe varchar(40) NOT NULL REFERENCES ghe(ma_ghe),
  ma_don varchar(40) REFERENCES don_dat_ve(ma_don),
  ngay_dat timestamp NOT NULL,
  thoi_diem_bat_dau timestamp NOT NULL,
  thoi_diem_het_han timestamp NOT NULL,
  trang_thai varchar(30) NOT NULL,
  CONSTRAINT ck_giu_cho_thoi_gian CHECK (thoi_diem_het_han > thoi_diem_bat_dau)
);

CREATE TABLE IF NOT EXISTS ve_xe (
  ma_ve varchar(40) PRIMARY KEY,
  ma_chuyen varchar(40) NOT NULL REFERENCES chuyen_xe(ma_chuyen),
  ma_ghe varchar(40) NOT NULL REFERENCES ghe(ma_ghe),
  ma_don varchar(40) NOT NULL REFERENCES don_dat_ve(ma_don),
  ho_ten_khach_hang varchar(160) NOT NULL,
  so_dien_thoai_khach_hang varchar(30) NOT NULL,
  cccd_hanh_khach varchar(20),
  gia_ve numeric(14,2) NOT NULL CHECK (gia_ve >= 0),
  ngay_xuat_ve timestamp,
  trang_thai varchar(30) NOT NULL,
  CONSTRAINT uq_ve_xe_ma_ghe_chuyen UNIQUE (ma_ghe, ma_chuyen),
  CONSTRAINT uq_ve_xe_ma_ve UNIQUE (ma_ve)
);

CREATE TABLE IF NOT EXISTS danh_gia (
  ma_danh_gia varchar(40) PRIMARY KEY,
  ma_nguoi_dung varchar(40) NOT NULL REFERENCES nguoi_dung(ma_nguoi_dung),
  ma_chuyen varchar(40) NOT NULL REFERENCES chuyen_xe(ma_chuyen),
  so_sao integer NOT NULL CHECK (so_sao BETWEEN 1 AND 5),
  binh_luan varchar(2000),
  ngay_danh_gia timestamp NOT NULL,
  trang_thai varchar(30) NOT NULL,
  CONSTRAINT uq_danh_gia_nguoi_dung_chuyen UNIQUE (ma_nguoi_dung, ma_chuyen)
);

CREATE TABLE IF NOT EXISTS phuong_thuc_thanh_toan (
  ma_phuong_thuc varchar(40) PRIMARY KEY,
  ten_phuong_thuc varchar(120) NOT NULL,
  mo_ta varchar(1000),
  trang_thai varchar(30) NOT NULL
);

CREATE TABLE IF NOT EXISTS thanh_toan (
  ma_giao_dich varchar(80) PRIMARY KEY,
  ma_don varchar(40) NOT NULL REFERENCES don_dat_ve(ma_don),
  ma_phuong_thuc varchar(40) NOT NULL REFERENCES phuong_thuc_thanh_toan(ma_phuong_thuc),
  so_tien numeric(14,2) NOT NULL CHECK (so_tien >= 0),
  noi_dung_thanh_toan varchar(1000),
  thoi_gian_thanh_toan timestamp,
  trang_thai varchar(30) NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_chuyen_ngay_trang_thai ON chuyen_xe (ngay_khoi_hanh, trang_thai);
CREATE INDEX IF NOT EXISTS ix_chuyen_tuyen ON chuyen_xe (ma_tuyen);
CREATE INDEX IF NOT EXISTS ix_chuyen_xe ON chuyen_xe (ma_xe);
CREATE INDEX IF NOT EXISTS ix_phan_cong_chuyen ON phan_cong_chuyen_xe (ma_chuyen);
CREATE INDEX IF NOT EXISTS ix_phan_cong_nhan_vien ON phan_cong_chuyen_xe (ma_nhan_vien);
CREATE INDEX IF NOT EXISTS ix_giu_cho_chuyen_ghe ON giu_cho (ma_chuyen, ma_ghe);
CREATE INDEX IF NOT EXISTS ix_giu_cho_het_han ON giu_cho (thoi_diem_het_han);
CREATE INDEX IF NOT EXISTS ix_don_dat_ve_nguoi_dung ON don_dat_ve (ma_nguoi_dung);
CREATE INDEX IF NOT EXISTS ix_don_dat_ve_ngay_dat ON don_dat_ve (ngay_dat);
CREATE INDEX IF NOT EXISTS ix_ve_xe_don ON ve_xe (ma_don);
CREATE INDEX IF NOT EXISTS ix_danh_gia_chuyen ON danh_gia (ma_chuyen);
CREATE INDEX IF NOT EXISTS ix_thanh_toan_don ON thanh_toan (ma_don);
CREATE INDEX IF NOT EXISTS ix_khuyen_mai_hieu_luc ON khuyen_mai (ngay_bat_dau, ngay_ket_thuc, trang_thai);
