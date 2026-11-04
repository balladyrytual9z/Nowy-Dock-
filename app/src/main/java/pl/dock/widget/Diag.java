
package pl.dock.widget;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

import java.util.Arrays;

// Odczyty ze wszystkich źródeł naraz
public class Diag {
    public static String report(Context c) {
        StringBuilder b = new StringBuilder();
        b.append("== Ostatni crash aplikacji ==\n");
        b.append(CrashLog.last(c)).append("\n\n");
        b.append("== Ostatni błąd widżetu ==\n");
        b.append(Prefs.sp(c).getString("lasterr", "brak")).append("\n\n");
        b.append("== WiFi / tethering ==\n");
        b.append("WiFi włączone: ").append(Net.wifiEnabled(c)).append("\n");
        b.append("WiFi połączone: ").append(Net.wifiConnected(c)).append("\n");
        Boolean ap = Net.apEnabled(c);
        b.append("hotspot (isWifiApEnabled): ").append(ap == null ? "brak odczytu" : ap).append("\n");
        String[] t = Net.tetheredIfaces(c);
        b.append("tethered ifaces: ").append(t == null ? "brak odczytu" : Arrays.toString(t)).append("\n");
        b.append("interfejs tetheringu aktywny: ").append(Net.tetherInterfaceUp()).append("\n");
        b.append("=> tethering: ").append(Net.tethering(c)).append("\n");
        b.append("Interfejsy:\n").append(Net.interfaces()).append("\n");

        b.append("== Bluetooth ==\n");
        b.append("włączony: ").append(Net.bluetoothOn()).append("\n\n");

        b.append("== Bateria Androida ==\n");
        Intent bi = Volt.batt(c);
        if (bi != null) {
            b.append("napięcie (surowe): ").append(bi.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)).append("\n");
            b.append("poziom: ").append(bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)).append("\n");
            b.append("zasilanie (plugged): ").append(bi.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)).append("\n");
            b.append("status: ").append(bi.getIntExtra(BatteryManager.EXTRA_STATUS, -1)).append("\n");
            b.append("temperatura: ").append(bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)).append("\n");
        } else {
            b.append("brak odczytu\n");
        }

        b.append("\n== Pliki napięcia (sysfs) ==\n");
        java.util.List<String> files = Volt.discoverFiles();
        if (files.isEmpty()) b.append("nie znaleziono\n");
        for (String f : files) {
            b.append(f).append(" = ").append(Volt.readRaw(f)).append("\n");
        }

        b.append("\n== Wybrane źródło ==\n");
        b.append(Volt.sourceLabel(c)).append("\n");
        b.append("wynik: ").append(Volt.text(c)).append("\n");
        return b.toString();
    }
}







