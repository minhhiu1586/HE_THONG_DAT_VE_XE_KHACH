-- 35 hồ sơ nhân viên, 47 tài khoản và 10 xe; giữ nguyên bản ghi đã có.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
CREATE TEMP TABLE project_operators ON COMMIT DROP AS
SELECT * FROM (VALUES
(1,'FUTA','NX_FUTA','LX_29','LX_40','51B-501.12','51B-501.13'),
(2,'TB','NX_TB','LX_29','LX_22','51B-602.45','51B-602.46'),
(3,'KUMHO','NX_KUMHO','LX_29','LX_40','51B-703.78','51B-703.79'),
(4,'HL','NX_HL','LX_40','LX_40','15B-804.88','15B-804.89'),
(5,'CT','NX_CT','LX_29','LX_22','79B-905.01','79B-905.02')
) o(n,code,owner,short_kind,long_kind,short_plate,long_plate);

INSERT INTO nhan_vien(ma_nhan_vien,ho_ten,so_dien_thoai,email,ngay_sinh,so_bang_lai,
ngay_het_han_bang_lai,loai_nhan_vien,trang_thai,ma_nha_xe)
SELECT 'NV_'||o.code||'_'||lpad(r::text,2,'0'),
(ARRAY['Nguyễn','Trần','Lê','Phạm','Võ'])[o.n]||' '||
(ARRAY['Minh Đức','Quốc Hưng','Thanh Hải','Hoàng Nam','Thu Hà','Ngọc Anh','Thanh Bình'])[r],
'091'||lpad((6000000+o.n*10+r)::text,7,'0'),
CASE WHEN r<=4 THEN 'tx'||r WHEN r<=6 THEN 'px'||(r-4) ELSE 'dieuhanh' END
||'.'||lower(o.code)||'@datvexe.test',DATE '1980-01-01'+o.n*365+r*90,
CASE WHEN r<=4 THEN (720000000000+o.n*10+r)::text END,
CASE WHEN r<=4 THEN DATE '2030-12-31' END,
CASE WHEN r<=4 THEN 'TAI_XE' WHEN r<=6 THEN 'PHU_XE' ELSE 'DIEU_HANH' END,
'DANG_LAM_VIEC',o.owner
FROM project_operators o CROSS JOIN generate_series(1,7) r
ON CONFLICT(ma_nhan_vien) DO NOTHING;

-- Hash PBKDF2 đúng định dạng Passwords.java. Mật khẩu khởi tạo ghi trong README.
CREATE TEMP TABLE project_password ON COMMIT DROP AS
SELECT 'pbkdf2-sha256$600000$8MXbJEq0IAAztwPNXV0eBw==$9I3uM7l3WCitmTOmyEOWJns3cxO09psqo8VRm4nWuRo='::text AS hash;
INSERT INTO nguoi_dung(ma_nguoi_dung,ho_ten,so_dien_thoai,email,mat_khau,loai_tai_khoan,
trang_thai,ma_nha_xe,ma_nhan_vien)
SELECT 'USR_'||n.ma_nhan_vien,n.ho_ten,n.so_dien_thoai,n.email,p.hash,
CASE WHEN n.loai_nhan_vien='DIEU_HANH' THEN 'NHA_XE' ELSE 'NHAN_VIEN' END,
'HOAT_DONG',CASE WHEN n.loai_nhan_vien='DIEU_HANH' THEN n.ma_nha_xe END,n.ma_nhan_vien
FROM nhan_vien n CROSS JOIN project_password p
WHERE n.ma_nhan_vien IN (SELECT 'NV_'||code||'_'||lpad(r::text,2,'0') FROM project_operators CROSS JOIN generate_series(1,7) r)
ON CONFLICT(ma_nguoi_dung) DO NOTHING;

