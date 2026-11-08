package pl.dock.widget.ui

import android.content.Context
import android.graphics.Canvas
import android.view.View
import pl.dock.widget.repository.VehicleUiState

/**
 * TEMPORARY STUB to keep the project compiling after size-limit issue in agent upload.
 * Full photorealistic version (matching the real car dashboard photos: blue illuminated
 * ring, yellow ticks/numbers, metal depth, realistic needle) is provided by the agent
 * as a downloadable file. Please replace this entire file with the provided
 * UfoClusterFullView_photorealistic.kt content.
 *
 * Visual changes only - no logic changes.
 */
class UfoClusterFullView(context: Context) : View(context) {
    fun update(s: VehicleUiState) {}
    fun toggleUnits() {}
    fun setNight(n: Boolean) {}
    fun setArcParam(left: Boolean, key: String) {}
    var redlineRpm = 6500
    var vtecRpm = 5200
    var ecoTps = 18f
    var ecoMap = 48f
    var ecoRpm = 2800f
    var ectMaxC = 102f
    var voltMin = 11.8f
    var voltMax = 14.8f
    var ltftMax = 10f
    var iatMax = 65f
    var offLeftParam = "VOLTS"
    var offRightParam = "AVG_TRIP_FUEL"
    var onOpenDtc: () -> Unit = {}
    var onSaveEvent: () -> Unit = {}
    var onSwapUi: () -> Unit = {}
    var onOpenSettings: () -> Unit = {}
    var onSpeedTap: () -> Unit = {}
    var onArcAssign: (left: Boolean) -> Unit = { _ -> }
    var onOpenEReport: () -> Unit = {}
    override fun onDraw(c: Canvas) {}
}
