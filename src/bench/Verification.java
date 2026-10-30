package bench;

import sort.Algorithms;
import sort.SortAlgorithm;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ForkJoinPool;

/**
 * Testes de corretude: compara cada algoritmo (serial e paralelo, várias quantidades de threads)
 * com {@link Arrays#sort(int[])} em casos de borda e entradas aleatórias.
 */
public final class Verification {

    private Verification() {
    }

    public static boolean run(PrintStream out) {
        List<int[]> cases = new ArrayList<>();
        cases.add(new int[0]);
        cases.add(new int[]{42});
        cases.add(new int[]{2, 1});
        cases.add(new int[]{1, 2});
        cases.add(new int[]{3, 3, 3, 3, 3});
        cases.add(new int[]{5, -1, 3, -1, 0, 1_000_000, -7, 5});
        cases.add(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE - 10, Integer.MAX_VALUE - 5});
        cases.add(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 3, Integer.MIN_VALUE + 1});
        SplittableRandom rnd = new SplittableRandom(1);
        for (int n : new int[]{3, 17, 100, 1_000, 8_191, 8_192, 8_193, 20_000, 70_001, 250_000}) {
            for (Distribution d : Distribution.values()) {
                int range = 1 + rnd.nextInt(5_000_000);
                cases.add(d.create(n, range, rnd.nextLong()));
            }
            int[] neg = new int[n];
            for (int i = 0; i < n; i++) neg[i] = rnd.nextInt(-50_000, 50_000);
            cases.add(neg);
        }

        int[] threadCounts = {1, 2, 3, 4, 7, 12, 16};
        List<ForkJoinPool> pools = new ArrayList<>();
        for (int t : threadCounts) pools.add(new ForkJoinPool(t));
        int checks = 0;
        int failures = 0;
        try {
            for (SortAlgorithm alg : Algorithms.all()) {
                for (int[] input : cases) {
                    if (alg.quadratic() && input.length > 100_000) continue;
                    int[] expected = input.clone();
                    Arrays.sort(expected);

                    int[] serial = input.clone();
                    alg.sortSerial(serial);
                    checks++;
                    if (!Arrays.equals(serial, expected)) {
                        failures++;
                        out.printf("FALHA %s serial n=%d%n", alg.name(), input.length);
                    }
                    for (ForkJoinPool pool : pools) {
                        int[] par = input.clone();
                        alg.sortParallel(par, pool);
                        checks++;
                        if (!Arrays.equals(par, expected)) {
                            failures++;
                            out.printf("FALHA %s paralelo(%d) n=%d%n", alg.name(), pool.getParallelism(), input.length);
                        }
                    }
                }
                out.printf("  %-14s ok%n", alg.name());
            }
            // Counting Sort deve recusar intervalos enormes em vez de tentar alocar gigabytes
            checks++;
            try {
                new sort.CountingSort().sortSerial(new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE});
                failures++;
                out.println("FALHA CountingSort aceitou intervalo de 2^32 valores");
            } catch (IllegalArgumentException expected) {
                // comportamento esperado
            }
        } finally {
            pools.forEach(ForkJoinPool::shutdown);
        }
        out.printf("%d verificações, %d falhas%n", checks, failures);
        return failures == 0;
    }
}
