param([string]$Serial = 'emulator-5580', [int]$AdbPort = 5042)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $projectPath '.toolchain\android-sdk\platform-tools\adb.exe'
$resultPath = Join-Path $projectPath 'test-results\0.1.1'
New-Item -ItemType Directory -Force -Path $resultPath | Out-Null
function Adb {
    $output = & $adbPath -P $AdbPort -s $Serial @args
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $args" }
    return ($output -join "`n")
}
function State {
    [xml]$prefs = Adb shell cat /data/data/cn.alice.timer/shared_prefs/alice_timer_v1.xml
    return ($prefs.SelectSingleNode('/map/string[@name="data"]').InnerText | ConvertFrom-Json)
}
if ($Serial -notmatch '^emulator-\d+$' -or (Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Official emulator required.' }
if ((Adb shell dumpsys package cn.alice.timer) -notmatch 'versionName=0.1.0') { throw 'The emulator must have the old release build installed.' }
if ((Adb shell dumpsys package com.RoamingStar.BlueArchive) -notmatch 'cn.alice.mockgame.MockGameActivity') { throw 'Test shell app required.' }
Adb root | Out-Null
Start-Sleep -Seconds 2
Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Adb shell wm dismiss-keyguard | Out-Null
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Start-Sleep -Seconds 2
Adb shell am start -W -n com.RoamingStar.BlueArchive/cn.alice.mockgame.MockGameActivity | Out-Null
Start-Sleep -Seconds 3
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Start-Sleep -Seconds 4
$before = State
if ($before.cycle.phase -ne 'RUNNING' -or $before.interval -ne 10800000) { throw 'Old build must have a running three-hour timer.' }
Adb shell am force-stop cn.alice.timer | Out-Null
[xml]$fixture = Adb shell cat /data/data/cn.alice.timer/shared_prefs/alice_timer_v1.xml
$configNode = $fixture.SelectSingleNode('/map/string[@name="data"]')
$config = $configNode.InnerText | ConvertFrom-Json
$config.lines = @($config.lines | Where-Object id -ne 'yuzu-1')
($config.lines | Where-Object id -eq 'hibiki-0').text = 'custom-upgrade-message'
($config.lines | Where-Object id -eq 'hibiki-1').enabled = $false
$config.lines += [pscustomobject]@{id='user-upgrade-line'; character='momoi'; text='custom-added-message'; enabled=$true}
$configNode.InnerText = $config | ConvertTo-Json -Depth 15 -Compress
$fixtureFile = Join-Path $resultPath 'upgrade-input.xml'
$fixture.Save($fixtureFile)
Adb push $fixtureFile /sdcard/alice-upgrade-input.xml | Out-Null
Adb shell cp /sdcard/alice-upgrade-input.xml /data/data/cn.alice.timer/shared_prefs/alice_timer_v1.xml | Out-Null
Adb install -r (Join-Path $projectPath 'dist\AliceTimer-0.1.1.apk') | Out-Null
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Start-Sleep -Seconds 4
$after = State
if ($after.catalogVersion -ne 2 -or $after.cycle.due -ne $before.cycle.due -or $after.interval -ne $before.interval) { throw 'Upgrade reset timer or failed to migrate.' }
if (@($after.lines | Where-Object character -eq 'hibiki').Count -gt 0 -or $after.characters -contains 'hibiki' -or $after.characters -notcontains 'aris') { throw 'Old character remained selectable.' }
if (($after.lines | Where-Object id -eq 'aris-0').text -ne 'custom-upgrade-message') { throw 'Edited reminder was lost.' }
if (($after.lines | Where-Object id -eq 'aris-1').enabled) { throw 'Disabled reminder was enabled.' }
if ($after.lines | Where-Object id -eq 'yuzu-1') { throw 'Deleted reminder was restored.' }
if (($after.lines | Where-Object id -eq 'user-upgrade-line').text -ne 'custom-added-message') { throw 'Added reminder was lost.' }
if (($after.lines | Where-Object id -eq 'momoi-0').text -notmatch '\u8001\u5E08\u8001\u5E08') { throw 'Unedited shipped reminder was not updated.' }
$after | ConvertTo-Json -Depth 15 | Set-Content -LiteralPath (Join-Path $resultPath 'upgrade-output.json') -Encoding utf8
@{scenario='0.1.0 to 0.1.1 release upgrade'; status='passed'; preservedDeadline=$after.cycle.due; checks=@('deadline and interval','character pool','updated default text','edited text','disabled text','deleted text','added text')} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $resultPath 'emulator-upgrade-test.json') -Encoding utf8
Write-Host 'PASS release upgrade: timer, custom edits and choices preserved; default students updated'
