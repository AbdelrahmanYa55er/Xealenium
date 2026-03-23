@echo off
setlocal
set "ROOT=%~dp0"
set "ROOT_URL=%ROOT:\=/%"
set "BASELINE_URL=file:///%ROOT_URL%pages/baseline.html"
set "UPDATED_URL=file:///%ROOT_URL%pages/updated.html"

echo ============================================================
echo   Visual Self-Healing Engine Runner
echo ============================================================
echo.
echo   [Step 1] Running BASELINE capture...
call "%ROOT%gradlew.bat" "-DtestUrl=%BASELINE_URL%" "-Dinteractive=false" "-Dreport=false" "-Dvisual.captureBaseline.refresh=true" test --tests "com.demo.VisualDemoTests"
if errorlevel 1 goto :fail

echo.
echo   [Step 2] Running HEALING against updated page...
call "%ROOT%gradlew.bat" "-DtestUrl=%UPDATED_URL%" "-Dinteractive=true" "-Dreport=true" test --tests "com.demo.VisualDemoTests"
if errorlevel 1 goto :fail

echo.
echo ============================================================
echo   DONE! Check visual-healing-report.html
echo ============================================================
pause
exit /b 0

:fail
echo.
echo ============================================================
echo   RUN FAILED
echo ============================================================
pause
exit /b 1
