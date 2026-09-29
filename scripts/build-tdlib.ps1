[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$versionFile = Join-Path $PSScriptRoot '..\gradle\tdlib.versions.properties'
$properties = @{}
Get-Content -LiteralPath $versionFile | ForEach-Object {
    if ($_ -match '^([^#][^=]*)=(.*)$') { $properties[$matches[1]] = $matches[2] }
}

Write-Host 'Gram does not support local TDLib native compilation.' -ForegroundColor Yellow
Write-Host "Pinned official commit: $($properties.TDLIB_COMMIT)"
Write-Host "Configured ABIs:       $($properties.TDLIB_ANDROID_ABIS)"
Write-Host "Run the GitHub Actions workflow: Build pinned TDLib"
Write-Host 'It produces the reusable checked AAR and a TDLib-backed debug APK without requiring local JDK/SDK/NDK/Docker/CMake/Ninja.'
