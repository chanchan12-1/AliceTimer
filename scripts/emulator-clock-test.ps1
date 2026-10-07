param([string]$Serial = 'emulator-5580', [int]$AdbPort = 5042)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $projectPath '.toolchain\android-sdk\platform-tools\adb.exe'
function Adb {
    $output = & $adbPath -P $AdbPort -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
    return ($output -join "`n")
}
function State {
    $output = Adb shell content query --uri content://cn.alice.timer.debug/state
    if ($output -notmatch 'json=(\{.*\})') { throw 'No debug state.' }
    return ($Matches[1] | ConvertFrom-Json)
}
if ($Serial -notmatch '^emulator-\d+$' -or (Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Official emulator required.' }
Adb root | Out-Null
Start-Sleep -Seconds 2
$autoTime = (Adb shell settings get global auto_time).Trim()
try {
    Adb shell settings put global auto_time 0 | Out-Null
    Adb shell am broadcast -a cn.alice.timer.TEST -n cn.alice.timer/.debug.DebugReceiver --ez reset true --ei seconds 30 --es server cn | Out-Null
    Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
    Start-Sleep -Seconds 3
    Adb shell am start -W -n com.RoamingStar.BlueArchive/cn.alice.mockgame.MockGameActivity | Out-Null
    Start-Sleep -Seconds 3
    Adb shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 4
    $before = State
    if ($before.cycle.phase -ne 'RUNNING') { throw 'No running timer.' }
    $guestSeconds = [long](Adb shell date +%s).Trim()
    Adb shell date -s ('@' + ($guestSeconds + 3600)) | Out-Null
    Start-Sleep -Seconds 4
    $after = State
    $shift = $after.cycle.due - $before.cycle.due
    if ($after.cycle.phase -ne 'RUNNING' -or $shift -lt 3598000 -or $shift -gt 3602000) { throw "Clock edit changed timer duration: shift=$shift" }
    @{scenario='manual clock forward one hour'; status='passed'; deadlineShiftMs=$shift} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectPath 'test-results\emulator-clock-test.json') -Encoding utf8
    Write-Host 'PASS manual clock change: remaining duration preserved'
} finally {
    Adb shell date -s ('@' + [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()) | Out-Null
    Adb shell settings put global auto_time $autoTime | Out-Null
}
