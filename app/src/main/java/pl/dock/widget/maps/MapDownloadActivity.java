package pl.dock.widget.maps;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.views.MapView;

import java.util.Locale;

import java.util.Calendar;
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
 * "Pobierz mapę regionu" - jednorazowe pobranie kafli calego regionu (np. Mazowsze)
 * przy hotspotcie z telefonu, zeby offline dzialalo od razu na dluzszej trasie
 * (bez jezdzenia i klikania "Pobierz okolice" w Nawigacji).
 *
 * Pobieranie idzie przez osmdroid CacheManager (te same kafle, ten sam katalog
 * filesDir/osmdroid co mapa), wiec zapisane regiony dzialaja pozniej bez internetu.
 * Liczba kafli i procent pochodza z osmdroid - nic nie jest szacowane.
 */
public class MapDownloadActivity extends BaseActivity {

    /** Region offline: nazwa + prostokat (N/S/E/W) + zakres zoomu do pobrania. */
    private static final class Region {
        final String name;
        final double north, south, east, west;
        final int minZoom, maxZoom;

        Region(String name, double north, double south, double east, double west,
               int minZoom, int maxZoom) {
            this.name = name;
            this.north = north;
            this.south = south;
            this.east = east;
            this.west = west;
            this.minZoom = minZoom;
            this.maxZoom = maxZoom;
        }

        BoundingBox bbox() {
            return new BoundingBox(north, east, south, west);
        }
    }

    private static final Region[] REGIONS = {
            new Region("Mazowsze (Warszawa)", 52.40, 51.85, 21.50, 20.55, 11, 15),
            new Region("Małopolska (Kraków)", 50.35, 49.85, 20.30, 19.55, 11, 15),
            new Region("Śląsk (Katowice)", 50.50, 49.90, 19.40, 18.60, 11, 15),
            new Region("Trójmiasto", 54.60, 54.20, 18.90, 18.25, 11, 15),
            new Region("Wielkopolska (Poznań)", 52.60, 52.20, 17.15, 16.55, 11, 15),
            new Region("Dolnośląskie (Wrocław)", 51.30, 50.90, 17.30, 16.70, 11, 15),
            new Region("Cała Polska (niska jakość)", 54.95, 48.95, 24.20, 14.05, 7, 10),
    };

