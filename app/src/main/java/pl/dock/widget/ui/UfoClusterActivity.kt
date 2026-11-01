package pl.dock.widget.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.dock.widget.ConfigActivity
import pl.dock.widget.ObdCoreService
import pl.dock.widget.dashboard.CarPlayDashboardActivity
import pl.dock.widget.diagnostics.DtcManagerActivity
import pl.dock.widget.orchestration.ConnectionManager
import pl.dock.widget.ui.UfoClusterFullView

/**
 * Pelnoekranowy host zegara Honda OEM UFO Cluster (1280x720).
 * Tryb immersyjny, bez paskow systemowych - zgodnie ze specyfikacja radia.
 */
class UfoClusterActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var cluster: UfoClusterFullView

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

        // akcje footer + gesty (akcja tapniecia predkosciomierza: km/h <-> mph)
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
        cluster.onOpenSettings = { startActivity(Intent(this, ConfigActivity::class.java)) }
        cluster.onSpeedTap = { cluster.toggleUnits() }

        ObdCoreService.start(this)

        MainScope().launch {
            manager.uiState.collectLatest { s ->
                cluster.update(
                    s.rpm, s.speedKmh, s.voltage, s.ectC,
                    s.tpsPct, s.mapKpa, s.iatC, s.ltftPct,
                    s.connected, s.connecting
                )
            }
        }
    }

    override fun onStart() { super.onStart(); manager.onAppForeground() }
    override fun onStop() { super.onStop(); manager.onAppBackground() }

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
