package chart;

import bench.Analysis.Row;
import bench.Distribution;
import bench.Measurement.Mode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Monta os gráficos a partir das linhas do resumo estatístico. As linhas recebidas já devem
 * estar filtradas por experimento, intervalo de valores e núcleos, exceto nos gráficos que
 * variam justamente essas dimensões.
 */
public final class ChartFactory {

    private ChartFactory() {
    }

    public static final List<String> ALGORITHM_ORDER = List.of("QuickSort", "InsertionSort", "MergeSort", "CountingSort");

    /** A cor acompanha o algoritmo em todos os gráficos (nunca a posição). */
    public static Color algColor(String alg) {
        int i = ALGORITHM_ORDER.indexOf(alg);
        if (i < 0) i = 4 + Math.floorMod(alg.hashCode(), 4);
        return Theme.CATEGORICAL[i];
    }

    /** No máximo 5 quantidades de threads por gráfico (limite de tons distinguíveis da rampa). */
    public static List<Integer> threadSubset(Collection<Integer> available) {
        List<Integer> all = new ArrayList<>(new TreeSet<>(available));
        int max = Theme.RAMP.length;
        if (all.size() <= max) return all;
        Set<Integer> pick = new LinkedHashSet<>();
        for (int t : new int[]{2, 4, 8, 12, 16}) if (all.contains(t)) pick.add(t);
        for (int i = 0; pick.size() < max && i < all.size(); i++) {
            pick.add(all.get((int) Math.round((double) i * (all.size() - 1) / Math.max(1, max - 1))));
        }
        List<Integer> out = new ArrayList<>(pick);
        out.sort(Comparator.naturalOrder());
        return out.size() > max ? out.subList(0, max) : out;
    }

    /** Quantidade de threads "principal" para comparações: igual ao nº de núcleos, se medida. */
    public static int mainThreads(List<Row> rows) {
        TreeSet<Integer> threads = new TreeSet<>();
        int cores = 0;
        for (Row r : rows) {
            if (r.mode() == Mode.PARALELO) threads.add(r.threads());
            cores = Math.max(cores, r.cores());
        }
        if (threads.isEmpty()) return 1;
        if (threads.contains(cores)) return cores;
        Integer below = threads.floor(cores);
        return below != null ? below : threads.last();
    }

    /**
     * true se a linha pertence ao intervalo de valores pedido. A distribuição POUCOS_VALORES tem
     * intervalo fixo (100 valores distintos) e por isso acompanha qualquer intervalo escolhido.
     */
    public static boolean matchesRange(Row r, int range) {
        return r.valueRange() == range || r.distribution() == Distribution.POUCOS_VALORES;
    }

