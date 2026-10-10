-- Fictional operators, staff, identifiers, fares and durations for testing only.
-- Run after schema 001, schema 002 and seed 001. Existing records are never overwritten.
BEGIN;
INSERT INTO nha_xe(ma_nha_xe,ten_nha_xe,mo_ta,trang_thai)
VALUES ('DEMO_NX_MORE','Nhà xe mô phỏng Miền Nam','Dữ liệu giả lập phục vụ kiểm thử, không phải doanh nghiệp thật','HOAT_DONG') ON CONFLICT DO NOTHING;
INSERT INTO loai_xe(ma_loai_xe,ten_loai_xe,so_cho_ngoi,so_tang,mo_ta)
VALUES ('DEMO_22','Giường nằm 22 chỗ',22,2,'Cấu hình demo') ON CONFLICT DO NOTHING;
INSERT INTO tinh_thanh(ma_tinh_thanh,ten_tinh_thanh)
VALUES ('DEMO_CT','Cần Thơ'),('DEMO_KH','Khánh Hòa') ON CONFLICT DO NOTHING;
INSERT INTO ben_xe(ma_ben_xe,ten_ben_xe,dia_chi,ma_tinh_thanh,trang_thai)
VALUES ('DEMO_CT_BX','Điểm đón demo Cần Thơ','Cần Thơ (địa chỉ mô phỏng)','DEMO_CT','HOAT_DONG'),
('DEMO_KH_BX','Điểm đón demo Nha Trang','Khánh Hòa (địa chỉ mô phỏng)','DEMO_KH','HOAT_DONG') ON CONFLICT DO NOTHING;
INSERT INTO tuyen_xe(ma_tuyen,ten_tuyen,tram_khoi_hanh,tram_den,thoi_gian_khoi_hanh,thoi_gian_du_kien,gia_co_ban,trang_thai,ma_nha_xe)
VALUES ('DEMO_R_CT','TP. Hồ Chí Minh - Cần Thơ','DEMO_BX1','DEMO_CT_BX','07:00',4,180000,'DANG_KHAI_THAC','DEMO_NX_MORE'),
('DEMO_R_KH','TP. Hồ Chí Minh - Nha Trang','DEMO_BX1','DEMO_KH_BX','07:00',8,320000,'DANG_KHAI_THAC','DEMO_NX_MORE'),
('DEMO_R_LD','TP. Hồ Chí Minh - Đà Lạt','DEMO_BX1','DEMO_BX2','07:00',7,280000,'DANG_KHAI_THAC','DEMO_NX_MORE') ON CONFLICT DO NOTHING;
INSERT INTO xe(ma_xe,bien_so,ma_loai_xe,so_cho_ngoi,ma_nha_xe,nam_san_xuat,ngay_dang_kiem,han_dang_kiem,trang_thai,dung_tich_xang,toc_do_gio)
SELECT 'DEMO_FLEET_'||i,'DEMO-FLEET-'||lpad(i::text,3,'0'),
CASE i%3 WHEN 1 THEN 'DEMO_LX' WHEN 2 THEN 'DEMO_LX2' ELSE 'DEMO_22' END,
CASE i%3 WHEN 1 THEN 29 WHEN 2 THEN 40 ELSE 22 END,'DEMO_NX_MORE',2020+i,
CURRENT_DATE-60,CURRENT_DATE+365,'SAN_SANG',120,55 FROM generate_series(1,6) i ON CONFLICT DO NOTHING;
INSERT INTO ghe(ma_ghe,vi_tri,tang,trang_thai,ma_xe)
SELECT x.ma_xe||'_G'||i,'G'||lpad(i::text,3,'0'),1+(i-1)*l.so_tang/x.so_cho_ngoi,'HOAT_DONG',x.ma_xe
FROM xe x JOIN loai_xe l ON l.ma_loai_xe=x.ma_loai_xe CROSS JOIN LATERAL generate_series(1,x.so_cho_ngoi) i
WHERE x.ma_xe LIKE 'DEMO_FLEET_%' ON CONFLICT DO NOTHING;
INSERT INTO nhan_vien(ma_nhan_vien,ho_ten,so_dien_thoai,email,so_bang_lai,ngay_het_han_bang_lai,loai_nhan_vien,trang_thai,ma_nha_xe)
SELECT 'DEMO_STAFF_'||i,CASE WHEN i<=6 THEN 'Tài xế mô phỏng ' ELSE 'Phụ xe mô phỏng ' END||i,
'TEST-PHONE-'||i,'staff'||i||'@example.test',CASE WHEN i<=6 THEN 'TEST-LICENSE-'||i END,
CASE WHEN i<=6 THEN CURRENT_DATE+365 END,CASE WHEN i<=6 THEN 'TAI_XE' ELSE 'PHU_XE' END,
'DANG_LAM_VIEC','DEMO_NX_MORE' FROM generate_series(1,12) i ON CONFLICT DO NOTHING;
INSERT INTO chuyen_xe(ma_chuyen,ma_tuyen,ma_xe,ngay_khoi_hanh,gio_khoi_hanh,gio_den_du_kien,trang_thai)
SELECT 'DEMO_SCHEDULE_'||i||'_'||d,
CASE i%3 WHEN 1 THEN 'DEMO_R_CT' WHEN 2 THEN 'DEMO_R_KH' ELSE 'DEMO_R_LD' END,
'DEMO_FLEET_'||i,CURRENT_DATE+d, TIME '07:00',
(CURRENT_DATE+d)+TIME '07:00'+make_interval(hours=>CASE i%3 WHEN 1 THEN 4 WHEN 2 THEN 8 ELSE 7 END),'CHUA_KHOI_HANH'
FROM generate_series(1,6) i CROSS JOIN generate_series(1,7) d ON CONFLICT DO NOTHING;
INSERT INTO phan_cong_chuyen_xe(ma_phan_cong,ma_chuyen,ma_nhan_vien,vai_tro,ghi_chu)
SELECT 'DEMO_ASSIGN_'||i||'_'||d||'_'||r,'DEMO_SCHEDULE_'||i||'_'||d,
'DEMO_STAFF_'||(i+CASE WHEN r=1 THEN 0 ELSE 6 END),
CASE WHEN r=1 THEN 'TAI_XE_CHINH' ELSE 'PHU_XE' END,'Lịch mô phỏng, mỗi nhân viên một chuyến mỗi ngày'
FROM generate_series(1,6) i CROSS JOIN generate_series(1,7) d CROSS JOIN generate_series(1,2) r ON CONFLICT DO NOTHING;
COMMIT;
