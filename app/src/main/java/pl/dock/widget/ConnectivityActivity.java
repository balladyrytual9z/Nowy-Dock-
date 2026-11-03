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
// Wczesniej Ui.dockWindow zawezalo okno o 77 dp - pasek po boku pokazywal tlo
// systemu, a FLAG_NOT_TOUCH_MODAL blokowalo wysuwanie panelu powiadomien.
public class ConnectivityActivity extends BaseActivity {

    private ConnectivityPanel panel;

    @Override
    protected void onCreate(Bundle b) {
        boolean night = Look.night(this);
        setTheme(android.R.style.Theme_Translucent_NoTitleBar);
        super.onCreate(b);
        // pelne okno + zerowanie immersive z BaseActivity = pasek statusu wysuwalny
        try {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        } catch (Throwable ignored) {
        }
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x66000000);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        panel = new ConnectivityPanel(this, night, new ConnectivityPanel.Host() {
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
        int w = Math.min(Ui.dp(this, 560),
                getResources().getDisplayMetrics().widthPixels - dockPx - Ui.dp(this, 24));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | (dockLeft ? Gravity.END : Gravity.START));
        lp.setMargins(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8));
        root.addView(panel, lp);

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
                    Intent i = getPackageManager()
                            .getLaunchIntentForPackage(top.get(index).pkg);
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

        setContentView(root);
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
}
