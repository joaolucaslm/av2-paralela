package bench;

import bench.Measurement.Mode;
import sort.SortAlgorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/**
 * Executa um experimento: para cada combinação (intervalo, distribuição, tamanho, algoritmo,
 * configuração serial/paralela) roda as execuções de aquecimento e depois as amostras medidas.
 *
 * <p>Cuidados de medição:
 * <ul>
 *   <li>todos recebem a mesma entrada (gerada uma vez, com semente fixa) e cada amostra ordena
 *       uma cópia nova — a cópia fica fora do tempo medido;</li>
 *   <li>um aquecimento global compila os métodos quentes (JIT) antes de qualquer medição, e
 *       cada configuração ainda descarta {@code warmup} execuções;</li>
 *   <li>um pool Fork/Join por quantidade de threads é criado uma vez e reaproveitado, para não
 *       medir criação de threads;</li>
 *   <li>o resultado de toda amostra é comparado com {@link Arrays#sort(int[])}; se divergir o
 *       experimento é abortado;</li>
 *   <li>para entradas grandes chama-se o GC antes de cada amostra, reduzindo a chance de uma
 *       coleta cair no meio da medição.</li>
 * </ul>
 */
public final class BenchmarkRunner {

    public interface Listener {
        default void onStart(BenchmarkConfig config, int totalRuns) {
        }

        default void onMeasurement(Measurement m, int done, int total) {
        }

        default void onLog(String message) {
        }
    }

    private final BenchmarkConfig cfg;
    private final Listener listener;
    private volatile boolean cancelled;

    public BenchmarkRunner(BenchmarkConfig cfg, Listener listener) {
        this.cfg = cfg;
        this.listener = listener;
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public List<Measurement> run() {
        int cores = Runtime.getRuntime().availableProcessors();
        int total = cfg.totalRuns();
        listener.onStart(cfg, total);
        listener.onLog("Processadores disponíveis para a JVM: " + cores);

        Map<Integer, ForkJoinPool> pools = new LinkedHashMap<>();
        for (int t : cfg.threads) {
            pools.computeIfAbsent(t, ForkJoinPool::new);
        }
        List<Measurement> results = new ArrayList<>(total);
        try {
            if (cfg.warmup > 0) {
                jitWarmup(pools);
            }
            int done = 0;
            int[] allSizes = BenchmarkConfig.sortedUnion(cfg.sizes, cfg.quadraticSizes);
            for (int range : cfg.valueRanges) {
                for (Distribution dist : cfg.distributions) {
                    for (int size : allSizes) {
                        if (cancelled) return results;
                        if (cfg.algorithms.stream().noneMatch(a -> cfg.sizesFor(a).contains(size))) continue;

                        int effRange = dist.rangeFor(range);
                        int[] base = dist.create(size, range, cfg.seed);
                        int[] expected = base.clone();
                        Arrays.sort(expected);
                        listener.onLog(String.format("> %s, n=%,d, intervalo=%,d", dist.label(), size, effRange));

                        for (SortAlgorithm alg : cfg.algorithms) {
                            if (!cfg.sizesFor(alg).contains(size)) continue;
                            List<Integer> configs = new ArrayList<>();
                            if (cfg.includeSerial) configs.add(0); // 0 = serial
                            configs.addAll(cfg.threads);
                            for (int t : configs) {
                                Mode mode = t == 0 ? Mode.SERIAL : Mode.PARALELO;
                                int threads = t == 0 ? 1 : t;
                                ForkJoinPool pool = t == 0 ? null : pools.get(t);
                                for (int w = 0; w < cfg.warmup; w++) {
                                    if (cancelled) return results;
                                    timeOnce(alg, pool, base, expected, size);
                                }
                                for (int s = 1; s <= cfg.samples; s++) {
                                    if (cancelled) return results;
                                    double ms = timeOnce(alg, pool, base, expected, size);
                                    Measurement m = new Measurement(cfg.experiment, alg.name(), mode, threads,
                                            cores, dist, size, effRange, s, ms);
                                    results.add(m);
                                    listener.onMeasurement(m, ++done, total);
                                }
                            }
                        }
                    }
                }
            }
            return results;
        } finally {
            for (ForkJoinPool p : pools.values()) {
                p.shutdown();
            }
            for (ForkJoinPool p : pools.values()) {
                try {
                    p.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /** Mede uma execução (em ms) sobre uma cópia de {@code base} e valida o resultado. */
    private double timeOnce(SortAlgorithm alg, ForkJoinPool pool, int[] base, int[] expected, int size) {
        int[] work = base.clone();
        if (size >= 1_000_000) {
            System.gc();
        }
        long t0 = System.nanoTime();
        if (pool == null) {
            alg.sortSerial(work);
        } else {
            alg.sortParallel(work, pool);
        }
        long t1 = System.nanoTime();
        if (!Arrays.equals(work, expected)) {
            throw new IllegalStateException("Resultado incorreto: " + alg.name()
                    + (pool == null ? " serial" : " paralelo " + pool.getParallelism()) + " n=" + size);
        }
        return (t1 - t0) / 1_000_000.0;
    }

    /** Aquecimento do JIT: roda todos os caminhos de código algumas vezes antes de medir. */
    private void jitWarmup(Map<Integer, ForkJoinPool> pools) {
        listener.onLog("Aquecendo a JVM (JIT)...");
        long t0 = System.nanoTime();
        for (SortAlgorithm alg : cfg.algorithms) {
            int n = alg.quadratic() ? 20_000 : 300_000;
            for (Distribution dist : List.of(Distribution.ALEATORIO, Distribution.ORDENADO, Distribution.POUCOS_VALORES)) {
                int[] base = dist.create(n, 100_000, 7);
                int[] expected = base.clone();
                Arrays.sort(expected);
                for (int rep = 0; rep < 3; rep++) {
                    if (cancelled) return;
                    timeOnce(alg, null, base, expected, n);
                    for (ForkJoinPool pool : pools.values()) {
                        timeOnce(alg, pool, base, expected, n);
                    }
                }
            }
        }
        listener.onLog(String.format("Aquecimento concluído em %.1f s", (System.nanoTime() - t0) / 1e9));
    }
}
