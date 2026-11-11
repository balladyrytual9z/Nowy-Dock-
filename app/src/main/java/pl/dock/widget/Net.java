
package pl.dock.widget;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.Locale;

// Stan BT / WiFi / tetheringu tabletu
public class Net {

    public static boolean bluetoothOn() {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            return a != null && a.isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    private static WifiManager wm(Context c) {
        return (WifiManager) c.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    public static boolean wifiEnabled(Context c) {
        try {
            WifiManager w = wm(c);
            return w != null && w.isWifiEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    public static boolean wifiConnected(Context c) {
        try {
            if (!wifiEnabled(c)) return false;
            ConnectivityManager cm = (ConnectivityManager)
                    c.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    // 0 = Wi-Fi wyłączone, 1 = włączone, brak sieci, 2 = połączone bez internetu, 3 = połączone z internetem
    public static int wifiState(Context c) {
        if (!wifiEnabled(c)) return 0;
        if (!wifiConnected(c)) return 1;
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network n : cm.getAllNetworks()) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? 3 : 2;
                }
            }
        } catch (Throwable ignored) {
        }
        return 3;
    }

    // Nazwa sieci WiFi (SSID) lub "" gdy brak połączenia / system nie oddaje nazwy.
    @SuppressWarnings("deprecation")
    public static String wifiSsid(Context c) {
        try {
            WifiManager w = wm(c);
            if (w == null || !wifiConnected(c)) return "";
            String s = w.getConnectionInfo().getSSID();
            if (s == null) return "";
            s = s.replace("\"", "").trim();
            if (s.length() == 0 || "<unknown ssid>".equals(s)) return "";
            return s;
        } catch (Throwable t) {
            return "";
        }
    }

    // hotspot WiFi (ukryte API przez refleksję); null = brak odczytu
    public static Boolean apEnabled(Context c) {
        try {
            Method m = WifiManager.class.getDeclaredMethod("isWifiApEnabled");
            m.setAccessible(true);
            return (Boolean) m.invoke(wm(c));
        } catch (Throwable t) {
            return null;
        }
    }

    public static String[] tetheredIfaces(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager)
                    c.getSystemService(Context.CONNECTIVITY_SERVICE);
            Method m = ConnectivityManager.class.getDeclaredMethod("getTetheredIfaces");
            m.setAccessible(true);
            return (String[]) m.invoke(cm);
        } catch (Throwable t) {
            return null;
        }
    }

