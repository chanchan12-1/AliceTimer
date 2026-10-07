param([string]$Serial = 'emulator-5580', [int]$AdbPort = 5042, [string]$Version = '0.1.3')
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $projectPath '.toolchain\android-sdk\platform-tools\adb.exe'
$resultPath = Join-Path $projectPath ('test-results\' + $Version)
New-Item -ItemType Directory -Force -Path $resultPath | Out-Null
function Adb {
    $output = & $adbPath -P $AdbPort -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
    return ($output -join "`n")
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
    for ($i=0; $i -lt 8; $i++) {
        $doc = Layout
        $node = $doc.SelectNodes('//node[@text!=""]') | Where-Object { $_.text -match $Pattern } | Select-Object -Last 1
        if ($node) { return $node }
        if (-not $Scroll) { break }
        Adb shell input swipe 600 990 600 350 300 | Out-Null
    }
    throw "UI text missing: $Pattern"
}
function State {
    [xml]$prefs = Adb shell cat /data/data/cn.alice.timer/shared_prefs/alice_timer_v1.xml
    return ($prefs.SelectSingleNode('/map/string[@name="data"]').InnerText | ConvertFrom-Json)
}
function Screenshot([string]$Name) {
    Adb shell screencap -p "/sdcard/$Name.png" | Out-Null
    Adb pull "/sdcard/$Name.png" (Join-Path $resultPath "$Name.png") | Out-Null
}
if ($Serial -notmatch '^emulator-\d+$' -or (Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Official emulator required.' }
if ((Adb shell dumpsys package cn.alice.timer) -match 'cn.alice.timer.debug') { throw 'Install the signed release APK first.' }
if ((Adb shell dumpsys package com.RoamingStar.BlueArchive) -notmatch 'cn.alice.mockgame.MockGameActivity') { throw 'Test shell app required.' }
Adb root | Out-Null
Start-Sleep -Seconds 2
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Start-Sleep -Seconds 2
$doc = Layout
if ($doc.SelectNodes('//node[@text!=""]') | Where-Object { $_.text -match '^\u6B22\u8FCE\u56DE\u6765\uFF0C\u8001\u5E08\uFF01$' }) {
    Screenshot 'onboarding'
    Tap (TextNode '\u8FDB\u5165\u8BA1\u65F6\u5668|\u5148\u770B\u770B' -Scroll)
}
Start-Sleep -Seconds 2
if ((State).interval -ne 10800000) { throw 'Default interval is not three hours.' }
TextNode '^\u4F5C\u8005\uFF1Abilibili w\u8749\u8749$' | Out-Null
$catalog = State
if ($catalog.lines.Count -ne 12 -or @($catalog.characters | Where-Object { $_ -notin @('aris','momoi','midori','yuzu') }).Count -gt 0) { throw 'Incorrect fresh character catalog.' }
Tap (TextNode '^\u8BBE\u7F6E$')
Screenshot 'settings'
Tap (TextNode '\u81EA\u5B9A\u4E49\u5C0F\u65F6')
$doc = Layout
$fields = @($doc.SelectNodes('//node[@class="android.widget.EditText"]'))
if ($fields.Count -ne 2) { throw 'Hours/minutes editor missing.' }
Tap $fields[0]
Adb shell input keyevent KEYCODE_MOVE_END KEYCODE_DEL | Out-Null
Adb shell input text 0 | Out-Null
$doc = Layout
$fields = @($doc.SelectNodes('//node[@class="android.widget.EditText"]'))
Tap $fields[1]
Adb shell input keyevent KEYCODE_MOVE_END KEYCODE_DEL | Out-Null
Adb shell input text 1 | Out-Null
Tap (TextNode '^\u4FDD\u5B58$')
if ((State).interval -ne 60000) { throw 'Custom interval did not save.' }
Adb shell am start -W -n com.RoamingStar.BlueArchive/cn.alice.mockgame.MockGameActivity | Out-Null
Start-Sleep -Seconds 3
Adb shell input keyevent KEYCODE_HOME | Out-Null
Start-Sleep -Seconds 3
$before = State
if ($before.cycle.phase -ne 'RUNNING') { throw 'Release automatic timer did not start.' }
Adb shell am start -W -n com.RoamingStar.BlueArchive/cn.alice.mockgame.MockGameActivity | Out-Null
Start-Sleep -Seconds 3
if ((State).cycle.due -ne $before.cycle.due) { throw 'Release reentry reset the deadline.' }
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Tap (TextNode '^\u8BA1\u65F6$')
Screenshot 'countdown'
$wait = [Math]::Max(1, [Math]::Ceiling(($before.cycle.due - [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) / 1000) + 3)
Start-Sleep -Seconds $wait
$notification = Adb shell dumpsys notification --noredact
if ($notification -notmatch 'cn.alice.timer.*id=20') { throw 'Release system notification missing.' }
$after = State
if ($after.cycle.phase -ne 'NOTIFIED' -or $after.lastReminderAt -ne $before.cycle.due) { throw 'Release reminder state is wrong.' }
Adb shell cmd statusbar expand-notifications | Out-Null
Start-Sleep -Seconds 1
Screenshot 'notification'
Adb shell cmd statusbar collapse | Out-Null
Tap (TextNode '^\u8BBE\u7F6E$')
Tap (TextNode '^3\u5C0F\u65F6$')
if ((State).interval -ne 10800000) { throw 'Three-hour preset did not save.' }
Screenshot 'settings'
Tap (TextNode '^\u53F0\u8BCD$')
TextNode '^\u6765\u81EA\u5B66\u751F\u7684\u6D88\u606F$' | Out-Null
foreach($name in @('^\u7231\u4E3D\u4E1D$','^\u6843$','^\u7EFF$','^\u67DA\u5B50$')) { TextNode $name | Out-Null }
Screenshot 'dialogues'
Tap (TextNode '^\u8BA1\u65F6$')
Screenshot 'home'
@{scenario='signed release APK'; status='passed'; defaultIntervalMs=10800000; testIntervalMs=60000; checks=@('welcome back onboarding','author credit','student wording','four corrected students','custom minutes','automatic exit timer','no reset on reentry','system character notification','three-hour preset','no debug entry points'); deadline=$before.cycle.due; apk=('dist/AliceTimer-' + $Version + '.apk')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resultPath 'emulator-release-test.json') -Encoding utf8
Write-Host 'PASS signed release: fresh start, settings, automatic timer, reentry, character notification'
