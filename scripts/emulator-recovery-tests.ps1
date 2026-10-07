param([string]$Serial = 'emulator-5580', [int]$AdbPort = 5042, [switch]$RebootOnly)
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
    if ($output -notmatch 'json=(\{.*\})') { throw "No state: $output" }
    return ($Matches[1] | ConvertFrom-Json)
}
function Reset([int]$Seconds) {
    Adb shell am broadcast -a cn.alice.timer.TEST -n cn.alice.timer/.debug.DebugReceiver --ez reset true --ei seconds $Seconds --es server cn | Out-Null
    Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
    Start-Sleep -Seconds 3
}
function Open-Game { Adb shell am start -W -n com.RoamingStar.BlueArchive/cn.alice.mockgame.MockGameActivity | Out-Null; Start-Sleep -Seconds 3 }
function Start-Timer {
    Open-Game
    Adb shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Seconds 4
    $state = State
    if ($state.cycle.phase -ne 'RUNNING') { throw 'Automatic timer did not start.' }
    return $state
}
function Wait-Reminder([long]$Deadline) {
    $wait = [Math]::Max(1, [Math]::Ceiling(($Deadline - [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) / 1000) + 3)
    Start-Sleep -Seconds $wait
    # Verify the system notification before asking the debug provider to start the app process.
    if ((Adb shell dumpsys notification --noredact) -notmatch 'cn.alice.timer.*id=20') { throw 'System reminder is missing.' }
    $state = State
    if ($state.cycle.phase -ne 'NOTIFIED' -or $state.lastReminderAt -ne $Deadline) { throw 'Wrong or missing reminder deadline.' }
}
if ($Serial -notmatch '^emulator-\d+$' -or (Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Official emulator required.' }
if ((Adb shell dumpsys package com.RoamingStar.BlueArchive) -notmatch 'cn.alice.mockgame.MockGameActivity') { throw 'Test shell app required.' }
$results = [System.Collections.Generic.List[object]]::new()
if (-not $RebootOnly) {
Reset 20
Open-Game
Adb shell uiautomator dump /sdcard/alice-mock-ui.xml | Out-Null
Adb pull /sdcard/alice-mock-ui.xml (Join-Path $projectPath 'test-results\mock-ui.xml') | Out-Null
[xml]$layout = Get-Content -LiteralPath (Join-Path $projectPath 'test-results\mock-ui.xml') -Raw -Encoding utf8
$button = $layout.SelectSingleNode("//node[@class='android.widget.Button']")
if ($button.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { throw 'Mock internal-page button missing.' }
$x = [int](([int]$Matches[1]+[int]$Matches[3])/2)
$y = [int](([int]$Matches[2]+[int]$Matches[4])/2)
Adb shell input tap $x $y | Out-Null
Start-Sleep -Seconds 3
$state = State
if ($state.cycle.phase -ne 'WAITING' -or -not $state.cycle.foreground) { throw 'Internal Activity change incorrectly started a timer.' }
$results.Add(@{scenario='internal Activity change'; status='passed'})
Write-Host 'PASS internal Activity change: no timer'

Reset 15
Open-Game
Adb shell am force-stop com.RoamingStar.BlueArchive | Out-Null
Start-Sleep -Seconds 4
if ((State).cycle.phase -ne 'RUNNING') { throw 'Killing the foreground game did not start a timer.' }
$results.Add(@{scenario='game process removed'; status='passed'})
Write-Host 'PASS foreground game process removal: starts timer'

Reset 20
$before = Start-Timer
Adb shell am crash cn.alice.timer | Out-Null
Wait-Reminder $before.cycle.due
$results.Add(@{scenario='timer app process crash'; status='passed'; deadline=$before.cycle.due})
Write-Host 'PASS timer app process crash: saved deadline still notifies'
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Start-Sleep -Seconds 2
}

Reset 60
$before = Start-Timer
Adb reboot | Out-Null
Start-Sleep -Seconds 4
$booted = $false
for ($i=0; $i -lt 45; $i++) {
    try { $boot = & $adbPath -P $AdbPort -s $Serial shell getprop sys.boot_completed 2>$null }
    catch { $boot = '' }
    if (($boot -join '').Trim() -eq '1') {
        Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
        Adb shell wm dismiss-keyguard | Out-Null
        $users = Adb shell dumpsys user
        if ($users -match 'State: RUNNING_UNLOCKED') { $booted = $true; break }
    }
    Start-Sleep -Seconds 2
}
if (-not $booted) { throw 'Emulator reboot timed out.' }
Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Adb shell wm dismiss-keyguard | Out-Null
Start-Sleep -Seconds 5
$restored = State
if ($restored.cycle.due -ne $before.cycle.due) { throw 'Reboot changed the deadline.' }
Wait-Reminder $before.cycle.due
$results.Add(@{scenario='device reboot'; status='passed'; deadline=$before.cycle.due})
Write-Host 'PASS reboot: restores same deadline and reminder'
$results | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $projectPath 'test-results\emulator-recovery-tests.json') -Encoding utf8
