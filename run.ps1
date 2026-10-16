$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not (Test-Path out\classes)) { & .\build.ps1 }
java -cp out\classes mapgame.Main
