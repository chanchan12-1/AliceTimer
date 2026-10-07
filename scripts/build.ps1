param([string]$JavaHome = $env:JAVA_HOME, [string]$AndroidSdk = $env:ANDROID_HOME,
    [string[]]$Tasks = @(':core:test', ':app:assembleDebug', ':app:assembleRelease', ':mockgame:assembleDebug', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw 'Set JAVA_HOME or pass -JavaHome with a JDK 17+ directory.' }
if (-not $AndroidSdk) { $AndroidSdk = $env:ANDROID_SDK_ROOT }
if ($AndroidSdk -and -not (Test-Path -LiteralPath $AndroidSdk -PathType Container)) { throw 'Android SDK directory does not exist.' }
$projectPath = Split-Path -Parent $PSScriptRoot
$buildPath = $projectPath
$aliasDrive = $null
# Some Java command-line argument files cannot resolve Chinese Windows paths.
if ($projectPath -match '[^\x00-\x7F]') {
    $used = [IO.DriveInfo]::GetDrives() | ForEach-Object { $_.Name.Substring(0,1) }
    $aliasDrive = @('T','U','V','W','X','Y','Z') | Where-Object { $_ -notin $used } | Select-Object -First 1
    if (-not $aliasDrive) { throw 'No free temporary drive letter.' }
    & subst.exe ($aliasDrive + ':') $projectPath
    if ($LASTEXITCODE -ne 0) { throw 'Could not create temporary directory alias.' }
    $buildPath = $aliasDrive + ':\'
}
$previousBuildEnv = @{}
foreach ($key in @('JAVA_HOME','GRADLE_USER_HOME','ANDROID_USER_HOME','ANDROID_HOME','ANDROID_SDK_ROOT')) { $previousBuildEnv[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$env:JAVA_HOME = $JavaHome
if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $buildPath '.toolchain\gradle-home' }
if (-not $env:ANDROID_USER_HOME) { $env:ANDROID_USER_HOME = Join-Path $buildPath '.toolchain\android-user' }
if ($AndroidSdk) { $env:ANDROID_HOME = $AndroidSdk; $env:ANDROID_SDK_ROOT = $AndroidSdk }
Push-Location $buildPath
try {
    & .\gradlew.bat @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build failed. See Gradle output.' }
    New-Item -ItemType Directory -Force -Path 'dist' | Out-Null
    $unsigned = 'app\build\outputs\apk\release\app-release-unsigned.apk'
    if (Test-Path -LiteralPath $unsigned) {
        $metadata = Get-Content -LiteralPath 'app\build\outputs\apk\release\output-metadata.json' -Raw -Encoding utf8 | ConvertFrom-Json
        $releaseCopy = 'dist\AliceTimer-' + $metadata.elements[0].versionName + '-unsigned.apk'
        Copy-Item -LiteralPath $unsigned -Destination $releaseCopy
        Write-Host "Unsigned release APK: $releaseCopy. Sign it with your own key before distribution."
    }
} finally {
    & .\gradlew.bat --stop | Out-Null
    Pop-Location
    if ($aliasDrive) { & subst.exe ($aliasDrive + ':') /D }
    foreach ($key in $previousBuildEnv.Keys) { [Environment]::SetEnvironmentVariable($key, $previousBuildEnv[$key], 'Process') }
}
