package chart;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Uma série de dados: nome (legenda), cor e pontos com barra de erro e texto de dica. */
public final class Series {

    /** Ponto (x, y) com meia-largura de erro (IC 95%) e texto exibido ao passar o mouse. */
    public record Pt(double x, double y, double err, String tip) {
    }

    public final String name;
    public final Color color;
    public final List<Pt> points = new ArrayList<>();
    /** Séries de referência (ideal, y = 1...) são desenhadas tracejadas e discretas. */
    public boolean reference;
    /** Padrão de traço das séries de referência. */
    public float[] dash = {6f, 5f};

    public Series(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    public static Series reference(String name) {
        Series s = new Series(name, Theme.MUTED);
        s.reference = true;
        return s;
    }

    /** Referência pontilhada, para diferenciar de uma segunda referência no mesmo gráfico. */
    public static Series referenceDotted(String name) {
        Series s = reference(name);
        s.dash = new float[]{1.5f, 4f};
        return s;
    }

    public Series add(double x, double y, double err, String tip) {
        points.add(new Pt(x, y, err, tip));
        return this;
    }

    public Series add(double x, double y) {
        return add(x, y, 0, null);
    }

    public boolean isEmpty() {
        return points.isEmpty();
    }
}
