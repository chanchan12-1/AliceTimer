param([string]$Serial = 'emulator-5580', [int]$AdbPort = 5042)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$AdbPath = Join-Path $projectPath '.toolchain\android-sdk\platform-tools\adb.exe'
$env:ANDROID_USER_HOME = Join-Path $projectPath '.toolchain\android-user'
function Adb {
    $output = & $AdbPath -P $AdbPort -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
    return ($output -join "`n")
}
function State {
    $output = Adb shell content query --uri content://cn.alice.timer.debug/state
    if ($output -notmatch 'json=(\{.*\})') { throw "No debug state: $output" }
    return ($Matches[1] | ConvertFrom-Json)
}
function Assert-Phase([string]$Expected) {
    $state = State
    if ($state.cycle.phase -ne $Expected) { throw "Expected $Expected, got $($state.cycle.phase)" }
    return $state
}
function Open-Game([string]$Package) { Adb shell am start -W -n "$Package/cn.alice.mockgame.MockGameActivity" | Out-Null }
function Reset-Test([string]$Server, [int]$Seconds = 20) {
    Adb shell am broadcast -a cn.alice.timer.TEST -n cn.alice.timer/.debug.DebugReceiver --ez reset true --ei seconds $Seconds --es server $Server | Out-Null
    Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
    Start-Sleep -Seconds 3
}
if ($Serial -notmatch '^emulator-\d+$') { throw 'This script only runs on an official Android Emulator serial.' }
if ((Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Refusing to run on a physical phone.' }
New-Item -ItemType Directory -Force -Path (Join-Path $projectPath 'test-results') | Out-Null
Adb install -r (Join-Path $projectPath 'app\build\outputs\apk\debug\app-debug.apk') | Out-Null
$variants = @(
    @{Name='cn'; Package='com.RoamingStar.BlueArchive'; Apk='cn\debug\mockgame-cn-debug.apk'},
    @{Name='jp'; Package='com.YostarJP.BlueArchive'; Apk='jp\debug\mockgame-jp-debug.apk'},
    @{Name='global'; Package='com.nexon.bluearchive'; Apk='global\debug\mockgame-global-debug.apk'}
)
foreach ($variant in $variants) {
    $existing = Adb shell pm list packages $variant.Package
    if ($existing -match ('(?m)^package:' + [regex]::Escape($variant.Package) + '$') -and (Adb shell dumpsys package $variant.Package) -notmatch 'cn.alice.mockgame.MockGameActivity') {
        throw "Existing real game detected: $($variant.Package). Will not overwrite it."
    }
    Adb install -r (Join-Path $projectPath ('mockgame\build\outputs\apk\' + $variant.Apk)) | Out-Null
}
Adb shell appops set cn.alice.timer GET_USAGE_STATS allow | Out-Null
Adb shell appops set cn.alice.timer SCHEDULE_EXACT_ALARM allow | Out-Null
Adb shell pm grant cn.alice.timer android.permission.POST_NOTIFICATIONS | Out-Null
Adb shell dumpsys deviceidle whitelist +cn.alice.timer | Out-Null
$results = [System.Collections.Generic.List[object]]::new()
foreach ($variant in $variants) {
    Reset-Test $variant.Name
    Open-Game $variant.Package
    Start-Sleep -Seconds 3
    $waiting = Assert-Phase 'WAITING'
    if (-not $waiting.cycle.foreground) { throw 'Game foreground was not detected.' }
    Adb shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 4
    $initial = Assert-Phase 'RUNNING'
    Open-Game $variant.Package
    Start-Sleep -Seconds 3
    $reopened = Assert-Phase 'RUNNING'
    if ($reopened.cycle.due -ne $initial.cycle.due) { throw 'Reentering the game reset the deadline.' }
    Adb shell input keyevent KEYCODE_HOME | Out-Null
    $wait = [Math]::Max(1, [Math]::Ceiling(($initial.cycle.due - [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) / 1000) + 2)
    Start-Sleep -Seconds $wait
    $expired = Assert-Phase 'NOTIFIED'
    if ($expired.lastReminderAt -eq 0 -or $expired.lastReminder -notmatch '\u8001\u5E08') { throw 'Expected character reminder was not posted.' }
    $notifications = Adb shell dumpsys notification --noredact
    if ($notifications -notmatch 'cn.alice.timer.*id=20') { throw 'System character notification is missing.' }
    Open-Game $variant.Package
    Start-Sleep -Seconds 3
    $null = Assert-Phase 'WAITING'
    Adb shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 4
    $next = Assert-Phase 'RUNNING'
    if ($next.cycle.started -le $initial.cycle.due) { throw 'The next cycle did not start after the prior reminder.' }
    $results.Add(@{server=$variant.Name; checks=@('foreground','automatic exit timer','no reset on reentry','random character notification','next cycle'); status='passed'})
    Write-Host "PASS $($variant.Name): exit, reentry, reminder, next cycle"
}
# Lock screen must start a cycle and the system alarm must deliver while screen-off.
Reset-Test 'cn' 12
Open-Game 'com.RoamingStar.BlueArchive'
Start-Sleep -Seconds 3
Adb shell input keyevent KEYCODE_SLEEP | Out-Null
Start-Sleep -Seconds 4
$locked = Assert-Phase 'RUNNING'
Start-Sleep -Seconds 11
$null = Assert-Phase 'NOTIFIED'
Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Adb shell wm dismiss-keyguard | Out-Null
$results.Add(@{scenario='lock-screen'; status='passed'})
Write-Host 'PASS lock screen: starts timer and sends reminder'
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $projectPath 'test-results\emulator-tests.json') -Encoding utf8
Write-Host 'All basic emulator scenarios passed.'
