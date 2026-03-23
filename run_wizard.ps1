$host.UI.RawUI.WindowTitle = "Visual Healing Engine Launcher"
Clear-Host
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   Visual Healing Engine Configuration" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$root = $PSScriptRoot
$rootUrl = ($root -replace "\\", "/").TrimEnd("/")
$baselineUrl = "file:///$rootUrl/pages/baseline.html"
$updatedUrl = "file:///$rootUrl/pages/updated.html"

$interactive = Read-Host "Enable Interactive Mode (Confirm each heal visually)? (y/N)"
if ($interactive -match "^[yY]") { $i = "true" } else { $i = "false" }

$report = Read-Host "Generate HTML Report after run? (Y/n)"
if ($report -match "^[nN]") { $r = "false" } else { $r = "true" }

$rebuild = Read-Host "Rebuild baseline JSON before healing? (Y/n)"
if ($rebuild -match "^[nN]") { $refresh = "false" } else { $refresh = "true" }

Write-Host ""
Write-Host "[Step 1/2] Running baseline capture..." -ForegroundColor Green
if ($refresh -eq "true") {
    & "$root\gradlew.bat" "-DtestUrl=$baselineUrl" "-Dinteractive=false" "-Dreport=false" "-Dvisual.captureBaseline.refresh=true" test --tests "com.demo.VisualDemoTests"
} else {
    & "$root\gradlew.bat" "-DtestUrl=$baselineUrl" "-Dinteractive=false" "-Dreport=false" test --tests "com.demo.VisualDemoTests"
}
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Baseline step failed." -ForegroundColor Red
    pause
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "[Step 2/2] Running healing on updated page..." -ForegroundColor Green
& "$root\gradlew.bat" "-DtestUrl=$updatedUrl" "-Dinteractive=$i" "-Dreport=$r" test --tests "com.demo.VisualDemoTests"
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "Healing step failed." -ForegroundColor Red
    pause
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Test Execution Complete!" -ForegroundColor Green
if ($r -eq "true") {
    Write-Host "Report saved to visual-healing-report.html" -ForegroundColor Yellow
}
pause