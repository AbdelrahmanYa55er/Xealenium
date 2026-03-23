param(
    [string]$ModelName = "gte-small-onnx",
    [string]$DestinationRoot = (Join-Path $PSScriptRoot "..\models"),
    [switch]$Force
)

$ErrorActionPreference = "Stop"

$resolvedRoot = [System.IO.Path]::GetFullPath($DestinationRoot)
$targetDir = Join-Path $resolvedRoot $ModelName
$modelFile = Join-Path $targetDir "model.onnx"
$tokenizerFile = Join-Path $targetDir "tokenizer.json"

$downloads = @(
    @{
        Url = "https://huggingface.co/Qdrant/gte-small-onnx/resolve/main/model.onnx?download=true"
        Path = $modelFile
    },
    @{
        Url = "https://huggingface.co/Qdrant/gte-small-onnx/resolve/main/tokenizer.json?download=true"
        Path = $tokenizerFile
    }
)

New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

foreach ($item in $downloads) {
    if ((Test-Path $item.Path) -and -not $Force) {
        Write-Host "Skipping existing file: $($item.Path)" -ForegroundColor Yellow
        continue
    }
    Write-Host "Downloading $($item.Url)" -ForegroundColor Cyan
    Invoke-WebRequest -Uri $item.Url -OutFile $item.Path
}

Write-Host ""
Write-Host "Model files ready in: $targetDir" -ForegroundColor Green
Write-Host "Launcher scripts will auto-enable embeddings when these files are present." -ForegroundColor Green