    /** Intervalo de valores mais frequente (ignorando POUCOS_VALORES). */
    public static int mainRange(List<Row> rows) {
        return rows.stream().filter(r -> r.distribution() != Distribution.POUCOS_VALORES)
                .collect(java.util.stream.Collectors.groupingBy(Row::valueRange, java.util.stream.Collectors.counting()))
                .entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey)
                .orElse(rows.isEmpty() ? 0 : rows.get(0).valueRange());
    }

    public static List<Row> where(List<Row> rows, Predicate<Row> p) {
        return rows.stream().filter(p).toList();
    }

    public static List<String> algorithms(List<Row> rows) {
        Set<String> present = new LinkedHashSet<>();
        for (Row r : rows) present.add(r.algorithm());
        List<String> out = new ArrayList<>();
        for (String a : ALGORITHM_ORDER) if (present.contains(a)) out.add(a);
        for (String a : present) if (!out.contains(a)) out.add(a);
        return out;
    }

    public static int largestSize(List<Row> rows, String alg) {
        return rows.stream().filter(r -> r.algorithm().equals(alg)).mapToInt(Row::size).max().orElse(0);
    }

    static String tip(Row r) {
        StringBuilder sb = new StringBuilder();
        sb.append(r.key().configLabel()).append(" · ").append(r.algorithm()).append('\n');
        sb.append("n = ").append(Theme.integer(r.size())).append(" · ").append(r.distribution().label())
                .append(" · intervalo ").append(Theme.compact(r.valueRange())).append('\n');
        sb.append("média ").append(Theme.num(r.mean())).append(" ms ± ").append(Theme.num(r.ci95()))
                .append(" (IC 95%)\n");
        sb.append("mediana ").append(Theme.num(r.stats().median())).append(" ms · CV ")
                .append(Theme.fixed(r.stats().cvPercent(), 1)).append("%");
        if (r.mode() == Mode.PARALELO && !Double.isNaN(r.speedup())) {
            sb.append('\n').append("speedup ").append(Theme.fixed(r.speedup(), 2)).append("× · eficiência ")
                    .append(Theme.fixed(100 * r.efficiency(), 0)).append("%");
        }
        sb.append('\n').append(r.cores()).append(" processadores lógicos");
        return sb.toString();
    }

    private static String ms(double v) {
        return Theme.num(v);
    }

    private static double[] ticks(Collection<Integer> values) {
        return values.stream().mapToDouble(Integer::doubleValue).sorted().distinct().toArray();
    }

    // ------------------------------------------------------------------ tempo × tamanho

    /** Um algoritmo: Serial + até 5 quantidades de threads, escala log-log. */
    public static LineChart timeVsSize(List<Row> rows, String alg, Distribution d) {
        List<Row> rs = where(rows, r -> r.algorithm().equals(alg) && r.distribution() == d);
        LineChart c = new LineChart();
        c.title(alg + " — tempo × tamanho da entrada")
                .subtitle("Distribuição: " + d.label().toLowerCase() + " · média de " + samples(rs) + " amostras ± IC 95% · escala log-log")
                .axes("Tamanho da entrada (n)", "Tempo (ms)");
        c.logX(true).logY(true).xFormat(Theme::compact).yFormat(ChartFactory::ms);
        c.add(series("Serial", Theme.SERIAL, where(rs, r -> r.mode() == Mode.SERIAL), Row::size));
        List<Integer> ts = threadSubset(rs.stream().filter(r -> r.mode() == Mode.PARALELO).map(Row::threads).toList());
        for (int i = 0; i < ts.size(); i++) {
            int t = ts.get(i);
            c.add(series(label(t), Theme.ramp(i, ts.size()),
                    where(rs, r -> r.mode() == Mode.PARALELO && r.threads() == t), Row::size));
        }
        return c;
    }

    /** Todos os algoritmos numa mesma configuração (serial ou paralelo com t threads). */
    public static LineChart algorithmsTimeVsSize(List<Row> rows, Mode mode, int threads, Distribution d) {
        List<Row> rs = where(rows, r -> r.distribution() == d && r.mode() == mode
                && (mode == Mode.SERIAL || r.threads() == threads));
        LineChart c = new LineChart();
        c.title("Comparação dos algoritmos — " + (mode == Mode.SERIAL ? "serial" : "paralelo com " + label(threads)))
                .subtitle("Distribuição: " + d.label().toLowerCase() + " · média ± IC 95% · escala log-log")
                .axes("Tamanho da entrada (n)", "Tempo (ms)");
        c.logX(true).logY(true).xFormat(Theme::compact).yFormat(ChartFactory::ms);
        for (String alg : algorithms(rs)) {
            c.add(series(alg, algColor(alg), where(rs, r -> r.algorithm().equals(alg)), Row::size));
        }
        return c;
    }

    // ------------------------------------------------------------------ speedup

    /** Speedup (ou eficiência) × threads, no maior tamanho de cada algoritmo (ou em {@code size}). */
    public static LineChart speedupVsThreads(List<Row> rows, List<String> algs, Distribution d, Integer size,
                                             boolean efficiency) {
        LineChart c = new LineChart();
        List<Integer> allThreads = new ArrayList<>();
        int cores = rows.stream().mapToInt(Row::cores).max().orElse(0);
        StringBuilder sizes = new StringBuilder();
        for (String alg : algs) {
            int n = size != null ? size : largestSize(where(rows, r -> r.distribution() == d), alg);
            List<Row> rs = where(rows, r -> r.algorithm().equals(alg) && r.distribution() == d && r.size() == n
                    && r.mode() == Mode.PARALELO);
            if (rs.isEmpty()) continue;
            rs.forEach(r -> allThreads.add(r.threads()));
            if (!sizes.isEmpty()) sizes.append(", ");
            sizes.append(shortName(alg)).append(" n=").append(Theme.compact(n));
            c.add(series(alg, algColor(alg), rs, Row::threads, efficiency ? r -> 100 * r.efficiency() : Row::speedup,
                    efficiency ? r -> 0.0 : ChartFactory::speedupErr));
        }
        double[] xt = ticks(allThreads);
        if (efficiency) {
            c.title("Eficiência paralela × número de threads")
                    .axes("Threads", "Eficiência (%)");
            Series ideal = Series.reference("Ideal (100%)");
            for (double t : xt) ideal.add(t, 100);
            c.add(ideal);
            c.yFormat(v -> Theme.num(v));
        } else {
            c.title("Speedup × número de threads").axes("Threads", "Speedup (T serial / T paralelo)");
            Series ideal = Series.reference("Ideal (linear)");
            for (double t : xt) ideal.add(t, Math.min(t, Math.max(cores, 1)));
            c.add(ideal);
            c.yFormat(v -> Theme.num(v) + "×");
        }
        c.subtitle("Distribuição: " + d.label().toLowerCase() + " · " + sizes + " · " + cores + " processadores lógicos");
        c.xTicks(xt).xFormat(v -> Theme.integer((long) v));
        return c;
    }

    /** Speedup de um único algoritmo, com referências linear (p) e quadrática (p²). */
    public static LineChart speedupVsThreadsWithQuadratic(List<Row> rows, String alg, Distribution d, Integer size) {
        LineChart c = speedupVsThreads(rows, List.of(alg), d, size, false);
        List<Row> rs = where(rows, r -> r.algorithm().equals(alg) && r.mode() == Mode.PARALELO && r.distribution() == d);
        Series sq = Series.referenceDotted("p² (blocos menores)");
        rs.stream().map(Row::threads).distinct().sorted().forEach(t -> sq.add(t, (double) t * t));
        c.add(sq);
        c.title(alg + " — speedup × número de threads");
        return c;
    }

    /** Speedup com t threads em função do tamanho da entrada (onde o paralelismo compensa). */
    public static LineChart speedupVsSize(List<Row> rows, List<String> algs, int threads, Distribution d) {
        LineChart c = new LineChart();
        c.title("Speedup × tamanho da entrada (" + label(threads) + ")")
                .subtitle("Distribuição: " + d.label().toLowerCase() + " · abaixo de 1× a versão paralela é mais lenta que a serial · escala log")
                .axes("Tamanho da entrada (n)", "Speedup");
        c.logX(true).logY(true).zeroY(false).xFormat(Theme::compact).yFormat(v -> Theme.num(v) + "×");
        double minN = Double.MAX_VALUE, maxN = 0;
        for (String alg : algs) {
            List<Row> rs = where(rows, r -> r.algorithm().equals(alg) && r.distribution() == d
                    && r.mode() == Mode.PARALELO && r.threads() == threads);
            for (Row r : rs) {
                minN = Math.min(minN, r.size());
                maxN = Math.max(maxN, r.size());
            }
            c.add(series(alg, algColor(alg), rs, Row::size, Row::speedup, ChartFactory::speedupErr));
        }
        if (maxN > 0) {
            Series one = Series.reference("1× (sem ganho)");
            one.add(minN, 1).add(maxN, 1);
            c.add(one);
        }
        return c;
    }

    /** Meia-largura do IC 95% do speedup (propagada na análise). */
    static double speedupErr(Row r) {
        return Double.isNaN(r.speedupCi()) ? 0 : r.speedupCi();
    }

    // ------------------------------------------------------------------ distribuições

    /** Tempo por natureza dos dados: Serial × Paralelo (t threads), um algoritmo. */
    public static BarChart timeByDistribution(List<Row> rows, String alg, int threads, Integer size) {
        int n = size != null ? size : largestSize(rows, alg);
        List<Row> rs = where(rows, r -> r.algorithm().equals(alg) && r.size() == n);
        List<Distribution> ds = rs.stream().map(Row::distribution).distinct().sorted().toList();
        BarChart c = new BarChart();
        c.categories(ds.stream().map(Distribution::label).toList());
        c.title(alg + " — tempo por natureza dos dados")
                .subtitle("n = " + Theme.integer(n) + " · média ± IC 95%")
                .axes("", "Tempo (ms)");
        c.yFormat(ChartFactory::ms);
        Series ser = new Series("Serial", Theme.SERIAL);
        Series par = new Series("Paralelo " + label(threads), Theme.RAMP[3]);
        for (int i = 0; i < ds.size(); i++) {
            Distribution d = ds.get(i);
            for (Row r : rs) {
                if (r.distribution() != d) continue;
                if (r.mode() == Mode.SERIAL) ser.add(i, r.mean(), r.ci95(), tip(r));
                else if (r.threads() == threads) par.add(i, r.mean(), r.ci95(), tip(r));
            }
        }
        c.add(ser).add(par);
        return c;
    }

    /** Speedup por natureza dos dados, uma coluna por algoritmo. */
    public static BarChart speedupByDistribution(List<Row> rows, List<String> algs, int threads) {
        List<Distribution> ds = rows.stream().map(Row::distribution).distinct().sorted().toList();
        BarChart c = new BarChart();
        c.categories(ds.stream().map(Distribution::label).toList());
        StringBuilder sizes = new StringBuilder();
        for (String alg : algs) {
            int n = largestSize(rows, alg);
            if (!sizes.isEmpty()) sizes.append(", ");
            sizes.append(shortName(alg)).append(" n=").append(Theme.compact(n));
            Series s = new Series(alg, algColor(alg));
            for (int i = 0; i < ds.size(); i++) {
                Row r = find(rows, alg, Mode.PARALELO, threads, ds.get(i), n);
                if (r != null) s.add(i, r.speedup(), speedupErr(r), tip(r));
            }
            c.add(s);
        }
        c.title("Speedup por natureza dos dados (" + label(threads) + ")")
                .subtitle(sizes.toString())
                .axes("", "Speedup");
        c.yFormat(v -> Theme.num(v) + "×");
        c.reference(1, "1×");
        return c;
    }

    // ------------------------------------------------------------------ intervalo de valores

    /** Tempo × intervalo de valores (k) para um algoritmo e tamanho fixos. */
    public static LineChart timeVsRange(List<Row> rows, String alg, int size, Distribution d) {
        List<Row> rs = where(rows, r -> r.algorithm().equals(alg) && r.size() == size && r.distribution() == d);
        LineChart c = new LineChart();
        c.title(alg + " — tempo × intervalo de valores")
                .subtitle("n = " + Theme.integer(size) + " · valores em [0, k) · média ± IC 95% · escala log-log")
                .axes("Intervalo de valores (k)", "Tempo (ms)");
        c.logX(true).logY(true).xFormat(Theme::compact).yFormat(ChartFactory::ms);
        c.add(series("Serial", Theme.SERIAL, where(rs, r -> r.mode() == Mode.SERIAL), Row::valueRange));
        List<Integer> ts = threadSubset(rs.stream().filter(r -> r.mode() == Mode.PARALELO).map(Row::threads).toList());
        for (int i = 0; i < ts.size(); i++) {
            int t = ts.get(i);
            c.add(series(label(t), Theme.ramp(i, ts.size()),
                    where(rs, r -> r.mode() == Mode.PARALELO && r.threads() == t), Row::valueRange));
        }
        return c;
    }

    public static LineChart speedupVsRange(List<Row> rows, List<String> algs, int size, int threads, Distribution d) {
        LineChart c = new LineChart();
        c.title("Speedup × intervalo de valores (" + label(threads) + ")")
                .subtitle("n = " + Theme.integer(size) + " · valores em [0, k) · escala log")
                .axes("Intervalo de valores (k)", "Speedup");
        c.logX(true).logY(false).xFormat(Theme::compact).yFormat(v -> Theme.num(v) + "×");
        double lo = Double.MAX_VALUE, hi = 0;
        for (String alg : algs) {
            List<Row> rs = where(rows, r -> r.algorithm().equals(alg) && r.size() == size && r.distribution() == d
                    && r.mode() == Mode.PARALELO && r.threads() == threads);
            for (Row r : rs) {
                lo = Math.min(lo, r.valueRange());
                hi = Math.max(hi, r.valueRange());
            }
            c.add(series(alg, algColor(alg), rs, Row::valueRange, Row::speedup, ChartFactory::speedupErr));
        }
        if (hi > 0) {
            Series one = Series.reference("1×");
            one.add(lo, 1).add(hi, 1);
            c.add(one);
        }
        return c;
    }

    // ------------------------------------------------------------------ núcleos

    /** Speedup × threads com uma linha por quantidade de núcleos disponíveis (afinidade de CPU). */
    public static LineChart speedupByCores(List<Row> rows, String alg, Distribution d, Integer size) {
        int n = size != null ? size : largestSize(where(rows, r -> r.distribution() == d), alg);
        List<Row> rs = where(rows, r -> r.algorithm().equals(alg) && r.distribution() == d && r.size() == n
                && r.mode() == Mode.PARALELO);
        List<Integer> cores = rs.stream().map(Row::cores).distinct().sorted().toList();
        LineChart c = new LineChart();
        c.title(alg + " — speedup por núcleos disponíveis")
                .subtitle("n = " + Theme.integer(n) + " · distribuição: " + d.label().toLowerCase()
                        + " · speedup relativo ao serial com os mesmos núcleos")
                .axes("Threads", "Speedup");
        List<Integer> shown = cores.size() > Theme.RAMP.length ? cores.subList(cores.size() - Theme.RAMP.length, cores.size()) : cores;
        for (int i = 0; i < shown.size(); i++) {
            int k = shown.get(i);
            c.add(series(k + (k == 1 ? " núcleo" : " núcleos"), Theme.ramp(i, shown.size()),
                    where(rs, r -> r.cores() == k), Row::threads, Row::speedup, ChartFactory::speedupErr));
        }
        c.xTicks(ticks(rs.stream().map(Row::threads).toList())).xFormat(v -> Theme.integer((long) v));
        c.yFormat(v -> Theme.num(v) + "×");
        return c;
    }

    // ------------------------------------------------------------------ auxiliares

    static Row find(List<Row> rows, String alg, Mode mode, int threads, Distribution d, int size) {
        for (Row r : rows) {
            if (r.algorithm().equals(alg) && r.mode() == mode && (mode == Mode.SERIAL || r.threads() == threads)
                    && r.distribution() == d && r.size() == size) return r;
        }
        return null;
    }

    static String label(int threads) {
        return threads + (threads == 1 ? " thread" : " threads");
    }

    static String shortName(String alg) {
        return alg.replace("Sort", "");
    }

    private static int samples(List<Row> rs) {
        return rs.stream().mapToInt(r -> r.stats().n()).max().orElse(0);
    }

    private static Series series(String name, Color color, List<Row> rows, java.util.function.ToIntFunction<Row> x) {
        return series(name, color, rows, x, Row::mean, Row::ci95);
    }

    private static Series series(String name, Color color, List<Row> rows, java.util.function.ToIntFunction<Row> x,
                                 java.util.function.ToDoubleFunction<Row> y,
                                 java.util.function.ToDoubleFunction<Row> err) {
        Series s = new Series(name, color);
        rows.stream().filter(Objects::nonNull).sorted(Comparator.comparingInt(x))
                .forEach(r -> s.add(x.applyAsInt(r), y.applyAsDouble(r), err.applyAsDouble(r), tip(r)));
        return s;
    }
}
