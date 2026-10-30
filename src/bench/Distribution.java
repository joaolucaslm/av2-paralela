package bench;

import java.util.Arrays;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Natureza do conjunto de dados de entrada. Os valores ficam em [0, intervalo).
 * A geração é determinística (semente fixa) para que todos os algoritmos e configurações
 * recebam exatamente a mesma entrada.
 */
public enum Distribution {

    ALEATORIO("Aleatório") {
        @Override
        int[] generate(int n, int range, SplittableRandom rnd) {
            int[] a = new int[n];
            for (int i = 0; i < n; i++) a[i] = rnd.nextInt(range);
            return a;
        }
    },
    ORDENADO("Ordenado") {
        @Override
        int[] generate(int n, int range, SplittableRandom rnd) {
            int[] a = ALEATORIO.generate(n, range, rnd);
            Arrays.sort(a);
            return a;
        }
    },
    INVERSO("Inverso") {
        @Override
        int[] generate(int n, int range, SplittableRandom rnd) {
            int[] a = ORDENADO.generate(n, range, rnd);
            for (int i = 0, j = n - 1; i < j; i++, j--) {
                int t = a[i];
                a[i] = a[j];
                a[j] = t;
            }
            return a;
        }
    },
    QUASE_ORDENADO("Quase ordenado") {
        @Override
        int[] generate(int n, int range, SplittableRandom rnd) {
            int[] a = ORDENADO.generate(n, range, rnd);
            int swaps = Math.max(1, n / 100); // 1% de trocas aleatórias
            for (int s = 0; s < swaps && n > 1; s++) {
                int i = rnd.nextInt(n);
                int j = rnd.nextInt(n);
                int t = a[i];
                a[i] = a[j];
                a[j] = t;
            }
            return a;
        }
    },
    POUCOS_VALORES("Poucos valores") {
        @Override
        int effectiveRange(int range) {
            return Math.min(range, FEW_VALUES);
        }

        @Override
        int[] generate(int n, int range, SplittableRandom rnd) {
            return ALEATORIO.generate(n, range, rnd);
        }
    };

    /** Quantidade de valores distintos da distribuição POUCOS_VALORES. */
    public static final int FEW_VALUES = 100;

    private final String label;

    Distribution(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** Intervalo de valores efetivamente usado para um intervalo pedido. */
    int effectiveRange(int range) {
        return range;
    }

    abstract int[] generate(int n, int range, SplittableRandom rnd);

    /** Gera n valores em [0, effectiveRange(range)) com semente determinística. */
    public int[] create(int n, int range, long seed) {
        int r = Math.max(1, effectiveRange(range));
        long mixed = seed ^ (31L * ordinal() + 1) * 0x9E3779B97F4A7C15L ^ (long) n * 0xBF58476D1CE4E5B9L ^ r;
        return generate(n, r, new SplittableRandom(mixed));
    }

    public int rangeFor(int range) {
        return Math.max(1, effectiveRange(range));
    }

    public static Distribution parse(String s) {
        String key = s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        for (Distribution d : values()) {
            if (d.name().equals(key) || d.name().startsWith(key)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Distribuição desconhecida: " + s);
    }

    public static Distribution fromLabelOrName(String s) {
        for (Distribution d : values()) {
            if (d.label.equalsIgnoreCase(s.trim())) return d;
        }
        return parse(s);
    }
}