    private boolean night;
    private MapView map;
    private DockView dock;
    private ProgressBar bar;
    private TextView status;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle b) {
        night = Look.night(this);
        super.onCreate(b);
        MapConfig.init(this);

        int bg = night ? Color.parseColor("#0B0C0E") : Color.parseColor("#E8E8ED");
        int card = night ? Color.parseColor("#1C1C1E") : Color.parseColor("#FFFFFF");
        int text = night ? Color.WHITE : Color.parseColor("#10131A");
        int dim = night ? Color.parseColor("#8E8E93") : Color.parseColor("#6B7686");

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Ui.dp(this, 18), Ui.dp(this, 20), Ui.dp(this, 18), Ui.dp(this, 14));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        // tresc odsunieta od doku (dock jest po lewej albo po prawej stronie)
        if (Ui.dockLeft(this)) clp.leftMargin = Ui.dp(this, Ui.DOCK_OFFSET_DP);
        else clp.rightMargin = Ui.dp(this, Ui.DOCK_OFFSET_DP);
        root.addView(col, clp);

        TextView title = new TextView(this);
        title.setText("Pobierz mapy offline");
        title.setTextSize(20f);
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextColor(text);
        col.addView(title);

        TextView info = new TextView(this);
        info.setText("Włącz hotspot z telefonu i pobierz region raz. Zapisane mapy "
                + "działają później bez internetu. Pobieranie można przerwać przyciskiem Wstecz.");
        info.setTextSize(12f);
        info.setTextColor(dim);
        info.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        col.addView(info);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        col.addView(bar);

        status = new TextView(this);
        status.setText("Gotowe do pobrania.");
        status.setTextSize(12f);
        status.setTextColor(dim);
        status.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        col.addView(status);

        ScrollView sc = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sc.addView(list);
        col.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        for (final Region r : REGIONS) list.addView(regionBtn(r, card, text));

        Button back = new Button(this);
        back.setText("WSTECZ (ZAMKNIJ)");
        back.setAllCaps(false);
        back.setTextSize(13f);
        back.setTextColor(text);
        GradientDrawable bd = new GradientDrawable();
        bd.setCornerRadius(Ui.dp(this, 20));
        bd.setColor(night ? Color.parseColor("#2C2C2E") : Color.parseColor("#D8DCE6"));
        back.setBackground(bd);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        col.addView(back);

        // Dock Docka po wlasciwej stronie - zostaje widoczny i klikalny.
        final boolean dockLeft = Ui.dockLeft(this);
        dock = new DockView(this);
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                Ui.dp(this, Ui.DOCK_OFFSET_DP), ViewGroup.LayoutParams.MATCH_PARENT,
                dockLeft ? Gravity.START : Gravity.END);
        root.addView(dock, dockLp);
        wireDock();

        setContentView(root);

        // Niewidoczna mapa 2x2 px: CacheManager potrzebuje provider'a kafli
        // z prawdziwego MapView, ale do samego pobierania widok nie jest potrzebny.
        try {
            map = new MapView(this);
            map.setTileSource("topo".equals(Prefs.mapStyle(this))
                    ? TileSourceFactory.OpenTopo : TileSourceFactory.MAPNIK);
            map.setUseDataConnection(true);
            map.setVisibility(View.INVISIBLE);
            FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(2, 2);
            root.addView(map, mlp);
        } catch (Throwable ignored) {
            map = null;
        }
    }

    private void wireDock() {
        Bitmap[] dockIcons = new Bitmap[4];
        List<AppEntry> top = Prefs.top(this, 3);
        for (int i = 0; i < 3 && i < top.size(); i++) dockIcons[i] = Icons.forEntry(this, top.get(i));
        dockIcons[3] = Icons.forEntry(this, Prefs.carplay(this));
        StatusCenter.Snap s = StatusCenter.get(this);
        Calendar cal = Calendar.getInstance();
        String clock = String.format(Locale.US, "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
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
                List<AppEntry> top = Prefs.top(MapDownloadActivity.this, 3);
                if (index == 3) {
                    Launch.carplay(MapDownloadActivity.this);
                    finish();
                } else if (index < top.size()) {
                    String pkg = top.get(index).pkg;
                    if (Launch.openDockTool(MapDownloadActivity.this, pkg)) {
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

    private Button regionBtn(final Region r, int card, int text) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setText(r.name + "\nzoom " + r.minZoom + "–" + r.maxZoom);
        btn.setTextSize(14f);
        btn.setTextColor(text);
        btn.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        btn.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.dp(this, 14));
        g.setColor(card);
        g.setStroke(Ui.dp(this, 1), night ? 0x33FFFFFF : 0x22000000);
        btn.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 8);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                download(r);
            }
        });
        return btn;
    }

    private void download(final Region r) {
        if (map == null) {
            toast("Mapa niedostępna");
            return;
        }
        if (!BulkGuard.allowed(map)) {
            status.setText(BulkGuard.DENIED_MSG);
            toast(BulkGuard.DENIED_MSG);
            return;
        }
        // Internet tylko na czas pobierania; po zakonczeniu wracamy do poprzedniego stanu.
        final boolean prevOnline = Prefs.mapOnline(this);
        Prefs.setMapOnline(this, true);
        map.setUseDataConnection(true);
        setBusy(true);
        status.setText("Pobieram: " + r.name + "…");
        bar.setProgress(0);

        try {
            CacheManager cm = new CacheManager(map);
            cm.downloadAreaAsyncNoUI(this, r.bbox(), r.minZoom, r.maxZoom,
                    new CacheManager.CacheManagerCallback() {
                        @Override
                        public void onTaskComplete() {
                            Prefs.setMapOnline(MapDownloadActivity.this, prevOnline);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    bar.setProgress(100);
                                    status.setText("Pobrano: " + r.name);
                                    toast("Pobrano: " + r.name);
                                    setBusy(false);
                                }
                            });
                        }

                        @Override
                        public void updateProgress(final int progress, int cz, int zmin, int zmax) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    bar.setProgress(progress);
                                    status.setText(String.format(Locale.US,
                                            "Pobieram: %s — %d%%", r.name, progress));
                                }
                            });
                        }

                        @Override
                        public void downloadStarted() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    status.setText("Pobieranie rozpoczęte: " + r.name);
                                }
                            });
                        }

                        @Override
                        public void setPossibleTilesInArea(final int total) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    status.setText(String.format(Locale.US,
                                            "%s — do pobrania %d kafli", r.name, total));
                                }
                            });
                        }

                        @Override
                        public void onTaskFailed(final int errors) {
                            Prefs.setMapOnline(MapDownloadActivity.this, prevOnline);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    status.setText("Pobieranie nieudane (błędy: " + errors
                                            + "). Sprawdź internet i spróbuj ponownie.");
                                    toast("Pobieranie nieudane");
                                    setBusy(false);
                                }
                            });
                        }
                    });
        } catch (Throwable t) {
            Prefs.setMapOnline(this, prevOnline);
            status.setText("Nie udało się rozpocząć pobierania.");
            setBusy(false);
        }
    }

    private void setBusy(boolean busy) {
        if (list == null) return;
        for (int i = 0; i < list.getChildCount(); i++) {
            list.getChildAt(i).setEnabled(!busy);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (map != null) {
            try { map.onResume(); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (map != null) {
            try { map.onPause(); } catch (Throwable ignored) {}
        }
    }

    @Override
    protected void onDestroy() {
        if (map != null) {
            try { map.onDetach(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }
}
