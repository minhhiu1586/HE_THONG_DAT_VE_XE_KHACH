-- Retire the removed inspection feature without deleting historical staff/ticket data.
DO $$
BEGIN
    DELETE FROM phien_dang_nhap s USING nguoi_dung u, nhan_vien n
    WHERE s.ma_nguoi_dung=u.ma_nguoi_dung AND u.ma_nhan_vien=n.ma_nhan_vien
        AND u.loai_tai_khoan='NHAN_VIEN' AND n.loai_nhan_vien='KIEM_VE';
    UPDATE nguoi_dung u SET trang_thai='KHOA' FROM nhan_vien n
    WHERE u.ma_nhan_vien=n.ma_nhan_vien AND u.loai_tai_khoan='NHAN_VIEN'
        AND n.loai_nhan_vien='KIEM_VE';
    UPDATE nhan_vien SET trang_thai='DA_NGHI_VIEC' WHERE loai_nhan_vien='KIEM_VE';
END;
$$;
