package pl.dock.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pl.dock.widget.blackbox.AppDatabase
import pl.dock.widget.blackbox.BlackBoxRecorder
import pl.dock.widget.blackbox.NanoServer
import pl.dock.widget.blackbox.TelemetrySnapshot
import pl.dock.widget.diagnostics.MechanicalHealthMonitor
import pl.dock.widget.diagnostics.MisfireAnalyzer
import pl.dock.widget.diagnostics.VirtualDyno
import pl.dock.widget.orchestration.ConnectionManager
import pl.dock.widget.repository.EngineStateLabel

/**
 * ObdCoreService - foreground core of the OBD stack.
 *
 * Started from BOOT_COMPLETED (via [BootReceiver]) and from the OBD UI.
 * Notification channel uses IMPORTANCE_LOW so Android 9 doesn't kill it,
 * while the user is not distracted by the status entry.
 *
 * Faza 6 (dopracowanie): alerty i zasilanie modulow sa bramkowane
 * przez SharedPreferences "obd_settings" (zakladka DIAG w Ustawieniach):
 * shift_light, ect_alert, analytics, blackbox; shift light czyta prog
 * redline_rpm zamiast stalej.
 *
 * Odometr (akumulacja dystansu dla modulu Serwis) liczony jest TUTAJ,
 * w strumieniu telemetrii - przebieg rosnie takze przy zamknietym
 * ekranie Serwis.
 *
 * ACC State Watchdog: gdy silnik stoi (RPM == 0 / OFF) a napiecie spada
 * ponizej 12.0 V na dluziej niz 3 minuty, Bluetooth jest rozlaczany -
 * zabezpieczenie akumulatora przed drena w Deep Sleep radia ESSGOO.
 */
class ObdCoreService : Service() {

