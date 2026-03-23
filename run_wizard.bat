@echo off
setlocal
title Visual Healing Engine Launcher
cls
set "ROOT=%~dp0"
set "ROOT_URL=%ROOT:\=/%"
set "BASELINE_URL=file:///%ROOT_URL%pages/baseline.html"
set "UPDATED_URL=file:///%ROOT_URL%pages/updated.html"

echo ==========================================
echo    Visual Healing Engine Configuration
echo ==========================================
echo.

set i=false
set /p interactive="Enable Interactive Mode (Confirm each heal visually)? (y/N): "
if /i "%interactive%"=="y" set i=true

set r=true
set /p report="Generate HTML Report after run? (Y/n): "
if /i "%report%"=="n" set r=false

set refresh=true
set /p rebuild="Rebuild baseline JSON before healing? (Y/n): "
if /i "%rebuild%"=="n" set refresh=false

echo.
echo [Step 1/2] Running baseline capture...
if /i "%refresh%"=="true" (
    call "%ROOT%gradlew.bat" "-DtestUrl=%BASELINE_URL%" "-Dinteractive=false" "-Dreport=false" "-Dvisual.captureBaseline.refresh=true" test --tests "com.demo.VisualDemoTests"
) else (
    call "%ROOT%gradlew.bat" "-DtestUrl=%BASELINE_URL%" "-Dinteractive=false" "-Dreport=false" test --tests "com.demo.VisualDemoTests"
)
if errorlevel 1 goto :fail

echo.
echo [Step 2/2] Running healing on updated page...
call "%ROOT%gradlew.bat" "-DtestUrl=%UPDATED_URL%" "-Dinteractive=%i%" "-Dreport=%r%" test --tests "com.demo.VisualDemoTests"
if errorlevel 1 goto :fail

echo.
echo Test Execution Complete!
if "%r%"=="true" (
    echo Report saved to visual-healing-report.html
)
pause
exit /b 0

:fail
echo.
echo Launcher failed. Review the Gradle output above.
pause
exit /b 1
