param(
  [string]$AliceIp = '192.168.0.2',
  [int]$AliceAdbPort = 5555,
  [string]$TvIp = '192.168.0.3',
  [int]$TvPort = 8765
)

# Разовая установка голосового моста на рутированную Алису с ПК.
# После установки демон работает на самой станции и стартует при включении.

$ErrorActionPreference = 'Stop'
$adb = Join-Path $env:USERPROFILE 'android-dev\sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { $adb = 'adb' }

& $adb connect "${AliceIp}:${AliceAdbPort}"
& $adb devices

& $adb push 'voice\aurora-voice.sh' /data/local/tmp/aurora-voice.sh
& $adb push 'voice\emit.sh' /data/local/tmp/aurora-voice-emit.sh
& $adb push 'voice\install.sh' /data/local/tmp/aurora-voice-install.sh
& $adb shell "chmod 755 /data/local/tmp/aurora-voice.sh /data/local/tmp/aurora-voice-emit.sh /data/local/tmp/aurora-voice-install.sh"
& $adb shell "su -c 'TV_IP=$TvIp TV_PORT=$TvPort sh /data/local/tmp/aurora-voice-install.sh /data/local/tmp'"

Write-Host ''
Write-Host 'Проверка состояния:'
& $adb shell "su -c '/data/local/bin/aurora-voice status'"
