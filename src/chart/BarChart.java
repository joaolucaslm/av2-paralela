package chart;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Colunas agrupadas: uma categoria por grupo, uma série por coluna dentro do grupo.
 * Colunas finas (até 24px), canto superior arredondado, base reta e 2px de espaço entre vizinhas.
 */
public final class BarChart extends Chart {

    private final List<String> categories = new ArrayList<>();
    private DoubleFunction<String> yFmt = Theme::num;
    private Double refY;
    private String refLabel;

    public BarChart categories(List<String> cats) {
        categories.clear();
        categories.addAll(cats);
        return this;
    }

    public BarChart yFormat(DoubleFunction<String> f) {
        yFmt = f;
        return this;
    }

    /** Linha horizontal de referência (ex.: speedup = 1). */
    public BarChart reference(double y, String label) {
        refY = y;
        refLabel = label;
        return this;
    }

    @Override
    protected void paintKey(Graphics2D g, Series s, double x, double cy) {
        if (s.reference) {
            super.paintKey(g, s, x, cy);
            return;
        }
        g.setColor(s.color);
        g.fill(new RoundRectangle2D.Double(x + 4, cy - 6, 12, 12, 4, 4));
    }

    @Override
    protected void paintPlot(Graphics2D g, Rectangle2D area) {
        List<Series> data = series.stream().filter(s -> !s.reference).toList();
        double hi = refY != null ? refY : 0;
        for (Series s : data) {
            for (Series.Pt p : s.points) {
                if (!Double.isNaN(p.y())) hi = Math.max(hi, p.y() + p.err());
            }
        }
        Scale ys = Scale.fit(false, 0, hi, true);

        g.setFont(Theme.font(Font.PLAIN, 11.5f));
        FontMetrics fm = g.getFontMetrics();
        double bottom = area.getMaxY() - (xLabel.isEmpty() ? 0 : 20) - fm.getHeight() - 6;
        ys.p0 = bottom;
        ys.p1 = area.getMinY();
        double left = area.getMinX() + (yLabel.isEmpty() ? 0 : 22) + yTickWidth(g, ys, yFmt) + 10;
        g.setFont(Theme.font(Font.PLAIN, 12f));
        double right = area.getMaxX() - (refLabel != null ? g.getFontMetrics().stringWidth(refLabel) + 12 : 4);
        Rectangle2D plot = new Rectangle2D.Double(left, ys.p1, right - left, bottom - ys.p1);

        paintYAxis(g, plot, ys, yFmt, yLabel);

        int nc = Math.max(1, categories.size());
        int ns = Math.max(1, data.size());
        double groupW = plot.getWidth() / nc;
        double gap = 2;
        double barW = Math.min(24, (groupW * 0.72 - gap * (ns - 1)) / ns);
        double clusterW = ns * barW + (ns - 1) * gap;

        for (int si = 0; si < data.size(); si++) {
            Series s = data.get(si);
            for (Series.Pt p : s.points) {
                int ci = (int) Math.round(p.x());
                if (ci < 0 || ci >= nc || Double.isNaN(p.y())) continue;
                double x = plot.getMinX() + ci * groupW + (groupW - clusterW) / 2 + si * (barW + gap);
                double top = ys.map(p.y());
                double h = bottom - top;
                g.setColor(s.color);
                g.fill(roundedTop(x, top, barW, h, Math.min(4, h)));
                if (p.err() > 0) {
                    double y1 = ys.map(Math.max(0, p.y() - p.err()));
                    double y2 = ys.map(p.y() + p.err());
                    if (y1 - y2 >= 3) {
                        g.setColor(Theme.TEXT_2);
                        g.setStroke(new BasicStroke(1f));
                        double cx = x + barW / 2;
                        g.draw(new Line2D.Double(cx, y1, cx, y2));
                        g.draw(new Line2D.Double(cx - 3, y2, cx + 3, y2));
                        g.draw(new Line2D.Double(cx - 3, y1, cx + 3, y1));
                    }
                }
                Rectangle2D hitArea = new Rectangle2D.Double(x - gap, Math.min(top, bottom - 12) - 6, barW + 2 * gap,
                        Math.max(h, 12) + 6);
                hits.add(new Hit(x + barW / 2, top, hitArea, s.color,
                        p.tip() != null ? p.tip() : s.name + "\n" + Theme.num(p.y())));
            }
        }

        g.setColor(Theme.AXIS);
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(plot.getMinX(), bottom, plot.getMaxX(), bottom));
        if (refY != null) {
            double py = ys.map(refY);
            g.setColor(Theme.MUTED);
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 5f}, 0f));
            g.draw(new Line2D.Double(plot.getMinX(), py, plot.getMaxX(), py));
            g.setFont(Theme.font(Font.PLAIN, 12f));
            g.drawString(refLabel, (float) (plot.getMaxX() + 8), (float) (py + g.getFontMetrics().getAscent() / 2.0 - 2));
        }
        g.setFont(Theme.font(Font.PLAIN, 12f));
        FontMetrics cfm = g.getFontMetrics();
        for (int ci = 0; ci < categories.size(); ci++) {
            String c = categories.get(ci);
            double cx = plot.getMinX() + (ci + 0.5) * groupW;
            g.setColor(Theme.TEXT_2);
            g.drawString(c, (float) (cx - cfm.stringWidth(c) / 2.0), (float) (bottom + 6 + cfm.getAscent()));
        }
        paintXLabel(g, plot, xLabel, area.getMaxY() - 4);
    }

    private static Path2D roundedTop(double x, double y, double w, double h, double r) {
        Path2D p = new Path2D.Double();
        p.moveTo(x, y + h);
        p.lineTo(x, y + r);
        p.quadTo(x, y, x + r, y);
        p.lineTo(x + w - r, y);
        p.quadTo(x + w, y, x + w, y + r);
        p.lineTo(x + w, y + h);
        p.closePath();
        return p;
    }
}
