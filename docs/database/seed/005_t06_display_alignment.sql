-- Normalize owned sample display fields; preserve IDs, schedules and business states.
BEGIN;
UPDATE nha_xe SET ten_nha_xe=CASE ma_nha_xe
    WHEN 'T06_NX_A' THEN 'Phương Trang - FUTA Bus Lines'
    ELSE 'Thành Bưởi'
END, mo_ta='Dữ liệu mẫu phục vụ đồ án' WHERE ma_nha_xe IN ('T06_NX_A','T06_NX_B');
UPDATE ben_xe b SET ten_ben_xe=v.name,dia_chi=v.address FROM (VALUES
('T06_BX_HCM_E','Bến xe Miền Đông mới','501 Hoàng Hữu Nam, TP. Hồ Chí Minh','DEMO_HCM','HOAT_DONG'),
('T06_BX_HCM_W','Bến xe Miền Tây','395 Kinh Dương Vương, TP. Hồ Chí Minh','DEMO_HCM','HOAT_DONG'),
('T06_BX_LD','Bến xe Liên tỉnh Đà Lạt','1 Tô Hiến Thành, Đà Lạt, Lâm Đồng','DEMO_LD','HOAT_DONG'),
('T06_BX_CT','Bến xe Trung tâm Cần Thơ','Quốc lộ 1, khu đô thị Nam Cần Thơ, Cần Thơ','DEMO_CT','HOAT_DONG'),
('T06_BX_KH','Bến xe Phía Nam Nha Trang','Km 6 đường 23 Tháng 10, Khánh Hòa','DEMO_KH','HOAT_DONG'),
('T06_BX_DN','Bến xe Trung tâm Đà Nẵng','185 Tôn Đức Thắng, Đà Nẵng','T06_DN','HOAT_DONG'),
('T06_BX_HN','Bến xe Nước Ngầm','1 Ngọc Hồi, Hà Nội','T06_HN','HOAT_DONG')
) v(id,name,address,city,state) WHERE b.ma_ben_xe=v.id;
UPDATE xe SET bien_so='51B-'||lpad((900+substring(ma_xe from 7)::int)::text,3,'0')||'.'||lpad((substring(ma_xe from 7)::int%100)::text,2,'0') WHERE ma_xe ~ '^T06_X_[0-9]+$';
UPDATE xe SET bien_so=CASE ma_xe WHEN 'T06_X_BAOTRI' THEN '51B-998.98' ELSE '51B-999.99' END WHERE ma_xe IN ('T06_X_BAOTRI','T06_X_HETHAN');
UPDATE ghe g SET vi_tri=CASE x.so_cho_ngoi WHEN 22 THEN 'P'||lpad(substring(g.ma_ghe from '[0-9]+$')::int::text,2,'0') WHEN 29 THEN lpad(substring(g.ma_ghe from '[0-9]+$')::int::text,2,'0') ELSE CASE WHEN substring(g.ma_ghe from '[0-9]+$')::int<=20 THEN 'A' ELSE 'B' END||lpad(((substring(g.ma_ghe from '[0-9]+$')::int-1)%20+1)::text,2,'0') END FROM xe x WHERE x.ma_xe=g.ma_xe AND (x.ma_xe ~ '^T06_X_[0-9]+$' OR x.ma_xe IN ('T06_X_BAOTRI','T06_X_HETHAN'));
UPDATE nhan_vien SET ho_ten=(ARRAY[
    'Nguyễn Văn An','Trần Minh Đức','Lê Hoàng Nam',
    'Phạm Thanh Bình','Võ Minh Tuấn','Đặng Quốc Huy'
])[(substring(ma_nhan_vien from '[0-9]+$')::int-1)%6+1]||' '||substring(ma_nhan_vien from 5),
so_dien_thoai='09'||CASE WHEN loai_nhan_vien='TAI_XE' THEN '1' ELSE '2' END
    ||lpad((8000000+substring(ma_nhan_vien from '[0-9]+$')::int)::text,7,'0')
