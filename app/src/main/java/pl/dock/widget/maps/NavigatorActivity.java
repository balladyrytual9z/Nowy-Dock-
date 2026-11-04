package pl.dock.widget.maps;

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
    private Controls controls;
    private boolean hasFix;
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

        // warstwa przyciskow (rysowana, jak reszta UI Docka)
        controls = new Controls(this);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                Ui.dp(this, 132), ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dockLeft(this) ? Gravity.END : Gravity.START);
        root.addView(controls, clp);

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

    // akcje z warstwy przyciskow
    private void clickControl(int id) {
        if (id == 0) finish();
        else if (id == 1 && map != null) map.getController().zoomIn();
        else if (id == 2 && map != null) map.getController().zoomOut();
        else if (id == 3) toggleFollow();
        else if (id == 4) toggleLayer();
        else if (id == 5) toggleOnline();
        else if (id == 6) downloadAround();
        else if (id == 7) openSearch();
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
        if (controls != null) controls.invalidate();
    }

    @Override
    public void onLocation(double lat, double lon, float bearing, float speedMs, boolean hasFix) {
        this.hasFix = hasFix;
        // Auto-centrowanie TYLKO na swiezym fix'ie GPS (stara "ostatnia znana
        // pozycja" nie jest podstawa do skoku - wtedy mapa by znikala).
        if (!centeredOnce && hasFix && map != null
                && System.currentTimeMillis() - MapState.get(this).lastFixAt() < 15000L) {
            centeredOnce = true;
            try {
                map.getController().setCenter(new GeoPoint(lat, lon));
                map.getController().setZoom(13d);
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
    private class Controls extends View {

        private final float d;
        private final Paint pt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tx = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private int pressed = -1;

        Controls(android.content.Context c) {
            super(c);
            d = c.getResources().getDisplayMetrics().density;
            setContentDescription("Sterowanie mapą");
        }

        private float itemH() {
            return 62f * d;
        }

        private float top() {
            return 88f * d;
        }

        private RectF rectFor(int i) {
            float m = 10f * d;
            float y = top() + i * (itemH() + 10f * d);
            return new RectF(m, y, getWidth() - m, y + itemH());
        }

        private String[] labels() {
            boolean online = Prefs.mapOnline(getContext());
            boolean topo = "topo".equals(Prefs.mapStyle(getContext()));
            boolean follow = Prefs.mapFollow(getContext());
            return new String[]{"Zamknij", "+", "−", follow ? "Śledź: ON" : "Śledź: OFF",
                    topo ? "Teren" : "Ulice", online ? "Net: ON" : "Net: OFF", "Pobierz okolice",
                    "Szukaj"};
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            String[] lab = labels();
            for (int i = 0; i < lab.length; i++) {
                RectF rr = rectFor(i);
                boolean act = (i == 3 && Prefs.mapFollow(getContext()))
                        || (i == 5 && Prefs.mapOnline(getContext()));
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(pressed == i ? (night ? 0xFF2C2C2E : 0xFFD8DCE6)
                        : (night ? 0xCC1C1C1E : 0xE6FFFFFF));
                cv.drawRoundRect(rr, 16f * d, 16f * d, pt);
                pt.setStyle(Paint.Style.STROKE);
                pt.setStrokeWidth(1.5f * d);
                pt.setColor(act ? 0xFF0A84FF : (night ? 0x33FFFFFF : 0x22000000));
                cv.drawRoundRect(rr, 16f * d, 16f * d, pt);
                txt(cv, lab[i], rr, act ? 0xFF0A84FF : (night ? 0xFFFFFFFF : 0xFF10131A),
                        i == 6 ? 11f : 14f);
            }
            // status GPS u gory
            RectF sr = new RectF(10f * d, 34f * d, getWidth() - 10f * d, 34f * d + 44f * d);
            boolean fix = hasFix;
            pt.setStyle(Paint.Style.FILL);
            pt.setColor(night ? 0xCC000000 : 0xE6FFFFFF);
            cv.drawRoundRect(sr, 14f * d, 14f * d, pt);
            txt(cv, fix ? "GPS OK" : "GPS…", sr, fix ? 0xFF30D158 : 0xFFFF9F0A, 13f);
            // pasek celu na dole (dotknij -> usun cel)
            if (hasDest) {
                RectF dr = destRect();
                pt.setStyle(Paint.Style.FILL);
                pt.setColor(night ? 0xCC000000 : 0xE6FFFFFF);
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
                    ct = "CEL  " + dd + "  ↑  " + Math.round(destBearing) + "°";
                } else {
                    ct = "CEL  (czekam na GPS)";
                }
                txt(cv, ct, dr, 0xFF0A84FF, 11f);
            }
        }

        private RectF destRect() {
            float m = 10f * d;
            float h = 42f * d;
            return new RectF(m, getHeight() - h - 10f * d, getWidth() - m, getHeight() - 10f * d);
        }

        private void txt(Canvas cv, String s, RectF rr, int color, float size) {
            tx.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            tx.setTextAlign(Paint.Align.CENTER);
            tx.setTextSize(size * d);
            tx.setColor(color);
            Paint.FontMetrics fm = tx.getFontMetrics();
            float cy = rr.centerY() - (fm.ascent + fm.descent) / 2f;
            cv.drawText(s, rr.centerX(), cy, tx);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (hasDest && destRect().contains(e.getX(), e.getY())) {
                if (a == MotionEvent.ACTION_UP) {
                    NavigatorActivity.this.clearDestination();
                }
                return true;
            }
            int idx = -1;
            String[] lab = labels();
            for (int i = 0; i < lab.length; i++) {
                if (rectFor(i).contains(e.getX(), e.getY())) {
                    idx = i;
                    break;
                }
            }
            if (a == MotionEvent.ACTION_DOWN) {
                pressed = idx;
                invalidate();
                return true;
            }
            if (a == MotionEvent.ACTION_UP) {
                int was = pressed;
                pressed = -1;
                invalidate();
                if (idx >= 0 && idx == was) {
                    clickIndex(idx);
                }
                return true;
            }
            return true;
        }

        private void clickIndex(int i) {
            NavigatorActivity.this.clickControl(i);
        }
    }
}
