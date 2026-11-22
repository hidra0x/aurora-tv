param(
  [string]$StationIp = '192.168.0.5',
  [string]$StationUser = 'root',
  [string]$SshKey = 'C:\lite-kit\work\station_rsa',
  [string]$TvIp = '192.168.0.3',
  [int]$TvPort = 8765
)

# One-time install of the Aurora TV voice bridge on the rooted rio station via SSH.
# ADB is not used: the station has root + dropbear, scripts go to /opt/bin.
# After install the daemon starts on boot without any PC.

$ErrorActionPreference = 'Stop'

$sshExe = 'C:\Windows\System32\OpenSSH\ssh.exe'
$scpExe = 'C:\Windows\System32\OpenSSH\scp.exe'
if (-not (Test-Path $sshExe)) { $sshExe = 'ssh' }
if (-not (Test-Path $scpExe)) { $scpExe = 'scp' }

$files = @(
  (Join-Path $PSScriptRoot 'aurora-voice.sh'),
  (Join-Path $PSScriptRoot 'emit.sh'),
  (Join-Path $PSScriptRoot 'install.sh')
)

foreach ($file in $files) {
  if (-not (Test-Path $file)) { throw ("File not found: " + $file) }
}

$sshArgs = @(
  '-i', $SshKey,
  '-o', 'StrictHostKeyChecking=no',
  '-o', 'BatchMode=yes',
  '-o', 'ConnectTimeout=10'
)

Write-Host ("Checking SSH to " + $StationUser + "@" + $StationIp + " ...")
& $sshExe @sshArgs "${StationUser}@${StationIp}" 'echo SSH_OK; id -u' | Write-Host
if ($LASTEXITCODE -ne 0) { throw 'SSH connection failed' }

Write-Host 'Uploading scripts to /opt/bin/ ...'
foreach ($file in $files) {
  $name = Split-Path -Leaf $file
  # scp -O forces legacy protocol (station has no sftp-server).
  & $scpExe -O @sshArgs $file "${StationUser}@${StationIp}:/opt/bin/${name}"
  if ($LASTEXITCODE -ne 0) { throw ("Upload failed: " + $name) }
}

Write-Host 'Installing daemon as root ...'
$remote = "chmod 755 /opt/bin/aurora-voice.sh /opt/bin/emit.sh /opt/bin/install.sh; " +
  "TV_IP=$TvIp TV_PORT=$TvPort sh /opt/bin/install.sh /opt/bin"
& $sshExe @sshArgs "${StationUser}@${StationIp}" $remote | Write-Host
if ($LASTEXITCODE -ne 0) { throw 'Daemon install failed' }

Write-Host ''
Write-Host 'Daemon status:'
& $sshExe @sshArgs "${StationUser}@${StationIp}" '/data/local/bin/aurora-voice status' | Write-Host
Write-Host ''
Write-Host 'Last log lines:'
& $sshExe @sshArgs "${StationUser}@${StationIp}" 'tail -20 /data/local/tmp/aurora-voice.log 2>/dev/null' | Write-Host
