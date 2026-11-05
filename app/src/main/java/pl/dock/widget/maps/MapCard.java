package pl.dock.widget.maps;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import pl.dock.widget.NightAware;
import pl.dock.widget.Prefs;
import pl.dock.widget.Ui;

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
    private SmoothLocationOverlay loc;
    private boolean online;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Karta jak pozostale widzety: promien 30 dp + obwodka 1.5 dp (Ui.ringCard).
    // Mapa jest dzieckiem tej karty, wiec bez przyciecia zaslanialaby caly kafelek
    // prostokatem - bez zaokraglonych rogow i bez obwodki.
    private static final float CARD_RADIUS_DP = 30f;
    private static final float CARD_STROKE_DP = 1.5f;
    private final RectF cardRect = new RectF();
    private final RectF speedRect = new RectF();
    private Typeface tfNum, tfUnit;
    private final Path cardClip = new Path();
    private int clipW = -1;
    private int clipH = -1;

    private boolean hasFix;
    private float speedMs;
    private boolean started;
    private boolean centeredOnce;
    // Wygladzanie ruchu i obrotu (heading-up). GPS daje fix raz na sekunde
    // (MapState), a znacznik i mapa maja sie poruszac CIAGLE - patrz HeadingSmoother.
    private final HeadingSmoother smooth = new HeadingSmoother();
    private final GeoPoint tmpPoint = new GeoPoint(0d, 0d);
    private boolean loopRunning;
    private static final long FRAME_MS = 33L;   // ~30 fps w ruchu
    private static final long IDLE_MS = 200L;   // na postoju - nie mielimy GPU
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
            MapConfig.applyNight(m, night);   // tlo + filtr kafli (dzien/noc)
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
            // Wlasny renderer znacznika: bez sluchacza GPS w overlayu.
            // GpsMyLocationProvider bierze GPS + NETWORK z minTime = 0 i to on
            // dokarmial znacznik surowymi, czesto niedokladnymi fixami.
            SmoothLocationOverlay l = new SmoothLocationOverlay(m);
            l.setDrawAccuracyEnabled(true);
            l.setNight(night);
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
        // Kolejnosc waznosci (patrz MapPlace.startupPlace):
        //   1) swiezy fix GPS, 2) "gdzie zaparkowalem" (ostatnia dobra pozycja GPS),
        //   3) ostatni widok mapy, 4) cala Polska.
        double fLat = 0d, fLon = 0d;
        boolean hasFreshFix = false;
        try {
            MapState st = MapState.get(getContext());
            if (st.hasFix() && st.lastFixAt() > 0L
                    && System.currentTimeMillis() - st.lastFixAt() < 60000L
                    && MapPlace.usable(st.lastLat(), st.lastLon())) {
                fLat = st.lastLat();
                fLon = st.lastLon();
                hasFreshFix = true;
            }
        } catch (Throwable ignored) {
        }
        double gLat = 0d, gLon = 0d;
        try {
            String ga = Prefs.posLastLat(getContext());
            String go = Prefs.posLastLon(getContext());
            if (ga.length() > 0 && go.length() > 0) {
                gLat = Double.parseDouble(ga);
                gLon = Double.parseDouble(go);
            }
        } catch (Throwable ignored) {
        }
        double vLat = 0d, vLon = 0d;
        try {
            String va = Prefs.mapLastLat(getContext());
            String vo = Prefs.mapLastLon(getContext());
            if (va.length() > 0 && vo.length() > 0) {
                vLat = Double.parseDouble(va);
                vLon = Double.parseDouble(vo);
            }
        } catch (Throwable ignored) {
        }
        double[] place = MapPlace.startupPlace(fLat, fLon, gLat, gLon, vLat, vLon);
        double lat = 52.07d, lon = 19.48d;      // cala Polska - ostatnia deska ratunku
        boolean hasSaved = false;
        if (place != null) {
            lat = place[0];
            lon = place[1];
            hasSaved = true;
        }
        try {
            map.getController().setCenter(new GeoPoint(lat, lon));
            if (hasSaved) {
                // ten sam zoom, jaki byl zapisany (sprawdzony co do zakresu)
                map.getController().setZoom(
                        (double) MapPlace.zoom(Prefs.mapLastZoom(getContext()), 16f));
            } else if (Prefs.mapPoland(getContext())) {
                map.getController().setZoom(6d);
            }
        } catch (Throwable ignored) {
        }
        // Pozycja z pamieci ("gdzie zaparkowalem") jest od razu punktem wyjscia dla
        // znacznika - GPS tylko ja doprecyzuje, bez szukania miejsca od zera.
        if (!hasFreshFix && hasSaved) {
            smooth.seed(lat, lon, SystemClock.uptimeMillis());
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
        // Motyw mapy jak reszta launchera: noc = przygaszone kafle + grafitowe tlo.
        MapConfig.applyNight(map, night);
        if (loc != null) loc.setNight(night);
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
        MapState.get(getContext()).addListener(this);
        startLoop();
    }

    private void stop() {
        if (!started) return;
        started = false;
        MapState.get(getContext()).removeListener(this);
        stopLoop();
        savePosition();
        if (map != null) {
            try { map.onPause(); } catch (Throwable ignored) {}
        }
    }


    private long lastGoodAt;

    /** Zapamietuje ostatnia dobra pozycje ("gdzie zaparkowalem") - najwyzej co 10 s. */
    private void saveLastGood() {
        if (!smooth.hasFix()) return;
        long now = System.currentTimeMillis();
        if (now - lastGoodAt < 10000L) return;
        lastGoodAt = now;
        Prefs.setPosLast(getContext(), smooth.lat(), smooth.lon(), now);
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
        lastGoodAt = 0L;            // przy zamykaniu zapisujemy pozycje na pewno
        saveLastGood();
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
        // Pozycje i obrot wygladzamy MIEDZY fixami (petla tick()): przy 1 Hz GPS
        // znacznik sunie, a mapa obraca sie ciagle, bez skokow co sekunde.
        // Najpierw smoother - to on odrzuca bledne fixy (skoki GPS).
        smooth.onFix(lat, lon, bearing, speedMs,
                MapState.get(getContext()).lastAccuracy(), SystemClock.uptimeMillis());
        saveLastGood();
        // Auto-centrowanie TYLKO na swiezym i PRZYJETYM fix'ie. Bierzemy pozycje ze
        // smoothera, a nie surowa, zeby pojedynczy blad GPS nie przeniosl mapy
        // w dalekie miejsce - bo ten srodek zapisuje sie potem w Ustawieniach.
        if (!centeredOnce && hasFix && smooth.hasFix() && map != null
                && System.currentTimeMillis() - MapState.get(getContext()).lastFixAt() < 15000L) {
            centeredOnce = true;
            try {
                map.getController().setCenter(new GeoPoint(smooth.lat(), smooth.lon()));
                // NIE zmieniamy zooma - zostaje na poziomie zapisanym w Prefs.
                // Skok na wysoki zoom czysci kafle i offline mapa znika na bialo.
                // Uzytkownik widzi swoja lokalizacje na ogolnym tle,
                // a w NavigatorActivity moze przyblizyc recznie.
            } catch (Throwable ignored) {
            }
        }
        startLoop();
    }

    // ---------------- wygladzanie ruchu i obrotu: jedna wspolna petla ----------------

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (!loopRunning) return;
            tick();
            long now = SystemClock.uptimeMillis();
            h.postDelayed(this, smooth.moving(now) ? FRAME_MS : IDLE_MS);
        }
    };

    private void startLoop() {
        if (loopRunning) return;
        loopRunning = true;
        h.postDelayed(frame, FRAME_MS);
    }

    private void stopLoop() {
        loopRunning = false;
        h.removeCallbacks(frame);
    }

    private void tick() {
        smooth.advance(SystemClock.uptimeMillis());
        // pozycja z pamieci ("gdzie zaparkowalem") tez ma pokazac znacznik -
        // dzieki temu po wejsciu do auta mapa jest od razu wlasciwa
        if ((!smooth.hasFix() && !smooth.seededKnown()) || !smooth.dirty()) return;
        speedMs = smooth.speedMs();          // pigulka predkosci tez idzie plynnie
        if (map != null) {
            // false = bez requestLayout na kazdej klatce (rysujemy od razu nizej):
            // requestLayout 30x/s na MT8163 to zbedny pomiar i uklad.
            map.setMapOrientation(smooth.mapOrientation(), false);
            if (Prefs.mapFollow(getContext())) {
                tmpPoint.setCoords(smooth.lat(), smooth.lon());
                map.getController().setCenter(tmpPoint);
            }
            map.invalidate();
        }
        if (loc != null) {
            loc.feed(smooth.lat(), smooth.lon(), smooth.markerBearing(),
                    smooth.accuracy(), smooth.isMoving());
        }
        invalidate();   // nakladka karty (pigulka predkosci)
    }

    // ---- rysowanie nakladki (pigulka predkosci) ----

    @Override
    protected void dispatchDraw(Canvas cv) {
        int w = getWidth();
        int h = getHeight();

        // Karta z zaokraglonymi rogami i obwodka DOKLADNIE jak pozostale widzety
        // (Ui.ringCard: 30 dp / 1.5 dp). Kolejnosc jest istotna, bo mapa jest
        // dzieckiem tej karty:
        //   1) wypelnienie karty - lezy POD mapa (widoczne, gdy kafle sie wczytuja),
        //   2) dzieci przyciete do KSZTALTU karty - mapa konczy sie na zaokragleniu,
        //   3) obwodka NA WIERZCHU mapy - inaczej mapa by ja zaslonila.
        if (w > 0 && h > 0) {
            cardRect.set(0f, 0f, w, h);
            float rad = CARD_RADIUS_DP * d;
            if (w != clipW || h != clipH) {
                // Ksztalt liczymy tylko przy zmianie rozmiaru (nie co klatke).
                clipW = w;
                clipH = h;
                float side = Math.min(w, h);
                cardClip.reset();
                if (rad > side * 0.18f) {
                    cardClip.set(Ui.squirclePath(cardRect, Math.min(rad / side, 0.45f)));
                } else {
                    cardClip.addRoundRect(cardRect, rad, rad, Path.Direction.CW);
                }
            }
            Ui.ringCard(cv, cardRect, rad, CARD_STROKE_DP * d, night, false);   // wypelnienie
            int save = cv.save();
            cv.clipPath(cardClip);                                              // rogi karty
            super.dispatchDraw(cv);
            cv.restoreToCount(save);
            Ui.ringStroke(cv, cardRect, rad, CARD_STROKE_DP * d, night);        // obwodka
        } else {
            super.dispatchDraw(cv);
        }

        // Kafelek to SAMA MAPA + wskaznik predkosci. Paska "MAPY / offline /
        // NAWIGACJA" tu nie ma (usuniety na zyczenie uzytkownika, 2026-11-05).
        //
        // Plakietka predkosci: PROSTOKAT z zaokraglonymi rogami (jak karty widzetow
        // obok) w prawym dolnym rogu, liczba + "km/h" pod nia. Czcionka z motywu
        // launchera (Inter z DockTheme, jak w reszcie UI).
        float kmh = speedMs * 3.6f;
        String val = hasFix ? String.valueOf(Math.round(kmh)) : "—";
        float bw = 84f * d;
        float bh = 64f * d;
        float rad = 20f * d;          // zaokraglenie; karta widzetu ma 30 dp przy ~200 dp
        float mgn = 14f * d;
        speedRect.set(w - bw - mgn, h - bh - mgn, w - mgn, h - mgn);
        float cx = speedRect.centerX();

        p.setStyle(Paint.Style.FILL);
        p.setColor(night ? 0xE60E0E0F : 0xE6F5F5F7);
        cv.drawRoundRect(speedRect, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f * d);
        p.setColor(night ? 0xFF0A84FF : 0xFF0A6CFF);
        cv.drawRoundRect(speedRect, rad, rad, p);

        if (tfNum == null) {              // czcionki bierzemy raz, nie co klatke
            tfNum = pl.dock.widget.ui.DockTheme.interSemiBold();
            tfUnit = pl.dock.widget.ui.DockTheme.interMedium();
        }
        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(tfNum);
        t.setTextSize(26f * d);
        t.setColor(night ? 0xFFFFFFFF : 0xFF10131A);
        cv.drawText(val, cx, speedRect.top + 31f * d, t);      // liczba
        t.setTypeface(tfUnit);
        t.setTextSize(11f * d);
        t.setColor(night ? 0xFF8E8E93 : 0xFF6B7686);
        cv.drawText("km/h", cx, speedRect.top + 50f * d, t);   // jednostka pod liczba
    }
}
