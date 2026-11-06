
package pl.dock.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Calendar;
import java.util.TimeZone;

// Wbudowane widżety jak w CarPlay: data, kalendarz, zegary świata, pogoda, zachód słońca.
// Każdy odświeża się raz na minutę i tylko wtedy, gdy jest widoczny.
public class CardWidgets {

    static final String[] DAYS = {"Niedz.", "Pon.", "Wt.", "Śr.", "Czw.", "Pt.", "Sob."};
    static final String[] MONTHS = {"Sty", "Lut", "Mar", "Kwi", "Maj", "Cze", "Lip", "Sie", "Wrz",
            "Paź", "Lis", "Gru"};
    static final String[] MONTHS_FULL = {"STYCZEŃ", "LUTY", "MARZEC", "KWIECIEŃ", "MAJ", "CZERWIEC",
            "LIPIEC", "SIERPIEŃ", "WRZESIEŃ", "PAŹDZIERNIK", "LISTOPAD", "GRUDZIEŃ"};

    // wspólna baza: tryb, odświeżanie co minutę, rysowanie tekstu
    abstract static class Base extends View implements NightAware, GlassAware {
        final float d;
        final float sd;
        boolean night = true;
        boolean glass;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF r = new RectF();
        private final Handler h = new Handler(Looper.getMainLooper());
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                onMinute();
                invalidate();
                schedule();
            }
        };

        Base(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            sd = c.getResources().getDisplayMetrics().scaledDensity;
        }

        void onMinute() {
        }

        private void schedule() {
            long now = System.currentTimeMillis();
            h.postDelayed(tick, 60000L - (now % 60000L) + 50L);
        }

        @Override
        public void setNight(boolean n) {
            night = n;
            invalidate();
        }

        @Override
        public void setGlass(boolean g) {
            glass = g;
            invalidate();
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            h.removeCallbacks(tick);
            schedule();
            onMinute();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            h.removeCallbacks(tick);
        }

        int fg() {
            return night ? 0xFFFFFFFF : 0xFF111114;
        }

        int sub() {
            return night ? 0xFF98989F : 0xFF6C6C70;
        }

        void card(Canvas cv) {
            r.set(0, 0, getWidth(), getHeight());
            if (glass) {
                Ui.glassCard(cv, r, 30f * d, 1.5f * d, night);
            } else {
                Ui.ringCard(cv, r, 30f * d, 1.5f * d, night, false);
            }
        }

        // tekst; size w sp
        void text(Canvas cv, String s, float x, float y, float size, int color, boolean bold,
                  Paint.Align al) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            p.setTextSize(size * sd);
            p.setColor(color);
            p.setTextAlign(al);
            cv.drawText(s, x, y, p);
        }
    }

    // ------------------------------------------------------------ data (duża cyfra)
    public static class DateCard extends Base {
        public DateCard(Context c) {
            super(c);
            setContentDescription("Data");
        }

        @Override
        protected void onDraw(Canvas cv) {
            card(cv);
            Calendar c = Calendar.getInstance();
            float w = getWidth();
            float h = getHeight();
            text(cv, DAYS[c.get(Calendar.DAY_OF_WEEK) - 1], w * 0.10f, h * 0.20f, 30, 0xFFFF453A, true,
                    Paint.Align.LEFT);
            p.setTextSize(30 * sd);
            float dw = p.measureText(DAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + " ");
            text(cv, MONTHS[c.get(Calendar.MONTH)], w * 0.10f + dw, h * 0.20f, 30, sub(), true,
                    Paint.Align.LEFT);
            String day = String.valueOf(c.get(Calendar.DAY_OF_MONTH));
            float size = 150f;
            p.setTypeface(Typeface.DEFAULT_BOLD);
            p.setTextSize(size * sd);
            while (p.measureText(day) > w * 0.84f && size > 40) {
                size -= 6f;
                p.setTextSize(size * sd);
            }
            text(cv, day, w / 2f, h * 0.84f, size, fg(), true, Paint.Align.CENTER);
        }
    }

    // ------------------------------------------------------------ kalendarz miesięczny
    public static class CalendarCard extends Base {
        public CalendarCard(Context c) {
            super(c);
            setContentDescription("Kalendarz");
        }

        @Override
        protected void onDraw(Canvas cv) {
            card(cv);
            Calendar now = Calendar.getInstance();
            int today = now.get(Calendar.DAY_OF_MONTH);
            Calendar first = (Calendar) now.clone();
            first.set(Calendar.DAY_OF_MONTH, 1);
            int offset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7;   // poniedziałek = 0
            int days = now.getActualMaximum(Calendar.DAY_OF_MONTH);
            float w = getWidth();
            float h = getHeight();
            float pad = w * 0.07f;
            text(cv, MONTHS_FULL[now.get(Calendar.MONTH)], pad, h * 0.13f, 17, 0xFFFF453A, true,
                    Paint.Align.LEFT);
            String[] hd = {"P", "W", "Ś", "C", "P", "S", "N"};
            float cw = (w - 2 * pad) / 7f;
            for (int i = 0; i < 7; i++) {
                text(cv, hd[i], pad + cw * i + cw / 2f, h * 0.26f, 14, i >= 5 ? sub() : fg(), true,
                        Paint.Align.CENTER);
            }
            float rowH = (h * 0.97f - h * 0.30f) / 6f;
            for (int day = 1; day <= days; day++) {
                int idx = offset + day - 1;
                int col = idx % 7;
                int row = idx / 7;
                float cx = pad + cw * col + cw / 2f;
                float cy = h * 0.30f + rowH * row + rowH / 2f;
                boolean weekend = col >= 5;
                if (day == today) {
                    p.setStyle(Paint.Style.FILL);
                    p.setShader(null);
                    p.setColor(0xFFFF453A);
                    cv.drawCircle(cx, cy, Math.min(cw, rowH) * 0.46f, p);
                }
                text(cv, String.valueOf(day), cx, cy + 6f * d, 15,
                        day == today ? 0xFFFFFFFF : (weekend ? sub() : fg()), true,
                        Paint.Align.CENTER);
            }
        }
    }

    // ------------------------------------------------------------ zegary świata
    public static class WorldClockCard extends Base {
        private static final String[] CODES = {"CUP", "TOK", "SYD", "PAR"};
        private static final String[] ZONES = {"America/Los_Angeles", "Asia/Tokyo",
                "Australia/Sydney", "Europe/Paris"};

        public WorldClockCard(Context c) {
            super(c);
            setContentDescription("Zegary świata");
        }

        @Override
        protected void onDraw(Canvas cv) {
            card(cv);
            float w = getWidth();
            float h = getHeight();
            float cell = Math.min(w, h) / 2f;
            for (int i = 0; i < 4; i++) {
                float cx = (i % 2) * cell + cell / 2f;
                float cy = (i / 2) * cell + cell / 2f;
                mini(cv, cx, cy, cell * 0.40f, CODES[i], ZONES[i]);
            }
        }

        private void mini(Canvas cv, float cx, float cy, float rad, String code, String zone) {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone(zone));
            int hr = c.get(Calendar.HOUR_OF_DAY);
            boolean day = hr >= 6 && hr < 18;
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(day ? 0xFFFFFFFF : 0xFF2C2C2E);
            cv.drawCircle(cx, cy, rad, p);
            int ink = day ? 0xFF111114 : 0xFFFFFFFF;
            String[] nums = {"12", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"};
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12.0 - Math.PI / 2;
                text(cv, nums[i], cx + (float) Math.cos(a) * rad * 0.78f,
                        cy + (float) Math.sin(a) * rad * 0.78f + 4f * d, 10, ink, false,
                        Paint.Align.CENTER);
            }
            text(cv, code, cx, cy - rad * 0.28f, 10, day ? 0xFF8E8E93 : 0xFF98989F, true,
                    Paint.Align.CENTER);
            float min = c.get(Calendar.MINUTE);
            float hh = (hr % 12) + min / 60f;
            double ha = Math.PI * 2 * hh / 12.0 - Math.PI / 2;
            double ma = Math.PI * 2 * min / 60.0 - Math.PI / 2;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(ink);
            p.setStrokeWidth(2.4f * d);
            cv.drawLine(cx, cy, cx + (float) Math.cos(ha) * rad * 0.5f,
                    cy + (float) Math.sin(ha) * rad * 0.5f, p);
            p.setStrokeWidth(1.8f * d);
            cv.drawLine(cx, cy, cx + (float) Math.cos(ma) * rad * 0.72f,
                    cy + (float) Math.sin(ma) * rad * 0.72f, p);
        }
    }

    // ------------------------------------------------------------ pogoda
    public static class WeatherCard extends Base {
        public WeatherCard(Context c) {
            super(c);
            setContentDescription("Pogoda");
        }

        @Override
        void onMinute() {
            WeatherData.refresh(getContext(), new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            });
        }

        private void cloud(Canvas cv, float x, float y, float s, boolean rain) {
            p.setStyle(Paint.Style.FILL);
            p.setShader(null);
            p.setColor(fg());
            cv.drawCircle(x - s * 0.28f, y, s * 0.26f, p);
            cv.drawCircle(x + s * 0.02f, y - s * 0.12f, s * 0.34f, p);
            cv.drawCircle(x + s * 0.34f, y + s * 0.02f, s * 0.24f, p);
            cv.drawRect(x - s * 0.28f, y, x + s * 0.34f, y + s * 0.26f, p);
            if (rain) {
                p.setColor(0xFF64D2FF);
                for (int i = 0; i < 3; i++) {
                    cv.drawRoundRect(new RectF(x - s * 0.26f + i * s * 0.28f, y + s * 0.34f,
                            x - s * 0.20f + i * s * 0.28f, y + s * 0.52f), 3f, 3f, p);
                }
            }
        }

        @Override
        protected void onDraw(Canvas cv) {
            card(cv);
            float w = getWidth();
            float h = getHeight();
            if (!WeatherData.ok) {
                text(cv, "Pogoda: brak danych", w / 2f, h / 2f, 20, sub(), false, Paint.Align.CENTER);
                text(cv, "Potrzebny internet (Wi-Fi / hotspot)", w / 2f, h / 2f + 28f * d, 14, sub(),
                        false, Paint.Align.CENTER);
                return;
            }
            float pad = w * 0.08f;
            text(cv, WeatherData.city, pad, h * 0.10f, 26, fg(), true, Paint.Align.LEFT);
            text(cv, Math.round(WeatherData.temp) + "°", pad, h * 0.34f, 70, fg(), true,
                    Paint.Align.LEFT);
            cloud(cv, w - w * 0.12f, h * 0.08f, w * 0.12f, WeatherData.code >= 51);
            text(cv, "↑" + Math.round(WeatherData.tMax) + "°", w - pad, h * 0.22f, 22, fg(), true,
                    Paint.Align.RIGHT);
            text(cv, "↓" + Math.round(WeatherData.tMin) + "°", w - pad, h * 0.31f, 22, sub(), true,
                    Paint.Align.RIGHT);
            String[] k = {"Opady", "Wiatr", "UVI", "Odczuwalna"};
            String[] v = {WeatherData.pop + "%", Math.round(WeatherData.wind) + " m/s",
                    Math.round(WeatherData.uv) + " " + WeatherData.uvLabel(),
                    Math.round(WeatherData.feels) + "°"};
            for (int i = 0; i < 4; i++) {
                float y = h * (0.50f + i * 0.155f);
                text(cv, k[i], pad, y, 24, fg(), true, Paint.Align.LEFT);
                text(cv, v[i], w - pad, y, 24, sub(), true, Paint.Align.RIGHT);
            }
        }
    }

    // ------------------------------------------------------------ zachód słońca
    public static class SunCard extends Base {
        public SunCard(Context c) {
            super(c);
            setContentDescription("Zachód słońca");
        }

        @Override
        void onMinute() {
            WeatherData.refresh(getContext(), new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            });
        }

        @Override
        protected void onDraw(Canvas cv) {
            card(cv);
            float w = getWidth();
            float h = getHeight();
            float pad = w * 0.09f;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f * d);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(fg());
            p.setShader(null);
            cv.drawLine(pad, h * 0.17f, pad + 28f * d, h * 0.17f, p);
            cv.drawArc(new RectF(pad + 4f * d, h * 0.09f, pad + 24f * d, h * 0.17f + 6f * d), 180,
                    180, false, p);
            text(cv, "ZACHÓD", pad + 40f * d, h * 0.14f, 22, fg(), true, Paint.Align.LEFT);
            text(cv, "SŁOŃCA", pad + 40f * d, h * 0.23f, 22, fg(), true, Paint.Align.LEFT);
            text(cv, WeatherData.sunset, pad, h * 0.55f, 54, fg(), true, Paint.Align.LEFT);
            text(cv, "Wschód: " + WeatherData.sunrise, pad, h * 0.86f, 22, fg(), true,
                    Paint.Align.LEFT);
        }
    }
}







