param([string]$Container = 'datvexe-postgres', [string]$Database = 'datvexe', [string]$User = 'datvexe_user', [switch]$IncludeScenarios)
$ErrorActionPreference = 'Stop'
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$seed = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tests/backend/fixtures/sql/001_base.sql')
$seed += "`n" + (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tests/backend/fixtures/sql/002_fleet.sql'))
$seed += "`n" + (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tests/backend/fixtures/sql/003_transport.sql'))
$checks = Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tests/backend/t06_transport_assertions.sql')
if ($IncludeScenarios) {
    $seed += "`n" + (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tests/backend/fixtures/sql/004_scenarios.sql'))
    $checks += "`n" + (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tests/backend/t06_scenario_assertions.sql'))
}
$seed += "`n" + (Get-Content -Raw -Encoding UTF8 (Join-Path $root 'tests/backend/fixtures/sql/005_alignment.sql'))
function Run-Sql([string]$Sql) {
    $Sql | docker exec -i $Container psql -U $User -d $Database -v ON_ERROR_STOP=1
    if ($LASTEXITCODE -ne 0) { throw 'SQL failed; transaction not committed.' }
}
# Test and reload inside one transaction, then roll back. No extra container/volume.
$body = $seed -replace '(?m)^BEGIN;\s*$', '' -replace '(?m)^COMMIT;\s*$', ''
$dropTemp = 'DROP TABLE t06_routes; DROP TABLE t06_vehicles;'
if ($IncludeScenarios) { $dropTemp += ' DROP TABLE t06_orders;' }
# Fingerprints include complete affected tables before/after the second load.
$tables = @('nha_xe','tinh_thanh','ben_xe','tuyen_xe','diem_dung','xe','ghe','nhan_vien','chuyen_xe','phan_cong_chuyen_xe')
if ($IncludeScenarios) {
    $tables += @('loai_xe','nguoi_dung','don_dat_ve','ve_xe','giu_cho','thanh_toan',
                 'khuyen_mai','chinh_sach_hoan_ve','phuong_thuc_thanh_toan','danh_gia')
}
$parts = foreach ($table in $tables) {
    "SELECT '$table' AS name, md5(coalesce(jsonb_agg(to_jsonb(r) ORDER BY to_jsonb(r)::text)::text,'[]')) AS hash FROM $table r"
}
$fingerprints = $parts -join ' UNION ALL '
$reloadCheck = "DO `$`$ BEGIN IF EXISTS (SELECT * FROM t06_fingerprints EXCEPT SELECT * FROM ($fingerprints) current_data) THEN RAISE EXCEPTION 'Reload changed data'; END IF; END `$`$;"
Run-Sql ("BEGIN;`n" + $body + "`n" + $checks + "`nCREATE TEMP TABLE t06_fingerprints AS $fingerprints;`n" + $dropTemp + "`n" + $body + "`n" + $checks + "`n" + $reloadCheck + "`nROLLBACK;")
Write-Output 'PASS: trial load and identical reload rolled back.'
# Commit only after validation. Constraints stop the entire transaction on error.
Run-Sql ("BEGIN;`n" + $body + "`n" + $checks + "`nCOMMIT;")
Write-Output 'PASS: sample data loaded; existing IDs, schedules and business states preserved.'
