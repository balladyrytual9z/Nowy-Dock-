package pl.dock.widget.maps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Przykrywka "ladowanie" w stylu iOS: zaslona w kolorze tla + obracajace sie koleczko
 * z osmioru kresek. Pokazywana tylko wtedy, gdy launcher NIE ZNA jeszcze zadnej pozycji
 * (pierwsze uruchomienie, brak zapamietanego miejsca) - zeby uzytkownik nie patrzyl na
 * skaczaca mape i szukanie fixa. Caly proces idzie w tle, przykrywka znika w momencie,
 * gdy pozycja jest znana. Blokuje dotyk, wiec mapa pod nia nie ucieka.
 */
public class LoadingCover extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float d;
    private float angle;
    private boolean light;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            angle = (angle + 12f) % 360f;
            invalidate();
            if (getVisibility() == VISIBLE && isAttachedToWindow()) postDelayed(this, 40);
        }
    };

    public LoadingCover(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(true);         // dotyk nie przechodzi na mape
        setFocusable(true);
        // Ten sam powod co w panelu lacznosci: na Mali-T720 nowo dodane poddrzewo
        // wlasnych widokow nie zawsze odtwarza sie z RenderNode - wlasna warstwa to leczy.
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    /** Motyw: na jasnym tle ciemna zaslona i odwrotnie (kolor zyje z reszta UI). */
    public void setLight(boolean l) {
        light = l;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(tick);
        postDelayed(tick, 40);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas cv) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(light ? 0xF2F2F7 : 0xFF0E0F11);
        cv.drawRect(0, 0, getWidth(), getHeight(), p);

        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float dl = 9f * d, sw = 3.2f * d, rad = 15f * d;
        p.setColor(light ? 0xFF6B7686 : 0xFFE8EDF2);
        for (int i = 0; i < 8; i++) {
            p.setAlpha(Math.max(26, 255 - i * 32));
            cv.save();
            cv.rotate(angle + i * 45f, cx, cy);
            r.set(cx - sw / 2f, cy - rad - dl, cx + sw / 2f, cy - rad);
            cv.drawRoundRect(r, sw / 2f, sw / 2f, p);
            cv.restore();
        }
    }
}
