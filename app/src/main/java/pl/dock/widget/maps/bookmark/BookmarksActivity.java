package pl.dock.widget.maps.bookmark;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

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
import pl.dock.widget.maps.NavigatorActivity;
import pl.dock.widget.maps.elevation.ElevationClient;
import pl.dock.widget.maps.elevation.ElevationProfile;
import pl.dock.widget.maps.elevation.ElevationProfileView;
import pl.dock.widget.maps.route.Guidance;

/**
 * Zakladki i slady - odpowiednik "Moje miejsca" z Organic Maps:
 * lista zakladek (tap = cel, przytrzymanie = usun), import/eksport GPX/KML
 * przez systemowe okno plikow (bez uprawnien), lista sladow z rejestatora,
 * profil wysokosci ostatniego sladu (online, opentopodata).
 *
 * Tylko widoki frameworka (jak panel lacznosci - na Mali-T720 custom onDraw
 * w dynamicznie doczepianym poddrzewie by nie ruszal), jedyny Canvas rysuje
 * ElevationProfileView w oknie dialogu aktywnosci.
 */
public class BookmarksActivity extends BaseActivity {

    private static final int REQ_IMPORT = 1;
    private static final int REQ_EXPORT_GPX = 2;
    private static final int REQ_EXPORT_KML = 3;

    private boolean night;
    private LinearLayout rows;
    private DockView dock;

