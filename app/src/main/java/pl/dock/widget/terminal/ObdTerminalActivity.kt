package pl.dock.widget.terminal

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pl.dock.widget.orchestration.ConnectionManager

/**
 * Terminal AT/OBD - surowe komendy do ELM327 przez ConnectionManager.
 * Odpowiedzi sa pokazywane 1:1 (dokladnie to, co zwrocil adapter):
 * zadnej interpretacji, zadnej fabryzacji.
 *
 * Fix: podwojny prompt ">>" przy wysylce (append sam dopisuje "> ").
 *
 * Tryb serwisowy (wg raportu Keihin):
 *  - TRYB SERWISOWY ON/OFF: odblokowuje komendy mutujace (10/27/30/31)
 *    w ElmSession - Read-Only Safety Lock domyslnie zablokowany.
 *  - Rzad komend serwisowych: 1003 (sesja rozszerzona), 2701 (seed),
 *    3E00 (TesterPresent), 3011 (test EVAP), 31011001 (reset adaptacji
 *    przepustnicy ETCS - NRC 22 jesli warunki niespelnione).
 *  - AUTO-KEY (2702): auto-nauka maski XOR - po jednej parze seed/key
 *    (zlegalnej sesji HDS) zapamietywana jest maska i klucz liczony
 *    automatycznie dla kazdego nastepnego seeda.
 *  - S3 KEEP-ALIVE: wysyla 3E00 co 4 s (timer S3 ECU = 5 s), inaczej
 *    ECU degraduje sesje i resetuje autoryzacje Security Access.
 */
class ObdTerminalActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var serviceBtn: Button
    private val log = StringBuilder()
    private val scope = kotlinx.coroutines.MainScope()
    private var keepAliveJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // pasek statusu Androida domyslnie schowany (immersive sticky)
        pl.dock.widget.ui.Immersive.apply(this)
        manager = ConnectionManager.get(applicationContext)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(24, 32, 24, 16)
        }

        root.addView(TextView(this).apply {
            text = "TERMINAL AT / OBD"
            textSize = 20f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, 0, 0, 12)
        })

        root.addView(backBtn())

        // TRYB SERWISOWY ON/OFF
        serviceBtn = Button(this).apply {
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 13f
            updateServiceLabel()
            setOnClickListener {
                val newVal = !manager.isServiceMode()
                manager.setServiceMode(newVal)
                updateServiceLabel()
                append(
                    if (newVal) "TRYB SERWISOWY WLACZONY - komendy 10/27/30/31 odblokowane. Uwaga: zmiana sesji 10 xx resetuje autoryzacje (LOCKED)."
                    else "TRYB SERWISOWY WYLACZONY - Read-Only Safety Lock aktywny (10/27/30/31 zablokowane).",
                    false
                )
                if (!newVal) stopKeepAlive()
            }
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#C41E1E"))
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
        }
        root.addView(serviceBtn)

        logView = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#30D158"))
            typeface = Typeface.MONOSPACE
            text = "> gotowy (polacz adapter, aby wysylac komendy)\n"
        }
        scroll = ScrollView(this).apply { addView(logView) }

        input = EditText(this).apply {
            hint = "np. 010C albo ATZ"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#8E8E93"))
            textSize = 15f
        }

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(input, LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = "WYSLIJ"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setOnClickListener { send() }
            background = GradientDrawable().apply {
                cornerRadius = 16f
                setColor(Color.parseColor("#0A84FF"))
            }
        })
        root.addView(row)

        // szybkie komendy
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (cmd in arrayOf("ATZ", "ATE0", "03", "07", "04", "010C")) {
            quick.addView(mkCmdBtn(cmd) { input.setText(cmd); send() })
        }
        root.addView(quick)

        // rzad komend serwisowych (dostepne tylko w trybie serwisowym)
        val svc1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        svc1.addView(mkCmdBtn("1003 sesja") { sendService("1003") })
        svc1.addView(mkCmdBtn("2701 seed") { sendService("2701") })
        svc1.addView(mkCmdBtn("AUTO-KEY 2702") { sendAutoKey() })
        svc1.addView(mkCmdBtn("3E00") { sendService("3E00") })
        root.addView(svc1)
        val svc2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        svc2.addView(mkCmdBtn("3011 EVAP") { sendService("3011") })
        svc2.addView(mkCmdBtn("31011001 przep") { sendService("31011001") })
        svc2.addView(mkCmdBtn("S3 KEEP-ALIVE") { toggleKeepAlive() })
        root.addView(svc2)

        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
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

    private fun mkCmdBtn(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            isAllCaps = false
            setOnClickListener { onClick() }
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#2C2C2E"))
            }
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(4, 8, 4, 8)
            }
        }

    private fun updateServiceLabel() {
        serviceBtn.text = if (manager.isServiceMode())
            "TRYB SERWISOWY: WLACZONY (kliknij, aby wylaczyc)"
        else "TRYB SERWISOWY: WYLACZONY (kliknij, aby wlaczyc)"
    }

    private fun send() {
        val cmd = input.text.toString().trim()
        if (cmd.isEmpty()) return
        append(cmd, true)
        input.setText("")
        scope.launch {
            val response = manager.rawRequest(cmd)
            if (cmd.uppercase().startsWith("2702")) learnMaskFromKey(cmd)
            append(if (response.isNullOrBlank()) "(brak odpowiedzi)" else response, false)
        }
    }

    /** Wysylka komendy serwisowej - ElmSession blokuje ja bez trybu serwisowego. */
    private fun sendService(cmd: String) {
        if (!manager.isServiceMode()) {
            append(cmd + " -> ZABLOKOWANE (wlacz TRYB SERWISOWY)", false)
            return
        }
        append(cmd, true)
        scope.launch {
            val response = manager.rawRequest(cmd)
            if (cmd.uppercase() == "2701") captureSeed(response)
            append(if (response.isNullOrBlank()) "(brak odpowiedzi)" else response, false)
        }
    }

    /**
     * Auto-nauka maski XOR (raport Keihin: key = seed XOR maska).
     * Po jednej zlegalnej parze seed/key (np. z loga HDS) maska jest
     * zapisana i kazdy nastepny seed obsluzony automatycznie.
     */
    private fun learnMaskFromKey(cmd: String) {
        val prefs = getSharedPreferences("obd_settings", MODE_PRIVATE)
        val seed = prefs.getString("last_seed", null) ?: return
        val keyHex = cmd.substring(4).trim()
        if (keyHex.isEmpty() || seed.isEmpty()) return
        val mask = xorHex(seed, keyHex) ?: return
        prefs.edit().putString("seed_xor_mask", mask).apply()
        append("Maska XOR zapisana (seed=" + seed + ", key=" + keyHex + ", maska=" + mask + ")", false)
    }

    private fun captureSeed(response: String?) {
        if (response == null) return
        val clean = response.replace(" ", "").replace("\r", "").replace("\n", "").uppercase()
        val idx = clean.indexOf("6701")
        if (idx < 0) {
            append("(brak 6701 w odpowiedzi - seed nieprzechwycony)", false)
            return
        }
        val seed = clean.substring(idx + 4).takeWhile { it.isDigit() || it in 'A'..'F' }
        if (seed.isEmpty()) return
        getSharedPreferences("obd_settings", MODE_PRIVATE)
            .edit().putString("last_seed", seed).apply()
        append("SEED zapisany: " + seed, false)
    }

    private fun sendAutoKey() {
        if (!manager.isServiceMode()) {
            append("AUTO-KEY -> ZABLOKOWANE (wlacz TRYB SERWISOWY)", false)
            return
        }
        val prefs = getSharedPreferences("obd_settings", MODE_PRIVATE)
        val seed = prefs.getString("last_seed", null)
        val mask = prefs.getString("seed_xor_mask", null)
        if (seed == null || mask == null) {
            append("AUTO-KEY -> brak danych: najpierw 2701 (seed), potem recznie 2702XXXX z legalnego loga HDS (nauka maski)", false)
            return
        }
        val key = xorHex(seed, mask)
        if (key == null) {
            append("AUTO-KEY -> blad obliczenia klucza", false)
            return
        }
        sendService("2702" + key)
    }

    private fun xorHex(a: String, b: String): String? {
        return try {
            val n = maxOf(a.length, b.length)
            val pa = a.padStart(n, '0')
            val pb = b.padStart(n, '0')
            val ia = pa.chunked(2).map { it.toInt(16) }
            val ib = pb.chunked(2).map { it.toInt(16) }
            val res = ia.zip(ib) { x, y -> x xor y }
            res.joinToString("") { String.format("%02X", it) }
        } catch (e: Exception) { null }
    }

    /** S3 TesterPresent co 4 s (timer S3 ECU = 5 s). */
    private fun toggleKeepAlive() {
        if (keepAliveJob?.isActive == true) {
            stopKeepAlive()
            append("S3 KEEP-ALIVE wylaczony", false)
        } else {
            if (!manager.isServiceMode()) {
                append("S3 KEEP-ALIVE -> najpierw wlacz TRYB SERWISOWY", false)
                return
            }
            keepAliveJob = scope.launch {
                while (isActive) {
                    manager.rawRequest("3E00")
                    delay(4000)
                }
            }
            append("S3 KEEP-ALIVE wlaczony (3E00 co 4 s, timer S3 ECU = 5 s)", false)
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    private fun append(line: String, sent: Boolean) {
        if (sent) {
            log.append("> ").append(line).append('\n')
        } else {
            log.append(line.replace("\r", "\n")).append('\n')
        }
        logView.text = log
        scroll.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) pl.dock.widget.ui.Immersive.apply(this)
    }

    override fun onDestroy() {
        stopKeepAlive()
        scope.cancel()
        super.onDestroy()
    }
}
