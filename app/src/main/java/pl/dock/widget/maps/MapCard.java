package pl.dock.widget.maps;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import pl.dock.widget.NightAware;
import pl.dock.widget.Prefs;

/**
 * Karta mapy na ekranie glownym (slot "hl", jak mapa w CarPlay).
 *
 * Dziala OFFLINE: kafle pochodza z pamieci aplikacji. Gdy tablet jest online
 * (i wlaczono to w Ustawieniach), mapa doklada nowe obszary.
 * Karta jest cienka nakladka na zywy MapView: naglowek + pigulka z predkoscia
 * sa rysowane przez te karte, a dotkniecie CALEGO kafelka otwiera pelna Nawigacje
 * (przesuwanie mapy w kafelku jest wylaczone, zeby nic nie "zjadalo" dotyku).
 */
public class MapCard extends FrameLayout implements NightAware, MapState.Listener {

    private final float d;
    private boolean night = true;
    private MapView map;
    private MyLocationNewOverlay loc;
    private boolean online;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF header = new RectF();

    private boolean hasFix;
    private float speedMs;
    private boolean started;
    private boolean centeredOnce;

    public MapCard(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        online = Prefs.mapOnline(c);
        MapConfig.init(c);
        setClipToPadding(false);
        try {
            map = new MapView(c);
            map.setTileSource("topo".equals(Prefs.mapStyle(c))
                    ? TileSourceFactory.OpenTopo : TileSourceFactory.MAPNIK);
            map.setMultiTouchControls(false);
            map.setTilesScaledToDpi(true);
            map.setUseDataConnection(online);
            map.setBackgroundColor(night ? 0xFF0B0C0E : 0xFFE8E8ED);
            map.getController().setZoom(Math.round(Prefs.mapLastZoom(c)));
            loc = new MyLocationNewOverlay(map);
            loc.setDrawAccuracyEnabled(true);
            if (MapState.hasPermission(c)) {
                loc.enableMyLocation();
                if (Prefs.mapFollow(c)) loc.enableFollowLocation();
            }
            map.getOverlays().add(loc);
            addView(map, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } catch (Throwable ignored) {
            map = null;
        }
        // Caly kafelek reaguje na dotkniecie (jak mapa w CarPlay):
        //  - tapniecie      -> otwiera pelna Nawigacje,
        //  - przytrzymanie  -> podmiana kafelka na inny widzet.
        // Przesuwanie mapy w kafelku jest wylaczone, wiec nic nie "zjada" dotyku.
        View tap = new View(c);
        tap.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                openNav();
            }
        });
        tap.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return MapCard.this.performLongClick();
            }
        });
        addView(tap, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        restoreCenter();
    }

    private void restoreCenter() {
        if (map == null) return;
        double lat = 52.07d, lon = 19.48d;   // cala Polska
        float zoom = 6f;
        boolean hasSaved = false;
        try {
            String la = Prefs.mapLastLat(getContext());
            String lo = Prefs.mapLastLon(getContext());
            if (la.length() > 0 && lo.length() > 0) {
                lat = Double.parseDouble(la);
                lon = Double.parseDouble(lo);
                hasSaved = true;
            }
        } catch (Throwable ignored) {
        }
        try {
            map.getController().setCenter(new GeoPoint(lat, lon));
            // Bez zapisanej pozycji startujemy z widokiem calej Polski
            // (mozna wylaczyc: Ustawienia -> Mapy -> "Startuj z mapa Polski").
            if (!hasSaved && Prefs.mapPoland(getContext())) {
                map.getController().setZoom((double) zoom);
            }
        } catch (Throwable ignored) {
        }
    }

    private void openNav() {
        try {
            Intent i = getContext().getPackageManager()
                    .getLaunchIntentForPackage(OmAccessibilityService.OM_PACKAGE);
            if (i == null) {
                Toast.makeText(getContext(),
                        "Zainstaluj Organic Maps, aby korzystać z nawigacji",
                        Toast.LENGTH_LONG).show();
                return;
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            getContext().startActivity(i);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void setNight(boolean n) {
        night = n;
        if (map != null) map.setBackgroundColor(night ? 0xFF0B0C0E : 0xFFE8E8ED);
        invalidate();
    }

    public void setOnline(boolean v) {
        online = v;
        if (map != null) map.setUseDataConnection(v);
        invalidate();
    }

    // ---- cykl zycia (samoobslugowy - karta sama pilnuje widocznosci, wiec dziala
    //      niezaleznie od tego, w jakim kontenerze sie znajdzie) ----

    private void start() {
        if (started) return;
        started = true;
        if (map != null) {
            try { map.onResume(); } catch (Throwable ignored) {}
        }
        // Uprawnienie moze zostac nadane juz po utworzeniu karty - probujemy ponownie.
        if (loc != null && MapState.hasPermission(getContext())) {
            try {
                loc.enableMyLocation();
                if (Prefs.mapFollow(getContext())) loc.enableFollowLocation();
            } catch (Throwable ignored) {
            }
        }
        MapState.get(getContext()).addListener(this);
    }

    private void stop() {
        if (!started) return;
        started = false;
        MapState.get(getContext()).removeListener(this);
        savePosition();
        if (map != null) {
            try { map.onPause(); } catch (Throwable ignored) {}
        }
    }

    public void savePosition() {
        if (map == null) return;
        try {
            org.osmdroid.api.IGeoPoint c = map.getMapCenter();
            if (c != null) {
                Prefs.setMapLast(getContext(), c.getLatitude(), c.getLongitude(),
                        (float) map.getZoomLevelDouble());
            }
        } catch (Throwable ignored) {
        }
    }

    private void updateLifecycle() {
        // isShown() = widoczny sam i cala sciezka rodzicow (pager ukrywa nieaktywne
        // strony przez setVisibility(INVISIBLE), wiec to dokladnie to, czego chcemy).
        if (isShown()) start();
        else stop();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateLifecycle();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changed, int vis) {
        super.onVisibilityChanged(changed, vis);
        updateLifecycle();
    }

    @Override
    protected void onWindowVisibilityChanged(int vis) {
        super.onWindowVisibilityChanged(vis);
        updateLifecycle();
    }

    @Override
    public void onLocation(double lat, double lon, float bearing, float speedMs, boolean hasFix) {
        this.hasFix = hasFix;
        this.speedMs = speedMs;
        if (!centeredOnce && hasFix && map != null && !hasSavedPosition()) {
            centeredOnce = true;
            try {
                map.getController().setCenter(new GeoPoint(lat, lon));
                map.getController().setZoom(15d);
            } catch (Throwable ignored) {
            }
        }
        invalidate();
    }

    private boolean hasSavedPosition() {
        try {
            Context c = getContext();
            return Prefs.mapLastLat(c).length() > 0 && Prefs.mapLastLon(c).length() > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- rysowanie nakladki (naglowek + pigulka predkosci) ----

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float pad = 10f * d;
        header.set(pad, pad, w - pad, pad + 34f * d);
    }

    @Override
    protected void dispatchDraw(Canvas cv) {
        // Najpierw mapa (dzieci), potem nasza nakladka na wierzchu.
        super.dispatchDraw(cv);
        if (header.width() <= 0) return;
        int w = getWidth();
        int h = getHeight();

        p.setStyle(Paint.Style.FILL);
        p.setColor(night ? 0x99000000 : 0xCCFFFFFF);
        cv.drawRoundRect(header, 14f * d, 14f * d, p);

        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextAlign(Paint.Align.LEFT);
        t.setTextSize(11f * d);
        t.setColor(night ? 0xFFFFFFFF : 0xFF10131A);
        cv.drawText("MAPY", header.left + 12f * d, header.top + 15f * d, t);
        t.setTypeface(Typeface.DEFAULT);
        t.setTextSize(9f * d);
        t.setColor(night ? 0xFF8E8E93 : 0xFF6B7686);
        cv.drawText(online ? "offline + sieć" : "offline", header.left + 12f * d,
                header.top + 27f * d, t);

        t.setTextAlign(Paint.Align.RIGHT);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextSize(10f * d);
        t.setColor(night ? 0xFFFFFFFF : 0xFF10131A);
        cv.drawText("NAWIGACJA  ›", header.right - 12f * d, header.top + 21f * d, t);

        // pigulka z predkoscia (jak w CarPlay) w prawym dolnym rogu
        float kmh = speedMs * 3.6f;
        String val = hasFix ? String.valueOf(Math.round(kmh)) : "—";
        float pw = 84f * d;
        float ph = 60f * d;
        RectF pr = new RectF(w - pw - 12f * d, h - ph - 12f * d, w - 12f * d, h - 12f * d);
        p.setStyle(Paint.Style.FILL);
        p.setColor(night ? 0xE6000000 : 0xE6FFFFFF);
        cv.drawRoundRect(pr, 20f * d, 20f * d, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * d);
        p.setColor(night ? 0xFF0A84FF : 0xFF0A6CFF);
        cv.drawRoundRect(new RectF(pr.left + d, pr.top + d, pr.right - d, pr.bottom - d),
                20f * d, 20f * d, p);

        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextSize(26f * d);
        t.setColor(night ? 0xFFFFFFFF : 0xFF10131A);
        cv.drawText(val, pr.centerX(), pr.top + 32f * d, t);
        t.setTypeface(Typeface.DEFAULT);
        t.setTextSize(9f * d);
        t.setColor(night ? 0xFF8E8E93 : 0xFF6B7686);
        cv.drawText("km/h", pr.centerX(), pr.top + 46f * d, t);
    }
}
