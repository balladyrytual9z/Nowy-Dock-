package pl.dock.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * Tlo okna panelu lacznosci = ramka szkla doka 1:1 (DockView.onDraw, galez
 * "glass"): korpus z rozblyskiem u gory, wewnetrzny cien na dole, jasny rim
 * u gory (refrakcja swiatla na krawedzi) i ukosna obwodka od lewego gornego
 * rogu - efekt "swiatla padajacego na szklo", jak na dokku i jego ikonach.
 * Kolor bazowy i promien rogow jak w Ui.popupBg (34 dp), wiec geometria okna
 * panelu bez zmian. Czysty Drawable: zero onDraw w ConnectivityPanel
 * (strażnik ConnectivityPanelGuardTest.panelJestFrameworkowy).
 */
public class PanelGlassFrame extends Drawable {

    private final float d;
    private final boolean night;
    private final float rad;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public PanelGlassFrame(Context c, boolean nightMode) {
        d = c.getResources().getDisplayMetrics().density;
        night = nightMode;
        rad = 34f * d;   // 1:1 z Ui.popupBg
    }

    @Override
    public void draw(Canvas cv) {
        Rect b = getBounds();
        float l = b.left;
        float t = b.top;
        float rr = b.right;
        float bt = b.bottom;
        // 0) korpus bazowy (kolor jak popupBg - pod swiatlami szkla)
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(night ? 0xF20E0E0F : 0xF2F5F5F7);
        rect.set(l, t, rr, bt);
        cv.drawRoundRect(rect, rad, rad, p);
        // 1) korpus szkla: rozblysk u gory, wyrazniejsi poblask na dole (glebia)
        p.setShader(new LinearGradient(l, t, l, bt,
                new int[]{0x24FFFFFF, 0x08FFFFFF, 0x05FFFFFF, 0x1AFFFFFF},
                new float[]{0f, 0.16f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        cv.drawRoundRect(rect, rad, rad, p);
        // 2) wewnetrzny cien na dole - szklo "unosi sie" nad tlem
        p.setShader(new LinearGradient(l, bt - 14f * d, l, bt,
                new int[]{0x00000000, 0x30000000},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        rect.set(l + 2f * d, bt - 14f * d, rr - 2f * d, bt - 2f * d);
        cv.drawRoundRect(rect, rad * 0.5f, rad * 0.5f, p);
        // 3) jasny rim u gory - refrakcja swiatla na krawedzi szkla
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * d);
        p.setShader(new LinearGradient(l, t, l, t + 22f * d,
                new int[]{0xE6FFFFFF, 0x4DFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        rect.set(l + 1f * d, t + 1f * d, rr - 1f * d, bt - 1f * d);
        cv.drawRoundRect(rect, rad - 1f * d, rad - 1f * d, p);
        // 4) obwodka - swiatlo z lewego gornego rogu, plynnie przechodzace
        //    w otoczenie (1:1 z obwodka doka)
        p.setStrokeWidth(1.4f * d);
        p.setShader(new LinearGradient(l, t, rr, bt,
                new int[]{0xB3FFFFFF, 0x40FFFFFF, 0x14FFFFFF, 0x40FFFFFF},
                new float[]{0f, 0.3f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        rect.set(l + d * 0.7f, t + d * 0.7f, rr - d * 0.7f, bt - d * 0.7f);
        cv.drawRoundRect(rect, rad - d * 0.7f, rad - d * 0.7f, p);
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
    }

    @Override
    public void setAlpha(int alpha) {
        // stala krycosc - kolor bazowy ma wlasna alfe
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // bez filtrow kolorow
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

