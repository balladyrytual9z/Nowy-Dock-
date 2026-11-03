package pl.dock.widget;

/**
 * Przyblizenie krzywej Plancka (Tanner Helland) - Kelvin -> sRGB.
 * Dokladnosc +/- 5% w zakresie 2200-6500 K, wystarczajaca dla overlayu.
 */
public final class ColorTemp {

    public static final double MIN_K = 2200;
    public static final double MAX_K = 6500;

    private ColorTemp() {}

    /** {r, g, b} w 0..255. */
    public static int[] kelvinToRgb(double kelvin) {
        double t = Math.max(MIN_K, Math.min(MAX_K, kelvin)) / 100.0;
        double r, g, b;

        if (t <= 66.0) {
            r = 255.0;
        } else {
            r = 329.698727446 * Math.pow(t - 60.0, -0.1332047592);
        }

        if (t <= 66.0) {
            g = 99.4708025861 * Math.log(t) - 161.1195681661;
        } else {
            g = 288.1221695283 * Math.pow(t - 60.0, -0.0755148492);
        }

        if (t >= 66.0) {
            b = 255.0;
        } else if (t <= 19.0) {
            b = 0.0;
        } else {
            b = 138.5177312231 * Math.log(t - 10.0) - 305.0447927307;
        }

        return new int[]{ clamp(r), clamp(g), clamp(b) };
    }

    /** 0 = neutralny (6500 K), 1 = swieca (2200 K). */
    public static double fractionToKelvin(float fraction) {
        fraction = Math.max(0f, Math.min(1f, fraction));
        return MAX_K - (MAX_K - MIN_K) * fraction;
    }

    private static int clamp(double v) {
        long r = Math.round(v);
        return (int) Math.max(0, Math.min(255, r));
    }
}
