import bench.Analysis;
import bench.BenchmarkConfig;
import bench.BenchmarkRunner;
import bench.CsvIO;
import bench.Distribution;
import bench.Measurement;
import bench.Verification;
import chart.ReportCharts;
import chart.Theme;
import gui.Dashboard;
import sort.Algorithms;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ponto de entrada.
 * <pre>
 *   Main                         abre a interface gráfica (gráficos dinâmicos)
 *   Main gui [medicoes.csv]      abre a interface gráfica carregando um CSV
 *   Main verificar               testes de corretude
 *   Main benchmark [opções]      executa um experimento e grava CSV + gráficos
 *   Main analisar &lt;pasta&gt;        recalcula resumo, complexidade e gráficos de uma pasta
 * </pre>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.forLanguageTag("pt-BR"));
        String cmd = args.length == 0 ? "gui" : args[0].toLowerCase(Locale.ROOT);
        switch (cmd) {
            case "gui" -> {
                if (GraphicsEnvironment.isHeadless()) {
                    usage();
                    return;
                }
                Dashboard.open(args.length > 1 ? Path.of(args[1]) : null);
            }
            case "verificar" -> System.exit(Verification.run(System.out) ? 0 : 1);
            case "benchmark" -> benchmark(options(args));
            case "analisar" -> {
                if (args.length < 2) {
                    usage();
                    return;
                }
                analyze(Path.of(args[1]));
            }
            default -> usage();
        }
    }

    private static void usage() {
        System.out.println("""
                Uso:
                  java -cp out Main                       abre a interface gráfica
                  java -cp out Main gui [medicoes.csv]    abre a interface já com um CSV carregado
                  java -cp out Main verificar             testes de corretude
                  java -cp out Main benchmark [opções]    executa um experimento
                  java -cp out Main analisar <pasta>      recalcula resumo.csv, complexidade.csv e gráficos

                Opções do benchmark:
                  --experimento principal|intervalo|nucleos|rapido   (padrão: principal)
                  --saida DIR                  pasta base dos resultados (padrão: resultados)
                  --algoritmos quick,insertion,merge,counting
                  --distribuicoes aleatorio,ordenado,inverso,quase_ordenado,poucos_valores
                  --tamanhos 1k,10k,100k,1m    tamanhos para Quick/Merge/Counting
                  --tamanhos-quadraticos 1k,10k  tamanhos para Insertion Sort
                  --intervalos 1m              intervalos de valores [0, k)
                  --threads 1,2,4,8            threads das execuções paralelas
                  --amostras 5                 amostras medidas por configuração
                  --aquecimento 1              execuções descartadas por configuração
                  --sem-serial                 não executa a versão serial
                  --anexar                     acrescenta ao medicoes.csv existente
                """);
    }

    private static Map<String, String> options(String[] args) {
        Map<String, String> o = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) throw new IllegalArgumentException("Opção inválida: " + a);
            String key = a.substring(2);
            if (key.equals("sem-serial") || key.equals("anexar")) {
                o.put(key, "true");
            } else {
                if (i + 1 >= args.length) throw new IllegalArgumentException("Falta o valor de " + a);
                o.put(key, args[++i]);
            }
        }
        return o;
    }

    private static void benchmark(Map<String, String> o) throws IOException {
        BenchmarkConfig cfg = BenchmarkConfig.preset(o.getOrDefault("experimento", "principal"));
        if (o.containsKey("algoritmos")) cfg.algorithms = Algorithms.parse(o.get("algoritmos"));
        if (o.containsKey("distribuicoes")) {
            cfg.distributions = java.util.Arrays.stream(o.get("distribuicoes").split(","))
                    .filter(s -> !s.isBlank()).map(Distribution::parse).toList();
        }
        if (o.containsKey("tamanhos")) cfg.sizes = BenchmarkConfig.parseInts(o.get("tamanhos"));
        if (o.containsKey("tamanhos-quadraticos")) cfg.quadraticSizes = BenchmarkConfig.parseInts(o.get("tamanhos-quadraticos"));
        if (o.containsKey("intervalos")) cfg.valueRanges = BenchmarkConfig.parseInts(o.get("intervalos"));
        if (o.containsKey("threads")) cfg.threads = BenchmarkConfig.parseInts(o.get("threads"));
        if (o.containsKey("amostras")) cfg.samples = Integer.parseInt(o.get("amostras"));
        if (o.containsKey("aquecimento")) cfg.warmup = Integer.parseInt(o.get("aquecimento"));
        if (o.containsKey("sem-serial")) cfg.includeSerial = false;
        boolean append = o.containsKey("anexar");

        Path dir = Path.of(o.getOrDefault("saida", "resultados"), cfg.experiment);
        Path csv = dir.resolve("medicoes.csv");
        Files.createDirectories(dir);
        writeEnvironment(dir.resolve("ambiente.txt"), cfg, append);

        System.out.println("== Benchmark ==\n  " + cfg);
        long start = System.nanoTime();
        try (CsvIO.MeasurementWriter writer = new CsvIO.MeasurementWriter(csv, append)) {
            BenchmarkRunner runner = new BenchmarkRunner(cfg, new BenchmarkRunner.Listener() {
                @Override
                public void onMeasurement(Measurement m, int done, int total) {
                    writer.write(m);
                    if (m.sample() == cfg.samples) {
                        double elapsed = (System.nanoTime() - start) / 1e9;
                        System.out.printf("  [%5.1f%% | %6.0fs] %-13s %-20s %-15s n=%,11d k=%,11d  %10.3f ms%n",
                                100.0 * done / total, elapsed, m.algorithm(), m.configLabel(),
                                m.distribution().name(), m.size(), m.valueRange(), m.timeMs());
                    }
                }

                @Override
                public void onLog(String message) {
                    System.out.println(message);
                }
            });
            runner.run();
        }
        System.out.printf("Concluído em %.1f min. Medições em %s%n", (System.nanoTime() - start) / 60e9, csv);
        analyze(dir);
    }

    private static void analyze(Path dir) throws IOException {
        Path csv = dir.resolve("medicoes.csv");
        List<Measurement> ms = CsvIO.readMeasurements(csv);
        List<Analysis.Row> rows = Analysis.summarize(ms);
        CsvIO.writeSummary(dir.resolve("resumo.csv"), rows);
        List<Analysis.Complexity> cx = Analysis.complexity(rows);
        CsvIO.writeComplexity(dir.resolve("complexidade.csv"), cx);
        System.setProperty("java.awt.headless", "true");
        List<Path> pngs = ReportCharts.exportAll(dir.resolve("graficos"), rows);
        System.out.printf("%d medições -> %d configurações. Gerados: resumo.csv, complexidade.csv e %d gráficos em %s%n",
                ms.size(), rows.size(), pngs.size(), dir.resolve("graficos"));
        printHighlights(rows);
    }

    /** Destaques no console: maior speedup de cada algoritmo no maior tamanho com dados aleatórios. */
    private static void printHighlights(List<Analysis.Row> rows) {
        System.out.println("\nDestaques (dados aleatórios, maior tamanho de cada algoritmo):");
        for (String alg : chart.ChartFactory.algorithms(rows)) {
            List<Analysis.Row> rs = rows.stream().filter(r -> r.algorithm().equals(alg)
                    && r.distribution() == Distribution.ALEATORIO).toList();
            if (rs.isEmpty()) continue;
            int n = rs.stream().mapToInt(Analysis.Row::size).max().orElse(0);
            int cores = rs.stream().mapToInt(Analysis.Row::cores).max().orElse(0);
            int range = rs.stream().filter(r -> r.size() == n).mapToInt(Analysis.Row::valueRange).max().orElse(0);
            Analysis.Row serial = rs.stream().filter(r -> r.size() == n && r.cores() == cores && r.valueRange() == range
                    && r.mode() == Measurement.Mode.SERIAL).findFirst().orElse(null);
            Analysis.Row best = rs.stream().filter(r -> r.size() == n && r.cores() == cores && r.valueRange() == range
                            && r.mode() == Measurement.Mode.PARALELO)
                    .max(java.util.Comparator.comparingDouble(Analysis.Row::speedup)).orElse(null);
            if (serial == null || best == null) continue;
            System.out.printf("  %-13s n=%-12s serial %s ms | melhor paralelo %s ms com %d threads | speedup %s×%n",
                    alg, Theme.integer(n), Theme.num(serial.mean()), Theme.num(best.mean()), best.threads(),
                    Theme.fixed(best.speedup(), 2));
        }
    }

    private static void writeEnvironment(Path file, BenchmarkConfig cfg, boolean append) throws IOException {
        Runtime rt = Runtime.getRuntime();
        String text = "== " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " ==\n"
                + "SO: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " ("
                + System.getProperty("os.arch") + ")\n"
                + "Processador: " + System.getenv().getOrDefault("PROCESSOR_IDENTIFIER", "?") + "\n"
                + "Processadores lógicos disponíveis para a JVM: " + rt.availableProcessors() + "\n"
                + "Java: " + System.getProperty("java.vm.name") + " " + System.getProperty("java.version") + "\n"
                + "Heap máximo: " + rt.maxMemory() / (1024 * 1024) + " MB\n"
                + "Argumentos da JVM: " + ManagementFactory.getRuntimeMXBean().getInputArguments() + "\n"
                + "Configuração:\n  " + cfg + "\n\n";
        Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
}
