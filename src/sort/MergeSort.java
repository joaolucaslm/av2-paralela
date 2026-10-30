package sort;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Merge Sort top-down estável.
 *
 * <p>Usa um único vetor auxiliar e alterna os papéis de origem/destino a cada nível da
 * recursão ("ping-pong"), evitando cópias de volta.
 * <p>Paralelo: as duas metades são ordenadas como tarefas Fork/Join independentes e a
 * intercalação (merge) também é paralela: o elemento do meio da sequência maior é localizado
 * na menor por busca binária, o que divide o merge em dois sub-merges independentes.
 * Sem o merge paralelo, a intercalação final de n elementos seria um gargalo serial O(n).
 */
public final class MergeSort implements SortAlgorithm {

    static final int SORT_THRESHOLD = 1 << 13;  // 8192
    static final int MERGE_THRESHOLD = 1 << 14; // 16384

    @Override
    public String name() {
        return "MergeSort";
    }

    @Override
    public String complexity() {
        return "O(n log n)";
    }

    @Override
    public void sortSerial(int[] a) {
        if (a.length < 2) {
            return;
        }
        int[] aux = a.clone();
        sort(aux, a, 0, a.length);
    }

    @Override
    public void sortParallel(int[] a, ForkJoinPool pool) {
        if (a.length < 2) {
            return;
        }
        int[] aux = a.clone();
        pool.invoke(new SortTask(aux, a, 0, a.length));
    }

    /**
     * Ordena dst[lo, hi). Na entrada src e dst têm o mesmo conteúdo nesse intervalo;
     * src é usado como área de trabalho.
     */
    static void sort(int[] src, int[] dst, int lo, int hi) {
        if (hi - lo < 2) {
            return;
        }
        int mid = (lo + hi) >>> 1;
        sort(dst, src, lo, mid);
        sort(dst, src, mid, hi);
        merge(src, lo, mid, src, mid, hi, dst, lo);
    }

    /** Intercala a[aLo, aHi) e b[bLo, bHi) em dst a partir de k (estável: empate favorece a). */
    static void merge(int[] a, int aLo, int aHi, int[] b, int bLo, int bHi, int[] dst, int k) {
        int i = aLo;
        int j = bLo;
        while (i < aHi && j < bHi) {
            dst[k++] = (a[i] <= b[j]) ? a[i++] : b[j++];
        }
        if (i < aHi) {
            System.arraycopy(a, i, dst, k, aHi - i);
        } else if (j < bHi) {
            System.arraycopy(b, j, dst, k, bHi - j);
        }
    }

    /** Primeiro índice em a[lo, hi) com a[idx] >= key. */
    static int lowerBound(int[] a, int lo, int hi, int key) {
        while (lo < hi) {
            int m = (lo + hi) >>> 1;
            if (a[m] < key) lo = m + 1;
            else hi = m;
        }
        return lo;
    }

    /** Primeiro índice em a[lo, hi) com a[idx] > key. */
    static int upperBound(int[] a, int lo, int hi, int key) {
        while (lo < hi) {
            int m = (lo + hi) >>> 1;
            if (a[m] <= key) lo = m + 1;
            else hi = m;
        }
        return lo;
    }

    private static final class SortTask extends RecursiveAction {
        private final int[] src;
        private final int[] dst;
        private final int lo;
        private final int hi;

        SortTask(int[] src, int[] dst, int lo, int hi) {
            this.src = src;
            this.dst = dst;
            this.lo = lo;
            this.hi = hi;
        }

        @Override
        protected void compute() {
            if (hi - lo <= SORT_THRESHOLD) {
                sort(src, dst, lo, hi);
                return;
            }
            int mid = (lo + hi) >>> 1;
            invokeAll(new SortTask(dst, src, lo, mid), new SortTask(dst, src, mid, hi));
            new MergeTask(src, lo, mid, mid, hi, dst, lo).compute();
        }
    }

    /**
     * Merge paralelo de src[aLo, aHi) (sequência da esquerda) com src[bLo, bHi) (da direita)
     * em dst a partir de k. Mantém a estabilidade: em empates, elementos da esquerda vêm antes.
     */
    static final class MergeTask extends RecursiveAction {
        private final int[] src;
        private final int aLo, aHi, bLo, bHi;
        private final int[] dst;
        private final int k;

        MergeTask(int[] src, int aLo, int aHi, int bLo, int bHi, int[] dst, int k) {
            this.src = src;
            this.aLo = aLo;
            this.aHi = aHi;
            this.bLo = bLo;
            this.bHi = bHi;
            this.dst = dst;
            this.k = k;
        }

        @Override
        protected void compute() {
            int na = aHi - aLo;
            int nb = bHi - bLo;
            if (na + nb <= MERGE_THRESHOLD || na == 0 || nb == 0) {
                merge(src, aLo, aHi, src, bLo, bHi, dst, k);
                return;
            }
            int aMid;
            int bMid;
            if (na >= nb) {
                aMid = (aLo + aHi) >>> 1;
                // elementos da direita estritamente menores vão antes do pivô da esquerda
                bMid = lowerBound(src, bLo, bHi, src[aMid]);
            } else {
                bMid = (bLo + bHi) >>> 1;
                // elementos da esquerda menores ou iguais vão antes do pivô da direita
                aMid = upperBound(src, aLo, aHi, src[bMid]);
            }
            int kMid = k + (aMid - aLo) + (bMid - bLo);
            invokeAll(new MergeTask(src, aLo, aMid, bLo, bMid, dst, k),
                    new MergeTask(src, aMid, aHi, bMid, bHi, dst, kMid));
        }
    }
}
