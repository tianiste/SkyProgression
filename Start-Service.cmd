@echo off
setlocal
cd /d "%~dp0"
title SkyProgression Service
if not defined JAVA_HOME if exist "%APPDATA%\PrismLauncher\java\java-runtime-epsilon\bin\java.exe" set "JAVA_HOME=%APPDATA%\PrismLauncher\java\java-runtime-epsilon"
if exist "%~dp0service\bin\service.bat" (
    call "%~dp0service\bin\service.bat"
    goto finished
)
if exist "%~dp0service\build\install\service\bin\service.bat" (
    call "%~dp0service\build\install\service\bin\service.bat"
    goto finished
)
echo Service files are missing. Extract the entire test-pack ZIP first.
echo For a source checkout, build with gradlew.bat :service:installDist first.
:finished
echo.
echo The service has stopped. Any startup error is shown above.
pause
endlocal
