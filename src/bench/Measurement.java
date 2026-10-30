package bench;

/**
 * Uma amostra de tempo (uma linha do CSV de medições).
 *
 * @param experiment    nome do experimento (principal, intervalo, nucleos...)
 * @param algorithm     nome do algoritmo
 * @param mode          SERIAL ou PARALELO
 * @param threads       threads usadas (1 para o serial)
 * @param cores         processadores lógicos disponíveis para a JVM
 * @param distribution  natureza dos dados
 * @param size          tamanho da entrada
 * @param valueRange    intervalo de valores [0, valueRange)
 * @param sample        número da amostra (1..N)
 * @param timeMs        tempo de execução em milissegundos
 */
public record Measurement(String experiment, String algorithm, Mode mode, int threads, int cores,
                          Distribution distribution, int size, int valueRange, int sample,
                          double timeMs) {

    public enum Mode {
        SERIAL, PARALELO
    }

    /** Rótulo da configuração usado nas legendas: "Serial" ou "Paralelo (8 threads)". */
    public String configLabel() {
        return configLabel(mode, threads);
    }

    public static String configLabel(Mode mode, int threads) {
        return mode == Mode.SERIAL ? "Serial" : "Paralelo " + threads + (threads == 1 ? " thread" : " threads");
    }
}
