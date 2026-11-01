package pl.dock.widget.terminal

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import pl.dock.widget.orchestration.ConnectionManager

/**
 * Terminal AT/OBD - surowe komendy do ELM327 przez ConnectionManager.
 * Odpowiedzi sa pokazywane 1:1 (dokladnie to, co zwrocil adapter):
 * zadnej interpretacji, zadnej fabryzacji.
 */
class ObdTerminalActivity : Activity() {

    private lateinit var manager: ConnectionManager
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private val log = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            quick.addView(Button(this).apply {
                text = cmd
                textSize = 12f
                setTextColor(Color.WHITE)
                isAllCaps = false
                setOnClickListener {
                    input.setText(cmd)
                    send()
                }
                background = GradientDrawable().apply {
                    cornerRadius = 20f
                    setColor(Color.parseColor("#2C2C2E"))
                }
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(4, 8, 4, 8)
                }
            })
        }
        root.addView(quick)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun send() {
        val cmd = input.text.toString().trim()
        if (cmd.isEmpty()) return
        append("> " + cmd, true)
        input.setText("")
        MainScope().launch {
            val response = manager.rawRequest(cmd)
            append(if (response.isNullOrBlank()) "(brak odpowiedzi)" else response, false)
        }
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
}
