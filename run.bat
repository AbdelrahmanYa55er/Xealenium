@echo off
setlocal
set "ROOT=%~dp0"
set "ROOT_URL=%ROOT:\=/%"
set "BASELINE_URL=file:///%ROOT_URL%pages/baseline.html"
set "UPDATED_URL=file:///%ROOT_URL%pages/updated.html"
set "MODEL_DIR=%ROOT%models\gte-small-onnx"
set "EMBEDDING_ARGS=-Dvisual.embedding.enabled=true"
if exist "%MODEL_DIR%\model.onnx" if exist "%MODEL_DIR%\tokenizer.json" (
    set "EMBEDDING_ARGS=%EMBEDDING_ARGS% -Dvisual.embedding.modelDir=%MODEL_DIR% -Dvisual.embedding.modelName=gte-small"
)

echo ============================================================
echo   Visual Self-Healing Engine Runner
echo ============================================================
echo.
if defined EMBEDDING_ARGS (
echo   Embeddings: REQUESTED
if exist "%MODEL_DIR%\model.onnx" if exist "%MODEL_DIR%\tokenizer.json" (
echo   Model: gte-small ^(local model detected^)
) else (
echo   Model: not found ^(runtime will log fallback reason^)
)
)
echo.
echo   [Step 1] Running BASELINE capture...
call "%ROOT%gradlew.bat" "-DtestUrl=%BASELINE_URL%" "-Dinteractive=false" "-Dreport=false" "-Dvisual.captureBaseline.refresh=true" %EMBEDDING_ARGS% test --tests "com.demo.VisualDemoTests"
if errorlevel 1 goto :fail

echo.
echo   [Step 2] Running HEALING against updated page...
call "%ROOT%gradlew.bat" "-DtestUrl=%UPDATED_URL%" "-Dinteractive=true" "-Dreport=true" %EMBEDDING_ARGS% test --tests "com.demo.VisualDemoTests"
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
