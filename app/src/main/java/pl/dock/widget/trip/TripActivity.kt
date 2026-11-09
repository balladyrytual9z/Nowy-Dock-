package pl.dock.widget.trip

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pl.dock.widget.orchestration.ConnectionManager
import pl.dock.widget.repository.VehicleUiState

/**
 * Trip / Fuel / Best Runs:
 * - licznik podrózy (czas jazdy, dystans z VSS, max RPM, max predkosc)
 * - Best Runs: pomiar 0-100 km/h (start przy przekroczeniu 5 km/h po postoju,
 *   stop przy 100 km/h); najlepszy czas zapisany w SharedPreferences.
 * Zero integracji z GPS - calosc z telemetrii OBD.
 *
 * Fix: korutyna zbierajaca telemetrie jest anulowana w onDestroy
 * (wczesniej zbierala dane w nieskonczonosc po zamknieciu ekranu).
 *
 * Fuel (dokumenty OBD: FAZA 4 "Trip Financial & Fuel Calculator",
 * obd part 2 #13 i #31): zuzycie SZACOWANE z MAF (PID 0110), bo ECU
 * L13Z1 nie raportuje wtrysku paliwa jako PID. Chwilowo L/100 km
 * w jezdzie, L/h na postoju; koszt trasy przy cenie paliwa z pref
 * "fuel_price_gr" [gr/litr]. Kazda wartosc paliwowa jest oznaczona
 * "(szac.)" - zgodnie z zasada: nie przedstawiaj estymacji jako
 * pomiaru ECU.
 */
class TripActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var body: TextView
    private val scope: CoroutineScope = MainScope()

    // trip
    private var tripMs = 0L
    private var tripMeters = 0.0
    private var maxRpm = 0
    private var maxSpeed = 0
    private var ltftSum = 0.0
    private var ltftCount = 0L
    private var lastTs = 0L

    // best run 0-100
    private var runStartMs = 0L
    private var runActive = false
    private var lastSpeed = 0
    private var bestRunMs = 0L

    // fuel estimate (MAF PID 0110)
    private val fuel = FuelCostCalculator()
    private var mafGs = Float.NaN
    private var curSpeed = -1
    private var priceGr = 650

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // pasek statusu Androida domyslnie schowany (immersive sticky)
        pl.dock.widget.ui.Immersive.apply(this)
        manager = ConnectionManager.get(applicationContext)
        bestRunMs = getSharedPreferences("obd_settings", MODE_PRIVATE).getLong("best_0_100", 0L)
        priceGr = getSharedPreferences("obd_settings", MODE_PRIVATE).getInt("fuel_price_gr", 650)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(28, 36, 28, 20)
        }
        root.addView(TextView(this).apply {
            text = "TRIP / FUEL / BEST RUNS"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        root.addView(backBtn())
        body = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#E5E5EA"))
            setPadding(0, 16, 0, 16)
        }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Button(this).apply {
            text = "RESET PODROZY"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#2C2C2E"))
            }
            setOnClickListener {
                tripMs = 0; tripMeters = 0.0; maxRpm = 0; maxSpeed = 0
                ltftSum = 0.0; ltftCount = 0; lastTs = 0
                runActive = false; lastSpeed = 0
                fuel.reset(); mafGs = Float.NaN
                render("Podroz zresetowana")
            }
        })
        root.addView(Button(this).apply {
            text = "CENA PALIWA (gr/L)"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#2C2C2E"))
            }
            setOnClickListener { priceDialog() }
        })
        setContentView(root)

        scope.launch {
            manager.uiState.collectLatest { s -> onSample(s) }
        }
    }

    private fun backBtn(): Button =
        Button(this).apply {
            text = "WSTECZ (ZAMKNIJ)"
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 13f
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#2C2C2E"))
            }
            setOnClickListener { finish() }
        }

    private fun onSample(s: VehicleUiState) {
        val now = System.currentTimeMillis()
        fuel.integrate(s.mafGs, now)
        mafGs = s.mafGs
        if (s.speedKmh >= 0) curSpeed = s.speedKmh
        if (lastTs != 0L && s.speedKmh >= 0) {
            val dt = (now - lastTs).coerceAtMost(3000)
            tripMs += dt
            tripMeters += s.speedKmh * (dt / 3_600_000.0)
            if (!s.ltftPct.isNaN()) { ltftSum += s.ltftPct; ltftCount++ }
        }
        lastTs = now
        if (s.rpm > maxRpm && s.rpm >= 0) maxRpm = s.rpm
        if (s.speedKmh > maxSpeed && s.speedKmh >= 0) maxSpeed = s.speedKmh

        // Best run 0-100
        if (s.speedKmh >= 0) {
            if (!runActive && lastSpeed <= 2 && s.speedKmh > 5) {
                runActive = true
                runStartMs = now
            }
            if (runActive && s.speedKmh >= 100) {
                runActive = false
                val t = now - runStartMs
                if (t in 3000..60000 && (bestRunMs == 0L || t < bestRunMs)) {
                    bestRunMs = t
                    getSharedPreferences("obd_settings", MODE_PRIVATE)
                        .edit().putLong("best_0_100", t).apply()
                }
            }
            lastSpeed = s.speedKmh
        }
        render(null)
    }

    private fun render(toast: String?) {
        val sb = StringBuilder()
        sb.append("Czas podrozy: ").append(fmtTime(tripMs)).append('\n')
        sb.append("Dystans: ").append(String.format("%.1f km", tripMeters / 1000.0)).append('\n')
        sb.append("Max predkosc: ").append(maxSpeed).append(" km/h\n")
        sb.append("Max RPM: ").append(maxRpm).append('\n')
        val avgLtft = if (ltftCount > 0) ltftSum / ltftCount else Double.NaN
        sb.append("Srednie LTFT: ")
            .append(if (avgLtft.isNaN()) "-" else String.format("%.1f %%", avgLtft)).append('\n')
        sb.append('\n')
        appendFuel(sb)
        sb.append('\n')
        sb.append("BEST 0-100 km/h: ")
            .append(if (bestRunMs == 0L) "brak pomiaru" else String.format("%.1f s", bestRunMs / 1000.0))
            .append('\n')
        if (runActive) sb.append("POMIAR W TOKU...\n")
        sb.append("(start: rusz z miejsca; stop: osiagnij 100 km/h)")
        body.text = sb.toString()
        if (toast != null) body.text = toast + "\n\n" + sb.toString()
    }

    /** Sekcja paliwowa - szacunki z MAF, oznaczone "(szac.)". */
    private fun appendFuel(sb: StringBuilder) {
        sb.append("Paliwo chwilowo: ")
        if (mafGs.isNaN()) {
            sb.append("- (brak MAF, PID 0110)\n")
        } else {
            val inst = fuel.instant(mafGs, curSpeed)
            if (inst.idle) {
                sb.append(String.format("%.2f L/h (postoj, szac.)\n", inst.lPerHour))
            } else {
                sb.append(String.format("%.1f L/100 km (szac.)\n", inst.lPer100km))
            }
        }
        sb.append("Zuzycie trasy: ")
        if (fuel.totalLiters > 0.0) sb.append(String.format("%.2f L (szac.)\n", fuel.totalLiters))
        else sb.append("- (szac. z MAF)\n")
        val avgFuel = fuel.avgLitersPer100km(tripMeters)
        sb.append("Srednie zuzycie: ")
            .append(if (avgFuel.isNaN()) "-" else String.format("%.1f L/100 km (szac.)", avgFuel))
            .append('\n')
        sb.append("Koszt trasy: ")
        when {
            priceGr <= 0 -> sb.append("ustaw cene paliwa\n")
            fuel.totalLiters > 0.0 -> sb.append(String.format("%.2f PLN (po %.2f PLN/L)\n",
                fuel.costPln(priceGr), priceGr / 100.0))
            else -> sb.append("-\n")
        }
    }

    /** Dialog ceny paliwa [gr/litr], zapis do "obd_settings". */
    private fun priceDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(priceGr.toString())
        }
        AlertDialog.Builder(this)
            .setTitle("Cena paliwa (gr/litr)")
            .setMessage("Przyklad: 650 = 6.50 PLN za litr")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val v = input.text.toString().trim().toIntOrNull() ?: return@setPositiveButton
                if (v in 100..3000) {
                    priceGr = v
                    getSharedPreferences("obd_settings", MODE_PRIVATE)
                        .edit().putInt("fuel_price_gr", v).apply()
                    render("Cena paliwa: " + String.format("%.2f PLN/L", v / 100.0))
                }
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun fmtTime(ms: Long): String {
        val s = ms / 1000
        return String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) pl.dock.widget.ui.Immersive.apply(this)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
