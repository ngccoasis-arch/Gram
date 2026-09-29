[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

function Find-Command([string]$Name) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    return $null
}

$java = Find-Command 'java'
$git = Find-Command 'git'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $null }

Write-Host "Gram Android environment"
Write-Host "  Java:       $(if ($java) { $java } else { 'MISSING (JDK 17 required)' })"
Write-Host "  Android SDK:$(if ($sdk) { ' ' + $sdk } else { ' MISSING (API 36 required)' })"
Write-Host "  Git:        $(if ($git) { $git } else { 'MISSING' })"
Write-Host "  Native TDLib: CI-only (no local NDK, Docker, CMake, or Ninja required)"

if (-not $java -or -not $sdk) { Write-Error 'Install JDK 17 and Android SDK 36, then rerun this check.' }
& $java -version
if (-not (Test-Path -LiteralPath (Join-Path $sdk 'platforms\android-36'))) { Write-Error "Android platform 36 is not installed under $sdk" }
Write-Host 'Environment is ready for Gradle.' -ForegroundColor Green
