
package pl.dock.widget;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;

import org.json.JSONArray;

import java.util.List;

import pl.dock.widget.media.MediaCapabilities;
import pl.dock.widget.media.MediaCommand;
import pl.dock.widget.media.MediaSnapshot;

/**
 * ContentProvider for inter-app communication (pages, packages, media snapshot & commands).
 * Media Center and trusted callers talk to Dock through this contract.
 */
public class DockStateProvider extends ContentProvider {

    public static final String AUTHORITY = "pl.dock.widget.state";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (getContext() == null) return null;
        String caller = callerPackage();
        Bundle b = new Bundle();

        if ("get_state".equals(method)) {
            LauncherActivity la = LauncherActivity.current();
            b.putString("last_page", Prefs.lastPage(getContext()));
            b.putString("external_caller", Prefs.externalCaller(getContext()));
            if (la != null) {
                la.fillStateBundle(b);
            } else {
                b.putString("page", Prefs.lastPage(getContext()));
                b.putInt("page_index", -1);
                b.putInt("home_index", -1);
                b.putInt("apps_index", -1);
                b.putInt("pages_total", 0);
            }
            return b;
        }
        if ("get_selected".equals(method)) {
            List<String> sel = Prefs.selectedPkgs(getContext());
            JSONArray a = new JSONArray();
            for (String s : sel) a.put(s);
            b.putString("pkgs", a.toString());
            return b;
        }
        if ("navigate".equals(method)) {
            if (!trusted(caller)) return denied();
            LauncherActivity la = LauncherActivity.current();
            if (la == null) {
                if (arg != null) Prefs.setLastPage(getContext(), arg);
                return ok("saved");
            }
            if (arg != null) {
                Prefs.setExternalCaller(getContext(), caller == null ? "" : caller);
                la.navigateToPage(arg);
            }
            return ok("navigated");
        }
        if ("set_page".equals(method)) {
            if (!trusted(caller)) return denied();
            String page = extras == null ? null : extras.getString("page");
            if (page != null) Prefs.setLastPage(getContext(), page);
            return ok("saved");
        }

        // ---- Media Core contract (Dock 2.0) ----
        if (MediaCommand.GET_SNAPSHOT.equals(method)) {
            return mediaSnapshotBundle();
        }
        if (MediaCommand.GET_CAPABILITIES.equals(method)) {
            return mediaCapabilities().toBundle();
        }
        if (MediaCommand.PLAY.equals(method)
                || MediaCommand.PAUSE.equals(method)
                || MediaCommand.TOGGLE.equals(method)
                || MediaCommand.NEXT.equals(method)
                || MediaCommand.PREVIOUS.equals(method)
                || MediaCommand.SEEK.equals(method)
                || MediaCommand.SELECT_SOURCE.equals(method)) {
            if (!trusted(caller) && !isSelf(caller)) return denied();
            return handleMediaCommand(method, extras);
        }

