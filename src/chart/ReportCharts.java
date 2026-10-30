package chart;

import bench.Analysis.Row;
import bench.Distribution;
import bench.Measurement.Mode;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Gera os gráficos PNG do relatório a partir do resumo de um experimento. */
public final class ReportCharts {

    private ReportCharts() {
    }

    static final int W = 960;
    static final int H = 600;
    static final int CELL_W = 760;
    static final int CELL_H = 500;
    static final int SCALE = 2;

    public static List<Path> exportAll(Path dir, List<Row> all) throws IOException {
        Files.createDirectories(dir);
        List<Path> files = new ArrayList<>();
        if (all.isEmpty()) return files;

        int maxCores = all.stream().mapToInt(Row::cores).max().orElse(1);
        List<Integer> ranges = all.stream().map(Row::valueRange).distinct().sorted().toList();
        List<Integer> cores = all.stream().map(Row::cores).distinct().sorted().toList();
        int mainRange = ChartFactory.mainRange(all);
        List<Row> main = ChartFactory.where(all, r -> r.cores() == maxCores && ChartFactory.matchesRange(r, mainRange));
        Distribution d = main.stream().anyMatch(r -> r.distribution() == Distribution.ALEATORIO)
                ? Distribution.ALEATORIO : main.get(0).distribution();
        List<String> algs = ChartFactory.algorithms(main);
        List<String> fastAlgs = algs.stream().filter(a -> !a.equals("InsertionSort")).toList();
        int t = ChartFactory.mainThreads(main);
        boolean multiSize = main.stream().map(Row::size).distinct().count() >= 3;

        if (multiSize) {
            List<Chart> grid = new ArrayList<>();
            for (String alg : algs) {
                LineChart c = ChartFactory.timeVsSize(main, alg, d);
                files.add(save(c, dir.resolve("tempo_tamanho_" + slug(alg) + ".png")));
                grid.add(c);
            }
            files.add(saveGrid(grid, 2, dir.resolve("tempo_tamanho_grade.png")));

            LineChart ser = ChartFactory.algorithmsTimeVsSize(main, Mode.SERIAL, 1, d);
            LineChart par = ChartFactory.algorithmsTimeVsSize(main, Mode.PARALELO, t, d);
            double[] e1 = ser.dataYExtent();
            double[] e2 = par.dataYExtent();
            double lo = Math.min(e1[0], e2[0]);
            double hi = Math.max(e1[1], e2[1]);
            ser.yRange(lo, hi);
            par.yRange(lo, hi);
            files.add(save(ser, dir.resolve("comparativo_serial.png")));
            files.add(save(par, dir.resolve("comparativo_paralelo.png")));
            files.add(saveGrid(List.of(ser, par), 2, dir.resolve("comparativo_grade.png")));

            files.add(save(ChartFactory.speedupVsSize(main, algs, t, d), dir.resolve("speedup_tamanho.png")));
        }
        boolean multiThreads = main.stream().filter(r -> r.mode() == Mode.PARALELO).map(Row::threads).distinct().count() >= 3;
        if (multiThreads && cores.size() == 1) {
            files.add(save(ChartFactory.speedupVsThreads(main, fastAlgs, d, null, false), dir.resolve("speedup_threads.png")));
            files.add(save(ChartFactory.speedupVsThreads(main, fastAlgs, d, null, true), dir.resolve("eficiencia_threads.png")));
            if (algs.contains("InsertionSort")) {
                files.add(save(ChartFactory.speedupVsThreadsWithQuadratic(main, "InsertionSort", d, null),
                        dir.resolve("speedup_threads_insertion.png")));
            }
        }
        boolean multiDist = main.stream().map(Row::distribution).distinct().count() >= 2;
        if (multiDist) {
            List<Chart> grid = new ArrayList<>();
            for (String alg : algs) {
                BarChart c = ChartFactory.timeByDistribution(main, alg, t, null);
                files.add(save(c, dir.resolve("distribuicoes_" + slug(alg) + ".png")));
                grid.add(c);
            }
            files.add(saveGrid(grid, 2, dir.resolve("distribuicoes_grade.png")));
            files.add(save(ChartFactory.speedupByDistribution(main, fastAlgs, t), dir.resolve("speedup_distribuicoes.png")));
        }
        if (ranges.size() >= 3) {
            List<Row> rr = ChartFactory.where(all, r -> r.cores() == maxCores);
            List<Integer> sizes = rr.stream().map(Row::size).distinct().sorted().toList();
            for (int n : sizes) {
                List<Chart> grid = new ArrayList<>();
                for (String alg : ChartFactory.algorithms(rr)) {
                    LineChart c = ChartFactory.timeVsRange(rr, alg, n, d);
                    if (!c.hasData()) continue;
                    files.add(save(c, dir.resolve("intervalo_" + slug(alg) + "_n" + compactSlug(n) + ".png")));
                    grid.add(c);
                }
                if (grid.size() > 1) files.add(saveGrid(grid, Math.min(grid.size(), 3) == 3 ? 3 : 2,
                        dir.resolve("intervalo_grade_n" + compactSlug(n) + ".png")));
                files.add(save(ChartFactory.speedupVsRange(rr, ChartFactory.algorithms(rr), n, ChartFactory.mainThreads(rr), d),
                        dir.resolve("intervalo_speedup_n" + compactSlug(n) + ".png")));
            }
        }
        if (cores.size() >= 2) {
            List<Chart> grid = new ArrayList<>();
            for (String alg : ChartFactory.algorithms(all)) {
                LineChart c = ChartFactory.speedupByCores(all, alg, d, null);
                files.add(save(c, dir.resolve("nucleos_" + slug(alg) + ".png")));
                grid.add(c);
            }
            files.add(saveGrid(grid, 2, dir.resolve("nucleos_grade.png")));
        }
        return files;
    }

    public static Path save(Chart c, Path file) throws IOException {
        return write(render(c, W, H), file);
    }

    public static BufferedImage render(Chart c, int w, int h) {
        BufferedImage img = new BufferedImage(w * SCALE, h * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.scale(SCALE, SCALE);
        c.paint(g, w, h);
        g.dispose();
        return img;
    }

    public static Path saveGrid(List<Chart> charts, int cols, Path file) throws IOException {
        int rows = (charts.size() + cols - 1) / cols;
        BufferedImage img = new BufferedImage(cols * CELL_W * SCALE, rows * CELL_H * SCALE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Theme.SURFACE);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.scale(SCALE, SCALE);
        for (int i = 0; i < charts.size(); i++) {
            Graphics2D cell = (Graphics2D) g.create((i % cols) * CELL_W, (i / cols) * CELL_H, CELL_W, CELL_H);
            charts.get(i).paint(cell, CELL_W, CELL_H);
            cell.dispose();
        }
        g.dispose();
        return write(img, file);
    }

    private static Path write(BufferedImage img, Path file) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        ImageIO.write(img, "png", file.toFile());
        return file;
    }

    static String slug(String alg) {
        return alg.replace("Sort", "").toLowerCase(Locale.ROOT);
    }

    static String compactSlug(int n) {
        if (n % 1_000_000 == 0) return (n / 1_000_000) + "m";
        if (n % 1_000 == 0) return (n / 1_000) + "k";
        return Integer.toString(n);
    }
}
