package pl.dock.widget;

/**
 * Przyblizenie krzywej Plancka (Tanner Helland) - Kelvin -> sRGB.
 * Dokladnosc +/- 5% w zakresie 2200-6500 K, wystarczajaca dla overlayu.
 */
public final class ColorTemp {

    public static final double MIN_K = 2200;
    public static final double MAX_K = 6500;

    /** Kotwica maksymalnego ciepla nakladki Night Shift jak w iOS (~2700 K). */
    public static final double WARM_K = 2700;

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

    /**
     * Docelowy punkt bieli nakladki dla glebi ciepla f
     * (0 = neutralna bial, 1 = WARM_K). Kanaly interpolowane liniowo,
     * dlatego suwak "Chlodniejsze/Cieplejsze" dziala postrzeganie rowno
     * po calym zakresie. {r, g, b} w 0..255.
     */
    public static int[] warmthWhitePoint(float f) {
        f = Math.max(0f, Math.min(1f, f));
        int[] warm = kelvinToRgb(WARM_K);
        int g = 255 - Math.round((255 - warm[1]) * f);
        int b = 255 - Math.round((255 - warm[2]) * f);
        return new int[]{ 255, clamp(g), clamp(b) };
    }

    /**
     * Kolor nakladki dla glebi ciepla f: {alpha, r, g, b}, kazdy 0..255.
     * Kolor jest NASYCANY i liczony tak, aby bialy piksel trafial dokladnie
     * w warmthWhitePoint(f):
     *     255*(1-a/255) + C*(a/255) = cel   =>   C = 255 - (255-cel)*255/a
     * Stad ekran ZOLCI sie zamiast bladnac. Alpha = 0.70*f: przy f -> 0
     * nakladka zanika do zera (plynny fade przy wylaczaniu), przy f = 1
     * daje glebokie cieplo WARM_K (2700 K).
     */
    public static int[] overlayTint(float f) {
        f = Math.max(0f, Math.min(1f, f));
        if (f <= 0.0005f) return new int[]{ 0, 255, 255, 255 };
        int a = Math.max(1, Math.round(0.70f * f * 255f));
        int[] w = warmthWhitePoint(f);
        int g = 255 - Math.round((255 - w[1]) * 255f / a);
        int b = 255 - Math.round((255 - w[2]) * 255f / a);
        return new int[]{ a, 255, clamp(g), clamp(b) };
    }

    private static int clamp(double v) {
        long r = Math.round(v);
        return (int) Math.max(0, Math.min(255, r));
    }
}
