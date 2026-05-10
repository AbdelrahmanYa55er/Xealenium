@echo off
setlocal EnableExtensions EnableDelayedExpansion
title Xealenium Demo and Benchmark Launcher

set "ROOT=%~dp0"
set "GRADLE=%ROOT%gradlew.bat"
set "MODEL_PATH=%ROOT%models\gte-small-onnx\model.onnx"
set "MODEL_TOKENIZER=%ROOT%models\gte-small-onnx\tokenizer.json"

if exist "%USERPROFILE%\Programs\jdk-17\bin\java.exe" (
    set "JAVA_HOME=%USERPROFILE%\Programs\jdk-17"
)
if exist "C:\Users\user\Programs\jdk-17\bin\java.exe" (
    set "JAVA_HOME=C:\Users\user\Programs\jdk-17"
)
if defined JAVA_HOME (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
)

cd /d "%ROOT%"

:menu
cls
echo ============================================================
echo              Xealenium Demo and Benchmark Launcher
echo ============================================================
echo.
echo Choose what to run:
echo.
echo   1. Basic visual demo                 pages/baseline.html -^> pages/updated.html
echo   2. Profile demo                      pages/profile_baseline.html -^> pages/profile_updated.html
echo   3. Real profile full-cycle demo
echo   4. Automation Exercise benchmark     local drifted fixtures
echo   5. Automation Exercise refusal case
echo   6. ISTQB full E2E demo               live istqb.org + local baseline
echo   7. Custom Gradle task
echo   Q. Quit
echo.
set "CHOICE="
set /p "CHOICE=Selection: " || exit /b 0

if /i "%CHOICE%"=="Q" exit /b 0
if "%CHOICE%"=="" goto menu
if "%CHOICE%"=="1" goto basic
if "%CHOICE%"=="2" goto profile
if "%CHOICE%"=="3" goto realprofile
if "%CHOICE%"=="4" goto ae
if "%CHOICE%"=="5" goto refusal
if "%CHOICE%"=="6" goto istqb
if "%CHOICE%"=="7" goto custom

echo.
echo Unknown selection.
pause
goto menu

:common_config
echo.
echo ---------------- Configuration ----------------
call :ask_bool INTERACTIVE "Interactive healing review dialogs" "Y"
call :ask_bool REPORT "Generate HTML/JSON reports" "Y"
call :ask_bool HEADLESS "Run browser headless" "N"
call :ask_bool RERUN "Force Gradle --rerun-tasks" "Y"
set "THRESHOLD="
set /p "THRESHOLD=Visual threshold override (blank = task default): " || set "THRESHOLD="

set "COMMON_ARGS="
set "COMMON_ARGS=!COMMON_ARGS! ""-Dinteractive=%INTERACTIVE%"""
set "COMMON_ARGS=!COMMON_ARGS! ""-Dreport=%REPORT%"""
if /i "%HEADLESS%"=="true" set "COMMON_ARGS=!COMMON_ARGS! ""-Dheadless=true"""
if not "%THRESHOLD%"=="" set "COMMON_ARGS=!COMMON_ARGS! ""-Dvisual.threshold=%THRESHOLD%"""

set "GRADLE_FLAGS=--no-daemon"
if /i "%RERUN%"=="true" set "GRADLE_FLAGS=!GRADLE_FLAGS! --rerun-tasks"
exit /b 0

:embedding_config
call :ask_bool EMBEDDINGS "Enable local embedding model" "Y"
set "EMBED_ARGS="
if /i "%EMBEDDINGS%"=="true" (
    set "EMBED_ARGS=""-Dvisual.embedding.enabled=true"" ""-Dvisual.embedding.modelName=gte-small"" ""-Dvisual.embedding.modelPath=%MODEL_PATH%"" ""-Dvisual.embedding.modelFile=%MODEL_PATH%"""
    if exist "%MODEL_PATH%" if exist "%MODEL_TOKENIZER%" (
        echo Local embedding model detected: %MODEL_PATH%
    ) else (
        echo Local embedding model not found. The run may fall back or fail depending on test config.
    )
) else (
    set "EMBED_ARGS=""-Dvisual.embedding.enabled=false"""
)
exit /b 0

:ask_mode
echo.
echo Run mode:
echo   1. Full cycle: capture baseline, then run healing
echo   2. Capture baseline only
echo   3. Run healing only using existing baseline
set "MODE="
set /p "MODE=Mode (1/2/3): " || set "MODE=1"
if "%MODE%"=="" set "MODE=1"
if "%MODE%"=="1" exit /b 0
if "%MODE%"=="2" exit /b 0
if "%MODE%"=="3" exit /b 0
echo Invalid mode.
goto ask_mode

:basic
call :common_config
call :embedding_config
call :ask_mode
if /i "%EMBEDDINGS%"=="true" (
    set "CAPTURE_TASK=captureBaselineWithEmbeddings"
    set "HEAL_TASK=runHealingWithEmbeddings"
) else (
    set "CAPTURE_TASK=captureBaseline"
    set "HEAL_TASK=runHealing"
)
goto run_pair

:profile
call :common_config
call :embedding_config
call :ask_mode
if /i "%EMBEDDINGS%"=="true" (
    set "CAPTURE_TASK=profileCaptureBaseline"
    set "HEAL_TASK=profileRunHealingWithEmbeddings"
) else (
    set "CAPTURE_TASK=profileCaptureBaseline"
    set "HEAL_TASK=profileRunHealing"
)
goto run_pair

