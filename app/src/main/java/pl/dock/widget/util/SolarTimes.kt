package pl.dock.widget.util

import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Kalkulator czasow wschodu/zachodu slonca (algorytm NOAA).
 * Dziala w pelni offline - dla radia samochodowego bez stalego internetu
 * to jedyny niezawodny zrodlo przejscia dzien/noc.
 */
object SolarTimes {

    /** Zwraca [wschod, zachod] w minutach od polnocy lokalnego czasu; null gdy doba polarna. */
    fun sunriseSunset(lat: Double, lon: Double, day: Calendar): DoubleArray? {
        val year = day.get(Calendar.YEAR)
        val month = day.get(Calendar.MONTH) + 1
        val dayOfMonth = day.get(Calendar.DAY_OF_MONTH)

        // julianski dzien o lokalnym poludniu
        val jd = julianDay(year, month, dayOfMonth) + 0.5

        val n = jd - 2451545.0
        // fotografia algorytmu NOAA (uproszczona forma, dokladnosc +/- 2 min)
        val meanSolarTime = n - lon / 360.0
        val meanAnomaly = (357.5291 + 0.98560028 * meanSolarTime) * PI / 180.0
        val trueLongitude = (meanAnomaly * 180.0 / PI +
            (1.9148 * sin(meanAnomaly) + 0.02 * sin(2 * meanAnomaly) + 0.0003 * sin(3 * meanAnomaly))) *
            PI / 180.0
        val omega = 125.04 * PI / 180.0 - 0.0529546 * n * PI / 180.0
        val lambda = trueLongitude - 0.00569 * PI / 180.0 - 0.00478 * PI / 180.0 * sin(omega)
        val declination = asin(sin(lambda) * sin(23.439 * PI / 180.0))

        val eqTime = 4.0 * (
            0.00007 + 0.00185 * cos(meanAnomaly) - 0.033 * sin(meanAnomaly) * (1.0 - 0.00047 * sin(2 * meanAnomaly))
            - 0.00759 * sin(2.0 * meanAnomaly)
            ) * 60.0 / (2.0 * PI) * 2.0 * PI / 60.0

        // przyblizona deklinacja dla sunrise/set z formula NOAA
        val decl = declination * 180.0 / PI
        val hourAngle = hourAngleDeg(decl, lat)

        if (hourAngle.isNaN()) return null // doba polarna

        val sunrise = 720.0 - 4.0 * (lon + hourAngle) - eqTime
        val sunset = 720.0 - 4.0 * (lon - hourAngle) - eqTime
        return doubleArrayOf(normalizeMinutes(sunrise), normalizeMinutes(sunset))
    }

    private fun hourAngleDeg(declDeg: Double, lat: Double): Double {
        val zen = 90.833 * PI / 180.0 // oficjalny wschod/zachod z refrakcja
        val latR = lat * PI / 180.0
        val dR = declDeg * PI / 180.0
        val ha = acos(
            cos(zen) / (cos(latR) * cos(dR)) - tan(latR) * tan(dR)
        )
        if (ha.isNaN()) return Double.NaN
        return ha * 180.0 / PI
    }

    private fun normalizeMinutes(m: Double): Double = ((m % 1440.0) + 1440.0) % 1440.0

    /** Czy obecnie jest noc (wg srednika slonecznego)? */
    fun isNight(lat: Double, lon: Double, now: Calendar = Calendar.getInstance()): Boolean {
        val ss = sunriseSunset(lat, lon, now) ?: return now.get(Calendar.HOUR_OF_DAY) >= 20 ||
            now.get(Calendar.HOUR_OF_DAY) < 6
        val minutesNow = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        return minutesNow < ss[0] || minutesNow > ss[1]
    }

    private fun julianDay(y: Int, m: Int, d: Int): Double {
        val yy = if (m <= 2) y - 1 else y
        val mm = if (m <= 2) m + 12 else m
        val a = yy / 100
        val b = 2 - a + a / 4
        return kotlin.math.floor(365.25 * (yy + 4716)) +
            kotlin.math.floor(30.6001 * (mm + 1)) + d + b - 1524.5
    }

    fun fmtMinutes(min: Double): String {
        val h = (min / 60.0).toInt()
        val m = (min - h * 60).toInt()
        return String.format("%02d:%02d", h, m)
    }
}
