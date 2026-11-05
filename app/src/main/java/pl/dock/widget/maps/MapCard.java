package pl.dock.widget.maps;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
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
    // Plytna animacja obrotu mapy (heading-up).
    private ValueAnimator headingAnimator;
    // Mapa zostala zwolniona (onDetach) - przy powrocie do okna trzeba ja odbudowac.
    private boolean released;

    private final Handler h = new Handler(Looper.getMainLooper());
    // Zwolnienie mapy dopiero wtedy, gdy karta NIE wroci do okna. Pager przebudowuje
    // strony (removeAllViews + addView), wiec krotkie odlaczenie jest normalne.
    private final Runnable releaseIfGone = new Runnable() {
        @Override
        public void run() {
            if (isAttachedToWindow()) return;
            releaseMap();
        }
    };

    public MapCard(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        online = Prefs.mapOnline(c);
        MapConfig.init(c);
        setClipToPadding(false);
        buildMap();
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

    /**
     * Tworzy (lub odtwarza) MapView. Wydzielone, bo mape trzeba czasem odbudowac
     * po zwolnieniu - patrz {@link #releaseMap()}.
     */
    private void buildMap() {
        try {
            MapView m = new MapView(getContext());
            m.setTileSource("topo".equals(Prefs.mapStyle(getContext()))
                    ? TileSourceFactory.OpenTopo : TileSourceFactory.MAPNIK);
            m.setMultiTouchControls(false);
            m.setTilesScaledToDpi(true);
            m.setUseDataConnection(online);
            m.setBackgroundColor(night ? 0xFF0B0C0E : 0xFFE8E8ED);
            // KLUCZOWE: nie pozwalamy osmdroid niszczyc mapy przy odlaczeniu od okna.
            // Domyslnie (destroyModeOnDetach = true) kazde odlaczenie wola onDetach():
            // czysci kafle z RAM, odpina tile provider, zeruje projekcje i listenery.
            // onAttachedToWindow() w osmdroid jest PUSTE, wiec po ponownym dodaniu
            // widoku mapa juz nie wraca - kafelek zostaje pusty. To byla przyczyna
            // objawu "mapa miga i znika, a w pelnym ekranie dziala".
            m.setDestroyMode(false);
            m.setMinZoomLevel(3d);
            m.setMaxZoomLevel(19d);
            m.getController().setZoom(Math.round(Prefs.mapLastZoom(getContext())));
            MyLocationNewOverlay l = new MyLocationNewOverlay(m);
            l.setDrawAccuracyEnabled(true);
            if (MapState.hasPermission(getContext())) {
                l.enableMyLocation();
                if (Prefs.mapFollow(getContext())) l.enableFollowLocation();
            }
            m.getOverlays().add(l);
            map = m;
            loc = l;
            released = false;
            // indeks 0 = pod warstwa dotyku dodana w konstruktorze
            addView(map, 0, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        } catch (Throwable ignored) {
            map = null;
            loc = null;
        }
    }

    /**
     * Zwalnia mape (osmdroid onDetach) - wolane tylko wtedy, gdy karta naprawde
     * zniknela z okna. Nie wolno tego robic przy zwyklej przebudowie stron pagera.
     */
    private void releaseMap() {
        if (map == null) return;
        try {
            map.onDetach();
        } catch (Throwable ignored) {
        }
        try {
            removeView(map);
        } catch (Throwable ignored) {
        }
        map = null;
        loc = null;
        released = true;
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
            Intent i = new Intent(getContext(), NavigatorActivity.class);
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
        if (map == null && released) {
            // karta byla ukryta, gdy zmieniono ustawienie sieci - odbuduj z nowym trybem
            buildMap();
            restoreCenter();
        }
        if (map != null) map.setUseDataConnection(v);
        invalidate();
    }

    // ---- cykl zycia (samoobslugowy - karta sama pilnuje widocznosci, wiec dziala
    //      niezaleznie od tego, w jakim kontenerze sie znajdzie) ----

    private void start() {
        if (started) return;
        started = true;
        // Mapa mogla zostac zwolniona przy poprzednim odlaczeniu od okna - wtedy
        // trzeba ja odbudowac, bo zwolnionego MapView nie da sie "wznowic".
        if (map == null && released) {
            buildMap();
            restoreCenter();
        }
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
        // Karta wrocila do okna (np. pager przebudowal strony) - nie zwalniamy mapy.
        h.removeCallbacks(releaseIfGone);
        updateLifecycle();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        // NIE zwalniamy mapy od razu: pager potrafi odlaczyc i podlaczyc te sama
        // strone w tej samej klatce (removeAllViews + addView). Zwolnienie dopiero
        // po chwili, jesli karta naprawde nie wrocila do okna.
        h.removeCallbacks(releaseIfGone);
        h.postDelayed(releaseIfGone, 1500L);
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
        // Auto-centrowanie TYLKO na swiezym fix'ie GPS (MapState podaje tez
        // stare "ostatnie znane pozycje", a wtedy nie ma co przeskakiwac -
        // mapa po prostu znikala, bo na nowym zoomie nie bylo kafli).
        if (!centeredOnce && hasFix && map != null
                && System.currentTimeMillis() - MapState.get(getContext()).lastFixAt() < 15000L) {
            centeredOnce = true;
            try {
                map.getController().setCenter(new GeoPoint(lat, lon));
                // NIE zmieniamy zooma - zostaje na poziomie zapisanym w Prefs.
                // Skok na wysoki zoom czysci kafle i offline mapa znika na bialo.
                // Uzytkownik widzi swoja lokalizacje na ogolnym tle,
                // a w NavigatorActivity moze przyblizyc recznie.
            } catch (Throwable ignored) {
            }
        }
        // Heading-up: plytny obrot mapy z kierunkiem jazdy na gorze.
        // Wlacza sie od 10 km/h (~2.78 m/s) - nizsze progi daja skaczacy obraz
        // przez szum GPS na wolnym MT8163. Animacja 250ms wygladza skoki.
        rotateMapTo((hasFix && speedMs > 2.78f) ? bearing : 0f);
        invalidate();
    }

    /**
     * Plytnie obraca mape do zadanego bearingu (stopnie).
     * Uzywa ValueAnimator zeby uniknac skokow przy szumie GPS.
     * Jesli animacja juz trwa, przerywa ja i startuje od nowa z obecnej pozycji.
     */
    private void rotateMapTo(float targetBearing) {
        if (map == null) return;
        if (headingAnimator != null && headingAnimator.isRunning()) {
            headingAnimator.cancel();
        }
        float from = map.getMapOrientation();
        // Najkrotsza droga: delta w zakresie [-180, 180]
        float delta = targetBearing - from;
        while (delta > 180f) delta -= 360f;
        while (delta < -180f) delta += 360f;
        float to = from + delta;
        if (Math.abs(delta) < 3f) return; // pomijamy mikroskoki
        headingAnimator = ValueAnimator.ofFloat(from, to);
        headingAnimator.setDuration(250L);
        headingAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                map.setMapOrientation((Float) a.getAnimatedValue());
            }
        });
        headingAnimator.start();
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
