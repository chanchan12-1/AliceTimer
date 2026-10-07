param([string]$Serial = 'emulator-5580', [int]$AdbPort = 5042)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$adbPath = Join-Path $projectPath '.toolchain\android-sdk\platform-tools\adb.exe'
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
        if ($node) {
            if ($Scroll -and $Pattern.StartsWith('^Alice') -and $node.bounds -match '\[\d+,(\d+)\]' -and [int]$Matches[1] -gt 850) {
                Adb shell input swipe 600 990 600 540 300 | Out-Null
                continue
            }
            return $node
        }
        if (-not $Scroll) { break }
        Adb shell input swipe 600 990 600 360 300 | Out-Null
    }
    throw "UI text missing: $Pattern"
}
function State {
    $output = Adb shell content query --uri content://cn.alice.timer.debug/state
    if ($output -notmatch 'json=(\{.*\})') { throw 'Debug state unavailable.' }
    return ($Matches[1] | ConvertFrom-Json)
}
if ($Serial -notmatch '^emulator-\d+$' -or (Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Official emulator required.' }
if ((Adb shell dumpsys package cn.alice.timer) -notmatch 'cn.alice.timer.debug') { throw 'Debug test build required.' }
Adb shell pm clear cn.alice.timer | Out-Null
Adb shell appops set cn.alice.timer GET_USAGE_STATS allow | Out-Null
Adb shell appops set cn.alice.timer SCHEDULE_EXACT_ALARM allow | Out-Null
Adb shell pm grant cn.alice.timer android.permission.POST_NOTIFICATIONS | Out-Null
Adb shell dumpsys deviceidle whitelist +cn.alice.timer | Out-Null
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Adb shell am broadcast -a cn.alice.timer.TEST -n cn.alice.timer/.debug.DebugReceiver --ez reset true --ei seconds 30 --es server cn | Out-Null
Adb shell am force-stop cn.alice.timer | Out-Null
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Start-Sleep -Seconds 2
$initial = State
Tap (TextNode '^\u53F0\u8BCD$')
Adb shell screencap -p /sdcard/alice-dialogues.png | Out-Null
Adb pull /sdcard/alice-dialogues.png (Join-Path $projectPath 'test-results\dialogues.png') | Out-Null
Tap (TextNode '^\u67DA\u5B50$')
Tap (TextNode '\u65B0\u589E\u53F0\u8BCD' -Scroll)
$doc = Layout
Tap ($doc.SelectSingleNode('//node[@class="android.widget.EditText"]'))
Adb shell input text Alice-UI-test | Out-Null
Adb shell input keyevent KEYCODE_BACK | Out-Null
Tap (TextNode '^\u4FDD\u5B58$')
$added = (State).lines | Where-Object text -eq 'Alice-UI-test'
if (-not $added -or (State).lines.Count -ne $initial.lines.Count + 1) { throw 'Adding dialogue failed.' }
$lineNode = TextNode '^Alice-UI-test$' -Scroll
$card = $lineNode.ParentNode
Tap ($card.SelectSingleNode('.//node[@checkable="true"]'))
if (((State).lines | Where-Object id -eq $added.id).enabled) { throw 'Disabling dialogue failed.' }
Tap ($card.SelectNodes('.//node[@text!=""]') | Where-Object { $_.text -match '^\u7F16\u8F91$' } | Select-Object -First 1)
$doc = Layout
Tap ($doc.SelectSingleNode('//node[@class="android.widget.EditText"]'))
Adb shell input keyevent KEYCODE_MOVE_END | Out-Null
Adb shell input text -edited | Out-Null
Adb shell input keyevent KEYCODE_BACK | Out-Null
Tap (TextNode '^\u4FDD\u5B58$')
$edited = (State).lines | Where-Object id -eq $added.id
if ($edited.text -ne 'Alice-UI-test-edited' -or $edited.enabled) { throw 'Editing dialogue did not preserve disabled state.' }
Adb shell am force-stop cn.alice.timer | Out-Null
Adb shell am start -W -n cn.alice.timer/.MainActivity | Out-Null
Start-Sleep -Seconds 2
if (((State).lines | Where-Object id -eq $added.id).text -ne 'Alice-UI-test-edited') { throw 'Dialogue did not persist.' }
Tap (TextNode '^\u53F0\u8BCD$')
Tap (TextNode '^\u67DA\u5B50$')
$lineNode = TextNode '^Alice-UI-test-edited$' -Scroll
Tap ($lineNode.ParentNode.SelectNodes('.//node[@text!=""]') | Where-Object { $_.text -match '^\u5220\u9664$' } | Select-Object -First 1)
Tap (TextNode '^\u5220\u9664$')
if ((State).lines.Count -ne $initial.lines.Count -or ((State).lines | Where-Object id -eq $added.id)) { throw 'Deleting dialogue failed.' }
@{scenario='dialogue add, disable, edit, process restart persistence, delete'; status='passed'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $projectPath 'test-results\emulator-ui-tests.json') -Encoding utf8
Write-Host 'PASS dialogue UI: add, disable, edit, persist, delete'
