package bench;

import bench.Measurement.Mode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Leitura e escrita dos arquivos CSV (separador vírgula, decimal com ponto, UTF-8). */
public final class CsvIO {

    private CsvIO() {
    }

    public static final String MEASUREMENT_HEADER =
            "experimento,algoritmo,modo,threads,nucleos,distribuicao,tamanho,intervalo_valores,amostra,tempo_ms";

    public static final String SUMMARY_HEADER =
            "experimento,algoritmo,modo,threads,nucleos,distribuicao,tamanho,intervalo_valores,amostras,"
                    + "media_ms,mediana_ms,desvio_padrao_ms,cv_pct,min_ms,max_ms,ic95_ms,speedup,ic95_speedup,eficiencia,karp_flatt";

    public static final String COMPLEXITY_HEADER =
            "experimento,algoritmo,modo,threads,nucleos,distribuicao,intervalo_valores,tamanho_min,tamanho_max,"
                    + "pontos,expoente_b,r2";

    public static String toCsv(Measurement m) {
        return String.join(",", m.experiment(), m.algorithm(), m.mode().name(), Integer.toString(m.threads()),
                Integer.toString(m.cores()), m.distribution().name(), Integer.toString(m.size()),
                Integer.toString(m.valueRange()), Integer.toString(m.sample()), fmt(m.timeMs(), 4));
    }

    /** Escritor incremental: cada medição é gravada e descarregada assim que termina. */
    public static final class MeasurementWriter implements AutoCloseable {
        private final BufferedWriter out;

        public MeasurementWriter(Path file, boolean append) throws IOException {
            Files.createDirectories(file.toAbsolutePath().getParent());
            boolean needsHeader = !append || !Files.exists(file) || Files.size(file) == 0;
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            if (needsHeader) {
                out.write(MEASUREMENT_HEADER);
                out.newLine();
                out.flush();
            }
        }

        public synchronized void write(Measurement m) {
            try {
                out.write(toCsv(m));
                out.newLine();
                out.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            out.close();
        }
    }

    public static void writeMeasurements(Path file, List<Measurement> ms) throws IOException {
        try (MeasurementWriter w = new MeasurementWriter(file, false)) {
            for (Measurement m : ms) w.write(m);
        }
    }

    public static List<Measurement> readMeasurements(Path file) throws IOException {
        List<Measurement> list = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).strip();
            if (line.isEmpty() || line.startsWith("experimento")) continue;
            String[] f = line.split(",");
            if (f.length < 10) {
                throw new IOException("Linha " + (i + 1) + " inválida em " + file + ": " + line);
            }
            list.add(new Measurement(f[0], f[1], Mode.valueOf(f[2]), Integer.parseInt(f[3]),
                    Integer.parseInt(f[4]), Distribution.valueOf(f[5]), Integer.parseInt(f[6]),
                    Integer.parseInt(f[7]), Integer.parseInt(f[8]), Double.parseDouble(f[9])));
        }
        return list;
    }

    public static void writeSummary(Path file, List<Analysis.Row> rows) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write(SUMMARY_HEADER);
            out.newLine();
            for (Analysis.Row r : rows) {
                Analysis.Key k = r.key();
                Stats.Summary s = r.stats();
                out.write(String.join(",", k.experiment(), k.algorithm(), k.mode().name(),
                        Integer.toString(k.threads()), Integer.toString(k.cores()), k.distribution().name(),
                        Integer.toString(k.size()), Integer.toString(k.valueRange()), Integer.toString(s.n()),
                        fmt(s.mean(), 4), fmt(s.median(), 4), fmt(s.stdDev(), 4), fmt(s.cvPercent(), 2),
                        fmt(s.min(), 4), fmt(s.max(), 4), fmt(s.ci95(), 4), fmt(r.speedup(), 3),
                        fmt(r.speedupCi(), 3), fmt(r.efficiency(), 3), fmt(r.karpFlatt(), 4)));
                out.newLine();
            }
        }
    }

    public static void writeComplexity(Path file, List<Analysis.Complexity> rows) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write(COMPLEXITY_HEADER);
            out.newLine();
            for (Analysis.Complexity c : rows) {
                out.write(String.join(",", c.experiment(), c.algorithm(), c.mode().name(),
                        Integer.toString(c.threads()), Integer.toString(c.cores()), c.distribution().name(),
                        Integer.toString(c.valueRange()), Integer.toString(c.minSize()),
                        Integer.toString(c.maxSize()), Integer.toString(c.regression().n()),
                        fmt(c.regression().slope(), 3), fmt(c.regression().r2(), 4)));
                out.newLine();
            }
        }
    }

    static String fmt(double v, int decimals) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "";
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }
}
