-- T11: hồ sơ nhân viên và phân công đi-về; dùng schema 001–003 và seed nền 001–003, 005.
-- Tên hãng/địa danh để tham khảo; nhân viên, xe và lịch là dữ liệu mẫu của đồ án.
-- Nạp lại giữ bản ghi đã có; không xóa dữ liệu hoặc thay đổi schema.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';

-- 1. Chuẩn hóa tên nhân viên T06 còn hậu tố TX/PX; giữ mã và lịch sử.
WITH staff AS (
SELECT ma_nhan_vien,substring(ma_nhan_vien from '[0-9]+$')::int
    +CASE WHEN loai_nhan_vien='PHU_XE' THEN 48 ELSE 0 END AS n
FROM nhan_vien WHERE ma_nhan_vien ~ '^T06_(TX|PX)_([1-9]|[1-3][0-9]|4[0-8])$'
AND ho_ten ~ ' (TX|PX)_[0-9]+$'
)
UPDATE nhan_vien employee SET ho_ten=
(ARRAY['Nguyễn','Trần','Lê','Phạm','Võ','Đặng'])[(s.n-1)%6+1]||' '||
(ARRAY['Văn','Thanh','Minh','Quốc'])[((s.n-1)/6)%4+1]||' '||
(ARRAY['An','Bình','Hải','Hưng'])[((s.n-1)/24)%4+1]
FROM staff s WHERE employee.ma_nhan_vien=s.ma_nhan_vien;

-- 2. Tái sử dụng sáu nhà xe và loại xe có sẵn (22/29/40 chỗ).
CREATE TEMP TABLE t11_operators ON COMMIT DROP AS
SELECT * FROM (VALUES
(1,'T06_NX_A','DEMO_22',22),(2,'T06_NX_B','DEMO_LX',29),
(3,'T06_NX_C','DEMO_LX2',40),(4,'T06_NX_D','DEMO_22',22),
(5,'DEMO_NX','DEMO_LX',29),(6,'DEMO_NX_MORE','DEMO_LX2',40)) o(n,owner,kind,capacity);

-- 3. Mỗi nhà xe có hai tài xế và một phụ xe.
INSERT INTO nhan_vien(ma_nhan_vien,ho_ten,so_dien_thoai,email,ngay_sinh,so_bang_lai,
ngay_het_han_bang_lai,loai_nhan_vien,trang_thai,ma_nha_xe)
SELECT 'NV_'||lpad((o.n*10+r.n)::text,3,'0'),
(ARRAY['Nguyễn','Trần','Lê','Phạm','Võ','Đặng'])[o.n]||' '||
(ARRAY['Minh Anh','Quốc Hưng','Thanh Hải'])[r.n],
'086'||lpad((9000000+o.n*10+r.n)::text,7,'0'),
'staff.'||(o.n*10+r.n)||'@staff.example.test',DATE '1980-01-01'+(o.n*365+r.n*30),
CASE WHEN r.n<=2 THEN lpad((720000000000+o.n*10+r.n)::text,12,'0') END,
CASE WHEN r.n<=2 THEN CURRENT_DATE+730 END,
CASE WHEN r.n<=2 THEN 'TAI_XE' ELSE 'PHU_XE' END,'DANG_LAM_VIEC',o.owner
FROM t11_operators o CROSS JOIN (VALUES (1),(2),(3)) r(n)
ON CONFLICT(ma_nhan_vien) DO NOTHING;