        return null;
    }

    private boolean isSelf(String caller) {
        return caller != null && caller.equals(getContext().getPackageName());
    }

    private Bundle mediaSnapshotBundle() {
        MediaSnapshot snap = buildSnapshot();
        Bundle b = snap.toBundle();
        b.putBoolean("ok", true);
        return b;
    }

    private MediaSnapshot buildSnapshot() {
        MediaHub.Track t = MediaHub.get().current();
        if (t == null) t = new MediaHub.Track();
        long actions = t.actions;
        boolean canPlay = (actions & PlaybackState.ACTION_PLAY) != 0 || t.hasSession;
        boolean canPause = (actions & PlaybackState.ACTION_PAUSE) != 0 || t.hasSession;
        boolean canSeek = (actions & PlaybackState.ACTION_SEEK_TO) != 0;
        boolean canNext = (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0 || MediaHub.get().canNext();
        boolean canPrev = (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0 || MediaHub.get().canPrev();
        boolean canShuffle = (actions & (1L << 21)) != 0; // ACTION_SET_SHUFFLE_MODE (API 24+)
        boolean canRepeat = (actions & (1L << 22)) != 0; // ACTION_SET_REPEAT_MODE (API 24+)

        return new MediaSnapshot(
                t.pkg + ":" + t.title,
                t.pkg,
                t.title,
                t.artist,
                t.album,
                t.duration,
                t.position(),
                t.playing(),
                canPlay,
                canPause,
                canSeek,
                canNext,
                canPrev,
                canShuffle,
                canRepeat,
                "",
                System.currentTimeMillis(),
                t.source);
    }

    private MediaCapabilities mediaCapabilities() {
        MediaHub.Track t = MediaHub.get().current();
        if (t == null) return MediaCapabilities.none();
        long a = t.actions;
        return new MediaCapabilities(
                (a & PlaybackState.ACTION_PLAY) != 0 || t.hasSession,
                (a & PlaybackState.ACTION_PAUSE) != 0 || t.hasSession,
                (a & PlaybackState.ACTION_SEEK_TO) != 0,
                (a & PlaybackState.ACTION_SKIP_TO_NEXT) != 0 || MediaHub.get().canNext(),
                (a & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0 || MediaHub.get().canPrev(),
                (a & (1L << 21)) != 0, // ACTION_SET_SHUFFLE_MODE
                (a & (1L << 22)) != 0, // ACTION_SET_REPEAT_MODE
                (a & PlaybackState.ACTION_FAST_FORWARD) != 0,
                (a & PlaybackState.ACTION_REWIND) != 0);
    }

    private Bundle handleMediaCommand(String method, Bundle extras) {
        MediaHub hub = MediaHub.get();
        try {
            if (MediaCommand.TOGGLE.equals(method)) {
                hub.toggle();
            } else if (MediaCommand.PLAY.equals(method)) {
                if (!hub.current().playing()) hub.toggle();
            } else if (MediaCommand.PAUSE.equals(method)) {
                if (hub.current().playing()) hub.toggle();
            } else if (MediaCommand.NEXT.equals(method)) {
                hub.next();
            } else if (MediaCommand.PREVIOUS.equals(method)) {
                hub.prev();
            } else if (MediaCommand.SEEK.equals(method)) {
                long pos = extras != null ? extras.getLong("position_ms", -1) : -1;
                if (pos >= 0) hub.seekTo(pos);
            } else if (MediaCommand.SELECT_SOURCE.equals(method)) {
                String pkg = extras != null ? extras.getString("package") : null;
                if (pkg != null && pkg.length() > 0) {
                    Prefs.setMediaPkg(getContext(), pkg);
                    hub.onListenerEvent();
                }
            }
            return ok("media_ok");
        } catch (Throwable t) {
            Bundle err = new Bundle();
            err.putBoolean("ok", false);
            err.putString("reason", t.getMessage() == null ? "error" : t.getMessage());
            return err;
        }
    }

    private boolean trusted(String caller) {
        if (caller == null) return false;
        if (caller.equals(getContext().getPackageName())) return true;
        return Prefs.trustedCallers(getContext()).contains(caller);
    }

    private Bundle denied() {
        Bundle b = new Bundle();
        b.putBoolean("ok", false);
        b.putString("reason", "caller not trusted");
        return b;
    }

    private Bundle ok(String what) {
        Bundle b = new Bundle();
        b.putBoolean("ok", true);
        b.putString("result", what);
        return b;
    }

    private String callerPackage() {
        try {
            int uid = android.os.Binder.getCallingUid();
            if (uid == Process.myUid()) return getContext().getPackageName();
            String[] pkgs = getContext().getPackageManager().getPackagesForUid(uid);
            return (pkgs != null && pkgs.length > 0) ? pkgs[0] : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        return null;
    }

    @Override
    public String getType(Uri u) {
        return null;
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
        return 0;
    }
}






