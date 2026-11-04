package pl.dock.widget.maps;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
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
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

import pl.dock.widget.AppEntry;
import pl.dock.widget.BaseActivity;
import pl.dock.widget.DockView;
import pl.dock.widget.Icons;
import pl.dock.widget.Launch;
import pl.dock.widget.Look;
import pl.dock.widget.Prefs;
import pl.dock.widget.StatusCenter;
import pl.dock.widget.Ui;

/**
 * Pelnoekranowa Nawigacja: mapa offline (OpenStreetMap) jak mapa w CarPlay.
 *
 * Mapy sa pobrane wczesniej (przycisk "Pobierz okolice" gdy tablet online)
 * i dzialaja bez internetu. Lewy/prawy dock Docka pozostaje widoczny i klikalny.
 */
public class NavigatorActivity extends BaseActivity implements MapState.Listener {

    private boolean night;
    private MapView map;
    private MyLocationNewOverlay loc;
    private DockView dock;
    private ChromeView chrome;
    private boolean hasFix;
    private float currentSpeedMs;
    private float currentBearing;
    private boolean centeredOnce;
    private boolean hasDest;
    private GeoPoint dest;
    private Polyline destLine;
    private DestOverlay destOverlay;
    private float destDistM = -1f;
    private float destBearing = -1f;

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
            map.setBackgroundColor(night ? 0xFF0B0C0E : 0xFFE8E8ED);
            map.getController().setZoom(Math.round(Prefs.mapLastZoom(this)));
            map.setMinZoomLevel(3d);
            map.setMaxZoomLevel(19d);
            loc = new MyLocationNewOverlay(map);
            loc.setDrawAccuracyEnabled(true);
            if (MapState.hasPermission(this)) {
                loc.enableMyLocation();
                if (Prefs.mapFollow(this)) loc.enableFollowLocation();
            }
            map.getOverlays().add(loc);
            destLine = new Polyline();
            destLine.setColor(night ? 0xFF0A84FF : 0xFF0A6CFF);
            destLine.setWidth(6f * getResources().getDisplayMetrics().density);
            map.getOverlays().add(destLine);
            destOverlay = new DestOverlay();
            map.getOverlays().add(destOverlay);
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
    }

    private void restoreCenter() {
        if (map == null) return;
        double lat = 52.07d, lon = 19.48d;   // cala Polska
        boolean hasSaved = false;
        try {
            String la = Prefs.mapLastLat(this);
            String lo = Prefs.mapLastLon(this);
            if (la.length() > 0 && lo.length() > 0) {
                lat = Double.parseDouble(la);
                lon = Double.parseDouble(lo);
                hasSaved = true;
            }
        } catch (Throwable ignored) {
        }
        map.getController().setCenter(new GeoPoint(lat, lon));
        // Bez zapisanej pozycji startujemy z widokiem calej Polski.
        if (!hasSaved && Prefs.mapPoland(this)) {
            map.getController().setZoom(6d);
        }
    }

    private void wireDock() {
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
            }

            @Override
            public void onBottomLong() {
            }
        });
    }

