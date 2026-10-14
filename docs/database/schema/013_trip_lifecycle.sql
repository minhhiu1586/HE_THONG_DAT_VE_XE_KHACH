-- T21: durable trip lifecycle audit, cancellation work and notification outbox.
CREATE TABLE IF NOT EXISTS trip_lifecycle_history (
  id varchar(40) PRIMARY KEY,
  ma_chuyen varchar(40) NOT NULL REFERENCES chuyen_xe(ma_chuyen),
  trang_thai_cu varchar(30) NOT NULL,
  trang_thai_moi varchar(30) NOT NULL,
  ma_nguoi_thao_tac varchar(40) NOT NULL REFERENCES nguoi_dung(ma_nguoi_dung),
  vai_tro varchar(30) NOT NULL,
  ly_do varchar(1000),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_trip_lifecycle_history_trip
  ON trip_lifecycle_history(ma_chuyen, created_at DESC);

CREATE TABLE IF NOT EXISTS trip_cancellation_work (
  ma_don varchar(40) NOT NULL REFERENCES don_dat_ve(ma_don),
  ma_chuyen varchar(40) NOT NULL REFERENCES chuyen_xe(ma_chuyen),
  trang_thai varchar(24) NOT NULL CHECK (trang_thai IN ('CHO_XU_LY','CAN_DOI_SOAT','HOAN_TAT','KHONG_CAN_HOAN')),
  ly_do varchar(1000) NOT NULL,
  last_error varchar(120),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (ma_don, ma_chuyen)
);
CREATE INDEX IF NOT EXISTS ix_trip_cancellation_work_trip
  ON trip_cancellation_work(ma_chuyen, trang_thai);

CREATE TABLE IF NOT EXISTS trip_cancellation_notification (
  ma_don varchar(40) NOT NULL REFERENCES don_dat_ve(ma_don),
  ma_chuyen varchar(40) NOT NULL REFERENCES chuyen_xe(ma_chuyen),
  recipient varchar(254) NOT NULL,
  recipient_name varchar(160) NOT NULL,
  status varchar(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','SENDING','SENT','FAILED','UNKNOWN')),
  attempts integer NOT NULL DEFAULT 0,
  next_attempt_at timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now(),
  started_at timestamptz,
  sent_at timestamptz,
  provider_message_id varchar(255),
  last_error varchar(120),
  PRIMARY KEY (ma_don, ma_chuyen)
);