WHERE ma_nhan_vien ~ '^T06_(TX|PX)_[0-9]+$';
UPDATE diem_dung SET ten_diem_dung=replace(ten_diem_dung,'Điểm dừng mô phỏng','Điểm nghỉ tuyến')
WHERE ma_diem_dung ~ '^T06_D_[0-9]+_[0-9]+$';
-- Preserve legacy keys used by tests; normalize only presentation fields.
UPDATE nha_xe SET ten_nha_xe=CASE ma_nha_xe WHEN 'DEMO_NX' THEN 'Mai Linh Express' ELSE 'Kumho Samco' END,
mo_ta='Dữ liệu mẫu phục vụ đồ án'
WHERE ma_nha_xe IN ('DEMO_NX','DEMO_NX_MORE');
UPDATE ben_xe b SET ten_ben_xe=v.name,dia_chi=v.address FROM (VALUES
('DEMO_BX1','Bến xe Miền Đông mới','501 Hoàng Hữu Nam, TP. Hồ Chí Minh'),
('DEMO_BX2','Bến xe Liên tỉnh Đà Lạt','1 Tô Hiến Thành, Đà Lạt, Lâm Đồng'),
('DEMO_CT_BX','Bến xe Trung tâm Cần Thơ','Quốc lộ 1, khu đô thị Nam Cần Thơ, Cần Thơ'),
('DEMO_KH_BX','Bến xe Phía Nam Nha Trang','Km 6 đường 23 Tháng 10, Khánh Hòa')
) v(id,name,address) WHERE b.ma_ben_xe=v.id;
UPDATE tuyen_xe SET ten_tuyen='TP. Hồ Chí Minh - Đà Lạt' WHERE ma_tuyen='DEMO_TUYEN';
UPDATE xe SET bien_so=CASE ma_xe WHEN 'DEMO_XE' THEN '51B-800.01'
WHEN 'DEMO_XE2' THEN '51B-800.02' WHEN 'DEMO_BAOTRI' THEN '51B-800.03'
ELSE '51B-800.04' END WHERE ma_xe IN ('DEMO_XE','DEMO_XE2','DEMO_BAOTRI','DEMO_HETHAN');
UPDATE xe SET bien_so='51B-801.'||lpad(substring(ma_xe from '[0-9]+$'),2,'0')
WHERE ma_xe ~ '^DEMO_FLEET_[1-6]$';
UPDATE ghe g SET vi_tri=CASE x.so_cho_ngoi WHEN 22 THEN 'P'||lpad(v.n::text,2,'0')
WHEN 29 THEN lpad(v.n::text,2,'0') ELSE CASE WHEN v.n<=20 THEN 'A' ELSE 'B' END
||lpad(((v.n-1)%20+1)::text,2,'0') END
FROM xe x, (SELECT ma_ghe,substring(ma_ghe from '[0-9]+$')::int n FROM ghe
WHERE ma_ghe ~ '^DEMO_(G[0-9]+|G2_[0-9]+|BAOTRI_G[0-9]+|HETHAN_G[0-9]+|FLEET_[1-6]_G[0-9]+)$') v
WHERE g.ma_ghe=v.ma_ghe AND x.ma_xe=g.ma_xe;
UPDATE nhan_vien SET ho_ten=CASE ma_nhan_vien
WHEN 'DEMO_TX' THEN 'Nguyễn Văn Hùng' WHEN 'DEMO_PX' THEN 'Trần Thanh Hải'
WHEN 'DEMO_TX2' THEN 'Lê Minh Quân' WHEN 'DEMO_TXHH' THEN 'Phạm Quốc Bảo'
ELSE 'Võ Thanh Tùng' END
WHERE ma_nhan_vien IN ('DEMO_TX','DEMO_PX','DEMO_TX2','DEMO_TXHH','DEMO_TXNGHI');
UPDATE nhan_vien SET ho_ten=(ARRAY['Nguyễn Văn Sơn','Trần Minh Tâm','Lê Thanh Phong',
'Phạm Quốc Hưng','Võ Hoàng Long','Đặng Minh Hải'])[(substring(ma_nhan_vien from '[0-9]+$')::int-1)%6+1],
so_dien_thoai='093'||lpad((8100000+substring(ma_nhan_vien from '[0-9]+$')::int)::text,7,'0')
WHERE ma_nhan_vien ~ '^DEMO_STAFF_([1-9]|1[0-2])$';
UPDATE nguoi_dung SET ho_ten=(ARRAY['Nguyễn Thanh Hà','Trần Ngọc Mai','Lê Minh Anh',
'Phạm Hoàng Nam','Võ Thu Trang'])[(substring(ma_nguoi_dung from '[0-9]+$')::int-1)%5+1],
so_dien_thoai='094'||lpad((8200000+substring(ma_nguoi_dung from '[0-9]+$')::int)::text,7,'0')
WHERE ma_nguoi_dung ~ '^T06_U_([1-9]|1[0-9]|20)$';
UPDATE don_dat_ve d SET khach_dat_ten=u.ho_ten,khach_dat_so_dien_thoai=u.so_dien_thoai,
ghi_chu=replace(d.ghi_chu,'Đơn demo đã thanh toán','Đơn đã thanh toán')
FROM nguoi_dung u WHERE d.ma_nguoi_dung=u.ma_nguoi_dung
AND d.ma_don ~ '^T06_O_([0-9]+|WAIT|FAIL|PROCESS|HOLD|EXPIRED|CANCEL)$';
UPDATE ve_xe v SET ho_ten_khach_hang=d.khach_dat_ten,
so_dien_thoai_khach_hang=d.khach_dat_so_dien_thoai
FROM don_dat_ve d WHERE d.ma_don=v.ma_don AND v.ma_ve ~ '^T06_V_[0-9]+_[0-9]+$';
UPDATE phan_cong_chuyen_xe SET ghi_chu='Phân công cùng nhà xe'
WHERE ma_phan_cong ~ '^T06_PC_[0-9]+_[0-9]+_(TX|PX)$';
UPDATE phan_cong_chuyen_xe SET ghi_chu='Mỗi nhân viên một chuyến mỗi ngày'
WHERE ma_phan_cong ~ '^DEMO_ASSIGN_[1-6]_[1-7]_[1-2]$';
UPDATE phuong_thuc_thanh_toan SET ten_phuong_thuc='Thanh toán kiểm thử'
WHERE ma_phuong_thuc='T06_PAYMENT';
UPDATE thanh_toan SET noi_dung_thanh_toan='Giao dịch kiểm thử'
WHERE ma_giao_dich IN ('T06_PAY_FAIL','T06_PAY_PROCESS');
UPDATE danh_gia SET binh_luan='Xe sạch, khởi hành đúng giờ, nhân viên hỗ trợ tốt.'
WHERE ma_danh_gia='T06_REVIEW';
UPDATE nhan_vien SET so_bang_lai=CASE ma_nhan_vien
WHEN 'DEMO_TX' THEN '123456789001' WHEN 'DEMO_TX2' THEN '123456789002'
WHEN 'DEMO_TXHH' THEN '123456789003' ELSE '123456789004' END
WHERE ma_nhan_vien IN ('DEMO_TX','DEMO_TX2','DEMO_TXHH','DEMO_TXNGHI');
COMMIT;
