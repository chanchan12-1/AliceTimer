param([string]$Serial = 'emulator-5580', [int]$AdbPort = 5042, [string]$Version = '0.1.3')
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $projectPath '.toolchain\android-sdk\platform-tools\adb.exe'
$resultPath = Join-Path $projectPath ('test-results\' + $Version)
$env:ANDROID_USER_HOME = Join-Path $projectPath '.toolchain\android-user'
New-Item -ItemType Directory -Force -Path $resultPath | Out-Null
function Adb {
    $output = & $adbPath -P $AdbPort -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
    return ($output -join [Environment]::NewLine)
}
function Layout {
    Adb shell uiautomator dump /sdcard/alice-ui.xml | Out-Null
    return [xml](Adb shell cat /sdcard/alice-ui.xml)
}
function Tap($Node) {
    if (-not $Node -or $Node.bounds -notmatch '\[(\d+),(\d+)\]\[(\d+),(\d+)\]') { throw 'Missing UI node.' }
    Adb shell input tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2)) | Out-Null
    Start-Sleep -Milliseconds 500
}
function TextNode([string]$Pattern, [switch]$Scroll) {
    for ($attempt=0; $attempt -lt 8; $attempt++) {
        $doc = Layout
        $node = $doc.SelectNodes('//node[@text!=""]') | Where-Object { $_.text -match $Pattern } | Select-Object -Last 1
        if ($node) { return $node }
        if (-not $Scroll) { break }
        Adb shell input swipe 600 990 600 600 250 | Out-Null
    }
    throw "Missing UI text: $Pattern"
}
function State {
    [xml]$prefs = Adb shell cat /data/data/cn.alice.timer/shared_prefs/alice_timer_v1.xml
    return ($prefs.SelectSingleNode('/map/string[@name="data"]').InnerText | ConvertFrom-Json)
}
function Screenshot([string]$Name) {
    Adb shell screencap -p "/sdcard/$Name.png" | Out-Null
    Adb pull "/sdcard/$Name.png" (Join-Path $resultPath "$Name.png") | Out-Null
}
function HomeTop {
    Tap (TextNode '^\u8BA1\u65F6$')
    1..2 | ForEach-Object { Adb shell input swipe 600 350 600 1000 200 | Out-Null }
}
function SettingsTop {
    Tap (TextNode '^\u8BBE\u7F6E$')
    1..3 | ForEach-Object { Adb shell input swipe 600 350 600 1000 200 | Out-Null }
}
function FillTime([int]$Hours, [int]$Minutes) {
    foreach ($index in 0..1) {
        $doc = Layout
        $fields = @($doc.SelectNodes('//node[@class="android.widget.EditText"]'))
        if ($fields.Count -ne 2) { throw 'Time editor must have two fields.' }
        Tap $fields[$index]
        Adb shell input keyevent KEYCODE_MOVE_END KEYCODE_DEL KEYCODE_DEL KEYCODE_DEL | Out-Null
        Adb shell input text (@($Hours,$Minutes)[$index]) | Out-Null
    }
    Adb shell input keyevent KEYCODE_BACK | Out-Null
}
function Assert-SaveEnabled([bool]$Expected) {
    $label = TextNode '^\u4FDD\u5B58$'
    $button = $label.ParentNode.SelectSingleNode('.//node[@class="android.widget.Button"]')
    $enabled = $label.ParentNode.enabled -ne 'false' -and $button.enabled -ne 'false'
    if ($enabled -ne $Expected) { throw "Expected Save enabled=$Expected, got $enabled" }
}
function Assert-Remaining($Data, [long]$ExpectedMs) {
    $remaining = $Data.cycle.due - $Data.clock.wall
    if ($Data.cycle.phase -ne 'RUNNING' -or $remaining -gt $ExpectedMs -or $remaining -lt $ExpectedMs - 10000) {
        throw "Expected running with about $ExpectedMs ms remaining, got $remaining ms / $($Data.cycle.phase)"
    }
}
function Set-Interval([int]$Minutes) {
    SettingsTop
    Tap (TextNode '\u81EA\u5B9A\u4E49\u5C0F\u65F6')
    FillTime 0 $Minutes
    Tap (TextNode '^\u4FDD\u5B58$')
    if ((State).interval -ne $Minutes * 60000) { throw 'Configured interval did not save.' }
}
function Assert-HiddenService {
    $notifications = Adb shell dumpsys notification --noredact
    $channel = [regex]::Match($notifications, 'NotificationChannel\{mId=''monitor''[^\r\n]+').Value
    $reminders = [regex]::Match($notifications, 'NotificationChannel\{mId=''character_reminders''[^\r\n]+').Value
    if ($channel -notmatch 'mImportance=0' -or $reminders -notmatch 'mImportance=4') { throw 'Monitor/reminder channel separation failed.' }
    $services = Adb shell dumpsys activity services cn.alice.timer
    if ($services -notmatch 'MonitorService' -or $services -notmatch 'isForeground=true') { throw 'Hidden monitor service is not running in foreground.' }
    return $notifications
}
function Wait-Reminder($Due) {
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds(90)
    do {
        $notifications = Adb shell dumpsys notification --noredact
        $data = State
        if ($data.cycle.phase -eq 'NOTIFIED' -and $data.lastReminderAt -eq $Due -and $notifications -match 'cn.alice.timer.*id=20') { return $data }
        Start-Sleep -Seconds 3
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw 'Character reminder did not arrive at the new deadline.'
}
if ($Serial -notmatch '^emulator-\d+$' -or (Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Official emulator required.' }
Adb install -r (Join-Path $projectPath ('dist\AliceTimer-' + $Version + '.apk')) | Out-Null
if ((Adb shell dumpsys package cn.alice.timer) -match 'cn.alice.timer.debug' -or (Adb shell dumpsys package cn.alice.timer) -notmatch ('versionName=' + [regex]::Escape($Version))) { throw 'Install the expected signed release first.' }
if ((Adb shell id) -notmatch 'uid=0') { throw 'Rootable test emulator required to read saved state.' }
$existingMock = Adb shell pm list packages com.RoamingStar.BlueArchive
if ($existingMock -match 'package:com.RoamingStar.BlueArchive' -and (Adb shell dumpsys package com.RoamingStar.BlueArchive) -notmatch 'cn.alice.mockgame.MockGameActivity') { throw 'Refusing to overwrite a real game.' }
Adb install -r (Join-Path $projectPath 'mockgame\build\outputs\apk\cn\debug\mockgame-cn-debug.apk') | Out-Null
Adb shell pm clear cn.alice.timer | Out-Null
Adb shell appops set cn.alice.timer GET_USAGE_STATS allow | Out-Null
Adb shell appops set cn.alice.timer SCHEDULE_EXACT_ALARM allow | Out-Null
Adb shell pm grant cn.alice.timer android.permission.POST_NOTIFICATIONS | Out-Null
Adb shell dumpsys deviceidle whitelist +cn.alice.timer | Out-Null
Adb shell logcat -c | Out-Null
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
$doc = Layout
if ($doc.SelectNodes('//node[@text!=""]') | Where-Object text -match '^\u6B22\u8FCE\u56DE\u6765') {
    Tap (TextNode '\u8FDB\u5165\u8BA1\u65F6\u5668|\u5148\u770B\u770B' -Scroll)
}
HomeTop
if ((State).cycle.phase -ne 'WAITING' -or (State).interval -ne 10800000) { throw 'Fresh three-hour waiting state required.' }
Tap (TextNode '^\u4ECE\u73B0\u5728\u5F00\u59CB$')
$first = State
Assert-Remaining $first 10800000
if ($first.cycle.due - $first.cycle.started -ne 10800000) { throw 'Manual start did not use a full interval.' }
Start-Sleep -Seconds 2
Tap (TextNode '^\u4ECE\u73B0\u5728\u5F00\u59CB$')
$restarted = State
if ($restarted.cycle.started -le $first.cycle.started -or $restarted.cycle.due - $restarted.cycle.started -ne 10800000) { throw 'Manual restart did not replace the deadline.' }
$counter = (TextNode '^\d{2}:\d{2}:\d{2}$').text.Split(':')
if ([int]$counter[0] * 3600 + [int]$counter[1] * 60 + [int]$counter[2] -gt 10800) { throw 'Displayed countdown exceeds the configured interval.' }
Screenshot 'manual-start'
Write-Host 'PASS manual start and restart at full configured interval'

Tap (TextNode '^\u4FEE\u6539\u5269\u4F59\u65F6\u95F4$')
FillTime 1 30
Assert-SaveEnabled $true
Screenshot 'remaining-editor'
Tap (TextNode '^\u4FDD\u5B58$')
$edited = State
Assert-Remaining $edited 5400000
if ($edited.cycle.started -ne $restarted.cycle.started) { throw 'Editing remaining time changed the original start.' }
Tap (TextNode '^\u4FEE\u6539\u5269\u4F59\u65F6\u95F4$')
FillTime 3 1
Assert-SaveEnabled $false
TextNode '\u4E0D\u80FD\u8D85\u8FC7' | Out-Null
Screenshot 'remaining-over-limit'
if ((State).cycle.due -ne $edited.cycle.due) { throw 'Invalid input changed the countdown.' }
FillTime 0 0
Assert-SaveEnabled $false
FillTime 0 60
Assert-SaveEnabled $false
FillTime 3 0
Assert-SaveEnabled $true
Tap (TextNode '^\u4FDD\u5B58$')
$atMaximum = State
Assert-Remaining $atMaximum 10800000
Adb shell am force-stop cn.alice.timer | Out-Null
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Start-Sleep -Seconds 2
if ((State).cycle.due -ne $atMaximum.cycle.due) { throw 'Edited deadline did not survive app restart.' }
Write-Host 'PASS hours/minutes edit, upper bound, zero and invalid minutes, saved deadline recovery'

SettingsTop
Tap (TextNode '^2\u5C0F\u65F6$')
if ((State).cycle.due -ne $atMaximum.cycle.due) { throw 'Preset changed the active timer without an edit.' }
HomeTop
Tap (TextNode '^\u4FEE\u6539\u5269\u4F59\u65F6\u95F4$')
FillTime 3 0
Assert-SaveEnabled $false
FillTime 2 0
Assert-SaveEnabled $true
Tap (TextNode '^\u4FDD\u5B58$')
Assert-Remaining (State) 7200000
Write-Host 'PASS remaining-time limit follows the latest configured interval'

SettingsTop
Tap (TextNode '^\u663E\u793A / \u9690\u85CF$' -Scroll)
$channelLayout = Layout
if (-not ($channelLayout.SelectNodes('//node[@text!=""]') | Where-Object text -match '^\u540E\u53F0\u76D1\u6D4B$')) { throw 'Did not open the monitor channel settings.' }
$channelSwitch = $channelLayout.SelectNodes('//node[@checkable="true"]') | Select-Object -First 1
if (-not $channelSwitch -or $channelSwitch.checked -ne 'true') { throw 'Monitor channel was not initially enabled.' }
Tap $channelSwitch
Screenshot 'system-monitor-hidden'
Adb shell input keyevent KEYCODE_BACK | Out-Null
TextNode '^\u5DF2\u9690\u85CF$' -Scroll | Out-Null
TextNode '^\u8DF3\u8F6C\u540E\u5173\u95ED\u901A\u77E5\u5373\u53EF\u9690\u85CF\u5E38\u9A7B\u901A\u77E5$' | Out-Null
Screenshot 'notification-hidden-settings'
Assert-HiddenService | Set-Content -LiteralPath (Join-Path $resultPath 'hidden-channel-before.txt') -Encoding utf8
Write-Host 'PASS system channel hidden, app status refreshed, foreground service still running'

Set-Interval 2
HomeTop
Tap (TextNode '^\u4ECE\u73B0\u5728\u5F00\u59CB$')
$twoMinutes = State
Assert-Remaining $twoMinutes 120000
Tap (TextNode '^\u4FEE\u6539\u5269\u4F59\u65F6\u95F4$')
FillTime 0 1
Tap (TextNode '^\u4FDD\u5B58$')
$oneMinute = State
Assert-Remaining $oneMinute 60000
if ($oneMinute.cycle.due -ge $twoMinutes.cycle.due) { throw 'Shortening did not replace the deadline.' }
Screenshot 'edited-countdown'
Adb shell am start -W -n com.RoamingStar.BlueArchive/cn.alice.mockgame.MockGameActivity | Out-Null
Start-Sleep -Seconds 3
if ((State).cycle.due -ne $oneMinute.cycle.due) { throw 'Game reentry reset the edited deadline.' }
Adb shell input keyevent KEYCODE_HOME | Out-Null
Start-Sleep -Seconds 3
if ((State).cycle.due -ne $oneMinute.cycle.due) { throw 'Game exit reset the edited deadline.' }
Adb shell input keyevent KEYCODE_SLEEP | Out-Null
$manualReminder = Wait-Reminder $oneMinute.cycle.due
Assert-HiddenService | Set-Content -LiteralPath (Join-Path $resultPath 'hidden-manual-reminder.txt') -Encoding utf8
Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Adb shell wm dismiss-keyguard | Out-Null
Start-Sleep -Seconds 1
Adb shell cmd statusbar expand-notifications | Out-Null
Start-Sleep -Seconds 1
Screenshot 'hidden-manual-notification'
Adb shell cmd statusbar collapse | Out-Null
Write-Host 'PASS edited deadline delivers character reminder while locked with monitor notification hidden'

Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Set-Interval 1
Adb shell am start -W -n com.RoamingStar.BlueArchive/cn.alice.mockgame.MockGameActivity | Out-Null
Start-Sleep -Seconds 3
if ((State).cycle.phase -ne 'WAITING' -or -not (State).cycle.foreground) { throw 'Next game entry did not rearm the cycle.' }
Adb shell input keyevent KEYCODE_HOME | Out-Null
Start-Sleep -Seconds 4
$automatic = State
Assert-Remaining $automatic 60000
if ($automatic.cycle.due - $automatic.cycle.started -ne 60000) { throw 'Hidden monitor did not automatically start a full interval.' }
Adb shell am start -W -n com.RoamingStar.BlueArchive/cn.alice.mockgame.MockGameActivity | Out-Null
Start-Sleep -Seconds 3
if ((State).cycle.due -ne $automatic.cycle.due) { throw 'Automatic countdown reset on reentry.' }
Adb shell input keyevent KEYCODE_HOME | Out-Null
Adb shell input keyevent KEYCODE_SLEEP | Out-Null
$automaticReminder = Wait-Reminder $automatic.cycle.due
Assert-HiddenService | Set-Content -LiteralPath (Join-Path $resultPath 'hidden-automatic-reminder.txt') -Encoding utf8
Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Adb shell wm dismiss-keyguard | Out-Null
Start-Sleep -Seconds 1
Adb shell cmd statusbar expand-notifications | Out-Null
Start-Sleep -Seconds 1
Screenshot 'hidden-automatic-notification'
$shade = Layout
if (-not ($shade.SelectNodes('//node[@text!=""]') | Where-Object { $_.text -match '\u8001\u5E08\uFF0C\u6478\u5934\u65F6\u95F4\u5230\u4E86' })) { throw 'Character reminder is missing from the notification shade.' }
if ($shade.SelectNodes('//node[@text!=""]') | Where-Object { $_.text -match '^\u7231\u4E3D\u4E1D\u8BA1\u65F6\u5668 \u00B7 \u56FD\u670D' }) { throw 'Ongoing monitor notification is still visible.' }
Adb shell cmd statusbar collapse | Out-Null
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
SettingsTop
Tap (TextNode '^3\u5C0F\u65F6$')
HomeTop
Tap (TextNode '^\u4ECE\u73B0\u5728\u5F00\u59CB$')
Screenshot 'final-home'
Write-Host 'PASS hidden monitoring automatically starts next cycle and delivers another reminder while locked'
$results = [ordered]@{
    version=$Version; apkSha256=(Get-FileHash -LiteralPath (Join-Path $projectPath ('dist\AliceTimer-' + $Version + '.apk')) -Algorithm SHA256).Hash.ToLowerInvariant();
    status='passed'; signedRelease=$true; androidApi=35;
    manualStart=$first.cycle; manualRestart=$restarted.cycle;
    edited90Minutes=$edited.cycle; maxIntervalEdit=$atMaximum.cycle;
    validation=@('over configured interval rejected','zero rejected','minutes above 59 rejected','equal maximum accepted','latest configured maximum enforced');
    savedDeadlineSurvivedRestart=$true; monitorNotificationHidden=$true; monitorServiceForeground=$true;
    shortenedDeadline=$oneMinute.cycle; manualReminderAt=$manualReminder.lastReminderAt;
    automaticCycle=$automatic.cycle; automaticReminderAt=$automaticReminder.lastReminderAt;
    reentryPreservedDeadlines=$true; remindersReceivedWhileLocked=$true;
    realGameInstalled=$false; finalConfiguredIntervalMs=(State).interval;
}
$results | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $resultPath 'emulator-timer-controls-test.json') -Encoding utf8
Write-Host 'PASS all new timer controls and hidden monitor notification checks'
