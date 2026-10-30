package chart;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.DoubleFunction;

/** Gráfico de linhas com eixos lineares ou logarítmicos, barras de erro e rótulos diretos. */
public final class LineChart extends Chart {

    private boolean logX;
    private boolean logY;
    private boolean zeroY = true;
    private double[] xTicks;
    private DoubleFunction<String> xFmt = Theme::num;
    private DoubleFunction<String> yFmt = Theme::num;
    private Double yMin;
    private Double yMax;

    public LineChart logX(boolean v) {
        logX = v;
        return this;
    }

    public LineChart logY(boolean v) {
        logY = v;
        return this;
    }

    public LineChart zeroY(boolean v) {
        zeroY = v;
        return this;
    }

    public LineChart xTicks(double... ticks) {
        xTicks = ticks;
        return this;
    }

    public LineChart xFormat(DoubleFunction<String> f) {
        xFmt = f;
        return this;
    }

    public LineChart yFormat(DoubleFunction<String> f) {
        yFmt = f;
        return this;
    }

    /** Fixa os limites do eixo y (útil para comparar gráficos lado a lado). */
    public LineChart yRange(Double min, Double max) {
        yMin = min;
        yMax = max;
        return this;
    }

    /** Extremos de y (com barras de erro) das séries de dados, para alinhar escalas. */
    public double[] dataYExtent() {
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (Series s : series) {
            if (s.reference) continue;
            for (Series.Pt p : s.points) {
                if (Double.isNaN(p.y())) continue;
                double low = p.y() - p.err();
                lo = Math.min(lo, logY && low <= 0 ? p.y() : low);
                hi = Math.max(hi, p.y() + p.err());
            }
        }
        return new double[]{lo, hi};
    }

    @Override
    protected void paintPlot(Graphics2D g, Rectangle2D area) {
        // ---- domínio
        double xlo = Double.MAX_VALUE, xhi = -Double.MAX_VALUE;
        for (Series s : series) {
            if (s.reference) continue;
            for (Series.Pt p : s.points) {
                if (Double.isNaN(p.y()) || (logX && p.x() <= 0)) continue;
                xlo = Math.min(xlo, p.x());
                xhi = Math.max(xhi, p.x());
            }
        }
        if (xTicks != null && xTicks.length > 0) {
            xlo = Math.min(xlo, xTicks[0]);
            xhi = Math.max(xhi, xTicks[xTicks.length - 1]);
        }
        double[] ye = dataYExtent();
        double ylo = ye[0], yhi = ye[1];
        for (Series s : series) {
            if (!s.reference) continue;
            for (Series.Pt p : s.points) {
                if (p.x() >= xlo - 1e-9 && p.x() <= xhi + 1e-9 && !Double.isNaN(p.y())) {
                    ylo = Math.min(ylo, p.y());
                    yhi = Math.max(yhi, p.y());
                }
            }
        }
        if (zeroY && !logY) ylo = Math.min(0, Math.max(ylo, 0));
        if (yMin != null) ylo = yMin;
        if (yMax != null) yhi = yMax;

        Scale xs;
        if (logX) {
            xs = Scale.fit(true, xlo, xhi, false);
        } else if (xTicks != null) {
            double span = Math.max(xhi - xlo, 1);
            xs = new Scale(false, xlo - span * 0.03, xhi + span * 0.03);
        } else {
            xs = Scale.fit(false, xlo, xhi, false);
        }
        Scale ys = Scale.fit(logY, ylo, yhi, zeroY && !logY);

        // ---- layout
        g.setFont(Theme.font(Font.PLAIN, 11.5f));
        FontMetrics fm = g.getFontMetrics();
        double bottom = area.getMaxY() - (xLabel.isEmpty() ? 0 : 20) - fm.getHeight() - 6;
        ys.p0 = bottom;
        ys.p1 = area.getMinY();
        double left = area.getMinX() + (yLabel.isEmpty() ? 0 : 22) + yTickWidth(g, ys, yFmt) + 10;

        List<Series> data = series.stream().filter(s -> !s.reference).toList();
        List<double[]> endLabels = endLabelPositions(data, ys);
        g.setFont(Theme.font(Font.PLAIN, 12f));
        FontMetrics lfm = g.getFontMetrics();
        double labelW = 0;
        if (endLabels != null) {
            for (Series s : data) labelW = Math.max(labelW, lfm.stringWidth(s.name));
        }
        double right = area.getMaxX() - (labelW > 0 ? labelW + 12 : 4);
        xs.p0 = left;
        xs.p1 = right;
        Rectangle2D plot = new Rectangle2D.Double(left, ys.p1, right - left, bottom - ys.p1);

        // ---- eixos
        paintYAxis(g, plot, ys, yFmt, yLabel);
        g.setColor(Theme.AXIS);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(plot.getMinX(), bottom, plot.getMaxX(), bottom));
        g.setFont(Theme.font(Font.PLAIN, 11.5f));
        List<Double> xt = new ArrayList<>();
        if (xTicks != null) for (double t : xTicks) xt.add(t);
        else xt = xs.ticks();
        double lastRight = -1e9;
        for (double t : xt) {
            if (!xs.contains(t)) continue;
            double px = xs.map(t);
            String s = xFmt.apply(t);
            int sw = fm.stringWidth(s);
            if (px - sw / 2.0 < lastRight + 6) continue; // evita rótulos sobrepostos
            g.setColor(Theme.AXIS);
            g.draw(new Line2D.Double(px, bottom, px, bottom + 4));
            g.setColor(Theme.MUTED);
            g.drawString(s, (float) (px - sw / 2.0), (float) (bottom + 6 + fm.getAscent()));
            lastRight = px + sw / 2.0;
        }
        paintXLabel(g, plot, xLabel, area.getMaxY() - 4);

