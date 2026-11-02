#Requires -Version 5.1
<#
    Включает always-on VPN для ByeByeDPI через ADB root.
    Только Android 7 (API 24) и только на своём устройстве.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$TvIp,

    [int]$Port = 5555,

    [string]$VpnPackage = "io.github.romanvht.byedpi"
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

$sdk = (& $adb -s $target shell getprop ro.build.version.sdk) -join ""
if ($sdk.Trim() -ne "24") {
    throw "Скрипт проверен только на Android 7 (API 24). Сейчас API $($sdk.Trim())."
}

Write-Host "Включаю root-shell ..."
& $adb -s $target root
Start-Sleep -Seconds 3
& $adb -s $target wait-for-device

$id = (& $adb -s $target shell id) -join ""
if ($id -notmatch "uid=0") {
    throw "adb root не дал uid=0. Проверьте, что прошивка userdebug и подтвердите отладку на ТВ."
}

Write-Host "Авторизую $VpnPackage как VPN ..."
& $adb -s $target shell service call connectivity 40 s16 $VpnPackage i32 0 i32 1

Write-Host "Включаю always-on VPN ..."
& $adb -s $target shell service call connectivity 47 i32 0 s16 $VpnPackage i32 0

$always = (& $adb -s $target shell settings get secure always_on_vpn_app) -join ""
Write-Host "always_on_vpn_app = $($always.Trim())"

if ($always.Trim() -ne $VpnPackage) {
    throw "Настройка не применилась. Проверьте ByeByeDPI и права root."
}

Write-Host ""
Write-Host "Готово. Перезагрузите ТВ и проверьте:" -ForegroundColor Green
Write-Host "  dumpsys connectivity | grep tun0"
Write-Host "В SmartTube stable войдите в аккаунт по QR/коду."
