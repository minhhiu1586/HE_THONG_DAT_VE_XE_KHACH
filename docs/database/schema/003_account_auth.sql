-- T07 / T07.1: additive migration; no demo passwords or privileged accounts.
BEGIN;
-- Normalize old identifiers only if safe; stop for manual review on collisions.
DO $$ BEGIN
  IF EXISTS (SELECT lower(trim(email)) FROM nguoi_dung GROUP BY lower(trim(email)) HAVING count(*) > 1) THEN
    RAISE EXCEPTION 'Case-insensitive duplicate user emails; review before applying T07';
  END IF;
END $$;
UPDATE nguoi_dung SET email = lower(trim(email));
CREATE UNIQUE INDEX IF NOT EXISTS uq_nguoi_dung_email_normalized ON nguoi_dung (lower(email));
ALTER TABLE nguoi_dung ADD COLUMN IF NOT EXISTS ma_nha_xe varchar(40) REFERENCES nha_xe(ma_nha_xe);
ALTER TABLE nguoi_dung ADD COLUMN IF NOT EXISTS ma_nhan_vien varchar(40) REFERENCES nhan_vien(ma_nhan_vien);
CREATE UNIQUE INDEX IF NOT EXISTS uq_nguoi_dung_nhan_vien ON nguoi_dung (ma_nhan_vien);
CREATE TABLE IF NOT EXISTS phien_dang_nhap (
  token_hash varchar(64) PRIMARY KEY,
  ma_nguoi_dung varchar(40) NOT NULL REFERENCES nguoi_dung(ma_nguoi_dung),
  csrf_token varchar(64) NOT NULL,
  tao_luc timestamp with time zone NOT NULL,
  het_han timestamp with time zone NOT NULL,
  CONSTRAINT ck_phien_het_han CHECK (het_han > tao_luc)
);
CREATE INDEX IF NOT EXISTS ix_phien_nguoi_dung ON phien_dang_nhap(ma_nguoi_dung);
CREATE INDEX IF NOT EXISTS ix_phien_het_han ON phien_dang_nhap(het_han);
COMMIT;
