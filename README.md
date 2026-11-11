Test systemu agentowego
# Dock 2.0 (CarPlay Launcher) + Honda OBD Discovery

Android launcher for car tablets (API 26+, target 28 / Android 9 Automotive).  
Application ID: `pl.dock.widget`  
Version: **1.3.0-obd**

MediaTek MT8163 (ARMv7) friendly - soft graphite night palette, heap-conscious design.

## Features added in 1.3.0 (Parts 4-6)

### Advanced analytics (Part 4)
- `diagnostics/MisfireAnalyzer` - Mode $06 (ISO-TP header stripping, misfire MIDs per cylinder)
- `diagnostics/VirtualDyno` - P_WHP = (m*dv/dt*v)/745.7, deltas < 50 ms rejected, baro (0133) + IAT compensation
- `diagnostics/MechanicalHealthMonitor` - clutch slip (TPS>=80%, deltaRPM>=450), tight valves (LTFT>=7.5% hot idle), ELD multi-day average drop > 0.4 V

### Black Box storage (Part 5)
- `blackbox/AppDatabase` - framework SQLite with WAL (readers never blocked by massive inserts)
- `blackbox/BlackBoxRecorder` - ArrayDeque(300) RAM ring; flushes as ONE transaction only on harsh deceleration (>=10 km/h per 1 s)
- `blackbox/NanoServer` - mini HTTP server :8080, GET /csv = WAL-safe SQL export (no raw DB file copy)

### UI / services / widget (Part 6)
- `ui/UfoClusterView` - Canvas dashboard, ZERO allocation in onDraw (clipRect, cached Paints)
- `ObdCoreService` - foreground service, IMPORTANCE_LOW channel, started at BOOT_COMPLETED
- `AudioAlertManager` - shift light / ECT>102C alerts with AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
- `ObdWidgetProvider` - 25dp OBD widget with stale-data marking

ConnectionManager is now a process-wide singleton shared by UI, widget and the core service.

## Previously: 1.2.2 - Honda OBD Discovery

Integrated ELM327 / vGate discovery stack (SPP + BLE) under MVVM isolation:

```
pl.dock.widget
|- transport/          # ElmTransport, SppTransport, BleTransport, ConnectionDaemon
|- session/            # ElmSession, EngineState
|- orchestration/      # ConnectionManager, PidScheduler
|- repository/         # VehicleUiState (StateFlow snapshot)
`- ObdDiscoveryActivity
```

UI observes only `ConnectionManager.uiState: StateFlow<VehicleUiState>`.  
Zero direct Bluetooth access from the presentation layer.

### Transport (Part 2)
- **SppTransport**: insecure RFCOMM -> secure RFCOMM -> reflection channel-1 (UUID 00001101-...)
- **BleTransport**: mutex, 12 s pair / 6 s TX timeouts, MTU 185, 20-byte chunks, WRITE_TYPE_NO_RESPONSE, CCCD 0x2902 (missing descriptor ignored)
- **ConnectionDaemon**: exponential backoff (max 30 s) + GATT cache clear via reflection

### Session & Scheduler (Part 3)
- **ElmSession**: ATZ, ATE0, ATSP6, ATH1, ATAT1 - waits for `>` (no Thread.sleep)
- AT MA blocked (request/response only)
- **PidScheduler**: Fast 100 ms (010C/010D), Medium 500 ms (Load/TPS/MAP), Slow 2 s (ECT/IAT/STFT/LTFT)
- Stale flag after 3 s without response
- Background keep-alive: AT RV only
- **EngineState**: protect when V < 11.0 V or RPM 1-450; resume RPM > 600 & V > 13.2 V

Open **Honda OBD Discovery** from the app (activity exported) or add as dock tool.

## Previous features (1.2.1 and earlier)

- App Library (Biblioteka aplikacji), connectivity panel with custom tools, Liquid Glass, Night Shift + Circadian, Media Center, notification badges, edit-mode wobble, etc.

## Built-in Circadian Display (no separate APK)

The launcher now ships an embedded port of Circadian Display (`pl.dock.widget.circadian`) - the external app is no longer required:
- `CircadianCurve` - linear time interpolation of warmth/dimming (CurveEngine port, all edge cases preserved)
- `CircadianPrefs` - curve stored in SharedPreferences, seeded with the default "Evening" profile (18:00-23:00)
- `CircadianService` - foreground service evaluating the curve every minute; single overlay window (black dimming + amber warmth), touch/gesture pass-through preserved on Android 12+
- `CircadianActivity` - on/off switch and curve point editor, opens from the "Circadian" icon or via `CircadianBridge` when the external app is absent

`CircadianBridge`/`NightModeReceiver` still prefer the external Circadian Display app when installed, and fall back to the embedded engine otherwise; night-theme sync via `pl.circadiandisplay.STATE_CHANGED` keeps working either way.

## Build

```bash
# requires Android SDK + JDK 17
./gradlew --no-daemon clean assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

Debug keystore: `app/dock-debug.p12` (alias `dock`, password `dockdock`) if present.

## CI

`.github/workflows/build-release.yml` - Java 17, Gradle 8.9, assembleDebug + GitHub Release.

## Notatka (AI)

Ten wpis został dodany automatycznie przez agenta OpenHands w ramach zadania testowego.
Zmiana nie wpływa na działanie aplikacji ani na proces budowania APK.

<!-- retrigger CI 2026-11-11 -->
