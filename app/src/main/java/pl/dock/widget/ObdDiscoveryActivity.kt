package pl.dock.widget

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.dock.widget.blackbox.BlackBoxActivity
import pl.dock.widget.dashboard.CarPlayDashboardActivity
import pl.dock.widget.diagnostics.DtcManagerActivity
import pl.dock.widget.diagnostics.MisfireLabActivity
import pl.dock.widget.maintenance.MaintenanceActivity
import pl.dock.widget.orchestration.ConnectionManager
import pl.dock.widget.report.EReportActivity
import pl.dock.widget.settings.ObdSettingsActivity
import pl.dock.widget.terminal.ObdTerminalActivity
import pl.dock.widget.trip.TripActivity
import pl.dock.widget.ui.UfoClusterActivity

/**
 * Honda OBD Discovery - centrum dowodzenia modulami OBD.
 * Obserwuje [ConnectionManager.uiState] - zero bezposredniego Bluetooth w UI.
 * Zakaz fabryzacji: brak danych = wyswietlone "-", nigdy zmyslone wartosci.
 *
 * Dopracowanie: korutyna telemetrii anulowana w onDestroy (brak wycieku),
 * przycisk WSTECZ zamyka ekran i wraca do launchera.
 */
class ObdDiscoveryActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var statusTv: TextView
    private lateinit var dataTv: TextView
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = ConnectionManager.get(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(32, 40, 32, 24)
        }
        statusTv = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            text = "Honda OBD Discovery"
        }
        dataTv = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#E5E5EA"))
            setPadding(0, 16, 0, 0)
        }
        root.addView(statusTv)

        // nawigacja do modulow
        val nav = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nav.addView(navBtn("UFO CLUSTER (ZEGARY)") {
            startActivity(Intent(this, UfoClusterActivity::class.java))
        })
        nav.addView(navBtn("CARPLAY DASHBOARD") {
            startActivity(Intent(this, CarPlayDashboardActivity::class.java))
        })
        nav.addView(navBtn("MISFIRE LAB") {
            startActivity(Intent(this, MisfireLabActivity::class.java))
        })
        nav.addView(navBtn("DTC MANAGER") {
            startActivity(Intent(this, DtcManagerActivity::class.java))
        })
        nav.addView(navBtn("TERMINAL AT/OBD") {
            startActivity(Intent(this, ObdTerminalActivity::class.java))
        })
        nav.addView(navBtn("BLACK BOX") {
            startActivity(Intent(this, BlackBoxActivity::class.java))
        })
        nav.addView(navBtn("TRIP / BEST RUNS") {
            startActivity(Intent(this, TripActivity::class.java))
        })
        nav.addView(navBtn("SERWIS (MAINTENANCE)") {
            startActivity(Intent(this, MaintenanceActivity::class.java))
        })
        nav.addView(navBtn("E-RAPORT") {
            startActivity(Intent(this, EReportActivity::class.java))
        })
        nav.addView(navBtn("USTAWIENIA OBD") {
            startActivity(Intent(this, ObdSettingsActivity::class.java))
        })
        nav.addView(navBtn("WSTECZ (ZAMKNIJ)") { finish() })
        root.addView(nav)

        root.addView(ScrollView(this).apply { addView(dataTv) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        // OBD core: czarna skrzynka + analityka dzialaja tez poza UI
        ObdCoreService.start(this)

        scope.launch {
            manager.uiState.collectLatest { state -> render(state) }
        }
    }

    private fun navBtn(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            isAllCaps = false
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#0A84FF"))
            }
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 84
            ).apply { topMargin = 8 }
        }

    private fun render(s: pl.dock.widget.repository.VehicleUiState) {
        statusTv.text = when {
            s.connecting -> "Laczenie..."
            s.connected -> "Polaczono - " + s.adapterName
            else -> "Rozlaczono" + (s.lastError?.let { " - " + it } ?: "")
        }
        dataTv.text = buildString {
            appendLine("Protokol: " + (s.protocol.ifEmpty { "-" }))
            appendLine("Stan silnika: " + s.engineState)
            appendLine("Napiecie: " + fmt(s.voltage) + " V")
            appendLine("RPM: " + (if (s.rpm >= 0) s.rpm else "-") + stale(s.staleFlags.rpm))
            appendLine("Predkosc: " + (if (s.speedKmh >= 0) s.speedKmh else "-") + " km/h" + stale(s.staleFlags.speed))
            appendLine("Obciazenie: " + fmt(s.engineLoadPct) + " %" + stale(s.staleFlags.load))
            appendLine("TPS: " + fmt(s.tpsPct) + " %" + stale(s.staleFlags.tps))
            appendLine("MAP: " + fmt(s.mapKpa) + " kPa" + stale(s.staleFlags.map))
            appendLine("ECT: " + fmt(s.ectC) + " C" + stale(s.staleFlags.ect))
            appendLine("IAT: " + fmt(s.iatC) + " C" + stale(s.staleFlags.iat))
            appendLine("STFT: " + fmt(s.stftPct) + " %" + stale(s.staleFlags.stft))
            appendLine("LTFT: " + fmt(s.ltftPct) + " %" + stale(s.staleFlags.ltft))
        }
        ObdWidgetProvider.push(this, s.rpm, s.speedKmh, s.voltage, s.connected)
    }

    private fun fmt(v: Float): String = if (v.isNaN()) "-" else String.format("%.1f", v)

    private fun stale(flag: Boolean): String = if (flag) " [stale]" else ""

    override fun onStart() { super.onStart(); manager.onAppForeground() }
    override fun onStop() { super.onStop(); manager.onAppBackground() }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
