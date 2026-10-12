param([string]$Container='datvexe-postgres',[string]$Database='datvexe',[string]$User='datvexe_user')
$ErrorActionPreference='Stop'
$OutputEncoding=[System.Text.UTF8Encoding]::new($false)
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$seed=Get-Content -Raw -Encoding UTF8 (Join-Path $root 'docs/database/seed/006_t11_staff.sql')
$body=$seed -replace '(?m)^BEGIN;\s*$', '' -replace '(?m)^COMMIT;\s*$', ''
function Run-Sql([string]$Sql) {
    $Sql | docker exec -i $Container psql -U $User -d $Database -v ON_ERROR_STOP=1
    if($LASTEXITCODE -ne 0) { throw 'T11 load failed; check schema and base transport seeds. Transaction rolled back.' }
}
$checks=@'
DO $$ BEGIN
IF (SELECT count(*) FROM nhan_vien WHERE ma_nhan_vien IN (SELECT 'NV_'||lpad((n*10+r)::text,3,'0') FROM t11_operators CROSS JOIN generate_series(1,3) r))<>18
OR (SELECT count(*) FROM xe WHERE ma_xe IN (SELECT 'XE_'||lpad(n::text,3,'0') FROM t11_operators))<>6
OR (SELECT count(*) FROM chuyen_xe WHERE ma_chuyen IN (SELECT 'CX_'||n||'_'||d FROM t11_operators CROSS JOIN generate_series(1,2) d))<>12 THEN
RAISE EXCEPTION 'T11 sample counts mismatch'; END IF;
IF EXISTS(SELECT 1 FROM phan_cong_chuyen_xe p JOIN nhan_vien n USING(ma_nhan_vien)
JOIN chuyen_xe c USING(ma_chuyen) JOIN tuyen_xe r USING(ma_tuyen)
WHERE c.ma_chuyen ~ '^CX_[1-6]_[1-2]$' AND n.ma_nha_xe<>r.ma_nha_xe) THEN
RAISE EXCEPTION 'T11 employee/operator mismatch'; END IF;
IF EXISTS(SELECT 1 FROM chuyen_xe c WHERE c.ma_chuyen ~ '^CX_[1-6]_[1-2]$'
AND (SELECT count(*) FROM phan_cong_chuyen_xe p WHERE p.ma_chuyen=c.ma_chuyen)<>3) THEN
RAISE EXCEPTION 'T11 crew missing'; END IF;
IF EXISTS(SELECT 1 FROM chuyen_xe a JOIN chuyen_xe b ON b.ma_xe=a.ma_xe
JOIN tuyen_xe r ON r.ma_tuyen=a.ma_tuyen JOIN tuyen_xe s ON s.ma_tuyen=b.ma_tuyen
WHERE a.ma_chuyen ~ '^CX_[1-6]_1$' AND b.ma_chuyen ~ '^CX_[1-6]_2$'
AND (r.tram_den<>s.tram_khoi_hanh OR a.gio_den_du_kien+INTERVAL '30 minutes'>b.ngay_khoi_hanh+b.gio_khoi_hanh)) THEN
RAISE EXCEPTION 'T11 round-trip connection invalid'; END IF;
END $$;
'@
$parts=foreach($table in @('nhan_vien','xe','ghe','tuyen_xe','chuyen_xe','phan_cong_chuyen_xe')) {
    "SELECT '$table' name, md5(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text)::text,'[]')) hash FROM $table r"
}
$fingerprints=$parts -join ' UNION ALL '
$same="DO `$`$ BEGIN IF EXISTS(SELECT * FROM t11_fingerprints EXCEPT SELECT * FROM ($fingerprints) current_data) THEN RAISE EXCEPTION 'T11 reload changed data'; END IF; END `$`$;"
Run-Sql ("BEGIN;`n$body`n$checks`nCREATE TEMP TABLE t11_fingerprints AS $fingerprints;`nDROP TABLE t11_operators;`n$body`n$checks`n$same`nROLLBACK;")
Run-Sql ("BEGIN;`n$body`n$checks`nCOMMIT;")
Write-Output 'PASS: T11 staff/round-trip seed, crew and identical reload; existing rows preserved.'
