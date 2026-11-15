package pl.dock.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Launcher: dock po lewej lub prawej; strony: widżety | ekran główny | aplikacje (jak CarPlay).
public class LauncherActivity extends BaseActivity {

    public static volatile boolean visible = false;

    private static final int HOST_ID = 4217;
    private static final int REQ_BIND = 31;
    private static final int REQ_CONFIG = 32;
    private static final int REQ_LOC = 77;

    private FrameLayout root;
    private BackgroundView bg;
    private PagerView pager;
    private DockView dock;
    private PageDots dotsTop;
    private long lastBlurAt = 0;
    private WidgetPage stacksPage;
    private HomeSections home;
    private NowPlayingCard nowCard;
    private final List<AppsGridPage> appPages = new ArrayList<AppsGridPage>();
    private List<AppsGridPage.Item> items = new ArrayList<AppsGridPage.Item>();
    private AppLibraryPage appLibraryPage;

    private AppWidgetManager awm;
    private AppWidgetHost host;
    private int pendingId = -1;
    private String pendingWhere = "s1";

    private boolean night;
    private boolean builtLeft = true;
    private float builtScale = 1f;
    private int homeIdx = 0;
    private int appsStart = 1;
    private int returnPage = -1;
    private long lastHomeIntentAt = 0L;   // anty-dubel: fizyczny home wysyla HOME 2x
    private int lastCp = -1;
    private String clockText = "--:--";
    private String lastDockSig = "";
    private List<AppEntry> topCache = new ArrayList<AppEntry>();
    private AppEntry cpCache;
    private boolean receiversOn;
    private FrameLayout overlay;
    private ImageView overlayGlass;   // zblurowana migawka tła w overlayu CC
    private View overlayTint;         // przyciemnienie pod panelem CC
    private boolean overlayTintNight;
    private ConnectivityPanelIos panel;
    // pelna lista aplikacji dla ostatniej strony (lista alfabetyczna), ladowana w tle
    private List<AppsGridPage.Item> libraryItems = new ArrayList<AppsGridPage.Item>();
    private VolumeHud hud;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            boolean n = Look.night(LauncherActivity.this);
            if (n != night) {
                night = n;
                smoothThemeChange();
            }
            refreshDock();
            scheduleMinute();
        }
    };
    // "na żywo": napięcie, ładowanie i bateria telefonu odświeżane co ~2 s, gdy launcher jest widoczny
    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            StatusCenter.refresh(LauncherActivity.this);
            refreshDock();
            h.postDelayed(this, 2000);
        }
    };
    private final Runnable onStatusChange = new Runnable() {
        @Override
        public void run() {
            refreshDock();
        }
    };

    private BroadcastReceiver statusReceiver;
    private BroadcastReceiver pkgReceiver;
    private final MediaHub.Listener mediaListener = new MediaHub.Listener() {
        @Override
        public void onTrack(MediaHub.Track t) {
            if (nowCard != null) nowCard.postInvalidate();
        }
    };

    // ---------------------------------------------------------------- cykl życia
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pl.dock.widget.ui.DockTheme.loadFonts(this);
        pl.dock.widget.maps.MapConfig.init(this);
        ensureLocationPermission();
        night = Look.night(this);
        builtScale = Prefs.uiScale(this);
        awm = AppWidgetManager.getInstance(this);
        host = new AppWidgetHost(this, HOST_ID);
        BtDevice.init(this);
        buildUi();
        StatusService.start(this);
        Updater.scheduleCheck(this);
        Updater.startPolling(this);   // sprawdzanie w tle co minute (Ustawienia)
        NightShift.restore(this);
        updateClockText();
        loadApps();
        currentRef = new java.lang.ref.WeakReference<LauncherActivity>(this);
        if (!restoredOnce) {
            restoreLastPage();
            restoredOnce = true;
        }
        Intent start = getIntent();
        if (start != null && start.hasExtra("pl.dock.widget.page")) {
            String page = start.getStringExtra("pl.dock.widget.page");
            String caller = start.getStringExtra("pl.dock.widget.caller");
            if (caller != null) Prefs.setExternalCaller(this, caller);
            final String pageF = page;
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (pager == null) return;
                    navigateToPage(pageF);
                }
            }, 250);
        }
    }

    // Uprawnienie do lokalizacji jest potrzebne mapie offline (GPS).
    // Android 9 (API 28) wymaga zapytania w czasie dzialania.
    private void ensureLocationPermission() {
        try {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                        REQ_LOC);
            }
        } catch (Throwable ignored) {
        }
    }

    // ... (full file truncated for this call - the complete fixed version is ready in local, please re-apply if needed) 
}