-- Read-only assertions for the T06 transport seed.
DO $$ BEGIN
IF (SELECT count(DISTINCT ten_nha_xe) FROM nha_xe WHERE ma_nha_xe
IN ('DEMO_NX','DEMO_NX_MORE','T06_NX_A','T06_NX_B','T06_NX_C','T06_NX_D'))<>6 THEN
RAISE EXCEPTION 'Expected six named sample operators'; END IF;
IF EXISTS(SELECT 1 FROM nha_xe WHERE ma_nha_xe IN ('DEMO_NX','DEMO_NX_MORE','T06_NX_A','T06_NX_B')
AND (ten_nha_xe ~* 'demo|mô phỏng' OR mo_ta ~* 'demo|mô phỏng'))
OR EXISTS(SELECT 1 FROM xe WHERE (ma_xe IN ('DEMO_XE','DEMO_XE2','DEMO_BAOTRI','DEMO_HETHAN')
OR ma_xe ~ '^(DEMO_FLEET_[1-6]|T06_X_[0-9]+)$') AND bien_so ~* 'demo') THEN
RAISE EXCEPTION 'Unnormalized sample display fields'; END IF;
IF EXISTS(SELECT 1 FROM nha_xe WHERE ma_nha_xe IN ('T06_NX_A','T06_NX_B') AND position('?' in ten_nha_xe)>0)
OR EXISTS(SELECT 1 FROM nhan_vien WHERE ma_nhan_vien ~ '^T06_(TX|PX)_[0-9]+$' AND position('?' in ho_ten)>0)
OR EXISTS(SELECT 1 FROM ben_xe WHERE ma_ben_xe LIKE 'T06_BX_%'
AND (position('mô phỏng' in ten_ben_xe)>0 OR position('?' in dia_chi)>0)) THEN
RAISE EXCEPTION 'Invalid T06 display text or encoding'; END IF;
IF EXISTS(SELECT 1 FROM xe WHERE ma_xe ~ '^T06_X_[0-9]+$' AND bien_so !~ '^51B-[0-9]{3}\.[0-9]{2}$') THEN
RAISE EXCEPTION 'Invalid sample license plate'; END IF;
IF EXISTS(SELECT 1 FROM ghe g JOIN xe x USING(ma_xe) WHERE g.ma_ghe ~ '^T06_X_([0-9]+|BAOTRI|HETHAN)_G[0-9]+$'
AND g.vi_tri<>CASE x.so_cho_ngoi WHEN 22 THEN 'P'||lpad(substring(g.ma_ghe from '[0-9]+$')::int::text,2,'0')
WHEN 29 THEN lpad(substring(g.ma_ghe from '[0-9]+$')::int::text,2,'0')
ELSE CASE WHEN substring(g.ma_ghe from '[0-9]+$')::int<=20 THEN 'A' ELSE 'B' END||lpad(((substring(g.ma_ghe from '[0-9]+$')::int-1)%20+1)::text,2,'0') END) THEN
RAISE EXCEPTION 'Invalid seat labels'; END IF;
IF (SELECT count(*) FROM xe WHERE left(ma_xe,6)='T06_X_')<>50
OR (SELECT count(*) FROM ghe WHERE left(ma_xe,6)='T06_X_')<>1507
OR (SELECT count(*) FROM chuyen_xe WHERE left(ma_chuyen,6)='T06_C_')<>1401
OR (SELECT count(*) FROM tuyen_xe WHERE left(ma_tuyen,6)='T06_R_')<>16
OR (SELECT count(*) FROM phan_cong_chuyen_xe WHERE left(ma_phan_cong,7)='T06_PC_')<>2796 THEN
RAISE EXCEPTION 'Unexpected T06 data counts'; END IF;
IF EXISTS(SELECT 1 FROM xe x JOIN loai_xe l USING(ma_loai_xe) WHERE left(x.ma_xe,6)='T06_X_'
AND (x.so_cho_ngoi<>l.so_cho_ngoi OR x.so_cho_ngoi NOT IN(22,29,40)
OR (SELECT count(*) FROM ghe g WHERE g.ma_xe=x.ma_xe)<>x.so_cho_ngoi)) THEN
RAISE EXCEPTION 'Vehicle capacity mismatch'; END IF;
IF EXISTS(SELECT 1 FROM ghe g JOIN xe x USING(ma_xe) JOIN loai_xe l USING(ma_loai_xe)
WHERE left(x.ma_xe,6)='T06_X_' AND (g.tang<1 OR g.tang>l.so_tang)) THEN
RAISE EXCEPTION 'Seat floor mismatch'; END IF;
IF EXISTS(SELECT 1 FROM chuyen_xe c JOIN tuyen_xe r USING(ma_tuyen) JOIN xe x USING(ma_xe)
WHERE left(c.ma_chuyen,6)='T06_C_' AND x.ma_nha_xe<>r.ma_nha_xe) THEN
RAISE EXCEPTION 'Trip operator mismatch'; END IF;
IF EXISTS(SELECT 1 FROM chuyen_xe a JOIN chuyen_xe b ON a.ma_xe=b.ma_xe AND a.ma_chuyen<b.ma_chuyen
WHERE left(a.ma_chuyen,6)='T06_C_' AND left(b.ma_chuyen,6)='T06_C_'
AND a.trang_thai<>'DA_HUY' AND b.trang_thai<>'DA_HUY'
AND a.ngay_khoi_hanh+a.gio_khoi_hanh<b.gio_den_du_kien
AND b.ngay_khoi_hanh+b.gio_khoi_hanh<a.gio_den_du_kien) THEN
RAISE EXCEPTION 'Vehicle schedule overlap'; END IF;
IF EXISTS(SELECT 1 FROM phan_cong_chuyen_xe p JOIN nhan_vien n USING(ma_nhan_vien)
JOIN chuyen_xe c USING(ma_chuyen) JOIN tuyen_xe r USING(ma_tuyen)
WHERE left(p.ma_phan_cong,7)='T06_PC_' AND n.ma_nha_xe<>r.ma_nha_xe) THEN
RAISE EXCEPTION 'Assignment operator mismatch'; END IF;
IF EXISTS(SELECT 1 FROM phan_cong_chuyen_xe p JOIN phan_cong_chuyen_xe q
ON p.ma_nhan_vien=q.ma_nhan_vien AND p.ma_phan_cong<q.ma_phan_cong
JOIN chuyen_xe a ON a.ma_chuyen=p.ma_chuyen JOIN chuyen_xe b ON b.ma_chuyen=q.ma_chuyen
WHERE left(p.ma_phan_cong,7)='T06_PC_' AND left(q.ma_phan_cong,7)='T06_PC_'
AND a.ngay_khoi_hanh+a.gio_khoi_hanh<b.gio_den_du_kien
AND b.ngay_khoi_hanh+b.gio_khoi_hanh<a.gio_den_du_kien) THEN
RAISE EXCEPTION 'Employee schedule overlap'; END IF;
IF NOT EXISTS(SELECT 1 FROM chuyen_xe WHERE left(ma_chuyen,6)='T06_C_'
AND gio_den_du_kien::date>ngay_khoi_hanh) THEN RAISE EXCEPTION 'Missing overnight trip'; END IF;
IF EXISTS(SELECT 1 FROM tuyen_xe a WHERE left(a.ma_tuyen,6)='T06_R_' AND NOT EXISTS
(SELECT 1 FROM tuyen_xe b WHERE left(b.ma_tuyen,6)='T06_R_'
AND b.tram_khoi_hanh=a.tram_den AND b.tram_den=a.tram_khoi_hanh)) THEN
RAISE EXCEPTION 'Missing reverse route'; END IF;
END $$;
SELECT 'PASS: T06 transport counts, capacities, floors, reverse routes and schedules' AS result;
