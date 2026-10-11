-- Fixture integrity only; does not test booking/payment/authentication services.
DO $$ BEGIN
IF (SELECT count(*) FROM nguoi_dung WHERE left(ma_nguoi_dung,6)='T06_U_')<>25
OR (SELECT count(*) FROM don_dat_ve WHERE left(ma_don,6)='T06_O_')<>66
OR (SELECT count(*) FROM ve_xe WHERE left(ma_ve,6)='T06_V_')<>119
OR (SELECT count(*) FROM thanh_toan WHERE left(ma_giao_dich,8)='T06_PAY_')<>62
OR (SELECT count(*) FROM giu_cho WHERE left(ma_giu_cho,6)='T06_H_')<>4 THEN
RAISE EXCEPTION 'Scenario count mismatch'; END IF;
IF EXISTS(SELECT 1 FROM nguoi_dung WHERE left(ma_nguoi_dung,6)='T06_U_'
AND (trang_thai<>'KHOA' OR mat_khau<>'!T06_DISABLED_NO_AUTH!')) THEN
RAISE EXCEPTION 'Fixture accounts must remain disabled'; END IF;
IF EXISTS(SELECT 1 FROM ve_xe v JOIN ghe g USING(ma_ghe) JOIN chuyen_xe c USING(ma_chuyen)
WHERE left(v.ma_ve,6)='T06_V_' AND (g.ma_xe<>c.ma_xe OR g.trang_thai<>'HOAT_DONG')) THEN
RAISE EXCEPTION 'Ticket does not match usable vehicle seat'; END IF;
IF EXISTS(SELECT ma_don FROM ve_xe WHERE left(ma_ve,6)='T06_V_' GROUP BY ma_don HAVING count(DISTINCT ma_chuyen)>1)
OR EXISTS(SELECT 1 FROM don_dat_ve d JOIN (SELECT ma_don,sum(gia_ve) amount FROM ve_xe
WHERE left(ma_ve,6)='T06_V_' GROUP BY ma_don) v USING(ma_don) WHERE d.tong_tien<>v.amount) THEN
RAISE EXCEPTION 'Order trip/amount mismatch'; END IF;
IF NOT EXISTS(SELECT 1 FROM ve_xe WHERE ma_don='T06_O_4' AND trang_thai='DA_XUAT_VE')
OR NOT EXISTS(SELECT 1 FROM ve_xe WHERE ma_don='T06_O_4' AND trang_thai='DA_HOAN') THEN
RAISE EXCEPTION 'Partial refund fixture missing'; END IF;
IF EXISTS(SELECT 1 FROM giu_cho WHERE left(ma_giu_cho,6)='T06_H_'
AND thoi_diem_het_han-thoi_diem_bat_dau<>INTERVAL '15 minutes') THEN
RAISE EXCEPTION 'Hold duration mismatch'; END IF;
IF EXISTS(SELECT 1 FROM giu_cho h JOIN ve_xe v USING(ma_chuyen,ma_ghe)
WHERE left(h.ma_giu_cho,6)='T06_H_' AND h.trang_thai='DANG_GIU'
AND v.trang_thai IN('DA_XUAT_VE','DA_LEN_XE')) THEN
RAISE EXCEPTION 'Active hold conflicts with ticket'; END IF;
IF EXISTS(SELECT 1 FROM khuyen_mai WHERE left(ma_khuyen_mai,7)='T06_KM_' AND so_luong_da_dung>so_luong)
OR NOT EXISTS(SELECT 1 FROM khuyen_mai WHERE ma_khuyen_mai='T06_KM_LAST' AND so_luong-so_luong_da_dung=1) THEN
RAISE EXCEPTION 'Promotion quota fixture mismatch'; END IF;
IF EXISTS(SELECT 1 FROM chinh_sach_hoan_ve WHERE left(ma_chinh_sach,7)='T06_CS_' AND trang_thai<>'TAM_NGUNG') THEN
RAISE EXCEPTION 'Unapproved fixture policies must remain inactive'; END IF;
BEGIN
INSERT INTO ve_xe SELECT 'T06_CHECK_DUPLICATE',ma_chuyen,ma_ghe,ma_don,ho_ten_khach_hang,
so_dien_thoai_khach_hang,cccd_hanh_khach,gia_ve,ngay_xuat_ve,trang_thai FROM ve_xe WHERE ma_ve='T06_V_1_1';
RAISE EXCEPTION 'Duplicate seat/trip ticket was accepted';
EXCEPTION WHEN unique_violation THEN NULL;
END;
END $$;
SELECT 'PASS: T06 scenario fixtures and duplicate-ticket constraint' AS result;
