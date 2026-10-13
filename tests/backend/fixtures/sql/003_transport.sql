-- T06 transport fixtures. Requires schemas 001/002 and seeds 001/002.
-- Test data based on real places; vehicles, fares and schedules are sample inputs.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
INSERT INTO nha_xe(ma_nha_xe,ten_nha_xe,mo_ta,trang_thai) VALUES
('T06_NX_A','Phương Trang - FUTA Bus Lines','Dữ liệu mẫu phục vụ đồ án','HOAT_DONG'),
('T06_NX_B','Thành Bưởi','Dữ liệu mẫu phục vụ đồ án','HOAT_DONG'),
('T06_NX_C','Hoàng Long','Dữ liệu mẫu phục vụ đồ án','HOAT_DONG'),
('T06_NX_D','Hải Sơn','Dữ liệu mẫu phục vụ đồ án','HOAT_DONG') ON CONFLICT (ma_nha_xe) DO NOTHING;
INSERT INTO tinh_thanh(ma_tinh_thanh,ten_tinh_thanh,khu_vuc) VALUES
('T06_DN','Đà Nẵng','Miền Trung'),('T06_HN','Hà Nội','Miền Bắc') ON CONFLICT (ma_tinh_thanh) DO NOTHING;
INSERT INTO ben_xe(ma_ben_xe,ten_ben_xe,dia_chi,ma_tinh_thanh,trang_thai) VALUES
('T06_BX_HCM_E','Bến xe Miền Đông mới','501 Hoàng Hữu Nam, TP. Hồ Chí Minh','DEMO_HCM','HOAT_DONG'),
('T06_BX_HCM_W','Bến xe Miền Tây','395 Kinh Dương Vương, TP. Hồ Chí Minh','DEMO_HCM','HOAT_DONG'),
('T06_BX_LD','Bến xe Liên tỉnh Đà Lạt','1 Tô Hiến Thành, Đà Lạt, Lâm Đồng','DEMO_LD','HOAT_DONG'),
('T06_BX_CT','Bến xe Trung tâm Cần Thơ','Quốc lộ 1, khu đô thị Nam Cần Thơ, Cần Thơ','DEMO_CT','HOAT_DONG'),
('T06_BX_KH','Bến xe Phía Nam Nha Trang','Km 6 đường 23 Tháng 10, Khánh Hòa','DEMO_KH','HOAT_DONG'),
('T06_BX_DN','Bến xe Trung tâm Đà Nẵng','185 Tôn Đức Thắng, Đà Nẵng','T06_DN','HOAT_DONG'),
('T06_BX_HN','Bến xe Nước Ngầm','1 Ngọc Hồi, Hà Nội','T06_HN','HOAT_DONG')
ON CONFLICT (ma_ben_xe) DO NOTHING;
CREATE TEMP TABLE t06_routes ON COMMIT DROP AS
SELECT (p.n-1)*2+d.n AS id,
CASE WHEN d.n=1 THEN p.a ELSE p.b END AS departure,
CASE WHEN d.n=1 THEN p.b ELSE p.a END AS arrival,
CASE WHEN d.n=1 THEN p.a_name||' - '||p.b_name ELSE p.b_name||' - '||p.a_name END AS name,
p.hours,p.fare,CASE WHEN p.n=7 THEN 'T06_NX_C' WHEN p.n=8 THEN 'T06_NX_D'
WHEN p.n%2=1 THEN 'T06_NX_A' ELSE 'T06_NX_B' END AS operator
FROM (VALUES
(1,'T06_BX_HCM_E','T06_BX_LD','TP. Hồ Chí Minh','Đà Lạt',7,280000),
(2,'T06_BX_HCM_W','T06_BX_CT','TP. Hồ Chí Minh','Cần Thơ',4,180000),
(3,'T06_BX_HCM_E','T06_BX_KH','TP. Hồ Chí Minh','Nha Trang',8,320000),
(4,'T06_BX_HCM_E','T06_BX_DN','TP. Hồ Chí Minh','Đà Nẵng',20,600000),
(5,'T06_BX_HCM_E','T06_BX_HN','TP. Hồ Chí Minh','Hà Nội',36,950000),
(6,'T06_BX_DN','T06_BX_HN','Đà Nẵng','Hà Nội',14,450000),
(7,'T06_BX_HCM_W','T06_BX_CT','TP. Hồ Chí Minh','Cần Thơ',4,180000),
(8,'T06_BX_HCM_E','T06_BX_KH','TP. Hồ Chí Minh','Nha Trang',8,320000)) p(n,a,b,a_name,b_name,hours,fare)
CROSS JOIN (VALUES (1),(2)) d(n);
INSERT INTO tuyen_xe(ma_tuyen,ten_tuyen,tram_khoi_hanh,tram_den,thoi_gian_khoi_hanh,
thoi_gian_du_kien,gia_co_ban,trang_thai,ma_nha_xe)
SELECT 'T06_R_'||id,name,departure,arrival,'08:00',hours,fare,'DANG_KHAI_THAC',operator FROM t06_routes
ON CONFLICT (ma_tuyen) DO NOTHING;
INSERT INTO diem_dung(ma_diem_dung,ma_tuyen,ten_diem_dung,dia_chi,thu_tu,thoi_gian_dung,loai_diem_dung)
SELECT 'T06_D_'||id||'_'||n,'T06_R_'||id,'Điểm nghỉ tuyến '||n,
'Điểm trung gian tuyến '||name,n,15,'CA_HAI' FROM t06_routes CROSS JOIN generate_series(1,2) n
WHERE hours>=7 ON CONFLICT (ma_diem_dung) DO NOTHING;
CREATE TEMP TABLE t06_vehicles ON COMMIT DROP AS
SELECT (r.id-1)*3+k.n AS id,r.id AS route,r.operator,k.kind,k.capacity,
CASE k.n WHEN 1 THEN TIME '08:00' WHEN 2 THEN TIME '14:00' ELSE TIME '22:00' END AS departure,r.hours
FROM t06_routes r CROSS JOIN (VALUES (1,'DEMO_22',22),(2,'DEMO_LX',29),(3,'DEMO_LX2',40)) k(n,kind,capacity);
INSERT INTO xe(ma_xe,bien_so,ma_loai_xe,so_cho_ngoi,ma_nha_xe,nam_san_xuat,
ngay_dang_kiem,han_dang_kiem,trang_thai)
SELECT 'T06_X_'||id,'51B-'||lpad((900+id)::text,3,'0')||'.'||lpad((id%100)::text,2,'0'),kind,capacity,operator,2022+(id%3),
CURRENT_DATE-30,CURRENT_DATE+365,'SAN_SANG' FROM t06_vehicles ON CONFLICT (ma_xe) DO NOTHING;
INSERT INTO xe(ma_xe,bien_so,ma_loai_xe,so_cho_ngoi,ma_nha_xe,nam_san_xuat,
ngay_dang_kiem,han_dang_kiem,trang_thai) VALUES
('T06_X_BAOTRI','51B-998.98','DEMO_22',22,'T06_NX_A',2022,CURRENT_DATE-30,CURRENT_DATE+365,'BAO_TRI'),
('T06_X_HETHAN','51B-999.99','DEMO_LX',29,'T06_NX_B',2022,CURRENT_DATE-365,CURRENT_DATE-1,'SAN_SANG')
ON CONFLICT (ma_xe) DO NOTHING;
INSERT INTO ghe(ma_ghe,ma_xe,vi_tri,tang,trang_thai)
SELECT x.ma_xe||'_G'||lpad(n::text,3,'0'),x.ma_xe,CASE x.so_cho_ngoi WHEN 22 THEN 'P'||lpad(n::text,2,'0') WHEN 29 THEN lpad(n::text,2,'0') ELSE CASE WHEN n<=20 THEN 'A' ELSE 'B' END||lpad(((n-1)%20+1)::text,2,'0') END,
1+(n-1)*l.so_tang/x.so_cho_ngoi,
CASE WHEN x.ma_xe='T06_X_1' AND n=22 THEN 'BAO_TRI'
WHEN x.ma_xe='T06_X_2' AND n=29 THEN 'NGUNG_SU_DUNG' ELSE 'HOAT_DONG' END
FROM xe x JOIN loai_xe l USING(ma_loai_xe) CROSS JOIN LATERAL generate_series(1,x.so_cho_ngoi) n
WHERE x.ma_xe IN (SELECT 'T06_X_'||id FROM t06_vehicles UNION ALL SELECT 'T06_X_BAOTRI' UNION ALL SELECT 'T06_X_HETHAN')
ON CONFLICT (ma_ghe) DO NOTHING;
INSERT INTO nhan_vien(ma_nhan_vien,ho_ten,so_dien_thoai,email,so_bang_lai,ngay_het_han_bang_lai,
loai_nhan_vien,trang_thai,ma_nha_xe)
SELECT 'T06_'||role||'_'||v.id,'Nhân viên '||role||' '||v.id,'T06-PHONE-'||role||'-'||v.id,
lower(role)||v.id||'@t06.example.test',CASE WHEN role='TX' THEN 'T06-LICENSE-'||v.id END,
CASE WHEN role='TX' THEN CURRENT_DATE+365 END,CASE WHEN role='TX' THEN 'TAI_XE' ELSE 'PHU_XE' END,
'DANG_LAM_VIEC',operator FROM t06_vehicles v CROSS JOIN (VALUES ('TX'),('PX')) roles(role)
ON CONFLICT (ma_nhan_vien) DO NOTHING;
INSERT INTO chuyen_xe(ma_chuyen,ma_tuyen,ma_xe,ngay_khoi_hanh,gio_khoi_hanh,gio_den_du_kien,trang_thai)
SELECT 'T06_C_'||v.id||'_'||d,'T06_R_'||v.route,'T06_X_'||v.id,CURRENT_DATE+d,v.departure,
(CURRENT_DATE+d)+v.departure+make_interval(hours=>v.hours),'CHUA_KHOI_HANH'
FROM t06_vehicles v CROSS JOIN LATERAL generate_series(0,30,CASE WHEN v.hours>24 THEN 2 ELSE 1 END) d
ON CONFLICT (ma_chuyen) DO NOTHING;
INSERT INTO chuyen_xe(ma_chuyen,ma_tuyen,ma_xe,ngay_khoi_hanh,gio_khoi_hanh,gio_den_du_kien,trang_thai) VALUES
('T06_C_DONE','T06_R_1','T06_X_1',CURRENT_DATE-3,'08:00',(CURRENT_DATE-3)+TIME '15:00','HOAN_TAT'),
('T06_C_CANCEL','T06_R_2',NULL,CURRENT_DATE+5,'08:00',(CURRENT_DATE+5)+TIME '15:00','DA_HUY'),
('T06_C_NO_VEHICLE','T06_R_1',NULL,CURRENT_DATE+5,'12:00',(CURRENT_DATE+5)+TIME '19:00','CHUA_KHOI_HANH')
ON CONFLICT (ma_chuyen) DO NOTHING;
INSERT INTO phan_cong_chuyen_xe(ma_phan_cong,ma_chuyen,ma_nhan_vien,vai_tro,ghi_chu)
SELECT 'T06_PC_'||v.id||'_'||d||'_'||role,'T06_C_'||v.id||'_'||d,'T06_'||role||'_'||v.id,
CASE WHEN role='TX' THEN 'TAI_XE_CHINH' ELSE 'PHU_XE' END,'Phân công cùng nhà xe'
FROM t06_vehicles v CROSS JOIN LATERAL generate_series(0,30,CASE WHEN v.hours>24 THEN 2 ELSE 1 END) d
CROSS JOIN (VALUES ('TX'),('PX')) roles(role) ON CONFLICT (ma_phan_cong) DO NOTHING;
COMMIT;
