param([int]$AdbPort = 5042, [int]$EmulatorPort = 5580, [switch]$Foreground, [ValidateSet('auto','off')][string]$Acceleration = 'auto')
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$physicalToolchain = Join-Path $projectPath '.toolchain'
$sessionFile = Join-Path $physicalToolchain 'emulator-session.json'
if (Test-Path -LiteralPath $sessionFile) { throw 'Run scripts/stop-emulator.ps1 before starting another emulator session.' }
$runtimePath = $projectPath
$aliasDrive = $null
# QEMU's BIOS loader on Windows cannot reliably resolve a Chinese installation path.
# SUBST is only a path alias: all SDK and AVD files remain in the original directory.
if ($projectPath -match '[^\x00-\x7F]') {
    $used = [IO.DriveInfo]::GetDrives() | ForEach-Object { $_.Name.Substring(0,1) }
    $aliasDrive = @('T','U','V','W','X','Y','Z') | Where-Object { $_ -notin $used } | Select-Object -First 1
    if (-not $aliasDrive) { throw 'No free temporary drive letter.' }
    & subst.exe ($aliasDrive + ':') $projectPath
    if ($LASTEXITCODE -ne 0) { throw 'Could not create temporary directory alias.' }
    $runtimePath = $aliasDrive + ':\'
}
$toolchainPath = Join-Path $runtimePath '.toolchain'
$sdkPath = Join-Path $toolchainPath 'android-sdk'
$env:ANDROID_HOME = $sdkPath
$env:ANDROID_SDK_ROOT = $sdkPath
$env:ANDROID_USER_HOME = Join-Path $toolchainPath 'android-user'
$env:ANDROID_EMULATOR_HOME = Join-Path $toolchainPath 'emulator-user'
$env:ANDROID_AVD_HOME = Join-Path $toolchainPath 'avd'
$env:ANDROID_ADB_SERVER_PORT = "$AdbPort"
$env:ADB_SERVER_SOCKET = "tcp:127.0.0.1:$AdbPort"
$env:TEMP = Join-Path $toolchainPath 'temp'
$env:TMP = $env:TEMP
$avdPath = Join-Path $env:ANDROID_AVD_HOME 'AliceTimerTest.avd'
foreach ($folder in @($env:ANDROID_USER_HOME, $env:ANDROID_EMULATOR_HOME, $env:ANDROID_AVD_HOME, $avdPath, $env:TEMP)) {
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
}
$imagePath = Join-Path $sdkPath 'system-images\android-35\default\x86_64'
if (-not (Test-Path -LiteralPath (Join-Path $imagePath 'system.img'))) { throw 'Android 15 x86_64 system image is missing.' }
$ini = "avd.ini.encoding=UTF-8`npath=$($avdPath.Replace('\','/'))`ntarget=android-35`n"
[System.IO.File]::WriteAllText((Join-Path $env:ANDROID_AVD_HOME 'AliceTimerTest.ini'), $ini, [System.Text.UTF8Encoding]::new($false))
$config = @"
avd.ini.encoding=UTF-8
AvdId=AliceTimerTest
avd.ini.displayname=Alice Timer Test
abi.type=x86_64
hw.cpu.arch=x86_64
hw.cpu.ncore=2
hw.ramSize=2048
hw.lcd.width=720
hw.lcd.height=1280
hw.lcd.density=240
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=swiftshader
hw.gps=no
hw.audioInput=no
disk.dataPartition.size=2G
image.sysdir.1=$($imagePath.Replace('\','/'))/
tag.id=default
tag.display=Default
showDeviceFrame=no
fastboot.forceColdBoot=yes
"@
[System.IO.File]::WriteAllText((Join-Path $avdPath 'config.ini'), $config, [System.Text.UTF8Encoding]::new($false))
$adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
& $adbPath -P $AdbPort start-server
if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated ADB server.' }
$emulatorPath = Join-Path $sdkPath 'emulator\emulator.exe'
$emulatorArgs = @('-avd','AliceTimerTest','-port',"$EmulatorPort",'-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader','-camera-back','none','-camera-front','none','-no-metrics','-accel',$Acceleration,'-feature','-Vulkan')
if ($Foreground) {
    try { & $emulatorPath @emulatorArgs }
    finally { if ($aliasDrive) { & subst.exe ($aliasDrive + ':') /D } }
    exit $LASTEXITCODE
}
try {
    $process = Start-Process -FilePath $emulatorPath -ArgumentList $emulatorArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $toolchainPath 'emulator.log') -RedirectStandardError (Join-Path $toolchainPath 'emulator.err')
    @{projectPath=$projectPath; aliasDrive=$aliasDrive; pid=$process.Id; adbPort=$AdbPort; emulatorPort=$EmulatorPort} | ConvertTo-Json | Set-Content -LiteralPath $sessionFile -Encoding utf8
} catch {
    if ($aliasDrive) { & subst.exe ($aliasDrive + ':') /D }
    throw
}
Write-Host "Official Android Emulator PID: $($process.Id); device: emulator-$EmulatorPort; ADB port: $AdbPort"
Write-Host "All emulator files and virtual-device data are in $physicalToolchain"
Write-Host 'Use scripts/stop-emulator.ps1 to close this emulator and remove its temporary path alias.'
