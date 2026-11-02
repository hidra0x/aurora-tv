#Requires -Version 5.1
<#
    Отключает always-on VPN ByeByeDPI. Сами настройки приложения не удаляются.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$TvIp,

    [int]$Port = 5555
)

$ErrorActionPreference = "Stop"
$dev = Join-Path $env:USERPROFILE "android-dev"
$adb = Join-Path $dev "sdk\platform-tools\adb.exe"
if (-not (Test-Path $adb)) {
    $adb = (Get-Command adb.exe -ErrorAction SilentlyContinue).Source
}
if (-not $adb) {
    throw "Не найден adb."
}

$target = "$TvIp`:$Port"
& $adb connect $target | Out-Null
Start-Sleep -Seconds 1

& $adb -s $target shell settings delete secure always_on_vpn_app | Out-Null
& $adb -s $target shell settings put secure always_on_vpn_lockdown 0 | Out-Null

Write-Host "Always-on VPN отключён." -ForegroundColor Green
Write-Host "Перезагрузите ТВ, чтобы он перестал запускать VPN автоматически."
