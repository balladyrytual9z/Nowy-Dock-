package pl.dock.widget;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import java.util.List;

// Okno zapasowe centrum lacznosci (np. po kliknieciu paska widzetu na innym launcherze).
// NAPRAWA (ESSGOO/MT8163, Android 9): pelnoekranowe okno z dokiem rysowanym wewnatrz.
// PORT: panel w stylu iOS (ConnectivityPanelIos) 1:1 z Liquid-Glass.
// NAPRAWA UCIĘCIA: zawartosc panelu (400+10+70+70+24 = 574 dp) byla szersza niz
// okno 560 dp - suwak jasności wystawal poza tlo. Panel ma teraz szerokosc
// WRAP_CONTENT, dzieki czemu tlo obejmuje cala zawartosc.
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
        root.setBackgroundColor(0x66000000);
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
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | (dockLeft ? Gravity.END : Gravity.START));
        lp.setMargins(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8));
        root.addView(panel, lp);

        // Gdy ekran jest nizszy niz panel (inne dpi niz tablet ESSGOO), skaluj
        // panel JEDNOLICIE w dol - proporcje i wyglad 1:1 bez znieksztalcen.
        // Gdy panel sie miesci (tablet), ten kod nic nie robi.
        panel.post(new Runnable() {
            @Override
            public void run() {
                int availH = getResources().getDisplayMetrics().heightPixels
                        - Ui.dp(ConnectivityActivity.this, 12)
                        - Ui.dp(ConnectivityActivity.this, 8);
                int h = panel.getHeight();
                if (h > 0 && h > availH) {
                    float sc = (float) availH / h;
                    panel.setPivotY(0f);
                    panel.setPivotX(Ui.dockLeft(ConnectivityActivity.this)
                            ? panel.getWidth() : 0f);
                    panel.setScaleX(sc);
                    panel.setScaleY(sc);
                }
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
