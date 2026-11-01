
package pl.dock.widget;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

// Ustawienia udostÄpniane przez inne aplikacje (np. OBD2 Dashboard, mapy, DiPlay).
// Aplikacja deklaruje ContentProvider z akcjÄ pl.dock.widget.SETTINGS_PROVIDER i opisuje
// swoje ustawienia w JSON; Dock rysuje je wÅasnym interfejsem. SzczegÃ³Åy: docs/PLUGIN_SETTINGS.md
public class PluginSettings {

    public static final String ACTION = "pl.dock.widget.SETTINGS_PROVIDER";

    public static class Plugin {
        public String label = "";
        public String authority = "";
    }

    public static List<Plugin> discover(Context c) {
        List<Plugin> out = new ArrayList<Plugin>();
        try {
            PackageManager pm = c.getPackageManager();
            List<ResolveInfo> l = pm.queryIntentContentProviders(new Intent(ACTION), 0);
            if (l == null) return out;
            for (ResolveInfo ri : l) {
                ProviderInfo pi = ri.providerInfo;
                if (pi == null || pi.authority == null) continue;
                Plugin p = new Plugin();
                p.authority = pi.authority.split(";")[0];
                p.label = pi.applicationInfo.loadLabel(pm).toString();
                out.add(p);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Bundle call(Context c, Plugin p, String method, String arg, Bundle extras) {
        try {
            return c.getContentResolver().call(Uri.parse("content://" + p.authority), method, arg,
                    extras);
        } catch (Throwable t) {
            return null;
        }
    }

    // rysuje wiersze do karty g; po zmianie wartoÅci wywoÅuje changed
    public static void render(final Activity a, final Plugin pl, LinearLayout g, final Look.Pal pal,
                              final Runnable changed) {
        Bundle res = call(a, pl, "schema", null, null);
        String json = res == null ? null : res.getString("json");
        if (json == null) {
            g.addView(label(a, pal, "Aplikacja nie odpowiada", "", false));
            return;
        }
        try {
            JSONArray rows = new JSONObject(json).getJSONArray("rows");
            for (int i = 0; i < rows.length(); i++) {
                final JSONObject r = rows.getJSONObject(i);
                final String type = r.optString("type");
                final String key = r.optString("key");
                String title = r.optString("label");
                if (i > 0) {
                    View sep = new View(a);
                    sep.setBackgroundColor(pal.sep);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    lp.leftMargin = Ui.dp(a, 16);
                    g.addView(sep, lp);
                }
                if ("toggle".equals(type)) {
                    LinearLayout row = base(a);
                    TextView t = tv(a, title, 17, pal.text, false);
                    row.addView(t, new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                    Switch sw = new Switch(a);
                    sw.setChecked(r.optBoolean("value"));
                    sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton v, boolean on) {
                            Bundle e = new Bundle();
                            e.putBoolean("value", on);
                            call(a, pl, "set", key, e);
                        }
                    });
                    row.addView(sw);
                    g.addView(row);
                } else if ("select".equals(type)) {
                    final JSONArray opts = r.optJSONArray("options");
                    g.addView(clickable(a, pal, title, r.optString("value"), new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (opts == null) return;
                            String[] items = new String[opts.length()];
                            for (int k = 0; k < items.length; k++) items[k] = opts.optString(k);
                            final String[] fi = items;
                            Popups.menu(a, r.optString("label"), items, new Popups.OnSel() {
                                @Override
                                public void on(int idx) {
                                    Bundle e = new Bundle();
                                    e.putString("value", fi[idx]);
                                    call(a, pl, "set", key, e);
                                    changed.run();
                                }
                            });
                        }
                    }));
                } else if ("action".equals(type)) {
                    g.addView(clickable(a, pal, title, "", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            call(a, pl, "action", key, null);
                            changed.run();
                        }
                    }));
                } else {
                    g.addView(label(a, pal, title, r.optString("value"), false));
                }
            }
        } catch (Throwable t) {
            g.addView(label(a, pal, "NieprawidÅowy opis ustawieÅ", "", false));
        }
    }

    private static LinearLayout base(Activity a) {
        LinearLayout row = new LinearLayout(a);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(a, 56));
        row.setPadding(Ui.dp(a, 16), Ui.dp(a, 8), Ui.dp(a, 16), Ui.dp(a, 8));
        return row;
    }

    private static TextView tv(Activity a, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private static View label(Activity a, Look.Pal pal, String title, String value, boolean x) {
        LinearLayout row = base(a);
        row.addView(tv(a, title, 17, pal.text, false), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv(a, value, 17, pal.sub, false));
        return row;
    }

    private static View clickable(Activity a, Look.Pal pal, String title, String value,
                                  View.OnClickListener l) {
        View row = label(a, pal, title, value.length() > 0 ? value + "  âº" : "âº", true);
        row.setOnClickListener(l);
        return row;
    }
}







