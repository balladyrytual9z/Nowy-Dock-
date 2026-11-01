
package pl.dock.widget;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

// Dane odtwarzania z aplikacji CarPlay (DiPlay publikuje sesjÄ multimedialnÄ z metadanymi
// i okÅadkÄ). KolejnoÅÄ ÅºrÃ³deÅ: sesja multimedialna > klasyczne broadcasty odtwarzacza.
// DziaÅa tylko, gdy ktoÅ jest podÅÄczony (widÅ¼et / ekran odtwarzania), wiÄc nie kosztuje w tle.
public class MediaHub {

    public static class Track {
        public String pkg = "";
        public String title = "";
        public String artist = "";
        public String album = "";
        public Bitmap art;
        public long duration = -1;
        public int state = PlaybackState.STATE_NONE;
        public long pos;
        public long posTime;      // elapsedRealtime w chwili odczytu pos
        public float speed = 1f;
        public long actions;
        public boolean hasSession;
        public String source = "";
        public List<PlaybackState.CustomAction> custom = new ArrayList<PlaybackState.CustomAction>();

        public boolean playing() {
            return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING;
        }

        public boolean hasData() {
            return title.length() > 0 || artist.length() > 0;
        }

        public long position() {
            if (!playing()) return pos;
            long p = pos + (long) ((SystemClock.elapsedRealtime() - posTime) * speed);
            return duration > 0 ? Math.min(p, duration) : p;
        }
    }

    public interface Listener {
        void onTrack(Track t);
    }

    private static final MediaHub I = new MediaHub();

    public static MediaHub get() {
        return I;
    }

