-- Passenger data is session-only until a ticket is issued; preserve and review any unexpected NHAP rows.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM ve_xe WHERE trang_thai = 'NHAP') THEN
    RAISE EXCEPTION 'Unexpected NHAP ticket rows exist; reconcile them before applying 005_booking_session.sql';
  END IF;
END;
$$;

DROP INDEX IF EXISTS uq_ve_xe_active_trip_seat;

CREATE UNIQUE INDEX uq_ve_xe_active_trip_seat
  ON ve_xe (ma_chuyen, ma_ghe)
  WHERE trang_thai IN ('DA_XUAT_VE', 'DA_LEN_XE');
