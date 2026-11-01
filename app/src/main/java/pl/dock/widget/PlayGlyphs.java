
package pl.dock.widget;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

// Symbole sterowania odtwarzaniem rysowane kodem (bez plików graficznych)
public class PlayGlyphs {

    public static void play(Canvas c, float cx, float cy, float s, Paint p) {
        Path t = new Path();
        t.moveTo(cx - 0.30f * s, cy - 0.42f * s);
        t.lineTo(cx + 0.44f * s, cy);
        t.lineTo(cx - 0.30f * s, cy + 0.42f * s);
        t.close();
        Paint.Style old = p.getStyle();
        float ow = p.getStrokeWidth();
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setStrokeWidth(0.10f * s);
        c.drawPath(t, p);
        p.setStyle(old);
        p.setStrokeWidth(ow);
    }

    public static void pause(Canvas c, float cx, float cy, float s, Paint p) {
        float w = 0.24f * s;
        float h = 0.80f * s;
        RectF r = new RectF();
        r.set(cx - 0.34f * s, cy - h / 2f, cx - 0.34f * s + w, cy + h / 2f);
        c.drawRoundRect(r, w / 3f, w / 3f, p);
        r.set(cx + 0.10f * s, cy - h / 2f, cx + 0.10f * s + w, cy + h / 2f);
        c.drawRoundRect(r, w / 3f, w / 3f, p);
    }

    // dwa trójkąty: forward = ▶▶, w przeciwnym razie ◀◀
    public static void skip(Canvas c, float cx, float cy, float s, boolean forward, Paint p) {
        float dir = forward ? 1f : -1f;
        for (int i = 0; i < 2; i++) {
            float ox = cx + dir * (i == 0 ? -0.26f : 0.26f) * s;
            Path t = new Path();
            t.moveTo(ox - dir * 0.26f * s, cy - 0.36f * s);
            t.lineTo(ox + dir * 0.26f * s, cy);
            t.lineTo(ox - dir * 0.26f * s, cy + 0.36f * s);
            t.close();
            Paint.Style old = p.getStyle();
            float ow = p.getStrokeWidth();
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(0.08f * s);
            c.drawPath(t, p);
            p.setStyle(old);
            p.setStrokeWidth(ow);
        }
    }

    // zastępcza okładka: kolorowe słupki jak w ikonie "Odtwarzane"
    public static void artPlaceholder(Canvas c, RectF r, float radius, boolean night) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(night ? 0xFF232326 : 0xFFE6E6EB);
        c.drawRoundRect(r, radius, radius, p);
        float cx = r.centerX();
        float cy = r.centerY();
        float u = Math.min(r.width(), r.height()) / 100f;
        float[] hh = {28, 46, 60, 40, 22};
        float[] xx = {-24, -12, 0, 12, 24};
        for (int i = 0; i < 5; i++) {
            Paint b = new Paint(Paint.ANTI_ALIAS_FLAG);
            b.setShader(new LinearGradient(0, cy - hh[i] * u, 0, cy + hh[i] * u,
                    0xFFFF375F, 0xFFFF9F0A, Shader.TileMode.CLAMP));
            float x = cx + xx[i] * u;
            c.drawRoundRect(new RectF(x - 3.4f * u, cy - hh[i] * u * 0.5f, x + 3.4f * u,
                    cy + hh[i] * u * 0.5f), 3.4f * u, 3.4f * u, b);
        }
    }

    public static String time(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }
}







