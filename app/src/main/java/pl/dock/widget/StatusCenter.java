
package pl.dock.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

// Jeden wspólny odczyt stanu WiFi / Bluetooth / tetheringu / napięcia.
// Odczyt jest drogi (refleksja, interfejsy sieciowe), więc robimy go po zdarzeniu,
// a nie przy każdym rysowaniu.
public class StatusCenter {

    public static class Snap {
        public boolean wifiEn, wifiCon, bt, tether;
        public int cp;
        public int wifi;           // 0..3 (patrz Net.wifiState)
        public boolean charging;   // napięcie wskazuje na pracujący silnik
        public int phone = -1;     // bateria iPhone'a (%), -1 = brak
        public String volt = "";

        boolean same(Snap o) {
            return o != null && wifiEn == o.wifiEn && wifiCon == o.wifiCon && bt == o.bt
                    && tether == o.tether && cp == o.cp && wifi == o.wifi && charging == o.charging && phone == o.phone && volt.equals(o.volt);
        }

        public String sig() {
            return (wifiEn ? "1" : "0") + (wifiCon ? "1" : "0") + (bt ? "1" : "0")
                    + (tether ? "1" : "0") + cp + wifi + (charging ? "c" : "n") + phone + "|" + volt;
        }
    }

    private static boolean chargingLatch;
    private static Snap last;
    private static volatile Runnable onChange;
    private static final Handler main = new Handler(Looper.getMainLooper());

    // launcher rejestruje tu odświeżenie docka (wywoływane od razu po zmianie stanu)
    public static void setOnChange(Runnable r) {
        onChange = r;
    }

    private static long lastFire;
    private static void fireChange() {
        Runnable r = onChange;
        if (r == null) return;
        long now = System.currentTimeMillis();
        if (now - lastFire < 50) return;
        lastFire = now;
        main.post(r);
    }

    // szybka ścieżka: bateria telefonu
    public static synchronized void phoneChanged() {
        if (last == null) return;
        int v = PhoneBattery.level();
        if (last.phone != v) {
            last.phone = v;
            fireChange();
        }
    }

    // szybka ścieżka: tylko stan CarPlay
    public static synchronized boolean refreshCp(Context c) {
        int cp = CarplayState.read(c.getApplicationContext());
        if (last == null) return refresh(c);
        if (last.cp == cp) return false;
        last.cp = cp;
        fireChange();
        return true;
    }

    private static long lastAt;

    // zwraca true, jeśli stan się zmienił
    public static synchronized boolean refresh(Context c) {
        Context app = c.getApplicationContext();
        Snap s = new Snap();
        s.wifiEn = Net.wifiEnabled(app);
        s.wifiCon = Net.wifiConnected(app);
        s.bt = BtDevice.isConnected(app);
        s.wifi = Net.wifiState(app);
        s.tether = Net.tethering(app);
        s.cp = CarplayState.read(app);
        Float volts = Prefs.showVoltage(app) ? Volt.read(app) : null;
        s.volt = Prefs.showVoltage(app) ? Volt.text(app) : "";
        // histereza: >= 13,3 V = alternator ładuje (silnik pracuje), poniżej 13,0 V = silnik stoi
        if (volts != null) {
            if (volts >= 13.3f) chargingLatch = true;
            else if (volts < 13.0f) chargingLatch = false;
        } else {
            chargingLatch = false;
        }
        s.charging = chargingLatch;
        s.phone = PhoneBattery.level();
        boolean changed = !s.same(last);
        last = s;
        lastAt = SystemClock.elapsedRealtime();
        if (changed) fireChange();
        return changed;
    }

    // odczyt tylko wtedy, gdy poprzedni jest starszy niż maxAgeMs (ogranicza częste zdarzenia baterii)
    public static synchronized boolean refreshIfStale(Context c, long maxAgeMs) {
        if (last != null && SystemClock.elapsedRealtime() - lastAt < maxAgeMs) return false;
        return refresh(c);
    }

    public static synchronized Snap get(Context c) {
        if (last == null || SystemClock.elapsedRealtime() - lastAt > 5000) refresh(c);
        return last;
    }
}







