-- Kiểm vé chung thuộc hệ thống; các vai trò nhân sự khác vẫn phải thuộc nhà xe.
ALTER TABLE nhan_vien ALTER COLUMN ma_nha_xe DROP NOT NULL;
ALTER TABLE nhan_vien ADD CONSTRAINT ck_nhan_vien_scope
    CHECK (ma_nha_xe IS NOT NULL OR loai_nhan_vien = 'KIEM_VE');

-- Chuyển hai hồ sơ vừa bổ sung sang phạm vi hệ thống, giữ ID và mật khẩu.
UPDATE nhan_vien SET ma_nha_xe=NULL,
    email=CASE ma_nhan_vien WHEN 'NV_FUTA_KV01' THEN 'kiemve1@datvexe.test' ELSE 'kiemve2@datvexe.test' END
WHERE ma_nhan_vien IN ('NV_FUTA_KV01','NV_FUTA_KV02') AND loai_nhan_vien='KIEM_VE'
    AND email IN ('kiemve1.futa@datvexe.test','kiemve2.futa@datvexe.test');
UPDATE nguoi_dung SET email=n.email,ma_nha_xe=NULL
FROM nhan_vien n WHERE nguoi_dung.ma_nhan_vien=n.ma_nhan_vien
    AND n.ma_nhan_vien IN ('NV_FUTA_KV01','NV_FUTA_KV02') AND n.loai_nhan_vien='KIEM_VE'
    AND nguoi_dung.email IN ('kiemve1.futa@datvexe.test','kiemve2.futa@datvexe.test');

-- Chuyển lịch khởi tạo của người thứ hai sang Thành Bưởi; giữ phân công thủ công.
DELETE FROM phan_cong_chuyen_xe
WHERE ma_nhan_vien='NV_FUTA_KV02' AND vai_tro='KIEM_VE'
    AND ma_phan_cong ~ '^CX_FUTA_1_[0-9]{8}_[12]_KV2$'
    AND ghi_chu='Kiểm soát vé và xác nhận hành khách lên xe.';
