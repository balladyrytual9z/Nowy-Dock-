
package pl.dock.widget;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

// Poziom baterii iPhone'a (jeÅli radio / Bluetooth go przekazuje).
// NasÅuchujemy dwÃ³ch typowych ÅºrÃ³deÅ: zdarzenia baterii urzÄdzenia Bluetooth
// oraz komendy HFP "+IPHONEACCEV" (tak iPhone raportuje bateriÄ zestawom gÅoÅnomÃ³wiÄcym).
public class PhoneBattery {

    public static final String ACTION_DEV = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";
    public static final String EXTRA_DEV = "android.bluetooth.device.extra.BATTERY_LEVEL";
    public static final String ACTION_VENDOR = "android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT";
    public static final String EXTRA_CMD = "android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_CMD";
    public static final String EXTRA_ARGS = "android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_ARGS";

    private static volatile int level = -1;
    private static volatile String lastSource = "brak odczytu";

    public static int level() {
        return level;
    }

    public static String lastSource() {
        return lastSource;
    }

    public static IntentFilter filter() {
        IntentFilter f = new IntentFilter();
        f.addAction(ACTION_DEV);
        f.addAction(ACTION_VENDOR);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        return f;
    }

    // zwraca true, jeÅli poziom siÄ zmieniÅ
    public static boolean handle(Intent i) {
        String a = i.getAction();
        int old = level;
        try {
            if (ACTION_DEV.equals(a)) {
                int v = i.getIntExtra(EXTRA_DEV, -1);
                if (v >= 0 && v <= 100) {
                    level = v;
                    lastSource = "bateria urzÄdzenia BT";
                }
            } else if (ACTION_VENDOR.equals(a)) {
                String cmd = i.getStringExtra(EXTRA_CMD);
                Bundle ex = i.getExtras();
                if (cmd != null && cmd.contains("IPHONEACCEV") && ex != null) {
                    Object[] args = (Object[]) ex.get(EXTRA_ARGS);
                    if (args != null && args.length >= 3) {
                        int pairs = Integer.parseInt(String.valueOf(args[0]));
                        for (int p = 0; p < pairs && 2 + 2 * p < args.length; p++) {
                            int key = Integer.parseInt(String.valueOf(args[1 + 2 * p]));
                            int val = Integer.parseInt(String.valueOf(args[2 + 2 * p]));
                            if (key == 1) {
                                level = Math.min(100, (val + 1) * 10);
                                lastSource = "HFP +IPHONEACCEV";
                            }
                        }
                    }
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) {
                level = -1;
            }
        } catch (Throwable ignored) {
        }
        return level != old;
    }

    public static void register(Context c, BroadcastReceiver r) {
        try {
            c.registerReceiver(r, filter());
        } catch (Throwable ignored) {
        }
    }
}







