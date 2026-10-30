package chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Base dos gráficos: título, legenda, área de plotagem, eixos e dica (tooltip) ao passar o mouse.
 * O mesmo código desenha na tela (Swing) e em imagens PNG para o relatório.
 */
public abstract class Chart {

    protected String title = "";
    protected String subtitle = "";
    protected String emptyMessage = "Sem dados para esta seleção";
    protected String xLabel = "";
    protected String yLabel = "";
    protected final List<Series> series = new ArrayList<>();

    /** Alvos de hover registrados durante o desenho. */
    protected record Hit(double cx, double cy, Rectangle2D area, Color color, String text) {
    }

    protected final List<Hit> hits = new ArrayList<>();
    private Point hover;

    protected static final int PAD = 20;

    public Chart title(String t) {
        this.title = t;
        return this;
    }

    public Chart subtitle(String s) {
        this.subtitle = s;
        return this;
    }

    /** Texto exibido no lugar do gráfico quando não há séries com dados. */
    public Chart emptyMessage(String s) {
        this.emptyMessage = s;
        return this;
    }

    public Chart axes(String x, String y) {
        this.xLabel = x;
        this.yLabel = y;
        return this;
    }

    public Chart add(Series s) {
        if (s != null && !s.isEmpty()) series.add(s);
        return this;
    }

    public String title() {
        return title;
    }

    public boolean hasData() {
        return series.stream().anyMatch(s -> !s.reference && !s.isEmpty());
    }

    public void setHover(Point p) {
        this.hover = p;
    }