-- 4. Xe và ghế dành cho kiểm tra phân công; không dùng chung lịch xe T06.
INSERT INTO xe(ma_xe,bien_so,ma_loai_xe,so_cho_ngoi,ma_nha_xe,nam_san_xuat,
ngay_dang_kiem,han_dang_kiem,trang_thai)
SELECT 'XE_'||lpad(n::text,3,'0'),'51B-820.'||lpad(n::text,2,'0'),kind,capacity,owner,
2023,CURRENT_DATE-30,CURRENT_DATE+365,'SAN_SANG' FROM t11_operators
ON CONFLICT(ma_xe) DO NOTHING;
INSERT INTO ghe(ma_ghe,ma_xe,vi_tri,tang,trang_thai)
SELECT x.ma_xe||'_G'||lpad(n::text,2,'0'),x.ma_xe,
CASE x.so_cho_ngoi WHEN 22 THEN 'P'||lpad(n::text,2,'0') WHEN 29 THEN lpad(n::text,2,'0')
ELSE CASE WHEN n<=20 THEN 'A' ELSE 'B' END||lpad(((n-1)%20+1)::text,2,'0') END,
CASE WHEN x.so_cho_ngoi=29 THEN 1 WHEN n<=x.so_cho_ngoi/2 THEN 1 ELSE 2 END,'HOAT_DONG'
FROM xe x CROSS JOIN LATERAL generate_series(1,x.so_cho_ngoi) n
WHERE x.ma_xe IN (SELECT 'XE_'||lpad(n::text,3,'0') FROM t11_operators)
ON CONFLICT(ma_ghe) DO NOTHING;

-- 5. Chuyến đi và về nối đúng bến; lịch cố định từ lần nạp đầu.
INSERT INTO tuyen_xe(ma_tuyen,ten_tuyen,tram_khoi_hanh,tram_den,thoi_gian_khoi_hanh,
thoi_gian_du_kien,gia_co_ban,trang_thai,ma_nha_xe)
SELECT 'TX_'||o.n||'_'||direction,
CASE direction WHEN 1 THEN 'TP. Hồ Chí Minh - Đà Lạt' ELSE 'Đà Lạt - TP. Hồ Chí Minh' END,
CASE direction WHEN 1 THEN 'T06_BX_HCM_E' ELSE 'T06_BX_LD' END,
CASE direction WHEN 1 THEN 'T06_BX_LD' ELSE 'T06_BX_HCM_E' END,
'08:00',7,280000,'DANG_KHAI_THAC',owner
FROM t11_operators o CROSS JOIN (VALUES (1),(2)) d(direction)
ON CONFLICT(ma_tuyen) DO NOTHING;
INSERT INTO chuyen_xe(ma_chuyen,ma_tuyen,ma_xe,ngay_khoi_hanh,gio_khoi_hanh,gio_den_du_kien,trang_thai)
SELECT 'CX_'||o.n||'_'||direction,'TX_'||o.n||'_'||direction,'XE_'||lpad(o.n::text,3,'0'),
CURRENT_DATE+direction,'08:00',(CURRENT_DATE+direction)+TIME '15:00','CHUA_KHOI_HANH'
FROM t11_operators o CROSS JOIN (VALUES (1),(2)) d(direction)
ON CONFLICT(ma_chuyen) DO NOTHING;

-- 6. Ba người/chuyến; dấu SEED là xác nhận mẫu, không chứng nhận nghỉ thực tế.
INSERT INTO phan_cong_chuyen_xe(ma_phan_cong,ma_chuyen,ma_nhan_vien,vai_tro,ghi_chu)
SELECT 'PC_'||o.n||'_'||direction||'_'||r.n,c.ma_chuyen,'NV_'||lpad((o.n*10+r.n)::text,3,'0'),
CASE r.n WHEN 1 THEN 'TAI_XE_CHINH' WHEN 2 THEN 'TAI_XE_PHU' ELSE 'PHU_XE' END,
'[T11_PLAN:'||to_char(c.ngay_khoi_hanh+c.gio_khoi_hanh,'YYYY-MM-DD"T"HH24:MI:SS')||'/'||to_char(c.gio_den_du_kien,'YYYY-MM-DD"T"HH24:MI:SS')||'/'||c.ma_xe||':SEED] '
||'Kịch bản có kế hoạch nghỉ và đổi tài xế; xác nhận lại khi thay đổi lịch.'
FROM t11_operators o CROSS JOIN (VALUES (1),(2)) d(direction) CROSS JOIN (VALUES (1),(2),(3)) r(n)
JOIN chuyen_xe c ON c.ma_chuyen='CX_'||o.n||'_'||direction
ON CONFLICT(ma_phan_cong) DO NOTHING;
COMMIT;
