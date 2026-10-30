package bench;

import java.util.Arrays;

/** Estatística descritiva e regressão linear simples. */
public final class Stats {

    private Stats() {
    }

    /** Valores críticos t de Student bicaudais (95%) para 1..30 graus de liberdade. */
    private static final double[] T95 = {
            12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
            2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
            2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042
    };

    public static double t95(int degreesOfFreedom) {
        if (degreesOfFreedom <= 0) return Double.NaN;
        if (degreesOfFreedom <= T95.length) return T95[degreesOfFreedom - 1];
        return 1.96;
    }

    /** Resumo de uma amostra de tempos. */
    public record Summary(int n, double mean, double median, double stdDev, double min, double max,
                          double ci95) {

        /** Coeficiente de variação em %. */
        public double cvPercent() {
            return mean == 0 ? 0 : 100.0 * stdDev / mean;
        }
    }

    public static Summary summarize(double[] values) {
        int n = values.length;
        if (n == 0) return new Summary(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        double[] v = values.clone();
        Arrays.sort(v);
        double sum = 0;
        for (double x : v) sum += x;
        double mean = sum / n;
        double sq = 0;
        for (double x : v) sq += (x - mean) * (x - mean);
        double sd = n > 1 ? Math.sqrt(sq / (n - 1)) : 0;
        double median = (n % 2 == 1) ? v[n / 2] : (v[n / 2 - 1] + v[n / 2]) / 2.0;
        double ci = n > 1 ? t95(n - 1) * sd / Math.sqrt(n) : 0;
        return new Summary(n, mean, median, sd, v[0], v[n - 1], ci);
    }

    /** Resultado de y = intercept + slope·x. */
    public record Regression(double slope, double intercept, double r2, int n) {
    }

    public static Regression linearRegression(double[] x, double[] y) {
        int n = x.length;
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) {
            mx += x[i];
            my += y[i];
        }
        mx /= n;
        my /= n;
        double sxy = 0, sxx = 0, syy = 0;
        for (int i = 0; i < n; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        double slope = sxx == 0 ? Double.NaN : sxy / sxx;
        double intercept = my - slope * mx;
        double r2 = (sxx == 0 || syy == 0) ? 1 : (sxy * sxy) / (sxx * syy);
        return new Regression(slope, intercept, r2, n);
    }
}
