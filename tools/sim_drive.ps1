<#
.SYNOPSIS
  Drives an emulator's GPS along a polyline with `adb emu geo fix`, for live location tests of WGO.

.DESCRIPTION
  Interpolates points every (speed * interval) metres along the polyline and sends one
  `adb -s <serial> emu geo fix <longitude> <latitude> <altitude>` per interval. NOTE: geo fix takes LONGITUDE FIRST.
  Points are given as "lat,lon" pairs (inline, separated by ';') or in a file with one "lat,lon" pair per line
  ('#' starts a comment). Only works on emulators; it never starts, restarts or kills one.

.EXAMPLE
  .\tools\sim_drive.ps1 -Points "1.2120,-77.2830;1.2120,-77.2808;1.2142,-77.2808" -SpeedKmh 30 -IntervalMs 1000

.EXAMPLE
  .\tools\sim_drive.ps1 -PolylineFile .\route.txt -SpeedKmh 5 -IntervalMs 2000 -Serial emulator-5554 -Loop
#>
[CmdletBinding()]
param(
    [string]$Points,
    [string]$PolylineFile,
    [double]$SpeedKmh = 30,
    [int]$IntervalMs = 1000,
    [string]$Serial = 'emulator-5554',
    [double]$AltitudeM = 2527,  # Pasto
    [string]$Adb = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'),
    [switch]$Loop,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$inv = [Globalization.CultureInfo]::InvariantCulture

function Parse-Pair([string]$text) {
    $parts = $text.Trim().Split(',')
    if ($parts.Count -ne 2) { throw "Bad point '$text' (expected lat,lon)" }
    [pscustomobject]@{ Lat = [double]::Parse($parts[0].Trim(), $inv); Lon = [double]::Parse($parts[1].Trim(), $inv) }
}

if ($PolylineFile) {
    $raw = Get-Content -LiteralPath $PolylineFile | Where-Object { $_.Trim() -and -not $_.Trim().StartsWith('#') }
} elseif ($Points) {
    $raw = $Points.Split(';') | Where-Object { $_.Trim() }
} else {
    throw 'Give -Points "lat,lon;lat,lon;..." or -PolylineFile <file>.'
}
$poly = @($raw | ForEach-Object { Parse-Pair $_ })
if ($poly.Count -lt 1) { throw 'The polyline is empty.' }
if ($SpeedKmh -le 0 -or $IntervalMs -le 0) { throw 'SpeedKmh and IntervalMs must be positive.' }

$R = 6371000.0
function Rad([double]$d) { $d * [Math]::PI / 180 }
function Dist($a, $b) {
    $dLat = Rad ($b.Lat - $a.Lat); $dLon = Rad ($b.Lon - $a.Lon)
    $h = [Math]::Pow([Math]::Sin($dLat / 2), 2) + [Math]::Cos((Rad $a.Lat)) * [Math]::Cos((Rad $b.Lat)) * [Math]::Pow([Math]::Sin($dLon / 2), 2)
    2 * $R * [Math]::Asin([Math]::Sqrt($h))
}

# Sample points every $step metres (linear interpolation is exact enough at city scale).
$step = $SpeedKmh / 3.6 * $IntervalMs / 1000.0
$samples = New-Object System.Collections.Generic.List[object]
$samples.Add($poly[0])
for ($i = 0; $i -lt $poly.Count - 1; $i++) {
    $a = $poly[$i]; $b = $poly[$i + 1]
    $len = Dist $a $b
    $carry = if ($samples.Count -gt 1) { $script:leftover } else { 0.0 }
    $d = $step - $carry
    while ($d -le $len) {
        $f = if ($len -gt 0) { $d / $len } else { 1 }
        $samples.Add([pscustomobject]@{ Lat = $a.Lat + ($b.Lat - $a.Lat) * $f; Lon = $a.Lon + ($b.Lon - $a.Lon) * $f })
        $d += $step
    }
    $script:leftover = $len - ($d - $step)
}
$last = $poly[$poly.Count - 1]
$samples.Add($last)

if (-not $DryRun) {
    $state = & $Adb -s $Serial get-state 2>$null
    if ($state -ne 'device') { throw "Device '$Serial' is not connected (adb get-state: '$state'). Start the emulator yourself." }
}

Write-Host ("{0} fixes, {1:N1} m apart, every {2} ms on {3}" -f $samples.Count, $step, $IntervalMs, $Serial)
do {
    foreach ($p in $samples) {
        $lon = $p.Lon.ToString('F6', $inv); $lat = $p.Lat.ToString('F6', $inv); $alt = $AltitudeM.ToString('F0', $inv)
        if ($DryRun) { Write-Output "geo fix $lon $lat $alt"; continue }
        & $Adb -s $Serial emu geo fix $lon $lat $alt | Out-Null
        Start-Sleep -Milliseconds $IntervalMs
    }
} while ($Loop)
