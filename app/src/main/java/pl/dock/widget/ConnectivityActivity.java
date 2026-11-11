package pl.dock.widget;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.List;

// Okno zapasowe centrum lacznosci (np. po kliknieciu paska widzetu na innym launcherze
// albo z Ustawien). PORT: panel w stylu iOS (ConnectivityPanelIos) 1:1 z Liquid-Glass.
// NAPRAWA UCIĘCIA: zawartosc panelu (400+10+70+70+24 = 574 dp) byla szersza niz
// okno 560 dp - suwak jasności wystawal poza tlo. Panel ma teraz szerokosc
// WRAP_CONTENT, dzieki czemu tlo obejmuje cala zawartosc.
// UJEDNOLICENIE Z LAUNCHEREM (jeden panel wszedzie, ten sam wyglad):
// 1. BLUR W TLE: migawka tapety 1/4 rozmiaru ekranu + rozmycie (Blur.blur) +
//    delikatna zaslona - dokladnie ta sama sciezka co w overlayu LauncherActivity,
//    zamiast wczesniejszej plaskiej polprzezroczystej czerni.
// 2. ANIMACJA OTWARCIA: panel rosnie od punktu przy doku (gorny naroznik od
//    strony doka) z OvershootInterpolator + fade calego okna; na koncu wymuszony
//    re-layout + invalidate (mechanizm naprawczy "pustego panelu" z PR #14).
//    Zamkniecie pozostaje natychmiastowe (okno aktywnosci).
// 3. PASEK SYSTEMOWY: cala zawartosc (blur + panel) zaczyna sie POD paskiem
//    statusu - gorna krawedz panelu nie chowa sie juz pod androidowy pasek.
public class ConnectivityActivity extends BaseActivity {

    private ConnectivityPanelIos panel;

