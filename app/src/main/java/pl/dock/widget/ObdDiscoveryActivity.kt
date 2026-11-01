package pl.dock.widget

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.dock.widget.orchestration.ConnectionManager
import pl.dock.widget.repository.VehicleUiState
import pl.dock.widget.ui.SpinnerView
import pl.dock.widget.ui.UfoClusterView

/**
 * Minimal host activity for Honda OBD Discovery.
 * Observes [ConnectionManager.uiState] â zero direct Bluetooth access.
 */
class ObdDiscoveryActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var statusTv: TextView
    private lateinit var dataTv: TextView
    private lateinit var spinner: SpinnerView
    private lateinit var cluster: UfoClusterView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        manager = ConnectionManager.get(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        statusTv = TextView(this).apply {
            textSize = 18f
            text = "Honda OBD Discovery"
        }
        dataTv = TextView(this).apply {
            textSize = 14f
            setPadding(0, 24, 0, 0)
        }
        spinner = SpinnerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(96, 96)
        }
        cluster = UfoClusterView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 320
            )
        }
        root.addView(statusTv)
        root.addView(cluster)
        root.addView(spinner)
        root.addView(ScrollView(this).apply { addView(dataTv) })
        setContentView(root)

        // OBD core: czarna skrzynka + analityka dzialaja tez poza UI
        ObdCoreService.start(this)

        // Observe UI state â pure MVVM boundary
        // (Activity is temporary host; production uses ViewModel + collectAsState)
        MainScope().launch {
            manager.uiState.collectLatest { state ->
                render(state)
            }
        }
    }

    private fun render(s: VehicleUiState) {
        statusTv.text = when {
            s.connecting -> "ÅÄczenieâ¦"
            s.connected -> "PoÅÄczono Â· ${s.adapterName}"
            else -> "RozÅÄczono${s.lastError?.let { " Â· $it" } ?: ""}"
        }
        spinner.visibility = if (s.connecting) android.view.View.VISIBLE else android.view.View.GONE
        cluster.update(s.rpm, s.speedKmh, s.voltage, s.ectC, s.connected)
        ObdWidgetProvider.push(this, s.rpm, s.speedKmh, s.voltage, s.connected)

        dataTv.text = buildString {
            appendLine("ProtokÃ³Å: ${s.protocol.ifEmpty { "â" }}")
            appendLine("Stan silnika: ${s.engineState}")
            appendLine("NapiÄcie: ${fmt(s.voltage)} V")
            appendLine("RPM: ${if (s.rpm >= 0) s.rpm else "â"} ${stale(s.staleFlags.rpm)}")
            appendLine("PrÄdkoÅÄ: ${if (s.speedKmh >= 0) s.speedKmh else "â"} km/h ${stale(s.staleFlags.speed)}")
            appendLine("ObciÄÅ¼enie: ${fmt(s.engineLoadPct)} % ${stale(s.staleFlags.load)}")
            appendLine("TPS: ${fmt(s.tpsPct)} % ${stale(s.staleFlags.tps)}")
            appendLine("MAP: ${fmt(s.mapKpa)} kPa ${stale(s.staleFlags.map)}")
            appendLine("ECT: ${fmt(s.ectC)} Â°C ${stale(s.staleFlags.ect)}")
            appendLine("IAT: ${fmt(s.iatC)} Â°C ${stale(s.staleFlags.iat)}")
            appendLine("STFT: ${fmt(s.stftPct)} % ${stale(s.staleFlags.stft)}")
            appendLine("LTFT: ${fmt(s.ltftPct)} % ${stale(s.staleFlags.ltft)}")
        }
    }

    private fun fmt(v: Float): String =
        if (v.isNaN()) "â" else String.format("%.1f", v)

    private fun stale(flag: Boolean): String = if (flag) "[stale]" else ""

    override fun onStop() {
        super.onStop()
        manager.onAppBackground()
    }

    override fun onStart() {
        super.onStart()
        manager.onAppForeground()
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.disconnect()
    }
}

