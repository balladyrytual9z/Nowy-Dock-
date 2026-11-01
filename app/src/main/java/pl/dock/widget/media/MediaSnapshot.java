
package pl.dock.widget.media;

import android.graphics.Bitmap;
import android.os.Bundle;

import org.json.JSONObject;

/**
 * Immutable snapshot of the active media session for Dock + Media Center.
 * Shared contract between launcher widget and future Media Center APK.
 */
public final class MediaSnapshot {

    public static final int VERSION = 1;

    public final int version;
    public final String sessionId;
    public final String packageName;
    public final String title;
    public final String artist;
    public final String album;
    public final long durationMs;
    public final long positionMs;
    public final boolean playing;
    public final boolean canPlay;
    public final boolean canPause;
    public final boolean canSeek;
    public final boolean canNext;
    public final boolean canPrevious;
    public final boolean canShuffle;
    public final boolean canRepeat;
    public final String artworkUri;
    public final long updatedAt;
    public final String source;
    public transient Bitmap artwork; // not serialized

    public MediaSnapshot(
            String sessionId,
            String packageName,
            String title,
            String artist,
            String album,
            long durationMs,
            long positionMs,
            boolean playing,
            boolean canPlay,
            boolean canPause,
            boolean canSeek,
            boolean canNext,
            boolean canPrevious,
            boolean canShuffle,
            boolean canRepeat,
            String artworkUri,
            long updatedAt,
            String source) {
        this.version = VERSION;
        this.sessionId = sessionId == null ? "" : sessionId;
        this.packageName = packageName == null ? "" : packageName;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.durationMs = durationMs;
        this.positionMs = positionMs;
        this.playing = playing;
        this.canPlay = canPlay;
        this.canPause = canPause;
        this.canSeek = canSeek;
        this.canNext = canNext;
        this.canPrevious = canPrevious;
        this.canShuffle = canShuffle;
        this.canRepeat = canRepeat;
        this.artworkUri = artworkUri == null ? "" : artworkUri;
        this.updatedAt = updatedAt;
        this.source = source == null ? "" : source;
    }

    public static MediaSnapshot empty() {
        return new MediaSnapshot("", "", "", "", "", -1, 0,
                false, false, false, false, false, false, false, false,
                "", System.currentTimeMillis(), "");
    }

    public boolean hasData() {
        return title.length() > 0 || artist.length() > 0 || packageName.length() > 0;
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putInt("version", version);
        b.putString("session_id", sessionId);
        b.putString("package", packageName);
        b.putString("title", title);
        b.putString("artist", artist);
        b.putString("album", album);
        b.putLong("duration_ms", durationMs);
        b.putLong("position_ms", positionMs);
        b.putBoolean("playing", playing);
        b.putBoolean("can_play", canPlay);
        b.putBoolean("can_pause", canPause);
        b.putBoolean("can_seek", canSeek);
        b.putBoolean("can_next", canNext);
        b.putBoolean("can_previous", canPrevious);
        b.putBoolean("can_shuffle", canShuffle);
        b.putBoolean("can_repeat", canRepeat);
        b.putString("artwork_uri", artworkUri);
        b.putLong("updated_at", updatedAt);
        b.putString("source", source);
        return b;
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("version", version);
            o.put("session_id", sessionId);
            o.put("package", packageName);
            o.put("title", title);
            o.put("artist", artist);
            o.put("album", album);
            o.put("duration_ms", durationMs);
            o.put("position_ms", positionMs);
            o.put("playing", playing);
            o.put("can_play", canPlay);
            o.put("can_pause", canPause);
            o.put("can_seek", canSeek);
            o.put("can_next", canNext);
            o.put("can_previous", canPrevious);
            o.put("can_shuffle", canShuffle);
            o.put("can_repeat", canRepeat);
            o.put("artwork_uri", artworkUri);
            o.put("updated_at", updatedAt);
            o.put("source", source);
            return o.toString();
        } catch (Throwable t) {
            return "{}";
        }
    }

    public static MediaSnapshot fromBundle(Bundle b) {
        if (b == null) return empty();
        return new MediaSnapshot(
                b.getString("session_id", ""),
                b.getString("package", ""),
                b.getString("title", ""),
                b.getString("artist", ""),
                b.getString("album", ""),
                b.getLong("duration_ms", -1),
                b.getLong("position_ms", 0),
                b.getBoolean("playing", false),
                b.getBoolean("can_play", false),
                b.getBoolean("can_pause", false),
                b.getBoolean("can_seek", false),
                b.getBoolean("can_next", false),
                b.getBoolean("can_previous", false),
                b.getBoolean("can_shuffle", false),
                b.getBoolean("can_repeat", false),
                b.getString("artwork_uri", ""),
                b.getLong("updated_at", 0),
                b.getString("source", ""));
    }
}






