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
    private int lastCp = -1;
    private String clockText = "--:--";
    private String lastDockSig = "";
    private List<AppEntry> topCache = new ArrayList<AppEntry>();
    private AppEntry cpCache;
    private boolean receiversOn;
    private FrameLayout overlay;
    private ConnectivityPanel panel;
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOC && grantResults != null && grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Po nadaniu zgody uruchamiamy nasluch GPS (kafelek mapy).
            try {
                pl.dock.widget.maps.MapState.get(this).onPermissionGranted();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        visible = true;
        try {
            host.startListening();
        } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);
        }
        registerReceivers();
        AppListWatcher.register(this);
        AppListWatcher.get().addListener(appsListener);
        registerNav();
        MediaHub.get().attach(this, mediaListener);
        StatusCenter.setOnChange(onStatusChange);
        StatusCenter.refresh(this);
        lastDockSig = "";
        refreshDock();
        scheduleMinute();
        h.removeCallbacks(statusTick);
        h.postDelayed(statusTick, 2000);
    }

    @Override
    protected void onStop() {
        super.onStop();
        visible = false;
        try {
            host.stopListening();
        } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);
        }
        unregisterReceivers();
        AppListWatcher.get().removeListener(appsListener);
        unregisterNav();
        MediaHub.get().detach(mediaListener);
        StatusCenter.setOnChange(null);
        h.removeCallbacks(minuteTick);
        h.removeCallbacks(statusTick);
        hideOverlay(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Powrot na STRONE GLOWNA (przycisk home w doku Nawigacji ustawia te flage).
        if (Prefs.goHome(this)) {
            Prefs.setGoHome(this, false);
            if (pager != null) pager.goTo(homeIdx, false);
        }
        applyBars();
        if (Math.abs(Prefs.uiScale(this) - builtScale) > 0.001f) {
            recreate();
            return;
        }
        if (Prefs.widgetsPage(this) != (stacksPage != null) || builtLeft != Ui.dockLeft(this)) {
            rebuildAll();
        } else {
            night = Look.night(this);
            applyTheme();
            pager.setReduceMotion(Prefs.reduceMotion(this));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            dockSlideIn();
            applyBars();
            if (Prefs.wakeBounce(this) && !Prefs.reduceMotion(this)) {
                playWakeBounce();
            }
            if ("glass".equals(Prefs.style(this)) && root != null && dock != null) {
                try {
                    Bitmap blur = LiveBlur.captureAndBlur(root, root.getWidth(), root.getHeight());
                    dock.setGlassBitmap(blur);
                    dock.setGlassOrigin(dock.getLeft());
                    dock.setRootSize(root.getWidth(), root.getHeight());
                } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);}
            }
        }
    }

    // Przycisk Home: z innej aplikacji wraca na stronę, z której ją otwarto;
    // na ekranie głównym wykonuje skonfigurowaną akcję (domyślnie otwiera CarPlay).
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.hasExtra("pl.dock.widget.page")) {
            String caller = intent.getStringExtra("pl.dock.widget.caller");
            if (caller != null) Prefs.setExternalCaller(this, caller);
            navigateToPage(intent.getStringExtra("pl.dock.widget.page"));
            return;
        }

        if (pager == null) return;
        if (overlay != null) {
            hideOverlay(true);
            return;
        }
        if (!visible) {
            int target = returnPage >= 0 && returnPage < pager.count() ? returnPage : homeIdx;
            returnPage = -1;
            pager.goTo(target, false);
            return;
        }
        // Przelacznik Home: dziala z kazdej strony launchera (widzetow, glownej,
        // aplikacji, biblioteki). Zapamietujemy aktualna strone - Home nacisniety
        // w DiPlay wroci do Docka dokladnie na te strone, z ktorej przyszedles.
        returnPage = Math.round(pager.position());
        runHomeAction();
    }

    private void runHomeAction() {
        String a = Prefs.homeAction(this);
        if ("carplay".equals(a)) {
            Launch.carplay(this);
            overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_fade_out);
        } else if ("app".equals(a)) {
            String pkg = Prefs.homeAppPkg(this);
            if (pkg.length() > 0) {
                openApp(pkg, homeIdx);
            } else {
                // nic nie wybrano: bezpieczny przełącznik na DiPlay (jak przycisk Quick Switch)
                Launch.carplay(this);
                overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_fade_out);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (overlay != null) {
            hideOverlay(true);
        } else if (pager != null && pager.position() > homeIdx + 0.5f) {
            pager.goTo(homeIdx, true);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        boolean n = Look.night(this);
        if (n != night) {
            night = n;
            applyTheme();
        }
    }

    // własny pasek głośności zamiast systemowego (można wyłączyć w ustawieniach)
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                && Prefs.customBars(this) && hud != null) {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        keyCode == KeyEvent.KEYCODE_VOLUME_UP ? AudioManager.ADJUST_RAISE
                                : AudioManager.ADJUST_LOWER, 0);
                int max = Math.max(1, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
                MediaHub.Track t = MediaHub.get().current();
                hud.show(am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max,
                        t.hasData() && t.playing() ? t.title : "");
                return true;
            } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void applyBars() {
        if (!Prefs.hideBars(this)) return;
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    // ---------------------------------------------------------------- budowa interfejsu
    private void buildUi() {
        builtLeft = Ui.dockLeft(this);
        boolean left = builtLeft;
        int dockPx = Ui.dp(this, Ui.DOCK_OFFSET_DP);
        root = new FrameLayout(this);
        // zaokrąglone rogi ekranu jak w CarPlay
        // NAPRAWA (ESSGOO/MT8163, Android 9): setClipToOutline(true) z Outline
        // liczonym z getWidth()/getHeight() przy pierwszym pomiarze (0x0)
        // przycinalo cale drzewo widokow - wlacznie z overlayem panelu
        // lacznosci - do pustego prostokata. Na API < 30 nie ustawiamy
        // clipowania w ogole; na nowszych guard na wymiary > 0.
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            root.setClipToOutline(true);
            root.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline o) {
                    if (v.getWidth() > 0 && v.getHeight() > 0) {
                        o.setRoundRect(0, 0, v.getWidth(), v.getHeight(),
                                Ui.dp(LauncherActivity.this, 26));
                    }
                }
            });
        }
        getWindow().setBackgroundDrawable("system".equals(Prefs.wallpaper(this))
                ? new ColorDrawable(Color.TRANSPARENT) : new ColorDrawable(Color.BLACK));

        bg = new BackgroundView(this);
        root.addView(bg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Pager occupies full width; dock overlays it (liquid glass draws content underneath)
        pager = new PagerView(this);
        pager.setReduceMotion(Prefs.reduceMotion(this));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        root.addView(pager, plp);

        // Page indicators keep side margin so they don't sit under the dock
        dotsTop = new PageDots(this, true);
        FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 28), Gravity.BOTTOM);
        if (left) {
            dlp.leftMargin = dockPx;
        } else {
            dlp.rightMargin = dockPx;
        }
        dlp.bottomMargin = Ui.dp(this, 6);
        root.addView(dotsTop, dlp);

        hud = new VolumeHud(this);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(Ui.dp(this, 380),
                Ui.dp(this, 64), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        hlp.topMargin = Ui.dp(this, 14);
        root.addView(hud, hlp);

        // Dock added last so it paints above scrolling content (liquid glass)
        dock = new DockView(this);
        root.addView(dock, new FrameLayout.LayoutParams(dockPx,
                ViewGroup.LayoutParams.MATCH_PARENT, left ? Gravity.START : Gravity.END));
        dock.setListener(new DockView.Listener() {
            @Override
            public void onHeader() {
                toggleOverlay();
            }

            @Override
            public void onApp(int index) {
                if (index < 3) {
                    if (index < topCache.size()) openApp(topCache.get(index).pkg, homeIdx);
                } else {
                    Launch.carplay(LauncherActivity.this);
                    overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_fade_out);
                }
            }

            @Override
            public void onBottom() {
                if (pager.position() < homeIdx + 0.5f) {
                    pager.goTo(appsStart, true);
                } else {
                    pager.goTo(homeIdx, true);
                }
            }

            @Override
            public void onBottomLong() {
                java.util.List<AppEntry> recents = Prefs.recents(LauncherActivity.this, 8);
                RecentAppsPopup.show(LauncherActivity.this, recents);
            }
        });

        pager.setListener(new PagerView.Listener() {
            @Override
            public void onScroll(float pos) {
                int total = Math.max(1, pager.count() - 1);
                bg.setParallax(Prefs.parallax(LauncherActivity.this) ? pos / total : 0f);
                float t = Math.max(0f, Math.min(1f, pos - homeIdx));
                dock.setBottomProgress(t);
                // Nie ciemniej strony widżetów w trybie dziennym
                bg.setNightBlend(0f);
                dock.setNightBlend(0f);
                // Liquid glass: ZADNE przechwytywanie/rozmycie w trakcie przewijania
                // (przechwytywanie calego ekranu + blur na watku UI tnilo gest na
                // slabym GPU tabletu) - snapshot robimy dopiero po osiadnieciu strony
                // wskaźnik kropek ma być widoczny na wszystkich stronach siatki,
                // znikać dopiero przy przejściu na ostatnią stronę (Biblioteka)
                float toLibrary = Math.max(0f, Math.min(1f, pos - (pager.count() - 2)));
                dotsTop.setAlpha(1f - toLibrary);
                // ZAWSZE realna liczba stron calego panelu (bez wyszukiwania), tak samo na
                // kazdej stronie - zgloszenie: na stronie glownej byly 2 kropki,
                // a na liscie aplikacji liczba aplikacji.
                int panelCount = panelPageCount();
                dotsTop.set(panelCount, Math.min(pos, panelCount - 1));
                // biale tlo (kapsula) tylko na stronie glownej
                dotsTop.setCapsule(Math.abs(pos - homeIdx) < 0.5f);
            }

            @Override
            public void onSettled(int index) {
                saveLastPage(index);
                refreshGlass();
                // wyjscie ze strony Biblioteki zamyka fokus i klawiature wyszukiwania
                if (appLibraryPage != null && index != pager.count() - 1) {
                    appLibraryPage.dismissSearch();
                }
            }

            @Override
            public void onSwipeUp(float x) {
                // gest ze strefy dolnej ekranu - centrum lacznosci
                if (overlay == null) showOverlayAt(x);
            }
        });

        setContentView(root);
        bg.setStyle(Prefs.wallpaper(this), night);
        buildWidgetPages();
        assemblePages();
        // Fix race condition: wskaznik od razu po zbudowaniu stron
        int panelCount = panelPageCount();
        dotsTop.set(panelCount, homeIdx);
        dotsTop.setCapsule(true);
        dotsTop.setAlpha(1f);
        lastDockSig = "";

        // Gest swipe UP od dołu → centrum łączności (środek panelu nad palcem).
        // NAPRAWA (root cause): wcześniej gest wisiał na listenerze dotyku roota -
        // ale pełnoekranowy pager konsumuje KAŻDY ACTION_DOWN u dzieci, więc ten
        // listener nigdy nie dostawał zdarzenia i gest był martwym kodem. Teraz
        // wykrywanie mieszka w PagerView (onInterceptTouchEvent/onTouchEvent),
        // które naprawdę widzi wszystkie dotyki; pager przejmuje gest tylko przy
        // wyraznie pionowym ruchu w górę z dolnej strefy (tapy i przewijanie stron
        // działają normalnie).
        pager.setSwipeEnabled(Prefs.overlayEnabled(this));

        // iOS-like bounce po zbudowaniu UI (wybudzenie / pierwsze otwarcie)
        if (Prefs.wakeBounce(this) && !Prefs.reduceMotion(this)) {
            root.post(new Runnable() {
                @Override public void run() {
                    playWakeBounce();
                }
            });
        }
    }

    /** Krótka animacja jak w iOS: elementy lekko wjeżdżają i odbijają się. */
    private void playWakeBounce() {
        if (pager == null) return;
        View page = null;
        try {
            int idx = Math.round(pager.position());
            if (idx >= 0 && idx < pager.getChildCount()) page = pager.getChildAt(idx);
        } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);}
        if (page == null) page = pager;
        final View target = page;
        target.setAlpha(0f);
        target.setTranslationY(Ui.dp(this, 28));
        target.setScaleX(0.96f);
        target.setScaleY(0.96f);
        target.animate()
                .alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.05f))
                .start();
        if (dock != null) {
            dock.setTranslationX(Ui.dockLeft(this) ? -Ui.dp(this, 24) : Ui.dp(this, 24));
            dock.setAlpha(0.6f);
            dock.animate().translationX(0f).alpha(1f).setDuration(380)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f))
                    .start();
        }
    }

    private void rebuildAll() {
        buildUi();
        refreshDock();
    }

    private void buildWidgetPages() {
        stacksPage = null;
        if (Prefs.widgetsPage(this)) {
            stacksPage = new WidgetPage(this, new String[]{"s1", "s2", "s3"}, true);
            stacksPage.setNight(night);
            stacksPage.setCallbacks(new WidgetPage.Callbacks() {
                @Override
                public void onAdd(String where) {
                    showWidgetPicker(where);
                }

                @Override
                public void onMenu(WidgetEntry e) {
                    widgetMenu(e);
                }

                @Override
                public void onEmptyLong() {
                    String w = stacksPage != null ? stacksPage.pendingWhere() : null;
                    showWidgetPicker(w != null ? w : "s2");
                }
            });
        }
        home = new HomeSections(this);
        nowCard = new NowPlayingCard(this);
        nowCard.setFrame(true, true);
        nowCard.setNight(night);
        loadWidgets();
    }

    // zbiera strony do pagera: [widzety] [ekran glowny] [aplikacje...]
    // UWAGA: nie wolno robic removeAllViews() + addView() przy kazdej zmianie
    // listy aplikacji. Odlaczenie widoku od okna niszczy osmdroid MapView
    // (domyslnie destroyModeOnDetach=true -> onDetach(): czyszczenie kafli,
    // zerwanie projekcji), a po ponownym dodaniu mapa zostaje pusta.
    // Dlatego: usuwamy tylko strony zbedne, brakujace dodajemy, a kolejnosc
    // ustawiamy przez bringChildToFront (bez detach/attach).
    private void assemblePages() {
        List<View> order = new ArrayList<View>();
        homeIdx = 0;
        if (stacksPage != null) {
            order.add(stacksPage);
            homeIdx = 1;
        }
        order.add(home);
        appsStart = homeIdx + 1;
        for (AppsGridPage p : appPages) order.add(p);
        // App Library (Biblioteka aplikacji) - last page, iOS style
        ensureAppLibrary();
        if (appLibraryPage != null) {
            appLibraryPage.setApps(libraryItems);
            appLibraryPage.setNight(night);
            order.add(appLibraryPage);
        }
        // 1) strony, ktorych juz nie ma na liscie
        for (int i = pager.getChildCount() - 1; i >= 0; i--) {
            if (!order.contains(pager.getChildAt(i))) pager.removeViewAt(i);
        }
        // 2) brakujace dodaj, istniejace przenies na wlasciwe miejsce
        for (int i = 0; i < order.size(); i++) {
            View v = order.get(i);
            if (v.getParent() == pager) {
                pager.bringChildToFront(v);
            } else {
                if (v.getParent() instanceof ViewGroup) {
                    ((ViewGroup) v.getParent()).removeView(v);
                }
                pager.addView(v, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            }
        }
        pager.setPosition(homeIdx);
    }

    private void ensureAppLibrary() {
        if (appLibraryPage != null) return;
        appLibraryPage = new AppLibraryPage(this);
        appLibraryPage.setHost(new AppLibraryPage.Host() {
            @Override
            public void openApp(String pkg) {
                if (pkg == null || pkg.length() == 0) return;
                LauncherActivity.this.openApp(pkg, Math.round(pager.position()));
            }

            @Override
            public void onLibraryChanged() {
                if (appLibraryPage != null) appLibraryPage.setApps(libraryItems);
            }

            @Override
            public void setOnGrid(String pkg, boolean onGrid) {
                setAppOnGrid(pkg, onGrid);
            }

            @Override
            public void showAppInfo(String pkg) {
                if (pkg == null || pkg.length() == 0) return;
                startSystemSmooth(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + pkg)));
            }

            @Override
            public void uninstall(String pkg) {
                uninstallApp(pkg);
            }
        });
    }

    // Dodaje / usuwa aplikacje z siatki aplikacji (strony z ikonami).
    // Pusta lista wybranych znaczy "wszystkie widoczne", wiec przy usuwaniu trzeba ja
    // najpierw zmaterializowac - inaczej odznaczenie jednej apki ukryloby cala reszte.
    private void setAppOnGrid(String pkg, boolean onGrid) {
        if (pkg == null || pkg.length() == 0) return;
        List<String> sel = Prefs.selectedPkgs(this);
        if (onGrid) {
            if (sel.isEmpty()) return;               // wszystko juz jest na siatce
            if (!sel.contains(pkg)) sel.add(pkg);
        } else {
            if (sel.isEmpty()) {
                for (AppsGridPage.Item it : items) {
                    if (it.pkg != null && it.pkg.length() > 0) sel.add(it.pkg);
                }
            }
            if (!sel.contains(pkg)) return;          // juz jej nie ma na siatce
            sel.remove(pkg);
        }
        Prefs.setSelectedPkgs(this, sel);
        loadApps();                                  // odbuduj siatke i liste w tle
    }

    private void applyTheme() {
        bg.setStyle(Prefs.wallpaper(this), night);
        if (stacksPage != null) stacksPage.setNight(night);
        home.setNight(night);
        for (int i = 0; i < appPages.size(); i++) {
            AppsGridPage p = appPages.get(i);
            int from = i * AppsGridPage.PER_PAGE;
            int to = Math.min(items.size(), from + AppsGridPage.PER_PAGE);
            if (from < to) {
                p.setItems(new ArrayList<AppsGridPage.Item>(items.subList(from, to)), night);
            }
        }
        if (appLibraryPage != null) {
            appLibraryPage.setNight(night);
            appLibraryPage.setApps(libraryItems);
        }
        lastDockSig = "";
        refreshDock();
    }

    // Plynne przejscie dzien/noc na CALYM ekranie: przezroczysty scrim narasta,
    // motyw zamienia sie pod nim, potem scrim zanika (zadnych naglych skokow).
    private View themeScrim;

    private void smoothThemeChange() {
        if (root == null || themeScrim != null || Prefs.reduceMotion(this)) {
            applyTheme();
            return;
        }
        final View scrim = new View(this);
        themeScrim = scrim;
        scrim.setBackgroundColor(night ? 0xFF0B0C0E : 0xFFE8E8ED);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setAlpha(0f);
        scrim.animate().alpha(0.4f).setDuration(160)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        applyTheme();
                        scrim.animate().alpha(0f).setDuration(320)
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            root.removeView(scrim);
                                        } catch (Throwable ignored) {
                                        }
                                        if (themeScrim == scrim) themeScrim = null;
                                    }
                                }).start();
                    }
                }).start();
    }

    // ---------------------------------------------------------------- dock
    private void updateClockText() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        clockText = String.format(Locale.US, "%02d:%02d", c.get(java.util.Calendar.HOUR_OF_DAY),
                c.get(java.util.Calendar.MINUTE));
    }

    private void scheduleMinute() {
        h.removeCallbacks(minuteTick);
        long now = System.currentTimeMillis();
        h.postDelayed(minuteTick, 60000L - (now % 60000L) + 30L);
    }

    // odświeża dock tylko wtedy, gdy zmieniły się dane
    private void refreshDock() {
        updateClockText();
        StatusCenter.Snap s = StatusCenter.get(this);
        String sig = Prefs.version + "|" + Icons.version() + "|" + s.sig() + "|" + night + "|"
                + clockText;
        if (sig.equals(lastDockSig)) return;
        lastDockSig = sig;
        topCache = Prefs.top(this, 3);
        cpCache = Prefs.carplay(this);
        Bitmap[] ic = new Bitmap[4];
        for (int i = 0; i < 3 && i < topCache.size(); i++) {
            ic[i] = Icons.forEntry(this, topCache.get(i));
        }
        ic[3] = Icons.forEntry(this, cpCache);
        dock.setData(night, "glass".equals(Prefs.style(this)), clockText, s,
                Prefs.showVoltage(this), ic);
        dock.setCarplayState(s.cp);
        int connected = s.cp == CarplayState.CONNECTED ? 1 : 0;
        if (connected != lastCp) {
            lastCp = connected;
            loadApps();
        }
    }

    // ---------------------------------------------------------------- odbiorniki (tylko gdy widoczny)
    private void registerReceivers() {
        if (receiversOn) return;
        receiversOn = true;
        statusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String a = intent.getAction();
                // Samoaktualizacja: przy podlaczeniu WiFi sprawdz nowy release na GitHubie
                if (ConnectivityManager.CONNECTIVITY_ACTION.equals(a)
                        || WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(a)) {
                    Updater.scheduleCheck(context);
                }
                if (Intent.ACTION_BATTERY_CHANGED.equals(a) && !Prefs.showVoltage(context)) return;
                BtDevice.handle(intent);
                PhoneBattery.handle(intent);
                if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                    StatusCenter.refreshIfStale(context, 1000L);   // bateria zgłasza się bardzo często
                } else {
                    StatusCenter.refresh(context);
                }
                refreshDock();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        f.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        f.addAction("android.net.conn.TETHER_STATE_CHANGED");
        f.addAction(Intent.ACTION_BATTERY_CHANGED);
        f.addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED");
        f.addAction("android.hardware.usb.action.USB_DEVICE_DETACHED");
        f.addAction(PhoneBattery.ACTION_DEV);
        f.addAction(PhoneBattery.ACTION_VENDOR);
        f.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(android.bluetooth.BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(statusReceiver, f);

        pkgReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Icons.invalidate();
                loadApps();   // nowo zainstalowane aplikacje trafiają do listy automatycznie
            }
        };
        IntentFilter p = new IntentFilter();
        p.addAction(Intent.ACTION_PACKAGE_ADDED);
        p.addAction(Intent.ACTION_PACKAGE_REMOVED);
        p.addAction(Intent.ACTION_PACKAGE_REPLACED);
        p.addDataScheme("package");
        registerReceiver(pkgReceiver, p);

    }

    private void unregisterReceivers() {
        if (!receiversOn) return;
        receiversOn = false;
        try {
            unregisterReceiver(statusReceiver);
        } catch (Throwable t) {
            android.util.Log.w("Dock", "unregister statusReceiver", t);
        }
        try {
            unregisterReceiver(pkgReceiver);
        } catch (Throwable t) {
            android.util.Log.w("Dock", "unregister pkgReceiver", t);
        }
    }

    // ---------------------------------------------------------------- aplikacje
    private final java.util.concurrent.atomic.AtomicInteger loadGen = new java.util.concurrent.atomic.AtomicInteger();

    private void loadApps() {
        final Context app = getApplicationContext();
        final int gen = loadGen.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (gen != loadGen.get()) return;   // przyszło nowsze żądanie
                final List<AppsGridPage.Item> list = buildItems(app);
                for (AppsGridPage.Item it : list) {
                    if (it.builtinId.length() > 0) {
                        Icons.builtin(app, it.builtinId);
                    } else if (it.entry != null) {
                        Icons.forEntry(app, it.entry);
                    } else {
                        Icons.forPackage(app, it.pkg);
                    }
                }
                // ostatnia strona (lista alfabetyczna) pokazuje WSZYSTKIE aplikacje -
                // inne zrodlo danych niz siatka, wiec liczymy je tutaj, w tle
                final List<AppsGridPage.Item> lib = buildLibraryItems(app);
                for (AppsGridPage.Item it : lib) {
                    if (it.settings) {
                        Icons.builtin(app, "settings");
                    } else if (it.builtinId.length() > 0) {
                        Icons.builtin(app, it.builtinId);
                    } else {
                        Icons.forPackageRaw(app, it.pkg);
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || gen != loadGen.get()) return;   // stara lista nie nadpisze nowej
                        items = list;
                        libraryItems = lib;
                        rebuildAppPages();
                    }
                });
            }
        }).start();
    }

    private List<AppsGridPage.Item> buildItems(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        String diplay = Prefs.diplayPkg(c);
        List<AppEntry> entries = Prefs.apps(c);
        List<AppsGridPage.Item> apps = new ArrayList<AppsGridPage.Item>();
        Set<String> seen = new HashSet<String>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName()) || pkg.equals(diplay) || !seen.add(pkg)) continue;
            if (!Prefs.isAppSelected(c, pkg)) continue;
            AppsGridPage.Item it = new AppsGridPage.Item();
            it.pkg = pkg;
            it.label = ri.loadLabel(pm).toString();
            AppEntry own = null;
            for (AppEntry e : entries) if (e.pkg.equals(pkg)) own = e;
            Overrides.O o = Overrides.match(it.label);
            if (it.label.equalsIgnoreCase("Material Files")) it.label = "Pliki";
            if (own != null) {
                it.entry = own;
            } else if (o != null) {
                AppEntry v = new AppEntry();
                v.id = 1000000L + (pkg.hashCode() & 0x7fffffff);
                v.pkg = pkg;
                v.mode = "builtin";
                v.iconPkg = o.icon;
                it.entry = v;
            }
            if (o != null) it.label = o.label;
            apps.add(it);
        }
        Collections.sort(apps, new Comparator<AppsGridPage.Item>() {
            @Override
            public int compare(AppsGridPage.Item a, AppsGridPage.Item b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });
        List<AppsGridPage.Item> out = new ArrayList<AppsGridPage.Item>();
        AppsGridPage.Item st = new AppsGridPage.Item();
        st.settings = true;
        st.label = "Ustawienia";
        out.add(st);
        AppsGridPage.Item cp = new AppsGridPage.Item();
        cp.label = "CarPlay";
        cp.builtinId = "carplay";
        cp.action = "carplay";
        out.add(cp);
        // ikona OBD (DiPlay/CarPlay filtruje wlasny pakiet, wiec dodajemy ja recznie)
        // Aplikacja OBD w launcherze ma ikone Hondy i otwiera ekran Honda OBD Discovery.
        AppsGridPage.Item obd = new AppsGridPage.Item();
        obd.label = "Honda OBD";
        obd.builtinId = "honda";
        obd.action = "obd";
        out.add(obd);
        out.addAll(apps);
        return out;
    }

    // Lista WSZYSTKICH aplikacji dla ostatniej strony (lista alfabetyczna).
    // Rozni sie od buildItems: nie filtruje po wybranych do siatki, nie podmienia
    // nazw (Overrides) i nie podmienia ikon - pokazuje oryginalna nazwe aplikacji.
    // Wykluczony jest tylko wlasny pakiet Docka (nie pokazujemy sami siebie).
    private List<AppsGridPage.Item> buildLibraryItems(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
        List<AppsGridPage.Item> out = new ArrayList<AppsGridPage.Item>();
        Set<String> seen = new HashSet<String>();
        for (ResolveInfo ri : ris) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(c.getPackageName())) continue;
            if (!seen.add(pkg)) continue;
            if (Prefs.isHiddenInLibrary(c, pkg)) continue;
            AppsGridPage.Item it = new AppsGridPage.Item();
            it.pkg = pkg;
            it.label = ri.loadLabel(pm).toString();   // oryginalna nazwa (bez Overrides)
            out.add(it);
        }
        // narzedzia launchera wchodza do tej samej listy i sortuja sie alfabetycznie
        // (CarPlay pod C, Honda OBD pod H, Ustawienia pod U)
        AppsGridPage.Item st = new AppsGridPage.Item();
        st.settings = true;
        st.label = "Ustawienia";
        out.add(st);
        AppsGridPage.Item cp = new AppsGridPage.Item();
        cp.label = "CarPlay";
        cp.builtinId = "carplay";
        cp.action = "carplay";
        out.add(cp);
        AppsGridPage.Item obd = new AppsGridPage.Item();
        obd.label = "Honda OBD";
        obd.builtinId = "honda";
        obd.action = "obd";
        out.add(obd);
        return out;
    }

    private void rebuildAppPages() {
        appPages.clear();
        int n = Math.max(1, (items.size() + AppsGridPage.PER_PAGE - 1) / AppsGridPage.PER_PAGE);
        for (int i = 0; i < n; i++) {
            AppsGridPage p = new AppsGridPage(this);
            int from = i * AppsGridPage.PER_PAGE;
            int to = Math.min(items.size(), from + AppsGridPage.PER_PAGE);
            p.setItems(new ArrayList<AppsGridPage.Item>(items.subList(from, to)), night);
            p.setListener(new AppsGridPage.Listener() {
                @Override
                public void onTap(AppsGridPage.Item it) {
                    if (EditModeController.get().isEditing()) return;
                    if (it.settings) {
                        Prefs.recordPkg(LauncherActivity.this, "dock:settings");  // liczy sie do 4 ikon doka
                        openSettings();
                    } else if ("obd".equals(it.action)) {
                        Prefs.recordPkg(LauncherActivity.this, "dock:obd");  // liczy sie do 4 ikon doka
                        try {
                            startActivity(new Intent(LauncherActivity.this,
                                    pl.dock.widget.ObdDiscoveryActivity.class));
                        } catch (Throwable ignored) {
                            android.util.Log.w("Dock", "OBD open", ignored);
                        }
                    } else if (it.action.length() > 0) {
                        Launch.carplay(LauncherActivity.this);
                        overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_fade_out);
                    } else {
                        openApp(it.pkg, Math.round(pager.position()));
                    }
                }

                @Override
                public void onLong(AppsGridPage.Item it) {
                    appMenu(it);
                }

                @Override
                public void onAppsChanged() {
                    rebuildAppPages();
                }

                @Override
                public void onEdgeDrag(AppsGridPage.Item it, int direction) {
                    moveItemToAdjacentPage(it, direction);
                }

                @Override
                public void onRemoveFromGrid(String pkg) {
                    setAppOnGrid(pkg, false);
                }
            });
            appPages.add(p);
        }
        float pos = pager.position();
        assemblePages();
        pager.setPosition(Math.min(pos, pager.count() - 1));
        // tryb edycji musi przetrwac odbudowe stron (usuwanie / przestawanie ikon):
        // nowe strony dostaja go z powrotem, dopoki nie zamknie go tap w tlo
        if (EditModeController.get().isEditing()) {
            for (AppsGridPage p : appPages) {
                p.enterEditMode();
            }
        }
    }

    // Edge scrolling: ikona przeciagnieta do krawedzi strony trafia na sasiednia strone
    private void moveItemToAdjacentPage(AppsGridPage.Item it, int direction) {
        if (it == null || direction == 0) return;
        int idx = items.indexOf(it);
        if (idx < 0) return;
        int target = Math.round(pager.position()) + direction;
        if (target < appsStart || target >= appsStart + appPages.size()) return;
        items.remove(idx);
        int insert = Math.max(appsStart, Math.min(target, items.size()));
        items.add(insert, it);
        // utrwal kolejnosc tak jak przy dragowaniu w obrebie strony
        try {
            List<AppEntry> ordered = new ArrayList<AppEntry>();
            java.util.LinkedHashSet<Integer> used = new java.util.LinkedHashSet<Integer>();
            for (AppsGridPage.Item i2 : items) {
                if (i2.entry != null) {
                    ordered.add(i2.entry);
                    used.add((int) i2.entry.id);
                }
            }
            for (AppEntry e : Prefs.apps(this)) {
                if (!used.contains((int) e.id)) ordered.add(e);
            }
            Prefs.saveApps(this, ordered);
        } catch (Throwable ignored) {
        }
        rebuildAppPages();
        pager.goTo(Math.min(target, pager.count() - 1), true);
    }

    private void appMenu(final AppsGridPage.Item it) {
        if (it.settings || it.action.length() > 0) return;
        final String[] options = {
                "Usuń z siatki aplikacji",
                "Informacje o aplikacji",
                "Usuń aplikację"
        };
        Popups.menu(this, it.label, options, new Popups.OnSel() {
            @Override
            public void on(int idx) {
                switch (idx) {
                    case 0:
                        setAppOnGrid(it.pkg, false);
                        break;
                    case 1:
                        startSystemSmooth(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + it.pkg)));
                        break;
                    case 2:
                        uninstallApp(it.pkg);
                        break;
                }
            }
        });
    }

    // każde uruchomienie z launchera trafia do statystyk; po Home wracamy na tę samą stronę
    private void openApp(String pkg, int fromPage) {
        // narzedzia systemowe z Biblioteki (dock:...) - Ustawienia, OBD, CarPlay
        if (pkg != null && pkg.startsWith("dock:")) {
            if ("dock:settings".equals(pkg)) {
                Prefs.recordPkg(this, pkg);   // liczy sie do 4 ikon doka
                openSettings();
            } else if ("dock:obd".equals(pkg)) {
                Prefs.recordPkg(this, pkg);   // liczy sie do 4 ikon doka
                try {
                    startActivity(new Intent(this, pl.dock.widget.ObdDiscoveryActivity.class));
                    overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_fade_out);
                } catch (Throwable t) {
                    Toast.makeText(this, "Nie można otworzyć OBD", Toast.LENGTH_SHORT).show();
                }
            } else {
                Launch.carplay(this);
                overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_fade_out);
            }
            return;
        }
        try {
            Intent i = pkg == null || pkg.length() == 0 ? null
                    : getPackageManager().getLaunchIntentForPackage(pkg);
            if (i == null) {
                Toast.makeText(this, "Nie można otworzyć tej aplikacji", Toast.LENGTH_SHORT).show();
                return;
            }
            Prefs.recordPkg(this, pkg);
            returnPage = fromPage;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_fade_out);
            lastDockSig = "";
        } catch (Throwable t) {
            Toast.makeText(this, "Nie można otworzyć tej aplikacji", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------- dok a ekrany systemowe
    // Android celowo blokuje rysowanie ponad bezpiecznymi ekranami (Ustawienia,
    // odinstalowanie, uprawnienia) - tego nie da sie obejsc bez roota. Zamiast
    // naglego znikania doka robiimy zamierzone, plynne przejscie: dok "celowo"
    // wyjezdza za krawedz przy otwieraniu ekranu systemowego i plynnie wraca
    // po powrocie (iluzja intencjonalnego designu jak w iOS).
    private void dockSlideIn() {
        if (dock == null) return;
        dock.animate().cancel();
        if (Prefs.reduceMotion(this)) {
            dock.setTranslationX(0f);
            dock.setAlpha(1f);
            return;
        }
        boolean left = Ui.dockLeft(this);
        dock.setTranslationX(left ? -Ui.dp(this, 26) : Ui.dp(this, 26));
        dock.setAlpha(0.4f);
        dock.animate().translationX(0f).alpha(1f).setDuration(320)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f))
                .start();
    }

    // NAPRAWA ("Odinstaluj" / "Informacje o aplikacji" nie dzialaly z menu):
    // ekran systemowy startowal dopiero PO zakonczeniu 200 ms animacji doka
    // (withEndAction). Zamkniecie menu zwracalo fokus launcherowi ->
    // onWindowFocusChanged(true) -> dockSlideIn() -> animate().cancel()
    // przerywalo te animacje w trakcie, wiec withEndAction nigdy nie odpalalo
    // i zadne systemowe okno sie nie otwieralo. Teraz intencja startuje
    // NATYCHMIAST; animacja doka jest czysto kosmetyczna, a po powrocie
    // fokusu dok plynnie wraca (dockSlideIn).

    /** Ekran systemowy (Ustawienia itp.): start natychmiast + kosmetyczne ukrycie doka. */
    private void startSystemSmooth(final Intent i) {
        try {
            startActivity(i);
            overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_fade_out);
            slideDockAside();
        } catch (Throwable t) {
            android.util.Log.w("Dock", "startSystemSmooth", t);
            Toast.makeText(this, "Nie mozna otworzyc", Toast.LENGTH_SHORT).show();
        }
    }

    /** Jak startSystemSmooth, ale z wynikiem (np. odinstalowanie aplikacji). */
    private boolean startSystemSmoothForResult(final Intent i, final int req) {
        try {
            startActivityForResult(i, req);
            overridePendingTransition(R.anim.cp_fade_in, R.anim.cp_fade_out);
            slideDockAside();
            return true;
        } catch (Throwable t) {
            android.util.Log.w("Dock", "startSystemSmoothForResult", t);
            return false;
        }
    }

    /** Kosmetyczne wysuniecie doka za krawedz (nie blokuje otwierania ekranu). */
    private void slideDockAside() {
        if (dock == null || Prefs.reduceMotion(this)) return;
        final boolean left = Ui.dockLeft(this);
        dock.animate().cancel();
        dock.animate()
                .translationX(left ? -Ui.dp(this, 40) : Ui.dp(this, 40))
                .alpha(0f)
                .setDuration(200)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .start();
    }

    /** Odinstalowanie aplikacji: systemowy deinstalator + zapasowa intencja ACTION_DELETE. */
    private void uninstallApp(String pkg) {
        if (pkg == null || pkg.length() == 0) return;
        try {
            Intent i = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
            i.setData(Uri.parse("package:" + pkg));
            i.putExtra(Intent.EXTRA_RETURN_RESULT, true);
            if (startSystemSmoothForResult(i, AppsGridPage.REQ_UNINSTALL)) return;
        } catch (Throwable ignored) {
        }
        try {
            Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg));
            if (!startSystemSmoothForResult(i, AppsGridPage.REQ_UNINSTALL)) {
                Toast.makeText(this, "Nie można otworzyć deinstalatora", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable t) {
            android.util.Log.w("Dock", "uninstallApp", t);
            Toast.makeText(this, "Nie można otworzyć deinstalatora", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        try {
            startActivity(new Intent(this, ConfigActivity.class));
            overridePendingTransition(R.anim.cp_slide_in, R.anim.cp_fade_out);
        } catch (Throwable t) {
            android.util.Log.w("Dock", "openSettings", t);
            Toast.makeText(this, "Nie można otworzyć ustawień", Toast.LENGTH_LONG).show();
        }
    }

    // ---------------------------------------------------------------- centrum łączności (szkło)
    // Liquid glass: jeden snapshot rozmycia dopiero po osiadnieciu strony
    private void refreshGlass() {
        if (root == null || dock == null) return;
        if (!"glass".equals(Prefs.style(this))) return;
        try {
            Bitmap blur = LiveBlur.captureAndBlur(root, root.getWidth(), root.getHeight());
            dock.setGlassBitmap(blur);
            dock.setGlassOrigin(dock.getLeft());
            dock.setRootSize(root.getWidth(), root.getHeight());
        } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);}
    }

    private void toggleOverlay() {
        if (overlay != null) {
            hideOverlay(true);
        } else {
            showOverlayAt(root != null ? root.getWidth() / 2f : 0f);
        }
    }

    private void showOverlay() {
        showOverlayAt(root != null ? root.getWidth() / 2f : 0f);
    }

    private void showOverlayAt(float gestureX) {
        if (overlay != null) return;
        Bitmap snap = null;
        try {
            int k = 6;
            snap = Bitmap.createBitmap(Math.max(1, root.getWidth() / k),
                    Math.max(1, root.getHeight() / k), Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(snap);
            cv.scale(1f / k, 1f / k);
            bg.draw(cv);
            cv.save();
            cv.translate(pager.getLeft(), pager.getTop());
            pager.draw(cv);
            cv.restore();
            Blur.blur(snap, 3, 3);
        } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);
        }
        overlay = new FrameLayout(this);
        // UWAGA: overlay NIE ma wlasnej warstwy. Potwierdzona konfiguracja (PR #14,
        // commit b21e953 - uzytkownik potwierdzil dzialajaca tresc panelu) miala
        // warstwe SOFTWARE TYLKO na ConnectivityPanel. Dodana pozniej warstwa na
        // overlayu (PR #46) tworzyla DWIe zagniezdzone warstwy - i panel znów byl
        // pusty. Warstwa tylko na panelu = sciezka frameworka, ktora dziala.
        if (snap != null) {
            ImageView glass = new ImageView(this);
            glass.setScaleType(ImageView.ScaleType.FIT_XY);
            glass.setImageBitmap(snap);
            overlay.addView(glass, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }
        View tint = new View(this);
        tint.setBackgroundColor(night ? 0x40000000 : 0x40FFFFFF);
        overlay.addView(tint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideOverlay(true);
            }
        });
        panel = new ConnectivityPanel(this, night, new ConnectivityPanel.Host() {
            @Override
            public void close() {
                hideOverlay(true);
            }

            @Override
            public void modeChanged() {
                night = Look.night(LauncherActivity.this);
                smoothThemeChange();
            }
        });
        boolean left = Ui.dockLeft(this);
        int dockPx = Ui.dp(this, Ui.DOCK_OFFSET_DP);
        // szerokość panelu: min 240dp, maks 560dp i nigdy szersza niż ekran - 24dp
        int panelW = Math.max(Ui.dp(this, 240),
                Math.min(Ui.dp(this, 560), Ui.windowWidth(this) - Ui.dp(this, 24)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                panelW, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        // pozycja X: środek panelu nad palcem (gdy follow) z clampingiem;
        // logika w czystej klasie OverlayTrigger pokryta testami jednostkowymi
        int panelX = OverlayTrigger.panelX(gestureX, panelW, root.getWidth(),
                dockPx, left, Prefs.overlayFollowGesture(this), Ui.dp(this, 8));
        lp.leftMargin = panelX;
        lp.topMargin = Ui.dp(this, 12);
        overlay.addView(panel, lp);
        // defensive: dodajemy pod dok (nad resztą). indexOfChild może dać -1
        // w krótkim oknie po odbudowie UI - wtedy zwykły addView na wierzch.
        int underDock = root.indexOfChild(dock);
        if (underDock >= 0) {
            root.addView(overlay, underDock, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            root.addView(overlay, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        // NAPRAWA (potwierdzona na urzadzeniu, PR #14, commit b21e953 - uzytkownik
        // potwierdzil, ze tresc panelu sie rysuje): DOKLADNIE ta sekwencja wejscia -
        // fade overlay + animacja panelu (skala 0.92 -> 1, translateY 40 -> 0,
        // Overshoot) + withEndAction wymuszajacy re-layout i invalidate.
        // Komentarz z tamtej poprawki: "na MT8163 pierwszy pomiar panelu
        // w animowanej overlay potrafil wyjsc 0x0 i tresc nigdy sie nie rysowala" -
        // koncowy requestLayout()+invalidate() przebudowuje pomiar i rysunek,
        // dopiero wtedy custom Canvas views sie pojawiaja.
        // ⚠️ NIE usuwac animacji ani withEndAction - bez tego panel jest pusty.
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(Prefs.reduceMotion(this) ? 0 : 220).start();
        panel.setScaleX(0.92f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(Ui.dp(this, 40));
        panel.setPivotX(panelW / 2f);
        panel.setPivotY(0f);
        panel.animate().scaleX(1f).scaleY(1f).translationY(0f)
                .setDuration(Prefs.reduceMotion(this) ? 0 : 280)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f))
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        // w tym miejscu panel byl juz pierwszy raz wymierzony -
                        // wymuszamy pelny re-layout + invalidate (mechanizm naprawczy)
                        if (panel != null) {
                            panel.requestLayout();
                            panel.invalidate();
                        }
                        if (overlay != null) overlay.invalidate();
                    }
                })
                .start();
    }

    private void hideOverlay(boolean animate) {
        final FrameLayout ov = overlay;
        if (ov == null) return;
        overlay = null;
        panel = null;
        if (!animate || Prefs.reduceMotion(this)) {
            root.removeView(ov);
            return;
        }
        // Tylko fade overlay - żadnego scale/translate na panelu (zob. NAPRAWA
        // w showOverlayAt: animacje transformacyjne na panelu nie wyświeżają
        // poprawnie rysowania dzieci na MT8163 i po drodze psuły layout).
        ov.animate().alpha(0f).setDuration(180)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        root.removeView(ov);
                        ov.animate().setListener(null);
                    }
                }).start();
    }

    // ---------------------------------------------------------------- widżety
    private void loadWidgets() {
        if (stacksPage != null) stacksPage.clearWidgets();
        List<WidgetEntry> list = Prefs.widgets(this);
        if (!Prefs.widgetsSeeded(this)) {
            // domyślnie jak w CarPlay: zegar, pogoda, zachód słońca
            int[] ids = {-1, -3, -4};
            String[] wh = {"s1", "s2", "s3"};
            for (int i = 0; i < 3; i++) {
                WidgetEntry e = new WidgetEntry();
                e.id = ids[i];
                e.where = wh[i];
                list.add(e);
            }
            Prefs.saveWidgets(this, list);
            Prefs.setWidgetsSeeded(this);
        }
        List<WidgetEntry> keep = new ArrayList<WidgetEntry>();
        boolean changed = false;
        for (WidgetEntry e : list) {
            if (e.where.startsWith("s") && stacksPage == null) {
                keep.add(e);
                continue;
            }
            View v = createWidgetView(e);
            if (v == null) {
                changed = true;
                continue;
            }
            keep.add(e);
            if (e.where.startsWith("s")) {
                markGlass(v);
                stacksPage.addWidget(e, v);
            } else {
                v.setTag(e);
            }
        }
        if (changed) Prefs.saveWidgets(this, keep);
        if (stacksPage != null) stacksPage.applySelection();
        rebuildHome(keep);
    }

    private void rebuildHome(List<WidgetEntry> list) {
        View l = null;
        View rt = null;
        for (WidgetEntry e : list) {
            if ("hl".equals(e.where) && l == null) l = createWidgetView(e);
            if ("hr".equals(e.where) && rt == null) rt = createWidgetView(e);
        }
        final HomeSections.Callbacks cb = new HomeSections.Callbacks() {
            @Override
            public void onSlot(String where) {
                showWidgetPicker(where);
            }
        };
        if (l == null) {
            // Domyslnie lewy slot to mapa offline (jak mapa w CarPlay).
            // Przytrzymanie pozwala podmienic na inny widzet.
            final pl.dock.widget.maps.MapCard mc = new pl.dock.widget.maps.MapCard(this);
            mc.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View x) {
                    showWidgetPicker("hl");
                    return true;
                }
            });
            l = mc;
        }
        if (rt == null) rt = new HomeSections.Slot(this, "hr", cb);
        for (View v : new View[]{l, rt}) {
            if (v.getTag() instanceof WidgetEntry) {
                final WidgetEntry we = (WidgetEntry) v.getTag();
                v.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View x) {
                        widgetMenu(we);
                        return true;
                    }
                });
            }
        }
        home.setViews(l, rt, nowCard);
        home.setNight(night);
    }

    private View createWidgetView(WidgetEntry e) {
        try {
            View v = null;
            if (e.id == -1) {
                v = new ClockCard(this);
            } else if (e.id == -2) {
                NowPlayingCard n = new NowPlayingCard(this);
                n.setFrame(true, false);
                v = n;
            } else if (e.id == -3) {
                v = new CardWidgets.WeatherCard(this);
            } else if (e.id == -4) {
                v = new CardWidgets.SunCard(this);
            } else if (e.id == -5) {
                v = new CardWidgets.DateCard(this);
            } else if (e.id == -6) {
                v = new CardWidgets.CalendarCard(this);
            } else if (e.id == -7) {
                v = new CardWidgets.WorldClockCard(this);
            } else if (e.id == -8) {
                v = new pl.dock.widget.maps.MapCard(this);
            }
            if (v != null) {
                ((NightAware) v).setNight(night);
                v.setTag(e);
                return v;
            }
            AppWidgetProviderInfo info = awm.getAppWidgetInfo(e.id);
            if (info == null) return null;
            AppWidgetHostView hv = host.createView(this, e.id, info);
            hv.setTag(e);
            return hv;
        } catch (Throwable t) {
            return null;
        }
    }

    private void showWidgetPicker(final String where) {
        if (where.startsWith("s") && stacksPage == null) return;
        PackageManager pm = getPackageManager();
        final List<String> labels = new ArrayList<String>();
        final int[] builtIns = {-1, -2, -3, -4, -5, -6, -7, -8};
        String[] bn = {"Zegar analogowy", "Odtwarzane", "Pogoda", "Zachód słońca", "Data",
                "Kalendarz", "Zegary świata", "Mapy (offline)"};
        for (String s : bn) labels.add(s);
        final PackageManager pmf = pm;
        final List<AppWidgetProviderInfo> sorted =
                new ArrayList<AppWidgetProviderInfo>(awm.getInstalledProviders());
        Collections.sort(sorted, new Comparator<AppWidgetProviderInfo>() {
            @Override
            public int compare(AppWidgetProviderInfo a, AppWidgetProviderInfo b) {
                return a.loadLabel(pmf).compareToIgnoreCase(b.loadLabel(pmf));
            }
        });
        for (AppWidgetProviderInfo i : sorted) {
            labels.add(i.loadLabel(pm) + "  ·  " + i.provider.getPackageName());
        }
        Popups.menu(this, "Dodaj widżet", labels.toArray(new String[0]), new Popups.OnSel() {
            @Override
            public void on(int idx) {
                if (idx < builtIns.length) {
                    addBuiltin(builtIns[idx], where);
                } else {
                    beginBind(sorted.get(idx - builtIns.length), where);
                }
            }
        });
    }

    private void addBuiltin(int id, String where) {
        List<WidgetEntry> list = Prefs.widgets(this);
        for (WidgetEntry e : list) {
            if (e.id == id) {
                Toast.makeText(this, "Ten widżet jest już dodany", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        WidgetEntry e = new WidgetEntry();
        e.id = id;
        e.where = where;
        list.add(e);
        Prefs.saveWidgets(this, list);
        attach(e, list);
    }

    private void attach(WidgetEntry e, List<WidgetEntry> list) {
        if (e.where.startsWith("s")) {
            View v = createWidgetView(e);
            if (v == null || stacksPage == null) return;
            markGlass(v);
            stacksPage.addWidget(e, v);
            stacksPage.applySelection();
        } else {
            rebuildHome(list);
        }
    }

    // widzety na stronie widzetow (skrajnie lewy ekran) sa przezroczyste ("szklo"):
    // tapeta prześwituje zamiast nieprzezroczystej karty; ekran glowny bez zmian
    private void markGlass(View v) {
        if (v instanceof GlassAware) ((GlassAware) v).setGlass(true);
    }

    private void beginBind(AppWidgetProviderInfo info, String where) {
        pendingWhere = where;
        pendingId = host.allocateAppWidgetId();
        boolean ok = false;
        try {
            ok = awm.bindAppWidgetIdIfAllowed(pendingId, info.provider);
        } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);
        }
        if (ok) {
            afterBind();
        } else {
            try {
                Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId);
                i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
                startActivityForResult(i, REQ_BIND);
            } catch (Throwable t) {
                Toast.makeText(this, "System nie pozwala dodać tego widżetu", Toast.LENGTH_LONG).show();
                cancelPending();
            }
        }
    }

    private void afterBind() {
        AppWidgetProviderInfo info = awm.getAppWidgetInfo(pendingId);
        if (info != null && info.configure != null) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, pendingId, 0, REQ_CONFIG, null);
                return;
            } catch (Throwable t) {
                // brak konfiguracji = dodajemy bez niej
            }
        }
        finishAdd();
    }

    private void finishAdd() {
        WidgetEntry e = new WidgetEntry();
        e.id = pendingId;
        e.where = pendingWhere;
        List<WidgetEntry> list = Prefs.widgets(this);
        list.add(e);
        Prefs.saveWidgets(this, list);
        attach(e, list);
        pendingId = -1;
    }

    private void cancelPending() {
        if (pendingId >= 0) {
            try {
                host.deleteAppWidgetId(pendingId);
            } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);
            }
        }
        pendingId = -1;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_BIND) {
            if (res == RESULT_OK && pendingId >= 0) {
                afterBind();
            } else {
                cancelPending();
            }
        } else if (req == REQ_CONFIG) {
            if (res == RESULT_OK && pendingId >= 0) {
                finishAdd();
            } else {
                cancelPending();
            }
        } else if (req == AppsGridPage.REQ_UNINSTALL) {
            // refresh apps after possible uninstall
            rebuildAppPages();
        }
    }

    private void widgetMenu(final WidgetEntry e) {
        final boolean stack = e.where.startsWith("s") && stacksPage != null;
        final boolean multi = stack && stacksPage.count(e.where) > 1;
        List<String> labels = new ArrayList<String>();
        if (multi) labels.add("Pokaż następny widżet w kolumnie");
        labels.add("Usuń widżet");
        Popups.menu(this, "Widżet", labels.toArray(new String[0]), new Popups.OnSel() {
            @Override
            public void on(int i) {
                if (multi && i == 0) {
                    stacksPage.cycle(e.where);
                } else {
                    Popups.confirm(LauncherActivity.this, "Usunąć widżet?", "", "Usuń",
                            new Runnable() {
                                @Override
                                public void run() {
                                    removeWidget(e);
                                }
                            });
                }
            }
        });
    }

    private void removeWidget(WidgetEntry e) {
        if (e.id > 0) {
            try {
                host.deleteAppWidgetId(e.id);
            } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);
            }
        }
        List<WidgetEntry> list = Prefs.widgets(this);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id == e.id) {
                list.remove(i);
                break;
            }
        }
        Prefs.saveWidgets(this, list);
        if (e.where.startsWith("s") && stacksPage != null) {
            stacksPage.removeWidget(e.id);
        } else {
            rebuildHome(list);
        }
    }

    // ---------------------------------------------------------------- pasek głośności
    private static class VolumeHud extends View {
        private float value;
        private String title = "";
        private boolean wide;
        private final android.graphics.Paint p = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private final Runnable hide = new Runnable() {
            @Override
            public void run() {
                animate().alpha(0f).setDuration(200).start();
                wide = false;
            }
        };

        VolumeHud(Context c) {
            super(c);
            setAlpha(0f);
        }

        void show(float v, String title) {
            value = v;
            this.title = title;
            wide = title.length() > 0;
            setAlpha(1f);
            invalidate();
            removeCallbacks(hide);
            postDelayed(hide, 1800);
        }

        @Override
        protected void onDraw(Canvas cv) {
            float d = getResources().getDisplayMetrics().density;
            float sd = getResources().getDisplayMetrics().scaledDensity;
            float w = wide ? getWidth() : getWidth() * 0.62f;
            float x0 = (getWidth() - w) / 2f;
            r.set(x0, 0, x0 + w, getHeight());
            p.setStyle(android.graphics.Paint.Style.FILL);
            p.setColor(0xEE131418);
            cv.drawRoundRect(r, getHeight() / 2f, getHeight() / 2f, p);
            float barL = x0 + 64f * d;
            float barR = x0 + w - 28f * d;
            float cy = wide ? getHeight() * 0.62f : getHeight() / 2f;
            if (wide) {
                p.setColor(0xFFFFFFFF);
                p.setTextSize(15f * sd);
                p.setTextAlign(android.graphics.Paint.Align.LEFT);
                cv.drawText(android.text.TextUtils.ellipsize(title, new android.text.TextPaint(p),
                        barR - barL, android.text.TextUtils.TruncateAt.END).toString(), barL,
                        getHeight() * 0.36f, p);
            }
            Bitmap mark = Icons.carplayMark(64, 0xFFFFFFFF);
            r.set(x0 + 22f * d, getHeight() / 2f - 12f * d, x0 + 46f * d, getHeight() / 2f + 12f * d);
            cv.drawBitmap(mark, null, r, p);
            p.setColor(0x44FFFFFF);
            r.set(barL, cy - 3f * d, barR, cy + 3f * d);
            cv.drawRoundRect(r, 3f * d, 3f * d, p);
            p.setColor(0xFFFFFFFF);
            r.set(barL, cy - 3f * d, barL + (barR - barL) * value, cy + 3f * d);
            cv.drawRoundRect(r, 3f * d, 3f * d, p);
        }
    }

    // ---------- stan stron / integracja zewnętrzna ----------
    public static final String ACTION_NAVIGATE = "pl.dock.widget.NAVIGATE";
    public static final String ACTION_STATE_QUERY = "pl.dock.widget.STATE_QUERY";
    public static final String ACTION_STATE = "pl.dock.widget.STATE";

    private static java.lang.ref.WeakReference<LauncherActivity> currentRef;
    private boolean restoredOnce;
    private BroadcastReceiver navReceiver;

    private final AppListWatcher.Listener appsListener = new AppListWatcher.Listener() {
        @Override
        public void onAppsChanged() {
            loadApps();
        }
    };

    public static LauncherActivity current() {
        return currentRef == null ? null : currentRef.get();
    }


    /** Liczba stron panelu BEZ strony wyszukiwania (ta nigdy nie liczy sie do kropek). */
    private int panelPageCount() {
        int n = pager == null ? 1 : pager.count();
        return Math.max(1, n - 1);
    }

    private void saveLastPage(int index) {
        if (stacksPage != null && index == 0) {
            Prefs.setLastPage(this, Prefs.PAGE_WIDGETS);
        } else if (index == homeIdx) {
            Prefs.setLastPage(this, Prefs.PAGE_HOME);
        } else if (index >= appsStart) {
            Prefs.setLastPage(this, Prefs.PAGE_APPS);
        }
    }

    private void restoreLastPage() {
        if (pager == null) return;
        int widgetsIdx = (stacksPage != null) ? 0 : -1;
        int appsIdx = (appPages.isEmpty()) ? -1 : appsStart;
        int idx = Prefs.lastPageIndex(this, widgetsIdx, homeIdx, appsIdx);
        if (idx < 0) idx = homeIdx;
        pager.setPosition(idx);
    }

    public void fillStateBundle(android.os.Bundle b) {
        int pos = pager == null ? homeIdx : Math.round(pager.position());
        String page = Prefs.PAGE_HOME;
        if (stacksPage != null && pos == 0) page = Prefs.PAGE_WIDGETS;
        else if (pos >= appsStart) page = Prefs.PAGE_APPS;
        b.putString("page", page);
        b.putInt("page_index", pos);
        b.putInt("home_index", homeIdx);
        b.putInt("apps_index", appsStart);
        b.putInt("pages_total", pager == null ? 0 : pager.count());
    }

    public void navigateToPage(String page) {
        if (pager == null) return;
        if (Prefs.PAGE_APPS.equals(page)) pager.goTo(appsStart, true);
        else if (Prefs.PAGE_WIDGETS.equals(page) && stacksPage != null) pager.goTo(0, true);
        else pager.goTo(homeIdx, true);
    }

    private void handleExternalNavigate(Intent i) {
        if (i == null) return;
        String caller = i.getStringExtra("caller");
        String page = i.getStringExtra("page");
        boolean save = i.getBooleanExtra("save_return", true);
        if (save && caller != null) Prefs.setExternalCaller(this, caller);
        navigateToPage(page);
    }

    private void broadcastState(String requester) {
        Intent r = new Intent(ACTION_STATE);
        if (requester != null) r.setPackage(requester);
        fillStateBundle(r.getExtras() == null ? new android.os.Bundle() : r.getExtras());
        android.os.Bundle b = new android.os.Bundle();
        fillStateBundle(b);
        r.putExtras(b);
        r.putExtra("last_page", Prefs.lastPage(this));
        r.putExtra("external_caller", Prefs.externalCaller(this));
        sendBroadcast(r);
    }

    private void registerNav() {
        if (navReceiver != null) return;
        navReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                String a = i.getAction();
                if (ACTION_NAVIGATE.equals(a)) handleExternalNavigate(i);
                else if (ACTION_STATE_QUERY.equals(a)) broadcastState(i.getStringExtra("caller"));
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_NAVIGATE);
        f.addAction(ACTION_STATE_QUERY);
        registerReceiver(navReceiver, f);
    }

    private void unregisterNav() {
        if (navReceiver == null) return;
        try {
            unregisterReceiver(navReceiver);
        } catch (Throwable ignored) { android.util.Log.w("Dock", "LauncherActivity", ignored);}
        navReceiver = null;
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentRef != null && currentRef.get() == this) currentRef = null;
    }
}