    // czy działa interfejs typowy dla tetheringu (hotspot, USB, BT)
    public static boolean tetherInterfaceUp() {
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName().toLowerCase(Locale.US);
                boolean nameHit = n.startsWith("ap") || n.startsWith("softap")
                        || n.startsWith("swlan") || n.startsWith("rndis")
                        || n.startsWith("bt-pan") || n.startsWith("usb") || n.startsWith("ncm");
                Enumeration<InetAddress> ad = ni.getInetAddresses();
                while (ad.hasMoreElements()) {
                    InetAddress a = ad.nextElement();
                    if (!(a instanceof Inet4Address)) continue;
                    String h = a.getHostAddress();
                    if (nameHit) return true;
                    if (h.equals("192.168.43.1") || h.equals("192.168.42.129")
                            || h.equals("192.168.44.1")) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean tethering(Context c) {
        Boolean ap = apEnabled(c);
        if (ap != null && ap) return true;
        String[] t = tetheredIfaces(c);
        if (t != null && t.length > 0) return true;
        return tetherInterfaceUp();
    }

    public static String interfaces() {
        StringBuilder b = new StringBuilder();
        try {
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
            while (en != null && en.hasMoreElements()) {
                NetworkInterface ni = en.nextElement();
                b.append("  ").append(ni.getName()).append(ni.isUp() ? " [up]" : " [down]");
                Enumeration<InetAddress> ad = ni.getInetAddresses();
                while (ad.hasMoreElements()) {
                    InetAddress a = ad.nextElement();
                    if (a instanceof Inet4Address) b.append(" ").append(a.getHostAddress());
                }
                b.append("\n");
            }
        } catch (Throwable t) {
            b.append("  brak odczytu\n");
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- przełączanie

    public static boolean setWifi(Context c, boolean on) {
        try {
            WifiManager w = wm(c);
            return w != null && w.setWifiEnabled(on);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean setBluetooth(boolean on) {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null) return false;
            return on ? a.enable() : a.disable();
        } catch (Throwable t) {
            return false;
        }
    }

    // Zwraca: "ok" (przełączono), "settings" (otwarto ustawienia systemu), "fail".
    // Zwykła aplikacja na Androidzie 9 zwykle nie może włączyć hotspotu sama.
    public static String setTethering(Context c, boolean on) {
        try {
            Class<?> cfg = Class.forName("android.net.wifi.WifiConfiguration");
            Method m = WifiManager.class.getDeclaredMethod("setWifiApEnabled", cfg, boolean.class);
            m.setAccessible(true);
            Object r = m.invoke(wm(c), null, on);
            if (Boolean.TRUE.equals(r)) return "ok";
        } catch (Throwable ignored) {
        }
        return openTetherSettings(c) ? "settings" : "fail";
    }

    // Szuka eksportowanej aktywności ustawień prowadzącej wprost do ekranu hotspotu.
    // Preferencja: nazwy z "wifi" (ekran hotspotu), potem z "settings", na końcu pozostałe.
    private static String findTetherActivity(Context c, String pkg) {
        try {
            android.content.pm.PackageInfo pi = c.getPackageManager()
                    .getPackageInfo(pkg, android.content.pm.PackageManager.GET_ACTIVITIES);
            if (pi.activities == null) return null;
            String best = null;
            int bestScore = 0;
            for (android.content.pm.ActivityInfo ai : pi.activities) {
                if (!ai.exported) continue;
                String n = ai.name == null ? "" : ai.name.toLowerCase(Locale.US);
                if (!n.contains("tether") && !n.contains("hotspot") && !n.contains("wifiap")) continue;
                if (n.contains("provision")) continue;
                int score = n.contains("wifi") ? 3 : (n.contains("settings") ? 2 : 1);
                if (score > bestScore) {
                    bestScore = score;
                    best = ai.name;
                }
            }
            return best;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean tryStart(Context c, String pkg, String cls) {
        try {
            Intent i = new Intent();
            i.setClassName(pkg, cls);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // Otwiera bezpośrednio ekran ustawień z przełącznikiem hotspotu (bez ekranów nadrzędnych).
    // Kolejność: najpierw DOKŁADNIE ekran „Hotspot Wi-Fi" (alias AOSP + ukryty
    // fragment), potem wyszukiwanie OEM, pozostałe aliasy, akcja systemowa, MTK.
    public static boolean openTetherSettings(Context c) {
        // Priorytet 1: alias aktywności ekranu „Hotspot Wi-Fi" (Android 9 AOSP)
        if (tryResolve(c, "com.android.settings",
                "com.android.settings.Settings$WifiTetherSettingsActivity")) return true;
        // Priorytet 2: wyszukaj aktywność ekranu hotspotu w ustawieniach systemu (OEM)
        String cls = findTetherActivity(c, "com.android.settings");
        if (cls != null && tryStart(c, "com.android.settings", cls)) return true;
        // Priorytet 3: ukryty fragment WifiTetherSettings w SubSettings
        if (tryFragment(c, "com.android.settings.wifi.tether.WifiTetherSettings")) return true;
        // Priorytet 4: pozostałe znane aliasy ekranu hotspotu
        if (tryResolve(c, "com.android.settings",
                "com.android.settings.Settings$TetherSettingsActivity")) return true;
        if (tryResolve(c, "com.android.settings",
                "com.android.settings.Settings$WifiApSettingsActivity")) return true;
        // Priorytet 5: systemowa akcja TETHER_SETTINGS
        try {
            Intent i = new Intent("android.settings.TETHER_SETTINGS");
            if (i.resolveActivity(c.getPackageManager()) != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            }
        } catch (Throwable ignored) {}
        // Priorytet 6: ukryty fragment TetherSettings w SubSettings
        if (tryFragment(c, "com.android.settings.TetherSettings")) return true;
        // Priorytet 7: ustawienia MediaTek (zwykle ekran nadrzędny — dopiero po AOSP)
        String mtk = findTetherActivity(c, "com.mediatek.settings");
        if (mtk != null && tryStart(c, "com.mediatek.settings", mtk)) return true;
        if (tryStart(c, "com.mediatek.settings", "com.mediatek.settings.TetherSettings")) return true;
        // Ostateczny fallback: ustawienia sieci bezprzewodowych
        return startFirst(c, new Intent(Settings.ACTION_WIRELESS_SETTINGS));
    }

    private static boolean tryResolve(Context c, String pkg, String cls) {
        try {
            Intent i = new Intent();
            i.setClassName(pkg, cls);
            if (i.resolveActivity(c.getPackageManager()) != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean tryFragment(Context c, String fragment) {
        try {
            Intent i = new Intent();
            i.setClassName("com.android.settings", "com.android.settings.SubSettings");
            i.putExtra(":settings:show_fragment", fragment);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    // ---------------------------------------------------------------- skróty do ustawień / aplikacji

    public static boolean openWifiSettings(Context c) {
        return startFirst(c, new Intent(Settings.ACTION_WIFI_SETTINGS));
    }

    public static boolean openBluetoothSettings(Context c) {
        return startFirst(c, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    // aplikacja Bluetooth radia (pakiet z ustawień); gdy jej nie ma – ustawienia systemowe
    public static boolean openBluetoothApp(Context c) {
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(Prefs.btPkg(c));
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return openBluetoothSettings(c);
    }

    private static boolean startFirst(Context c, Intent i) {
        try {
            if (i.resolveActivity(c.getPackageManager()) == null) return false;
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}

// retrigger CI 2026-11-11: run #495 utknal w kolejce (zmiana tylko komentarza)
