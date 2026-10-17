-- T18: additive payment alignment. The QR token already exists in the approved class diagram.
-- Keep existing ticket rows and generate opaque values only where the legacy row is missing one.
ALTER TABLE ve_xe ADD COLUMN IF NOT EXISTS ma_qr varchar(80);

ALTER TABLE ve_xe ALTER COLUMN ma_qr SET DEFAULT gen_random_uuid()::text;

UPDATE ve_xe
SET ma_qr = gen_random_uuid()::text
WHERE ma_qr IS NULL;

ALTER TABLE ve_xe ALTER COLUMN ma_qr SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_ve_xe_ma_qr ON ve_xe (ma_qr);

INSERT INTO phuong_thuc_thanh_toan(ma_phuong_thuc, ten_phuong_thuc, mo_ta, trang_thai)
VALUES ('VNPAY', 'VNPay', 'Thanh toán sandbox VNPay cho đồ án', 'HOAT_DONG'),
       ('MIEN_PHI', 'Miễn phí', 'Xác nhận nội bộ cho đơn có tổng thanh toán bằng 0', 'HOAT_DONG')
ON CONFLICT (ma_phuong_thuc) DO NOTHING;
