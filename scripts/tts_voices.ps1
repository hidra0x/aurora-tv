#Requires -Version 5.1
param(
    [Parameter(Mandatory = $true)]
    [string]$JobsFile
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Speech

$jobs = Get-Content $JobsFile -Raw -Encoding UTF8 | ConvertFrom-Json
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer

foreach ($job in $jobs) {
    $voice = [string]$job.voice
    if ($voice) {
        $synth.SelectVoice($voice)
    }
    $synth.Rate = [int]$job.rate
    $synth.Volume = 100
    $synth.SetOutputToWaveFile([string]$job.output)
    $synth.Speak([string]$job.text)
}

$synth.Dispose()
Write-Host "TTS done: $($jobs.Count) files"
