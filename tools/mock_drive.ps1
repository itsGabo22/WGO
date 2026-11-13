<#
.SYNOPSIS
  Mock GPS drive on a REAL watch (debug build): replays the route the app is following, or holds one position.

.DESCRIPTION
  Real watches have no `adb emu geo fix` (tools/sim_drive.ps1 is emulator-only). This sends the debug-only
  MockDriveReceiver a polyline; it feeds the fused provider in mock mode at the given speed, with a partial wake lock so
  the replay continues with the screen off (test tool only: it inflates battery numbers). The first time:
  `adb -s <serial> shell appops set com.wgo.navigator android:mock_location allow` (done by -Setup).
  Points are "lat,lon" (latitude FIRST here, unlike `geo fix`).

.EXAMPLE
  .\tools\mock_drive.ps1 -Serial 192.168.1.2:33379 -Setup -Hold "1.2290,-77.2945"   # origin before a trip
  .\tools\mock_drive.ps1 -Serial 192.168.1.2:33379 -FromLogcat -SpeedKmh 36           # replay the active route
  .\tools\mock_drive.ps1 -Serial 192.168.1.2:33379 -Stop                              # leave mock mode
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Serial,
    [string]$Hold,
    [switch]$FromLogcat,
    [string]$Points,
    [double]$SpeedKmh = 36,
    [int]$IntervalMs = 1000,
    [switch]$Setup,
    [switch]$Stop,
    [string]$Pkg = 'com.wgo.navigator',
    [string]$Adb = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)
$ErrorActionPreference = 'Stop'
$inv = [Globalization.CultureInfo]::InvariantCulture
$action = "$Pkg.debug.MOCK_DRIVE"
if ($Setup) { & $Adb -s $Serial shell appops set $Pkg android:mock_location allow }
if ($Stop) { & $Adb -s $Serial shell am broadcast -a $action -p $Pkg --ez stop true | Out-Null; 'mock stopped'; return }
if ($FromLogcat) {
    $lines = @(& $Adb -s $Serial logcat -d -s WGO_ROUTE:I | ForEach-Object { ($_ -split 'WGO_ROUTE:\s*', 2)[-1].Trim() })
    $begin = -1; $end = -1
    for ($i = $lines.Count - 1; $i -ge 0; $i--) {
        if ($end -lt 0 -and $lines[$i] -eq 'end') { $end = $i }
        elseif ($end -ge 0 -and $lines[$i] -like 'begin*') { $begin = $i; break }
    }
    if ($begin -lt 0) { throw 'No WGO_ROUTE dump in logcat: start a trip on a debug build first.' }
    $Points = ($lines[($begin + 1)..($end - 1)]) -join ';'
} elseif ($Hold) {
    $Points = $Hold
}
if (-not $Points) { throw 'Give -Hold "lat,lon", -Points "lat,lon;..." or -FromLogcat.' }
& $Adb -s $Serial shell am broadcast -a $action -p $Pkg --es points "'$Points'" --ef speed_kmh $SpeedKmh.ToString($inv) --el interval_ms $IntervalMs | Out-Null
"mock drive: $(($Points -split ';').Count) points at $SpeedKmh km/h on $Serial"
