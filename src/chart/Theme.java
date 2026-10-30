package chart;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Arrays;
import java.util.Locale;

/** Cores, fontes e formatação numérica (pt-BR) compartilhadas por todos os gráficos. */
public final class Theme {

    private Theme() {
    }

    // superfície e tinta
    public static final Color SURFACE = new Color(0xfcfcfb);
    public static final Color PAGE = new Color(0xf9f9f7);
    public static final Color TEXT = new Color(0x0b0b0b);
    public static final Color TEXT_2 = new Color(0x52514e);
    public static final Color MUTED = new Color(0x898781);
    public static final Color GRID = new Color(0xe1e0d9);
    public static final Color AXIS = new Color(0xc3c2b7);
    public static final Color BORDER = new Color(11, 11, 11, 26);

    /** Paleta categórica (ordem fixa) — validada para daltonismo nos pares adjacentes. */
    public static final Color[] CATEGORICAL = {
            new Color(0x2a78d6), new Color(0xeb6834), new Color(0x1baf7a), new Color(0xeda100),
            new Color(0xe87ba4), new Color(0x008300), new Color(0x6250d6), new Color(0xe34948)
    };

    /** Rampa ordinal de um só tom (claro -> escuro), para quantidades ordenadas (threads, núcleos). */
    public static final Color[] RAMP = {
            new Color(0x86b6ef), new Color(0x5598e7), new Color(0x2a78d6), new Color(0x1c5cab), new Color(0x0d366b)
    };

    /** Cor da linha de base serial: neutra, para não competir com a rampa das execuções paralelas. */
    public static final Color SERIAL = new Color(0x52514e);

    public static final String FONT_FAMILY = pickFont();

    private static String pickFont() {
        String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String wanted : new String[]{"Segoe UI", "Inter", "Helvetica Neue", "Arial"}) {
            if (Arrays.asList(names).contains(wanted)) return wanted;
        }
        return Font.SANS_SERIF;
    }

    public static Font font(int style, float size) {
        return new Font(FONT_FAMILY, style, 1).deriveFont(size);
    }

    /** Cor da i-ésima de {@code count} séries ordinais (no máximo {@link #RAMP}.length). */
    public static Color ramp(int index, int count) {
        if (count <= 1) return RAMP[2];
        if (count >= RAMP.length) return RAMP[Math.min(index, RAMP.length - 1)];
        int pos = (int) Math.round((double) index * (RAMP.length - 1) / (count - 1));
        return RAMP[pos];
    }

    // ---------------------------------------------------------------- números
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(PT_BR);

    /** Número legível em pt-BR: 1.234 · 12,5 · 0,42 · 0,0031. */
    public static String num(double v) {
        if (Double.isNaN(v)) return "–";
        double a = Math.abs(v);
        String pattern;
        if (a >= 100 || a == 0) pattern = "#,##0";
        else if (a >= 10) pattern = "#,##0.#";
        else if (a >= 1) pattern = "#,##0.##";
        else if (a >= 0.1) pattern = "0.###";
        else pattern = "0.####";
        return new DecimalFormat(pattern, SYMBOLS).format(v);
    }

    /** Número com casas decimais fixas em pt-BR. */
    public static String fixed(double v, int decimals) {
        if (Double.isNaN(v)) return "–";
        StringBuilder p = new StringBuilder("#,##0");
        if (decimals > 0) p.append('.').append("0".repeat(decimals));
        return new DecimalFormat(p.toString(), SYMBOLS).format(v);
    }

    /** Inteiro com separador de milhar: 10.000.000. */
    public static String integer(long v) {
        return new DecimalFormat("#,##0", SYMBOLS).format(v);
    }

    /** Forma compacta para eixos: 1 mil · 100 mil · 1 mi · 10 mi. */
    public static String compact(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return num(v / 1e9) + " bi";
        if (a >= 1e6) return num(v / 1e6) + " mi";
        if (a >= 1e3) return num(v / 1e3) + " mil";
        return num(v);
    }
}