private void toggleFollow() {
        boolean f = !Prefs.mapFollow(this);
        Prefs.setMapFollow(this, f);
        if (loc != null) {
            if (f) loc.enableFollowLocation();
            else loc.disableFollowLocation();
        }
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
        // Auto-centrowanie TYLKO na swiezym fix'ie GPS (stara "ostatnia znana
        // pozycja" nie jest podstawa do skoku - wtedy mapa by znikala).
        if (!centeredOnce && hasFix && map != null
                && System.currentTimeMillis() - MapState.get(this).lastFixAt() < 15000L) {
            centeredOnce = true;
            try {
                map.getController().setCenter(new GeoPoint(lat, lon));
                // zachowujemy obecny zoom — nie skaczemy na 13 zeby nie
                // wyczyscic kafli na slabym laczu/offline.
            } catch (Throwable ignored) {
            }
        }
        if (hasDest && dest != null && hasFix && map != null) {
            try {
                GeoPoint here = new GeoPoint(lat, lon);
                destDistM = (float) here.distanceToAsDouble(dest);
                destBearing = (float) here.bearingTo(dest);
                List<GeoPoint> pts = new ArrayList<GeoPoint>();
                pts.add(here);
                pts.add(dest);
                destLine.setPoints(pts);
            } catch (Throwable ignored) {
            }
        }
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
        hasDest = false;
        dest = null;
        destDistM = -1f;
        destBearing = -1f;
        if (destLine != null) destLine.setPoints(new ArrayList<GeoPoint>());
        if (map != null) map.invalidate();
        invalidateControls();
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
        final String url;
        try {
            url = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&q="
                    + java.net.URLEncoder.encode(q, "UTF-8");
        } catch (Throwable tr) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final List<String> names = new ArrayList<String>();
                    final List<GeoPoint> pts = new ArrayList<GeoPoint>();
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
                            Toast.makeText(NavigatorActivity.this,
                                    "Brak wyników lub brak internetu", Toast.LENGTH_SHORT).show();
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
        if (loc != null && MapState.hasPermission(this)) {
            loc.enableMyLocation();
            if (Prefs.mapFollow(this)) loc.enableFollowLocation();
        }
        MapState.get(this).addListener(this);
        applyImmersive();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MapState.get(this).removeListener(this);
        savePosition();
        if (map != null) {
            try { map.onPause(); } catch (Throwable ignored) {}
        }
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
    }

    @Override
    protected void onDestroy() {
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
            int n = 7;
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

        private float speedR() {
            return 34f * d;
        }

        private RectF destCardRect() {
            float m = 12f * d;
            float w = 220f * d;
            float h = 44f * d;
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
                    "Szukaj"
            };
        }

        @Override
        protected void onDraw(Canvas cv) {
            // === top bar (ETA/dystans/kompas) ===
            RectF tb = topBarRect();
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(night ? 0xCC000000 : 0xCCF5F5F7);
            cv.drawRect(tb, pt);

            // lewa: dystans do celu lub predkosc
            String topLeft;
            if (hasDest && destDistM >= 0) {
                String dd = destDistM >= 1000
                        ? String.format(java.util.Locale.US, "%.1f km", destDistM / 1000f)
                        : String.format(java.util.Locale.US, "%.0f m", destDistM);
                topLeft = "↑  " + dd;
            } else if (hasFix) {
                int kph = Math.round(currentSpeedMs * 3.6f);
                topLeft = String.valueOf(kph) + " km/h";
            } else {
                topLeft = "GPS…";
            }
            drawText(cv, topLeft, tb.left + 14f * d, tb.centerY(),
                    hasFix ? 0xFF30D158 : 0xFFFF9F0A, 13f, Paint.Align.LEFT);

            // prawa: strzalka kierunku (kompas)
            float ccx = tb.right - 30f * d;
            float ccy = tb.centerY();
            drawCompass(cv, ccx, ccy, currentBearing);

            // === kolo predkosci (Waze / CarPlay) ===
            float scx = speedCx();
            float scy = speedCy();
            float sr = speedR();
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(0xCC000000);
            cv.drawCircle(scx, scy, sr, pt);
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeWidth(2f * d);
            pt.setColor(0xFFFFFFFF);
            cv.drawCircle(scx, scy, sr, pt);
            int kph = hasFix ? Math.round(currentSpeedMs * 3.6f) : 0;
            drawText(cv, String.valueOf(kph), scx, scy - 5f * d, 0xFFFFFFFF, 36f, Paint.Align.CENTER);
            drawText(cv, "km/h", scx, scy + 15f * d, 0xFF909090, 10f, Paint.Align.CENTER);

            // === przyciski (plywajace pigułki) ===
            String[] labs = labels();
            for (int i = 0; i < labs.length; i++) {
                RectF rr = rectFor(i);
                boolean act = (i == BTN_FOLLOW && Prefs.mapFollow(getContext()))
                        || (i == BTN_ONLINE && Prefs.mapOnline(getContext()))
                        || (i == BTN_LAYER && "topo".equals(Prefs.mapStyle(getContext())));
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
                if (destDistM >= 0) {
                    String dd = destDistM >= 1000
                            ? String.format(java.util.Locale.US, "%.1f km", destDistM / 1000f)
                            : String.format(java.util.Locale.US, "%.0f m", destDistM);
                    ct = "↑  " + dd + "  " + Math.round(destBearing) + "°";
                } else {
                    ct = "↑  Cel  (czekam na GPS)";
                }
                drawText(cv, ct, dr.centerX(), dr.centerY(), 0xFF0A84FF, 12f, Paint.Align.CENTER);
            }
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
                    handleButton(idx);
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

        private void handleButton(int i) {
            if (i == BTN_CLOSE) finish();
            else if (i == BTN_ZOOM_IN && map != null) map.getController().zoomIn();
            else if (i == BTN_ZOOM_OUT && map != null) map.getController().zoomOut();
            else if (i == BTN_FOLLOW) toggleFollow();
            else if (i == BTN_LAYER) toggleLayer();
            else if (i == BTN_ONLINE) toggleOnline();
            else if (i == BTN_SEARCH) openSearch();
        }
    }
}
