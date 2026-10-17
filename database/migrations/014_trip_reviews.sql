-- T22: reuse DanhGia and the existing account-trip unique constraint.
ALTER TABLE danh_gia ADD COLUMN IF NOT EXISTS da_xoa boolean NOT NULL DEFAULT false;
CREATE INDEX IF NOT EXISTS ix_danh_gia_public ON danh_gia(ma_chuyen,ngay_danh_gia DESC)
  WHERE trang_thai='DA_DUYET' AND da_xoa=false;
CREATE TABLE IF NOT EXISTS review_history (
  id varchar(40) PRIMARY KEY,
  ma_danh_gia varchar(40) NOT NULL REFERENCES danh_gia(ma_danh_gia),
  ma_nguoi_thao_tac varchar(40) NOT NULL REFERENCES nguoi_dung(ma_nguoi_dung),
  thao_tac varchar(24) NOT NULL,
  trang_thai_cu varchar(30),
  trang_thai_moi varchar(30) NOT NULL,
  so_sao_cu integer,
  so_sao_moi integer NOT NULL,
  binh_luan_cu varchar(2000),
  binh_luan_moi varchar(2000),
  ly_do varchar(1000),
  created_at timestamptz NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_review_history_review ON review_history(ma_danh_gia,created_at);
