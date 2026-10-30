# Execução descartada

`principal_execucao1/` é a primeira execução do experimento principal (30/10/2026, 17:12–17:19).
Ela foi descartada porque as versões seriais rodaram bem mais lentas que o normal: por exemplo,
Insertion Sort com 50 mil elementos levou ~800 ms, contra ~285 ms nas execuções seguintes, e o
mesmo código executado numa thread do pool levou ~1,2 s contra ~3,2 s na thread principal com
100 mil elementos.

Um teste com afinidade de CPU mostrou que esses valores correspondem a um núcleo de eficiência
(E) do i5-13420H, ou a um núcleo com frequência reduzida pela carga sustentada. Como o speedup é
calculado em relação ao tempo serial, isso inflaria todos os speedups. O experimento foi refeito
com a JVM iniciada em prioridade alta (`scripts/java.ps1`).
