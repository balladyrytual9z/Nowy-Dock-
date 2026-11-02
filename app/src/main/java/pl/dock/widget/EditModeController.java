package pl.dock.widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Globalny tryb edycji ikon (home screen: przesuwanie / usuwanie).
 * Animacja trzesienia (wobble) zostala calkowicie usunieta.
 */
public final class EditModeController {

    private static final EditModeController INSTANCE = new EditModeController();

    public static EditModeController get() {
        return INSTANCE;
    }

    public interface Listener {
        void onEditModeChanged(boolean editing);
    }

    private boolean editing;
    private final List<Listener> listeners = new ArrayList<Listener>();

    private EditModeController() {
    }

    public boolean isEditing() {
        return editing;
    }

    public void setEditing(boolean on) {
        if (editing == on) return;
        editing = on;
        for (Listener l : listeners) {
            try {
                l.onEditModeChanged(on);
            } catch (Throwable ignored) {
            }
        }
    }

    public void toggle() {
        setEditing(!editing);
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }
}