    companion object {
        private const val TAG = "ObdCoreService"
        private const val CHANNEL = "obd_core"
        private const val NOTIF_ID = 42

        /** ECT alert threshold [deg C] */
        const val ECT_ALERT_C = 102f
        /** Shift light fallback [RPM] (nadpisywany przez redline_rpm z ustawien) */
        const val SHIFT_LIGHT_RPM = 6500
        /** Black Box feed interval [ms] */
        private const val FRAME_MS = 1_000L
        /** Mode 06 poll interval [ms] */
        private const val MODE06_MS = 30_000L
        /** ACC watchdog: zaplon OFF z niskim napieciem [ms] do rozlaczenia */
        private const val ACC_TIMEOUT_MS = 180_000L
        /** ACC watchdog: prog napiecia [V] */
        private const val ACC_LOW_V = 12.0f

        /**
         * NAPRAWA "ZAPISZ EVENT": akcja intentu z przycisku w zegarach UFO.
         * Wczesniej przycisk tylko startowal usluge i nic nie zapisywal;
         * teraz wymusza flush calego bufora czarnej skrzynki (~30 s telemetrii)
         * do bazy jako jedno zdarzenie MANUAL_EVENT.
         */
        const val ACTION_SAVE_EVENT = "pl.dock.widget.action.SAVE_EVENT"

        @Volatile var running = false
            private set

        @JvmStatic
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, ObdCoreService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "start failed: " + e.message)
            }
        }

        @JvmStatic
        fun stop(context: Context) {
            try { context.stopService(Intent(context, ObdCoreService::class.java)) } catch (ignored: Exception) {}
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var manager: ConnectionManager
    private lateinit var db: AppDatabase
    private lateinit var recorder: BlackBoxRecorder
    private lateinit var nano: NanoServer
    private lateinit var audio: AudioAlertManager
    private val dyno = VirtualDyno()
    private val misfire = MisfireAnalyzer()
    private lateinit var health: MechanicalHealthMonitor
    private lateinit var obdPrefs: SharedPreferences
    private lateinit var maintPrefs: SharedPreferences

    private var lastFrameMs = 0L
    private var lastMode06Ms = 0L
    private var accOffMs = 0L
    @Volatile private var lastAlert: String? = null
    /** Odbiornik resume po zaplonie ON (patrz startIgnitionResume). */
    private var ignitionResume: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        manager = ConnectionManager.get(applicationContext)
        db = AppDatabase(this)
        obdPrefs = getSharedPreferences("obd_settings", MODE_PRIVATE)
        maintPrefs = getSharedPreferences("obd_maintenance", MODE_PRIVATE)
        recorder = BlackBoxRecorder(db)
        nano = NanoServer(this, db, recorder)
        audio = AudioAlertManager(this)
        health = MechanicalHealthMonitor(Prefs.sp(this))
        nano.start()
        startForeground(NOTIF_ID, buildNotification("Dock OBD core aktywny"))
        autoConnect()
        startIgnitionResume()
        observeTelemetry()
        Log.i(TAG, "ObdCoreService created (WAL db + NanoServer :" + NanoServer.DEFAULT_PORT + ")")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SAVE_EVENT) {
            try {
                recorder.flush("MANUAL_EVENT")
                Log.i(TAG, "SAVE_EVENT: reczny zapis bufora czarnej skrzynki")
            } catch (e: Exception) {
                Log.w(TAG, "SAVE_EVENT failed: " + e.message)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeTelemetry() {
        scope.launch {
            manager.uiState.collect { s ->
                val now = System.currentTimeMillis()
                val analyticsOn = obdPrefs.getBoolean("analytics", true)
                val blackboxOn = obdPrefs.getBoolean("blackbox", true)

                // --- odometr (Serwis): akumulacja dystansu z VSS, w tle ---
                accumulateOdometer(s.speedKmh, now)

                // --- ACC State Watchdog: rozlacz BT po dlugim staniu na zaplonie ---
                if (s.engineState == EngineStateLabel.OFF &&
                    !s.voltage.isNaN() && s.voltage < ACC_LOW_V) {
                    if (accOffMs == 0L) {
                        accOffMs = now
                    } else if (now - accOffMs > ACC_TIMEOUT_MS && s.connected) {
                        raiseAlert("ACC",
                            "Zaplon OFF (V " + "%.1f".format(s.voltage) +
                            ") - rozlaczam Bluetooth, oszczedzam akumulator")
                        manager.disconnect()
                        accOffMs = 0L
                    }
                } else {
                    accOffMs = 0L
                }

                // --- Black Box feed (~1 Hz), bramkowane ustawieniem ---
                if (blackboxOn && now - lastFrameMs >= FRAME_MS) {
                    lastFrameMs = now
                    recorder.offer(
                        TelemetrySnapshot(
                            timestampMs = now,
                            voltage = s.voltage,
                            rpm = s.rpm,
                            speedKmh = s.speedKmh,
                            loadPct = s.engineLoadPct,
                            tpsPct = s.tpsPct,
                            mapKpa = s.mapKpa,
                            ectC = s.ectC,
                            iatC = s.iatC,
                            stftPct = s.stftPct,
                            ltftPct = s.ltftPct
                        )
                    )
                }

                // --- analytics (VirtualDyno + MechanicalHealthMonitor) ---
                if (analyticsOn) {
                    if (s.speedKmh >= 0) dyno.onSpeed(s.speedKmh, now)
                    if (!s.iatC.isNaN()) dyno.setIatC(s.iatC)
                    health.onTelemetry(s.rpm, s.speedKmh, s.tpsPct, s.ectC, s.ltftPct, s.voltage, now)
                        .forEach { raiseAlert(it.kind.name, it.message) }
                }

                // --- critical alerts (audio duck), bramkowane ustawieniami ---
                val redline = obdPrefs.getString("redline_rpm", "6500")?.toIntOrNull() ?: SHIFT_LIGHT_RPM
                if (obdPrefs.getBoolean("ect_alert", true) && !s.ectC.isNaN() && s.ectC >= ECT_ALERT_C) {
                    raiseAlert("ECT", "Przegrzanie: " + s.ectC.toInt() + " C")
                } else if (obdPrefs.getBoolean("shift_light", true) && s.rpm >= redline) {
                    raiseAlert("SHIFT", "Shift light " + s.rpm + " RPM")
                } else if (s.engineState == EngineStateLabel.PROTECTED) {
                    raiseAlert("VOLT", "Niskie napiecie " + "%.1f".format(s.voltage) + " V")
                }

                // --- widget feed (throttled inside) ----------------------------
                ObdWidgetProvider.push(this@ObdCoreService, s)

                // --- periodic Mode 06 (misfire), bramkowane ustawieniem analytics ---
                if (analyticsOn && s.connected && now - lastMode06Ms >= MODE06_MS) {
                    lastMode06Ms = now
                    try {
                        val res = misfire.poll { cmd -> manager.rawRequest(cmd) }
                        val failed = res.filter { it.failed }
                        if (failed.isNotEmpty()) {
                            raiseAlert("MISFIRE", "Wypadanie zaplonow: cyl. " + failed.joinToString { it.mid.toString() })
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Mode 06 poll failed: " + e.message)
                    }
                }
            }
        }
    }

    /**
     * Automatyczne polaczenie z zapamietanym adapterem (pref "adapter_mac"
     * + transport "prefer_ble"). Uruchamiane przy starcie uslugi w tle, gdy
     * zapamietany jest adres MAC. Bez zapamietanego adresu nic nie robimy.
     */
    private fun autoConnect() {
        val mac = obdPrefs.getString("adapter_mac", "")?.trim().orEmpty()
        if (mac.isEmpty()) return
        // Stale wiazanie (pref "pair_lock", domyslnie wlaczone): gdy kierowca je
        // wylaczy, aplikacja nie laczy sie automatycznie w tle i nie przeskakuje
        // na zapamietany adapter - polaczenie wybiera sie recznie z ekranu skanu.
        if (!obdPrefs.getBoolean("pair_lock", true)) return
        val preferBle = obdPrefs.getBoolean("prefer_ble", false)
        scope.launch {
            delay(1500)   // settle po BOOT_COMPLETED / starcie radia
            val st = manager.uiState.value
            if (st.connected || st.connecting) return@launch   // sesja juz zyje
            try {
                manager.connect(mac, preferBle)
            } catch (e: Exception) {
                Log.w(TAG, "autoConnect failed: " + e.message)
            }
        }
    }

    /**
     * Akumulacja przebiegu dla modulu Serwis ("obd_maintenance":
     * odo_acc w metrach, odo_ts = znacznik czasu ostatniej probki).
     * Przedzial dt ograniczony do 100..5000 ms - chroni przed zliczaniem
     * dystansu po dlugiej przerwie w telemetrii (gap > 5 s jest ignorowany).
     */
    private fun accumulateOdometer(speedKmh: Int, now: Long) {
        if (speedKmh > 0) {
            val lastTs = maintPrefs.getLong("odo_ts", 0L)
            if (lastTs == 0L) {
                maintPrefs.edit().putLong("odo_ts", now).apply()
                return
            }
            val dt = now - lastTs
            if (dt in 100..5000) {
                val acc = maintPrefs.getFloat("odo_acc", 0f)
                val meters = (speedKmh * (dt / 3_600_000.0)).toFloat()
                maintPrefs.edit()
                    .putFloat("odo_acc", acc + meters)
                    .putLong("odo_ts", now)
                    .apply()
            } else {
                // za dluga przerwa - odswiez znacznik bez doliczania dystansu
                maintPrefs.edit().putLong("odo_ts", now).apply()
            }
        } else {
            maintPrefs.edit().putLong("odo_ts", now).apply()
        }
    }

    private fun raiseAlert(kind: String, message: String) {
        Log.w(TAG, "[" + kind + "] " + message)
        audio.alert(kind)   // rate-limited inside AudioAlertManager
        lastAlert = kind
    }

    private fun buildNotification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL, "Dock OBD", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Usluga OBD w tle (czarna skrzynka, analityka)"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
        @Suppress("DEPRECATION")
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("Dock OBD")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    /**
     * RESUME PO ZAPLONIE ON (dokonczenie ACC Watchdog - KROK 2 z sesji 3):
     * po rozlaczeniu BT przez watchdog (silnik stoi, akumulator slabnie)
     * powrot kierowcy z zaplonem ON budzi radio -> ACTION_SCREEN_ON albo
     * ACTION_POWER_CONNECTED. Po 2.5 s settle (ECU Keihin budzi CAN po chwili)
     * wolamy autoConnect() - ten sam kod co po starcie uslugi: szanuje
     * adapter_mac, pair_lock i nic nie robi, gdy sesja juz zyje.
     * Reczna droga to QUICK BUTTON "wymus reconnect" w CarPlay Dashboard.
     */
    private fun startIgnitionResume() {
        if (ignitionResume != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_POWER_CONNECTED -> {
                        scope.launch {
                            delay(2500)   // settle: zaplon ON, ECU budzi CAN
                            val st = manager.uiState.value
                            if (!st.connected && !st.connecting) autoConnect()
                        }
                    }
                }
            }
        }
        try {
            val f = IntentFilter()
            f.addAction(Intent.ACTION_SCREEN_ON)
            f.addAction(Intent.ACTION_POWER_CONNECTED)
            registerReceiver(r, f)
            ignitionResume = r
        } catch (e: Exception) {
            Log.w(TAG, "ignition resume register failed: " + e.message)
        }
    }

    private fun stopIgnitionResume() {
        ignitionResume?.let {
            try { unregisterReceiver(it) } catch (ignored: Exception) {}
        }
        ignitionResume = null
    }

    override fun onDestroy() {
        running = false
        stopIgnitionResume()
        try { recorder.flush("SERVICE_STOP") } catch (ignored: Exception) {}
        nano.stop()
        audio.release()
        scope.cancel()
        Log.i(TAG, "ObdCoreService destroyed")
        super.onDestroy()
    }
}
