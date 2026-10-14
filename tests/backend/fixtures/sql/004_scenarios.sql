-- T06 fixtures, after seed 003. No production authentication/payment integration.
-- Locked accounts and inactive refund policies; numeric values are test inputs, not approved policies.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
INSERT INTO nguoi_dung(ma_nguoi_dung,ho_ten,so_dien_thoai,email,mat_khau,loai_tai_khoan,trang_thai)
SELECT 'T06_U_'||id,'Khách hàng '||id,'T06-USER-'||id,id::text||'@t06.example.test',
'!T06_DISABLED_NO_AUTH!','KHACH_HANG','KHOA' FROM generate_series(1,20) id
ON CONFLICT (ma_nguoi_dung) DO NOTHING;
INSERT INTO nguoi_dung(ma_nguoi_dung,ho_ten,so_dien_thoai,email,mat_khau,loai_tai_khoan,trang_thai)
SELECT 'T06_U_'||id,'Tài khoản '||id,'T06-USER-'||id,lower(id)||'@t06.example.test',
'!T06_DISABLED_NO_AUTH!',role,'KHOA' FROM (VALUES ('ADMIN','ADMIN'),('NX_A','NHA_XE'),('NX_B','NHA_XE'),
('NV_A','NHAN_VIEN'),('NV_B','NHAN_VIEN')) u(id,role) ON CONFLICT (ma_nguoi_dung) DO NOTHING;
INSERT INTO khuyen_mai(ma_khuyen_mai,ten_khuyen_mai,mo_ta,loai_giam,gia_tri_giam,gia_tri_toi_da,
don_toi_thieu,so_luong,so_luong_da_dung,ngay_bat_dau,ngay_ket_thuc,trang_thai)
SELECT 'T06_KM_'||id,'Mã kiểm thử '||id,'Fixture kiểm tra dữ liệu; không phải chính sách kinh doanh',
'PHAN_TRAM',10,50000,200000,10,used,
LOCALTIMESTAMP+make_interval(days=>starts),LOCALTIMESTAMP+make_interval(days=>ends),state
FROM (VALUES ('ACTIVE',0,-1,30,'HOAT_DONG'),('FUTURE',0,1,30,'HOAT_DONG'),
('EXPIRED',0,-30,-1,'HET_HAN'),('FULL',10,-1,30,'HET_LUOT'),('LAST',9,-1,30,'HOAT_DONG'))
k(id,used,starts,ends,state) ON CONFLICT (ma_khuyen_mai) DO NOTHING;
INSERT INTO khuyen_mai(ma_khuyen_mai,ten_khuyen_mai,mo_ta,loai_giam,gia_tri_giam,don_toi_thieu,
so_luong,so_luong_da_dung,ngay_bat_dau,ngay_ket_thuc,trang_thai) VALUES
('T06_KM_BOUNDARY','Mã kiểm thử đúng hạn','Cố định clock tại ngay_ket_thuc để test biên',
'SO_TIEN_CO_DINH',10000,0,10,0,LOCALTIMESTAMP-INTERVAL '1 day',LOCALTIMESTAMP,'HOAT_DONG')
ON CONFLICT (ma_khuyen_mai) DO NOTHING;
INSERT INTO chinh_sach_hoan_ve(ma_chinh_sach,ten_chinh_sach,so_gio_toi_thieu_truoc_khoi_hanh,
ty_le_hoan,phi_hoan,cho_phep_hoan_ve_khuyen_mai,trang_thai)
SELECT 'T06_CS_'||h,'Fixture chưa áp dụng '||h||' giờ',h,0,0,false,'TAM_NGUNG'
FROM (VALUES (0),(12),(24)) m(h) ON CONFLICT (ma_chinh_sach) DO NOTHING;
INSERT INTO phuong_thuc_thanh_toan(ma_phuong_thuc,ten_phuong_thuc,mo_ta,trang_thai) VALUES
('T06_PAYMENT','Thanh toán kiểm thử','Không kết nối cổng thanh toán','HOAT_DONG')
ON CONFLICT (ma_phuong_thuc) DO NOTHING;
CREATE TEMP TABLE t06_orders ON COMMIT DROP AS
SELECT n,(n-1)%18+1 AS vehicle,15+(n-1)/18 AS day,
CASE WHEN n=1 THEN 1 ELSE 2 END AS quantity FROM generate_series(1,60) n;
INSERT INTO don_dat_ve(ma_don,ma_nguoi_dung,ngay_dat,tong_tien,tien_giam,khach_dat_ten,
khach_dat_so_dien_thoai,ghi_chu,kenh_dat,trang_thai)
SELECT 'T06_O_'||o.n,'T06_U_'||((o.n-1)%20+1),LOCALTIMESTAMP,
r.gia_co_ban*o.quantity,0,'Khách hàng '||o.n,'T06-BOOKER-'||o.n,
CASE o.n WHEN 1 THEN 'Một vé' WHEN 2 THEN 'Nhiều người đi' WHEN 3 THEN 'Lịch sử hoàn toàn bộ, chưa gắn chính sách'
WHEN 4 THEN 'Lịch sử hoàn một vé; vé còn lại hợp lệ' WHEN 5 THEN 'Lịch sử lên xe' ELSE 'Đơn đã thanh toán' END,
'WEB','DA_THANH_TOAN' FROM t06_orders o JOIN chuyen_xe c
ON c.ma_chuyen=CASE WHEN o.n=5 THEN 'T06_C_DONE' ELSE 'T06_C_'||o.vehicle||'_'||o.day END
JOIN tuyen_xe r USING(ma_tuyen) ON CONFLICT (ma_don) DO NOTHING;
INSERT INTO ve_xe(ma_ve,ma_chuyen,ma_ghe,ma_don,ho_ten_khach_hang,so_dien_thoai_khach_hang,
gia_ve,ngay_xuat_ve,trang_thai)
SELECT 'T06_V_'||o.n||'_'||s,
CASE WHEN o.n=5 THEN 'T06_C_DONE' ELSE 'T06_C_'||o.vehicle||'_'||o.day END,
'T06_X_'||CASE WHEN o.n=5 THEN 1 ELSE o.vehicle END||'_G'||lpad(s::text,3,'0'),
'T06_O_'||o.n,'Hành khách '||o.n||'/'||s,'T06-RIDER-'||o.n||'-'||s,
r.gia_co_ban,LOCALTIMESTAMP,
CASE WHEN o.n=3 OR (o.n=4 AND s=1) THEN 'DA_HOAN' WHEN o.n=5 THEN 'DA_LEN_XE' ELSE 'DA_XUAT_VE' END
FROM t06_orders o CROSS JOIN LATERAL generate_series(1,o.quantity) s JOIN chuyen_xe c
ON c.ma_chuyen=CASE WHEN o.n=5 THEN 'T06_C_DONE' ELSE 'T06_C_'||o.vehicle||'_'||o.day END
JOIN tuyen_xe r USING(ma_tuyen) ON CONFLICT (ma_ve) DO NOTHING;
INSERT INTO thanh_toan(ma_giao_dich,ma_don,ma_phuong_thuc,so_tien,noi_dung_thanh_toan,thoi_gian_thanh_toan,trang_thai)
SELECT 'T06_PAY_'||n,'T06_O_'||n,'T06_PAYMENT',d.tong_tien,'Fixture, số tiền gốc; không phải số tiền hoàn',
LOCALTIMESTAMP,CASE n WHEN 3 THEN 'DA_HOAN_TIEN' WHEN 4 THEN 'HOAN_TIEN_MOT_PHAN' ELSE 'THANH_CONG' END
FROM t06_orders o JOIN don_dat_ve d ON d.ma_don='T06_O_'||o.n ON CONFLICT (ma_giao_dich) DO NOTHING;
INSERT INTO don_dat_ve(ma_don,ma_nguoi_dung,ngay_dat,tong_tien,tien_giam,khach_dat_ten,
khach_dat_so_dien_thoai,ghi_chu,kenh_dat,trang_thai)
SELECT 'T06_O_'||id,'T06_U_1',LOCALTIMESTAMP,280000,0,'Nguyễn Thanh Hà','T06-BOOKER-SCENARIO',note,'WEB',state
FROM (VALUES ('WAIT','CHO_THANH_TOAN','Chờ thanh toán'),('FAIL','CHO_THANH_TOAN','Thanh toán thất bại'),
('PROCESS','CHO_THANH_TOAN','Giao dịch đang xử lý'),('HOLD','CHO_THANH_TOAN','Có giữ ghế'),
('EXPIRED','DA_HUY','Giữ chỗ hết hạn; chưa có enum đơn hết hạn riêng'),('CANCEL','DA_HUY','Đơn đã hủy'))
d(id,state,note) ON CONFLICT (ma_don) DO NOTHING;
INSERT INTO thanh_toan(ma_giao_dich,ma_don,ma_phuong_thuc,so_tien,noi_dung_thanh_toan,thoi_gian_thanh_toan,trang_thai)
SELECT 'T06_PAY_'||id,'T06_O_'||id,'T06_PAYMENT',280000,'Giao dịch kiểm thử',
CASE WHEN id='FAIL' THEN LOCALTIMESTAMP END,state
FROM (VALUES ('FAIL','THAT_BAI'),('PROCESS','CHO_XU_LY')) p(id,state) ON CONFLICT (ma_giao_dich) DO NOTHING;
INSERT INTO giu_cho(ma_giu_cho,ma_chuyen,ma_ghe,ma_don,ngay_dat,thoi_diem_bat_dau,thoi_diem_het_han,trang_thai)
SELECT 'T06_H_'||id,'T06_C_2_20','T06_X_2_G'||lpad(seat::text,3,'0'),'T06_O_'||order_id,
LOCALTIMESTAMP-make_interval(mins=>minutes),LOCALTIMESTAMP-make_interval(mins=>minutes),
LOCALTIMESTAMP+make_interval(mins=>15-minutes),state
FROM (VALUES ('ACTIVE',10,'HOLD',1,'DANG_GIU'),('EXPIRED',11,'EXPIRED',16,'HET_HAN'),
('BOUNDARY',12,'HOLD',15,'DANG_GIU'),('CANCEL',13,'CANCEL',2,'DA_HUY')) h(id,seat,order_id,minutes,state)
ON CONFLICT (ma_giu_cho) DO NOTHING;
INSERT INTO danh_gia(ma_danh_gia,ma_nguoi_dung,ma_chuyen,so_sao,binh_luan,ngay_danh_gia,trang_thai) VALUES
('T06_REVIEW','T06_U_5','T06_C_DONE',4,'Xe sạch, khởi hành đúng giờ, nhân viên hỗ trợ tốt.',LOCALTIMESTAMP,'DA_DUYET')
ON CONFLICT (ma_danh_gia) DO NOTHING;
COMMIT;
