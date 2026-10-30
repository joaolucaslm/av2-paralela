# Executa todos os experimentos do relatório:
#   1. verificação de corretude
#   2. principal  - 4 algoritmos x 5 distribuições x vários tamanhos x serial + 1..16 threads
#   3. intervalo  - efeito do intervalo de valores k (relevante para o Counting Sort)
#   4. nucleos    - mesma bateria com a JVM restrita a 1, 2, 4, 8 e 12 processadores lógicos
#
# Uso: powershell -ExecutionPolicy Bypass -File scripts\executar-experimentos.ps1 [-Pular principal,intervalo]
# Feche outros programas pesados (navegador, Docker, Teams...) e deixe o notebook na tomada.
param([string[]]$Pular = @())
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
. (Join-Path $PSScriptRoot "java.ps1")
& (Join-Path $PSScriptRoot "compilar.ps1")
$cp = "out\production\AV2-paralela"

Invoke-Java @("-cp", $cp, "Main", "verificar")

if ($Pular -notcontains "principal") {
    Invoke-Java @("-cp", $cp, "Main", "benchmark", "--experimento", "principal")
}
if ($Pular -notcontains "intervalo") {
    Invoke-Java @("-cp", $cp, "Main", "benchmark", "--experimento", "intervalo")
}
if ($Pular -notcontains "nucleos") {
    & (Join-Path $PSScriptRoot "experimento-nucleos.ps1")
}