    public final void paint(Graphics2D g0, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setColor(Theme.SURFACE);
            g.fillRect(0, 0, w, h);
            hits.clear();

            double y = PAD;
            g.setFont(Theme.font(Font.BOLD, 16f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(Theme.TEXT);
            g.drawString(title, PAD, (float) (y + fm.getAscent()));
            y += fm.getHeight();
            if (!subtitle.isEmpty()) {
                g.setFont(Theme.font(Font.PLAIN, 12.5f));
                fm = g.getFontMetrics();
                g.setColor(Theme.TEXT_2);
                g.drawString(subtitle, PAD, (float) (y + fm.getAscent()));
                y += fm.getHeight();
            }
            y += 6;
            y = paintLegend(g, PAD, y, w - 2 * PAD);

            if (!hasData()) {
                g.setFont(Theme.font(Font.PLAIN, 13f));
                g.setColor(Theme.MUTED);
                String msg = emptyMessage;
                int tw = g.getFontMetrics().stringWidth(msg);
                g.drawString(msg, (w - tw) / 2f, (float) (y + (h - y) / 2));
                return;
            }
            Rectangle2D area = new Rectangle2D.Double(PAD, y + 8, w - 2 * PAD, h - y - 8 - PAD);
            paintPlot(g, area);
            paintHover(g, w, h);
        } finally {
            g.dispose();
        }
    }

    /** Desenha a área de dados (eixos inclusos) dentro de {@code area}. */
    protected abstract void paintPlot(Graphics2D g, Rectangle2D area);

    /** Uma legenda é sempre exibida para duas ou mais séries. */
    protected double paintLegend(Graphics2D g, double x0, double y0, double width) {
        List<Series> data = series.stream().filter(s -> !s.reference).toList();
        List<Series> refs = series.stream().filter(s -> s.reference).toList();
        if (data.size() < 2 && refs.isEmpty()) return y0;
        List<Series> items = new ArrayList<>(data);
        items.addAll(refs);
        g.setFont(Theme.font(Font.PLAIN, 12.5f));
        FontMetrics fm = g.getFontMetrics();
        double x = x0;
        double y = y0;
        double rowH = fm.getHeight() + 4;
        for (Series s : items) {
            double itemW = 26 + fm.stringWidth(s.name) + 18;
            if (x + itemW > x0 + width && x > x0) {
                x = x0;
                y += rowH;
            }
            double cy = y + rowH / 2;
            paintKey(g, s, x, cy);
            g.setColor(Theme.TEXT_2);
            g.drawString(s.name, (float) (x + 26), (float) (cy + fm.getAscent() / 2.0 - 2));
            x += itemW;
        }
        return y + rowH;
    }

    /** Símbolo da série na legenda (linha + marcador para linhas, quadrado para barras). */
    protected void paintKey(Graphics2D g, Series s, double x, double cy) {
        if (s.reference) {
            g.setColor(Theme.MUTED);
            g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, s.dash, 0f));
            g.draw(new Line2D.Double(x, cy, x + 20, cy));
            return;
        }
        g.setColor(s.color);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(x, cy, x + 20, cy));
        marker(g, s.color, x + 10, cy, 4);
    }

    protected static void marker(Graphics2D g, Color c, double cx, double cy, double r) {
        g.setColor(Theme.SURFACE);
        g.fill(new Ellipse2D.Double(cx - r - 2, cy - r - 2, 2 * r + 4, 2 * r + 4));
        g.setColor(c);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
    }

    private Hit hovered() {
        if (hover == null) return null;
        Hit best = null;
        double bestD = Double.MAX_VALUE;
        for (Hit h : hits) {
            if (h.area() != null) {
                if (h.area().contains(hover)) return h;
                continue;
            }
            double d = hover.distance(h.cx(), h.cy());
            if (d < 18 && d < bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    private void paintHover(Graphics2D g, int w, int h) {
        Hit hit = hovered();
        if (hit == null || hit.text() == null) return;
        if (hit.area() == null) {
            g.setColor(Theme.SURFACE);
            g.fill(new Ellipse2D.Double(hit.cx() - 8, hit.cy() - 8, 16, 16));
            g.setColor(hit.color());
            g.setStroke(new BasicStroke(2f));
            g.draw(new Ellipse2D.Double(hit.cx() - 6, hit.cy() - 6, 12, 12));
            g.fill(new Ellipse2D.Double(hit.cx() - 3, hit.cy() - 3, 6, 6));
        }
        String[] lines = hit.text().split("\n");
        Font bold = Theme.font(Font.BOLD, 12.5f);
        Font plain = Theme.font(Font.PLAIN, 12.5f);
        FontMetrics fb = g.getFontMetrics(bold);
        FontMetrics fp = g.getFontMetrics(plain);
        int tw = 0;
        for (int i = 0; i < lines.length; i++) {
            tw = Math.max(tw, (i == 0 ? fb : fp).stringWidth(lines[i]));
        }
        int lh = fp.getHeight();
        int bw = tw + 24 + 14;
        int bh = lines.length * lh + 16;
        double bx = hit.cx() + 14;
        double by = hit.cy() - bh - 10;
        if (bx + bw > w - 4) bx = hit.cx() - bw - 14;
        if (by < 4) by = hit.cy() + 14;
        if (by + bh > h - 4) by = h - bh - 4;
        RoundRectangle2D box = new RoundRectangle2D.Double(bx, by, bw, bh, 10, 10);
        g.setColor(new Color(0, 0, 0, 28));
        g.fill(new RoundRectangle2D.Double(bx + 1, by + 2, bw, bh, 10, 10));
        g.setColor(Color.WHITE);
        g.fill(box);
        g.setColor(Theme.BORDER);
        g.setStroke(new BasicStroke(1f));
        g.draw(box);
        g.setColor(hit.color());
        g.fill(new RoundRectangle2D.Double(bx + 12, by + 8 + (lh - 10) / 2.0, 10, 10, 3, 3));
        for (int i = 0; i < lines.length; i++) {
            g.setFont(i == 0 ? bold : plain);
            g.setColor(i == 0 ? Theme.TEXT : Theme.TEXT_2);
            g.drawString(lines[i], (float) (bx + 12 + (i == 0 ? 16 : 0)), (float) (by + 8 + i * lh + fp.getAscent()));
        }
    }

    // ---------------------------------------------------------------- eixos

    /** Escala linear ou logarítmica de valor para pixel. */
    protected static final class Scale {
        final boolean log;
        final double min;
        final double max;
        double p0;
        double p1;

        Scale(boolean log, double min, double max) {
            this.log = log;
            this.min = min;
            this.max = max;
        }

        double map(double v) {
            double t;
            if (log) {
                t = (Math.log10(v) - Math.log10(min)) / (Math.log10(max) - Math.log10(min));
            } else {
                t = (v - min) / (max - min);
            }
            return p0 + t * (p1 - p0);
        }

        boolean contains(double v) {
            return !Double.isNaN(v) && v >= min * (1 - 1e-9) && v <= max * (1 + 1e-9) && (!log || v > 0);
        }

        List<Double> ticks() {
            List<Double> t = new ArrayList<>();
            if (log) {
                int lo = (int) Math.floor(Math.log10(min) + 1e-9);
                int hi = (int) Math.ceil(Math.log10(max) - 1e-9);
                int decades = hi - lo;
                double[] mult = decades <= 2 ? new double[]{1, 2, 5} : new double[]{1};
                int stride = decades > 8 ? 2 : 1;
                for (int e = lo; e <= hi; e += stride) {
                    for (double m : mult) {
                        double v = m * Math.pow(10, e);
                        if (v >= min * (1 - 1e-9) && v <= max * (1 + 1e-9)) t.add(v);
                    }
                }
            } else {
                double step = niceStep((max - min) / 5);
                for (double v = Math.ceil(min / step) * step; v <= max + step * 1e-6; v += step) {
                    t.add(Math.abs(v) < step * 1e-9 ? 0 : v);
                }
            }
            return t;
        }

        static Scale fit(boolean log, double dataMin, double dataMax, boolean zeroBased) {
            if (log) {
                double lo = Math.max(dataMin, 1e-6);
                double hi = Math.max(dataMax, lo * 1.0001);
                return new Scale(true, niceFloorLog(lo), niceCeilLog(hi));
            }
            double lo = zeroBased ? Math.min(0, dataMin) : dataMin;
            double hi = dataMax;
            if (hi <= lo) hi = lo + 1;
            double step = niceStep((hi - lo) / 5);
            lo = Math.floor(lo / step) * step;
            hi = Math.ceil(hi * 1.02 / step) * step;
            return new Scale(false, lo, hi);
        }

        static double niceStep(double raw) {
            if (raw <= 0) return 1;
            double e = Math.pow(10, Math.floor(Math.log10(raw)));
            double f = raw / e;
            double nice = f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10;
            return nice * e;
        }

        static double niceFloorLog(double v) {
            double e = Math.pow(10, Math.floor(Math.log10(v)));
            double f = v / e;
            return (f >= 5 ? 5 : f >= 2 ? 2 : 1) * e;
        }

        static double niceCeilLog(double v) {
            double e = Math.pow(10, Math.floor(Math.log10(v)));
            double f = v / e;
            return (f <= 1.0000001 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * e;
        }
    }

    /** Linhas de grade horizontais + rótulos do eixo y; devolve a largura usada pelos rótulos. */
    protected static double yTickWidth(Graphics2D g, Scale ys, java.util.function.DoubleFunction<String> fmt) {
        g.setFont(Theme.font(Font.PLAIN, 11.5f));
        FontMetrics fm = g.getFontMetrics();
        double w = 0;
        for (double t : ys.ticks()) w = Math.max(w, fm.stringWidth(fmt.apply(t)));
        return w;
    }

    protected static void paintYAxis(Graphics2D g, Rectangle2D plot, Scale ys,
                                     java.util.function.DoubleFunction<String> fmt, String label) {
        g.setFont(Theme.font(Font.PLAIN, 11.5f));
        FontMetrics fm = g.getFontMetrics();
        for (double t : ys.ticks()) {
            double py = ys.map(t);
            g.setColor(Theme.GRID);
            g.setStroke(new BasicStroke(1f));
            g.draw(new Line2D.Double(plot.getMinX(), py, plot.getMaxX(), py));
            String s = fmt.apply(t);
            g.setColor(Theme.MUTED);
            g.drawString(s, (float) (plot.getMinX() - 8 - fm.stringWidth(s)), (float) (py + fm.getAscent() / 2.0 - 1));
        }
        if (label != null && !label.isEmpty()) {
            Graphics2D r = (Graphics2D) g.create();
            r.setFont(Theme.font(Font.PLAIN, 12f));
            r.setColor(Theme.TEXT_2);
            int lw = r.getFontMetrics().stringWidth(label);
            r.translate(PAD + 10, plot.getCenterY() + lw / 2.0);
            r.rotate(-Math.PI / 2);
            r.drawString(label, 0, 0);
            r.dispose();
        }
    }

    protected static void paintXLabel(Graphics2D g, Rectangle2D plot, String label, double bottom) {
        if (label == null || label.isEmpty()) return;
        g.setFont(Theme.font(Font.PLAIN, 12f));
        g.setColor(Theme.TEXT_2);
        int lw = g.getFontMetrics().stringWidth(label);
        g.drawString(label, (float) (plot.getCenterX() - lw / 2.0), (float) bottom);
    }
}
