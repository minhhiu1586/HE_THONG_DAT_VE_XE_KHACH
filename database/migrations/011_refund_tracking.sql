CREATE TABLE IF NOT EXISTS payment_gateway_metadata (
  ma_giao_dich varchar(80) PRIMARY KEY REFERENCES thanh_toan(ma_giao_dich),
  merchant_code varchar(32) NOT NULL,
  request_created_at timestamptz NOT NULL,
  provider_transaction_no varchar(80)
);

CREATE TABLE IF NOT EXISTS refund_request (
  ma_hoan_tien varchar(40) PRIMARY KEY,
  ma_don varchar(40) NOT NULL REFERENCES don_dat_ve(ma_don),
  ma_giao_dich_goc varchar(80) NOT NULL REFERENCES thanh_toan(ma_giao_dich),
  ma_nguoi_yeu_cau varchar(40) NOT NULL REFERENCES nguoi_dung(ma_nguoi_dung),
  ma_chinh_sach varchar(40) NOT NULL REFERENCES chinh_sach_hoan_ve(ma_chinh_sach),
  ly_do varchar(500),
  so_tien_hoan numeric(14,0) NOT NULL CHECK (so_tien_hoan >= 0),
  trang_thai varchar(24) NOT NULL CHECK (trang_thai IN ('CHO_XU_LY','THANH_CONG','THAT_BAI','CAN_DOI_SOAT')),
  chi_tiet jsonb NOT NULL,
  lich_su jsonb NOT NULL DEFAULT '[]'::jsonb,
  ma_request_cong varchar(32) NOT NULL,
  ma_giao_dich_cong varchar(80),
  loai_hoan_toan_phan boolean NOT NULL,
  ly_do_loi varchar(120),
  created_at timestamptz NOT NULL,
  confirmed_at timestamptz
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_refund_pending_payment
  ON refund_request(ma_giao_dich_goc) WHERE trang_thai IN ('CHO_XU_LY','CAN_DOI_SOAT');
CREATE INDEX IF NOT EXISTS ix_refund_order ON refund_request(ma_don,created_at);
CREATE INDEX IF NOT EXISTS ix_refund_ticket_details ON refund_request USING gin(chi_tiet);
