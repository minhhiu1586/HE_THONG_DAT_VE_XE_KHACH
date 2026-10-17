-- 20 tuyến một chiều, lịch 14 ngày tiếp theo. Xe ngắn chạy đi-về mỗi ngày;
-- xe dài luân phiên chiều theo ngày lịch, tránh trùng chuyến và sai bến.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
CREATE TEMP TABLE project_routes ON COMMIT DROP AS
SELECT * FROM (VALUES
('FUTA','NX_FUTA',1,'BX_MT','BX_CT','TP. Hồ Chí Minh','Cần Thơ',3.5,165,180000),
('FUTA','NX_FUTA',2,'BX_MD_MOI','BX_DL','TP. Hồ Chí Minh','Đà Lạt',7.0,310,290000),
('TB','NX_TB',1,'BX_MT','BX_CT','TP. Hồ Chí Minh','Cần Thơ',3.5,165,190000),
('TB','NX_TB',2,'BX_MD_MOI','BX_DL','TP. Hồ Chí Minh','Đà Lạt',6.5,305,340000),
('KUMHO','NX_KUMHO',1,'BX_MD_MOI','BX_VT','TP. Hồ Chí Minh','Vũng Tàu',2.5,100,150000),
('KUMHO','NX_KUMHO',2,'BX_MD_MOI','BX_NT','TP. Hồ Chí Minh','Nha Trang',8.5,430,320000),
('HL','NX_HL',1,'BX_NN','BX_NB','Hà Nội','Ninh Bình',2.5,95,140000),
('HL','NX_HL',2,'BX_NN','BX_DN','Hà Nội','Đà Nẵng',16.0,770,550000),
('CT','NX_CT',1,'BX_NT','BX_PR','Nha Trang','Phan Rang',2.0,100,140000),
('CT','NX_CT',2,'BX_NT','BX_MD_MOI','Nha Trang','TP. Hồ Chí Minh',8.5,430,380000)
) r(code,owner,vehicle_no,station_a,station_b,name_a,name_b,hours,km,price);

INSERT INTO tuyen_xe(ma_tuyen,ten_tuyen,tram_khoi_hanh,tram_den,thoi_gian_khoi_hanh,
thoi_gian_du_kien,khoang_cach_km,gia_co_ban,trang_thai,ma_nha_xe)
SELECT 'TX_'||code||'_'||vehicle_no||'_'||direction,
CASE direction WHEN 1 THEN name_a||' - '||name_b ELSE name_b||' - '||name_a END,
CASE direction WHEN 1 THEN station_a ELSE station_b END,
CASE direction WHEN 1 THEN station_b ELSE station_a END,
CASE WHEN vehicle_no=2 THEN TIME '20:00' WHEN direction=1 THEN TIME '08:00' ELSE TIME '14:00' END,
hours,km,price,'DANG_KHAI_THAC',owner
FROM project_routes CROSS JOIN generate_series(1,2) direction
ON CONFLICT(ma_tuyen) DO NOTHING;

-- Đón/trả tại đúng bến; điểm nghỉ không được tự coi là điểm đón khách.
INSERT INTO diem_dung(ma_diem_dung,ma_tuyen,ten_diem_dung,dia_chi,thu_tu,thoi_gian_dung,loai_diem_dung)
SELECT t.ma_tuyen||'_DD'||r,t.ma_tuyen,b.ten_ben_xe,b.dia_chi,r,15,
CASE r WHEN 1 THEN 'DIEM_DON' ELSE 'DIEM_TRA' END
FROM tuyen_xe t CROSS JOIN generate_series(1,2) r
JOIN ben_xe b ON b.ma_ben_xe=CASE r WHEN 1 THEN t.tram_khoi_hanh ELSE t.tram_den END
WHERE t.ma_tuyen IN (SELECT 'TX_'||code||'_'||vehicle_no||'_'||d FROM project_routes CROSS JOIN generate_series(1,2) d)
ON CONFLICT(ma_diem_dung) DO NOTHING;

CREATE TEMP TABLE project_departures ON COMMIT DROP AS
SELECT r.code,r.vehicle_no,day::date,
CASE WHEN r.vehicle_no=1 THEN d ELSE 1+((day::date-DATE '2026-01-01')%2) END AS direction,
CASE WHEN r.vehicle_no=2 THEN TIME '20:00' WHEN d=1 THEN TIME '08:00' ELSE TIME '14:00' END AS departure,
r.hours
FROM project_routes r
CROSS JOIN generate_series(CURRENT_DATE+1,CURRENT_DATE+14,INTERVAL '1 day') day
CROSS JOIN generate_series(1,2) d WHERE r.vehicle_no=1 OR d=1;

INSERT INTO chuyen_xe(ma_chuyen,ma_tuyen,ma_xe,ngay_khoi_hanh,gio_khoi_hanh,gio_den_du_kien,trang_thai)
SELECT 'CX_'||code||'_'||vehicle_no||'_'||to_char(day,'YYYYMMDD')||'_'||direction,
'TX_'||code||'_'||vehicle_no||'_'||direction,'XE_'||code||'_'||lpad(vehicle_no::text,2,'0'),
day,departure,day+departure+(hours*60)*INTERVAL '1 minute','NHAP'
FROM project_departures ON CONFLICT(ma_chuyen) DO NOTHING;

-- Tổ ngắn: tài xế 01 + phụ xe 05. Tài xế 02 dự phòng.
-- Tổ dài: tài xế 03/04 + phụ xe 06. Dấu xác nhận chứa chính lịch/xe của chuyến.
INSERT INTO phan_cong_chuyen_xe(ma_phan_cong,ma_chuyen,ma_nhan_vien,vai_tro,ghi_chu)
SELECT c.ma_chuyen||'_PC'||r,c.ma_chuyen,
'NV_'||p.code||'_'||lpad((CASE WHEN p.vehicle_no=1 THEN CASE r WHEN 1 THEN 1 ELSE 5 END
ELSE CASE r WHEN 1 THEN 3 WHEN 2 THEN 4 ELSE 6 END END)::text,2,'0'),
CASE WHEN p.vehicle_no=1 THEN CASE r WHEN 1 THEN 'TAI_XE_CHINH' ELSE 'PHU_XE' END
ELSE CASE r WHEN 1 THEN 'TAI_XE_CHINH' WHEN 2 THEN 'TAI_XE_PHU' ELSE 'PHU_XE' END END,
CASE WHEN p.vehicle_no=2 THEN '[T11_PLAN:'||to_char(c.ngay_khoi_hanh+c.gio_khoi_hanh,'YYYY-MM-DD"T"HH24:MI:SS')
||'/'||to_char(c.gio_den_du_kien,'YYYY-MM-DD"T"HH24:MI:SS')||'/'||c.ma_xe||':PROJECT] '
||'Bố trí nghỉ và đổi tài xế theo kế hoạch điều hành; rà soát lại trước vận hành.' END
FROM project_departures p JOIN chuyen_xe c ON c.ma_chuyen='CX_'||p.code||'_'||p.vehicle_no||'_'||to_char(p.day,'YYYYMMDD')||'_'||p.direction
CROSS JOIN generate_series(1,3) r WHERE p.vehicle_no=2 OR r<=2
ON CONFLICT(ma_phan_cong) DO NOTHING;
COMMIT;