INSERT INTO nguoi_dung(ma_nguoi_dung,ho_ten,so_dien_thoai,email,mat_khau,loai_tai_khoan,trang_thai)
SELECT id,name,phone,email,p.hash,'ADMIN','HOAT_DONG'
FROM (VALUES ('USR_ADMIN_01','Nguyễn Minh Khang','0901000001','admin1@datvexe.test'),
('USR_ADMIN_02','Trần Ngọc Linh','0901000002','admin2@datvexe.test')) a(id,name,phone,email)
CROSS JOIN project_password p ON CONFLICT(ma_nguoi_dung) DO NOTHING;

-- Chuẩn hóa tên khởi tạo cũ; giữ tên người dùng đã tự cập nhật.
UPDATE nguoi_dung SET ho_ten='Nguyễn Minh Khang'
WHERE ma_nguoi_dung='USR_ADMIN_01' AND email='admin1@datvexe.test' AND ho_ten='Quản trị hệ thống 1';
UPDATE nguoi_dung SET ho_ten='Trần Ngọc Linh'
WHERE ma_nguoi_dung='USR_ADMIN_02' AND email='admin2@datvexe.test' AND ho_ten='Quản trị hệ thống 2';

INSERT INTO nguoi_dung(ma_nguoi_dung,ho_ten,so_dien_thoai,email,mat_khau,loai_tai_khoan,trang_thai)
SELECT 'USR_KH_'||lpad(r::text,2,'0'),
(ARRAY['Nguyễn Văn An','Trần Thị Bích','Lê Hoàng Cường','Phạm Minh Anh','Võ Thanh Tâm',
'Đặng Ngọc Hà','Bùi Quốc Huy','Đỗ Thảo Linh','Huỳnh Gia Bảo','Phan Thu Trang'])[r],
'0987'||lpad(r::text,6,'0'),'khach'||r||'@datvexe.test',p.hash,'KHACH_HANG','HOAT_DONG'
FROM generate_series(1,10) r CROSS JOIN project_password p
ON CONFLICT(ma_nguoi_dung) DO NOTHING;

INSERT INTO xe(ma_xe,bien_so,ma_loai_xe,so_cho_ngoi,so_do_ngoi,ma_nha_xe,nam_san_xuat,
ngay_dang_kiem,han_dang_kiem,dung_tich_xang,toc_do_gio,trang_thai)
SELECT 'XE_'||o.code||'_'||lpad(r::text,2,'0'),
CASE r WHEN 1 THEN short_plate ELSE long_plate END,l.ma_loai_xe,l.so_cho_ngoi,l.so_tang,
o.owner,2024,CURRENT_DATE-30,DATE '2030-12-31',180,60,'SAN_SANG'
FROM project_operators o CROSS JOIN generate_series(1,2) r
JOIN loai_xe l ON l.ma_loai_xe=CASE r WHEN 1 THEN short_kind ELSE long_kind END
ON CONFLICT(ma_xe) DO NOTHING;

INSERT INTO ghe(ma_ghe,ma_xe,vi_tri,tang,trang_thai)
SELECT x.ma_xe||'_G'||lpad(r::text,2,'0'),x.ma_xe,
CASE x.so_cho_ngoi WHEN 22 THEN 'P'||lpad(r::text,2,'0') WHEN 29 THEN 'G'||lpad(r::text,2,'0')
ELSE CASE WHEN r<=20 THEN 'A' ELSE 'B' END||lpad(((r-1)%20+1)::text,2,'0') END,
CASE WHEN x.so_cho_ngoi=29 OR r<=x.so_cho_ngoi/2 THEN 1 ELSE 2 END,'HOAT_DONG'
FROM xe x CROSS JOIN LATERAL generate_series(1,x.so_cho_ngoi) r
WHERE x.ma_xe IN (SELECT 'XE_'||code||'_'||lpad(r::text,2,'0') FROM project_operators CROSS JOIN generate_series(1,2) r)
ON CONFLICT(ma_ghe) DO NOTHING;
COMMIT;
