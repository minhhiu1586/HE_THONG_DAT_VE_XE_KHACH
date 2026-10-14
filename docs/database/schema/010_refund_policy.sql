-- Align the promotion-refund flag with the class diagram while retaining its stored values.
DO $$
DECLARE
  old_column_exists boolean;
  new_column_exists boolean;
BEGIN
  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'chinh_sach_hoan_ve'
      AND column_name = 'cho_phep_hoan_huy_ve'
  ) INTO old_column_exists;

  SELECT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'chinh_sach_hoan_ve'
      AND column_name = 'cho_phep_hoan_ve_khuyen_mai'
  ) INTO new_column_exists;

  IF old_column_exists AND NOT new_column_exists THEN
    ALTER TABLE chinh_sach_hoan_ve
      RENAME COLUMN cho_phep_hoan_huy_ve TO cho_phep_hoan_ve_khuyen_mai;
  ELSIF old_column_exists AND new_column_exists THEN
    IF EXISTS (
      SELECT 1 FROM chinh_sach_hoan_ve
      WHERE cho_phep_hoan_ve_khuyen_mai IS DISTINCT FROM cho_phep_hoan_huy_ve
    ) THEN
      RAISE EXCEPTION
        'Refund policy flag columns contain conflicting values; reconcile them before migration.';
    END IF;
  ELSIF NOT new_column_exists THEN
    ALTER TABLE chinh_sach_hoan_ve
      ADD COLUMN cho_phep_hoan_ve_khuyen_mai boolean NOT NULL DEFAULT false;
  END IF;
END $$;

-- Refuse ambiguous existing configuration rather than silently selecting a policy.
DO $$
BEGIN
  IF (SELECT count(*) FROM chinh_sach_hoan_ve WHERE trang_thai = 'HOAT_DONG') > 1 THEN
    RAISE EXCEPTION
      'Multiple active refund policies exist; choose one active policy before migration.';
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'ck_chinh_sach_hoan_ve_phi_nguyen'
      AND conrelid = 'chinh_sach_hoan_ve'::regclass
  ) THEN
    ALTER TABLE chinh_sach_hoan_ve
      ADD CONSTRAINT ck_chinh_sach_hoan_ve_phi_nguyen
      CHECK (phi_hoan >= 0 AND phi_hoan = trunc(phi_hoan));
  END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_chinh_sach_hoan_ve_active
  ON chinh_sach_hoan_ve ((trang_thai))
  WHERE trang_thai = 'HOAT_DONG';

-- Insert the demo rule only when its code is free and there is no active policy.
INSERT INTO chinh_sach_hoan_ve (
  ma_chinh_sach,
  ten_chinh_sach,
  so_gio_toi_thieu_truoc_khoi_hanh,
  ty_le_hoan,
  phi_hoan,
  cho_phep_hoan_ve_khuyen_mai,
  trang_thai
)
SELECT
  'CS_DEMO_24H_80',
  'Hoàn vé trước 24 giờ',
  24,
  80.00,
  0,
  true,
  'HOAT_DONG'
WHERE NOT EXISTS (
    SELECT 1 FROM chinh_sach_hoan_ve WHERE trang_thai = 'HOAT_DONG'
  )
  AND NOT EXISTS (
    SELECT 1 FROM chinh_sach_hoan_ve WHERE ma_chinh_sach = 'CS_DEMO_24H_80'
  )
ON CONFLICT (ma_chinh_sach) DO NOTHING;
