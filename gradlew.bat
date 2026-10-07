@echo off
setlocal
set DIR=%~dp0
if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)
if not exist "%DIR%gradle\wrapper\gradle-wrapper.jar" (
  echo Missing Gradle wrapper. Run powershell -File scripts\bootstrap-wrapper.ps1
  exit /b 1
)
"%JAVA_EXE%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
