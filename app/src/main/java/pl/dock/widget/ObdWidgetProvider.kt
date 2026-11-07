package pl.dock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import pl.dock.widget.repository.VehicleUiState
import pl.dock.widget.util.SolarTimes

/**
 * OBD widget na ekranie glownym radia (pkt 38):
 *  - tlo #1C1C1E, radius 16 dp, obwodka #2C2C2E; wariant dzienny
 *    (#F2F2F7 / #D1D1D6) ustawiany automatycznie wg NOAA Sunset
 *    Engine - jak cala apka (bez recznego przelacznika);
 *  - wartosci duze (bold) + jednostki mniejsze; dane wylacznie
 *    z VehicleUiState - brak odczytu = "--" (zakaz fabrykacji);
 *  - personalizacja: 1-4 parametry (sloty widget_p1..p4) wybierane
 *    w Ustawieniach sposrod WSZYSTKICH parametrow apki;
 *  - odswiezanie: ObdCoreService trzyma dane i pcha widzet najwyzej
 *    raz na 2 s (RemoteViews sa drogie - zaden 100 ms); systemowy
 *    updatePeriodMillis pozostaje tylko awaryjny (30 min);
 *  - klik w widzet otwiera ekran OBD Discovery.
 */
class ObdWidgetProvider : AppWidgetProvider() {

    companion object {
        const val STALE_MS = 5_000L

        /** Wszystkie parametry apki do wyboru na widzet (pkt 38). */
        val PARAMS = linkedMapOf(
            "RPM" to "obr/min",
            "SPEED" to "km/h",
            "LOAD" to "%",
            "TPS" to "%",
            "MAP" to "kPa",
            "ECT" to "C",
            "IAT" to "C",
            "STFT" to "%",
            "LTFT" to "%",
            "VOLT" to "V")

        @Volatile private var cached: VehicleUiState? = null
        @Volatile private var cachedUpdateMs: Long = 0L
        @Volatile private var lastWidgetPushMs: Long = 0L

        private val rowIds = intArrayOf(
            R.id.obd_p1_row, R.id.obd_p2_row, R.id.obd_p3_row, R.id.obd_p4_row)
        private val nameIds = intArrayOf(
            R.id.obd_p1_name, R.id.obd_p2_name, R.id.obd_p3_name, R.id.obd_p4_name)
        private val valIds = intArrayOf(
            R.id.obd_p1_val, R.id.obd_p2_val, R.id.obd_p3_val, R.id.obd_p4_val)
        private val unitIds = intArrayOf(
            R.id.obd_p1_unit, R.id.obd_p2_unit, R.id.obd_p3_unit, R.id.obd_p4_unit)

        /** Wolane przez ObdCoreService / ObdDiscoveryActivity przy swiezym stanie. */
        fun push(context: Context, s: VehicleUiState) {
            cached = s
            cachedUpdateMs = System.currentTimeMillis()
            // RemoteViews pushes are expensive - throttle to one per 2 s
            if (cachedUpdateMs - lastWidgetPushMs >= 2_000L) {
                lastWidgetPushMs = cachedUpdateMs
                updateAll(context)
            }
        }

        /** Sloty widget_p1..p4 z Ustawien; bez zapisu: RPM + SPEED. */
        private fun widgetParams(context: Context): List<String> {
            val sp = context.getSharedPreferences("obd_settings", Context.MODE_PRIVATE)
            val out = ArrayList<String>()
            for (i in 1..4) {
                val def = when (i) { 1 -> "RPM"; 2 -> "SPEED"; else -> "NONE" }
                val p = sp.getString("widget_p" + i, def) ?: def
                if (PARAMS.containsKey(p) && !out.contains(p)) out.add(p)
            }
            return out
        }

        private fun paramValue(name: String, s: VehicleUiState): String = when (name) {
            "RPM" -> if (s.rpm >= 0) s.rpm.toString() else "--"
            "SPEED" -> if (s.speedKmh >= 0) s.speedKmh.toString() else "--"
            "LOAD" -> if (!s.engineLoadPct.isNaN()) String.format("%.0f", s.engineLoadPct) else "--"
            "TPS" -> if (!s.tpsPct.isNaN()) String.format("%.0f", s.tpsPct) else "--"
            "MAP" -> if (!s.mapKpa.isNaN()) String.format("%.0f", s.mapKpa) else "--"
            "ECT" -> if (!s.ectC.isNaN()) String.format("%.0f", s.ectC) else "--"
            "IAT" -> if (!s.iatC.isNaN()) String.format("%.0f", s.iatC) else "--"
            "STFT" -> if (!s.stftPct.isNaN()) String.format("%.1f", s.stftPct) else "--"
            "LTFT" -> if (!s.ltftPct.isNaN()) String.format("%.1f", s.ltftPct) else "--"
            "VOLT" -> if (!s.voltage.isNaN()) String.format("%.1f", s.voltage) else "--"
            else -> "--"
        }

        private fun updateAll(context: Context) {
            val m = AppWidgetManager.getInstance(context)
            val ids = m.getAppWidgetIds(ComponentName(context, ObdWidgetProvider::class.java))
            if (ids == null || ids.isEmpty()) return
            m.updateAppWidget(ids, build(context))
        }

        private fun build(context: Context): RemoteViews {
            val sp = context.getSharedPreferences("obd_settings", Context.MODE_PRIVATE)
            // motyw dzien/noc jak w calej apce (pkt 34: NOAA Sunset Engine)
            val night = if (sp.getBoolean("night_auto", false))
                SolarTimes.isNight(
                    sp.getString("lat", "52.23")?.toDoubleOrNull() ?: 52.23,
                    sp.getString("lon", "21.01")?.toDoubleOrNull() ?: 21.01)
            else true
            val s = cached
            val stale = cachedUpdateMs > 0 &&
                System.currentTimeMillis() - cachedUpdateMs > STALE_MS

            val rv = RemoteViews(context.packageName, R.layout.obd_widget)
            rv.setInt(R.id.obd_widget_root, "setBackgroundResource",
                if (night) R.drawable.obd_widget_bg else R.drawable.obd_widget_bg_day)
            val cVal = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val cSub = if (night) 0x99FFFFFF.toInt() else 0x66000000
            rv.setTextColor(R.id.obd_title, cSub)
            rv.setTextColor(R.id.obd_stale, cSub)

            val chosen = widgetParams(context)
            for (i in 0 until 4) {
                if (i < chosen.size) {
                    val p = chosen[i]
                    rv.setViewVisibility(rowIds[i], View.VISIBLE)
                    rv.setTextViewText(nameIds[i], p)
                    rv.setTextColor(nameIds[i], cSub)
                    rv.setTextViewText(valIds[i], if (s == null) "--" else paramValue(p, s))
                    rv.setTextColor(valIds[i], cVal)
                    rv.setTextViewText(unitIds[i], PARAMS[p] ?: "")
                    rv.setTextColor(unitIds[i], cSub)
                } else {
                    rv.setViewVisibility(rowIds[i], View.GONE)
                }
            }
            rv.setTextViewText(R.id.obd_stale, when {
                s == null || !s.connected -> "OBD X"
                stale -> "stale"
                else -> ""
            })
            return rv
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // klik otwiera apke: ekran Honda OBD Discovery
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
