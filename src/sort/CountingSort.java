package sort;

import java.util.concurrent.ForkJoinPool;

/**
 * Counting Sort estável (aceita negativos: os valores são deslocados pelo mínimo).
 *
 * <p>Serial: conta as ocorrências de cada valor, transforma as contagens em posições iniciais
 * (soma de prefixos exclusiva) e distribui os elementos no vetor de saída. Tempo O(n + k),
 * onde k = max − min + 1 é o intervalo de valores.
 *
 * <p>Paralelo (P blocos de dados, um por thread):
 * <ol>
 *   <li>mínimo e máximo em paralelo;</li>
 *   <li>cada thread monta um histograma local do seu bloco (sem disputa por memória
 *       compartilhada, sem sincronização);</li>
 *   <li>o intervalo de valores é dividido em P faixas e, para cada valor, calcula-se a posição
 *       inicial de cada bloco: soma de prefixos sobre (valor, bloco), feita em duas passadas
 *       paralelas;</li>
 *   <li>cada thread escreve os elementos do seu bloco nas suas posições (estável, sem
 *       conflitos de escrita);</li>
 *   <li>cópia de volta em paralelo.</li>
 * </ol>
 * O custo extra é O(P·k) de memória e tempo para os histogramas: quando k é grande em relação
 * a n, a versão paralela perde vantagem — um ponto analisado no experimento de intervalo.
 */
public final class CountingSort implements SortAlgorithm {

    /** Tamanho mínimo de bloco por thread (controle de granularidade). */
    static final int MIN_CHUNK = 1 << 14; // 16384

    /** Maior intervalo de valores aceito (evita alocar vetores de contagem gigantes). */
    static final long MAX_RANGE = 1L << 27;

    @Override
    public String name() {
        return "CountingSort";
    }

    @Override
    public String complexity() {
        return "O(n + k)";
    }

    @Override
    public void sortSerial(int[] a) {
        int n = a.length;
        if (n < 2) {
            return;
        }
        int min = a[0];
        int max = a[0];
        for (int v : a) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        int k = checkedRange(min, max);

        int[] count = new int[k];
        for (int v : a) {
            count[v - min]++;
        }
        int sum = 0;
        for (int v = 0; v < k; v++) {
            int c = count[v];
            count[v] = sum;
            sum += c;
        }
        int[] out = new int[n];
        for (int v : a) {
            out[count[v - min]++] = v;
        }
        System.arraycopy(out, 0, a, 0, n);
    }

    @Override
    public void sortParallel(int[] a, ForkJoinPool pool) {
        int n = a.length;
        if (n < 2) {
            return;
        }
        int chunks = Math.max(1, Math.min(pool.getParallelism(), (n + MIN_CHUNK - 1) / MIN_CHUNK));

        // 1) mínimo e máximo por bloco
        int[] mins = new int[chunks];
        int[] maxs = new int[chunks];
        Parallel.forEach(pool, chunks, c -> {
            int lo = Parallel.blockStart(n, chunks, c);
            int hi = Parallel.blockStart(n, chunks, c + 1);
            int mn = a[lo];
            int mx = a[lo];
            for (int i = lo + 1; i < hi; i++) {
                int v = a[i];
                if (v < mn) mn = v;
                if (v > mx) mx = v;
            }
            mins[c] = mn;
            maxs[c] = mx;
        });
        int min = mins[0];
        int max = maxs[0];
        for (int c = 1; c < chunks; c++) {
            min = Math.min(min, mins[c]);
            max = Math.max(max, maxs[c]);
        }
        final int base = min;
        final int k = checkedRange(min, max);

        // 2) histograma local por bloco
        int[][] hist = new int[chunks][];
        Parallel.forEach(pool, chunks, c -> {
            int lo = Parallel.blockStart(n, chunks, c);
            int hi = Parallel.blockStart(n, chunks, c + 1);
            int[] h = new int[k];
            for (int i = lo; i < hi; i++) {
                h[a[i] - base]++;
            }
            hist[c] = h;
        });

        // 3) posições iniciais: soma de prefixos na ordem (valor, bloco), em duas passadas
        int bands = Math.max(1, Math.min(pool.getParallelism(), k));
        long[] bandTotal = new long[bands];
        Parallel.forEach(pool, bands, b -> {
            int vLo = Parallel.blockStart(k, bands, b);
            int vHi = Parallel.blockStart(k, bands, b + 1);
            long total = 0;
            for (int v = vLo; v < vHi; v++) {
                for (int c = 0; c < chunks; c++) {
                    total += hist[c][v];
                }
            }
            bandTotal[b] = total;
        });
        int[] bandStart = new int[bands];
        long running = 0;
        for (int b = 0; b < bands; b++) {
            bandStart[b] = (int) running;
            running += bandTotal[b];
        }
        Parallel.forEach(pool, bands, b -> {
            int vLo = Parallel.blockStart(k, bands, b);
            int vHi = Parallel.blockStart(k, bands, b + 1);
            int pos = bandStart[b];
            for (int v = vLo; v < vHi; v++) {
                for (int c = 0; c < chunks; c++) {
                    int[] h = hist[c];
                    int cnt = h[v];
                    h[v] = pos;
                    pos += cnt;
                }
            }
        });

        // 4) distribuição: cada bloco escreve nas suas próprias posições
        int[] out = new int[n];
        Parallel.forEach(pool, chunks, c -> {
            int lo = Parallel.blockStart(n, chunks, c);
            int hi = Parallel.blockStart(n, chunks, c + 1);
            int[] h = hist[c];
            for (int i = lo; i < hi; i++) {
                int v = a[i];
                out[h[v - base]++] = v;
            }
        });

        // 5) cópia de volta
        Parallel.forEach(pool, chunks, c -> {
            int lo = Parallel.blockStart(n, chunks, c);
            int hi = Parallel.blockStart(n, chunks, c + 1);
            System.arraycopy(out, lo, a, lo, hi - lo);
        });
    }

    private static int checkedRange(int min, int max) {
        long k = (long) max - (long) min + 1;
        if (k > MAX_RANGE) {
            throw new IllegalArgumentException(
                    "Intervalo de valores grande demais para Counting Sort: " + k);
        }
        return (int) k;
    }
}
