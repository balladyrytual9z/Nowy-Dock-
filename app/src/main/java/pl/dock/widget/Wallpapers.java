
package pl.dock.widget;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;

// Zestaw tapet w stylu CarPlay (kaÅ¼da w wersji dziennej i nocnej). Tryb nocny: czerÅ.
public class Wallpapers {

    public static class Style {
        public final String id;
        public final String name;
        final int nBase, nG1, nG2, dBase, dG1, dG2;
        final boolean arcs;

        Style(String id, String name, int nBase, int nG1, int nG2, int dBase, int dG1, int dG2,
              boolean arcs) {
            this.id = id;
            this.name = name;
            this.nBase = nBase;
            this.nG1 = nG1;
            this.nG2 = nG2;
            this.dBase = dBase;
            this.dG1 = dG1;
            this.dG2 = dG2;
            this.arcs = arcs;
        }
    }

    public static final Style[] ALL = {
            new Style("graphite", "Grafit", 0xFF000000, 0xFF26262A, 0xFF18181B,
                    0xFFE8E8ED, 0xFFFAFAFC, 0xFFD6D6DC, true),
            new Style("carbon", "WÄgiel", 0xFF000000, 0xFF111113, 0xFF0A0A0B,
                    0xFFECECF0, 0xFFF7F7FA, 0xFFE0E0E5, false),
            new Style("aurora", "Zorza", 0xFF000000, 0xFF0D2A22, 0xFF10192A,
                    0xFFDDEEE8, 0xFFC4E6DA, 0xFFC8D4EE, true),
            new Style("ocean", "Ocean", 0xFF000000, 0xFF0B2133, 0xFF09121F,
                    0xFFDCE8F4, 0xFFBFD8F0, 0xFFD2E0F2, true),
            new Style("ember", "Å»ar", 0xFF000000, 0xFF30150D, 0xFF1F0F0B,
                    0xFFF4E6DE, 0xFFF0D2C2, 0xFFEBDCD2, true),
            new Style("violet", "Fiolet", 0xFF000000, 0xFF220F34, 0xFF150B23,
                    0xFFE9E1F4, 0xFFDACBF0, 0xFFE2D8F0, true),
            new Style("dune", "Wydma", 0xFF000000, 0xFF2B2415, 0xFF1A150D,
                    0xFFF2ECDD, 0xFFE8DDBF, 0xFFEFE6D0, true)
    };

    public static Style byId(String id) {
        for (Style s : ALL) if (s.id.equals(id)) return s;
        return ALL[0];
    }

    private static int clear(int c) {
        return c & 0x00FFFFFF;
    }

    public static void paint(Canvas cv, int w, int h, Style st, boolean night) {
        int base = night ? st.nBase : st.dBase;
        int g1 = night ? st.nG1 : st.dG1;
        int g2 = night ? st.nG2 : st.dG2;
        cv.drawColor(base);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(w * 0.30f, -h * 0.18f, h * 1.25f,
                new int[]{g1, (g1 & 0x00FFFFFF) | 0x99000000, clear(g1)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        p.setShader(new RadialGradient(w * 0.88f, h * 1.02f, h * 0.9f,
                new int[]{g2, (g2 & 0x00FFFFFF) | 0xAA000000, clear(g2)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, p);
        if (st.arcs) {
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f);
            p.setColor(night ? 0x22FFFFFF : 0x22000000);
            cv.drawCircle(w * 0.95f, h * 1.35f, h * 0.98f, p);
            cv.drawCircle(w * 0.02f, h * 0.62f, h * 0.72f, p);
        }
        if (night) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(new LinearGradient(0, h * 0.78f, 0, h, 0x00000000, 0x88000000,
                    Shader.TileMode.CLAMP));
            cv.drawRect(0, h * 0.78f, w, h, p);
        }
    }
}







