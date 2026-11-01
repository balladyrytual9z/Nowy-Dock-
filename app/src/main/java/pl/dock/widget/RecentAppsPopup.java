
package pl.dock.widget;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * iOS-style App Switcher lite: horizontal cards with large icons,
 * springy scale-up from bottom, frosted glass backdrop.
 * Long-press Home / bottom gesture â this panel.
 */
public final class RecentAppsPopup {

    private static final PathInterpolator SPRING =
            new PathInterpolator(0.25f, 1.1f, 0.5f, 1.0f);

    public static void show(final Activity a, final List<AppEntry> recents) {
        boolean night = Look.night(a);
        final Dialog dialog = new Dialog(a, android.R.style.Theme_Translucent_NoTitleBar);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.55f);
            w.setGravity(Gravity.BOTTOM);
        }

        final FrameLayout root = new FrameLayout(a);
        root.setClickable(true);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dismissAnimated(dialog, root);
            }
        });

        TextView title = new TextView(a);
        title.setText("Ostatnio uÅ¼ywane");
        title.setTextSize(15);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, Ui.dp(a, 18), 0, Ui.dp(a, 8));
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP;
        root.addView(title, tlp);

        HorizontalScrollView hsv = new HorizontalScrollView(a);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hsv.setFillViewport(false);
        hsv.setPadding(Ui.dp(a, 16), 0, Ui.dp(a, 16), Ui.dp(a, 28));
        hsv.setClipToPadding(false);

        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        hsv.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int cardW = Ui.dp(a, 148);
        int cardH = Ui.dp(a, 210);
        int gap = Ui.dp(a, 14);

        if (recents.isEmpty()) {
            TextView empty = new TextView(a);
            empty.setText("Brak ostatnio uÅ¼ywanych aplikacji");
            empty.setTextColor(0xCCFFFFFF);
            empty.setTextSize(16);
            empty.setGravity(Gravity.CENTER);
            row.addView(empty, new LinearLayout.LayoutParams(
                    Ui.dp(a, 280), ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            for (int i = 0; i < recents.size(); i++) {
                final AppEntry e = recents.get(i);
                View card = buildCard(a, e, night, cardW, cardH, dialog, root);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(cardW, cardH);
                if (i > 0) lp.leftMargin = gap;
                row.addView(card, lp);

                card.setScaleX(0.72f);
                card.setScaleY(0.72f);
                card.setAlpha(0f);
                card.setTranslationY(Ui.dp(a, 48));
                final int delay = 40 + i * 55;
                card.animate()
                        .scaleX(1f).scaleY(1f).alpha(1f).translationY(0f)
                        .setStartDelay(delay)
                        .setDuration(420)
                        .setInterpolator(SPRING)
                        .start();
            }
        }

        FrameLayout.LayoutParams hsvLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, cardH + Ui.dp(a, 40));
        hsvLp.gravity = Gravity.BOTTOM;
        hsvLp.bottomMargin = Ui.dp(a, 36);
        root.addView(hsv, hsvLp);

        root.setAlpha(0f);
        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.show();

        root.animate().alpha(1f).setDuration(280).setInterpolator(SPRING).start();
    }

    private static View buildCard(final Activity a, final AppEntry e, boolean night,
                                  int cardW, int cardH, final Dialog dialog, final View root) {
        FrameLayout card = new FrameLayout(a);
        card.setClickable(true);

        View bg = new View(a) {
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r = new RectF();
            @Override protected void onDraw(Canvas c) {
                float rad = 22f * getResources().getDisplayMetrics().density;
                r.set(0, 0, getWidth(), getHeight());
                p.setStyle(Paint.Style.FILL);
                p.setColor(night ? 0xE628282C : 0xF0F2F2F7);
                c.drawRoundRect(r, rad, rad, p);
                p.setColor(0x33FFFFFF);
                r.set(1, 1, getWidth() - 1, getHeight() * 0.35f);
                c.drawRoundRect(r, rad, rad, p);
            }
        };
        card.addView(bg, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ImageView icon = new ImageView(a);
        Bitmap bmp = Icons.forEntry(a, e);
        icon.setImageBitmap(bmp);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(
                Ui.dp(a, 72), Ui.dp(a, 72));
        ilp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        ilp.topMargin = Ui.dp(a, 36);
        card.addView(icon, ilp);

        TextView label = new TextView(a);
        label.setText(appLabel(a, e.pkg));
        label.setTextSize(13);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setTextColor(night ? 0xFFFFFFFF : 0xFF1C1C1E);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.gravity = Gravity.BOTTOM;
        llp.bottomMargin = Ui.dp(a, 18);
        llp.leftMargin = Ui.dp(a, 8);
        llp.rightMargin = Ui.dp(a, 8);
        card.addView(label, llp);

        card.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90)
                        .withEndAction(new Runnable() {
                            @Override public void run() {
                                try {
                                    android.content.Intent i = a.getPackageManager()
                                            .getLaunchIntentForPackage(e.pkg);
                                    if (i != null) {
                                        Prefs.recordPkg(a, e.pkg);
                                        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                                        a.startActivity(i);
                                        a.overridePendingTransition(R.anim.cp_push_in, R.anim.cp_push_out);
                                    }
                                } catch (Throwable ignored) {}
                                dismissAnimated(dialog, root);
                            }
                        }).start();
            }
        });

        card.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start();
                } else if (event.getAction() == MotionEvent.ACTION_UP
                        || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
                return false;
            }
        });

        return card;
    }

    private static void dismissAnimated(final Dialog dialog, final View root) {
        if (dialog == null || !dialog.isShowing()) return;
        root.animate().alpha(0f).translationY(Ui.dp(root.getContext(), 24))
                .setDuration(220).setInterpolator(SPRING)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                    }
                }).start();
    }

    private static String appLabel(Activity a, String pkg) {
        try {
            return a.getPackageManager().getApplicationLabel(
                    a.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return pkg;
        }
    }

    private RecentAppsPopup() {}
}


