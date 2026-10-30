package sort;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Quick Sort com partição de Hoare e pivô pela mediana de três.
 *
 * <p>Serial: recursão no lado menor e laço no lado maior, o que limita a pilha a O(log n).
 * <p>Paralelo: paralelismo de tarefas com Fork/Join. Depois de cada partição as duas metades
 * são independentes e viram tarefas separadas. Abaixo de {@link #FORK_THRESHOLD} elementos a
 * tarefa ordena serialmente (controle de granularidade, evita criar tarefas pequenas demais).
 */
public final class QuickSort implements SortAlgorithm {

    static final int FORK_THRESHOLD = 1 << 13; // 8192

    @Override
    public String name() {
        return "QuickSort";
    }

    @Override
    public String complexity() {
        return "O(n log n)";
    }

    @Override
    public void sortSerial(int[] a) {
        quickSort(a, 0, a.length - 1);
    }

    @Override
    public void sortParallel(int[] a, ForkJoinPool pool) {
        if (a.length < 2) {
            return;
        }
        pool.invoke(new QuickTask(a, 0, a.length - 1));
    }

    /** Ordena a[lo..hi] (intervalo fechado). */
    static void quickSort(int[] a, int lo, int hi) {
        while (lo < hi) {
            int p = partition(a, lo, hi);
            if (p - lo < hi - p) {
                quickSort(a, lo, p);
                lo = p + 1;
            } else {
                quickSort(a, p + 1, hi);
                hi = p;
            }
        }
    }

    /**
     * Partição de Hoare. Ao final, todo elemento de a[lo..j] é <= pivô e todo elemento de
     * a[j+1..hi] é >= pivô, com lo <= j < hi.
     */
    static int partition(int[] a, int lo, int hi) {
        int mid = lo + ((hi - lo) >>> 1);
        // Mediana de três: deixa a[lo] <= a[mid] <= a[hi]; o pivô fica na posição do meio,
        // o que garante o término do esquema de Hoare e evita o pior caso em dados ordenados.
        if (a[mid] < a[lo]) swap(a, mid, lo);
        if (a[hi] < a[lo]) swap(a, hi, lo);
        if (a[hi] < a[mid]) swap(a, hi, mid);
        int pivot = a[mid];

        int i = lo - 1;
        int j = hi + 1;
        while (true) {
            do {
                i++;
            } while (a[i] < pivot);
            do {
                j--;
            } while (a[j] > pivot);
            if (i >= j) {
                return j;
            }
            swap(a, i, j);
        }
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static final class QuickTask extends RecursiveAction {
        private final int[] a;
        private final int lo;
        private final int hi;

        QuickTask(int[] a, int lo, int hi) {
            this.a = a;
            this.lo = lo;
            this.hi = hi;
        }

        @Override
        protected void compute() {
            if (hi - lo + 1 <= FORK_THRESHOLD) {
                quickSort(a, lo, hi);
                return;
            }
            int p = partition(a, lo, hi);
            invokeAll(new QuickTask(a, lo, p), new QuickTask(a, p + 1, hi));
        }
    }
}
