package pl.dock.widget.blackbox

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Black Box UI: podglad ostatnich ramek telemetrii (SQLite WAL),
 * liczba zapisanych probek, eksport CSV przez share intent.
 * Odczyt przez SELECT cursor - bezpieczny przy aktywnym WAL.
 */
class BlackBoxActivity : Activity() {

    private lateinit var db: AppDatabase
    private lateinit var body: TextView
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(28, 36, 28, 20)
        }
        root.addView(TextView(this).apply {
            text = "BLACK BOX"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        })
        body = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#30D158"))
            typeface = Typeface.MONOSPACE
            setPadding(0, 16, 0, 16)
        }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(Button(this).apply {
            text = "ODSWIEZ"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#0A84FF"))
            }
            setOnClickListener { refresh() }
        })
        root.addView(Button(this).apply {
            text = "UDOSTEPNIJ CSV"
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 20f
                setColor(Color.parseColor("#2C2C2E"))
            }
            setOnClickListener {
                Toast.makeText(this@BlackBoxActivity, "Eksport: http://<IP-radia>:8080/csv", Toast.LENGTH_LONG).show()
                val csv = db.exportCsv(5000)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, csv.take(100_000))
                }
                startActivity(Intent.createChooser(send, "Black Box CSV"))
            }
        })
        setContentView(root)
        refresh()
    }

    private fun refresh() {
        val count = try { db.count() } catch (e: Exception) { -1L }
        val sb = StringBuilder()
        sb.append("Ramek w bazie: ").append(count).append('\n')
        sb.append("Server CSV: port 8080 (/csv)\n\n")
        try {
            val d = db.readableDatabase
            d.query("snapshots", null, null, null, null, null, "ts DESC", "60").use { c ->
                while (c.moveToNext()) {
                    sb.append(fmt.format(Date(c.getLong(0))))
                        .append(" V=").append(c.getFloat(1))
                        .append(" RPM=").append(c.getInt(2))
                        .append(" VSS=").append(c.getInt(3))
                        .append(" ECT=").append(c.getFloat(7))
                        .append(" LTFT=").append(c.getFloat(10))
                        .append(" EV=").append(c.getString(11))
                        .append('\n')
                }
            }
        } catch (e: Exception) {
            sb.append("(blad odczytu bazy)")
        }
        body.text = sb.toString()
    }

    override fun onDestroy() {
        super.onDestroy()
        db.close()
    }
}
