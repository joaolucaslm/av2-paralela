# Experimento de núcleos: roda o benchmark com a JVM presa (afinidade de CPU) a 1, 2, 4, 8 e
# todos os processadores lógicos. A JVM lê a máscara de afinidade ao iniciar, então
# Runtime.availableProcessors() e a coluna "nucleos" do CSV refletem a restrição.
# Máscaras: os N primeiros processadores lógicos (0x1, 0x3, 0xF, 0xFF, ...). No i5-13420H os
# processadores 0-7 são os 4 núcleos P com Hyper-Threading e 8-11 são os 4 núcleos E.
param([int[]]$Nucleos = @())
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. (Join-Path $PSScriptRoot "java.ps1")
$cp = "out\production\AV2-paralela"

$total = [Environment]::ProcessorCount
if ($Nucleos.Count -eq 0) {
    $Nucleos = @(1, 2, 4, 8, $total) | Where-Object { $_ -le $total } | Select-Object -Unique
}
$csv = "resultados\nucleos\medicoes.csv"
if (Test-Path $csv) { Remove-Item $csv }

foreach ($n in $Nucleos) {
    $mask = [Convert]::ToString(([long]1 -shl $n) - 1, 16).ToUpper()
    Write-Host "== $n processadores lógicos (máscara 0x$mask) =="
    Invoke-Java @("-cp", $cp, "Main", "benchmark", "--experimento", "nucleos", "--anexar") $mask
}
