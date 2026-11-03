param(
    [switch]$Stop
)

$root = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $root "host\sunshine\sunshine.exe"

if ($Stop) {
    Get-Process sunshine -ErrorAction SilentlyContinue | Stop-Process -Force
    Write-Host "Sunshine stopped."
    exit 0
}

if (-not (Test-Path $exe)) {
    Write-Error "Sunshine not found at $exe"
    exit 1
}

Start-Process -FilePath $exe -WorkingDirectory (Split-Path $exe) -WindowStyle Hidden
Write-Host "Sunshine started."
Write-Host "Web UI: https://localhost:47990"
Write-Host "GameStream: 47989 (TCP)"
