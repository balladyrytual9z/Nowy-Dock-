
package pl.dock.widget.media;

/**
 * Commands accepted by Media Core / DockStateProvider.
 */
public final class MediaCommand {
    public static final String PLAY = "media_play";
    public static final String PAUSE = "media_pause";
    public static final String TOGGLE = "media_toggle";
    public static final String NEXT = "media_next";
    public static final String PREVIOUS = "media_previous";
    public static final String SEEK = "media_seek";
    public static final String SELECT_SOURCE = "media_select_source";
    public static final String GET_SNAPSHOT = "get_media_snapshot";
    public static final String GET_CAPABILITIES = "get_media_capabilities";

    private MediaCommand() {}
}






