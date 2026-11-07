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
import pl.dock.widget.diagnostics.KasujDtcActivity
import pl.dock.widget.orchestration.ConnectionManager
import pl.dock.widget.settings.ObdSettingsActivity

/**
 * Pelnoekranowy host zegara Honda OEM UFO Cluster (1280x720).
 * Tryb immersyjny, bez paskow systemowych - zgodnie ze specyfikacja radia.
 *
 * Progi zegarow i alarmow (redline/VTEC/ECO/ECT/VOLTS/LTFT/IAT) sa
 * aplikowane w onResume - powrot z Ustawien od razu pokazuje nowe
 * wartosci. Akcja tapniecia predkosciomierza jest konfigurowalna
 * (prefs speed_tap_action), zgodnie z pkt 9.7 specyfikacji.
 */
class UfoClusterActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var cluster: UfoClusterFullView
    private val scope = MainScope()
    private var speedTapAction = "units"

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

        // akcje footer + gesty (pkt 11 / 9.7 / 10.5)
        cluster.onOpenDtc = { startActivity(Intent(this, DtcManagerActivity::class.java)) }
        cluster.onSaveEvent = {
            // ZAPISZ EVENT: flush ostatnich ~30 s telemetrii do czarnej skrzynki
            val i = Intent(this, ObdCoreService::class.java)
            i.action = ObdCoreService.ACTION_SAVE_EVENT
            try {
                startForegroundService(i)
            } catch (e: Exception) {
                try { startService(i) } catch (ignored: Exception) {}
            }
            Toast.makeText(this, "EVENT: zapisano telemetrie (30 s)", Toast.LENGTH_SHORT).show()
        }
        cluster.onSwapUi = {
            startActivity(Intent(this, CarPlayDashboardActivity::class.java))
            finish()
        }
        cluster.onOpenSettings = { startActivity(Intent(this, ObdSettingsActivity::class.java)) }
        cluster.onSpeedTap = { onSpeedTapped() }
        cluster.onArcAssign = { left -> showArcAssignDialog(left) }

        applyGaugePrefs()
        ObdCoreService.start(this)

        scope.launch {
            manager.uiState.collectLatest { s -> cluster.update(s) }
        }
    }

    /**
     * Akcja tapniecia predkosciomierza - konfigurowalna (pkt 9.7):
     * units = km/h <-> mph, dtc = szybkie kasowanie DTC, none = brak.
     */
    private fun onSpeedTapped() {
        when (speedTapAction) {
            "units" -> cluster.toggleUnits()
            "dtc" -> startActivity(Intent(this, KasujDtcActivity::class.java))
            "none" -> Unit
        }
    }

    /**
     * Menu szybkiego przypisania parametru do luku (pkt 10.5 - long press).
     */
    private fun showArcAssignDialog(left: Boolean) {
        val params = UfoClusterFullView.ARC_PARAMS
        val labels = params.map { UfoClusterFullView.labelOf(it) }.toTypedArray()
        val title = if (left) "Lewy luk - parametr" else "Prawy luk - parametr"
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { d, which ->
                cluster.setArcParam(left, params[which])
                d.dismiss()
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    /** Progi z SharedPreferences - wolane przy starcie i po powrocie z Ustawien. */
    private fun applyGaugePrefs() {
        val sp = getSharedPreferences("obd_settings", MODE_PRIVATE)
        cluster.redlineRpm = sp.getString("redline_rpm", "6500")?.toIntOrNull() ?: 6500
        cluster.vtecRpm = sp.getString("vtec_rpm", "5200")?.toIntOrNull() ?: 5200
        cluster.ecoTps = sp.getString("eco_tps", "18")?.toFloatOrNull() ?: 18f
        cluster.ecoMap = sp.getString("eco_map", "48")?.toFloatOrNull() ?: 48f
        cluster.ecoRpm = sp.getString("eco_rpm", "2800")?.toFloatOrNull() ?: 2800f
        // progi alarmowe luk (pkt 10.7 specyfikacji)
        cluster.ectMaxC = sp.getString("alarm_ect_max", "102")?.toFloatOrNull() ?: 102f
        cluster.voltMin = sp.getString("alarm_volt_min", "11.8")?.toFloatOrNull() ?: 11.8f
        cluster.voltMax = sp.getString("alarm_volt_max", "14.8")?.toFloatOrNull() ?: 14.8f
        cluster.ltftMax = sp.getString("alarm_ltft_max", "10")?.toFloatOrNull() ?: 10f
        cluster.iatMax = sp.getString("alarm_iat_max", "65")?.toFloatOrNull() ?: 65f
        // akcja tapniecia predkosci (pkt 9.7): units | dtc | none
        speedTapAction = sp.getString("speed_tap_action", "units") ?: "units"
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
