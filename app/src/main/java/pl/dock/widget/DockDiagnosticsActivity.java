
package pl.dock.widget;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import pl.dock.widget.ui.DockColors;

/**
 * Dock 2.0 diagnostics – FPS proxy, memory, media latency, provider status.
 */
public class DockDiagnosticsActivity extends Activity {

    private TextView body;
    private final Handler h = new Handler(Looper.getMainLooper());
    private long lastFrame;
    private int frames;
    private float fps;
    private final Runnable sample = new Runnable() {
        @Override
        public void run() {
            frames++;
            long now = SystemClock.elapsedRealtime();
            if (now - lastFrame >= 1000) {
                fps = frames * 1000f / (now - lastFrame);
                frames = 0;
                lastFrame = now;
                refresh();
            }
            h.postDelayed(this, 16);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sc = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(DockColors.NIGHT_BASE);
        body = new TextView(this);
        body.setTextColor(DockColors.NIGHT_TEXT);
        body.setTextSize(13);
        body.setTypeface(Typeface.MONOSPACE);
        body.setGravity(Gravity.START);
        root.addView(body);
        sc.addView(root);
        setContentView(sc);
        lastFrame = SystemClock.elapsedRealtime();
        refresh();
    }

    @Override
    protected void onStart() {
        super.onStart();
        h.post(sample);
    }

    @Override
    protected void onStop() {
        super.onStop();
        h.removeCallbacks(sample);
    }

    private void refresh() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024;
        long max = rt.maxMemory() / 1024;
        Debug.MemoryInfo mi = new Debug.MemoryInfo();
        Debug.getMemoryInfo(mi);

        MediaHub.Track t = MediaHub.get().current();
        StringBuilder sb = new StringBuilder();
        sb.append("Dock Diagnostics 2.0\n");
        sb.append("====================\n\n");
        sb.append(String.format("FPS (approx): %.1f\n", fps));
        sb.append(String.format("Java heap: %d / %d KB\n", used, max));
        sb.append(String.format("PSS total: %d KB\n", mi.getTotalPss()));
        sb.append(String.format("Native PSS: %d KB\n", mi.nativePss));
        sb.append("\n--- Media ---\n");
        sb.append("Access: ").append(MediaHub.get().hasAccess() ? "OK" : "NO LISTENER").append('\n');
        sb.append("Pkg: ").append(t.pkg).append('\n');
        sb.append("Title: ").append(t.title).append('\n');
        sb.append("Playing: ").append(t.playing()).append('\n');
        sb.append("Source: ").append(t.source).append('\n');
        sb.append("\n--- Device ---\n");
        sb.append("API: ").append(android.os.Build.VERSION.SDK_INT).append('\n');
        sb.append("Device: ").append(android.os.Build.MODEL).append('\n');
        sb.append("Android: ").append(android.os.Build.VERSION.RELEASE).append('\n');
        sb.append("\nProvider: pl.dock.widget.state\n");
        sb.append("Media contract: get_media_snapshot / media_*\n");
        body.setText(sb.toString());
    }
}






