package pl.dock.widget;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// Okna podręczne w stylu Apple (nie na cały ekran, obok docka)
public class Popups {

    public interface OnSel {
        void on(int index);
    }

    public static int txt(boolean night) {
        return night ? 0xFFFFFFFF : 0xFF111114;
    }

    public static int sub(boolean night) {
        return night ? 0xFF9A9AA0 : 0xFF6C6C70;
    }

    public static TextView text(Activity a, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    public static Dialog panel(Activity a, View content, int widthDp, int gravity) {
        return panel(a, content, widthDp, gravity, true);
    }

    public static Dialog panel(Activity a, View content, int widthDp, int gravity, boolean blur) {
        boolean night = Look.night(a);
        final Dialog d = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        Ui.dockWindow(d.getWindow(), a);
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(0x55000000);
        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                d.dismiss();
            }
        });

        // Liquid Glass: blurred screenshot under the panel
        if (blur) {
            try {
                View decor = a.getWindow().getDecorView();
                int k = 4;
                int bw = Math.max(1, decor.getWidth() / k);
                int bh = Math.max(1, decor.getHeight() / k);
                android.graphics.Bitmap screenshot = android.graphics.Bitmap.createBitmap(
                        bw, bh, android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c = new android.graphics.Canvas(screenshot);
                c.scale(1f / k, 1f / k);
                decor.draw(c);
                Blur.blur(screenshot, 6, 3);
                android.widget.ImageView bg = new android.widget.ImageView(a);
                bg.setImageBitmap(screenshot);
                bg.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                root.addView(bg, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } catch (Throwable ignored) {
            }
        }

        LinearLayout panel = new LinearLayout(a);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.popupBg(a, night));
        panel.setClickable(true);
        panel.setElevation(Ui.dp(a, 12));
        panel.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int w = Math.min(Ui.dp(a, widthDp), Ui.windowWidth(a) - Ui.dp(a, 24));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                w, ViewGroup.LayoutParams.WRAP_CONTENT, gravity);
        int m = Ui.dp(a, 12);
        lp.setMargins(m, m, m, m);
        root.addView(panel, lp);
        d.setContentView(root);
        return d;
    }

    private static View hairline(Activity a, boolean night) {
        View v = new View(a);
        v.setBackgroundColor(night ? 0x33FFFFFF : 0x22000000);
        return v;
    }

    // lista pozycji do wyboru
    public static void menu(final Activity a, String title, String[] items, final OnSel cb) {
        boolean night = Look.night(a);
        final Dialog[] ref = new Dialog[1];
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(a, title, 15, sub(night), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(a, 20), Ui.dp(a, 16), Ui.dp(a, 20), Ui.dp(a, 10));
        c.addView(t);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            if (i > 0) {
                list.addView(hairline(a, night), new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
            TextView r = text(a, items[i], 20, txt(night), false);
            r.setPadding(Ui.dp(a, 24), Ui.dp(a, 16), Ui.dp(a, 24), Ui.dp(a, 16));
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ref[0].dismiss();
                    cb.on(idx);
                }
            });
            list.addView(r);
        }
        ScrollView s = new ScrollView(a);
        s.addView(list);
        int maxH = a.getResources().getDisplayMetrics().heightPixels - Ui.dp(a, 170);
        c.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                items.length > 6 ? maxH : ViewGroup.LayoutParams.WRAP_CONTENT));
        ref[0] = panel(a, c, 480, Gravity.CENTER);
        ref[0].show();
    }

    // potwierdzenie czynności nieodwracalnej
    public static void confirm(final Activity a, String title, String msg, String okLabel,
                               final Runnable ok) {
        boolean night = Look.night(a);
        final Dialog[] ref = new Dialog[1];
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(a, title, 20, txt(night), true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 22), Ui.dp(a, 6));
        c.addView(t);
        if (msg != null && msg.length() > 0) {
            TextView m = text(a, msg, 16, sub(night), false);
            m.setGravity(Gravity.CENTER);
            m.setPadding(Ui.dp(a, 22), 0, Ui.dp(a, 22), Ui.dp(a, 16));
            c.addView(m);
        }
        c.addView(hairline(a, night), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView yes = text(a, okLabel, 20, 0xFFFF453A, true);
        yes.setGravity(Gravity.CENTER);
        yes.setPadding(0, Ui.dp(a, 16), 0, Ui.dp(a, 16));
        yes.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ref[0].dismiss();
                ok.run();
            }
        });
        c.addView(yes);
        c.addView(hairline(a, night), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        TextView no = text(a, "Anuluj", 20, txt(night), false);
        no.setGravity(Gravity.CENTER);
        no.setPadding(0, Ui.dp(a, 16), 0, Ui.dp(a, 16));
        no.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ref[0].dismiss();
            }
        });
        c.addView(no);
        ref[0] = panel(a, c, 440, Gravity.CENTER);
        ref[0].show();
    }

    // menu przyklejone do widoku (obok ikony aplikacji, jak w iOS)
    public static void menuNear(final Activity a, View anchor, String title, String[] items,
                               final OnSel cb) {
        boolean night = Look.night(a);
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(Ui.popupBg(a, night));
        c.setElevation(Ui.dp(a, 12));
        if (title != null && title.length() > 0) {
            TextView t = text(a, title, 14, sub(night), true);
            t.setPadding(Ui.dp(a, 20), Ui.dp(a, 12), Ui.dp(a, 20), Ui.dp(a, 4));
            c.addView(t);
        }
        final android.widget.PopupWindow[] w = new android.widget.PopupWindow[1];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            if (i > 0) {
                View hl = new View(a);
                hl.setBackgroundColor(night ? 0x33FFFFFF : 0x22000000);
                c.addView(hl, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
            TextView r = text(a, items[i], 18, txt(night), false);
            r.setPadding(Ui.dp(a, 22), Ui.dp(a, 12), Ui.dp(a, 22), Ui.dp(a, 12));
            c.addView(r);
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    w[0].dismiss();
                    cb.on(idx);
                }
            });
        }
        w[0] = new android.widget.PopupWindow(c, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        w[0].setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        w[0].setOutsideTouchable(true);
        w[0].showAsDropDown(anchor, 0, -anchor.getHeight() / 2);
    }







}