    @Override
    protected void onCreate(Bundle b) {
        boolean night = Look.night(this);
        setTheme(android.R.style.Theme_Translucent_NoTitleBar);
        super.onCreate(b);
                // Pasek statusu domyslnie UKRYTY, wysuwalny gestem od gory (IMMERSIVE_STICKY).
        try {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        } catch (Throwable ignored) {}
        applyImmersive();
        FrameLayout root = new FrameLayout(this);
        // BLUR W TLE (jak w launcherze): rozmyta tapeta + zaslona zamiast czerni
        try {
            int sw = getResources().getDisplayMetrics().widthPixels;
            int sh = getResources().getDisplayMetrics().heightPixels;
            Bitmap snap = Bitmap.createBitmap(Math.max(1, sw / 4), Math.max(1, sh / 4),
                    Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(snap);
            cv.scale(1f / 4, 1f / 4);
            Wallpapers.paint(cv, sw, sh, Wallpapers.byId(Prefs.style(this)), night);
            Blur.blur(snap, 3, 3);
            ImageView glass = new ImageView(this);
            glass.setScaleType(ImageView.ScaleType.FIT_XY);
            glass.setImageBitmap(snap);
            root.addView(glass, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } catch (Throwable ignored) {
            // awaryjnie: stara polprzezroczysta czerń
            root.setBackgroundColor(0x66000000);
        }
        View tint = new View(this);
        tint.setBackgroundColor(night ? 0x59000000 : 0x4DFFFFFF);
        root.addView(tint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        panel = new ConnectivityPanelIos(this, night, new ConnectivityPanelIos.Host() {
            @Override
            public void close() {
                finish();
            }

            @Override
            public void modeChanged() {
                DockWidget.updateAll(ConnectivityActivity.this);
            }
        });
        boolean dockLeft = Ui.dockLeft(this);
        int dockPx = Ui.dp(this, Ui.DOCK_OFFSET_DP);
        // szerokosc dopasowana do zawartosci panelu (naprawa uciętego suwaka jasności)
        // panel przy doku (po jego wewnętrznej stronie) - nie zasłania doka;
        // gora = margines + PASEK SYSTEMOWY (panel pod paskiem statusu)
        int topInset = Ui.dp(this, 12) + Ui.statusBarHeight(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | (dockLeft ? Gravity.START : Gravity.END));
        lp.setMargins(dockLeft ? dockPx + Ui.dp(this, 8) : Ui.dp(this, 8), topInset,
                dockLeft ? Ui.dp(this, 8) : dockPx + Ui.dp(this, 8), Ui.dp(this, 8));
        root.addView(panel, lp);

        // Gdy ekran jest nizszy niz panel (inne dpi niz tablet ESSGOO), skaluj
        // panel JEDNOLICIE w dol - proporcje i wyglad 1:1 bez znieksztalcen.
        // Gdy panel sie miesci (tablet), ten kod nic nie robi.
        // + ANIMACJA OTWARCIA jak w launcherze (patrz naglowek klasy).
        panel.post(new Runnable() {
            @Override
            public void run() {
                int availH = getResources().getDisplayMetrics().heightPixels
                        - Ui.dp(ConnectivityActivity.this, 12)
                        - Ui.statusBarHeight(ConnectivityActivity.this)
                        - Ui.dp(ConnectivityActivity.this, 8);
                int h = panel.getHeight();
                int w = panel.getWidth();
                boolean dockLeft2 = Ui.dockLeft(ConnectivityActivity.this);
                int availW = getResources().getDisplayMetrics().widthPixels
                        - Ui.dp(ConnectivityActivity.this, 8)
                        - (dockLeft2 ? Ui.dp(ConnectivityActivity.this, 8)
                                : Ui.dp(ConnectivityActivity.this, Ui.DOCK_OFFSET_DP + 8));
                float sc = 1f;
                if (h > 0 && h > availH) sc = (float) availH / h;
                if (w > 0 && w > availW) sc = Math.min(sc, (float) availW / w);
                // punkt, od ktorego panel rosnie: gorny naroznik od strony doka
                panel.setPivotX(dockLeft2 ? 0f : panel.getWidth());
                panel.setPivotY(0f);
                // ANIMACJA OTWARCIA (jak w LauncherActivity): start 0.6 z
                // OvershootInterpolator, na koncu wymuszony re-layout +
                // invalidate (mechanizm naprawczy "pustego panelu", PR #14).
                panel.setScaleX(sc * 0.6f);
                panel.setScaleY(sc * 0.6f);
                panel.animate().scaleX(sc).scaleY(sc)
                        .setDuration(Prefs.reduceMotion(ConnectivityActivity.this) ? 0 : 300)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f))
                        .withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                panel.requestLayout();
                                panel.invalidate();
                            }
                        })
                        .start();
                // fade calego okna (blur + zaslona + panel) - jak overlay w launcherze
                root.setAlpha(0f);
                root.animate().alpha(1f)
                        .setDuration(Prefs.reduceMotion(ConnectivityActivity.this) ? 0 : 220)
                        .start();
            }
        });

        // dok po wlasciwej stronie - widoczny i klikalny wewnatrz tego okna
        DockView dock = new DockView(this);
        FrameLayout.LayoutParams dockLp = new FrameLayout.LayoutParams(
                dockPx, ViewGroup.LayoutParams.MATCH_PARENT,
                dockLeft ? Gravity.START : Gravity.END);
        root.addView(dock, dockLp);
        dock.setListener(new DockView.Listener() {
            @Override public void onHeader() {
                finish();   // panel lacznosci jest juz otwarty nad tym oknem
            }
            @Override public void onApp(int index) {
                List<AppEntry> top = Prefs.top(ConnectivityActivity.this, 3);
                if (index == 3) {
                    Launch.carplay(ConnectivityActivity.this);
                    finish();
                } else if (index < top.size()) {
                    String p = top.get(index).pkg;
                    if (Launch.openDockTool(ConnectivityActivity.this, p)) {
                        finish();
                        return;
                    }
                    Intent i = getPackageManager().getLaunchIntentForPackage(p);
                    if (i != null) {
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        finish();
                    }
                }
            }
            @Override public void onBottom() {
                finish();
            }
            @Override public void onBottomLong() {
            }
        });
        StatusCenter.Snap s = StatusCenter.get(this);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String clock = String.format(java.util.Locale.US, "%02d:%02d",
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
        dock.setData(night, "glass".equals(Prefs.style(this)), clock, s,
                Prefs.showVoltage(this), dockIcons());
        dock.setCarplayState(s.cp);
        // ikona dolnego przycisku = strona, z ktorej otwarto panel (origin page)
        dock.setBottomMode(DockView.bottomModeFor(originPage()));

        setContentView(root);
    }

    /** Strona launchera, z ktorej otwarto ten ekran (fallback: ostatnia strona). */
    private String originPage() {
        String p = getIntent() == null ? null : getIntent().getStringExtra("origin_page");
        return p != null ? p : Prefs.lastPage(this);
    }

    private android.graphics.Bitmap[] dockIcons() {
        List<AppEntry> top = Prefs.top(this, 3);
        android.graphics.Bitmap[] ic = new android.graphics.Bitmap[4];
        for (int i = 0; i < 3 && i < top.size(); i++) ic[i] = Icons.forEntry(this, top.get(i));
        ic[3] = Icons.forEntry(this, Prefs.carplay(this));
        return ic;
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        finish();
    }

    private void applyImmersive() {
        try {
            int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            if (!Look.night(this)) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(flags);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }
}
