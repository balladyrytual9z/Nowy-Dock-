package pl.dock.widget;

/**
 * Czysta logika gestu otwarcia centrum łączności i pozycjonowania panelu.
 * Zero zależności od Androida - klasa jest w pełni testowalna na JVM (JUnit),
 * a LauncherActivity używa jej w produkcji, więc testy pokrywają dokładnie
 * ścieżkę wykonywaną na urządzeniu.
 *
 * NAPRAWA (root cause): poprzednia wersja gestu swipe-up w LauncherActivity
 * zwracała false w ACTION_DOWN, więc framework uznawał, że nikt nie konsumuje
 * dotyku i ACTION_UP nigdy nie przychodził - panel był nie do otwarcia gestem.
 */
final class OverlayTrigger {

    private OverlayTrigger() {
    }

    /**
     * Czy dotyk w ACTION_DOWN zaczął się w dolnej strefie ekranu (strefa gestu)?
     * Tylko wtedy konsumujemy DOWN (return true), żeby MOVE/UP dotarły do nas.
     */
    static boolean inSwipeZone(float downY, int viewH, int edgePx) {
        if (viewH <= 0 || edgePx < 0) return false;
        return downY > viewH - edgePx;
    }

    /** Czy sekwencja DOWN->UP to swipe UP (wystarczająco długi i szybki)? */
    static boolean isSwipeUp(float downY, float upY, long dtMs, float minDyPx, long maxMs) {
        float dy = downY - upY;
        return dy > minDyPx && dtMs >= 0 && dtMs < maxMs;
    }

    /**
     * Pozycja X panelu nad palcem (środek panelu na gest), z clampingiem
     * do ekranu i uwzględnieniem doka po lewej/prawej. Logika wydzielona
     * z LauncherActivity (showOverlayAt) pod testy.
     */
    static int panelX(float gestureX, int panelW, int rootW, int dockPx,
                      boolean dockLeft, boolean follow, int padPx) {
        int x;
        if (follow && gestureX > 0) {
            x = (int) (gestureX - panelW / 2f);
            x = Math.max(padPx, Math.min(x, rootW - panelW - padPx));
            if (dockLeft && x < dockPx) x = dockPx + padPx;
            if (!dockLeft && x + panelW > rootW - dockPx) x = rootW - dockPx - panelW - padPx;
        } else {
            x = dockLeft ? dockPx + padPx : padPx;
        }
        return x;
    }
}
