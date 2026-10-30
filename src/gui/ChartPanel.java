package gui;

import chart.Chart;
import chart.Theme;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Painel Swing que desenha um {@link Chart} e mostra a dica do ponto sob o mouse. */
public final class ChartPanel extends JPanel {

    private Chart chart;

    public ChartPanel() {
        setBackground(Theme.SURFACE);
        setPreferredSize(new Dimension(900, 560));
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (chart != null) {
                    chart.setHover(e.getPoint());
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (chart != null) {
                    chart.setHover(null);
                    repaint();
                }
            }
        };
        addMouseMotionListener(mouse);
        addMouseListener(mouse);
    }

    public void setChart(Chart chart) {
        this.chart = chart;
        repaint();
    }

    public Chart chart() {
        return chart;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (chart != null) {
            chart.paint((Graphics2D) g, getWidth(), getHeight());
        }
    }
}
