Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$composeFile = Join-Path $repoRoot "healenium/docker-compose.yml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not installed or not on PATH. Install Docker Desktop before using this script."
}

Push-Location $repoRoot
try {
    docker compose -f $composeFile down
    Write-Host "Healenium services stopped."
} finally {
    Pop-Location
}
