Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$composeFile = Join-Path $repoRoot "healenium/docker-compose.yml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not installed or not on PATH. Install Docker Desktop, start it, then run this script again."
}

Push-Location $repoRoot
try {
    docker compose -f $composeFile up -d
    Write-Host ""
    Write-Host "Healenium services are starting."
    Write-Host "Backend report: http://localhost:7878/healenium/report"
    Write-Host "Selector imitator docs: http://localhost:8000/docs"
    Write-Host ""
    Write-Host "Check status with:"
    Write-Host "  docker compose -f healenium/docker-compose.yml ps"
} finally {
    Pop-Location
}
