BEGIN;
-- Upgrade only the earlier 16-seat demo fleet; preserve IDs and existing seats.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM loai_xe WHERE ma_loai_xe='DEMO_LX' AND so_cho_ngoi=16) THEN
        IF EXISTS (SELECT 1 FROM xe WHERE ma_loai_xe='DEMO_LX'
                   AND (ma_xe NOT IN ('DEMO_XE','DEMO_BAOTRI','DEMO_HETHAN') OR so_cho_ngoi<>16))
           OR EXISTS (SELECT 1 FROM ghe g JOIN ve_xe v ON v.ma_ghe=g.ma_ghe
                      WHERE g.ma_xe IN ('DEMO_XE','DEMO_BAOTRI','DEMO_HETHAN'))
           OR EXISTS (SELECT 1 FROM ghe g JOIN giu_cho h ON h.ma_ghe=g.ma_ghe
                      WHERE g.ma_xe IN ('DEMO_XE','DEMO_BAOTRI','DEMO_HETHAN')) THEN
            RAISE EXCEPTION 'Old demo fleet has external use or ticket/hold history; review before resizing';
        END IF;
        UPDATE loai_xe SET ten_loai_xe='Xe khách ghế ngồi 29 chỗ',so_cho_ngoi=29
        WHERE ma_loai_xe='DEMO_LX' AND so_cho_ngoi=16;
        UPDATE xe SET so_cho_ngoi=29 WHERE ma_loai_xe='DEMO_LX'
        AND ma_xe IN ('DEMO_XE','DEMO_BAOTRI','DEMO_HETHAN') AND so_cho_ngoi=16;
    END IF;
