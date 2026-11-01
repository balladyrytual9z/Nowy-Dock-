
package pl.dock.widget.media;

import android.os.Bundle;

/** Describes what the active session can do. */
public final class MediaCapabilities {
    public final boolean canPlay;
    public final boolean canPause;
    public final boolean canSeek;
    public final boolean canNext;
    public final boolean canPrevious;
    public final boolean canShuffle;
    public final boolean canRepeat;
    public final boolean canFastForward;
    public final boolean canRewind;

    public MediaCapabilities(
            boolean canPlay, boolean canPause, boolean canSeek,
            boolean canNext, boolean canPrevious,
            boolean canShuffle, boolean canRepeat,
            boolean canFastForward, boolean canRewind) {
        this.canPlay = canPlay;
        this.canPause = canPause;
        this.canSeek = canSeek;
        this.canNext = canNext;
        this.canPrevious = canPrevious;
        this.canShuffle = canShuffle;
        this.canRepeat = canRepeat;
        this.canFastForward = canFastForward;
        this.canRewind = canRewind;
    }

    public static MediaCapabilities none() {
        return new MediaCapabilities(false, false, false, false, false, false, false, false, false);
    }

    public Bundle toBundle() {
        Bundle b = new Bundle();
        b.putBoolean("can_play", canPlay);
        b.putBoolean("can_pause", canPause);
        b.putBoolean("can_seek", canSeek);
        b.putBoolean("can_next", canNext);
        b.putBoolean("can_previous", canPrevious);
        b.putBoolean("can_shuffle", canShuffle);
        b.putBoolean("can_repeat", canRepeat);
        b.putBoolean("can_fast_forward", canFastForward);
        b.putBoolean("can_rewind", canRewind);
        return b;
    }
}






