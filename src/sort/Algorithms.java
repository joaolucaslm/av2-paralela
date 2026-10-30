package sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Registro dos algoritmos disponíveis. */
public final class Algorithms {

    private Algorithms() {
    }

    public static List<SortAlgorithm> all() {
        return List.of(new QuickSort(), new InsertionSort(), new MergeSort(), new CountingSort());
    }

    public static SortAlgorithm byName(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT).replace("sort", "").replace("_", "");
        for (SortAlgorithm alg : all()) {
            String algKey = alg.name().toLowerCase(Locale.ROOT).replace("sort", "");
            if (algKey.equals(key)) {
                return alg;
            }
        }
        throw new IllegalArgumentException("Algoritmo desconhecido: " + name
                + " (use quick, insertion, merge, counting)");
    }

    public static List<SortAlgorithm> parse(String csv) {
        List<SortAlgorithm> list = new ArrayList<>();
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                list.add(byName(part));
            }
        }
        return list;
    }
}
