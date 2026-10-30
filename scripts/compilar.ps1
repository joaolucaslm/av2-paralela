# Compila o projeto em out/production/AV2-paralela (mesma pasta usada pelo IntelliJ).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "java.ps1")

$out = Join-Path $root "out\production\AV2-paralela"
New-Item -ItemType Directory -Force $out | Out-Null
$sources = Get-ChildItem -Recurse -Filter *.java (Join-Path $root "src") | ForEach-Object { $_.FullName }
& $JAVAC -encoding UTF-8 -d $out $sources
if ($LASTEXITCODE -ne 0) { throw "Falha na compilação" }
Write-Host "Compilado em $out"
