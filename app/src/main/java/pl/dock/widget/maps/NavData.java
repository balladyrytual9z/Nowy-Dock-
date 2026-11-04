package pl.dock.widget.maps;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Wspolny stan danych nawigacji odczytanych z Organic Maps.
 *
 * Usluga dostepnosci (OmAccessibilityService) zapisuje tu dane, a kafelki
 * i nakladka Docka je czytaja. Wszystko zostaje na urzadzeniu.
 */
public final class NavData {

    public interface Listener {
        void onNavChanged(NavData data);
    }

    /** Dane uznajemy za aktualne, jesli odswiezono je w ciagu 20 s. */
    private static final long FRESH_MS = 20000L;

    private static NavData instance;

    public static synchronized NavData get() {
        if (instance == null) instance = new NavData();
        return instance;
    }

    private final List<Listener> listeners = new ArrayList<Listener>();
    private final Handler h = new Handler(Looper.getMainLooper());

    private volatile boolean navActive;
    private volatile String turn = "";
    private volatile String turnDistance = "";
    private volatile String street = "";
    private volatile String eta = "";
    private volatile String remainTime = "";
    private volatile String remainDistance = "";
    private volatile String speed = "";
    private volatile String speedUnit = "km/h";
    private volatile String speedLimit = "";
    private volatile long updatedAt;

    private NavData() {
    }

    public boolean isActive() {
        return navActive;
    }

    /** Czy mamy swieze dane (nawigacja trwa i dane przyszly niedawno). */
    public boolean isFresh() {
        return navActive && (System.currentTimeMillis() - updatedAt) < FRESH_MS;
    }

    public String turn() { return turn; }
    public String turnDistance() { return turnDistance; }
    public String street() { return street; }
    public String eta() { return eta; }
    public String remainTime() { return remainTime; }
    public String remainDistance() { return remainDistance; }
    public String speed() { return speed; }
    public String speedUnit() { return speedUnit; }
    public String speedLimit() { return speedLimit; }

    public void addListener(Listener l) {
        if (l == null) return;
        synchronized (listeners) {
            if (!listeners.contains(l)) listeners.add(l);
        }
        l.onNavChanged(this);
    }

    public void removeListener(Listener l) {
        if (l == null) return;
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    /** Wywolywane przez usluge dostepnosci po kazdej zmianie ekranu Organic Maps. */
    public void update(boolean active, String turn, String turnDistance, String street,
                       String eta, String remainTime, String remainDistance,
                       String speed, String speedUnit, String speedLimit) {
        boolean changed = active != navActive
                || !eq(this.turn, turn)
                || !eq(this.turnDistance, turnDistance)
                || !eq(this.street, street)
                || !eq(this.eta, eta)
                || !eq(this.remainTime, remainTime)
                || !eq(this.remainDistance, remainDistance)
                || !eq(this.speed, speed)
                || !eq(this.speedUnit, speedUnit)
                || !eq(this.speedLimit, speedLimit);

        this.navActive = active;
        this.turn = nz(turn);
        this.turnDistance = nz(turnDistance);
        this.street = nz(street);
        this.eta = nz(eta);
        this.remainTime = nz(remainTime);
        this.remainDistance = nz(remainDistance);
        this.speed = nz(speed);
        this.speedUnit = nz(speedUnit).isEmpty() ? "km/h" : nz(speedUnit);
        this.speedLimit = nz(speedLimit);
        this.updatedAt = System.currentTimeMillis();

        if (changed) notifyListeners();
    }

    private void notifyListeners() {
        final List<Listener> copy;
        synchronized (listeners) {
            copy = new ArrayList<Listener>(listeners);
        }
        h.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : copy) {
                    try {
                        l.onNavChanged(NavData.this);
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    private static String nz(String s) {
        return s == null ? "" : s.trim();
    }

    private static boolean eq(String a, String b) {
        return nz(a).equals(nz(b));
    }
}
