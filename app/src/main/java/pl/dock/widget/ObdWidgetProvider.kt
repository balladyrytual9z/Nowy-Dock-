package pl.dock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * OBD widget (1 cell, 25 dp).
 *
 * The system throttles background widget updates, so the widget marks
 * displayed data as STALE whenever the cached telemetry is older than
 * [STALE_MS].
 */
class ObdWidgetProvider : AppWidgetProvider() {

    companion object {
        const val STALE_MS = 5_000L

        /** process-wide telemetry cache, fed by ObdCoreService */
        @Volatile var cachedRpm: Int = -1
        @Volatile var cachedSpeedKmh: Int = -1
        @Volatile var cachedVoltage: Float = Float.NaN
        @Volatile var cachedConnected: Boolean = false
        @Volatile var cachedUpdateMs: Long = 0L
        @Volatile private var lastWidgetPushMs = 0L

        /** Called by ObdCoreService whenever a fresh state arrives. */
        fun push(context: Context, rpm: Int, speedKmh: Int, voltage: Float, connected: Boolean) {
            cachedRpm = rpm
            cachedSpeedKmh = speedKmh
            cachedVoltage = voltage
            cachedConnected = connected
            cachedUpdateMs = System.currentTimeMillis()
            // RemoteViews pushes are expensive - throttle to one per 2 s
            if (cachedUpdateMs - lastWidgetPushMs >= 2_000L) {
                lastWidgetPushMs = cachedUpdateMs
                updateAll(context)
            }
        }

        private fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, ObdWidgetProvider::class.java))
            if (ids == null || ids.isEmpty()) return
            m.updateAppWidget(ids, build(context))
        }

        private fun build(context: Context): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.obd_widget)
            val stale = cachedUpdateMs > 0 &&
                System.currentTimeMillis() - cachedUpdateMs > STALE_MS

            val rpmText = if (cachedRpm >= 0) cachedRpm.toString() else "--"
            val spdText = if (cachedSpeedKmh >= 0) cachedSpeedKmh.toString() else "--"
            rv.setTextViewText(R.id.obd_rpm, rpmText)
            rv.setTextViewText(R.id.obd_speed, spdText)
            rv.setTextViewText(
                R.id.obd_stale,
                when {
                    !cachedConnected -> "OBD X"
                    stale -> "stale"
                    else -> ""
                }
            )
            return rv
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // open the OBD discovery screen on tap
        val i = Intent(context, ObdDiscoveryActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val rv = build(context)
        rv.setOnClickPendingIntent(R.id.obd_widget_root, pi)
        manager.updateAppWidget(appWidgetIds, rv)
    }
}
