@echo off
setlocal
title Visual Healing Engine Launcher
cls
echo ==========================================
echo    Visual Healing Engine Configuration
echo ==========================================
echo.

set v=false
set /p visible="Run test visibly on screen? (y/N): "
if /i "%visible%"=="y" set v=true

set i=false
set /p interactive="Enable Interactive Mode (Confirm each heal visually)? (y/N): "
if /i "%interactive%"=="y" set i=true

set r=true
set /p report="Generate HTML Report after run? (Y/n): "
if /i "%report%"=="n" set r=false

echo.
echo Launching Engine...
echo.
call gradlew.bat test --tests "com.demo.VisualDemoTests" -DtestUrl=http://localhost:8080/v3_failing.html -Dvisible=%v% -Dinteractive=%i% -Dreport=%r%

echo.
echo Test Execution Complete!
if "%r%"=="true" (
    echo Report saved to visual-healing-report.html
)
pause
