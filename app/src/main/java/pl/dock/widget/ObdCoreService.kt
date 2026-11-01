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

    private var lastFrameMs = 0L
    private var lastMode06Ms = 0L
    @Volatile private var lastAlert: String? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        manager = ConnectionManager.get(applicationContext)
        db = AppDatabase(this)
        obdPrefs = getSharedPreferences("obd_settings", MODE_PRIVATE)
        recorder = BlackBoxRecorder(db)
        nano = NanoServer(this, db, recorder)
        audio = AudioAlertManager(this)
        health = MechanicalHealthMonitor(Prefs.sp(this))
        nano.start()
        startForeground(NOTIF_ID, buildNotification("Dock OBD core aktywny"))
        observeTelemetry()
        Log.i(TAG, "ObdCoreService created (WAL db + NanoServer :" + NanoServer.DEFAULT_PORT + ")")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun observeTelemetry() {
        scope.launch {
            manager.uiState.collect { s ->
                val now = System.currentTimeMillis()
                val analyticsOn = obdPrefs.getBoolean("analytics", true)
                val blackboxOn = obdPrefs.getBoolean("blackbox", true)

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
                ObdWidgetProvider.push(this@ObdCoreService, s.rpm, s.speedKmh, s.voltage, s.connected)

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

    override fun onDestroy() {
        running = false
        try { recorder.flush("SERVICE_STOP") } catch (ignored: Exception) {}
        nano.stop()
        audio.release()
        scope.cancel()
        Log.i(TAG, "ObdCoreService destroyed")
        super.onDestroy()
    }
}
