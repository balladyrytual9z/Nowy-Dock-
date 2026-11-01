
package pl.dock.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.widget.TimePicker;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// Ustawienia widÅ¼etu w stylu CarPlay
public class ConfigActivity extends BaseActivity {

    private static final int REQ_FILE = 1;
    private static final int REQ_BACKUP_EXPORT = 40;
    private static final int REQ_BACKUP_IMPORT = 41;
    private AppEntry fileEntry;
    private Look.Pal p;
    private boolean night;

    private interface OnPick {
        void on(String pkg);
    }

    private interface OnSel {
        void on(int index);
    }

    private static class AppItem {
        String label;
        String pkg;
    }

    @Override
    protected void onCreate(Bundle b) {
        night = Look.night(this);
        setTheme(night ? R.style.Dock_Night : R.style.Dock_Day);
        super.onCreate(b);
        // Allow system navigation bar / home gesture so user can pull the system bar
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } catch (Throwable ignored) {}
        Ui.dockWindow(getWindow(), this);
        // Ensure the activity window does not swallow system gestures
        try {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            // Keep NOT_TOUCH_MODAL so touches outside (dock area) can pass, but allow system UI
        } catch (Throwable ignored) {}
        p = Look.pal(night);
        StatusService.start(this);
        build();
    }

    // ---------- pomocnicze ----------
    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private LinearLayout group(LinearLayout parent) {
        LinearLayout g = new LinearLayout(this);
        g.setOrientation(LinearLayout.VERTICAL);
        g.setBackground(round(p.card, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        parent.addView(g, lp);
        return g;
    }

    private void section(LinearLayout parent, String title) {
        TextView t = text(title.toUpperCase(), 13, p.sub, true);
        t.setPadding(dp(8), dp(22), 0, dp(8));
        t.setTag("sec|" + title);
        parent.addView(t);
    }

    private void footer(LinearLayout parent, String s) {
        TextView t = text(s, 13, p.sub, false);
        t.setPadding(dp(8), dp(2), dp(8), 0);
        parent.addView(t);
    }

    private void sep(LinearLayout g) {
        View v = new View(this);
        v.setBackgroundColor(p.sep);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2));
        lp.leftMargin = dp(16);
        g.addView(v, lp);
    }

    private View row(Bitmap icon, String title, String value, boolean chevron,
                     int titleColor, View.OnClickListener click) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(68));
        r.setPadding(dp(16), dp(8), dp(16), dp(8));
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(icon);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(48), dp(48));
            lp.rightMargin = dp(14);
            r.addView(iv, lp);
        }
        TextView t = text(title, 19, titleColor, false);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (value != null && value.length() > 0) {
            r.addView(text(value, 17, p.sub, false));
        }
        if (chevron) {
            TextView ch = text("  âº", 26, p.sub, false);
            r.addView(ch);
        }
        if (click != null) r.setOnClickListener(click);
        return r;
    }

    private View switchRow(String title, boolean checked, final CompoundButton.OnCheckedChangeListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(68));
        r.setPadding(dp(16), dp(8), dp(16), dp(8));
        r.addView(text(title, 19, p.text, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        r.addView(sw);
        return r;
    }

    private View segRow(String title, String[] labels, int sel, final OnSel cb) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(dp(16), dp(14), dp(16), dp(14));
        v.addView(text(title, 19, p.text, false));
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(round(p.bg, 13));
        seg.setPadding(dp(3), dp(3), dp(3), dp(3));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(10);
        v.addView(seg, sp);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView tv = text(labels[i], 17, i == sel ? Color.WHITE : p.text, i == sel);
            tv.setGravity(Gravity.CENTER);
            if (i == sel) tv.setBackground(round(p.accent, 10));
            seg.addView(tv, new LinearLayout.LayoutParams(0, dp(46), 1f));
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    cb.on(idx);
                }
            });
        }
        return v;
    }

    // ---------- ekran ----------
    private void build() {
        ScrollView sv = new ScrollView(this);
        sv.setBackground(Ui.pageBg(this, night));
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        outer.setPadding(0, dp(20), 0, dp(40));
        sv.addView(outer, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int w = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(760));
        outer.addView(col, new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("Ustawienia", 34, p.text, true);
        title.setPadding(dp(8), dp(4), 0, 0);
        col.addView(title);

        // --- WyglÄd ---
        section(col, "WyglÄd");
        LinearLayout g = group(col);
        g.addView(segRow("Styl", new String[]{"Klasyczny", "Liquid Glass"},
                "glass".equals(Prefs.style(this)) ? 1 : 0, new OnSel() {
                    @Override
                    public void on(int i) {
                        Prefs.setStyle(ConfigActivity.this, i == 1 ? "glass" : "classic");
                        themeChanged();
                    }
                }));
        sep(g);
        String fr = Prefs.frame(this);
        g.addView(segRow("Ramka ikon", new String[]{"Brak", "PierÅcieÅ", "CarPlay"},
                "none".equals(fr) ? 0 : ("ring".equals(fr) ? 1 : 2), new OnSel() {
                    @Override
                    public void on(int i) {
                        Prefs.setFrame(ConfigActivity.this,
                                i == 0 ? "none" : (i == 1 ? "ring" : "carplay"));
                        changed();
                    }
                }));
        sep(g);
        g.addView(switchRow("PodÄÅ¼aj za ustawieniami systemu", Prefs.followSystem(this),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton v, boolean on) {
                        if (on == Prefs.followSystem(ConfigActivity.this)) return;
                        Prefs.setFollowSystem(ConfigActivity.this, on);
                        themeChanged();
                    }
                }));
        if (!Prefs.followSystem(this)) {
            sep(g);
            String mode = Prefs.mode(this);
            g.addView(segRow("Tryb", new String[]{"Dzienny", "Nocny", "Wg godzin"},
                    "day".equals(mode) ? 0 : ("night".equals(mode) ? 1 : 2), new OnSel() {
                        @Override
                        public void on(int i) {
                            Prefs.setMode(ConfigActivity.this,
                                    i == 0 ? "day" : (i == 1 ? "night" : "time"));
                            themeChanged();
                        }
                    }));
            if ("time".equals(mode)) {
                sep(g);
                g.addView(row(null, "Noc zaczyna siÄ o", hhmm(Prefs.nightStart(this)), true,
                        p.text, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                pickTime(true);
                            }
                        }));
                sep(g);
                g.addView(row(null, "Noc koÅczy siÄ o", hhmm(Prefs.nightEnd(this)), true,
                        p.text, new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                pickTime(false);
                            }
                        }));
            }
        }

        // --- GÃ³rne aplikacje ---
        section(col, "GÃ³rne aplikacje");
        g = group(col);
        g.addView(segRow("KtÃ³re pokazywaÄ", new String[]{"Ostatnio uÅ¼ywane", "NajczÄÅciej uÅ¼ywane"},
                "count".equals(Prefs.sortMode(this)) ? 1 : 0, new OnSel() {
                    @Override
                    public void on(int i) {
                        Prefs.setSortMode(ConfigActivity.this, i == 1 ? "count" : "last");
                        changed();
                    }
                }));
        sep(g);
        g.addView(row(null, "Wyzeruj statystyki uÅ¼ycia", "", false, p.accent,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.resetStats(ConfigActivity.this);
                        Toast.makeText(ConfigActivity.this, "Wyzerowano", Toast.LENGTH_SHORT).show();
                        changed();
                    }
                }));
        footer(col, "Na widÅ¼ecie widaÄ 3 aplikacje z listy poniÅ¼ej (wg powyÅ¼szego wyboru) "
                + "oraz CarPlay na dole. Zliczane sÄ klikniÄcia na widÅ¼ecie.");

        // --- CarPlay ---
        section(col, "Ikona CarPlay w docku");
        g = group(col);
        final AppEntry cp = Prefs.carplay(this);
        g.addView(row(Icons.forEntry(this, cp), "Aplikacja",
                cp.pkg.length() == 0 ? "Nie wybrano" : appLabel(cp.pkg), true, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickApp(new OnPick() {
                            @Override
                            public void on(String pkg) {
                                cp.pkg = pkg;
                                Prefs.persist(ConfigActivity.this, cp);
                                changed();
                            }
                        });
                    }
                }));
        sep(g);
        g.addView(row(null, "Ikona", iconModeName(cp), true, p.text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iconMenu(cp);
            }
        }));

        // --- Lista aplikacji ---
        List<AppEntry> apps = Prefs.apps(this);
        int visible = 0;
        for (AppEntry e : apps) {
            if (Prefs.isAppSelected(this, e.pkg)) visible++;
        }
        section(col, "Aplikacje (" + visible + " z " + apps.size() + " widocznych)");
        g = group(col);
        g.addView(row(null, "Wybierz aplikacje widoczne w siatce",
                "", true, p.accent, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(ConfigActivity.this, AppsPickerActivity.class));
                    }
                }));
        sep(g);
        for (final AppEntry e : apps) {
            String val = "";
            g.addView(row(Icons.forEntry(this, e), appLabel(e.pkg), val, true, p.text,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            entryMenu(e);
                        }
                    }));
            sep(g);
        }
        g.addView(row(null, "Dodaj aplikacjÄ", "", false, p.accent, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickApp(new OnPick() {
                    @Override
                    public void on(final String pkg) {
                        final AppEntry ne = Prefs.add(ConfigActivity.this, pkg);
                        changed();
                        Popups.menu(ConfigActivity.this, "Ikona: " + appLabel(pkg),
                                new String[]{"Jak w CarPlay (ramka)",
                                        "DokÅadnie jak oryginalna ikona aplikacji"},
                                new Popups.OnSel() {
                                    @Override
                                    public void on(int i) {
                                        if (i == 1) {
                                            ne.orig = true;
                                            Prefs.persist(ConfigActivity.this, ne);
                                        }
                                        changed();
                                    }
                                });
                    }
                });
            }
        }));
        sep(g);
        g.addView(row(null, "Dobierz ikony automatycznie (wg nazw)", "", false, p.accent,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        autoIcons();
                    }
                }));
        sep(g);
        g.addView(row(null, "PrzywrÃ³Ä domyÅlne (DSP, Bluetooth, OBD2, pliki, DiPlay)", "", false,
                p.accent, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Popups.confirm(ConfigActivity.this, "PrzywrÃ³ciÄ domyÅlne aplikacje?",
                                "Lista aplikacji i aplikacja CarPlay wrÃ³cÄ do ustawieÅ domyÅlnych.",
                                "PrzywrÃ³Ä", new Runnable() {
                                    @Override
                                    public void run() {
                                        Prefs.resetToDefaults(ConfigActivity.this);
                                        changed();
                                    }
                                });
                    }
                }));
        footer(col, "Dodaj ile chcesz. KaÅ¼da moÅ¼e mieÄ wÅasnÄ ikonÄ.");

        // --- Przycisk siatki ---
        // --- Launcher ---
        section(col, "Launcher");
        g = group(col);
        g.addView(segRow("Pozycja docka", new String[]{"Lewa strona", "Prawa strona"},
                Ui.dockLeft(this) ? 0 : 1, new OnSel() {
                    @Override
                    public void on(int i) {
                        Prefs.setDockSide(ConfigActivity.this, i == 0 ? "left" : "right");
                        themeChanged();
                    }
                }));
        sep(g);
        g.addView(toggle("Paralaksa tÅa przy przewijaniu", Prefs.parallax(this), new BoolSetter() {
            @Override
            public void set(boolean v) {
                Prefs.setParallax(ConfigActivity.this, v);
            }
        }));
        sep(g);
        g.addView(toggle("Ogranicz animacje", Prefs.reduceMotion(this), new BoolSetter() {
            @Override
            public void set(boolean v) {
                Prefs.setReduceMotion(ConfigActivity.this, v);
            }
        }));
        sep(g);
        g.addView(toggle("Animacja bounce przy wybudzeniu (iOS)", Prefs.wakeBounce(this),
                new BoolSetter() {
                    @Override
                    public void set(boolean v) {
                        Prefs.setWakeBounce(ConfigActivity.this, v);
                    }
                }));
        sep(g);
        g.addView(toggle("Gest od doÅu â centrum ÅÄcznoÅci", Prefs.overlayEnabled(this),
                new BoolSetter() {
                    @Override
                    public void set(boolean v) {
                        Prefs.setOverlayEnabled(ConfigActivity.this, v);
                    }
                }));
        sep(g);
        g.addView(toggle("Panel nad miejscem gestu", Prefs.overlayFollowGesture(this),
                new BoolSetter() {
                    @Override
                    public void set(boolean v) {
                        Prefs.setOverlayFollowGesture(ConfigActivity.this, v);
                    }
                }));
        sep(g);
        g.addView(toggle("Ukryj paski systemowe", Prefs.hideBars(this), new BoolSetter() {
            @Override
            public void set(boolean v) {
                Prefs.setHideBars(ConfigActivity.this, v);
            }
        }));
        sep(g);
        g.addView(toggle("Strona z widÅ¼etami (przesuniÄcie w lewo)", Prefs.widgetsPage(this),
                new BoolSetter() {
                    @Override
                    public void set(boolean v) {
                        Prefs.setWidgetsPage(ConfigActivity.this, v);
                    }
                }));
        sep(g);
        g.addView(row(null, "Ustaw Dock jako domyÅlny launcher", "", true, p.accent,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        try {
                            startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
                        } catch (Throwable t) {
                            Toast.makeText(ConfigActivity.this,
                                    "Wybierz launcher w ustawieniach Androida (Aplikacje domyÅlne)",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }));
        footer(col, "WidÅ¼ety systemowe dodajesz dÅugim dotkniÄciem ekranu gÅÃ³wnego "
                + "albo kafelka âDodaj widÅ¼etâ na stronie widÅ¼etÃ³w.");

        // --- ÅÄcznoÅÄ ---
        section(col, "ÅÄcznoÅÄ");
        g = group(col);
        String ba = Prefs.btAction(this);
        g.addView(segRow("KlikniÄcie Bluetooth w panelu",
                new String[]{"PrzeÅÄcz / aplikacja", "Aplikacja", "PrzeÅÄcz"},
                "auto".equals(ba) ? 0 : ("app".equals(ba) ? 1 : 2), new OnSel() {
                    @Override
                    public void on(int i) {
                        Prefs.setBtAction(ConfigActivity.this,
                                i == 0 ? "auto" : (i == 1 ? "app" : "toggle"));
                        changed();
                    }
                }));
        sep(g);
        g.addView(row(null, "Aplikacja Bluetooth radia", Prefs.btPkg(this), true, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ask("Pakiet aplikacji Bluetooth", new String[]{"com.autochips.bluetooth"},
                                new String[]{Prefs.btPkg(ConfigActivity.this)}, new OnText() {
                                    @Override
                                    public void on(String[] val) {
                                        if (val[0].length() > 0) {
                                            Prefs.setBtPkg(ConfigActivity.this, val[0]);
                                        }
                                        changed();
                                    }
                                });
                    }
                }));
        footer(col, "âPrzeÅÄcz / aplikacjaâ: Dock prÃ³buje przeÅÄczyÄ Bluetooth, a gdy radio "
                + "tego nie przyjmie, otwiera jego aplikacjÄ.");

        // --- Status CarPlay (DiPlay) ---
        section(col, "Status CarPlay");
        g = group(col);
        g.addView(toggle("PokaÅ¼ ikonÄ statusu CarPlay na doku", Prefs.cpShow(this), new BoolSetter() {
            @Override
            public void set(boolean v) {
                Prefs.setCpShow(ConfigActivity.this, v);
            }
        }));
        sep(g);
        g.addView(toggle("SygnaÅ: sesja multimedialna DiPlay", Prefs.cpUseSession(this),
                new BoolSetter() {
                    @Override
                    public void set(boolean v) {
                        Prefs.setCpUseSession(ConfigActivity.this, v);
                    }
                }));
        sep(g);
        g.addView(toggle("SygnaÅ: powiadomienie DiPlay", Prefs.cpUseNotif(this), new BoolSetter() {
            @Override
            public void set(boolean v) {
                Prefs.setCpUseNotif(ConfigActivity.this, v);
            }
        }));
        sep(g);
        g.addView(toggle("SygnaÅ: iPhone na USB", Prefs.cpUseUsb(this), new BoolSetter() {
            @Override
            public void set(boolean v) {
                Prefs.setCpUseUsb(ConfigActivity.this, v);
            }
        }));
        sep(g);
        g.addView(row(null, "Pakiet aplikacji CarPlay", Prefs.diplayPkg(this), true, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ask("Pakiet aplikacji CarPlay (DiPlay)", new String[]{"com.shihab.diplay"},
                                new String[]{Prefs.diplayPkg(ConfigActivity.this)}, new OnText() {
                                    @Override
                                    public void on(String[] val) {
                                        if (val[0].length() > 0) {
                                            Prefs.setDiplayPkg(ConfigActivity.this, val[0]);
                                        }
                                        changed();
                                    }
                                });
                    }
                }));
        sep(g);
        g.addView(row(null, "Broadcast ze stanem (opcjonalnie)", Prefs.cpAction(this), true, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ask("Broadcast ze stanem CarPlay",
                                new String[]{"Akcja", "Nazwa extra (tekst: connected / connecting)"},
                                new String[]{Prefs.cpAction(ConfigActivity.this),
                                        Prefs.cpExtra(ConfigActivity.this)}, new OnText() {
                                    @Override
                                    public void on(String[] val) {
                                        Prefs.setCpBcast(ConfigActivity.this, val[0], val[1]);
                                        StatusService.restart(ConfigActivity.this);
                                        changed();
                                    }
                                });
                    }
                }));
        footer(col, "DiPlay nie ma oficjalnego API, wiÄc stan jest wnioskowany z kilku sygnaÅÃ³w. "
                + "Zielona ikona = sesja aktywna, pomaraÅczowa = iPhone wykryty / ÅÄczenie.");

        // --- Odtwarzanie ---
        section(col, "Odtwarzanie muzyki");
        g = group(col);
        g.addView(row(null, "DostÄp do powiadomieÅ",
                MediaHub.accessGranted(this) ? "wÅÄczony" : "WYÅÄCZONY", true,
                MediaHub.accessGranted(this) ? p.text : p.accent, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        NowPlayingCard.openAccessSettings(ConfigActivity.this);
                    }
                }));
        sep(g);
        g.addView(row(null, "Å¹rÃ³dÅo odtwarzania (pakiet)",
                Prefs.mediaPkg(this).length() == 0 ? "DiPlay" : Prefs.mediaPkg(this), true, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ask("Pakiet ÅºrÃ³dÅa odtwarzania (puste = DiPlay)", new String[]{"pakiet"},
                                new String[]{Prefs.mediaPkg(ConfigActivity.this)}, new OnText() {
                                    @Override
                                    public void on(String[] val) {
                                        Prefs.setMediaPkg(ConfigActivity.this, val[0]);
                                        changed();
                                    }
                                });
                    }
                }));
        sep(g);
        g.addView(row(null, "Diagnostyka odtwarzania i CarPlay", "", true, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mediaDiag();
                    }
                }));
        footer(col, "Dock czyta sesjÄ multimedialnÄ DiPlay (tytuÅ, wykonawca, okÅadka). "
                + "Wymaga jednorazowego wÅÄczenia dostÄpu do powiadomieÅ.");

        section(col, "Przycisk aplikacji");
        g = group(col);
        g.addView(segRow("Otwiera listÄ aplikacji", new String[]{"Dock", "Launchera systemu"},
                "system".equals(Prefs.gridMode(this)) ? 1 : 0, new OnSel() {
                    @Override
                    public void on(int i) {
                        Prefs.setGridMode(ConfigActivity.this, i == 1 ? "system" : "dock");
                        changed();
                    }
                }));
        footer(col, "âDockâ to wÅasna lista wszystkich aplikacji. JeÅli launcher tabletu ma "
                + "swojÄ listÄ, sprÃ³buj drugiej opcji.");

        // --- Pasek stanu ---
        section(col, "Pasek stanu");
        g = group(col);
        g.addView(switchRow("PokaÅ¼ napiÄcie tabletu", Prefs.showVoltage(this),
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton v, boolean on) {
                        if (on == Prefs.showVoltage(ConfigActivity.this)) return;
                        Prefs.setShowVoltage(ConfigActivity.this, on);
                        DockWidget.updateAll(ConfigActivity.this);
                    }
                }));
        sep(g);
        g.addView(row(null, "Å¹rÃ³dÅo napiÄcia", "", true, p.text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                voltSourceDialog();
            }
        }));
        sep(g);
        g.addView(row(null, "Diagnostyka ÅÄcznoÅci i napiÄcia", "", true, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        diagDialog();
                    }
                }));
        sep(g);
        g.addView(row(null, "Diagnostyka ÅºrÃ³dÅa napiÄcia (12 V)", "", true, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(ConfigActivity.this, VoltDiagActivity.class));
                    }
                }));
        footer(col, "Å¹rÃ³dÅo: " + Volt.sourceLabel(this) + ". Diagnostyka pokazuje odczyty "
                + "ze wszystkich ÅºrÃ³deÅ naraz â zrÃ³b zrzut ekranu.");

        extraSections(col);
        finishLayout(col, title, sv);
        setContentView(sv);
    }

    // ---------- hierarchia: kategorie gÅÃ³wne (styl iOS) -> podsekcje ----------
    private String openSection;
    private String searchQuery = "";

    /** GÅÃ³wne kategorie ustawieÅ (kolejnoÅÄ jak w iOS: ÅÄcznoÅÄ â ekran â wyglÄd â aplikacje â ogÃ³lne). */
    private static final String[] TOP_CATEGORIES = {
            "PoÅÄczenia",
            "Ekran gÅÃ³wny i Launcher",
            "WyglÄd i dÅºwiÄk",
            "Aplikacje",
            "OgÃ³lne"
    };

    /** Mapuje nazwÄ podsekcji (tag sec|) na kategoriÄ gÅÃ³wnÄ. */
    private String categoryForSection(String secName) {
        if (secName == null) return "OgÃ³lne";
        String s = secName.toLowerCase();
        // Aplikacje najpierw (Å¼eby âIkona CarPlayâ¦â nie wpadÅa do PoÅÄczeÅ)
        if (s.startsWith("aplikacje")
                || s.contains("ikona carplay")
                || s.contains("wtyczki")
                || s.contains("plugin")
                || s.contains("obd")
                || s.contains("dashboard")) {
            return "Aplikacje";
        }
        // PoÅÄczenia
        if (s.contains("ÅÄcznoÅÄ") || s.contains("lacznosc")
                || s.equals("telefon")
                || s.contains("status carplay")
                || s.contains("carplay (diplay)")
                || (s.contains("carplay") && !s.contains("ikona"))) {
            return "PoÅÄczenia";
        }
        // Ekran gÅÃ³wny i Launcher
        if (s.contains("gÃ³rne aplikacje") || s.contains("gorne aplikacje")
                || s.equals("launcher")
                || s.contains("przycisk aplikacji")
                || s.contains("przycisk home")
                || s.contains("pozycja dock")
                || s.contains("gesty")) {
            return "Ekran gÅÃ³wny i Launcher";
        }
        // WyglÄd i dÅºwiÄk
        if (s.equals("wyglÄd") || s.equals("wyglad")
                || s.contains("skala interfejsu")
                || s.equals("tapeta")
                || s.contains("odtwarzanie muzyki")
                || s.contains("pasek stanu")) {
            return "WyglÄd i dÅºwiÄk";
        }
        // Reszta â OgÃ³lne (Circadian, Kopia, SamochÃ³d i system, diagnostyka itd.)
        return "OgÃ³lne";
    }

    private Bitmap categoryIcon(String cat) {
        // Proste kolorowe ikony kategorii (okrÄg + symbol)
        int size = dp(40);
        Bitmap bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(bm);
        android.graphics.Paint fill = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        android.graphics.Paint stroke = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(android.graphics.Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(2, size / 18f));
        stroke.setColor(0xFFFFFFFF);
        float cx = size / 2f, cy = size / 2f, r = size / 2f - 1;
        int bg;
        if ("PoÅÄczenia".equals(cat)) {
            bg = 0xFF007AFF; // niebieski jak Wi-Fi/BT
            fill.setColor(bg);
            c.drawCircle(cx, cy, r, fill);
            // symbol sygnaÅu / anteny
            c.drawCircle(cx, cy + r * 0.25f, r * 0.18f, stroke);
            stroke.setStrokeWidth(Math.max(1.5f, size / 22f));
            c.drawArc(cx - r * 0.45f, cy - r * 0.15f, cx + r * 0.45f, cy + r * 0.55f, 200, 140, false, stroke);
            c.drawArc(cx - r * 0.7f, cy - r * 0.35f, cx + r * 0.7f, cy + r * 0.65f, 200, 140, false, stroke);
        } else if ("Ekran gÅÃ³wny i Launcher".equals(cat)) {
            bg = 0xFF34C759; // zielony
            fill.setColor(bg);
            c.drawCircle(cx, cy, r, fill);
            // dom
            android.graphics.Path house = new android.graphics.Path();
            house.moveTo(cx, cy - r * 0.45f);
            house.lineTo(cx + r * 0.4f, cy - r * 0.05f);
            house.lineTo(cx + r * 0.4f, cy + r * 0.4f);
            house.lineTo(cx - r * 0.4f, cy + r * 0.4f);
            house.lineTo(cx - r * 0.4f, cy - r * 0.05f);
            house.close();
            fill.setColor(0xFFFFFFFF);
            c.drawPath(house, fill);
        } else if ("WyglÄd i dÅºwiÄk".equals(cat)) {
            bg = 0xFFAF52DE; // fiolet
            fill.setColor(bg);
            c.drawCircle(cx, cy, r, fill);
            // sÅoÅce / jasnoÅÄ
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, r * 0.28f, fill);
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(Math.max(2, size / 20f));
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4;
                float x1 = (float) (cx + Math.cos(a) * r * 0.42f);
                float y1 = (float) (cy + Math.sin(a) * r * 0.42f);
                float x2 = (float) (cx + Math.cos(a) * r * 0.62f);
                float y2 = (float) (cy + Math.sin(a) * r * 0.62f);
                c.drawLine(x1, y1, x2, y2, stroke);
            }
        } else if ("Aplikacje".equals(cat)) {
            bg = 0xFFFF9500; // pomaraÅcz
            fill.setColor(bg);
            c.drawCircle(cx, cy, r, fill);
            // siatka 2x2
            fill.setColor(0xFFFFFFFF);
            float g = r * 0.22f, off = r * 0.32f;
            c.drawRoundRect(cx - off - g, cy - off - g, cx - off + g, cy - off + g, 3, 3, fill);
            c.drawRoundRect(cx + off - g, cy - off - g, cx + off + g, cy - off + g, 3, 3, fill);
            c.drawRoundRect(cx - off - g, cy + off - g, cx - off + g, cy + off + g, 3, 3, fill);
            c.drawRoundRect(cx + off - g, cy + off - g, cx + off + g, cy + off + g, 3, 3, fill);
        } else { // OgÃ³lne
            bg = 0xFF8E8E93; // szary
            fill.setColor(bg);
            c.drawCircle(cx, cy, r, fill);
            // zÄbatka uproszczona
            fill.setColor(0xFFFFFFFF);
            c.drawCircle(cx, cy, r * 0.22f, fill);
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(Math.max(2.5f, size / 16f));
            for (int i = 0; i < 6; i++) {
                double a = i * Math.PI / 3;
                float x1 = (float) (cx + Math.cos(a) * r * 0.28f);
                float y1 = (float) (cy + Math.sin(a) * r * 0.28f);
                float x2 = (float) (cx + Math.cos(a) * r * 0.55f);
                float y2 = (float) (cy + Math.sin(a) * r * 0.55f);
                c.drawLine(x1, y1, x2, y2, stroke);
            }
        }
        return bm;
    }

    private void finishLayout(LinearLayout col, TextView title, ScrollView sv) {
        List<String> names = new ArrayList<String>();
        java.util.Map<String, List<View>> map = new java.util.LinkedHashMap<String, List<View>>();
        String cur = null;
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            if (v == title) continue;
            Object tag = v.getTag();
            if (tag instanceof String && ((String) tag).startsWith("sec|")) {
                cur = ((String) tag).substring(4);
                names.add(cur);
                map.put(cur, new ArrayList<View>());
            } else if (cur != null) {
                map.get(cur).add(v);
            }
        }
        col.removeAllViews();

        // Grupowanie podsekcji w kategorie gÅÃ³wne
        java.util.Map<String, List<String>> catToSecs = new java.util.LinkedHashMap<String, List<String>>();
        for (String cat : TOP_CATEGORIES) {
            catToSecs.put(cat, new ArrayList<String>());
        }
        for (String n : names) {
            String cat = categoryForSection(n);
            if (!catToSecs.containsKey(cat)) catToSecs.put(cat, new ArrayList<String>());
            catToSecs.get(cat).add(n);
        }

        boolean isTopLevel = (openSection == null);
        boolean isCategory = false;
        for (String c : TOP_CATEGORIES) {
            if (c.equals(openSection)) {
                isCategory = true;
                break;
            }
        }
        // kompatybilnoÅÄ: jeÅli openSection to stara nazwa podsekcji, otwÃ³rz jej kategoriÄ
        if (!isTopLevel && !isCategory && map.containsKey(openSection)) {
            openSection = categoryForSection(openSection);
            isCategory = true;
        }
        if (!isTopLevel && !isCategory) {
            openSection = null;
            isTopLevel = true;
        }

        if (isTopLevel) {
            col.addView(title);
            TextView hint = text("Ustawienia pogrupowane jak w iOS â wybierz kategoriÄ.", 14, p.sub, false);
            hint.setPadding(dp(8), dp(2), dp(8), dp(8));
            col.addView(hint);

            // Pasek wyszukiwania (filtruje kategorie i podsekcje)
            final EditText search = new EditText(this);
            search.setHint("Szukaj ustawieÅâ¦");
            search.setTextSize(17);
            search.setTextColor(p.text);
            search.setHintTextColor(p.sub);
            search.setBackground(round(p.card, 14));
            search.setPadding(dp(16), dp(12), dp(16), dp(12));
            search.setSingleLine(true);
            search.setText(searchQuery);
            LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            searchLp.bottomMargin = dp(14);
            searchLp.leftMargin = dp(4);
            searchLp.rightMargin = dp(4);
            col.addView(search, searchLp);

            final LinearLayout listHost = new LinearLayout(this);
            listHost.setOrientation(LinearLayout.VERTICAL);
            col.addView(listHost);

            final Runnable refreshList = new Runnable() {
                @Override
                public void run() {
                    listHost.removeAllViews();
                    String q = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
                    LinearLayout g = group(listHost);
                    boolean first = true;
                    for (String cat : TOP_CATEGORIES) {
                        List<String> secs = catToSecs.get(cat);
                        if (secs == null || secs.isEmpty()) continue;
                        // filtr: kategoria lub dowolna podsekcja pasuje do zapytania
                        boolean match = q.length() == 0 || cat.toLowerCase().contains(q);
                        if (!match) {
                            for (String s : secs) {
                                if (s.toLowerCase().contains(q)) {
                                    match = true;
                                    break;
                                }
                            }
                        }
                        if (!match) continue;
                        if (!first) sep(g);
                        first = false;
                        final String catName = cat;
                        String value = secs.size() + (secs.size() == 1 ? " sekcja" : " sekcje");
                        g.addView(row(categoryIcon(catName), catName, value, true, p.text,
                                new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        openSection = catName;
                                        searchQuery = "";
                                        build();
                                        slideIn();
                                    }
                                }));
                    }
                    if (first) {
                        TextView empty = text("Brak wynikÃ³w dla \"" + searchQuery + "\"", 16, p.sub, false);
                        empty.setPadding(dp(16), dp(20), dp(16), dp(20));
                        empty.setGravity(Gravity.CENTER);
                        listHost.addView(empty);
                    }
                }
            };
            refreshList.run();

            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    searchQuery = s != null ? s.toString() : "";
                    refreshList.run();
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        } else {
            // Ekran kategorii: wstecz + tytuÅ + podsekcje naleÅ¼Äce do kategorii
            LinearLayout bar = new LinearLayout(this);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            TextView back = text("â¹", 30, p.text, true);
            back.setGravity(Gravity.CENTER);
            back.setBackground(round(p.card, 22));
            back.setContentDescription("Wstecz");
            back.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onBackPressed();
                }
            });
            bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView t = text(openSection, 28, p.text, true);
            t.setPadding(dp(14), 0, 0, 0);
            bar.addView(t);
            col.addView(bar);

            List<String> secs = catToSecs.get(openSection);
            if (secs != null) {
                for (String secName : secs) {
                    // nagÅÃ³wek podsekcji
                    TextView secTitle = text(secName.toUpperCase(), 13, p.sub, true);
                    secTitle.setPadding(dp(8), dp(18), 0, dp(6));
                    col.addView(secTitle);
                    List<View> views = map.get(secName);
                    if (views != null) {
                        for (View v : views) {
                            col.addView(v);
                        }
                    }
                }
            }
        }
    }

    private void slideIn() {
        View c = findViewById(android.R.id.content);
        if (c == null) return;
        c.setTranslationX(dp(40));
        c.setAlpha(0f);
        c.animate().translationX(0).alpha(1f).setDuration(Prefs.reduceMotion(this) ? 0 : 240).start();
    }

    @Override
    public void onBackPressed() {
        if (openSection != null) {
            openSection = null;
            build();
            slideIn();
        } else {
            super.onBackPressed();
            overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_slide_out);
        }
    }

    // ---------- nowe wpisy ----------
    private void extraSections(LinearLayout col) {
        LinearLayout g;

        section(col, "Przycisk Home");
        g = group(col);
        String ha = Prefs.homeAction(this);
        g.addView(segRow("Po naciÅniÄciu Home na ekranie gÅÃ³wnym",
                new String[]{"OtwÃ³rz CarPlay", "OtwÃ³rz aplikacjÄ", "Nic"},
                "carplay".equals(ha) ? 0 : ("app".equals(ha) ? 1 : 2), new OnSel() {
                    @Override
                    public void on(int i) {
                        Prefs.setHomeAction(ConfigActivity.this,
                                i == 0 ? "carplay" : (i == 1 ? "app" : "none"));
                        changed();
                    }
                }));
        if ("app".equals(ha)) {
            sep(g);
            String cur = Prefs.homeAppPkg(this);
            g.addView(row(null, "Aplikacja", cur.length() == 0 ? "Nie wybrano" : appLabel(cur), true,
                    p.text, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pickApp(new OnPick() {
                                @Override
                                public void on(String pkg) {
                                    Prefs.setHomeAppPkg(ConfigActivity.this, pkg);
                                    changed();
                                }
                            });
                        }
                    }));
        }
        footer(col, "Z innej aplikacji Home zawsze wraca na ekran gÅÃ³wny. Opcja dziaÅa tylko wtedy, "
                + "gdy jesteÅ juÅ¼ na ekranie gÅÃ³wnym. Wybierz âNicâ, jeÅli launcher nie ma uruchamiaÄ "
                + "Å¼adnej aplikacji.");

        section(col, "Skala interfejsu");
        g = group(col);
        float us = Prefs.uiScale(this);
        g.addView(segRow("Rozmiar elementÃ³w", new String[]{"MaÅy", "Standardowy", "DuÅ¼y"},
                us < 0.95f ? 0 : (us > 1.05f ? 2 : 1), new OnSel() {
                    @Override
                    public void on(int i) {
                        Prefs.setUiScale(ConfigActivity.this, i == 0 ? 0.9f : (i == 2 ? 1.12f : 1f));
                        recreate();
                    }
                }));
        footer(col, "Nie zaleÅ¼y od ustawienia âRozmiar wyÅwietlaniaâ w Androidzie.");

        section(col, "Tapeta");
        g = group(col);
        String cw = Prefs.wallpaper(this);
        for (int i = 0; i < Wallpapers.ALL.length; i++) {
            final Wallpapers.Style st = Wallpapers.ALL[i];
            if (i > 0) sep(g);
            g.addView(row(null, st.name, st.id.equals(cw) ? "â" : "", false, p.text,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Prefs.setWallpaper(ConfigActivity.this, st.id);
                            changed();
                        }
                    }));
        }
        sep(g);
        g.addView(row(null, "Tapeta systemowa", "system".equals(cw) ? "â" : "", false, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Prefs.setWallpaper(ConfigActivity.this, "system");
                        changed();
                    }
                }));
        footer(col, "KaÅ¼da tapeta ma wersjÄ jasnÄ i ciemnÄ; przeÅÄczajÄ siÄ razem z trybem wyglÄdu.");

        section(col, "Circadian Display");
        g = group(col);
        if (pl.dock.widget.circadian.CircadianBridge.isInstalled(this)) {
            g.addView(toggle("Automatycznie aktywuj profil Circadian przy trybie nocnym",
                    Prefs.circadianAuto(this), new BoolSetter() {
                @Override
                public void set(boolean v) {
                    Prefs.setCircadianAuto(ConfigActivity.this, v);
                    if (v) {
                        pl.dock.widget.circadian.CircadianBridge.setNightMode(
                                ConfigActivity.this, Look.night(ConfigActivity.this));
                    }
                }
            }));
            sep(g);
            g.addView(row(null, "OtwÃ³rz Circadian Display", "", true, p.accent, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    android.content.Intent i = pl.dock.widget.circadian.CircadianBridge.openApp(ConfigActivity.this);
                    if (i != null) startActivity(i);
                }
            }));
        } else {
            g.addView(row(null, "Zainstaluj Circadian Display", "Brak", true, p.accent, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://f-droid.org/packages/com.circadiandisplay.app/")));
                    } catch (Throwable ignored) {}
                }
            }));
        }
        footer(col, "Circadian Display ustawia ciepÅo i przygaszenie ekranu wg krzywej. "
                + "Dock aktywuje wybrany profil przy przejÅciu w tryb nocny.");

        section(col, "Kopia zapasowa");
        g = group(col);
        g.addView(row(null, "Eksportuj ustawienia do pliku", "", true, p.accent, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "dock-backup-" + System.currentTimeMillis() + ".json");
                startActivityForResult(i, REQ_BACKUP_EXPORT);
            }
        }));
        sep(g);
        g.addView(row(null, "Importuj ustawienia z pliku", "", true, p.accent, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                startActivityForResult(i, REQ_BACKUP_IMPORT);
            }
        }));
        footer(col, "Kopia obejmuje listÄ aplikacji, widÅ¼ety, tapetÄ, wyglÄd i wszystkie ustawienia Docka. "
                + "Nie obejmuje peÅnych danych Circadian Display â te majÄ wÅasny eksport.");

        section(col, "Telefon");
        g = group(col);
        String bn = Prefs.btDeviceName(this);
        g.addView(row(null, "Telefon do Åledzenia", bn.length() == 0 ? "Nie wybrano" : bn, true, p.text,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickPhone();
                    }
                }));
        footer(col, "Ikona Bluetooth w docku Åwieci tylko wtedy, gdy poÅÄczony jest ten telefon.");

        section(col, "CarPlay (DiPlay)");
        g = group(col);
        String ca = Prefs.cpActivity(this);
        g.addView(row(null, "Okno CarPlay", ca.length() == 0 ? "Automatycznie" : shortName(ca), true,
                p.text, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pickCpActivity();
                    }
                }));
        sep(g);
        g.addView(row(null, "OtwÃ³rz CarPlay teraz", "", true, p.accent, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Launch.carplay(ConfigActivity.this);
            }
        }));
        footer(col, "ZmieÅ okno tylko wtedy, gdy przycisk CarPlay otwiera ekran ustawieÅ DiPlay "
                + "zamiast obrazu z telefonu.");

        section(col, "SamochÃ³d i system");
        g = group(col);
        g.addView(row(null, "Ustawienia samochodu", "", true, p.text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPkg("com.hcn.autosetting");
            }
        }));
        sep(g);
        g.addView(row(null, "Ustawienia Androida", "", true, p.text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Throwable ignored) {
                }
            }
        }));
        sep(g);
        g.addView(row(null, "Wi-Fi", "", true, p.text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Net.openWifiSettings(ConfigActivity.this);
            }
        }));
        sep(g);
        g.addView(row(null, "Hotspot", "", true, p.text, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Net.openTetherSettings(ConfigActivity.this);
            }
        }));

        // ustawienia udostÄpniane przez inne aplikacje (np. OBD2 Dashboard)
        List<PluginSettings.Plugin> plugins = PluginSettings.discover(this);
        for (final PluginSettings.Plugin pl : plugins) {
            section(col, pl.label);
            g = group(col);
            PluginSettings.render(this, pl, g, p, new Runnable() {
                @Override
                public void run() {
                    changed();
                }
            });
        }
    }

    private String shortName(String cls) {
        int i = cls.lastIndexOf('.');
        return i >= 0 ? cls.substring(i + 1) : cls;
    }

    private void openPkg(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(this, "Aplikacja nie jest zainstalowana", Toast.LENGTH_SHORT).show();
            return;
        }
        Prefs.recordPkg(this, pkg);
        startActivity(i);
    }

    private void pickPhone() {
        final List<android.bluetooth.BluetoothDevice> list = BtDevice.bonded();
        if (list.isEmpty()) {
            Toast.makeText(this, "Najpierw sparuj telefon w ustawieniach Bluetooth", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[list.size() + 1];
        for (int i = 0; i < list.size(); i++) {
            String n = list.get(i).getName();
            names[i] = n == null ? list.get(i).getAddress() : n;
        }
        names[list.size()] = "Å»aden (dowolne urzÄdzenie)";
        Popups.menu(this, "Telefon do Åledzenia", names, new Popups.OnSel() {
            @Override
            public void on(int i) {
                if (i >= list.size()) {
                    Prefs.setBtDevice(ConfigActivity.this, "", "");
                } else {
                    String n = list.get(i).getName();
                    Prefs.setBtDevice(ConfigActivity.this, list.get(i).getAddress(),
                            n == null ? list.get(i).getAddress() : n);
                }
                StatusCenter.refresh(ConfigActivity.this);
                changed();
            }
        });
    }

    private void pickCpActivity() {
        final List<String[]> acts = Launch.activities(this);
        String[] labels = new String[acts.size() + 1];
        labels[0] = "Automatycznie";
        for (int i = 0; i < acts.size(); i++) {
            labels[i + 1] = shortName(acts.get(i)[0]) + " (" + acts.get(i)[1] + ")";
        }
        Popups.menu(this, "Okno CarPlay", labels, new Popups.OnSel() {
            @Override
            public void on(int i) {
                Prefs.setCpActivity(ConfigActivity.this, i == 0 ? "" : acts.get(i - 1)[0]);
                changed();
            }
        });
    }

    private void changed() {
        DockWidget.updateAll(this);
        build();
    }

    private void themeChanged() {
        DockWidget.updateAll(this);
        recreate();
    }

    private String iconModeName(AppEntry e) {
        if ("file".equals(e.mode)) return "Z pliku";
        if ("app".equals(e.mode)) return "Innej aplikacji";
        return "DomyÅlna";
    }

    private String appLabel(String pkg) {
        if (pkg == null || pkg.length() == 0) return "â";
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg + " (brak)";
        }
    }

    // ---------- menu wpisu ----------
    private void entryMenu(final AppEntry e) {
        String[] items = {"ZmieÅ aplikacjÄ", "Ikona",
                "Ikona dokÅadnie jak oryginalna: " + (e.orig ? "TAK" : "nie"),
                "PrzesuÅ wyÅ¼ej", "PrzesuÅ niÅ¼ej", "UsuÅ z listy"};
        Popups.menu(this, appLabel(e.pkg), items, new Popups.OnSel() {
            @Override
            public void on(int which) {
                if (which == 0) {
                    pickApp(new OnPick() {
                        @Override
                        public void on(String pkg) {
                            e.pkg = pkg;
                            Prefs.persist(ConfigActivity.this, e);
                            changed();
                        }
                    });
                } else if (which == 1) {
                    iconMenu(e);
                } else if (which == 2) {
                    e.orig = !e.orig;
                    Prefs.persist(ConfigActivity.this, e);
                    changed();
                } else if (which == 3) {
                    Prefs.move(ConfigActivity.this, e.id, -1);
                    changed();
                } else if (which == 4) {
                    Prefs.move(ConfigActivity.this, e.id, 1);
                    changed();
                } else {
                    Popups.confirm(ConfigActivity.this, "UsunÄÄ z listy?", appLabel(e.pkg),
                            "UsuÅ", new Runnable() {
                                @Override
                                public void run() {
                                    Prefs.remove(ConfigActivity.this, e.id);
                                    changed();
                                }
                            });
                }
            }
        });
    }

    private void iconMenu(final AppEntry e) {
        String[] items = {"Z pliku (obraz z tabletu)", "Ikona innej aplikacji",
                "Wbudowane ikony (styl iOS / CarPlay)", "DomyÅlna ikona aplikacji"};
        new AlertDialog.Builder(this)
                .setTitle("Ikona")
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                            i.setType("image/*");
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            if (i.resolveActivity(getPackageManager()) == null) {
                                Toast.makeText(ConfigActivity.this,
                                        "Tablet nie ma wyboru plikÃ³w â uÅ¼yj ikony innej aplikacji",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            fileEntry = e;
                            startActivityForResult(i, REQ_FILE);
                        } else if (which == 1) {
                            pickApp(new OnPick() {
                                @Override
                                public void on(String pkg) {
                                    e.mode = "app";
                                    e.iconPkg = pkg;
                                    Prefs.persist(ConfigActivity.this, e);
                                    changed();
                                }
                            });
                        } else if (which == 2) {
                            pickBuiltin(new OnPick() {
                                @Override
                                public void on(String id) {
                                    e.mode = "builtin";
                                    e.iconPkg = id;
                                    Prefs.persist(ConfigActivity.this, e);
                                    changed();
                                }
                            });
                        } else {
                            e.mode = "default";
                            e.iconPkg = "";
                            Prefs.persist(ConfigActivity.this, e);
                            changed();
                        }
                    }
                }).show();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_BACKUP_EXPORT && res == RESULT_OK && data != null && data.getData() != null) {
            boolean ok = pl.dock.widget.backup.BackupManager.writeTo(this, data.getData());
            android.widget.Toast.makeText(this, ok ? "Zapisano" : "BÅÄd zapisu",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (req == REQ_BACKUP_IMPORT && res == RESULT_OK && data != null && data.getData() != null) {
            boolean ok = pl.dock.widget.backup.BackupManager.importFrom(this, data.getData());
            android.widget.Toast.makeText(this, ok ? "Zaimportowano â zrestartuj aplikacjÄ" : "BÅÄd importu",
                    android.widget.Toast.LENGTH_LONG).show();
            if (ok) recreate();
            return;
        }
        if (req == REQ_FILE && res == RESULT_OK && data != null && data.getData() != null
                && fileEntry != null) {
            try {
                Uri u = data.getData();
                boolean ok = Icons.saveFromStream(this, fileEntry,
                        getContentResolver().openInputStream(u),
                        getContentResolver().openInputStream(u));
                if (ok) {
                    fileEntry.mode = "file";
                    Prefs.persist(this, fileEntry);
                    changed();
                } else {
                    Toast.makeText(this, "Nie udaÅo siÄ wczytaÄ obrazu", Toast.LENGTH_LONG).show();
                }
            } catch (Exception ex) {
                Toast.makeText(this, "Nie udaÅo siÄ wczytaÄ obrazu", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- wybÃ³r aplikacji ----------
    private void pickApp(final OnPick cb) {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        final List<AppItem> items = new ArrayList<AppItem>();
        for (ResolveInfo ri : ris) {
            AppItem a = new AppItem();
            a.label = ri.loadLabel(pm).toString();
            a.pkg = ri.activityInfo.packageName;
            items.add(a);
        }
        Collections.sort(items, new Comparator<AppItem>() {
            @Override
            public int compare(AppItem x, AppItem y) {
                return x.label.compareToIgnoreCase(y.label);
            }
        });

        ListView lv = new ListView(this);
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Wybierz aplikacjÄ")
                .setView(lv)
                .setNegativeButton("Anuluj", null)
                .create();

        lv.setAdapter(new ArrayAdapter<AppItem>(this, 0, items) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                Context c = getContext();
                LinearLayout r = new LinearLayout(c);
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(dp(12), dp(8), dp(12), dp(8));
                AppItem a = getItem(pos);
                ImageView iv = new ImageView(c);
                iv.setImageBitmap(Icons.forPackage(c, a.pkg));
                r.addView(iv, new LinearLayout.LayoutParams(dp(48), dp(48)));
                TextView t = text(a.label, 20, p.text, false);
                t.setPadding(dp(14), 0, 0, 0);
                r.addView(t);
                return r;
            }
        });
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                dlg.dismiss();
                cb.on(items.get(pos).pkg);
            }
        });
        dlg.show();
    }

    // ---------- ÅºrÃ³dÅo napiÄcia ----------
    private interface OnText {
        void on(String[] values);
    }

    private void ask(String title, String[] hints, String[] initial, final OnText cb) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText[] ets = new EditText[hints.length];
        for (int i = 0; i < hints.length; i++) {
            EditText e = new EditText(this);
            e.setHint(hints[i]);
            e.setText(initial[i]);
            e.setTextColor(p.text);
            e.setHintTextColor(p.sub);
            e.setSingleLine(true);
            l.addView(e);
            ets[i] = e;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(l)
                .setNegativeButton("Anuluj", null)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String[] v = new String[ets.length];
                        for (int i = 0; i < ets.length; i++) {
                            v[i] = ets[i].getText().toString().trim();
                        }
                        cb.on(v);
                    }
                }).show();
    }

    private void sourceChanged() {
        StatusService.restart(this);
        changed();
    }

    private void voltSourceDialog() {
        final List<String> paths = new ArrayList<String>();
        List<String> labels = new ArrayList<String>();
        labels.add("Bateria Androida (domyÅlnie)");
        paths.add("");
        for (String f : Volt.discoverFiles()) {
            labels.add(f + "  â  " + Volt.readRaw(f));
            paths.add(f);
        }
        final int n = paths.size();
        labels.add("WÅasny plikâ¦");
        labels.add("WÅasny broadcastâ¦");
        labels.add("Klucz ustawieÅ Androidaâ¦");
        labels.add("Z aplikacji OBD2 Dashboard (broadcast Docka)");
        new AlertDialog.Builder(this)
                .setTitle("Å¹rÃ³dÅo napiÄcia")
                .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == 0) {
                            Prefs.setVoltSource(ConfigActivity.this, "battery");
                            sourceChanged();
                        } else if (which < n) {
                            Prefs.setVoltSource(ConfigActivity.this, "file");
                            Prefs.setVoltPath(ConfigActivity.this, paths.get(which));
                            sourceChanged();
                        } else if (which == n) {
                            ask("WÅasny plik", new String[]{"/sys/â¦/voltage_now"},
                                    new String[]{Prefs.voltPath(ConfigActivity.this)},
                                    new OnText() {
                                        @Override
                                        public void on(String[] v) {
                                            Prefs.setVoltSource(ConfigActivity.this, "file");
                                            Prefs.setVoltPath(ConfigActivity.this, v[0]);
                                            sourceChanged();
                                        }
                                    });
                        } else if (which == n + 1) {
                            ask("WÅasny broadcast",
                                    new String[]{"Akcja (np. com.x.VOLTAGE)", "Nazwa extra z liczbÄ"},
                                    new String[]{Prefs.bcastAction(ConfigActivity.this),
                                            Prefs.bcastExtra(ConfigActivity.this)},
                                    new OnText() {
                                        @Override
                                        public void on(String[] v) {
                                            Prefs.setBcast(ConfigActivity.this, v[0], v[1]);
                                            Prefs.setVoltSource(ConfigActivity.this, "bcast");
                                            sourceChanged();
                                        }
                                    });
                        } else if (which == n + 2) {
                            ask("Klucz ustawieÅ Androida",
                                    new String[]{"np. system:nazwa, global:nazwa, secure:nazwa"},
                                    new String[]{Prefs.settingKey(ConfigActivity.this)},
                                    new OnText() {
                                        @Override
                                        public void on(String[] v) {
                                            Prefs.setSettingKey(ConfigActivity.this, v[0]);
                                            Prefs.setVoltSource(ConfigActivity.this, "setting");
                                            sourceChanged();
                                        }
                                    });
                        } else {
                            // umowa z poprawkÄ aplikacji OBD2 Dashboard (patrz obd-bridge/README)
                            Prefs.setBcast(ConfigActivity.this, "pl.dock.widget.OBD_VOLTAGE", "voltage");
                            Prefs.setVoltSource(ConfigActivity.this, "bcast");
                            sourceChanged();
                        }
                    }
                }).show();
    }

    private void diagDialog() {
        TextView t = new TextView(this);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(13);
        t.setTextColor(p.text);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        t.setText(Diag.report(this));
        ScrollView s = new ScrollView(this);
        s.addView(t);
        new AlertDialog.Builder(this)
                .setTitle("Diagnostyka")
                .setView(s)
                .setPositiveButton("Zamknij", null)
                .show();
    }

    private String hhmm(int m) {
        return String.format(java.util.Locale.US, "%02d:%02d", m / 60, m % 60);
    }

    private void pickTime(final boolean start) {
        int cur = start ? Prefs.nightStart(this) : Prefs.nightEnd(this);
        new TimePickerDialog(this, new TimePickerDialog.OnTimeSetListener() {
            @Override
            public void onTimeSet(TimePicker view, int h, int m) {
                if (start) {
                    Prefs.setNightStart(ConfigActivity.this, h * 60 + m);
                } else {
                    Prefs.setNightEnd(ConfigActivity.this, h * 60 + m);
                }
                themeChanged();
            }
        }, cur / 60, cur % 60, true).show();
    }

    // ---------- wbudowane ikony ----------
    private void pickBuiltin(final OnPick cb) {
        final BuiltinIcons.Def[] defs = BuiltinIcons.ALL;
        GridView gv = new GridView(this);
        gv.setNumColumns(4);
        gv.setVerticalSpacing(dp(12));
        gv.setPadding(dp(12), dp(8), dp(12), dp(8));
        final AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("Wbudowane ikony")
                .setView(gv)
                .setNegativeButton("Anuluj", null)
                .create();
        gv.setAdapter(new ArrayAdapter<BuiltinIcons.Def>(this, 0, defs) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                Context c = getContext();
                BuiltinIcons.Def d = getItem(pos);
                LinearLayout cell = new LinearLayout(c);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                ImageView iv = new ImageView(c);
                iv.setImageBitmap(Icons.builtin(c, d.id));
                cell.addView(iv, new LinearLayout.LayoutParams(dp(60), dp(60)));
                TextView t = text(d.label, 12, p.text, false);
                t.setGravity(Gravity.CENTER);
                t.setMaxLines(2);
                cell.addView(t);
                return cell;
            }
        });
        gv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int pos, long id) {
                dlg.dismiss();
                cb.on(defs[pos].id);
            }
        });
        dlg.show();
    }

    // dobiera wbudowane ikony do aplikacji z domyÅlnÄ ikonÄ, wg nazwy
    private void autoIcons() {
        int count = 0;
        int obd = 0;
        List<AppEntry> list = Prefs.apps(this);
        for (AppEntry e : list) {
            if (!"default".equals(e.mode)) continue;
            String id = BuiltinIcons.suggest(appLabel(e.pkg));
            if (id == null) continue;
            if ("obd".equals(id)) {
                obd++;
                id = "obd" + Math.min(obd, 3);
            }
            e.mode = "builtin";
            e.iconPkg = id;
            count++;
        }
        Prefs.saveApps(this, list);
        AppEntry cp = Prefs.carplay(this);
        if ("default".equals(cp.mode) && cp.pkg.length() > 0
                && "carplay".equals(BuiltinIcons.suggest(appLabel(cp.pkg)))) {
            cp.mode = "builtin";
            cp.iconPkg = "carplay";
            Prefs.saveCarplay(this, cp);
            count++;
        }
        Toast.makeText(this, "Dobrano ikon: " + count, Toast.LENGTH_SHORT).show();
        changed();
    }

    // ---------- pomocnicze (launcher) ----------
    private interface BoolSetter {
        void set(boolean v);
    }

    private View toggle(String title, boolean cur, final BoolSetter setter) {
        return switchRow(title, cur, new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean on) {
                setter.set(on);
                DockWidget.updateAll(ConfigActivity.this);
            }
        });
    }

    private void mediaDiag() {
        TextView t = new TextView(this);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(13);
        t.setTextColor(p.text);
        t.setPadding(dp(18), dp(14), dp(18), dp(14));
        t.setText(MediaHub.describe(this));
        Popups.panel(this, t, 600, Gravity.CENTER).show();
    }
}







