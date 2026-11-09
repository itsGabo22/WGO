<#
.SYNOPSIS
  Starts the WGO test emulator headless and detached, waits for boot, or stops it (-Stop).

.DESCRIPTION
  The emulator's Qt window crashes on this PC, so the AVD runs with -no-window, launched through
  Win32_Process.Create so it is not tied to the calling terminal. Output goes to %TEMP%\wgo-emu.log.
  After launch: `adb wait-for-device`, then sys.boot_completed is polled every 5 s (6 min timeout).
  If boot times out or the emulator process disappears, it relaunches ONCE with -gpu angle_indirect and
  prints the log tail. Never uses swiftshader (its jank numbers are meaningless). PowerShell 5.1 compatible.

.EXAMPLE
  .\tools\start-emulator.ps1            # first AVD whose name contains "Small"
  .\tools\start-emulator.ps1 -Avd Wear_OS_Small_Round
  .\tools\start-emulator.ps1 -Stop      # adb -s emulator-5554 emu kill
#>
[CmdletBinding()]
param(
    [string]$Avd,
    [switch]$Stop,
    [string]$Serial = 'emulator-5554',
    [int]$BootTimeoutSec = 360
)

$ErrorActionPreference = 'Stop'
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$log = Join-Path $env:TEMP 'wgo-emu.log'

function Show-Devices { Write-Host '--- adb devices'; & $adb devices | Write-Host }

function Show-LogTail([int]$Lines = 25) {
    if (Test-Path -LiteralPath $log) {
        Write-Host "--- tail of $log"
        Get-Content -LiteralPath $log -Tail $Lines | Write-Host
    }
}

function Test-EmulatorProcess {
    [bool](Get-Process -Name 'emulator', 'qemu-system-*' -ErrorAction SilentlyContinue)
}

if ($Stop) {
    Write-Host "Stopping $Serial (adb emu kill)"
    & $adb -s $Serial emu kill | Write-Host
    Show-Devices
    return
}

if (-not (Test-Path -LiteralPath $emulator)) { throw "emulator.exe not found at $emulator" }
if (-not $Avd) {
    $Avd = & $emulator -list-avds | Where-Object { $_ -match 'Small' } | Select-Object -First 1
    if (-not $Avd) { throw 'No AVD whose name contains "Small"; pass -Avd <name>.' }
}

$listed = (& $adb devices) -match "^$([regex]::Escape($Serial))\s"
if ($listed -and ($listed -match '\sdevice$')) {
    Write-Host "$Serial is already online; nothing to start."
    Show-Devices
    return
}

function Start-Headless([string]$Gpu) {
    $cmd = "cmd.exe /c `"`"$emulator`" -avd $Avd -no-window -gpu $Gpu -no-snapshot-load -no-boot-anim -allow-host-audio -verbose > `"$log`" 2>&1`""
    Write-Host ("[{0:HH:mm:ss}] Launching {1} headless with -gpu {2}" -f (Get-Date), $Avd, $Gpu)
    $r = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{ CommandLine = $cmd }
    if ($r.ReturnValue -ne 0) { throw "Win32_Process.Create failed with code $($r.ReturnValue)" }
}

function Wait-Boot {
    $deadline = (Get-Date).AddSeconds($BootTimeoutSec)
    Start-Sleep -Seconds 5
    # wait-for-device blocks forever if the emulator dies, so run it as a job bounded by the deadline.
    $job = Start-Job -ScriptBlock { param($a, $s) & $a -s $s wait-for-device } -ArgumentList $adb, $Serial
    while ($job.State -eq 'Running') {
        if ((Get-Date) -gt $deadline) { Stop-Job $job; Remove-Job $job -Force; return 'timeout' }
        if (-not (Test-EmulatorProcess)) { Stop-Job $job; Remove-Job $job -Force; return 'died' }
        Start-Sleep -Seconds 2
    }
    Remove-Job $job -Force
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-EmulatorProcess)) { return 'died' }
        $booted = (& $adb -s $Serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($booted -eq '1') { return 'booted' }
        Start-Sleep -Seconds 5
    }
    return 'timeout'
}

Start-Headless 'host'
$result = Wait-Boot
if ($result -ne 'booted') {
    Write-Host ("[{0:HH:mm:ss}] First launch ended with '{1}'." -f (Get-Date), $result)
    Show-LogTail
    if (Test-EmulatorProcess) { & $adb -s $Serial emu kill 2>$null | Out-Null; Start-Sleep -Seconds 5 }
    Start-Headless 'angle_indirect'
    $result = Wait-Boot
    if ($result -ne 'booted') {
        Write-Host ("[{0:HH:mm:ss}] Second launch ended with '{1}'." -f (Get-Date), $result)
        Show-LogTail
    } else {
        Write-Host 'NOTE: running on -gpu angle_indirect, not host: jank numbers are not comparable with the host baselines.'
    }
}
Write-Host ("[{0:HH:mm:ss}] Result: {1}" -f (Get-Date), $result)
Show-Devices
