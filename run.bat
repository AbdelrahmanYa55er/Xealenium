@echo off
echo ============================================================
echo   Visual Self-Healing Engine Runner
echo ============================================================
echo.
echo   [Step 1] Running BASELINE capture...
call .\gradlew.bat test --tests "com.demo.VisualDemoTests" -DtestUrl="file:///c:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/pages/baseline.html" -Dinteractive=false -Dreport=false

echo.
echo   [Step 2] Running HEALING against updated page...
call .\gradlew.bat test --tests "com.demo.VisualDemoTests" -DtestUrl="file:///c:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/pages/updated.html" -Dinteractive=true -Dreport=true

echo.
echo ============================================================
echo   DONE! Check visual-healing-report.html
echo ============================================================
pause
