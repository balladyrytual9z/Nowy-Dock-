
package pl.dock.widget;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

// Okno zapasowe centrum ÅÄcznoÅci (np. po klikniÄciu paska widÅ¼etu na innym launcherze)
public class ConnectivityActivity extends BaseActivity {

    private ConnectivityPanel panel;

    @Override
    protected void onCreate(Bundle b) {
        boolean night = Look.night(this);
        setTheme(android.R.style.Theme_Translucent_NoTitleBar);
        super.onCreate(b);
        Ui.dockWindow(getWindow(), this);
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
        int w = Math.min(Ui.dp(this, 560), Ui.windowWidth(this) - Ui.dp(this, 24));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | (Ui.dockLeft(this) ? Gravity.START : Gravity.END));
        lp.setMargins(Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 8));
        root.addView(panel, lp);
        setContentView(root);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        finish();
    }
}







