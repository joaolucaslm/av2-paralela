package sort;

import java.util.concurrent.ForkJoinPool;

/**
 * Contrato comum dos algoritmos de ordenação.
 * Cada algoritmo tem uma versão serial e uma versão paralela que roda dentro de um
 * {@link ForkJoinPool} cujo paralelismo define a quantidade de threads usadas.
 */
public interface SortAlgorithm {

    /** Nome curto usado no CSV e nos gráficos. */
    String name();

    /** Complexidade de tempo no caso médio (apenas informativo). */
    String complexity();

    /** true para algoritmos O(n²): o framework usa tamanhos de entrada menores para eles. */
    default boolean quadratic() {
        return false;
    }

    /** Ordena o vetor inteiro em ordem crescente usando uma única thread. */
    void sortSerial(int[] a);

    /** Ordena o vetor inteiro em ordem crescente usando as threads do pool. */
    void sortParallel(int[] a, ForkJoinPool pool);
}