    private Context app;
    private final ArrayList<Listener> ls = new ArrayList<Listener>();
    private int refs;
    private MediaSessionManager msm;
    private ComponentName cn;
    private MediaController ctl;
    private Track track = new Track();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean accessOk = true;

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> controllers) {
                    pick(controllers);
                }
            };

    // --- Retry okladki: gdy sesja zywa, ale okladki/metadanych brak (np. powrot
    // do auta po przerwie), co 3 s dopytujemy metadane az aplikacja je wypchnie.
    private int artTries;
    private final Runnable artPoll = new Runnable() {
        @Override
        public void run() {
            if (ctl == null || refs <= 0) {
                artTries = 0;
                return;
            }
            if (++artTries > 20) {   // max ~60 s, potem czekamy na zdarzenie sesji
                artTries = 0;
                return;
            }
            readFrom(ctl);
            scheduleArtPoll(3000L);
        }
    };

    private void scheduleArtPoll(long delay) {
        main.removeCallbacks(artPoll);
        if (ctl != null && refs > 0 && (!track.hasData() || track.art == null)) {
            main.postDelayed(artPoll, delay);
        }
    }

    private final MediaController.Callback ctlCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata m) {
            readFrom(ctl);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            readFrom(ctl);
        }

        @Override
        public void onSessionDestroyed() {
            ctl = null;
            // Zmiana: Podtrzymujemy dane o utworze i okÅadce,
            // jedynie oznaczamy brak sesji i zatrzymujemy status odtwarzania.
            if (track != null) {
                track.state = PlaybackState.STATE_NONE;
                track.hasSession = false;
            } else {
                track = new Track();
            }
            fire();
        }
    };

    // klasyczne broadcasty odtwarzaczy (czÄÅÄ aplikacji w radiach wysyÅa tylko je)
    private BroadcastReceiver classic;

    // ---------------------------------------------------------------- dostÄp

    public static boolean accessGranted(Context c) {
        try {
            String s = Settings.Secure.getString(c.getContentResolver(),
                    "enabled_notification_listeners");
            return s != null && s.contains(c.getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean hasAccess() {
        return accessOk;
    }

    // czy dana aplikacja ma teraz aktywnÄ sesjÄ multimedialnÄ
    public static boolean hasSession(Context c, String pkg) {
        try {
            MediaSessionManager m = (MediaSessionManager)
                    c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> l = m.getActiveSessions(
                    new ComponentName(c, DockNotificationListener.class));
            if (l == null) return false;
            for (MediaController mc : l) {
                if (pkg.equals(mc.getPackageName())) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ---------------------------------------------------------------- cykl Å¼ycia

    public synchronized void attach(Context c, Listener l) {
        app = c.getApplicationContext();
        if (!ls.contains(l)) ls.add(l);
        if (refs++ == 0) start();
        l.onTrack(track);
    }

    public synchronized void detach(Listener l) {
        ls.remove(l);
        if (--refs <= 0) {
            refs = 0;
            stop();
        }
    }

    private void start() {
        msm = (MediaSessionManager) app.getSystemService(Context.MEDIA_SESSION_SERVICE);
        cn = new ComponentName(app, DockNotificationListener.class);
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn, main);
        } catch (Throwable t) {
            accessOk = false;
        }
        if (classic == null) {
            classic = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent i) {
                    onClassic(i);
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction("com.android.music.metachanged");
            f.addAction("com.android.music.playstatechanged");
            f.addAction("com.android.music.playbackcomplete");
            try {
                app.registerReceiver(classic, f);
            } catch (Throwable ignored) {
            }
        }
        refresh();
    }

    private void stop() {
        main.removeCallbacks(artPoll);
        artTries = 0;
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(sessionsListener);
        } catch (Throwable ignored) {
        }
        if (ctl != null) {
            try {
                ctl.unregisterCallback(ctlCallback);
            } catch (Throwable ignored) {
            }
            ctl = null;
        }
        if (classic != null) {
            try {
                app.unregisterReceiver(classic);
            } catch (Throwable ignored) {
            }
            classic = null;
        }
    }

    // woÅane z NotificationListenerService
    public void onListenerEvent() {
        if (refs > 0) {
            main.post(new Runnable() {
                @Override
                public void run() {
                    refresh();
                }
            });
        }
    }

    // ---------------------------------------------------------------- odczyt

    private String preferredPkg() {
        String p = Prefs.mediaPkg(app);
        return p.length() > 0 ? p : Prefs.diplayPkg(app);
    }

    private void refresh() {
        try {
            List<MediaController> l = msm.getActiveSessions(cn);
            accessOk = true;
            pick(l);
        } catch (Throwable t) {
            accessOk = false;
            fire();
        }
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            String want = preferredPkg();
            for (MediaController mc : list) {
                if (want.equals(mc.getPackageName())) {
                    best = mc;
                    break;
                }
            }
            if (best == null) {
                for (MediaController mc : list) {
                    PlaybackState ps = mc.getPlaybackState();
                    if (ps != null && ps.getState() == PlaybackState.STATE_PLAYING) {
                        best = mc;
                        break;
                    }
                }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        if (best != ctl) {
            if (ctl != null) {
                try {
                    ctl.unregisterCallback(ctlCallback);
                } catch (Throwable ignored) {
                }
            }
            ctl = best;
            if (ctl != null) ctl.registerCallback(ctlCallback, main);
        }
        if (ctl == null) {
            // Zmiana: JeÅli kontroler zniknÄÅ (np. po wybudzeniu tabletu, przed peÅnym
            // poÅÄczeniem CarPlay), podtrzymujemy ostatnio znane metadane i okÅadkÄ.
            if (track != null) {
                track.state = PlaybackState.STATE_NONE;
                track.hasSession = false;
            } else {
                track = new Track();
            }
            fire();
        } else {
            readFrom(ctl);
        }
    }

    private static String str(MediaMetadata m, String k) {
        String s = m.getString(k);
        return s == null ? "" : s;
    }

    private void readFrom(MediaController c) {
        if (c == null) return;
        Track t = new Track();
        t.pkg = c.getPackageName();
        t.hasSession = true;
        t.source = "sesja";
        MediaMetadata m = c.getMetadata();
        if (m != null) {
            t.title = str(m, MediaMetadata.METADATA_KEY_TITLE);
            if (t.title.length() == 0) t.title = str(m, MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            t.artist = str(m, MediaMetadata.METADATA_KEY_ARTIST);
            if (t.artist.length() == 0) t.artist = str(m, MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            if (t.artist.length() == 0) {
                t.artist = str(m, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE);
            }
            t.album = str(m, MediaMetadata.METADATA_KEY_ALBUM);
            long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            t.duration = dur > 0 ? dur : -1;
            Bitmap a = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (a == null) a = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if (a == null) a = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            t.art = shrink(a);
        }

        // --- NOWY KOD: Podtrzymywanie danych z poprzedniej sesji (Wake-up fix) ---
        if (track != null && track.hasData()) {
            // JeÅli nowa sesja po wybudzeniu zaraportowaÅa puste metadane,
            // przywracamy zapisany tytuÅ, wykonawcÄ i album.
            if (t.title.length() == 0) {
                t.title = track.title;
                t.artist = track.artist;
                t.album = track.album;
                t.duration = track.duration;
            }
            // JeÅli tytuÅ jest zgodny, ale aplikacja w tle jeszcze nie wypchnÄÅa
            // nowej okÅadki (t.art == null), wstrzykujemy okÅadkÄ zachowanÄ w pamiÄci.
            if (t.art == null && t.title.equals(track.title) && track.art != null) {
                t.art = track.art;
            }
        }
        // --- KONIEC NOWEGO KODU ---

        PlaybackState ps = c.getPlaybackState();
        if (ps != null) {
            t.state = ps.getState();
            t.pos = ps.getPosition();
            t.posTime = SystemClock.elapsedRealtime();
            t.speed = ps.getPlaybackSpeed();
            t.actions = ps.getActions();
            if (ps.getCustomActions() != null) t.custom.addAll(ps.getCustomActions());
        }
        track = t;
        fire();
    }

    // okÅadka moÅ¼e byÄ duÅ¼a; trzymamy kopiÄ do 512 px (oszczÄdnoÅÄ pamiÄci)
    private static Bitmap shrink(Bitmap b) {
        if (b == null) return null;
        int max = Math.max(b.getWidth(), b.getHeight());
        if (max <= 512) return b;
        float k = 512f / max;
        try {
            return Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k),
                    Math.round(b.getHeight() * k), true);
        } catch (Throwable t) {
            return b;
        }
    }

    private void onClassic(Intent i) {
        if (ctl != null) return;   // sesja ma pierwszeÅstwo
        String a = i.getAction();
        Track t = track.source.equals("broadcast") ? track : new Track();
        t.source = "broadcast";
        if (i.hasExtra("track")) t.title = String.valueOf(i.getStringExtra("track"));
        if (i.hasExtra("artist")) t.artist = String.valueOf(i.getStringExtra("artist"));
        if (i.hasExtra("album")) t.album = String.valueOf(i.getStringExtra("album"));
        if (i.hasExtra("playing")) {
            t.state = i.getBooleanExtra("playing", false) ? PlaybackState.STATE_PLAYING
                    : PlaybackState.STATE_PAUSED;
        }
        if ("com.android.music.playbackcomplete".equals(a)) t.state = PlaybackState.STATE_STOPPED;
        track = t;
        fire();
    }

    private void fire() {
        final Track t = track;
        // brak okladki/metadanych przy zywej sesji -> dopytuj w tle
        try {
            scheduleArtPoll(2000L);
        } catch (Throwable ignored) {
        }
        if (app != null) {
            try {
                StatusCenter.refreshCp(app);
            } catch (Throwable ignored) {
            }
        }
        for (Listener l : new ArrayList<Listener>(ls)) l.onTrack(t);
    }

    public Track current() {
        return track;
    }

    // ---------------------------------------------------------------- sterowanie

    private void key(int code) {
        if (app == null) return;
        try {
            AudioManager am = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            am.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        } catch (Throwable ignored) {
        }
    }

    public void toggle() {
        if (ctl != null) {
            try {
                if (track.playing()) {
                    ctl.getTransportControls().pause();
                } else {
                    ctl.getTransportControls().play();
                }
                return;
            } catch (Throwable ignored) {
            }
        }
        key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public void next() {
        if (ctl != null) {
            try {
                ctl.getTransportControls().skipToNext();
                return;
            } catch (Throwable ignored) {
            }
        }
        key(KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public void prev() {
        if (ctl != null) {
            try {
                ctl.getTransportControls().skipToPrevious();
                return;
            } catch (Throwable ignored) {
            }
        }
        key(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    private boolean has(long flag) {
        return track.actions == 0 || (track.actions & flag) != 0;   // 0 = odtwarzacz nie podaÅ listy
    }

    public boolean canPrev() {
        return has(PlaybackState.ACTION_SKIP_TO_PREVIOUS);
    }

    public boolean canNext() {
        return has(PlaybackState.ACTION_SKIP_TO_NEXT);
    }

    public boolean canToggle() {
        return has(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE)
                || (track.actions & (PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE)) != 0;
    }

    // przewijanie przy przytrzymaniu (do przodu / do tyÅu o delta ms)
    public void seekBy(long delta) {
        if (ctl == null) return;
        try {
            if (canSeek()) {
                long target = track.position() + delta;
                if (track.duration > 0) target = Math.min(target, track.duration - 500);
                seekTo(Math.max(0, target));
            } else if (delta > 0 && (track.actions & PlaybackState.ACTION_FAST_FORWARD) != 0) {
                ctl.getTransportControls().fastForward();
            } else if (delta < 0 && (track.actions & PlaybackState.ACTION_REWIND) != 0) {
                ctl.getTransportControls().rewind();
            }
        } catch (Throwable ignored) {
        }
    }

    public boolean canScan() {
        return canSeek() || (track.actions & (PlaybackState.ACTION_FAST_FORWARD
                | PlaybackState.ACTION_REWIND)) != 0;
    }

    public boolean canSeek() {
        return ctl != null && (track.actions & PlaybackState.ACTION_SEEK_TO) != 0;
    }

    public void seekTo(long ms) {
        if (!canSeek()) return;
        try {
            ctl.getTransportControls().seekTo(ms);
        } catch (Throwable ignored) {
        }
    }

    // wÅasna akcja odtwarzacza, ktÃ³rej nazwa zawiera sÅowo (np. "shuffle", "repeat", "like")
    public PlaybackState.CustomAction findCustom(String word) {
        for (PlaybackState.CustomAction a : track.custom) {
            String id = a.getAction() == null ? "" : a.getAction().toLowerCase();
            String nm = a.getName() == null ? "" : a.getName().toString().toLowerCase();
            if (id.contains(word) || nm.contains(word)) return a;
        }
        return null;
    }

    public void sendCustom(PlaybackState.CustomAction a) {
        if (ctl == null || a == null) return;
        try {
            ctl.getTransportControls().sendCustomAction(a, null);
        } catch (Throwable ignored) {
        }
    }

    // opis do diagnostyki
    public static String describe(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("DostÄp do powiadomieÅ: ").append(accessGranted(c) ? "wÅÄczony" : "WYÅÄCZONY").append("\n");
        b.append("UsÅuga nasÅuchu poÅÄczona: ").append(DockNotificationListener.connected()).append("\n");
        b.append("Pakiet DiPlay: ").append(Prefs.diplayPkg(c)).append("\n");
        try {
            MediaSessionManager m = (MediaSessionManager) c.getSystemService(Context.MEDIA_SESSION_SERVICE);
            List<MediaController> l = m.getActiveSessions(new ComponentName(c, DockNotificationListener.class));
            b.append("Aktywne sesje: ").append(l == null ? 0 : l.size()).append("\n");
            if (l != null) {
                for (MediaController mc : l) {
                    PlaybackState ps = mc.getPlaybackState();
                    MediaMetadata md = mc.getMetadata();
                    b.append(" - ").append(mc.getPackageName())
                            .append(" stan=").append(ps == null ? "?" : String.valueOf(ps.getState()))
                            .append(" tytuÅ=").append(md == null ? "-" : str(md, MediaMetadata.METADATA_KEY_TITLE))
                            .append("\n");
                }
            }
        } catch (Throwable t) {
            b.append("Sesje: brak dostÄpu (").append(t.getClass().getSimpleName()).append(")\n");
        }
        String[] n = DockNotificationListener.notificationText(Prefs.diplayPkg(c));
        b.append("Powiadomienie DiPlay: ")
                .append(n == null ? "brak" : n[0] + " / " + n[1]).append("\n");
        b.append("iPhone na USB (VID 0x05AC): ").append(CarplayState.appleUsbPresent(c)).append("\n");
        b.append("Stan CarPlay: ").append(CarplayState.label(CarplayState.read(c))).append("\n");
        return b.toString();
    }
}







