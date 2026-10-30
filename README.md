# AV2 · Algoritmos de ordenação em ambientes seriais e paralelos (Java)

Estudo comparativo de desempenho de **Quick Sort, Insertion Sort, Merge Sort e Counting Sort**, cada um com
versão **serial** e **paralela**, variando tamanho e natureza da entrada, intervalo de valores, número de
threads e número de núcleos disponíveis. Os resultados são gravados em CSV, resumidos estatisticamente e
plotados (PNG para o relatório e gráficos dinâmicos em uma interface Swing).

Java 21, sem dependências externas.

## Como executar

**IntelliJ:** abra a pasta do projeto e rode `Main` → abre a interface gráfica (já carrega
`resultados/principal/medicoes.csv`, se existir).

**Linha de comando (PowerShell):**

```powershell
scripts\compilar.ps1                                   # compila em out\production\AV2-paralela
java -cp out\production\AV2-paralela Main              # interface gráfica
java -cp out\production\AV2-paralela Main verificar    # testes de corretude
java -cp out\production\AV2-paralela Main benchmark --experimento principal
java -cp out\production\AV2-paralela Main analisar resultados\principal   # refaz resumo e gráficos

# todos os experimentos do relatório (≈ 30 min; feche programas pesados e deixe o notebook na tomada)
powershell -ExecutionPolicy Bypass -File scripts\executar-experimentos.ps1
```

Opções do `benchmark` (todas opcionais, sobrescrevem o experimento escolhido):

| Opção | Exemplo | Significado |
|---|---|---|
| `--experimento` | `principal`, `intervalo`, `nucleos`, `rapido` | conjunto de parâmetros pré-definido |
| `--algoritmos` | `quick,merge` | algoritmos a medir |
| `--distribuicoes` | `aleatorio,inverso` | natureza dos dados |
| `--tamanhos` | `10k,1m,10m` | tamanhos para Quick/Merge/Counting |
| `--tamanhos-quadraticos` | `1k,50k` | tamanhos para o Insertion Sort (O(n²)) |
| `--intervalos` | `1000,1m` | intervalo de valores: dados em [0, k) |
| `--threads` | `1,2,4,8,12,16` | threads das execuções paralelas |
| `--amostras` / `--aquecimento` | `5` / `1` | amostras medidas / execuções descartadas por configuração |
| `--sem-serial`, `--anexar`, `--saida DIR` | | |

## Estrutura

```
src/
  Main.java                 linha de comando (benchmark, analisar, verificar, gui)
  sort/                     algoritmos: interface SortAlgorithm + Quick, Insertion, Merge, Counting
  bench/                    framework de teste: geração de dados, execução, estatística, CSV
  chart/                    gráficos em Java2D (PNG e tela) e montagem dos gráficos do relatório
  gui/                      interface Swing com gráficos dinâmicos
scripts/                    compilação e execução dos experimentos (PowerShell)
resultados/<experimento>/   medicoes.csv, resumo.csv, complexidade.csv, ambiente.txt, graficos/*.png
```

## Estratégias de paralelização

Todas as versões paralelas rodam em um `ForkJoinPool` com paralelismo = número de threads. O pool é criado
uma vez por quantidade de threads e reaproveitado em todas as medições.

| Algoritmo | Serial | Paralelo |
|---|---|---|
| **Quick Sort** | Hoare + mediana de três; recursão no lado menor (pilha O(log n)) | as duas partições viram tarefas Fork/Join; abaixo de 8.192 elementos ordena serialmente |
| **Merge Sort** | top-down estável, buffer auxiliar "ping-pong" | metades em paralelo **e merge paralelo** (divisão por busca binária), evitando o gargalo O(n) do merge final |
| **Insertion Sort** | clássico, O(n²) | decomposição de domínio: P blocos ordenados em paralelo + merge em árvore. Como P·(n/P)² = n²/P, o trabalho total cai — speedup pode ser superlinear |
| **Counting Sort** | estável, O(n + k) | histogramas locais por thread (sem sincronização), soma de prefixos em paralelo por faixas de valores, distribuição estável em paralelo. Custo extra O(P·k) |

## Metodologia de medição

* mesma entrada para todos (semente fixa); cada amostra ordena uma cópia nova, feita fora do tempo medido;
* aquecimento global do JIT + 1 execução descartada por configuração; 5 amostras medidas;
* `System.gc()` antes de cada amostra com n ≥ 1 milhão; heap fixo (`-Xms4g -Xmx4g`);
* toda amostra é comparada com `Arrays.sort` — resultado errado aborta o experimento;
* tempo com `System.nanoTime()`;
* os scripts iniciam a JVM com prioridade alta (`start /high`). Em processadores híbridos (núcleos P e E),
  com prioridade normal a thread serial pode cair num núcleo E e ficar até 3× mais lenta, o que infla todos
  os speedups. A primeira execução do experimento principal teve esse problema e foi guardada em
  `resultados/descartados/` (ver `LEIAME.md` lá dentro).

## Arquivos CSV

* `medicoes.csv` — uma linha por amostra:
  `experimento,algoritmo,modo,threads,nucleos,distribuicao,tamanho,intervalo_valores,amostra,tempo_ms`
* `resumo.csv` — uma linha por configuração: média, mediana, desvio padrão, CV, mín., máx., IC 95% (t de Student),
  speedup (T serial / T paralelo) e seu IC, eficiência (speedup / min(threads, núcleos)) e métrica de Karp–Flatt.
* `complexidade.csv` — expoente empírico *b* de T(n) ≈ a·nᵇ por regressão linear em escala log-log, com R².

Separador vírgula e decimal com ponto (no Excel em português use *Dados → De Texto/CSV*).
