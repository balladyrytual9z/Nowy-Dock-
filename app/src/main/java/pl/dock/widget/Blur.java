
package pl.dock.widget;

import android.graphics.Bitmap;

// Szybkie rozmycie (kilka przebiegów filtra pudełkowego) małej bitmapy: tło "liquid glass"
public class Blur {

    private static int cl(int v, int max) {
        return v < 0 ? 0 : (v > max ? max : v);
    }

    public static void blur(Bitmap b, int radius, int passes) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[] a = new int[w * h];
        int[] t = new int[w * h];
        b.getPixels(a, 0, w, 0, 0, w, h);
        for (int i = 0; i < passes; i++) {
            horizontal(a, t, w, h, radius);
            vertical(t, a, w, h, radius);
        }
        b.setPixels(a, 0, w, 0, 0, w, h);
    }

    private static void horizontal(int[] src, int[] dst, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            int row = y * w;
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int x = -r; x <= r; x++) {
                int p = src[row + cl(x, w - 1)];
                sa += p >>> 24;
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int x = 0; x < w; x++) {
                dst[row + x] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int p1 = src[row + cl(x + r + 1, w - 1)];
                int p0 = src[row + cl(x - r, w - 1)];
                sa += (p1 >>> 24) - (p0 >>> 24);
                sr += ((p1 >> 16) & 255) - ((p0 >> 16) & 255);
                sg += ((p1 >> 8) & 255) - ((p0 >> 8) & 255);
                sb += (p1 & 255) - (p0 & 255);
            }
        }
    }

    private static void vertical(int[] src, int[] dst, int w, int h, int r) {
        int div = 2 * r + 1;
        for (int x = 0; x < w; x++) {
            int sa = 0, sr = 0, sg = 0, sb = 0;
            for (int y = -r; y <= r; y++) {
                int p = src[cl(y, h - 1) * w + x];
                sa += p >>> 24;
                sr += (p >> 16) & 255;
                sg += (p >> 8) & 255;
                sb += p & 255;
            }
            for (int y = 0; y < h; y++) {
                dst[y * w + x] = ((sa / div) << 24) | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int p1 = src[cl(y + r + 1, h - 1) * w + x];
                int p0 = src[cl(y - r, h - 1) * w + x];
                sa += (p1 >>> 24) - (p0 >>> 24);
                sr += ((p1 >> 16) & 255) - ((p0 >> 16) & 255);
                sg += ((p1 >> 8) & 255) - ((p0 >> 8) & 255);
                sb += (p1 & 255) - (p0 & 255);
            }
        }
    }
}







