
package pl.dock.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

public class DockWidget extends AppWidgetProvider {

    private static final int[] TOP_IDS = {R.id.app1, R.id.app2, R.id.app3};

    // sygnatura ostatnio wysłanego widoku: gdy się nie zmieniła, nie przebudowujemy widżetu
    private static String lastSig = "";

    @Override
    public void onEnabled(Context context) {
        StatusService.start(context);
    }

    @Override
    public void onDisabled(Context context) {
        // ostatni widżet usunięty: usługa pierwszoplanowa nie jest już potrzebna
        StatusService.stop(context);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        StatusService.start(context);
        lastSig = signature(context);
        RemoteViews rv = build(context);
        for (int id : ids) {
            manager.updateAppWidget(id, rv);
        }
    }

    private static String signature(Context c) {
        return Prefs.version + "|" + StatusCenter.get(c).sig() + "|" + Look.night(c) + "|" + Icons.version();
    }

    public static void updateAll(Context context) {
        AppWidgetManager m = AppWidgetManager.getInstance(context);
        int[] ids = m.getAppWidgetIds(new ComponentName(context, DockWidget.class));
        if (ids == null || ids.length == 0) return;
        String sig = signature(context);
        if (sig.equals(lastSig)) return;
        lastSig = sig;
        m.updateAppWidget(ids, build(context));
    }

    private static PendingIntent launchIntent(Context c, AppEntry e, int req) {
        Intent i = new Intent(c, LaunchReceiver.class);
        i.setData(Uri.parse("dock://launch/" + e.key()));
        i.putExtra("id", e.id);
        return PendingIntent.getBroadcast(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static RemoteViews build(Context c) {
        try {
            return buildInner(c);
        } catch (Throwable t) {
            try {
                StackTraceElement[] st = t.getStackTrace();
                String where = st.length > 0 ? st[0].toString() : "";
                Prefs.sp(c).edit().putString("lasterr", t.toString() + " @ " + where).apply();
            } catch (Throwable ignored) {
            }
            return new RemoteViews(c.getPackageName(), R.layout.dock_widget);
        }
    }

    private static RemoteViews buildInner(Context c) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.dock_widget);

        // wygląd: styl + tryb dzienny/nocny
        boolean night = Look.night(c);
        boolean glass = "glass".equals(Prefs.style(c));
        int fg = night ? 0xFFFFFFFF : 0xFF111111;
        int dim = (fg & 0x00FFFFFF) | 0x66000000;
        int dimmer = (fg & 0x00FFFFFF) | 0x40000000;
        rv.setInt(R.id.root, "setBackgroundResource", Look.bg(glass, night));
        rv.setTextColor(R.id.clock, fg);
        rv.setTextColor(R.id.volt, fg);
        rv.setImageViewBitmap(R.id.battery, Icons.vector(c, R.drawable.ic_battery, 96, fg));
        rv.setImageViewBitmap(R.id.grid, Icons.vector(c, R.drawable.ic_grid, 128, fg));

        // statusy z jednego, wspólnego odczytu
        // Wi-Fi / hotspot / Bluetooth rysowane kodem (Glyphs) - tak samo jak w doku
        // i w panelu łączności, bez plików XML i bez filtra PorterDuff.SRC_IN
        StatusCenter.Snap s = StatusCenter.get(c);
        rv.setImageViewBitmap(R.id.wifi, Icons.wifi(c, s.wifi, fg));
        rv.setImageViewBitmap(R.id.tether, Glyphs.bitmap("hot", 96, s.tether ? fg : dimmer));
        rv.setImageViewBitmap(R.id.bt, Glyphs.bitmap("bt", 96, s.bt ? fg : dimmer));
        if (Prefs.cpShow(c)) {
            rv.setViewVisibility(R.id.cpstat, View.VISIBLE);
            if (s.cp == CarplayState.PHONE) {
                rv.setImageViewBitmap(R.id.cpstat, Icons.reconnectingRing(96, 0xFFFF9F0A));
            } else {
                rv.setImageViewBitmap(R.id.cpstat, Icons.carplayMark(96, CarplayState.color(s.cp, fg)));
            }
        } else {
            rv.setViewVisibility(R.id.cpstat, View.GONE);
        }

        // 3 górne aplikacje (wg ostatniego / najczęstszego użycia)
        List<AppEntry> top = Prefs.top(c, 3);
        for (int i = 0; i < TOP_IDS.length; i++) {
            if (i < top.size()) {
                AppEntry e = top.get(i);
                rv.setViewVisibility(TOP_IDS[i], View.VISIBLE);
                rv.setImageViewBitmap(TOP_IDS[i], Icons.forEntry(c, e));
                rv.setOnClickPendingIntent(TOP_IDS[i], launchIntent(c, e, i));
            } else {
                // INVISIBLE, żeby CarPlay został na swoim miejscu
                rv.setViewVisibility(TOP_IDS[i], View.INVISIBLE);
            }
        }

        // CarPlay: 4. ikona
        AppEntry cp = Prefs.carplay(c);
        rv.setImageViewBitmap(R.id.carplay, Icons.forEntry(c, cp));
        rv.setOnClickPendingIntent(R.id.carplay, launchIntent(c, cp, 10));

        // siatka = lista aplikacji
        PendingIntent grid;
        if ("system".equals(Prefs.gridMode(c))) {
            Intent all = new Intent(Intent.ACTION_ALL_APPS);
            if (all.resolveActivity(c.getPackageManager()) == null) {
                all = new Intent(Intent.ACTION_MAIN);
                all.addCategory(Intent.CATEGORY_HOME);
            }
            all.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            grid = PendingIntent.getActivity(c, 30, all, PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            Intent apps = new Intent(c, AppsActivity.class);
            apps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            grid = PendingIntent.getActivity(c, 30, apps, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        rv.setOnClickPendingIntent(R.id.grid, grid);

        // pojedyncze dotknięcie zegara/statusów = panel łączności
        Intent hdr = new Intent(c, HeaderReceiver.class);
        hdr.setData(Uri.parse("dock://header"));
        rv.setOnClickPendingIntent(R.id.header, PendingIntent.getBroadcast(
                c, 20, hdr, PendingIntent.FLAG_UPDATE_CURRENT));

        // napięcie (opcjonalnie)
        if (Prefs.showVoltage(c)) {
            rv.setViewVisibility(R.id.power_row, View.VISIBLE);
            if (s.charging) {
                // silnik pracuje: ikona z zielonym piorunem zamiast liczby
                rv.setImageViewBitmap(R.id.battery, Icons.batteryCharging(c, 96, fg));
                rv.setViewVisibility(R.id.volt, View.GONE);
            } else {
                rv.setViewVisibility(R.id.volt, View.VISIBLE);
                rv.setTextViewText(R.id.volt, s.volt.length() > 0 ? s.volt : "-- V");
            }
        } else {
            rv.setViewVisibility(R.id.power_row, View.GONE);
        }
        // bateria iPhone'a (gdy CarPlay połączony i telefon ją raportuje)
        if (s.phone >= 0 && s.cp == CarplayState.CONNECTED) {
            rv.setViewVisibility(R.id.phone_row, View.VISIBLE);
            rv.setImageViewBitmap(R.id.phone_bat, Icons.phoneBattery(s.phone, fg));
            rv.setTextViewText(R.id.phone_pct, s.phone + "%");
            rv.setTextColor(R.id.phone_pct, fg);
        } else {
            rv.setViewVisibility(R.id.phone_row, View.GONE);
        }
        return rv;
    }
}







