package pl.dock.widget.maps;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

import java.util.Locale;

import pl.dock.widget.Ui;

/**
 * Warstwa "chrome" nakladki CarPlay rysowana NAD Organic Maps.
 *
 * Rysuje trzy elementy:
 *  - karte manewru (góra-lewo): strzalka, dystans do manewru, nazwa ulicy,
 *  - pasek ETA (dol-lewo): godzina dojazdu, pozostaly czas, pozostaly dystans,
 *  - kolo predkosci (dol-prawo) z tarcza limitu, gdy droga go ma.
 *
 * Widok jest nieklikalny (okno z FLAG_NOT_TOUCHABLE) - dotkniecia przechodza
 * do Organic Maps. Klikalny jest osobny pasek doka.
 */
public class NavChromeView extends View implements NavData.Listener {

    private final float d;
    private final boolean dockLeft;
    private final float dockPx;
    private final float statusBar;
    private boolean night = true;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public NavChromeView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        dockLeft = Ui.dockLeft(c);
        dockPx = Ui.dp(c, Ui.DOCK_OFFSET_DP);
        statusBar = statusBarHeight(c);
        setContentDescription("Nakladka nawigacji");
    }

    private static float statusBarHeight(Context c) {
        try {
            int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (id > 0) return c.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
        }
        return 24f * c.getResources().getDisplayMetrics().density;
    }

    public void setNight(boolean n) {
        night = n;
        invalidate();
    }

    @Override
    public void onNavChanged(NavData data) {
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        NavData nd = NavData.get();
        if (!nd.isFresh()) return;

        float pad = 14f * d;
        float x0 = (dockLeft ? dockPx : 0f) + pad;
        float x1 = w - (dockLeft ? 0f : dockPx) - pad;
        if (x1 <= x0) return;

        drawManeuver(cv, nd, x0, statusBar + 10f * d, x1);
        drawEtaBar(cv, nd, x0, h - pad);
        drawSpeed(cv, nd, x1, h - pad);
    }

    // ---------------------------------------------------------------- karta manewru

    private void drawManeuver(Canvas cv, NavData nd, float x0, float y0, float x1) {
        float cardW = Math.min(340f * d, (x1 - x0) * 0.58f);
        float cardH = 92f * d;
        r.set(x0, y0, x0 + cardW, y0 + cardH);
        card(cv, r, 22f * d);

        int fg = night ? 0xFFFFFFFF : 0xFF10131A;
        int dim = night ? 0xFF8E8E93 : 0xFF6B7686;
        int accent = 0xFF0A84FF;

        drawTurn(cv, x0 + 46f * d, y0 + cardH / 2f, 22f * d, nd.turn(), accent);

        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(26f * d);
        t.setColor(fg);
        String dist = nd.turnDistance().isEmpty() ? "—" : nd.turnDistance();
        cv.drawText(dist, x0 + 86f * d, y0 + 44f * d, t);

        t.setTypeface(Typeface.DEFAULT);
        t.setTextSize(13f * d);
        t.setColor(nd.street().isEmpty() ? dim : fg);
        String street = nd.street().isEmpty() ? "Jedź prosto" : ellipsize(nd.street(), 24);
        cv.drawText(street, x0 + 86f * d, y0 + 68f * d, t);
    }

    // ---------------------------------------------------------------- pasek ETA

    private void drawEtaBar(Canvas cv, NavData nd, float x0, float bottom) {
        float barH = 62f * d;
        float barW = 430f * d;
        r.set(x0, bottom - barH, x0 + barW, bottom);
        card(cv, r, 20f * d);

        int fg = night ? 0xFFFFFFFF : 0xFF10131A;
        int dim = night ? 0xFF8E8E93 : 0xFF6B7686;
        int accent = 0xFF0A84FF;

        float ty = bottom - barH / 2f + 7f * d;

        t.setTextAlign(Paint.Align.LEFT);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextSize(21f * d);
        t.setColor(accent);
        cv.drawText(nd.eta().isEmpty() ? "—" : nd.eta(), x0 + 18f * d, ty, t);

        t.setTypeface(Typeface.DEFAULT);
        t.setTextSize(13f * d);
        t.setColor(dim);
        cv.drawText(nd.remainTime(), x0 + 108f * d, ty, t);
        cv.drawText(nd.remainDistance(), x0 + 200f * d, ty, t);
    }

    // ---------------------------------------------------------------- kolo predkosci

    private void drawSpeed(Canvas cv, NavData nd, float right, float bottom) {
        float rad = 50f * d;
        float cx = right - rad;
        float cy = bottom - rad;

        int fg = night ? 0xFFFFFFFF : 0xFF10131A;
        int dim = night ? 0xFF8E8E93 : 0xFF6B7686;
        int accent = 0xFF0A84FF;

        p.setStyle(Paint.Style.FILL);
        p.setColor(night ? 0xE6101012 : 0xE6FFFFFF);
        cv.drawCircle(cx, cy, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * d);
        p.setColor(accent);
        cv.drawCircle(cx, cy, rad, p);

        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextSize(26f * d);
        t.setColor(fg);
        cv.drawText(nd.speed().isEmpty() ? "—" : nd.speed(), cx, cy + 7f * d, t);

        t.setTypeface(Typeface.DEFAULT);
        t.setTextSize(9f * d);
        t.setColor(dim);
        cv.drawText(nd.speedUnit().isEmpty() ? "km/h" : nd.speedUnit(), cx, cy + 22f * d, t);

        if (!nd.speedLimit().isEmpty()) {
            float lr = 16f * d;
            float lx = cx + rad * 0.80f;
            float ly = cy - rad * 0.80f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(lx, ly, lr, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3.4f * d);
            p.setColor(0xFFE60012);
            cv.drawCircle(lx, ly, lr - 1.8f * d, p);
            t.setTextAlign(Paint.Align.CENTER);
            t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            t.setTextSize(15f * d);
            t.setColor(0xFF10131A);
            cv.drawText(nd.speedLimit(), lx, ly + 5.5f * d, t);
        }
    }

    // ---------------------------------------------------------------- pomocnicze

    private void card(Canvas cv, RectF rr, float radius) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(night ? 0xE6101012 : 0xE6FFFFFF);
        cv.drawRoundRect(rr, radius, radius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.2f * d);
        p.setColor(night ? 0x33FFFFFF : 0x22000000);
        cv.drawRoundRect(rr, radius, radius, p);
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

    private float angleFor(String turn) {
        if (turn == null) return 0f;
        String s = turn.toLowerCase(Locale.ROOT);
        if (s.contains("zawróć") || s.contains("u-turn") || s.contains("uturn")) return 180f;
        if (s.contains("lewo") || s.contains("left")) return -90f;
        if (s.contains("prawo") || s.contains("right")) return 90f;
        return 0f;
    }

    private static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
