$host.UI.RawUI.WindowTitle = "Visual Healing Engine Launcher"
Clear-Host
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   Visual Healing Engine Configuration" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$visible = Read-Host "Run test visibly on screen? (y/N)"
if ($visible -match "^[yY]") { $v = "true" } else { $v = "false" }

$interactive = Read-Host "Enable Interactive Mode (Confirm each heal visually)? (y/N)"
if ($interactive -match "^[yY]") { $i = "true" } else { $i = "false" }

$report = Read-Host "Generate HTML Report after run? (Y/n)"
if ($report -match "^[nN]") { $r = "false" } else { $r = "true" }

Write-Host "
Launching Engine...
" -ForegroundColor Green
.\gradlew.bat test --tests "com.demo.VisualDemoTests" -DtestUrl=http://localhost:8080/v3_failing.html -Dvisible=$v -Dinteractive=$i -Dreport=$r

Write-Host "
Test Execution Complete!" -ForegroundColor Green
if ($r -eq "true") {
    Write-Host "Report saved to visual-healing-report.html" -ForegroundColor Yellow
}
pause
