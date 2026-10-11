param([string]$ApiUrl = 'http://localhost:8080/api')
$ErrorActionPreference = 'Stop'
$ApiUrl = $ApiUrl.TrimEnd('/')
$checks = 0
function Read-Api([string]$Path) { Invoke-RestMethod "$ApiUrl$Path" -TimeoutSec 20 }
function Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
    $script:checks++
}
$stations = Read-Api '/ben-xe'
Check (@($stations | Where-Object {$_.maBenXe -like 'T06_*'}).Count -eq 7) 'Station count mismatch'
$routes = Read-Api '/tuyen-xe'
Check (@($routes | Where-Object {$_.maTuyen -like 'T06_*'}).Count -eq 16) 'Route count mismatch'
foreach ($case in @(@('T06_X_1',22),@('T06_X_2',29),@('T06_X_3',40))) {
    $seats = Read-Api "/xe/$($case[0])/ghe"
    Check ($seats.Count -eq $case[1] -and @($seats | Where-Object maXe -ne $case[0]).Count -eq 0) 'Seat capacity/vehicle mismatch'
}
foreach ($case in @(@('T06_X_1','BAO_TRI'),@('T06_X_2','NGUNG_SU_DUNG'))) {
    $seats = Read-Api "/xe/$($case[0])/ghe"
    Check (@($seats | Where-Object trangThai -eq $case[1]).Count -eq 1) 'Physical seat status mismatch'
}
$night = Read-Api '/chuyen-xe/T06_C_3_1'
Check (([datetime]$night.gioDenDuKien).Date -gt ([datetime]$night.ngayKhoiHanh).Date) 'Overnight arrival mismatch'
$trip = Read-Api '/chuyen-xe/T06_C_1_1'
Check ($trip.maXe -eq 'T06_X_1') 'Trip vehicle mismatch'
$found = Read-Api "/chuyen-xe?from=T06_BX_HCM_E&to=T06_BX_LD&date=$($trip.ngayKhoiHanh)"
Check (@($found | Where-Object maChuyen -eq 'T06_C_1_1').Count -eq 1) 'Search did not return seeded trip'
foreach ($case in @(@('T06_C_DONE','HOAN_TAT'),@('T06_C_CANCEL','DA_HUY'))) {
    $value = Read-Api "/chuyen-xe/$($case[0])"
    Check ($value.trangThai -eq $case[1]) 'Trip status mismatch'
}
$unassigned = Read-Api '/chuyen-xe/T06_C_NO_VEHICLE'
Check ($null -eq $unassigned.maXe) 'Unassigned trip unexpectedly has a vehicle'
function Check-Status([string]$Path, [int]$Expected) {
    $status = 200
    try { $null = Invoke-WebRequest "$ApiUrl$Path" -UseBasicParsing -TimeoutSec 20 }
    catch {
        if ($null -eq $_.Exception.Response) { throw }
        $status = [int]$_.Exception.Response.StatusCode
    }
    Check ($status -eq $Expected) "Expected HTTP $Expected for $Path, got $status"
}
Check-Status '/chuyen-xe?from=T06_BX_HCM_E&to=T06_BX_LD&date=invalid' 400
Check-Status '/chuyen-xe/T06_NOT_FOUND' 404
$nightSearch = Read-Api "/chuyen-xe?from=T06_BX_HCM_E&to=T06_BX_LD&date=$($night.ngayKhoiHanh)"
Check (@($nightSearch | Where-Object maChuyen -eq 'T06_C_3_1').Count -eq 1) 'Night trip must be found by departure date'
Write-Output "PASS: $checks read-only T06 API checks."
