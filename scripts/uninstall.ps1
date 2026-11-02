#Requires -Version 5.1
<#
    Удаляет Aurora TV с телевизора. После удаления система вернёт штатный рабочий стол.
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$TvIp,

    [int]$Port = 5555
)

$ErrorActionPreference = 'Stop'
$dev = Join-Path $env:USERPROFILE 'android-dev'
$adb = Join-Path $dev 'sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) {
    $adb = (Get-Command adb.exe -ErrorAction SilentlyContinue).Source
}
if (-not $adb) {
    throw "Не найден adb."
}

$target = "$TvIp`:$Port"
& $adb connect $target | Out-Null
Write-Host "Удаляю ru.auroratv.launcher ..."
& $adb -s $target uninstall ru.auroratv.launcher

Write-Host "Возвращаюсь на домашний экран ..."
& $adb -s $target shell am start -a android.intent.action.MAIN -c android.intent.category.HOME | Out-Null
& $adb disconnect $target | Out-Null
Write-Host "Готово. Если открылась Aurora TV — нажмите Home или перезагрузите телевизор." -ForegroundColor Green
