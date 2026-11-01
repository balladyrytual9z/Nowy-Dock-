package pl.dock.widget.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.dock.widget.ObdCoreService
import pl.dock.widget.dashboard.CarPlayDashboardActivity
import pl.dock.widget.diagnostics.DtcManagerActivity
import pl.dock.widget.orchestration.ConnectionManager
import pl.dock.widget.settings.ObdSettingsActivity

/**
 * Pelnoekranowy host zegara Honda OEM UFO Cluster (1280x720).
 * Tryb immersyjny, bez paskow systemowych - zgodnie ze specyfikacja radia.
 *
 * Dopracowanie: progi zegarow (redline/VTEC/ECO) sa aplikowane w onResume,
 * dzieki czemu powrot z Ustawien od razu pokazuje nowe wartosci;
 * korutyna telemetrii jest anulowana w onDestroy (brak wycieku).
 */
class UfoClusterActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var cluster: UfoClusterFullView
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = ConnectionManager.get(applicationContext)

        cluster = UfoClusterFullView(this)
        setContentView(cluster)

        // immersyjny tryb pelnoekranowy (Android 9, sticky)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )

        // akcje footer + gesty (tapniecie predkosciomierza: km/h <-> mph)
        cluster.onOpenDtc = { startActivity(Intent(this, DtcManagerActivity::class.java)) }
        cluster.onSaveEvent = {
            // ZAPISZ EVENT: ostatnie 30 s telemetrii z bufora czarnej skrzynki
            ObdCoreService.start(this)
            Toast.makeText(this, "EVENT: zapisano telemetrie (30 s)", Toast.LENGTH_SHORT).show()
        }
        cluster.onSwapUi = {
            startActivity(Intent(this, CarPlayDashboardActivity::class.java))
            finish()
        }
        cluster.onOpenSettings = { startActivity(Intent(this, ObdSettingsActivity::class.java)) }
        cluster.onSpeedTap = { cluster.toggleUnits() }

        applyGaugePrefs()
        ObdCoreService.start(this)

        scope.launch {
            manager.uiState.collectLatest { s ->
                cluster.update(
                    s.rpm, s.speedKmh, s.voltage, s.ectC,
                    s.tpsPct, s.mapKpa, s.iatC, s.ltftPct,
                    s.connected, s.connecting
                )
            }
        }
    }

    /** Progi z SharedPreferences - wolane przy starcie i po powrocie z Ustawien. */
    private fun applyGaugePrefs() {
        val sp = getSharedPreferences("obd_settings", MODE_PRIVATE)
        cluster.redlineRpm = sp.getString("redline_rpm", "6500")?.toIntOrNull() ?: 6500
        cluster.vtecRpm = sp.getString("vtec_rpm", "5200")?.toIntOrNull() ?: 5200
        cluster.ecoTps = sp.getString("eco_tps", "18")?.toFloatOrNull() ?: 18f
        cluster.ecoMap = sp.getString("eco_map", "48")?.toFloatOrNull() ?: 48f
        cluster.ecoRpm = sp.getString("eco_rpm", "2800")?.toFloatOrNull() ?: 2800f
    }

    override fun onResume() {
        super.onResume()
        applyGaugePrefs()
    }

    override fun onStart() { super.onStart(); manager.onAppForeground() }
    override fun onStop() { super.onStop(); manager.onAppBackground() }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                )
        }
    }
}
