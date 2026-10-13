-- Danh mục khởi tạo dự án; nạp lại không ghi đè thông tin đã cập nhật.
BEGIN;
SET LOCAL TIME ZONE 'Asia/Ho_Chi_Minh';
INSERT INTO tinh_thanh(ma_tinh_thanh,ten_tinh_thanh,khu_vuc) VALUES
('HCM','TP. Hồ Chí Minh','Miền Nam'),('LD','Lâm Đồng','Tây Nguyên'),
('KH','Khánh Hòa','Nam Trung Bộ'),('CT','Cần Thơ','Tây Nam Bộ'),
('HN','Hà Nội','Miền Bắc'),('DN','Đà Nẵng','Miền Trung'),('NB','Ninh Bình','Miền Bắc')
ON CONFLICT(ma_tinh_thanh) DO NOTHING;

INSERT INTO ben_xe(ma_ben_xe,ten_ben_xe,dia_chi,ma_tinh_thanh,trang_thai) VALUES
('BX_MD_MOI','Bến xe Miền Đông mới','501 Hoàng Hữu Nam, TP. Hồ Chí Minh','HCM','HOAT_DONG'),
('BX_MT','Bến xe Miền Tây','395 Kinh Dương Vương, TP. Hồ Chí Minh','HCM','HOAT_DONG'),
('BX_DL','Bến xe Liên tỉnh Đà Lạt','1 Tô Hiến Thành, Đà Lạt, Lâm Đồng','LD','HOAT_DONG'),
('BX_NT','Bến xe Phía Nam Nha Trang','Đường 23 Tháng 10, Khánh Hòa','KH','HOAT_DONG'),
('BX_CT','Bến xe Trung tâm Cần Thơ','Quốc lộ 1, Cần Thơ','CT','HOAT_DONG'),
('BX_NN','Bến xe Nước Ngầm','1 Ngọc Hồi, Hà Nội','HN','HOAT_DONG'),
('BX_DN','Bến xe Trung tâm Đà Nẵng','185 Tôn Đức Thắng, Đà Nẵng','DN','HOAT_DONG'),
('BX_VT','Bến xe Vũng Tàu','Vũng Tàu, TP. Hồ Chí Minh','HCM','HOAT_DONG'),
('BX_PR','Bến xe Phan Rang','Phan Rang, Khánh Hòa','KH','HOAT_DONG'),
('BX_NB','Bến xe Ninh Bình','Ninh Bình','NB','HOAT_DONG')
ON CONFLICT(ma_ben_xe) DO NOTHING;

INSERT INTO nha_xe(ma_nha_xe,ten_nha_xe,hotline,dia_chi,mo_ta,trang_thai) VALUES
('NX_FUTA','Phương Trang – FUTA Bus Lines','19006067','TP. Hồ Chí Minh','Đơn vị vận tải trong hệ thống đặt vé','HOAT_DONG'),
('NX_TB','Thành Bưởi','19006079','TP. Hồ Chí Minh','Đơn vị vận tải trong hệ thống đặt vé','HOAT_DONG'),
('NX_KUMHO','Kumho Samco','19006065','TP. Hồ Chí Minh','Đơn vị vận tải trong hệ thống đặt vé','HOAT_DONG'),
('NX_HL','Hoàng Long','02253920920','Hải Phòng','Đơn vị vận tải trong hệ thống đặt vé','HOAT_DONG'),
('NX_CT','Cúc Tùng','19006606','Khánh Hòa','Đơn vị vận tải trong hệ thống đặt vé','HOAT_DONG')
ON CONFLICT(ma_nha_xe) DO NOTHING;

UPDATE nha_xe n SET logo_url=v.image
FROM (VALUES ('NX_FUTA','/operators/futa.jpg'),('NX_TB','/operators/thanh-buoi.jpg'),
('NX_KUMHO','/operators/kumho.png'),('NX_HL','/operators/hoang-long.png'),
('NX_CT','/operators/cuc-tung.png')) v(id,image)
WHERE n.ma_nha_xe=v.id AND n.logo_url IS NULL;

INSERT INTO loai_xe(ma_loai_xe,ten_loai_xe,so_cho_ngoi,so_tang,mo_ta) VALUES
('LX_22','Limousine phòng 22 chỗ',22,2,'22 vị trí P01–P22, chia hai tầng'),
('LX_29','Xe ghế ngồi 29 chỗ',29,1,'29 vị trí G01–G29, một tầng'),
('LX_40','Xe giường nằm 40 chỗ',40,2,'Tầng dưới A01–A20; tầng trên B01–B20')
ON CONFLICT(ma_loai_xe) DO NOTHING;

INSERT INTO phuong_thuc_thanh_toan(ma_phuong_thuc,ten_phuong_thuc,mo_ta,trang_thai)
VALUES('PTTT_VNPAY','VNPAY','Danh mục phương thức thanh toán; không tạo giao dịch đã thanh toán','HOAT_DONG')
ON CONFLICT(ma_phuong_thuc) DO NOTHING;
COMMIT;
