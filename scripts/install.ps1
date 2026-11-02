#Requires -Version 5.1
<#
    Устанавливает Aurora TV на телевизор по ADB и назначает домашним экраном.

    Пример:
      .\scripts\install.ps1 -TvIp 192.168.1.50
      .\scripts\install.ps1 -TvIp 192.168.1.50 -Build
      .\scripts\install.ps1 -TvIp 192.168.1.50 -DebugBuild

    На телевизоре должен быть включён отладчик по сети (Developer options -> Network debugging)
    либо выполнено `adb tcpip 5555` по USB.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$TvIp,

    [int]$Port = 5555,

    [switch]$Build,

    [switch]$DebugBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dev = Join-Path $env:USERPROFILE 'android-dev'

$apk = if ($DebugBuild) {
    Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
} else {
    Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
}

$adb = Join-Path $dev 'sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
    $adb = (Get-Command adb.exe -ErrorAction SilentlyContinue).Source
}
if (-not $adb) {
    throw "Не найден adb. Установите platform-tools или задайте ANDROID_HOME."
}

if ($Build -or -not (Test-Path $apk)) {
    & (Join-Path $PSScriptRoot 'build.ps1') -DebugBuild:$DebugBuild
    if ($LASTEXITCODE -ne 0) { throw "Сборка не удалась." }
}

if (-not (Test-Path $apk)) {
    $fallback = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    if (Test-Path $fallback) {
        Write-Warning "Release APK не найден, использую debug."
        $apk = $fallback
    } else {
        throw "APK не найден. Запустите .\scripts\build.ps1"
    }
}

$target = "$TvIp`:$Port"
Write-Host "Подключаюсь к $target ..."
& $adb connect $target
Start-Sleep -Seconds 1

$devices = & $adb devices
if (-not ($devices -match [regex]::Escape($target))) {
    throw "Телевизор не появился в adb. Проверьте IP, порт 5555 и 'Network debugging' на ТВ."
}

$model = (& $adb -s $target shell getprop ro.product.model) -join '' | ForEach-Object { $_.Trim() }
$android = (& $adb -s $target shell getprop ro.build.version.release) -join '' | ForEach-Object { $_.Trim() }
Write-Host "Устройство: $model, Android $android"

Write-Host "Устанавливаю APK ..."
& $adb -s $target install -r -d $apk
if ($LASTEXITCODE -ne 0) {
    throw "adb install завершился с ошибкой (код $LASTEXITCODE)."
}

Write-Host "Выдаю доступ к медиатеке ..."
& $adb -s $target shell pm grant ru.auroratv.launcher android.permission.READ_EXTERNAL_STORAGE 2>$null

Write-Host "Назначаю Aurora TV домашним экраном ..."
& $adb -s $target shell cmd package set-home-activity --user 0 ru.auroratv.launcher/.MainActivity 2>&1 | Out-Null
$resolved = & $adb -s $target shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>&1
if ($resolved -match "ru.auroratv.launcher") {
    Write-Host "Домашний экран: Aurora TV" -ForegroundColor Green
} else {
    Write-Warning "Не удалось назначить лончер автоматически. Нажмите Home и выберите Aurora TV."
}

Write-Host "Запускаю оболочку ..."
& $adb -s $target shell am start -a android.intent.action.MAIN -c android.intent.category.HOME 2>$null | Out-Null

Write-Host ""
Write-Host "Готово. Нажмите Home на пульте — должна открыться Aurora TV." -ForegroundColor Green
Write-Host "Если система спросит лончер, выберите Aurora TV и 'Всегда'."
Write-Host "Откат: .\scripts\uninstall.ps1 -TvIp $TvIp"
