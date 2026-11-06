package pl.dock.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Siatka dodatkowych narzędzi — PRZEBUDOWANA OD ZERA (2026-11-06).
 *
 * Na ESSGOO / MT8163 / Mali-T720 / API 28 w dynamicznie doczepianym poddrzewie
 * (nakładka launchera) NIE rysują się widoki z własnym onDraw(). Dlatego każdy
 * kafelek to wyłącznie widoki frameworka: FrameLayout + View z tłem
 * GradientDrawable (koło) + ImageView z bitmapą ikony + TextView z etykietą +
 * TextView „−" (tryb edycji). Żaden widok nie ma własnego onDraw().
 */
public class ToolGrid extends LinearLayout {

    public interface Host {
        void onTool(PanelTool.Def d, boolean longPress);
        void onChanged(List<String> ids);
    }

    private static final Map<String, Bitmap> ICON_CACHE = new HashMap<String, Bitmap>();

    private final Context c;
    private final boolean night;
    private final Host host;
    private final List<String> ids = new ArrayList<String>();
    private final List<View> tiles = new ArrayList<View>();
    private boolean editing;

    public ToolGrid(Context c, boolean night, Host host) {
        super(c);
        this.c = c;
        this.night = night;
        this.host = host;
        setOrientation(VERTICAL);
    }

    private int dp(float v) {
        return Ui.dp(c, v);
    }

    public void setTools(List<String> list) {
        ids.clear();
        if (list != null) ids.addAll(list);
        rebuild();
    }

    public boolean has(String id) {
        return id != null && ids.contains(id);
    }

    public List<String> ids() {
        return new ArrayList<String>(ids);
    }

    public void addTool(String id) {
        if (id == null || ids.contains(id)) return;
        ids.add(id);
        rebuild();
        host.onChanged(new ArrayList<String>(ids));
    }

    public void refreshTiles() {
        for (int i = 0; i < tiles.size(); i++) {
            PanelTool.Def d = PanelTool.find(ids.get(i));
            if (d != null) refreshTile(c, night, tiles.get(i), d);
        }
    }

    public void setEditing(boolean on) {
        editing = on;
        for (View t : tiles) {
            View minus = t.findViewWithTag("minus");
            if (minus != null) minus.setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    private void rebuild() {
        removeAllViews();
        tiles.clear();
        List<String> cur = new ArrayList<String>(ids);
        final int PER = 4;
        for (int i = 0; i < cur.size(); i += PER) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(HORIZONTAL);
            int n = Math.min(PER, cur.size() - i);
            for (int j = 0; j < n; j++) {
                final String id = cur.get(i + j);
                final PanelTool.Def d = PanelTool.find(id);
                if (d == null) continue;
                final View tile = createTile(c, night, d);
                tile.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (editing) {
                            removeTool(id);
                        } else {
                            host.onTool(d, false);
                        }
                    }
                });
                tile.setOnLongClickListener(new OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        host.onTool(d, true);
                        return true;
                    }
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(72));
                lp.leftMargin = j == 0 ? 0 : dp(6);
                row.addView(tile, lp);
                tiles.add(tile);
            }
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rp.topMargin = dp(6);
            addView(row, rp);
        }
    }

    private void removeTool(String id) {
        ids.remove(id);
        rebuild();
        host.onChanged(new ArrayList<String>(ids));
    }

    // ---------------------------------------------------------------- kafelek (framework-only)

    /** Kafelek zbudowany WYŁĄCZNIE z widoków frameworka (zero onDraw). */
    public static View createTile(Context c, boolean night, PanelTool.Def d) {
        FrameLayout box = new FrameLayout(c);

        // koło tła
        View bg = new View(c);
        bg.setTag("bg");
        FrameLayout.LayoutParams bgLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        bgLp.setMargins(Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 6), Ui.dp(c, 12));
        box.addView(bg, bgLp);

        // ikona (bitmapa generowana raz, wyświetlana przez ImageView)
        ImageView icon = new ImageView(c);
        icon.setTag("icon");
        icon.setImageBitmap(tileIcon(c, d));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30));
        ilp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        ilp.topMargin = Ui.dp(c, 15);
        box.addView(icon, ilp);

        // etykieta
        TextView label = new TextView(c);
        label.setTag("label");
        label.setText(d.label);
        label.setTextSize(11);
        label.setMaxLines(1);
        label.setGravity(Gravity.CENTER);
        label.setTextColor(0xFFFFFFFF);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        llp.bottomMargin = Ui.dp(c, 4);
        box.addView(label, llp);

        // znak „−" (tylko tryb edycji)
        TextView minus = new TextView(c);
        minus.setTag("minus");
        minus.setText("−");
        minus.setTextColor(0xFFFFFFFF);
        minus.setGravity(Gravity.CENTER);
        minus.setTextSize(16);
        minus.setBackground(Ui.round(0xFFFF3B30, 999, c));
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
        mlp.gravity = Gravity.TOP | Gravity.END;
        box.addView(minus, mlp);
        minus.setVisibility(View.GONE);

        refreshTile(c, night, box, d);
        return box;
    }

    /** Odświeża stan kafelka: kolor koła i etykiety wg on/off. */
    public static void refreshTile(Context c, boolean night, View box, PanelTool.Def d) {
        View bg = box.findViewWithTag("bg");
        if (bg == null) return;
        boolean on = d.kind == PanelTool.K_TOGGLE ? PanelTool.isOn(c, d) : true;
        int color = on ? PanelTool.onColor(d)
                : (night ? 0x4D787880 : 0xFFE4E4EA);
        bg.setBackground(Ui.round(color, 40, c));
        ImageView icon = (ImageView) box.findViewWithTag("icon");
        if (icon != null) icon.setAlpha(on ? 255 : 150);
        TextView label = (TextView) box.findViewWithTag("label");
        if (label != null) {
            label.setTextColor(on ? 0xFFFFFFFF : (night ? 0x66FFFFFF : 0x33000000));
        }
    }

    private static Bitmap tileIcon(Context c, PanelTool.Def d) {
        String key = d.id;
        Bitmap b = ICON_CACHE.get(key);
        if (b == null) {
            b = makeIconBitmap(c, d);
            if (b != null) ICON_CACHE.put(key, b);
        }
        return b;
    }

    private static Bitmap makeIconBitmap(Context c, PanelTool.Def d) {
        if (d.kind == PanelTool.K_APP && d.action != null) {
            try {
                Drawable dr = c.getPackageManager().getApplicationIcon(d.action);
                Bitmap b = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
                Canvas cv = new Canvas(b);
                dr.setBounds(0, 0, 48, 48);
                dr.draw(cv);
                return b;
            } catch (Throwable t) {
                // spadamy na glif
            }
        }
        float den = c.getResources().getDisplayMetrics().density;
        int px = (int) Math.ceil(30f * den);
        Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        PanelTool.glyph(cv, d.glyph, px / 2f, px / 2f, 11f * den, 0xFFFFFFFF);
        return b;
    }
}
