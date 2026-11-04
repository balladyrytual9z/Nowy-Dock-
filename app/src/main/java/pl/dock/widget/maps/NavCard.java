package pl.dock.widget.maps;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Toast;

import java.util.Locale;

import pl.dock.widget.NightAware;
import pl.dock.widget.Ui;

/**
 * Kafelek "Nawigacja" na ekranie glownym Docka (styl CarPlay).
 *
 * Dane pochodza z Organic Maps przez usluge dostepnosci (patrz NavData):
 * manewr + dystans, nazwa ulicy, ETA, pozostaly czas i dystans, predkosc
 * oraz limit predkosci. Kafelka NIE da sie wypelnic zywa mapa innej aplikacji
 * (Android na to nie pozwala) - dlatego pokazuje dane, a dotkniecie otwiera
 * pelna nawigacje w Organic Maps.
 */
public class NavCard extends View implements NightAware, NavData.Listener {

    private final float d;
    private boolean night = true;
    private boolean started;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bg = new RectF();

    public NavCard(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setContentDescription("Nawigacja");
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openOrganicMaps();
            }
        });
    }

    private void openOrganicMaps() {
        try {
            Intent i = getContext().getPackageManager()
                    .getLaunchIntentForPackage(OmAccessibilityService.OM_PACKAGE);
            if (i == null) {
                Toast.makeText(getContext(),
                        "Zainstaluj Organic Maps, aby korzystac z nawigacji",
                        Toast.LENGTH_LONG).show();
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void setNight(boolean n) {
        night = n;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        started = true;
        NavData.get().addListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        NavData.get().removeListener(this);
        started = false;
        super.onDetachedFromWindow();
    }

    @Override
    public void onNavChanged(NavData data) {
        if (started) postInvalidate();
    }

    // ---------- rysowanie ----------

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        bg.set(0, 0, w, h);
        Ui.ringCard(cv, bg, 30f * d, 1.5f * d, night, true);

        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(11f * d);
        t.setColor(night ? 0xFF8E8E93 : 0xFF6B7686);
        cv.drawText("NAWIGACJA", 20f * d, 26f * d, t);

        NavData nd = NavData.get();
        if (!nd.isFresh()) {
            drawIdle(cv, w, h);
            return;
        }
        drawLive(cv, w, h, nd);
    }

    private void drawIdle(Canvas cv, int w, int h) {
        int accent = 0xFF0A84FF;
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(15f * d);
        t.setColor(night ? 0xFFFFFFFF : 0xFF10131A);
        cv.drawText("Brak aktywnej trasy", w / 2f, h / 2f - 6f * d, t);

        t.setTypeface(Typeface.DEFAULT);
        t.setTextSize(11f * d);
        t.setColor(night ? 0xFF8E8E93 : 0xFF6B7686);
        cv.drawText("Dotknij, aby otworzyć Organic Maps", w / 2f, h / 2f + 14f * d, t);

        t.setTextSize(10f * d);
        t.setColor(accent);
        String hint = OmAccessibilityService.isConnected()
                ? "Odczyt z Organic Maps: włączony"
                : "Włącz odczyt w Ustawieniach → Mapy";
        cv.drawText(hint, w / 2f, h / 2f + 34f * d, t);
    }

    private void drawLive(Canvas cv, int w, int h, NavData nd) {
        int fg = night ? 0xFFFFFFFF : 0xFF10131A;
        int dim = night ? 0xFF8E8E93 : 0xFF6B7686;
        int accent = 0xFF0A84FF;

        // --- lewa czesc: manewr + dystans + ulica ---
        float arrowX = 58f * d;
        float arrowY = h * 0.42f;
        drawTurn(cv, arrowX, arrowY, 26f * d, nd.turn(), accent);

        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(30f * d);
        t.setColor(fg);
        String dist = nd.turnDistance().isEmpty() ? "—" : nd.turnDistance();
        cv.drawText(dist, 108f * d, arrowY + 10f * d, t);

        if (!nd.street().isEmpty()) {
            t.setTypeface(Typeface.DEFAULT);
            t.setTextSize(14f * d);
            t.setColor(fg);
            cv.drawText(ellipsize(nd.street(), 22), 108f * d, arrowY + 34f * d, t);
        }

        // --- dol: ETA / czas / dystans ---
        float by = h - 26f * d;
        t.setTextAlign(Paint.Align.LEFT);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextSize(16f * d);
        t.setColor(accent);
        cv.drawText(nd.eta().isEmpty() ? "—" : nd.eta(), 20f * d, by, t);

        t.setTypeface(Typeface.DEFAULT);
        t.setTextSize(11f * d);
        t.setColor(dim);
        cv.drawText(nd.remainTime(), 84f * d, by, t);
        cv.drawText(nd.remainDistance(), 160f * d, by, t);

        // --- prawa czesc: kółko predkosci z limitem ---
        float r = Math.min(46f * d, h * 0.28f);
        drawSpeed(cv, w - r - 24f * d, h * 0.46f, r, nd.speed(), nd.speedUnit(),
                nd.speedLimit(), accent, fg, dim);
    }

    private void drawTurn(Canvas cv, float cx, float cy, float s, String turn, int color) {
        Path path = new Path();
        float u = s;
        path.moveTo(0f, -0.9f * u);
        path.lineTo(0.78f * u, 0f);
        path.lineTo(0.28f * u, 0f);
        path.lineTo(0.28f * u, 0.9f * u);
        path.lineTo(-0.28f * u, 0.9f * u);
        path.lineTo(-0.28f * u, 0f);
        path.lineTo(-0.78f * u, 0f);
        path.close();

        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        cv.save();
        cv.translate(cx, cy);
        cv.rotate(angleFor(turn));
        cv.drawPath(path, p);
        cv.restore();
    }

    /** Kat obrotu strzalki na podstawie opisu manewru z Organic Maps. */
    private float angleFor(String turn) {
        if (turn == null) return 0f;
        String s = turn.toLowerCase(Locale.ROOT);
        if (s.contains("zawróć") || s.contains("u-turn") || s.contains("uturn")) return 180f;
        if (s.contains("lewo") || s.contains("left")) return -90f;
        if (s.contains("prawo") || s.contains("right")) return 90f;
        return 0f;
    }

    private void drawSpeed(Canvas cv, float cx, float cy, float r, String speed, String unit,
                           String limit, int accent, int fg, int dim) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(night ? 0xE6000000 : 0xE6FFFFFF);
        cv.drawCircle(cx, cy, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * d);
        p.setColor(accent);
        cv.drawCircle(cx, cy, r, p);

        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextSize(24f * d);
        t.setColor(fg);
        cv.drawText(speed.isEmpty() ? "—" : speed, cx, cy + 6f * d, t);

        t.setTypeface(Typeface.DEFAULT);
        t.setTextSize(9f * d);
        t.setColor(dim);
        cv.drawText(unit == null || unit.isEmpty() ? "km/h" : unit, cx, cy + 20f * d, t);

        // Tarcza z limitem predkosci (jak w CarPlay) - tylko gdy droga ma limit.
        if (limit != null && !limit.isEmpty()) {
            float lr = 15f * d;
            float lx = cx + r * 0.80f;
            float ly = cy - r * 0.80f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(lx, ly, lr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3.2f * d);
            p.setColor(0xFFE60012);
            cv.drawCircle(lx, ly, lr - 1.8f * d, p);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            t.setTextSize(14f * d);
            t.setColor(0xFF10131A);
            cv.drawText(limit, lx, ly + 5f * d, t);
        }
    }

    private static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
