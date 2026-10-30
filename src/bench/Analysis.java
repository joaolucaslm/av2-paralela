package bench;

import bench.Measurement.Mode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Análise estatística das medições: agrupa as amostras de cada configuração e calcula média,
 * mediana, desvio padrão, coeficiente de variação, intervalo de confiança de 95% (t de Student),
 * speedup, eficiência e a métrica de Karp–Flatt (fração serial estimada experimentalmente).
 * Também estima o expoente empírico de complexidade b em T(n) ≈ a·n^b por regressão linear
 * em escala log-log.
 */
public final class Analysis {

    private Analysis() {
    }

    /** Chave de uma configuração (tudo menos o número da amostra). */
    public record Key(String experiment, String algorithm, Mode mode, int threads, int cores,
                      Distribution distribution, int size, int valueRange) {

        Key serialBaseline() {
            return new Key(experiment, algorithm, Mode.SERIAL, 1, cores, distribution, size, valueRange);
        }

        public String configLabel() {
            return Measurement.configLabel(mode, threads);
        }
    }

    /**
     * @param speedupCi meia-largura aproximada do IC 95% do speedup, propagada dos ICs relativos
     *                  dos tempos serial e paralelo: S·sqrt((ic_s/T_s)² + (ic_p/T_p)²)
     */
    public record Row(Key key, Stats.Summary stats, double speedup, double speedupCi, double efficiency,
                      double karpFlatt) {
        public String algorithm() { return key.algorithm(); }
        public Mode mode() { return key.mode(); }
        public int threads() { return key.threads(); }
        public int cores() { return key.cores(); }
        public Distribution distribution() { return key.distribution(); }
        public int size() { return key.size(); }
        public int valueRange() { return key.valueRange(); }
        public double mean() { return stats.mean(); }
        public double ci95() { return stats.ci95(); }
    }

    public static List<Row> summarize(List<Measurement> measurements) {
        Map<Key, List<Double>> groups = new LinkedHashMap<>();
        for (Measurement m : measurements) {
            Key k = new Key(m.experiment(), m.algorithm(), m.mode(), m.threads(), m.cores(),
                    m.distribution(), m.size(), m.valueRange());
            groups.computeIfAbsent(k, x -> new ArrayList<>()).add(m.timeMs());
        }
        Map<Key, Stats.Summary> stats = new HashMap<>();
        for (var e : groups.entrySet()) {
            stats.put(e.getKey(), Stats.summarize(e.getValue().stream().mapToDouble(Double::doubleValue).toArray()));
        }
        List<Row> rows = new ArrayList<>();
        for (var e : groups.keySet()) {
            Key k = e;
            Stats.Summary s = stats.get(k);
            double speedup = Double.NaN, speedupCi = Double.NaN, eff = Double.NaN, kf = Double.NaN;
            Stats.Summary base = stats.get(k.serialBaseline());
            if (base != null && s.mean() > 0 && base.mean() > 0) {
                speedup = base.mean() / s.mean();
                double relS = base.ci95() / base.mean();
                double relP = s.ci95() / s.mean();
                speedupCi = k.mode() == Mode.SERIAL ? 0 : speedup * Math.sqrt(relS * relS + relP * relP);
                int p = k.mode() == Mode.SERIAL ? 1 : Math.min(k.threads(), k.cores());
                eff = speedup / p;
                if (p > 1) {
                    kf = (1.0 / speedup - 1.0 / p) / (1.0 - 1.0 / p);
                }
            }
            rows.add(new Row(k, s, speedup, speedupCi, eff, kf));
        }
        rows.sort(ROW_ORDER);
        return rows;
    }

    public static final Comparator<Row> ROW_ORDER = Comparator
            .comparing((Row r) -> r.key().experiment())
            .thenComparing(r -> r.key().algorithm())
            .thenComparing(r -> r.key().distribution())
            .thenComparingInt(r -> r.key().valueRange())
            .thenComparingInt(r -> r.key().size())
            .thenComparingInt(r -> r.key().cores())
            .thenComparing(r -> r.key().mode())
            .thenComparingInt(r -> r.key().threads());

    /** Expoente empírico de crescimento por configuração. */
    public record Complexity(String experiment, String algorithm, Mode mode, int threads, int cores,
                             Distribution distribution, int valueRange, int minSize, int maxSize,
                             Stats.Regression regression) {
        public String configLabel() {
            return Measurement.configLabel(mode, threads);
        }
    }

    /**
     * Ajusta log10(T) = log10(a) + b·log10(n) para cada configuração com pelo menos 3 tamanhos.
     * Usa só tamanhos >= 10.000 quando há pelo menos 3 deles (tamanhos pequenos são dominados
     * por custos fixos e ruído de medição).
     */
    public static List<Complexity> complexity(List<Row> rows) {
        record G(String e, String a, Mode m, int t, int c, Distribution d, int r) {
        }
        Map<G, List<Row>> groups = new LinkedHashMap<>();
        for (Row r : rows) {
            Key k = r.key();
            groups.computeIfAbsent(new G(k.experiment(), k.algorithm(), k.mode(), k.threads(), k.cores(),
                    k.distribution(), k.valueRange()), x -> new ArrayList<>()).add(r);
        }
        List<Complexity> out = new ArrayList<>();
        for (var e : groups.entrySet()) {
            List<Row> pts = new ArrayList<>(e.getValue());
            List<Row> large = pts.stream().filter(r -> r.size() >= 10_000).toList();
            if (large.size() >= 3) pts = new ArrayList<>(large);
            if (pts.size() < 3) continue;
            pts.sort(Comparator.comparingInt(Row::size));
            double[] x = new double[pts.size()];
            double[] y = new double[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                x[i] = Math.log10(pts.get(i).size());
                y[i] = Math.log10(Math.max(pts.get(i).mean(), 1e-6));
            }
            G g = e.getKey();
            out.add(new Complexity(g.e(), g.a(), g.m(), g.t(), g.c(), g.d(), g.r(),
                    pts.get(0).size(), pts.get(pts.size() - 1).size(), Stats.linearRegression(x, y)));
        }
        return out;
    }

    /** Busca uma linha específica (ou null). */
    public static Row find(List<Row> rows, String algorithm, Mode mode, int threads, Distribution d,
                           int size, Integer valueRange, Integer cores) {
        for (Row r : rows) {
            Key k = r.key();
            if (k.algorithm().equals(algorithm) && k.mode() == mode && k.threads() == threads
                    && k.distribution() == d && k.size() == size
                    && (valueRange == null || k.valueRange() == valueRange)
                    && (cores == null || Objects.equals(k.cores(), cores))) {
                return r;
            }
        }
        return null;
    }
}
