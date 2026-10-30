package sort;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.function.IntConsumer;

/** Utilitários de paralelismo de dados usados pelos algoritmos baseados em blocos. */
public final class Parallel {

    private Parallel() {
    }

    /**
     * Executa {@code body.accept(i)} para cada i em [0, parts) em paralelo e espera todos
     * terminarem. A divisão é recursiva (dividir e conquistar), o que distribui as tarefas
     * pelo pool por roubo de trabalho (work stealing).
     */
    public static void forEach(ForkJoinPool pool, int parts, IntConsumer body) {
        if (parts <= 0) {
            return;
        }
        if (parts == 1) {
            body.accept(0);
            return;
        }
        pool.invoke(new RangeTask(0, parts, body));
    }

    /** Início do bloco {@code part} quando [0, n) é dividido em {@code parts} blocos quase iguais. */
    public static int blockStart(int n, int parts, int part) {
        return (int) ((long) n * part / parts);
    }

    private static final class RangeTask extends RecursiveAction {
        private final int from;
        private final int to;
        private final IntConsumer body;

        RangeTask(int from, int to, IntConsumer body) {
            this.from = from;
            this.to = to;
            this.body = body;
        }

        @Override
        protected void compute() {
            if (to - from == 1) {
                body.accept(from);
                return;
            }
            int mid = (from + to) >>> 1;
            invokeAll(new RangeTask(from, mid, body), new RangeTask(mid, to, body));
        }
    }
}
