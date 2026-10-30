package bench;

import sort.Algorithms;
import sort.SortAlgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Parâmetros de um experimento (o que variar e quantas amostras coletar). */
public final class BenchmarkConfig {

    public String experiment = "principal";
    public List<SortAlgorithm> algorithms = Algorithms.all();
    public List<Distribution> distributions = List.of(Distribution.values());
    /** Tamanhos para os algoritmos O(n log n) / O(n + k). */
    public List<Integer> sizes = List.of(1_000, 10_000, 100_000, 1_000_000, 5_000_000, 10_000_000);
    /** Tamanhos para os algoritmos O(n²) (Insertion Sort). */
    public List<Integer> quadraticSizes = List.of(1_000, 5_000, 10_000, 50_000, 100_000);
    /** Intervalos de valores: os dados ficam em [0, intervalo). */
    public List<Integer> valueRanges = List.of(1_000_000);
    /** Quantidades de threads das execuções paralelas. */
    public List<Integer> threads = List.of(1, 2, 4, 6, 8, 10, 12, 16);
    public boolean includeSerial = true;
    /** Amostras medidas por configuração. */
    public int samples = 5;
    /** Execuções descartadas antes das amostras de cada configuração (aquecimento). */
    public int warmup = 1;
    public long seed = 20261030L;

    public List<Integer> sizesFor(SortAlgorithm alg) {
        return alg.quadratic() ? quadraticSizes : sizes;
    }

    /** Número total de amostras medidas (sem contar o aquecimento). */
    public int totalRuns() {
        int configs = threads.size() + (includeSerial ? 1 : 0);
        int total = 0;
        for (SortAlgorithm alg : algorithms) {
            total += sizesFor(alg).size() * distributions.size() * valueRanges.size() * configs * samples;
        }
        return total;
    }

    /** Experimentos pré-definidos usados no relatório. */
    public static BenchmarkConfig preset(String name) {
        BenchmarkConfig c = new BenchmarkConfig();
        switch (name.toLowerCase(Locale.ROOT)) {
            case "principal" -> {
                // valores padrão acima
            }
            case "intervalo" -> {
                c.experiment = "intervalo";
                c.algorithms = Algorithms.parse("quick,merge,counting");
                c.distributions = List.of(Distribution.ALEATORIO);
                c.sizes = List.of(1_000_000, 10_000_000);
                c.valueRanges = List.of(10, 1_000, 100_000, 1_000_000, 10_000_000);
                c.threads = List.of(1, 2, 4, 8, 12, 16);
            }
            case "nucleos" -> {
                c.experiment = "nucleos";
                c.distributions = List.of(Distribution.ALEATORIO);
                c.sizes = List.of(5_000_000);
                c.quadraticSizes = List.of(50_000);
                c.threads = List.of(1, 2, 4, 8, 12, 16);
            }
            case "rapido" -> {
                c.experiment = "rapido";
                c.sizes = List.of(10_000, 100_000, 1_000_000);
                c.quadraticSizes = List.of(1_000, 10_000);
                c.threads = List.of(1, 2, 4);
                c.samples = 2;
            }
            default -> throw new IllegalArgumentException(
                    "Experimento desconhecido: " + name + " (use principal, intervalo, nucleos, rapido)");
        }
        return c;
    }

    public static List<Integer> parseInts(String csv) {
        List<Integer> list = new ArrayList<>();
        for (String part : csv.split(",")) {
            String p = part.trim().toLowerCase(Locale.ROOT).replace("_", "");
            if (p.isEmpty()) continue;
            long mult = 1;
            if (p.endsWith("k")) {
                mult = 1_000;
                p = p.substring(0, p.length() - 1);
            } else if (p.endsWith("m")) {
                mult = 1_000_000;
                p = p.substring(0, p.length() - 1);
            }
            list.add(Math.toIntExact(Long.parseLong(p) * mult));
        }
        return list;
    }

    @Override
    public String toString() {
        return "experimento=" + experiment
                + "\n  algoritmos=" + algorithms.stream().map(SortAlgorithm::name).toList()
                + "\n  distribuicoes=" + distributions
                + "\n  tamanhos=" + sizes + "  tamanhos O(n²)=" + quadraticSizes
                + "\n  intervalos=" + valueRanges
                + "\n  threads=" + threads + (includeSerial ? " + serial" : "")
                + "\n  amostras=" + samples + "  aquecimento=" + warmup + "  semente=" + seed
                + "\n  total de amostras=" + totalRuns();
    }

    public BenchmarkConfig copy() {
        BenchmarkConfig c = new BenchmarkConfig();
        c.experiment = experiment;
        c.algorithms = List.copyOf(algorithms);
        c.distributions = List.copyOf(distributions);
        c.sizes = List.copyOf(sizes);
        c.quadraticSizes = List.copyOf(quadraticSizes);
        c.valueRanges = List.copyOf(valueRanges);
        c.threads = List.copyOf(threads);
        c.includeSerial = includeSerial;
        c.samples = samples;
        c.warmup = warmup;
        c.seed = seed;
        return c;
    }

    static int[] sortedUnion(List<Integer> a, List<Integer> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream())
                .mapToInt(Integer::intValue).distinct().sorted().toArray();
    }

    static String join(int[] values) {
        return Arrays.toString(values);
    }
}
