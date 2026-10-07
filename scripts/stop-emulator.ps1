$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$sessionFile = Join-Path $projectPath '.toolchain\emulator-session.json'
if (-not (Test-Path -LiteralPath $sessionFile)) { Write-Host 'No emulator session created by the startup script.'; exit 0 }
$sessionData = Get-Content -LiteralPath $sessionFile -Raw -Encoding utf8 | ConvertFrom-Json
if ($sessionData.projectPath -ne $projectPath) { throw 'Emulator session belongs to a different project.' }
$adbPath = Join-Path $projectPath '.toolchain\android-sdk\platform-tools\adb.exe'
$serial = 'emulator-' + $sessionData.emulatorPort
$childProcesses = @(Get-CimInstance Win32_Process | Where-Object {
    $_.ParentProcessId -eq $sessionData.pid -and $_.Name -match '^qemu-system'
} | ForEach-Object { Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue })
$deviceList = & $adbPath -P $sessionData.adbPort devices
if (($deviceList -join "`n") -match ($serial + '\s+device')) {
    $name = & $adbPath -P $sessionData.adbPort -s $serial emu avd name
    if (($name -join "`n") -notmatch '^AliceTimerTest') { throw 'The device is not the project test emulator.' }
    & $adbPath -P $sessionData.adbPort -s $serial emu kill
}
$process = Get-Process -Id $sessionData.pid -ErrorAction SilentlyContinue
if ($process -and -not $process.WaitForExit(30000)) { throw 'Emulator is still stopping; keep its alias and retry shortly.' }
foreach ($childProcess in $childProcesses) {
    if (-not $childProcess.WaitForExit(30000)) { throw 'QEMU is still stopping; keep its alias and retry shortly.' }
}
& $adbPath -P $sessionData.adbPort kill-server
if ($sessionData.aliasDrive) {
    $aliasSession = $sessionData.aliasDrive + ':\.toolchain\emulator-session.json'
    if (-not (Test-Path -LiteralPath $aliasSession) -or (Get-FileHash -LiteralPath $aliasSession).Hash -ne (Get-FileHash -LiteralPath $sessionFile).Hash) { throw 'Temporary drive does not point to this emulator session.' }
    & subst.exe ($sessionData.aliasDrive + ':') /D
    if ($LASTEXITCODE -ne 0) { throw 'Temporary drive removal failed.' }
}
Remove-Item -LiteralPath $sessionFile
Write-Host 'Project emulator and isolated ADB stopped; temporary path alias removed.'
