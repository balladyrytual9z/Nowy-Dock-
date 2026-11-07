package pl.dock.widget.maps;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

import pl.dock.widget.AppEntry;
import pl.dock.widget.BaseActivity;
import pl.dock.widget.DockView;
import pl.dock.widget.Icons;
import pl.dock.widget.Launch;
import pl.dock.widget.Look;
import pl.dock.widget.Prefs;
import pl.dock.widget.RecentAppsPopup;
import pl.dock.widget.StatusCenter;
import pl.dock.widget.Ui;
import pl.dock.widget.maps.bookmark.Bookmark;
import pl.dock.widget.maps.bookmark.BookmarksActivity;
import pl.dock.widget.maps.bookmark.GpxIo;
import pl.dock.widget.maps.bookmark.MapsDb;
import pl.dock.widget.maps.bookmark.TrackFiles;
import pl.dock.widget.maps.bookmark.TrackRecorder;
import pl.dock.widget.maps.route.Guidance;
import pl.dock.widget.maps.route.Maneuvers;
import pl.dock.widget.maps.route.Route;
import pl.dock.widget.maps.route.RouteClient;
import pl.dock.widget.maps.route.VoiceGuide;
import pl.dock.widget.maps.wiki.WikiNearby;

/**
 * Pelnoekranowa Nawigacja: mapa offline (OpenStreetMap) jak mapa w CarPlay.
 *
 * Mapy sa pobrane wczesniej (przycisk "Pobierz okolice" gdy tablet online)
 * i dzialaja bez internetu. Lewy/prawy dock Docka pozostaje widoczny i klikalny.
 */
public class NavigatorActivity extends BaseActivity implements MapState.Listener {

    private boolean night;
    private MapView map;
    private SmoothLocationOverlay loc;
    private DockView dock;
    private ChromeView chrome;
    private boolean hasFix;
    private float currentSpeedMs;
    private float currentBearing;
    private boolean centeredOnce;
    // Wygladzanie ruchu i obrotu (heading-up). GPS daje fix raz na sekunde
    // (MapState), a mapa i znacznik maja sie poruszac CIAGLE - patrz HeadingSmoother.
    private final HeadingSmoother smooth = new HeadingSmoother();
    private final GeoPoint tmpPoint = new GeoPoint(0d, 0d);
    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean loopRunning;
    private boolean followPaused;   // uzytkownik przesunal mape - nie ciagujemy go
    private static final long FRAME_MS = 33L;   // ~30 fps w ruchu
    private static final long IDLE_MS = 200L;   // na postoju - nie mielimy GPU
    private boolean hasDest;
    private GeoPoint dest;
    private Polyline destLine;
    private DestOverlay destOverlay;
    private float destDistM = -1f;
    private float destBearing = -1f;
    // ---------------- nawigacja jak w Organic Maps: trasa + prowadzenie + glos ----------------
    private Route route;                       // aktualna trasa z OSRM (null = brak)
    private Guidance guide;                    // maszyna prowadzenia (manewry, zjazd z trasy)
    private VoiceGuide voice;                  // komunikaty glosowe TTS (pl_PL)
    private Polyline routeLine;                // zielona linia trasy
    private Polyline trackLine;                // pomaranczowy slad jazdy
    private boolean navigating;                // trasa aktywna: banner z manewrami
    private long lastRerouteAt;                // throttle przeliczania trasy (15 s)
    private String navInstruction = "";        // opis najblizszego manewru (banner)
    private float navManeuverDistM = -1f;       // dystans do manewru
    private float navRemainingM = -1f;         // dystans do celu
    private float navEtaS = -1f;               // czas do celu [s]
    private final TrackRecorder recorder = new TrackRecorder();
    private boolean recording;                 // nagrywanie sladu jazdy

