$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (Test-Path out\classes) { Remove-Item out\classes -Recurse -Force }
New-Item -ItemType Directory -Force out\classes | Out-Null
$sources = Get-ChildItem src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d out\classes $sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (Test-Path res) { Copy-Item res\* out\classes -Recurse -Force }
Write-Host "Build OK -> out\classes"
