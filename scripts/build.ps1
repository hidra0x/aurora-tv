#Requires -Version 5.1
<#
    Собирает Aurora TV.
    По умолчанию — подписанный release-APK (меньше и быстрее). Для отладки: -DebugBuild.

    Требуется JDK 17 и Android SDK 34. Пути можно задать через JAVA_HOME / ANDROID_HOME.
#>
param(
    [switch]$DebugBuild
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
if ($root -match '[^\x00-\x7F]') {
    Write-Warning "Путь проекта содержит не-ASCII символы: $root"
    Write-Warning "Android Gradle Plugin на Windows может работать некорректно. Используйте, например, C:\AuroraTV."
}

$dev = Join-Path $env:USERPROFILE 'android-dev'

if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem (Join-Path $dev 'jdk17') -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($jdk) {
        $env:JAVA_HOME = $jdk.FullName
    }
}
if (-not $env:JAVA_HOME) {
    throw "Не найден JDK 17. Установите Temurin 17 или задайте JAVA_HOME."
}

if (-not $env:ANDROID_HOME) {
    $candidate = Join-Path $dev 'sdk'
    if (Test-Path $candidate) {
        $env:ANDROID_HOME = $candidate
    }
}
if (-not $env:ANDROID_HOME) {
    throw "Не найден Android SDK. Задайте ANDROID_HOME."
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"

if (-not $DebugBuild -and -not (Test-Path (Join-Path $root 'keystore.properties'))) {
    Write-Warning "Нет keystore.properties — release-APK не будет подписан. Собираю debug."
    $DebugBuild = $true
}

$task = if ($DebugBuild) { 'assembleDebug' } else { 'assembleRelease' }
$apk = if ($DebugBuild) {
    Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
} else {
    Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
}

Write-Host "JDK:     $env:JAVA_HOME"
Write-Host "SDK:     $env:ANDROID_HOME"
Write-Host "Проект:  $root"
Write-Host "Задача:  $task"
Write-Host ""

& (Join-Path $root 'gradlew.bat') -p $root $task --console=plain
if ($LASTEXITCODE -ne 0) {
    throw "Сборка не удалась (код $LASTEXITCODE)."
}

Write-Host ""
Write-Host "Готово: $apk" -ForegroundColor Green
Write-Host "Установка: .\scripts\install.ps1 -TvIp 192.168.1.50"
