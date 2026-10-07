$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
$wrapperPath = Join-Path $projectPath 'gradle\wrapper\gradle-wrapper.jar'
Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar' -OutFile $wrapperPath
Write-Host "Gradle wrapper saved to $wrapperPath"
