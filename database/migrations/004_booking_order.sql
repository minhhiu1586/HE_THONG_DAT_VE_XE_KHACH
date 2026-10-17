-- Ensure existing active seat conflicts are reviewed before enforcing the new lifecycle rule.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM ve_xe
    WHERE trang_thai IN ('NHAP', 'DA_XUAT_VE', 'DA_LEN_XE')
    GROUP BY ma_chuyen, ma_ghe
    HAVING COUNT(*) > 1
  ) THEN
    RAISE EXCEPTION 'Active duplicate tickets exist for the same trip and seat; resolve them before applying 004_booking_order.sql';
  END IF;
END;
$$;

ALTER TABLE ve_xe
  DROP CONSTRAINT IF EXISTS uq_ve_xe_ma_ghe_chuyen;

CREATE UNIQUE INDEX IF NOT EXISTS uq_ve_xe_active_trip_seat
  ON ve_xe (ma_chuyen, ma_ghe)
  WHERE trang_thai IN ('NHAP', 'DA_XUAT_VE', 'DA_LEN_XE');

ALTER TABLE don_dat_ve
  DROP CONSTRAINT IF EXISTS don_dat_ve_ma_khuyen_mai_fkey;

CREATE TABLE IF NOT EXISTS api_idempotency (
  user_id varchar(40) NOT NULL,
  method varchar(10) NOT NULL,
  path varchar(240) NOT NULL,
  request_key varchar(128) NOT NULL,
  request_hash varchar(64) NOT NULL,
  status integer NOT NULL,
  response_body text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  PRIMARY KEY (user_id, method, path, request_key)
);

CREATE INDEX IF NOT EXISTS ix_api_idempotency_expires_at
  ON api_idempotency (expires_at);