END $$;
INSERT INTO nha_xe(ma_nha_xe,ten_nha_xe,trang_thai) VALUES ('DEMO_NX','Nhà xe demo','HOAT_DONG') ON CONFLICT DO NOTHING;
INSERT INTO loai_xe(ma_loai_xe,ten_loai_xe,so_cho_ngoi,so_tang) VALUES ('DEMO_LX','Xe khách ghế ngồi 29 chỗ',29,1) ON CONFLICT DO NOTHING;
INSERT INTO xe(ma_xe,bien_so,ma_loai_xe,so_cho_ngoi,ma_nha_xe,nam_san_xuat,ngay_dang_kiem,han_dang_kiem,trang_thai)
VALUES ('DEMO_XE','DEMO-51B-001','DEMO_LX',29,'DEMO_NX',2024,CURRENT_DATE-30,CURRENT_DATE+365,'SAN_SANG') ON CONFLICT DO NOTHING;
INSERT INTO ghe(ma_ghe,vi_tri,tang,trang_thai,ma_xe)
SELECT 'DEMO_G'||i,'G'||lpad(i::text,3,'0'),1,'HOAT_DONG','DEMO_XE' FROM generate_series(1,29) i ON CONFLICT DO NOTHING;
INSERT INTO nhan_vien(ma_nhan_vien,ho_ten,so_dien_thoai,email,so_bang_lai,ngay_het_han_bang_lai,loai_nhan_vien,trang_thai,ma_nha_xe)
VALUES ('DEMO_TX','Tài xế demo','0900000001','driver@example.test','DEMO-LICENSE-01',CURRENT_DATE+365,'TAI_XE','DANG_LAM_VIEC','DEMO_NX'),
('DEMO_PX','Phụ xe demo','0900000002','assistant@example.test',NULL,NULL,'PHU_XE','DANG_LAM_VIEC','DEMO_NX') ON CONFLICT DO NOTHING;
INSERT INTO tinh_thanh(ma_tinh_thanh,ten_tinh_thanh) VALUES ('DEMO_HCM','TP. Hồ Chí Minh'),('DEMO_LD','Lâm Đồng') ON CONFLICT DO NOTHING;
INSERT INTO ben_xe(ma_ben_xe,ten_ben_xe,dia_chi,ma_tinh_thanh,trang_thai)
VALUES ('DEMO_BX1','Bến demo HCM','TP. Hồ Chí Minh','DEMO_HCM','HOAT_DONG'),('DEMO_BX2','Bến demo Đà Lạt','Đà Lạt','DEMO_LD','HOAT_DONG') ON CONFLICT DO NOTHING;
INSERT INTO tuyen_xe(ma_tuyen,ten_tuyen,tram_khoi_hanh,tram_den,thoi_gian_khoi_hanh,thoi_gian_du_kien,gia_co_ban,trang_thai,ma_nha_xe)
VALUES ('DEMO_TUYEN','HCM - Đà Lạt demo','DEMO_BX1','DEMO_BX2','08:00',6,250000,'DANG_KHAI_THAC','DEMO_NX') ON CONFLICT DO NOTHING;
INSERT INTO chuyen_xe(ma_chuyen,ma_tuyen,ma_xe,ngay_khoi_hanh,gio_khoi_hanh,gio_den_du_kien,trang_thai)
VALUES ('DEMO_CHUYEN','DEMO_TUYEN','DEMO_XE',CURRENT_DATE+7,'08:00',(CURRENT_DATE+7)+TIME '14:00','CHUA_KHOI_HANH') ON CONFLICT DO NOTHING;
INSERT INTO phan_cong_chuyen_xe(ma_phan_cong,ma_chuyen,ma_nhan_vien,vai_tro)
VALUES ('DEMO_PC1','DEMO_CHUYEN','DEMO_TX','TAI_XE_CHINH'),('DEMO_PC2','DEMO_CHUYEN','DEMO_PX','PHU_XE') ON CONFLICT DO NOTHING;
INSERT INTO loai_xe(ma_loai_xe,ten_loai_xe,so_cho_ngoi,so_tang) VALUES ('DEMO_LX2','Giường nằm hai tầng',40,2) ON CONFLICT DO NOTHING;
INSERT INTO xe(ma_xe,bien_so,ma_loai_xe,so_cho_ngoi,ma_nha_xe,nam_san_xuat,ngay_dang_kiem,han_dang_kiem,trang_thai,dung_tich_xang,toc_do_gio)
VALUES ('DEMO_XE2','DEMO-51B-002','DEMO_LX2',40,'DEMO_NX',2024,CURRENT_DATE-30,CURRENT_DATE+365,'SAN_SANG',150,60),
('DEMO_BAOTRI','DEMO-51B-003','DEMO_LX',29,'DEMO_NX',2023,CURRENT_DATE-30,CURRENT_DATE+365,'BAO_TRI',100,50),
('DEMO_HETHAN','DEMO-51B-004','DEMO_LX',29,'DEMO_NX',2022,CURRENT_DATE-365,CURRENT_DATE-1,'SAN_SANG',100,50) ON CONFLICT DO NOTHING;
INSERT INTO ghe(ma_ghe,vi_tri,tang,trang_thai,ma_xe)
SELECT 'DEMO_G2_'||i,'G'||lpad(i::text,3,'0'),CASE WHEN i<=20 THEN 1 ELSE 2 END,'HOAT_DONG','DEMO_XE2' FROM generate_series(1,40) i ON CONFLICT DO NOTHING;
INSERT INTO ghe(ma_ghe,vi_tri,tang,trang_thai,ma_xe)
SELECT x.id||'_G'||i,'G'||lpad(i::text,3,'0'),1,'HOAT_DONG',x.id FROM (VALUES ('DEMO_BAOTRI'),('DEMO_HETHAN')) x(id) CROSS JOIN generate_series(1,29) i ON CONFLICT DO NOTHING;
INSERT INTO nhan_vien(ma_nhan_vien,ho_ten,so_dien_thoai,email,so_bang_lai,ngay_het_han_bang_lai,loai_nhan_vien,trang_thai,ma_nha_xe)
VALUES ('DEMO_TX2','Tài xế phụ demo','0900000003','driver2@example.test','DEMO-LICENSE-02',CURRENT_DATE+365,'TAI_XE','DANG_LAM_VIEC','DEMO_NX'),
('DEMO_TXHH','Tài xế hết hạn bằng','0900000004','expired@example.test','DEMO-LICENSE-03',CURRENT_DATE-1,'TAI_XE','DANG_LAM_VIEC','DEMO_NX'),
('DEMO_TXNGHI','Tài xế tạm nghỉ','0900000005','leave@example.test','DEMO-LICENSE-04',CURRENT_DATE+365,'TAI_XE','TAM_NGHI','DEMO_NX') ON CONFLICT DO NOTHING;
INSERT INTO chuyen_xe(ma_chuyen,ma_tuyen,ma_xe,ngay_khoi_hanh,gio_khoi_hanh,gio_den_du_kien,trang_thai)
VALUES ('DEMO_TRUNG','DEMO_TUYEN',NULL,CURRENT_DATE+7,'09:00',(CURRENT_DATE+7)+TIME '15:00','CHUA_KHOI_HANH'),
('DEMO_SAU','DEMO_TUYEN','DEMO_XE2',CURRENT_DATE+8,'08:00',(CURRENT_DATE+8)+TIME '14:00','CHUA_KHOI_HANH'),
('DEMO_HUY','DEMO_TUYEN',NULL,CURRENT_DATE+9,'08:00',(CURRENT_DATE+9)+TIME '14:00','DA_HUY') ON CONFLICT DO NOTHING;
INSERT INTO phan_cong_chuyen_xe(ma_phan_cong,ma_chuyen,ma_nhan_vien,vai_tro,ghi_chu)
VALUES ('DEMO_PC3','DEMO_CHUYEN','DEMO_TX2','TAI_XE_PHU','Tài xế phụ theo Class Diagram') ON CONFLICT DO NOTHING;
COMMIT;