        // ---- referências (tracejadas, discretas)
        Shape oldClip = g.getClip();
        g.clip(new Rectangle2D.Double(plot.getMinX(), plot.getMinY() - 2, plot.getWidth() + 2, plot.getHeight() + 4));
        for (Series s : series) {
            if (!s.reference) continue;
            Path2D path = polyline(s, xs, ys);
            g.setColor(Theme.MUTED);
            g.setStroke(new BasicStroke(1.75f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, s.dash, 0f));
            g.draw(path);
        }
        // ---- séries de dados
        for (Series s : data) {
            g.setColor(s.color);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(polyline(s, xs, ys));
            Color errColor = new Color(s.color.getRed(), s.color.getGreen(), s.color.getBlue(), 150);
            g.setStroke(new BasicStroke(1.25f));
            for (Series.Pt p : s.points) {
                if (!visible(p, xs, ys) || p.err() <= 0) continue;
                double px = xs.map(p.x());
                double lo = p.y() - p.err();
                double y1 = ys.map(ys.log && lo <= 0 ? ys.min : Math.max(lo, ys.min));
                double y2 = ys.map(Math.min(p.y() + p.err(), ys.max));
                if (Math.abs(y1 - y2) < 3) continue;
                g.setColor(errColor);
                g.draw(new Line2D.Double(px, y1, px, y2));
                g.draw(new Line2D.Double(px - 3, y1, px + 3, y1));
                g.draw(new Line2D.Double(px - 3, y2, px + 3, y2));
            }
        }
        g.setClip(oldClip);
        for (Series s : data) {
            for (Series.Pt p : s.points) {
                if (!visible(p, xs, ys)) continue;
                double px = xs.map(p.x());
                double py = ys.map(p.y());
                marker(g, s.color, px, py, 4);
                hits.add(new Hit(px, py, null, s.color, p.tip() != null ? p.tip() : s.name + "\n" + Theme.num(p.y())));
            }
        }

        // ---- rótulos diretos no fim das linhas (só se não colidirem)
        g.setFont(Theme.font(Font.PLAIN, 12f));
        if (endLabels != null) {
            for (int i = 0; i < data.size(); i++) {
                double[] pos = endLabels.get(i);
                if (pos == null) continue;
                g.setColor(Theme.TEXT_2);
                g.drawString(data.get(i).name, (float) (xs.map(pos[0]) + 10), (float) (pos[1] + lfm.getAscent() / 2.0 - 2));
            }
        }
    }

    private static boolean visible(Series.Pt p, Scale xs, Scale ys) {
        return xs.contains(p.x()) && ys.contains(p.y());
    }

    private static Path2D polyline(Series s, Scale xs, Scale ys) {
        Path2D path = new Path2D.Double();
        boolean started = false;
        for (Series.Pt p : s.points) {
            if (Double.isNaN(p.y()) || (xs.log && p.x() <= 0) || (ys.log && p.y() <= 0)) {
                started = false;
                continue;
            }
            double px = xs.map(p.x());
            double py = ys.map(p.y());
            if (!started) {
                path.moveTo(px, py);
                started = true;
            } else {
                path.lineTo(px, py);
            }
        }
        return path;
    }

    /**
     * Posições (x, y em pixel) dos rótulos diretos ou null se não devem ser usados:
     * só para 2 a 4 séries e somente quando os rótulos não se sobrepõem.
     */
    private List<double[]> endLabelPositions(List<Series> data, Scale ys) {
        if (data.size() < 2 || data.size() > 4) return null;
        List<double[]> pos = new ArrayList<>();
        for (Series s : data) {
            Series.Pt last = s.points.stream().filter(p -> !Double.isNaN(p.y()) && ys.contains(p.y()))
                    .max(Comparator.comparingDouble(Series.Pt::x)).orElse(null);
            pos.add(last == null ? null : new double[]{last.x(), ys.map(last.y())});
        }
        List<double[]> sorted = pos.stream().filter(p -> p != null).sorted(Comparator.comparingDouble(p -> p[1])).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (Math.abs(sorted.get(i)[0] - sorted.get(i - 1)[0]) < 1e-9 && sorted.get(i)[1] - sorted.get(i - 1)[1] < 15) {
                return null;
            }
        }
        return pos;
    }
}
