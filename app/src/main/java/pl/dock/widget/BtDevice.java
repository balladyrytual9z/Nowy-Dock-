
package pl.dock.widget;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Wskaźnik Bluetooth w doku pokazuje, czy połączony jest WYBRANY telefon,
// a nie czy sam Bluetooth jest włączony.
public class BtDevice {

    private static final Set<String> connected = Collections.synchronizedSet(new HashSet<String>());
    private static boolean started;

    public static IntentFilter filter() {
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        f.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        f.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return f;
    }

    // aktualizuje zbiór połączonych urządzeń; true, gdy coś się zmieniło
    public static boolean handle(Intent i) {
        String a = i.getAction();
        if (a == null) return false;
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(a)) {
            if (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0) != BluetoothAdapter.STATE_ON) {
                connected.clear();
            }
            return true;
        }
        BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (d == null) return false;
        String mac = d.getAddress();
        boolean up;
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(a)) {
            up = true;
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(a)) {
            up = false;
        } else {
            int st = i.getIntExtra(BluetoothProfile.EXTRA_STATE, 0);
            up = st == BluetoothProfile.STATE_CONNECTED;
            if (st != BluetoothProfile.STATE_CONNECTED && st != BluetoothProfile.STATE_DISCONNECTED) {
                return false;
            }
        }
        return up ? connected.add(mac) : connected.remove(mac);
    }

    // początkowy stan z profili HFP i A2DP
    public static void init(final Context c) {
        if (started) return;
        started = true;
        try {
            final BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad == null) return;
            int[] profiles = {BluetoothProfile.HEADSET, BluetoothProfile.A2DP};
            for (int p : profiles) {
                ad.getProfileProxy(c.getApplicationContext(), new BluetoothProfile.ServiceListener() {
                    @Override
                    public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        try {
                            for (BluetoothDevice d : proxy.getConnectedDevices()) {
                                connected.add(d.getAddress());
                            }
                            StatusCenter.refresh(c);
                            ad.closeProfileProxy(profile, proxy);
                        } catch (Throwable ignored) {
                        }
                    }

                    @Override
                    public void onServiceDisconnected(int profile) {
                    }
                }, p);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isConnected(Context c) {
        String mac = Prefs.btDevice(c);
        if (mac.length() == 0) return Net.bluetoothOn();   // nie wybrano telefonu
        return connected.contains(mac);
    }

    // Nazwy podłączonych urządzeń (np. do podpisu kafelka Bluetooth).
    // Gdy system nie odda nazwy - adres MAC jako zapas.
    public static List<String> connectedNames() {
        List<String> out = new ArrayList<String>();
        BluetoothAdapter ad = null;
        try {
            ad = BluetoothAdapter.getDefaultAdapter();
        } catch (Throwable ignored) {
        }
        synchronized (connected) {
            for (String mac : connected) {
                String n = null;
                try {
                    BluetoothDevice d = ad == null ? null : ad.getRemoteDevice(mac);
                    if (d != null) n = d.getName();
                } catch (Throwable ignored) {
                }
                out.add(n == null || n.trim().length() == 0 ? mac : n.trim());
            }
        }
        return out;
    }

    public static List<BluetoothDevice> bonded() {
        List<BluetoothDevice> out = new ArrayList<BluetoothDevice>();
        try {
            BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
            if (ad != null && ad.getBondedDevices() != null) out.addAll(ad.getBondedDevices());
        } catch (Throwable ignored) {
        }
        return out;
    }
}