    @Override
    protected void onCreate(Bundle b) {
        night = Look.night(this);
        setTheme(android.R.style.Theme_Translucent_NoTitleBar);
        super.onCreate(b);
        applyImmersive();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(night ? 0xFF0E0F11 : 0xFFF5F5F7);

        ScrollView scroll = new ScrollView(this);
        int dockPx = Ui.dp(this, Ui.DOCK_OFFSET_DP);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (Ui.dockLeft(this)) lp.leftMargin = dockPx;
        else lp.rightMargin = dockPx;
        scroll.setLayoutParams(lp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        box.setPadding(pad, pad, pad, pad);
        scroll.addView(box, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // naglowek
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("ZAKŁADKI I ŚLADY");
        title.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        title.setTextSize(18f);
        title.setTextColor(textColor());
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        header.addView(pill("Zamknij", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        }));
        box.addView(header);
        box.addView(spacer(12));

        // przyciski akcji
        LinearLayout r1 = new LinearLayout(this);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.addView(pill("Zapisz pozycję", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePosition();
            }
        }));
        r1.addView(spacerH(8));
        r1.addView(pill("Import GPX/KML", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    startActivityForResult(i, REQ_IMPORT);
                } catch (Throwable t) {
                    toast("Brak wybieracza plików");
                }
            }
        }));
        box.addView(r1);
        box.addView(spacer(8));
        LinearLayout r2 = new LinearLayout(this);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.addView(pill("Eksport GPX", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportGpx();
            }
        }));
        r2.addView(spacerH(8));
        r2.addView(pill("Eksport KML", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportKml();
            }
        }));
        box.addView(r2);
        box.addView(spacer(8));
        LinearLayout r3 = new LinearLayout(this);
        r3.setOrientation(LinearLayout.HORIZONTAL);
        r3.addView(pill("Profil wysokości śladu", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showLastTrackProfile();
            }
        }));
        box.addView(r3);
        box.addView(spacer(16));

        rows = box;
        root.addView(scroll);

        dock = new DockView(this);
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                dockPx, ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dockLeft(this) ? Gravity.START : Gravity.END);
        root.addView(dock, dockLp);
        wireDock();

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        rebuild();
    }

    // ---------------- listy ----------------

    private void rebuild() {
        // zostaja naglowek i przyciski (pierwsze 8 widokow) - listy dopisujemy od nowa
        while (rows.getChildCount() > 8) rows.removeViewAt(rows.getChildCount() - 1);

        rows.addView(sectionLabel("ZAKŁADKI"));
        List<Bookmark> bs = MapsDb.get(this).listBookmarks();
        if (bs.isEmpty()) {
            rows.addView(hint("Brak zakładek — zapisz pozycję z Nawigacji („Zap”)"));
        } else {
            for (int i = 0; i < bs.size(); i++) {
                final Bookmark bm = bs.get(i);
                View row = itemRow(bm.name,
                        String.format(Locale.US, "%.5f, %.5f", bm.lat, bm.lon),
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent it = new Intent(BookmarksActivity.this, NavigatorActivity.class);
                                it.putExtra("dest_lat", bm.lat);
                                it.putExtra("dest_lon", bm.lon);
                                it.putExtra("dest_name", bm.name);
                                startActivity(it);
                                finish();
                            }
                        },
                        new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                confirmDeleteBookmark(bm);
                                return true;
                            }
                        });
                rows.addView(row);
            }
        }

        rows.addView(spacer(16));
        rows.addView(sectionLabel("ŚLADY"));
        List<java.io.File> ts = TrackFiles.list(this);
        if (ts.isEmpty()) {
            rows.addView(hint("Brak śladów — nagrywaj z Nawigacji („Ślad”)"));
        } else {
            for (int i = 0; i < ts.size(); i++) {
                final java.io.File f = ts.get(i);
                String sub = trackInfo(f);
                View row = itemRow(f.getName(), sub,
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                Intent it = new Intent(BookmarksActivity.this, NavigatorActivity.class);
                                it.putExtra("track_path", f.getAbsolutePath());
                                startActivity(it);
                                finish();
                            }
                        },
                        new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                confirmDeleteTrack(f);
                                return true;
                            }
                        });
                rows.addView(row);
            }
        }
    }

    private String trackInfo(java.io.File f) {
        try {
            List<double[]> pts = GpxIo.parseTrack(TrackFiles.read(this, f));
            double m = 0d;
            for (int i = 1; i < pts.size(); i++) {
                m += Guidance.distM(pts.get(i - 1)[0], pts.get(i - 1)[1],
                        pts.get(i)[0], pts.get(i)[1]);
            }
            return pts.size() + " pkt · " + (m >= 1000d
                    ? String.format(Locale.US, "%.1f km", m / 1000d)
                    : String.format(Locale.US, "%.0f m", m));
        } catch (Throwable t) {
            return "?";
        }
    }

    private void confirmDeleteBookmark(final Bookmark bm) {
        new AlertDialog.Builder(this)
                .setTitle("Usunąć zakładkę?")
                .setMessage(bm.name)
                .setPositiveButton("Usuń", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        MapsDb.get(BookmarksActivity.this).deleteBookmark(bm.id);
                        rebuild();
                    }
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    private void confirmDeleteTrack(final java.io.File f) {
        new AlertDialog.Builder(this)
                .setTitle("Usunąć ślad?")
                .setMessage(f.getName())
                .setPositiveButton("Usuń", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        TrackFiles.delete(f);
                        rebuild();
                    }
                })
                .setNegativeButton("Anuluj", null)
                .show();
    }

    // ---------------- akcje ----------------

    private void savePosition() {
        double lat = 0d;
        double lon = 0d;
        boolean ok = false;
        try {
            pl.dock.widget.maps.MapState st = pl.dock.widget.maps.MapState.get(this);
            if (st.hasFix()) {
                lat = st.lastLat();
                lon = st.lastLon();
                ok = true;
            }
        } catch (Throwable ignored) {
        }
        if (!ok) {
            try {
                lat = Double.parseDouble(Prefs.posLastLat(this));
                lon = Double.parseDouble(Prefs.posLastLon(this));
                ok = lat != 0d || lon != 0d;
            } catch (Throwable ignored) {
            }
        }
        if (!ok) {
            toast("Brak pozycji GPS");
            return;
        }
        String name = "Miejsce " + new java.text.SimpleDateFormat("dd.MM HH:mm", Locale.US)
                .format(new java.util.Date());
        MapsDb.get(this).addBookmark(name, lat, lon);
        toast("Zapisano: " + name);
        rebuild();
    }

    private void exportGpx() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/gpx+xml");
            i.putExtra(Intent.EXTRA_TITLE, "dock-zakladki.gpx");
            startActivityForResult(i, REQ_EXPORT_GPX);
        } catch (Throwable t) {
            toast("Brak wybieracza plików");
        }
    }

    private void exportKml() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/vnd.google-earth.kml+xml");
            i.putExtra(Intent.EXTRA_TITLE, "dock-zakladki.kml");
            startActivityForResult(i, REQ_EXPORT_KML);
        } catch (Throwable t) {
            toast("Brak wybieracza plików");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_IMPORT) {
            importFrom(uri);
        } else if (requestCode == REQ_EXPORT_GPX) {
            writeUri(uri, GpxIo.writeBookmarks(MapsDb.get(this).listBookmarks()));
            toast("Zapisano GPX");
        } else if (requestCode == REQ_EXPORT_KML) {
            writeUri(uri, GpxIo.writeBookmarksKml(MapsDb.get(this).listBookmarks()));
            toast("Zapisano KML");
        }
    }

    private void importFrom(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
            is.close();
            String text = new String(bos.toByteArray(), "UTF-8");
            List<Bookmark> found;
            if (text.contains("<gpx") || text.contains("<GPX")) {
                found = GpxIo.parseWaypoints(text);
            } else if (text.contains("<kml") || text.contains("<KML")) {
                found = GpxIo.parseKmlWaypoints(text);
            } else {
                found = GpxIo.parseWaypoints(text);
                if (found.isEmpty()) found = GpxIo.parseKmlWaypoints(text);
            }
            if (found.isEmpty()) {
                toast("Nie znaleziono punktów w pliku");
                return;
            }
            MapsDb db = MapsDb.get(this);
            for (int i = 0; i < found.size(); i++) {
                db.addBookmark(found.get(i).name.length() > 0
                        ? found.get(i).name : "Import " + (i + 1),
                        found.get(i).lat, found.get(i).lon);
            }
            toast("Zaimportowano " + found.size() + " pkt");
            rebuild();
        } catch (Throwable t) {
            toast("Nie udało się wczytać pliku");
        }
    }

    private void writeUri(Uri uri, String content) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(content.getBytes("UTF-8"));
            os.close();
        } catch (Throwable t) {
            toast("Nie udało się zapisać pliku");
        }
    }

    /** Profil wysokosci ostatniego sladu (opentopodata, online). */
    private void showLastTrackProfile() {
        List<java.io.File> ts = TrackFiles.list(this);
        if (ts.isEmpty()) {
            toast("Brak śladów");
            return;
        }
        final List<double[]> pts = GpxIo.parseTrack(TrackFiles.read(this, ts.get(0)));
        if (pts.size() < 2) {
            toast("Ślad za krótki");
            return;
        }
        // probkowanie do <=100 punktow (limit opentopodata na zapytanie)
        int stride = (pts.size() + 99) / 100;
        int m = (pts.size() + stride - 1) / stride;
        double[] lats = new double[m];
        double[] lons = new double[m];
        int k = 0;
        for (int i = 0; i < pts.size() && k < m; i += stride) {
            lats[k] = pts.get(i)[0];
            lons[k] = pts.get(i)[1];
            k++;
        }
        toast("Pobieram profil wysokości…");
        ElevationClient.fetch(lats, lons, new ElevationClient.Callback() {
            @Override
            public void onResult(double[] elev, String error) {
                if (elev == null) {
                    toast("Brak profilu: " + (error == null ? "brak internetu" : error));
                    return;
                }
                double[] la = new double[elev.length];
                double[] lo = new double[elev.length];
                int c = 0;
                for (int i = 0; i < elev.length; i++) {
                    if (!Double.isNaN(elev[i])) {
                        la[c] = lats[i];
                        lo[c] = lons[i];
                        c++;
                    }
                }
                if (c < 2) {
                    toast("Brak danych wysokości dla tego rejonu");
                    return;
                }
                double[] la2 = new double[c];
                double[] lo2 = new double[c];
                double[] el2 = new double[c];
                for (int i = 0; i < c; i++) {
                    la2[i] = la[i];
                    lo2[i] = lo[i];
                    el2[i] = elev[i];
                }
                final ElevationProfile pf = ElevationProfile.of(la2, lo2, el2);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ElevationProfileView v = new ElevationProfileView(BookmarksActivity.this);
                        v.setNight(night);
                        v.setProfile(pf);
                        new AlertDialog.Builder(BookmarksActivity.this)
                                .setTitle("Profil wysokości")
                                .setView(v)
                                .setPositiveButton("OK", null)
                                .show();
                    }
                });
            }
        });
    }

    // ---------------- UI helpery ----------------

    private int textColor() {
        return night ? 0xFFE8E8ED : 0xFF10131A;
    }

    private int secondaryColor() {
        return night ? 0xFF8E8E93 : 0xFF6B7686;
    }

    private TextView pill(String label, View.OnClickListener click) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13f);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextColor(night ? 0xFFE8E8ED : 0xFF10131A);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 9), Ui.dp(this, 14), Ui.dp(this, 9));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 14));
        bg.setColor(night ? 0xFF1C1C1E : 0xFFFFFFFF);
        bg.setStroke(Ui.dp(this, 1), night ? 0xFF2C2C2E : 0xFFD1D1D6);
        t.setBackgroundDrawable(bg);
        t.setOnClickListener(click);
        return t;
    }

    private TextView sectionLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12f);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t.setTextColor(secondaryColor());
        t.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        return t;
    }

    private TextView hint(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13f);
        t.setTextColor(secondaryColor());
        t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        return t;
    }

    private View itemRow(String title, String sub,
                         View.OnClickListener click,
                         View.OnLongClickListener longClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 14));
        bg.setColor(night ? 0xFF1C1C1E : 0xFFFFFFFF);
        row.setBackgroundDrawable(bg);
        TextView t1 = new TextView(this);
        t1.setText(title);
        t1.setTextSize(15f);
        t1.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        t1.setTextColor(textColor());
        row.addView(t1);
        TextView t2 = new TextView(this);
        t2.setText(sub);
        t2.setTextSize(12f);
        t2.setTextColor(secondaryColor());
        row.addView(t2);
        row.setOnClickListener(click);
        row.setOnLongClickListener(longClick);
        return row;
    }

    private View spacer(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, dp)));
        return v;
    }

    private View spacerH(int dp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(this, dp), ViewGroup.LayoutParams.WRAP_CONTENT));
        return v;
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ---------------- dock + immersja (wzorzec NavigatorActivity) ----------------

    private void wireDock() {
        dock.setBottomProgress(1f);
        android.graphics.Bitmap[] dockIcons = new android.graphics.Bitmap[4];
        List<AppEntry> top = Prefs.top(this, 3);
        for (int i = 0; i < 3 && i < top.size(); i++) {
            dockIcons[i] = Icons.forEntry(this, top.get(i));
        }
        dockIcons[3] = Icons.forEntry(this, Prefs.carplay(this));
        StatusCenter.Snap s = StatusCenter.get(this);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String clock = String.format(Locale.US, "%02d:%02d",
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
                List<AppEntry> top = Prefs.top(BookmarksActivity.this, 3);
                if (index == 3) {
                    Launch.carplay(BookmarksActivity.this);
                    finish();
                } else if (index < top.size()) {
                    String pkg = top.get(index).pkg;
                    if (Launch.openDockTool(BookmarksActivity.this, pkg)) {
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
                Prefs.setGoHome(BookmarksActivity.this, true);
                finish();
            }

            @Override
            public void onBottomLong() {
                RecentAppsPopup.show(BookmarksActivity.this,
                        Prefs.recents(BookmarksActivity.this, 8));
            }
        });
    }

    private void applyImmersive() {
        try {
            int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
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
}