:realprofile
call :common_config
call :embedding_config
call :ask_mode
if /i "%EMBEDDINGS%"=="true" (
    set "CAPTURE_TASK=realProfileCaptureBaseline"
    set "HEAL_TASK=realProfileRunHealingWithEmbeddings"
) else (
    set "CAPTURE_TASK=realProfileCaptureBaseline"
    set "HEAL_TASK=realProfileRunHealing"
)
goto run_pair

:ae
call :common_config
call :embedding_config
call :ask_bool RECAPTURE "Capture a fresh Automation Exercise baseline before healing" "Y"
if /i "%EMBEDDINGS%"=="true" (
    set "TASK=aeCompetitionRunHealingWithEmbeddings"
    set "SKIP=-x aeCompetitionCaptureBaselineWithEmbeddings"
) else (
    set "TASK=aeCompetitionRunHealing"
    set "SKIP=-x aeCompetitionCaptureBaseline"
)
if /i "%RECAPTURE%"=="true" set "SKIP="
call :run_gradle "%TASK%" "%SKIP%" ""
goto done

:refusal
call :common_config
set "EMBEDDINGS=true"
set "EMBED_ARGS=""-Dvisual.embedding.enabled=true"" ""-Dvisual.embedding.modelName=gte-small"" ""-Dvisual.embedding.modelPath=%MODEL_PATH%"" ""-Dvisual.embedding.modelFile=%MODEL_PATH%"""
call :ask_bool RECAPTURE "Capture a fresh Automation Exercise baseline before refusal run" "Y"
set "SKIP="
if /i "%RECAPTURE%"=="false" set "SKIP=-x aeCompetitionCaptureBaselineWithEmbeddings"
call :run_gradle "aeCompetitionRunRefusalWithEmbeddings" "%SKIP%" ""
goto done

:istqb
call :common_config
call :embedding_config
call :ask_bool RECAPTURE "Capture a fresh ISTQB local baseline before live run" "Y"
set "LIVE_URL="
set /p "LIVE_URL=Live start URL (blank = https://istqb.org/): " || set "LIVE_URL="
set "ISTQB_ARGS="
if not "%LIVE_URL%"=="" set "ISTQB_ARGS=""-Distqb.live.url=%LIVE_URL%"""
set "SKIP="
if /i "%RECAPTURE%"=="false" set "SKIP=-x istqbDemoCaptureBaseline"
call :run_gradle "istqbDemoRunLiveInteractive" "%SKIP%" "-b build-demotest.gradle %ISTQB_ARGS%"
goto done

:custom
call :common_config
call :embedding_config
set "CUSTOM_TASK="
set /p "CUSTOM_TASK=Gradle task or arguments to run: " || exit /b 0
if "%CUSTOM_TASK%"=="" goto menu
call :run_gradle "%CUSTOM_TASK%" "" ""
goto done

:run_pair
if "%MODE%"=="1" (
    call :run_gradle "%CAPTURE_TASK%" "" ""
    if errorlevel 1 goto done
    call :run_gradle "%HEAL_TASK%" "" ""
    goto done
)
if "%MODE%"=="2" (
    call :run_gradle "%CAPTURE_TASK%" "" ""
    goto done
)
if "%MODE%"=="3" (
    call :run_gradle "%HEAL_TASK%" "" ""
    goto done
)
goto menu

:run_gradle
set "TASKS=%~1"
set "SKIP_ARGS=%~2"
set "EXTRA_PREFIX=%~3"
echo.
echo ============================================================
echo Running:
echo   gradlew.bat %EXTRA_PREFIX% %GRADLE_FLAGS% %COMMON_ARGS% %EMBED_ARGS% %TASKS% %SKIP_ARGS%
echo ============================================================
echo.
call "%GRADLE%" %EXTRA_PREFIX% %GRADLE_FLAGS% %COMMON_ARGS% %EMBED_ARGS% %TASKS% %SKIP_ARGS%
set "RUN_EXIT=%ERRORLEVEL%"
if not "%RUN_EXIT%"=="0" (
    echo.
    echo Run failed with exit code %RUN_EXIT%.
    exit /b %RUN_EXIT%
)
exit /b 0

:ask_bool
set "%~1=false"
set "ANSWER="
if /i "%~3"=="Y" (
    set "%~1=true"
    set /p "ANSWER=%~2? (Y/n): " || set "ANSWER="
    if /i "!ANSWER!"=="n" set "%~1=false"
) else (
    set /p "ANSWER=%~2? (y/N): " || set "ANSWER="
    if /i "!ANSWER!"=="y" set "%~1=true"
)
exit /b 0

:done
set "FINAL_EXIT=%ERRORLEVEL%"
echo.
if "%FINAL_EXIT%"=="0" (
    echo Done.
) else (
    echo Finished with errors.
)
echo.
echo Useful report locations:
echo   visual-healing-report.html
echo   test-outputs\xealenium\automation-exercise\visual-healing-report.html
echo   test-outputs\xealenium\automation-exercise-refusal\visual-healing-report.html
echo   test-outputs\xealenium\istqb-demo\visual-healing-report.html
echo.
set "AGAIN="
set /p "AGAIN=Run another demo? (y/N): " || set "AGAIN="
if /i "%AGAIN%"=="y" goto menu
exit /b %FINAL_EXIT%
