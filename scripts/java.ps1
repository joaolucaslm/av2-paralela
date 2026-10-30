# Localiza o JDK: usa JAVA_HOME se definido, senão o JDK baixado pelo IntelliJ (~/.jdks), senão o PATH.
$jdkHome = $env:JAVA_HOME
if (-not $jdkHome) {
    $jdk = Get-ChildItem (Join-Path $env:USERPROFILE ".jdks") -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending | Select-Object -First 1
    if ($jdk) { $jdkHome = $jdk.FullName }
}
if ($jdkHome) {
    $JAVA = Join-Path $jdkHome "bin\java.exe"
    $JAVAC = Join-Path $jdkHome "bin\javac.exe"
} else {
    $JAVA = "java"
    $JAVAC = "javac"
}
# Heap fixo evita redimensionamentos durante as medições.
$JVM_OPTS = @("-Xms4g", "-Xmx4g", "-Dstdout.encoding=UTF-8")

# Executa a JVM com prioridade alta (menos interferência de outros programas e menor chance de o
# Windows tratar o processo como "de fundo" e mandá-lo para os núcleos de eficiência).
# $Affinity (hexadecimal, opcional) restringe o processo a um conjunto de processadores lógicos.
function Invoke-Java([string[]]$JavaArgs, [string]$Affinity = "") {
    $quoted = (@($JVM_OPTS) + $JavaArgs | ForEach-Object { '"' + $_ + '"' }) -join " "
    $aff = if ($Affinity) { "/affinity $Affinity" } else { "" }
    cmd /c "start `"`" /high $aff /wait /b `"$JAVA`" $quoted"
    if ($LASTEXITCODE -ne 0) { throw "A JVM terminou com código $LASTEXITCODE" }
}
