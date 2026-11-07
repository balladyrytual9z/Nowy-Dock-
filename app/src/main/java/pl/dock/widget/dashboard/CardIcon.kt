package pl.dock.widget.dashboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * Ikona karty Overview (pkt 16: kazda karta ma ikone, 24 x 24 px).
 * Glify rysowane wektorowo na Canvas - bez plikow zasobow, jeden
 * spojny styl linii. Kolor #8E8E93 (neutral, czytelny w dzien i nocy).
 */
class CardIcon(private val glyph: Int) : Drawable() {

    companion object {
        const val TACHO = 0
        const val SPEEDO = 1
        const val BOLT = 2
        const val THERMO = 3
        const val WARNING = 4
        const val BARS = 5

        /** Dobor glify po naglowku karty. */
        fun forCard(caption: String): CardIcon = when (caption) {
            "RPM" -> CardIcon(TACHO)
            "SPEED" -> CardIcon(SPEEDO)
            "BATTERY" -> CardIcon(BOLT)
            "ECT" -> CardIcon(THERMO)
            "DTC" -> CardIcon(WARNING)
            else -> CardIcon(BARS)
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()

    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        paint.color = Color.parseColor("#8E8E93")
        path.reset()
        when (glyph) {
            TACHO -> {
                // tarcza obrotomierza + wskazowka
                c.drawArc(w * 0.1f, h * 0.1f, w * 0.9f, h * 0.9f, 150f, 240f, false, paint)
                c.drawLine(w / 2f, h / 2f, w * 0.78f, h * 0.3f, paint)
                c.drawPoint(w / 2f, h / 2f, paint)
            }
            SPEEDO -> {
                c.drawArc(w * 0.1f, h * 0.15f, w * 0.9f, h * 0.85f, 160f, 220f, false, paint)
                c.drawLine(w / 2f, h / 2f, w * 0.62f, h * 0.75f, paint)
                c.drawPoint(w / 2f, h / 2f, paint)
            }
            BOLT -> {
                // blyskawica (napiecie)
                path.moveTo(w * 0.55f, h * 0.05f)
                path.lineTo(w * 0.3f, h * 0.55f)
                path.lineTo(w * 0.5f, h * 0.55f)
                path.lineTo(w * 0.45f, h * 0.95f)
                path.lineTo(w * 0.7f, h * 0.45f)
                path.lineTo(w * 0.5f, h * 0.45f)
                path.close()
                c.drawPath(path, paint)
            }
            THERMO -> {
                // termometr
                c.drawLine(w * 0.5f, h * 0.1f, w * 0.5f, h * 0.6f, paint)
                paint.style = Paint.Style.FILL
                c.drawCircle(w * 0.5f, h * 0.75f, w * 0.16f, paint)
                paint.style = Paint.Style.STROKE
            }
            WARNING -> {
                // trojkat ostrzegawczy + wykrzyknik
                path.moveTo(w * 0.5f, h * 0.08f)
                path.lineTo(w * 0.95f, h * 0.88f)
                path.lineTo(w * 0.05f, h * 0.88f)
                path.close()
                c.drawPath(path, paint)
                c.drawLine(w * 0.5f, h * 0.38f, w * 0.5f, h * 0.6f, paint)
                c.drawPoint(w * 0.5f, h * 0.74f, paint)
            }
            else -> {
                // slupki sygnalu (status OBD)
                c.drawLine(w * 0.12f, h * 0.85f, w * 0.12f, h * 0.6f, paint)
                c.drawLine(w * 0.38f, h * 0.85f, w * 0.38f, h * 0.42f, paint)
                c.drawLine(w * 0.64f, h * 0.85f, w * 0.64f, h * 0.2f, paint)
                c.drawLine(w * 0.9f, h * 0.85f, w * 0.9f, h * 0.06f, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {}

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = 24

    override fun getIntrinsicHeight(): Int = 24
}
