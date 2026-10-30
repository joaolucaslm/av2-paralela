package gui;

import bench.Analysis;
import bench.Analysis.Row;
import bench.BenchmarkConfig;
import bench.BenchmarkRunner;
import bench.CsvIO;
import bench.Distribution;
import bench.Measurement;
import bench.Measurement.Mode;
import chart.Chart;
import chart.ChartFactory;
import chart.LineChart;
import chart.ReportCharts;
import chart.Theme;
import sort.Algorithms;
import sort.SortAlgorithm;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Painel interativo: configura e executa benchmarks com gráficos que se atualizam a cada amostra
 * medida, ou abre um CSV de medições para explorar os resultados com filtros.
 */
public final class Dashboard extends JFrame {

    private static final String ALL = "Todos";
    private static final String LARGEST = "Maior disponível";
    private static final String SERIAL = "Serial";

    // ---- dados
    private final List<Measurement> measurements = new ArrayList<>();
    private List<Row> rows = List.of();

    // ---- controles de execução
    private final Map<SortAlgorithm, JCheckBox> algBoxes = new LinkedHashMap<>();
    private final Map<Distribution, JCheckBox> distBoxes = new LinkedHashMap<>();
    private final JTextField sizesField = new JTextField("10k,100k,1m,5m");
    private final JTextField quadSizesField = new JTextField("1k,10k,50k");
    private final JTextField rangesField = new JTextField("1m");
    private final JTextField threadsField = new JTextField(defaultThreads());
    private final JSpinner samplesSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 100, 1));
    private final JSpinner warmupSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 20, 1));
    private final JCheckBox appendBox = new JCheckBox("Acrescentar aos dados atuais");
    private final JButton runButton = new JButton("Executar");
    private final JButton stopButton = new JButton("Parar");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = new JLabel("Pronto");

    // ---- filtros
    private final JComboBox<String> expCombo = new JComboBox<>();
    private final JComboBox<String> algCombo = new JComboBox<>();
    private final JComboBox<String> distCombo = new JComboBox<>();
    private final JComboBox<String> sizeCombo = new JComboBox<>();
    private final JComboBox<String> rangeCombo = new JComboBox<>();
    private final JComboBox<String> coresCombo = new JComboBox<>();
    private final JComboBox<String> threadsCombo = new JComboBox<>();
    private boolean updatingFilters;

    // ---- visualização
    private final JTabbedPane tabs = new JTabbedPane();
    private final Map<String, ChartPanel> chartTabs = new LinkedHashMap<>();
    private final Map<String, Supplier<Chart>> chartBuilders = new LinkedHashMap<>();
    private final SummaryTableModel tableModel = new SummaryTableModel();
    private final JTextArea log = new JTextArea();

    private RunWorker worker;

    public static void open(Path csv) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // mantém o visual padrão
            }
            Dashboard d = new Dashboard();
            d.setVisible(true);
            Path initial = csv;
            if (initial == null) {
                Path def = Path.of("resultados", "principal", "medicoes.csv");
                if (Files.exists(def)) initial = def;
            }
            if (initial != null) d.load(initial);
        });
    }

    private Dashboard() {
        super("AV2 · Ordenação serial × paralela");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 700));
        setSize(1380, 860);
        setLocationRelativeTo(null);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildControls(), buildCenter());
        split.setDividerLocation(300);
        split.setBorder(null);
        setContentPane(split);
        refreshAll();
    }

    private static String defaultThreads() {
        int cores = Runtime.getRuntime().availableProcessors();
        StringBuilder sb = new StringBuilder();
        for (int t = 1; t < cores; t *= 2) sb.append(t).append(',');
        sb.append(cores);
        return sb.toString();
    }

    // ================================================================== layout

    private JComponent buildControls() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        p.add(section("Algoritmos"));
        for (SortAlgorithm a : Algorithms.all()) {
            JCheckBox b = new JCheckBox(a.name() + "  " + a.complexity(), true);
            algBoxes.put(a, b);
            p.add(left(b));
        }
        p.add(Box.createVerticalStrut(8));
        p.add(section("Natureza dos dados"));
        for (Distribution d : Distribution.values()) {
            JCheckBox b = new JCheckBox(d.label(), d == Distribution.ALEATORIO || d == Distribution.INVERSO);
            distBoxes.put(d, b);
            p.add(left(b));
        }
        p.add(Box.createVerticalStrut(8));
        p.add(section("Parâmetros"));
        p.add(field("Tamanhos (Quick, Merge, Counting)", sizesField));
        p.add(field("Tamanhos (Insertion, O(n²))", quadSizesField));
        p.add(field("Intervalos de valores [0, k)", rangesField));
        p.add(field("Threads (execuções paralelas)", threadsField));
        JPanel spins = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        spins.add(new JLabel("Amostras "));
        spins.add(samplesSpinner);
        spins.add(Box.createHorizontalStrut(12));
        spins.add(new JLabel("Aquecimento "));
        spins.add(warmupSpinner);
        p.add(left(spins));
        p.add(left(appendBox));
        p.add(Box.createVerticalStrut(8));

        JPanel run = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        runButton.addActionListener(e -> startRun());
        stopButton.addActionListener(e -> stopRun());
        stopButton.setEnabled(false);
        run.add(runButton);
        run.add(Box.createHorizontalStrut(8));
        run.add(stopButton);
        p.add(left(run));
        p.add(Box.createVerticalStrut(8));
        progress.setStringPainted(true);
        progress.setString("");
        p.add(left(progress));
        p.add(left(status));

        p.add(Box.createVerticalStrut(16));
        p.add(section("Arquivos"));
        JButton openCsv = new JButton("Abrir CSV…");
        openCsv.addActionListener(e -> chooseAndLoad());
        JButton saveCsv = new JButton("Salvar CSV…");
        saveCsv.addActionListener(e -> saveCsv());
        JButton savePng = new JButton("Exportar gráfico (PNG)…");
        savePng.addActionListener(e -> saveCurrentPng());
        JButton saveAll = new JButton("Exportar todos os gráficos…");
        saveAll.addActionListener(e -> exportAll());
        for (JButton b : List.of(openCsv, saveCsv, savePng, saveAll)) {
            p.add(left(b));
            p.add(Box.createVerticalStrut(4));
        }
        p.add(Box.createVerticalGlue());
        JScrollPane sp = new JScrollPane(p);
        sp.setBorder(null);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    private JComponent buildCenter() {
        JPanel center = new JPanel(new BorderLayout());
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        filters.add(labeled("Experimento", expCombo));
        filters.add(labeled("Algoritmo", algCombo));
        filters.add(labeled("Distribuição", distCombo));
        filters.add(labeled("Tamanho", sizeCombo));
        filters.add(labeled("Intervalo", rangeCombo));
        filters.add(labeled("Núcleos", coresCombo));
        filters.add(labeled("Comparar com", threadsCombo));
        for (JComboBox<String> c : List.of(expCombo, algCombo, distCombo, sizeCombo, rangeCombo, coresCombo, threadsCombo)) {
            c.addActionListener(e -> {
                if (!updatingFilters) refreshCharts();
            });
        }
        center.add(filters, BorderLayout.NORTH);

        addChartTab("Tempo × tamanho", this::chartTimeVsSize);
        addChartTab("Speedup × threads", () -> chartSpeedupVsThreads(false));
        addChartTab("Eficiência", () -> chartSpeedupVsThreads(true));
        addChartTab("Speedup × tamanho", this::chartSpeedupVsSize);
        addChartTab("Distribuições", this::chartDistributions);
        addChartTab("Intervalo de valores", this::chartRange);
        addChartTab("Núcleos", this::chartCores);

        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(true);
        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.setFillsViewportHeight(true);
        tabs.addTab("Tabela", new JScrollPane(table));

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        tabs.addTab("Log", new JScrollPane(log));
        tabs.addChangeListener(e -> refreshCharts());
        center.add(tabs, BorderLayout.CENTER);
        return center;
    }

    private void addChartTab(String name, Supplier<Chart> builder) {
        ChartPanel panel = new ChartPanel();
        chartTabs.put(name, panel);
        chartBuilders.put(name, builder);
        tabs.addTab(name, panel);
    }

    private static JComponent section(String title) {
        JLabel l = new JLabel(title);
        l.setFont(l.getFont().deriveFont(Font.BOLD, l.getFont().getSize2D() + 1f));
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return left(l);
    }

    private static JComponent field(String label, JTextField f) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.add(new JLabel(label), BorderLayout.NORTH);
        p.add(f, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return left(p);
    }

    private static JComponent labeled(String label, JComboBox<String> c) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.add(new JLabel(label));
        p.add(c);
        return p;
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        return c;
    }

    // ================================================================== execução

    private void startRun() {
        BenchmarkConfig cfg;
        try {
            cfg = configFromForm();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Parâmetros inválidos", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!appendBox.isSelected()) {
            measurements.clear();
            rows = List.of();
            refreshAll();
        }
        int total = cfg.totalRuns();
        progress.setMaximum(total);
        progress.setValue(0);
        progress.setString("0 / " + total);
        runButton.setEnabled(false);
        stopButton.setEnabled(true);
        status.setText("Executando…");
        appendLog("== Nova execução ==\n  " + cfg + "\n");
        appendLog("Dica: para as medições oficiais do relatório use o modo linha de comando, sem a interface aberta.\n");

        worker = new RunWorker(cfg);
        worker.execute();
    }

    private void stopRun() {
        if (worker != null) worker.runner.cancel();
        status.setText("Interrompendo…");
    }

    /** Executa o benchmark fora da thread de eventos e publica cada amostra para os gráficos. */
    private final class RunWorker extends SwingWorker<Void, Measurement> {
        final BenchmarkRunner runner;

        RunWorker(BenchmarkConfig cfg) {
            runner = new BenchmarkRunner(cfg, new BenchmarkRunner.Listener() {
                @Override
                public void onMeasurement(Measurement m, int done, int total) {
                    RunWorker.this.publish(m);
                }

                @Override
                public void onLog(String message) {
                    SwingUtilities.invokeLater(() -> appendLog(message + System.lineSeparator()));
                }
            });
        }

        @Override
        protected Void doInBackground() {
            runner.run();
            return null;
        }

        @Override
        protected void process(List<Measurement> chunk) {
            measurements.addAll(chunk);
            progress.setValue(progress.getValue() + chunk.size());
            progress.setString(progress.getValue() + " / " + progress.getMaximum());
            Measurement last = chunk.get(chunk.size() - 1);
            status.setText(String.format("%s · %s · n=%s", last.algorithm(), last.configLabel(),
                    Theme.integer(last.size())));
            recompute();
        }

        @Override
        protected void done() {
            runButton.setEnabled(true);
            stopButton.setEnabled(false);
            try {
                get();
                status.setText(runner.isCancelled() ? "Interrompido" : "Concluído");
            } catch (Exception ex) {
                Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                status.setText("Erro: " + cause.getMessage());
                appendLog("ERRO: " + cause + System.lineSeparator());
            }
            recompute();
        }
    }

    private BenchmarkConfig configFromForm() {
        BenchmarkConfig c = new BenchmarkConfig();
        c.experiment = "gui";
        c.algorithms = algBoxes.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
        c.distributions = distBoxes.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
        c.sizes = BenchmarkConfig.parseInts(sizesField.getText());
        c.quadraticSizes = BenchmarkConfig.parseInts(quadSizesField.getText());
        c.valueRanges = BenchmarkConfig.parseInts(rangesField.getText());
        c.threads = BenchmarkConfig.parseInts(threadsField.getText());
        c.samples = (Integer) samplesSpinner.getValue();
        c.warmup = (Integer) warmupSpinner.getValue();
        if (c.algorithms.isEmpty()) throw new IllegalArgumentException("Selecione ao menos um algoritmo.");
        if (c.distributions.isEmpty()) throw new IllegalArgumentException("Selecione ao menos uma distribuição.");
        if (c.valueRanges.isEmpty() || c.threads.isEmpty()) throw new IllegalArgumentException("Informe intervalos e threads.");
        if (c.threads.stream().anyMatch(t -> t < 1 || t > 256)) throw new IllegalArgumentException("Threads devem estar entre 1 e 256.");
        if (c.quadraticSizes.stream().anyMatch(n -> n > 300_000)
                && c.algorithms.stream().anyMatch(SortAlgorithm::quadratic)) {
            throw new IllegalArgumentException("Insertion Sort acima de 300 mil elementos levaria minutos por amostra.");
        }
        return c;
    }

    // ================================================================== arquivos

    private void chooseAndLoad() {
        JFileChooser fc = new JFileChooser(new File("resultados"));
        fc.setFileFilter(new FileNameExtensionFilter("Medições (CSV)", "csv"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            load(fc.getSelectedFile().toPath());
        }
    }

    private void load(Path csv) {
        try {
            List<Measurement> ms = CsvIO.readMeasurements(csv);
            measurements.clear();
            measurements.addAll(ms);
            appendLog("Carregado " + csv + " (" + ms.size() + " medições)\n");
            status.setText("Carregado: " + csv.getFileName());
            recompute();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Não foi possível ler " + csv + ":\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveCsv() {
        JFileChooser fc = new JFileChooser(new File("resultados"));
        fc.setSelectedFile(new File("medicoes_gui.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Path f = fc.getSelectedFile().toPath();
                CsvIO.writeMeasurements(f, measurements);
                CsvIO.writeSummary(f.resolveSibling(f.getFileName().toString().replace(".csv", "") + "_resumo.csv"), rows);
                status.setText("CSV salvo: " + f.getFileName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveCurrentPng() {
        Component sel = tabs.getSelectedComponent();
        if (!(sel instanceof ChartPanel cp) || cp.chart() == null) {
            JOptionPane.showMessageDialog(this, "Selecione uma aba de gráfico.");
            return;
        }
        JFileChooser fc = new JFileChooser(new File("resultados"));
        fc.setSelectedFile(new File(tabs.getTitleAt(tabs.getSelectedIndex()).replaceAll("[^A-Za-z0-9]+", "_") + ".png"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                cp.chart().setHover(null);
                ReportCharts.save(cp.chart(), fc.getSelectedFile().toPath());
                status.setText("PNG salvo: " + fc.getSelectedFile().getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void exportAll() {
        JFileChooser fc = new JFileChooser(new File("resultados"));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                int n = ReportCharts.exportAll(fc.getSelectedFile().toPath(), filteredByExperiment()).size();
                status.setText(n + " gráficos exportados");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void appendLog(String s) {
        log.append(s);
        log.setCaretPosition(log.getDocument().getLength());
    }

    // ================================================================== dados -> filtros -> gráficos

    private void recompute() {
        rows = Analysis.summarize(measurements);
        refreshAll();
    }

    private void refreshAll() {
        refreshFilters();
        tableModel.setRows(rows);
        refreshCharts();
    }

    private void refreshFilters() {
        updatingFilters = true;
        try {
            setItems(expCombo, rows.stream().map(r -> r.key().experiment()).distinct().toList(), null);
            List<Row> exp = filteredByExperiment();
            List<String> algs = new ArrayList<>();
            algs.add(ALL);
            algs.addAll(ChartFactory.algorithms(exp));
            setItems(algCombo, algs, null);
            setItems(distCombo, exp.stream().map(Row::distribution).distinct().sorted().map(Distribution::label).toList(),
                    Distribution.ALEATORIO.label());
            List<String> sizes = new ArrayList<>();
            sizes.add(LARGEST);
            exp.stream().map(Row::size).distinct().sorted().forEach(n -> sizes.add(Theme.integer(n)));
            setItems(sizeCombo, sizes, null);
            List<Integer> ranges = exp.stream().filter(r -> r.distribution() != Distribution.POUCOS_VALORES)
                    .map(Row::valueRange).distinct().sorted().toList();
            if (ranges.isEmpty()) ranges = exp.stream().map(Row::valueRange).distinct().sorted().toList();
            setItems(rangeCombo, ranges.stream().map(Theme::integer).toList(),
                    ranges.isEmpty() ? null : Theme.integer(ChartFactory.mainRange(exp)));
            List<Integer> cores = exp.stream().map(Row::cores).distinct().sorted().toList();
            setItems(coresCombo, cores.stream().map(String::valueOf).toList(),
                    cores.isEmpty() ? null : String.valueOf(cores.get(cores.size() - 1)));
            List<String> threads = new ArrayList<>();
            threads.add(SERIAL);
            exp.stream().filter(r -> r.mode() == Mode.PARALELO).map(Row::threads).distinct().sorted()
                    .forEach(t -> threads.add(t + " threads"));
            setItems(threadsCombo, threads, exp.isEmpty() ? null : ChartFactory.mainThreads(exp) + " threads");
        } finally {
            updatingFilters = false;
        }
    }

    /** Atualiza os itens de um combo preservando a seleção atual quando possível. */
    private static void setItems(JComboBox<String> combo, List<String> items, String preferred) {
        Object current = combo.getSelectedItem();
        List<String> existing = new ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++) existing.add(combo.getItemAt(i));
        if (existing.equals(items)) return;
        combo.removeAllItems();
        for (String s : items) combo.addItem(s);
        if (current != null && items.contains(current.toString())) combo.setSelectedItem(current);
        else if (preferred != null && items.contains(preferred)) combo.setSelectedItem(preferred);
        else if (!items.isEmpty()) combo.setSelectedIndex(0);
    }

    private void refreshCharts() {
        Component sel = tabs.getSelectedComponent();
        for (var e : chartTabs.entrySet()) {
            if (e.getValue() != sel) continue;
            Chart c;
            try {
                c = rows.isEmpty() ? emptyChart() : chartBuilders.get(e.getKey()).get();
            } catch (RuntimeException ex) {
                c = emptyChart();
            }
            e.getValue().setChart(c);
        }
    }

    private static Chart emptyChart() {
        return new LineChart().title("Execute um benchmark ou abra um CSV de medições");
    }

    // ---- seleção atual
    private List<Row> filteredByExperiment() {
        Object e = expCombo.getSelectedItem();
        if (e == null) return rows;
        return rows.stream().filter(r -> r.key().experiment().equals(e)).toList();
    }

    private String selectedAlg() {
        Object a = algCombo.getSelectedItem();
        return a == null || ALL.equals(a) ? null : a.toString();
    }

    private Distribution selectedDist() {
        Object d = distCombo.getSelectedItem();
        return d == null ? Distribution.ALEATORIO : Distribution.fromLabelOrName(d.toString());
    }

    private Integer selectedSize() {
        Object s = sizeCombo.getSelectedItem();
        return s == null || LARGEST.equals(s) ? null : Integer.parseInt(s.toString().replace(".", ""));
    }

    private Integer selectedRange() {
        Object s = rangeCombo.getSelectedItem();
        return s == null ? null : Integer.parseInt(s.toString().replace(".", ""));
    }

    private Integer selectedCores() {
        Object s = coresCombo.getSelectedItem();
        return s == null ? null : Integer.parseInt(s.toString());
    }

    /** null = serial. */
    private Integer selectedThreads() {
        Object s = threadsCombo.getSelectedItem();
        if (s == null || SERIAL.equals(s)) return null;
        return Integer.parseInt(s.toString().replace(" threads", ""));
    }

    private int comparisonThreads(List<Row> rs) {
        Integer t = selectedThreads();
        return t != null ? t : ChartFactory.mainThreads(rs);
    }

    /** Linhas do experimento, núcleos e intervalo selecionados. */
    private List<Row> base(boolean filterRange, boolean filterCores) {
        Integer range = selectedRange();
        Integer cores = selectedCores();
        return filteredByExperiment().stream()
                .filter(r -> !filterRange || range == null || ChartFactory.matchesRange(r, range))
                .filter(r -> !filterCores || cores == null || r.cores() == cores)
                .toList();
    }

    private List<String> selectedAlgs(List<Row> rs, boolean excludeQuadraticWhenAll) {
        String a = selectedAlg();
        if (a != null) return List.of(a);
        List<String> all = ChartFactory.algorithms(rs);
        if (excludeQuadraticWhenAll && all.size() > 1) {
            return all.stream().filter(x -> !x.equals("InsertionSort")).toList();
        }
        return all;
    }

    // ---- construtores das abas
    private Chart chartTimeVsSize() {
        List<Row> rs = base(true, true);
        String alg = selectedAlg();
        if (alg != null) return ChartFactory.timeVsSize(rs, alg, selectedDist());
        Integer t = selectedThreads();
        return ChartFactory.algorithmsTimeVsSize(rs, t == null ? Mode.SERIAL : Mode.PARALELO, t == null ? 1 : t, selectedDist());
    }

    private Chart chartSpeedupVsThreads(boolean efficiency) {
        List<Row> rs = base(true, true);
        String alg = selectedAlg();
        if (!efficiency && "InsertionSort".equals(alg)) {
            return ChartFactory.speedupVsThreadsWithQuadratic(rs, alg, selectedDist(), selectedSize());
        }
        return ChartFactory.speedupVsThreads(rs, selectedAlgs(rs, true), selectedDist(), selectedSize(), efficiency);
    }

    private Chart chartSpeedupVsSize() {
        List<Row> rs = base(true, true);
        return ChartFactory.speedupVsSize(rs, selectedAlgs(rs, false), comparisonThreads(rs), selectedDist());
    }

    private Chart chartDistributions() {
        List<Row> rs = base(true, true);
        String alg = selectedAlg();
        if (alg != null) return ChartFactory.timeByDistribution(rs, alg, comparisonThreads(rs), selectedSize());
        return ChartFactory.speedupByDistribution(rs, selectedAlgs(rs, true), comparisonThreads(rs));
    }

    private Chart chartRange() {
        List<Row> rs = base(false, true);
        Distribution dist = selectedDist();
        long ranges = rs.stream().filter(r -> r.distribution() == dist).mapToInt(Row::valueRange).distinct().count();
        if (ranges < 2) {
            return new LineChart().title("Intervalo de valores")
                    .emptyMessage("Este experimento usa um único intervalo de valores. Escolha o experimento \"intervalo\".");
        }
        Integer size = selectedSize();
        String alg = selectedAlg();
        int n = size != null ? size : rs.stream().mapToInt(Row::size).max().orElse(0);
        if (alg != null) return ChartFactory.timeVsRange(rs, alg, n, selectedDist());
        return ChartFactory.speedupVsRange(rs, ChartFactory.algorithms(rs), n, comparisonThreads(rs), selectedDist());
    }

    private Chart chartCores() {
        List<Row> rs = base(true, false);
        if (rs.stream().mapToInt(Row::cores).distinct().count() < 2) {
            return new LineChart().title("Núcleos disponíveis")
                    .emptyMessage("Este experimento rodou com um único número de núcleos. Escolha o experimento \"nucleos\".");
        }
        String alg = selectedAlg();
        if (alg == null) {
            List<String> algs = ChartFactory.algorithms(rs);
            if (algs.isEmpty()) return emptyChart();
            alg = algs.get(0);
        }
        return ChartFactory.speedupByCores(rs, alg, selectedDist(), selectedSize());
    }

    // ================================================================== tabela

    private static final class SummaryTableModel extends AbstractTableModel {
        private static final String[] COLS = {"Experimento", "Algoritmo", "Modo", "Threads", "Núcleos", "Distribuição",
                "n", "Intervalo", "Amostras", "Média (ms)", "Mediana (ms)", "Desvio (ms)", "CV (%)", "IC 95% (ms)",
                "Speedup", "Eficiência", "Karp–Flatt"};
        private List<Row> data = List.of();

        void setRows(List<Row> rows) {
            if (Objects.equals(rows, data)) return;
            data = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return COLS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLS[c];
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c <= 2 || c == 5 ? String.class : c <= 8 ? Integer.class : Double.class;
        }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = data.get(r);
            return switch (c) {
                case 0 -> row.key().experiment();
                case 1 -> row.algorithm();
                case 2 -> row.mode().name();
                case 3 -> row.threads();
                case 4 -> row.cores();
                case 5 -> row.distribution().label();
                case 6 -> row.size();
                case 7 -> row.valueRange();
                case 8 -> row.stats().n();
                case 9 -> round(row.mean(), 3);
                case 10 -> round(row.stats().median(), 3);
                case 11 -> round(row.stats().stdDev(), 3);
                case 12 -> round(row.stats().cvPercent(), 1);
                case 13 -> round(row.ci95(), 3);
                case 14 -> round(row.speedup(), 2);
                case 15 -> round(row.efficiency(), 2);
                case 16 -> round(row.karpFlatt(), 3);
                default -> null;
            };
        }

        private static Double round(double v, int d) {
            if (Double.isNaN(v)) return null;
            double f = Math.pow(10, d);
            return Math.round(v * f) / f;
        }
    }
}
