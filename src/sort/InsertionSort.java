package sort;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Insertion Sort.
 *
 * <p>O laço interno depende do resultado da iteração anterior (cada elemento é inserido no
 * prefixo já ordenado), então o algoritmo em si não tem paralelismo interno aproveitável.
 * A versão paralela usa decomposição de domínio: o vetor é dividido em P blocos (P = número de
 * threads), cada bloco é ordenado por Insertion Sort em uma thread e os blocos ordenados são
 * intercalados em árvore (log P níveis de merge, também em paralelo).
 *
 * <p>Como o custo é quadrático, P blocos de tamanho n/P custam P·(n/P)² = n²/P no total: o
 * trabalho cai junto com o número de blocos. Por isso o speedup pode passar de P
 * (speedup superlinear) — efeito algorítmico, não só de paralelismo.
 */
public final class InsertionSort implements SortAlgorithm {

    @Override
    public String name() {
        return "InsertionSort";
    }

    @Override
    public String complexity() {
        return "O(n²)";
    }

    @Override
    public boolean quadratic() {
        return true;
    }

    @Override
    public void sortSerial(int[] a) {
        insertionSort(a, 0, a.length);
    }

    @Override
    public void sortParallel(int[] a, ForkJoinPool pool) {
        int n = a.length;
        if (n < 2) {
            return;
        }
        int runs = Math.max(1, Math.min(pool.getParallelism(), n));
        if (runs == 1) {
            pool.invoke(new RecursiveAction() {
                @Override
                protected void compute() {
                    insertionSort(a, 0, n);
                }
            });
            return;
        }
        int[] bounds = new int[runs + 1];
        for (int r = 0; r <= runs; r++) {
            bounds[r] = Parallel.blockStart(n, runs, r);
        }
        int[] aux = new int[n];
        pool.invoke(new RunsTask(a, aux, bounds, 0, runs));
    }

    /** Ordena a[lo, hi) por inserção. */
    static void insertionSort(int[] a, int lo, int hi) {
        for (int i = lo + 1; i < hi; i++) {
            int key = a[i];
            int j = i - 1;
            while (j >= lo && a[j] > key) {
                a[j + 1] = a[j];
                j--;
            }
            a[j + 1] = key;
        }
    }

    /** Ordena os blocos [r0, r1) e os intercala, deixando a[bounds[r0], bounds[r1]) ordenado. */
    private static final class RunsTask extends RecursiveAction {
        private final int[] a;
        private final int[] aux;
        private final int[] bounds;
        private final int r0;
        private final int r1;

        RunsTask(int[] a, int[] aux, int[] bounds, int r0, int r1) {
            this.a = a;
            this.aux = aux;
            this.bounds = bounds;
            this.r0 = r0;
            this.r1 = r1;
        }

        @Override
        protected void compute() {
            if (r1 - r0 == 1) {
                insertionSort(a, bounds[r0], bounds[r1]);
                return;
            }
            int rm = (r0 + r1) >>> 1;
            invokeAll(new RunsTask(a, aux, bounds, r0, rm), new RunsTask(a, aux, bounds, rm, r1));
            int lo = bounds[r0];
            int mid = bounds[rm];
            int hi = bounds[r1];
            new MergeSort.MergeTask(a, lo, mid, mid, hi, aux, lo).compute();
            System.arraycopy(aux, lo, a, lo, hi - lo);
        }
    }
}
