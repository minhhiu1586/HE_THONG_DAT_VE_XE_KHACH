-- Chuẩn hóa mã kiểm vé chung, giữ thông tin hồ sơ, tài khoản và mật khẩu.
DO $$
DECLARE old_id text; new_id text;
BEGIN
    FOR old_id,new_id IN SELECT * FROM (VALUES
        ('NV_FUTA_KV01','NV_KV01'),('NV_FUTA_KV02','NV_KV02')) ids(old_id,new_id)
    LOOP
        IF EXISTS (SELECT 1 FROM nhan_vien WHERE ma_nhan_vien=old_id AND loai_nhan_vien='KIEM_VE')
           AND NOT EXISTS (SELECT 1 FROM nhan_vien WHERE ma_nhan_vien=new_id) THEN
            -- Tạm giải phóng khóa liên hệ duy nhất trong transaction để chuyển khóa chính.
            UPDATE nhan_vien SET email='~'||email,so_dien_thoai='~'||so_dien_thoai WHERE ma_nhan_vien=old_id;
            INSERT INTO nhan_vien(ma_nhan_vien,ho_ten,so_dien_thoai,email,ngay_sinh,
                so_bang_lai,ngay_het_han_bang_lai,loai_nhan_vien,trang_thai,ma_nha_xe)
            SELECT new_id,ho_ten,substring(so_dien_thoai FROM 2),substring(email FROM 2),ngay_sinh,
                so_bang_lai,ngay_het_han_bang_lai,loai_nhan_vien,trang_thai,NULL
            FROM nhan_vien WHERE ma_nhan_vien=old_id;
            UPDATE nguoi_dung SET ma_nhan_vien=new_id WHERE ma_nhan_vien=old_id;
            UPDATE phan_cong_chuyen_xe SET ma_nhan_vien=new_id WHERE ma_nhan_vien=old_id;
            UPDATE nhat_ky_kiem_ve SET ma_nhan_vien=new_id WHERE ma_nhan_vien=old_id;
            DELETE FROM nhan_vien WHERE ma_nhan_vien=old_id;
        END IF;
    END LOOP;
END;
$$;

-- Kiểm vé chung có quyền theo nghiệp vụ hệ thống, không dùng lịch nhân sự nhà xe.
DELETE FROM phan_cong_chuyen_xe WHERE ma_nhan_vien IN ('NV_KV01','NV_KV02') AND vai_tro='KIEM_VE';