    @Override
    protected void onCreate(Bundle b) {
        night = Look.night(this);
        setTheme(android.R.style.Theme_Translucent_NoTitleBar);
        super.onCreate(b);
        try {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        } catch (Throwable ignored) {
        }
        applyImmersive();
        MapConfig.init(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0C0E);

        try {
            map = new MapView(this);
            map.setTileSource("topo".equals(Prefs.mapStyle(this))
                    ? TileSourceFactory.OpenTopo : TileSourceFactory.MAPNIK);
            map.setMultiTouchControls(true);
            map.setTilesScaledToDpi(true);
            map.setUseDataConnection(Prefs.mapOnline(this));
            MapConfig.applyNight(map, night);   // tlo + filtr kafli (dzien/noc)
            map.getController().setZoom(Math.round(Prefs.mapLastZoom(this)));
            map.setMinZoomLevel(3d);
            map.setMaxZoomLevel(19d);
            // Wlasny renderer znacznika: bez sluchacza GPS w overlayu.
            // GpsMyLocationProvider bierze GPS + NETWORK z minTime = 0
            // i dokarmial znacznik surowymi fixami - stad skakanie strzalki.
            loc = new SmoothLocationOverlay(map);
            loc.setDrawAccuracyEnabled(true);
            loc.setNight(night);
            loc.setOnUserTouch(new Runnable() {
                @Override
                public void run() {
                    // uzytkownik chwycil mape - przestajemy ja ciagnac
                    followPaused = true;
                }
            });
            map.getOverlays().add(loc);
            destLine = new Polyline();
            destLine.setColor(night ? 0xFF0A84FF : 0xFF0A6CFF);
            destLine.setWidth(6f * getResources().getDisplayMetrics().density);
            map.getOverlays().add(destLine);
            destOverlay = new DestOverlay();
            map.getOverlays().add(destOverlay);
            // trasa nawigacji (zielona, jak w Organic Maps) i slad jazdy (pomaranczowy)
            routeLine = new Polyline();
            routeLine.setColor(0xFF30D158);
            routeLine.setWidth(7f * getResources().getDisplayMetrics().density);
            map.getOverlays().add(routeLine);
            trackLine = new Polyline();
            trackLine.setColor(0xFFFF9500);
            trackLine.setWidth(5f * getResources().getDisplayMetrics().density);
            map.getOverlays().add(trackLine);
            // dlugie przytrzymanie na mapie = cel podrozy
            map.getOverlays().add(new MapEventsOverlay(new org.osmdroid.events.MapEventsReceiver() {
                @Override
                public boolean singleTapConfirmedHelper(GeoPoint p) {
                    return false;
                }

                @Override
                public boolean longPressHelper(GeoPoint p) {
                    setDestination(p);
                    return true;
                }
            }));
            restoreCenter();
            root.addView(map, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable ignored) {
            map = null;
        }

        // CarPlay-style chrome overlay
        chrome = new ChromeView(this);
        root.addView(chrome, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // dock Docka po wlasciwej stronie
        int dockPx = Ui.dp(this, Ui.DOCK_OFFSET_DP);
        dock = new DockView(this);
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                dockPx, ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dockLeft(this) ? Gravity.START : Gravity.END);
        root.addView(dock, dockLp);
        wireDock();

        setContentView(root);
        voice = new VoiceGuide(this);
        handleIntent(getIntent());
    }

    /** Docelowo z BookmarksActivity: cel (dest_lat/dest_lon) albo slad (track_path). */
    private void handleIntent(Intent i) {
        if (i == null) return;
        try {
            if (i.hasExtra("dest_lat") && i.hasExtra("dest_lon")) {
                double la = i.getDoubleExtra("dest_lat", 0d);
                double lo = i.getDoubleExtra("dest_lon", 0d);
                if (MapPlace.usable(la, lo) && map != null) {
                    GeoPoint p = new GeoPoint(la, lo);
                    map.getController().setCenter(p);
                    map.getController().setZoom(15d);
                    setDestination(p);
                }
            }
            if (i.hasExtra("track_path")) {
                showTrack(i.getStringExtra("track_path"));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handleIntent(i);
    }

    private void restoreCenter() {
        if (map == null) return;
        // Kolejnosc waznosci (patrz MapPlace.startupPlace):
        //   1) swiezy fix GPS, 2) "gdzie zaparkowalem" (ostatnia dobra pozycja GPS),
        //   3) ostatni widok mapy, 4) cala Polska.
        double fLat = 0d, fLon = 0d;
        boolean hasFreshFix = false;
        try {
            MapState st = MapState.get(this);
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
            String ga = Prefs.posLastLat(this);
            String go = Prefs.posLastLon(this);
            if (ga.length() > 0 && go.length() > 0) {
                gLat = Double.parseDouble(ga);
                gLon = Double.parseDouble(go);
            }
        } catch (Throwable ignored) {
        }
        double vLat = 0d, vLon = 0d;
        try {
            String va = Prefs.mapLastLat(this);
            String vo = Prefs.mapLastLon(this);
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
                        (double) MapPlace.zoom(Prefs.mapLastZoom(this), 16f));
            } else if (Prefs.mapPoland(this)) {
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

    private void wireDock() {
            // Ikona dolnego przycisku: "jeden kwadracik" = powrot do ekranu glownego
            // (na kafelkach launchera pokazuje siatke aplikacji - tutaj ma byc dom).
            dock.setBottomProgress(1f);
        Bitmap[] dockIcons = new Bitmap[4];
        List<AppEntry> top = Prefs.top(this, 3);
        for (int i = 0; i < 3 && i < top.size(); i++) dockIcons[i] = Icons.forEntry(this, top.get(i));
        dockIcons[3] = Icons.forEntry(this, Prefs.carplay(this));
        StatusCenter.Snap s = StatusCenter.get(this);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String clock = String.format(java.util.Locale.US, "%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
        dock.setData(night, "glass".equals(Prefs.style(this)), clock, s,
                Prefs.showVoltage(this), dockIcons);
        dock.setCarplayState(s.cp);
        dock.setListener(new DockView.Listener() {
            @Override
            public void onHeader() {
                finish();
            }

            @Override
            public void onApp(int index) {
                List<AppEntry> top = Prefs.top(NavigatorActivity.this, 3);
                if (index == 3) {
                    Launch.carplay(NavigatorActivity.this);
                    finish();
                } else if (index < top.size()) {
                    String pkg = top.get(index).pkg;
                    if (Launch.openDockTool(NavigatorActivity.this, pkg)) {
                        finish();
                        return;
                    }
                    Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (i != null) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        finish();
                    }
                }
            }

            @Override
            public void onBottom() {
                // Zgloszenie: w pelnoekranowej mapie ten przycisk nie robil nic.
                // Ma wrocic na STRONE GLOWNA launchera (ta flaga + finish).
                Prefs.setGoHome(NavigatorActivity.this, true);
                finish();
            }

            @Override
            public void onBottomLong() {
                RecentAppsPopup.show(NavigatorActivity.this, Prefs.recents(NavigatorActivity.this, 8));
            }
        });
    }

private void toggleFollow() {
        boolean f = !Prefs.mapFollow(this);
        Prefs.setMapFollow(this, f);
        followPaused = !f;   // po wlaczeniu "Sledz" znow ciagujemy mape
        invalidateControls();
    }

    private void toggleLayer() {
        if (map == null) return;
        boolean topo = "topo".equals(Prefs.mapStyle(this));
        String next = topo ? "mapnik" : "topo";
        Prefs.setMapStyle(this, next);
        map.setTileSource("topo".equals(next) ? TileSourceFactory.OpenTopo : TileSourceFactory.MAPNIK);
        invalidateControls();
    }

    private void toggleOnline() {
        boolean v = !Prefs.mapOnline(this);
        Prefs.setMapOnline(this, v);
        if (map != null) map.setUseDataConnection(v);
        Toast.makeText(this, v ? "Internet: mapy dociągają nowe obszary" : "Offline: tylko pobrane mapy",
                Toast.LENGTH_SHORT).show();
        invalidateControls();
    }

    private void downloadAround() {
        if (map == null) {
            Toast.makeText(this, "Mapa niedostępna", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Prefs.mapOnline(this)) {
            Toast.makeText(this, "Włącz internet, aby pobrać mapy", Toast.LENGTH_LONG).show();
            return;
        }
        if (!BulkGuard.allowed(map)) {
            Toast.makeText(this, BulkGuard.DENIED_MSG, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            org.osmdroid.api.IGeoPoint c = map.getMapCenter();
            BoundingBox bb = map.getBoundingBox();
            if (bb == null || c == null) {
                Toast.makeText(this, "Brak obszaru do pobrania", Toast.LENGTH_SHORT).show();
                return;
            }
            CacheManager cm = new CacheManager(map);
            cm.downloadAreaAsyncNoUI(this, bb, 13, 16, new CacheManager.CacheManagerCallback() {
                @Override
                public void onTaskComplete() {
                    toastOnUi("Pobrano mapy okolicy");
                }

                @Override
                public void updateProgress(int progress, int currentZoomLevel, int zoomMin, int zoomMax) {
                }

                @Override
                public void downloadStarted() {
                    toastOnUi("Pobieram mapy okolicy…");
                }

                @Override
                public void setPossibleTilesInArea(int total) {
                }

                @Override
                public void onTaskFailed(int errors) {
                    toastOnUi("Pobieranie nieudane (" + errors + ")");
                }
            });
        } catch (Throwable e) {
            Toast.makeText(this, "Nie udało się pobrać map", Toast.LENGTH_SHORT).show();
        }
    }

    private void toastOnUi(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(NavigatorActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void invalidateControls() {
        if (chrome != null) chrome.invalidate();
    }

    @Override
    public void onLocation(double lat, double lon, float bearing, float speedMs, boolean hasFix) {
        this.hasFix = hasFix;
        this.currentSpeedMs = speedMs;
        this.currentBearing = bearing;
        // Najpierw smoother: on odrzuca bledne fixy (skoki GPS o kilometry).
        smooth.onFix(lat, lon, bearing, speedMs,
                MapState.get(this).lastAccuracy(), SystemClock.uptimeMillis());
        // nagrywanie sladu: tylko przyjete fixy GPS (surowe wartosci, bez wygladzania)
        if (recording && hasFix) {
            recorder.add(lat, lon, System.currentTimeMillis());
        }
        saveLastGood();
        // Auto-centrowanie TYLKO na swiezym i PRZYJETYM fix'ie GPS (stara "ostatnia
        // znana pozycja" nie jest podstawa do skoku - wtedy mapa by znikala).
        // Bierzemy pozycje ze smoothera, nie surowa, zeby pojedynczy blad GPS nie
        // przeniosl mapy w dalekie miejsce (ten srodek zapisuje sie w Ustawieniach).
        if (!centeredOnce && hasFix && smooth.hasFix() && map != null
                && System.currentTimeMillis() - MapState.get(this).lastFixAt() < 15000L) {
            centeredOnce = true;
            try {
                map.getController().setCenter(new GeoPoint(smooth.lat(), smooth.lon()));
                // zachowujemy obecny zoom — nie skaczemy na 13 zeby nie
                // wyczyscic kafli na slabym laczu/offline.
            } catch (Throwable ignored) {
            }
        }
        // Dystans do celu liczony z przyjetej pozycji - bledny fix potrafil
        // pokazac "o 3 km dalej do celu" na jedna sekunde.
        if (hasDest && dest != null && hasFix && map != null && smooth.hasFix()) {
            try {
                GeoPoint here = new GeoPoint(smooth.lat(), smooth.lon());
                destDistM = (float) here.distanceToAsDouble(dest);
                destBearing = (float) here.bearingTo(dest);
                List<GeoPoint> pts = new ArrayList<GeoPoint>();
                pts.add(here);
                pts.add(dest);
                destLine.setPoints(pts);
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
        // prowadzenie po trasie: dziala niezaleznie od odswiezania znacznika
        if (navigating && guide != null && (smooth.hasFix() || smooth.seededKnown())) {
            try {
                Guidance.Update u = guide.update(smooth.lat(), smooth.lon(),
                        smooth.markerBearing(), smooth.speedMs(), SystemClock.uptimeMillis());
                navRemainingM = (float) u.remainingM;
                navEtaS = (float) u.etaS;
                navManeuverDistM = (float) u.distToManeuverM;
                if (u.instruction != null && u.instruction.length() > 0) {
                    navInstruction = u.instruction;
                }
                boolean speak = Prefs.mapVoice(this) && voice != null;
                if (u.prompt == Guidance.PROMPT_FAR) {
                    if (speak) voice.speak("Za " + Maneuvers.distText(u.distToManeuverM)
                            + ": " + u.instruction);
                } else if (u.prompt == Guidance.PROMPT_NOW) {
                    if (speak) voice.speak(u.instruction + ", teraz");
                } else if (u.prompt == Guidance.PROMPT_ARRIVED) {
                    if (speak) voice.speak("Dojechałeś do celu");
                    Toast.makeText(this, "Cel osiągnięty", Toast.LENGTH_LONG).show();
                    clearDestination();
                } else if (u.prompt == Guidance.PROMPT_OFF_ROUTE) {
                    if (speak) voice.speak("Zjechałeś z trasy. Przeliczam.");
                    reroute();
                }
                invalidateControls();
            } catch (Throwable ignored) {
            }
        }
        // pozycja z pamieci ("gdzie zaparkowalem") tez ma pokazac znacznik -
        // dzieki temu po wejsciu do auta mapa jest od razu wlasciwa
        if ((!smooth.hasFix() && !smooth.seededKnown()) || !smooth.dirty()) return;
        if (map != null) {
            // false = bez requestLayout na kazdej klatce (rysujemy od razu nizej):
            // requestLayout 30x/s na MT8163 to zbedny pomiar i uklad.
            map.setMapOrientation(smooth.mapOrientation(), false);
            if (Prefs.mapFollow(this) && !followPaused) {
                tmpPoint.setCoords(smooth.lat(), smooth.lon());
                map.getController().setCenter(tmpPoint);
            }
            map.invalidate();
        }
        if (loc != null) {
            loc.feed(smooth.lat(), smooth.lon(), smooth.markerBearing(),
                    smooth.accuracy(), smooth.isMoving());
        }
        // kompas i wskaznik predkosci ida ta sama, wygladzona wartoscia
        currentSpeedMs = smooth.speedMs();
        currentBearing = smooth.markerBearing();
        invalidateControls();
    }

    // ------------------------------------------------------------ cel / wyszukiwanie

    private void setDestination(GeoPoint p) {
        if (p == null || map == null) return;
        dest = p;
        hasDest = true;
        destDistM = -1f;
        destBearing = -1f;
        if (destLine != null) destLine.setPoints(new ArrayList<GeoPoint>());
        if (map != null) map.invalidate();
        invalidateControls();
        Toast.makeText(this, "Cel ustawiony — dotknij paska CEL, aby usunąć",
                Toast.LENGTH_SHORT).show();
    }

    private void clearDestination() {
        stopNavigation(false);   // usuniecie celu konczy tez nawigacje (bez komunikatu)
        hasDest = false;
        dest = null;
        destDistM = -1f;
        destBearing = -1f;
        if (destLine != null) destLine.setPoints(new ArrayList<GeoPoint>());
        if (map != null) map.invalidate();
        invalidateControls();
    }

    // ---------------- nawigacja: trasa OSRM, prowadzenie, glos ----------------

    /** Pobiera trase z OSRM (profil z ustawien: auto/rower/pieszo) i rozpoczyna prowadzenie. */
    private void startNavigation() {
        if (!hasDest || dest == null) {
            Toast.makeText(this, "Najpierw ustaw cel — długie przytrzymanie na mapie",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!smooth.hasFix() && !smooth.seededKnown()) {
            Toast.makeText(this, "Czekam na GPS — nie mogę wyznaczyć trasy", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Wyznaczam trasę…", Toast.LENGTH_SHORT).show();
        final double fla = smooth.lat();
        final double flo = smooth.lon();
        RouteClient.route(Prefs.mapRouteProfile(this), fla, flo,
                dest.getLatitude(), dest.getLongitude(), new RouteClient.Callback() {
                    @Override
                    public void onRoute(Route r) {
                        applyRoute(r, true);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(NavigatorActivity.this,
                                "Brak trasy (internet?) — pokazuję tylko kierunek",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void applyRoute(Route r, boolean announce) {
        route = r;
        guide = new Guidance(r);
        navigating = true;
        navInstruction = "Jedź zgodnie z trasą";
        navManeuverDistM = -1f;
        navRemainingM = (float) r.distanceM;
        navEtaS = (float) r.durationS;
        if (routeLine != null && map != null) {
            List<GeoPoint> pts = new ArrayList<GeoPoint>();
            for (int i = 0; i < r.pointCount(); i++) {
                pts.add(new GeoPoint(r.lats[i], r.lons[i]));
            }
            routeLine.setPoints(pts);
            map.getController().setZoom(16d);
            map.invalidate();
        }
        String info = Maneuvers.distText(r.distanceM) + " · " + Maneuvers.minText(r.durationS);
        if (announce) {
            Toast.makeText(this, "Trasa: " + info, Toast.LENGTH_LONG).show();
            if (Prefs.mapVoice(this) && voice != null) {
                voice.speak("Nawigacja rozpoczęta. " + info + " do celu");
            }
        }
        invalidateControls();
    }

    private void stopNavigation(boolean speak) {
        navigating = false;
        guide = null;
        route = null;
        navInstruction = "";
        navManeuverDistM = -1f;
        navRemainingM = -1f;
        navEtaS = -1f;
        if (routeLine != null) routeLine.setPoints(new ArrayList<GeoPoint>());
        if (map != null) map.invalidate();
        if (speak && Prefs.mapVoice(this) && voice != null) {
            voice.speak("Nawigacja zakończona");
        }
        invalidateControls();
    }

    /** Przelicza trase po zjezdzie z kursu; nie czesciej niz raz na 15 s. */
    private void reroute() {
        long now = SystemClock.uptimeMillis();
        if (now - lastRerouteAt < 15000L) return;
        lastRerouteAt = now;
        if (!hasDest || dest == null || (!smooth.hasFix() && !smooth.seededKnown())) return;
        final double fla = smooth.lat();
        final double flo = smooth.lon();
        RouteClient.route(Prefs.mapRouteProfile(this), fla, flo,
                dest.getLatitude(), dest.getLongitude(), new RouteClient.Callback() {
                    @Override
                    public void onRoute(Route r) {
                        applyRoute(r, false);
                    }

                    @Override
                    public void onError(String message) {
                        // zostaje stara trasa; kolejny tick zjedzenia z trasy sprobuje znowu
                    }
                });
    }

    /** Rysuje slad z pliku GPX (track_path z BookmarksActivity). */
    private void showTrack(String path) {
        if (path == null || map == null || trackLine == null) return;
        try {
            java.io.File f = new java.io.File(path);
            if (!f.exists()) return;
            String gpx = TrackFiles.read(this, f);
            List<double[]> pts = GpxIo.parseTrack(gpx);
            List<GeoPoint> gp = new ArrayList<GeoPoint>();
            for (int i = 0; i < pts.size(); i++) {
                gp.add(new GeoPoint(pts.get(i)[0], pts.get(i)[1]));
            }
            trackLine.setPoints(gp);
            if (!gp.isEmpty()) {
                map.getController().setCenter(gp.get(gp.size() / 2));
                if (gp.size() > 1) map.getController().setZoom(15d);
                map.invalidate();
                Toast.makeText(this, "Ślad: " + gp.size() + " punktów", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable tr) {
            Toast.makeText(this, "Nie udało się wczytać śladu", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleTrack() {
        if (recording) {
            recording = false;
            if (recorder.size() < 2) {
                Toast.makeText(this, "Za krótki ślad — nie zapisuję", Toast.LENGTH_SHORT).show();
            } else {
                String name = "slad-" + System.currentTimeMillis();
                java.io.File f = TrackFiles.save(this, name,
                        GpxIo.writeTrack(name, recorder.lats(), recorder.lons(), recorder.times()));
                Toast.makeText(this, f != null
                        ? "Ślad zapisany (" + Maneuvers.distText(recorder.totalM()) + ")"
                        : "Nie udało się zapisać śladu", Toast.LENGTH_LONG).show();
            }
            recorder.reset();
        } else {
            recorder.reset();
            recording = true;
            Toast.makeText(this, "Nagrywanie śladu rozpoczęte", Toast.LENGTH_SHORT).show();
        }
        invalidateControls();
    }

    /** Awaryjny zapis sladu przy zamykaniu Nawigacji (przycisk X, HOME). */
    private void saveTrackIfRecording() {
        if (!recording || recorder.size() < 2) {
            recording = false;
            return;
        }
        try {
            String name = "slad-" + System.currentTimeMillis();
            TrackFiles.save(this, name,
                    GpxIo.writeTrack(name, recorder.lats(), recorder.lons(), recorder.times()));
        } catch (Throwable ignored) {
        }
        recording = false;
    }

    private void saveBookmark() {
        if (!smooth.hasFix() && !smooth.seededKnown()) {
            Toast.makeText(this, "Czekam na GPS — nie wiem, gdzie jesteś", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String name = new java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.US)
                    .format(new java.util.Date());
            MapsDb.get(this).addBookmark(name, smooth.lat(), smooth.lon());
            Toast.makeText(this, "Zapisano zakładkę: " + name, Toast.LENGTH_SHORT).show();
        } catch (Throwable tr) {
            Toast.makeText(this, "Nie udało się zapisać zakładki", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBookmarks() {
        try {
            startActivity(new Intent(this, BookmarksActivity.class));
        } catch (Throwable tr) {
            Toast.makeText(this, "Zakładki niedostępne", Toast.LENGTH_SHORT).show();
        }
    }

    /** Miejsca z Wikipedii wokol pozycji (jak warstwa "Wikipedia" w Organic Maps). */
    private void wikiNearby() {
        double la;
        double lo;
        if (smooth.hasFix() || smooth.seededKnown()) {
            la = smooth.lat();
            lo = smooth.lon();
        } else {
            try {
                la = Double.parseDouble(Prefs.posLastLat(this));
                lo = Double.parseDouble(Prefs.posLastLon(this));
            } catch (Throwable tr) {
                Toast.makeText(this, "Nie wiem, gdzie jesteś", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        Toast.makeText(this, "Szukam miejsc z Wikipedii…", Toast.LENGTH_SHORT).show();
        WikiNearby.search(la, lo, 10000d, new WikiNearby.Callback() {
            @Override
            public void onResults(List<String> names, List<double[]> latlon) {
                if (latlon.isEmpty()) {
                    Toast.makeText(NavigatorActivity.this, "Brak miejsc w okolicy",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                final List<GeoPoint> pts = new ArrayList<GeoPoint>();
                for (int i = 0; i < latlon.size(); i++) {
                    pts.add(new GeoPoint(latlon.get(i)[0], latlon.get(i)[1]));
                }
                showResults(names, pts);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(NavigatorActivity.this, "Brak internetu",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Wyszukiwanie adresow / miejsc przez Nominatim (otwarte mapy OSM).
    private void openSearch() {
        try {
            final android.widget.EditText et = new android.widget.EditText(this);
            et.setSingleLine(true);
            et.setHint("np. Warszawa, ul. Marszałkowska…");
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Szukaj miejsca")
                    .setView(et)
                    .setPositiveButton("Szukaj", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int w) {
                            search(et.getText().toString().trim());
                        }
                    })
                    .setNegativeButton("Anuluj", null)
                    .show();
        } catch (Throwable tr) {
            Toast.makeText(this, "Wyszukiwanie niedostępne", Toast.LENGTH_SHORT).show();
        }
    }

    private void search(final String q) {
        if (q.length() == 0) return;
        // najpierw zapisane zakladki i zapamietane miejsca - dzialaja offline
        final List<String> names = new ArrayList<String>();
        final List<GeoPoint> pts = new ArrayList<GeoPoint>();
        try {
            List<Bookmark> bs = MapsDb.get(this).searchBookmarks(q, 5);
            for (int i = 0; i < bs.size(); i++) {
                names.add("★ " + bs.get(i).name);
                pts.add(new GeoPoint(bs.get(i).lat, bs.get(i).lon));
            }
            List<Bookmark> ps = MapsDb.get(this).searchPlaces(q, 5);
            for (int i = 0; i < ps.size(); i++) {
                names.add(ps.get(i).name);
                pts.add(new GeoPoint(ps.get(i).lat, ps.get(i).lon));
            }
        } catch (Throwable ignored) {
        }
        final String url;
        try {
            url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q="
                    + java.net.URLEncoder.encode(q, "UTF-8");
        } catch (Throwable tr) {
            showResults(names, pts);
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    java.net.HttpURLConnection c = null;
                    try {
                        java.net.URL u = new java.net.URL(url);
                        c = (java.net.HttpURLConnection) u.openConnection();
                        c.setRequestProperty("User-Agent", "pl.dock.widget/1.0 (car launcher)");
                        c.setConnectTimeout(8000);
                        c.setReadTimeout(8000);
                        java.io.InputStream is = c.getInputStream();
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[4096];
                        int n;
                        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                        String json = new String(bos.toByteArray(), "UTF-8");
                        org.json.JSONArray arr = new org.json.JSONArray(json);
                        for (int i = 0; i < arr.length(); i++) {
                            org.json.JSONObject o = arr.getJSONObject(i);
                            names.add(o.optString("display_name", "?"));
                            pts.add(new GeoPoint(o.optDouble("lat", 0), o.optDouble("lon", 0)));
                        }
                    } finally {
                        if (c != null) c.disconnect();
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showResults(names, pts);
                        }
                    });
                } catch (Throwable tr) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // brak sieci, ale zakladki/miejsca offline sa - pokazujemy je
                            if (!names.isEmpty()) {
                                showResults(names, pts);
                            } else {
                                Toast.makeText(NavigatorActivity.this,
                                        "Brak wyników lub brak internetu", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                }
            }
        }).start();
    }

    private void showResults(final List<String> names, final List<GeoPoint> pts) {
        if (names.isEmpty() || map == null) {
            Toast.makeText(this, "Brak wyników", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Wyniki")
                .setItems(names.toArray(new String[0]),
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which) {
                                if (map == null) return;
                                GeoPoint p = pts.get(which);
                                // zapamietaj miejsce - nastepnym razem znajdzie sie offline
                                try {
                                    MapsDb.get(NavigatorActivity.this).cachePlace(
                                            names.get(which), p.getLatitude(), p.getLongitude());
                                } catch (Throwable ignored) {
                                }
                                map.getController().setCenter(p);
                                map.getController().setZoom(15d);
                                setDestination(p);
                            }
                        })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    /** Znacznik celu na mapie: niebieskie kolo z biala kropka. */
    private class DestOverlay extends org.osmdroid.views.overlay.Overlay {
        @Override
        public void draw(Canvas c, MapView mapView, boolean shadow) {
            if (shadow || !hasDest || dest == null) return;
            try {
                android.graphics.Point sc = mapView.getProjection().toPixels(dest, null);
                float d = getResources().getDisplayMetrics().density;
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFF0A84FF);
                c.drawCircle(sc.x, sc.y, 9f * d, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3f * d);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(sc.x, sc.y, 9f * d, p);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(sc.x, sc.y, 3.5f * d, p);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            try { map.onResume(); } catch (Throwable ignored) {}
        }
        MapState.get(this).addListener(this);
        followPaused = false;
        startLoop();
        applyImmersive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MapState.get(this).removeListener(this);
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
        Prefs.setPosLast(this, smooth.lat(), smooth.lon(), now);
    }

    private void savePosition() {
        if (map == null) return;
        try {
            org.osmdroid.api.IGeoPoint c = map.getMapCenter();
            if (c != null) {
                Prefs.setMapLast(this, c.getLatitude(), c.getLongitude(),
                        (float) map.getZoomLevelDouble());
            }
        } catch (Throwable ignored) {
        }
        lastGoodAt = 0L;            // przy zamykaniu zapisujemy pozycje na pewno
        saveLastGood();
    }

    @Override
    protected void onDestroy() {
        saveTrackIfRecording();
        if (voice != null) {
            try { voice.shutdown(); } catch (Throwable ignored) {}
        }
        if (map != null) {
            try { map.onDetach(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    private void applyImmersive() {
        try {
            int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            if (!Look.night(this)) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    /**
     * Pionowa kolumna duzych, czytelnych przyciskow po przeciwnej stronie docka.
     * Rysowana recznie (bez XML/AndroidX), dotyk wykrywany po obszarze.
     */
    // CarPlay-style chrome: przezroczyste elementy nakladane na mape
    private class ChromeView extends View {

        private static final int BTN_CLOSE = 0;
        private static final int BTN_ZOOM_IN = 1;
        private static final int BTN_ZOOM_OUT = 2;
        private static final int BTN_FOLLOW = 3;
        private static final int BTN_LAYER = 4;
        private static final int BTN_ONLINE = 5;
        private static final int BTN_SEARCH = 6;
        private static final int BTN_SAVE = 7;
        private static final int BTN_TRACK = 8;
        private static final int BTN_NAV = 9;

        private final float d;
        private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int pressed = -1;

        ChromeView(Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            setContentDescription("Nawigacja CarPlay");
        }

        private float btnColX() {
            boolean left = Ui.dockLeft(getContext());
            float w = 48f * d;
            float margin = 8f * d;
            return left ? getWidth() - w - margin : margin;
        }

        private float btnColY() {
            int n = 10;
            float total = n * (36f * d) + (n - 1) * (6f * d);
            return (getHeight() - total) / 2f;
        }

        private RectF rectFor(int i) {
            float x = btnColX();
            float y = btnColY() + i * (42f * d);
            return new RectF(x, y, x + 48f * d, y + 36f * d);
        }

        private RectF topBarRect() {
            return new RectF(0, 0, getWidth(), 48f * d);
        }

        private float speedCx() {
            boolean left = Ui.dockLeft(getContext());
            float dockP = Ui.dp(getContext(), Ui.DOCK_OFFSET_DP);
            return left ? dockP + 62f * d : getWidth() - dockP - 62f * d;
        }

        private float speedCy() {
            return 52f * d + 60f * d;
        }

        private RectF speedRect = new RectF();
        private Typeface numTf, unitTf;

        private float speedR() {
            return 34f * d;
        }

        private RectF destCardRect() {
            float m = 12f * d;
            float w = 220f * d;
            // podczas nawigacji karta jest wyzsza: manewr + dystanse
            float h = (navigating ? 66f : 44f) * d;
            float y = getHeight() - h - m;
            boolean left = Ui.dockLeft(getContext());
            float x = left ? (Ui.dp(getContext(), Ui.DOCK_OFFSET_DP) + 12f * d) : 12f * d;
            return new RectF(x, y, x + w, y + h);
        }

        private String[] labels() {
            boolean follow = Prefs.mapFollow(getContext());
            return new String[]{
                    "X",
                    "+",
                    "-",
                    follow ? "Sledz" : "Sledz",
                    "Mapa",
                    "Net",
                    "Szukaj",
                    "Zap",
                    "Ślad",
                    "Jedź"
            };
        }

        @Override
        protected void onDraw(Canvas cv) {
// === plakietka predkosci: prostokat z rogami (taki sam jak na kafelku
            // mapy na ekranie glownym - ten sam rozmiar, promien i czcionki) ===
            float scx = speedCx();
            float scy = speedCy();
            float bw = 84f * d, bh = 64f * d;
            speedRect.set(scx - bw / 2f, scy - bh / 2f, scx + bw / 2f, scy + bh / 2f);
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(night ? 0xE60E0E0F : 0xE6F5F5F7);
            cv.drawRoundRect(speedRect, 20f * d, 20f * d, pt);
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(2f * d);
            pt.setColor(night ? 0xFF0A84FF : 0xFF0A6CFF);
            cv.drawRoundRect(speedRect, 20f * d, 20f * d, pt);
            if (numTf == null) {            // czcionki launchera - raz, nie co klatke
                numTf = pl.dock.widget.ui.DockTheme.interSemiBold();
                unitTf = pl.dock.widget.ui.DockTheme.interMedium();
            }
            String kphS = hasFix ? String.valueOf(Math.round(currentSpeedMs * 3.6f)) : "—";
            drawTextTf(cv, kphS, speedRect.centerX(), speedRect.top + 25f * d,
                    night ? 0xFFFFFFFF : 0xFF10131A, 26f, Paint.Align.CENTER, numTf);
            drawTextTf(cv, "km/h", speedRect.centerX(), speedRect.top + 46f * d,
                    night ? 0xFF8E8E93 : 0xFF6B7686, 11f, Paint.Align.CENTER, unitTf);

            // === przyciski (plywajace pigułki) ===
            String[] labs = labels();
            for (int i = 0; i < labs.length; i++) {
                RectF rr = rectFor(i);
                boolean act = (i == BTN_FOLLOW && Prefs.mapFollow(getContext()))
                        || (i == BTN_ONLINE && Prefs.mapOnline(getContext()))
                        || (i == BTN_LAYER && "topo".equals(Prefs.mapStyle(getContext())))
                        || (i == BTN_TRACK && recording)
                        || (i == BTN_NAV && navigating);
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(pressed == i ? 0xFF2C2C2E : 0xCC202020);
                cv.drawRoundRect(rr, 16f * d, 16f * d, pt);
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(1.5f * d);
                pt.setColor(act ? 0xFF0A84FF : 0x33FFFFFF);
                cv.drawRoundRect(rr, 16f * d, 16f * d, pt);
                drawText(cv, labs[i], rr.centerX(), rr.centerY() - 1f * d,
                        act ? 0xFF0A84FF : 0xCCFFFFFF, 11f, Paint.Align.CENTER);
            }

            // === karta celu (dol-lewo) ===
            if (hasDest) {
                RectF dr = destCardRect();
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(0xCC000000);
                cv.drawRoundRect(dr, 14f * d, 14f * d, pt);
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(1.5f * d);
                pt.setColor(0xFF0A84FF);
                cv.drawRoundRect(dr, 14f * d, 14f * d, pt);
                String ct;
                if (navigating) {
                    // dwie linie: manewr + "manewr · do celu · czas"
                    drawText(cv, navInstruction.length() > 0 ? navInstruction : "Jedź zgodnie z trasą",
                            dr.centerX(), dr.top + 20f * d, 0xFFFFFFFF, 11f, Paint.Align.CENTER);
                    StringBuilder sub = new StringBuilder();
                    if (navManeuverDistM >= 0f) sub.append(Maneuvers.distText(navManeuverDistM));
                    if (navRemainingM >= 0f) {
                        if (sub.length() > 0) sub.append("  ·  ");
                        sub.append(Maneuvers.distText(navRemainingM));
                    }
                    if (navEtaS >= 0f) {
                        if (sub.length() > 0) sub.append("  ·  ");
                        sub.append(Maneuvers.minText(navEtaS));
                    }
                    if (sub.length() == 0) sub.append("czekam na GPS");
                    drawText(cv, sub.toString(), dr.centerX(), dr.top + 46f * d,
                            0xFF30D158, 11f, Paint.Align.CENTER);
                } else if (destDistM >= 0) {
                    String dd = destDistM >= 1000
                            ? String.format(java.util.Locale.US, "%.1f km", destDistM / 1000f)
                            : String.format(java.util.Locale.US, "%.0f m", destDistM);
                    ct = "↑  " + dd + "  " + Math.round(destBearing) + "°";
                    drawText(cv, ct, dr.centerX(), dr.centerY(), 0xFF0A84FF, 12f, Paint.Align.CENTER);
                } else {
                    ct = "↑  Cel  (czekam na GPS)";
                    drawText(cv, ct, dr.centerX(), dr.centerY(), 0xFF0A84FF, 12f, Paint.Align.CENTER);
                }
            }
        }

        /** Jak drawText, ale z podana czcionka (czcionki launchera dla plakietki). */
        private void drawTextTf(Canvas cv, String s, float x, float cy, int color,
                                float size, Paint.Align align, Typeface tf) {
            tx.setTypeface(tf);
            tx.setTextAlign(align);
            tx.setTextSize(size * d);
            tx.setColor(color);
            Paint.FontMetrics fm = tx.getFontMetrics();
            cv.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, tx);
        }

        private void drawText(Canvas cv, String s, float x, float cy, int color, float size, Paint.Align align) {
            tx.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            tx.setTextAlign(align);
            tx.setTextSize(size * d);
            tx.setColor(color);
            Paint.FontMetrics fm = tx.getFontMetrics();
            cv.drawText(s, x, cy - (fm.ascent + fm.descent) / 2f, tx);
        }

        private void drawCompass(Canvas cv, float cx, float cy, float bearingDeg) {
            cv.save();
            cv.rotate(bearingDeg, cx, cy);
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(cx, cy - 14f * d);
            p.lineTo(cx - 7f * d, cy + 6f * d);
            p.lineTo(cx + 7f * d, cy + 6f * d);
            p.close();
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(0xFFFF3B30);
            cv.drawPath(p, pt);
            p.reset();
            p.moveTo(cx, cy + 14f * d);
            p.lineTo(cx - 7f * d, cy - 6f * d);
            p.lineTo(cx + 7f * d, cy - 6f * d);
            p.close();
            pt.setColor(0xFF909090);
            cv.drawPath(p, pt);
            cv.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            int idx = -1;
            String[] labs = labels();
            for (int i = 0; i < labs.length; i++) {
                if (rectFor(i).contains(e.getX(), e.getY())) {
                    idx = i;
                    break;
                }
            }
            boolean onDest = hasDest && destCardRect().contains(e.getX(), e.getY());
            if (a == MotionEvent.ACTION_DOWN) {
                pressed = onDest ? -2 : idx;
                if (pressed >= 0) invalidate();
                return onDest || idx >= 0;
            }
            if (a == MotionEvent.ACTION_UP) {
                int was = pressed;
                pressed = -1;
                invalidate();
                if (onDest) {
                    NavigatorActivity.this.clearDestination();
                    return true;
                }
                if (idx >= 0 && idx == was) {
                    // dlugie przytrzymanie = druga funkcja (jak w Organic Maps)
                    if (e.getEventTime() - e.getDownTime() > 550L) {
                        handleLongPress(idx);
                    } else {
                        handleButton(idx);
                    }
                    return true;
                }
                return false;
            }
            if (a == MotionEvent.ACTION_MOVE) {
                int np = (onDest || idx >= 0) ? idx : -1;
                if (np != pressed) { pressed = np; invalidate(); }
                return pressed >= 0;
            }
            if (a == MotionEvent.ACTION_CANCEL) {
                pressed = -1; invalidate(); return false;
            }
            return false;
        }

        private void handleLongPress(int i) {
            // dlugie przytrzymanie otwiera managera; dla "Szukaj" - Wikipedie wokol
            if (i == BTN_SAVE || i == BTN_TRACK) {
                openBookmarks();
            } else if (i == BTN_SEARCH) {
                wikiNearby();
            } else {
                handleButton(i);
            }
        }

        private void handleButton(int i) {
            if (i == BTN_CLOSE) finish();
            else if (i == BTN_ZOOM_IN && map != null) map.getController().zoomIn();
            else if (i == BTN_ZOOM_OUT && map != null) map.getController().zoomOut();
            else if (i == BTN_FOLLOW) toggleFollow();
            else if (i == BTN_LAYER) toggleLayer();
            else if (i == BTN_ONLINE) toggleOnline();
            else if (i == BTN_SEARCH) openSearch();
            else if (i == BTN_SAVE) saveBookmark();
            else if (i == BTN_TRACK) toggleTrack();
            else if (i == BTN_NAV) {
                if (navigating) stopNavigation(true);
                else startNavigation();
            }
        }
    }
}

