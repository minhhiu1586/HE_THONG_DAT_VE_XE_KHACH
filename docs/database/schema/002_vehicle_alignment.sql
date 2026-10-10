-- Apply after 001_route_trip.sql, on both new and existing databases.
BEGIN;
ALTER TABLE nhan_vien ADD COLUMN IF NOT EXISTS so_bang_lai varchar(80);
ALTER TABLE xe ADD COLUMN IF NOT EXISTS dung_tich_xang numeric(10,2);
ALTER TABLE phan_cong_chuyen_xe ADD COLUMN IF NOT EXISTS ghi_chu varchar(1000);
-- Existing role values and records are preserved; no automatic data conversion.
COMMIT;
