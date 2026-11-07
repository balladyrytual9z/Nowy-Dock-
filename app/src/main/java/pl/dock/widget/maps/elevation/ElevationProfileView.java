package pl.dock.widget.maps.elevation;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * Rysuje profil wysokosci (jak zakladka terenowa w Organic Maps):
 * krzywa wysokosc/dystans + etykiety min/max/suma podejsc.
 *
 * Rysowany w oknie DIALOGU aktywnosci (nie w nakladce launchera), wiec
 * wlasciwy onDraw jest tu bezpieczny - NavigatorActivity/ChromeView rysuja
 * sie w ten sam sposob. Warstwa SOFTWARE jak wszystkie custom widoki na Mali-T720.
 */
public class ElevationProfileView extends View {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float d;
    private boolean night = true;
    private ElevationProfile pf;

    public ElevationProfileView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setContentDescription("Profil wysokosci");
    }

    public void setNight(boolean n) {
        night = n;
        invalidate();
    }

    public void setProfile(ElevationProfile profile) {
        pf = profile;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas cv) {
        int w = getWidth();
        int h = getHeight();
        cv.drawRect(0, 0, w, h, fill(night ? 0xFF1C1C1E : 0xFFF5F5F7));
        if (pf == null || pf.elevM.length < 2) {
            t.setColor(night ? 0xFF8E8E93 : 0xFF6B7686);
            t.setTextSize(14f * d);
            t.setTextAlign(Paint.Align.CENTER);
            cv.drawText("Brak danych", w / 2f, h / 2f, t);
            return;
        }

        float pad = 10f * d;
        float left = pad;
        float right = w - pad;
        float top = pad;
        float bottom = h - pad - 14f * d;

        double minE = pf.minElevM;
        double maxE = pf.maxElevM;
        if (maxE - minE < 1d) maxE = minE + 1d;
        double totalD = pf.totalM > 0d ? pf.totalM : 1d;

        // wypelnienie pod krzywa
        path.reset();
        path.moveTo(left, bottom);
        for (int i = 0; i < pf.elevM.length; i++) {
            float x = left + (float) (pf.distM[i] / totalD) * (right - left);
            float y = bottom - (float) ((pf.elevM[i] - minE) / (maxE - minE)) * (bottom - top);
            if (i == 0) path.lineTo(x, y);
            else path.lineTo(x, y);
        }
        path.lineTo(right, bottom);
        path.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(night ? 0x3330D158 : 0x331E9E4A);
        cv.drawPath(path, p);

        // linia profilu
        path.reset();
        for (int i = 0; i < pf.elevM.length; i++) {
            float x = left + (float) (pf.distM[i] / totalD) * (right - left);
            float y = bottom - (float) ((pf.elevM[i] - minE) / (maxE - minE)) * (bottom - top);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f * d);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(night ? 0xFF30D158 : 0xFF1E9E4A);
        cv.drawPath(path, p);

        // podpisy
        t.setTextSize(11f * d);
        t.setTextAlign(Paint.Align.LEFT);
        t.setColor(night ? 0xFFE8E8ED : 0xFF10131A);
        cv.drawText(String.format(java.util.Locale.US, "↑ %.0f m  ↓ %.0f m  ∑ %.0f m",
                (double) Math.round(pf.maxElevM), (double) Math.round(pf.minElevM),
                (double) Math.round(pf.gainM)), left, top + 12f * d, t);
        t.setColor(night ? 0xFF8E8E93 : 0xFF6B7686);
        t.setTextAlign(Paint.Align.RIGHT);
        cv.drawText(pf.totalM >= 1000d
                ? String.format(java.util.Locale.US, "%.1f km", pf.totalM / 1000d)
                : String.format(java.util.Locale.US, "%.0f m", pf.totalM),
                right, bottom + 12f * d, t);
    }

    private Paint fill(int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }
}
