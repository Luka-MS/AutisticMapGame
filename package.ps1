param(
    [Parameter(Mandatory = $true)]
    [string]$Version,
    [string]$Vendor = "Luka-MS"
)

$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

$appName = "AutisticMapGame-Version-$Version"

Write-Host "==> Building classes"
& .\build.ps1
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Packaging runnable jar"
$jarDir = "out\jar"
if (Test-Path $jarDir) { Remove-Item $jarDir -Recurse -Force }
New-Item -ItemType Directory -Force $jarDir | Out-Null
$jarPath = Join-Path $jarDir "app.jar"
jar --create --file $jarPath --main-class mapgame.Main -C out\classes .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Running jpackage"
$distDir = "out\dist"
if (Test-Path $distDir) { Remove-Item $distDir -Recurse -Force }
New-Item -ItemType Directory -Force $distDir | Out-Null

jpackage `
    --type app-image `
    --input $jarDir `
    --name $appName `
    --main-jar app.jar `
    --main-class mapgame.Main `
    --icon assets\icon.ico `
    --dest $distDir `
    --app-version $Version `
    --vendor $Vendor
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Zipping release asset"
$appDir = Join-Path $distDir $appName
$zipPath = Join-Path $distDir "$appName-win-x64.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
Compress-Archive -Path $appDir -DestinationPath $zipPath

Write-Host "Release zip -> $zipPath"
