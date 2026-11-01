package pl.dock.widget

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Critical audio alerts (shift light, overheating > 102 °C).
 *
 * Requests AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK so media ducks to ~20 %
 * during the alert, plays a short beep via ToneGenerator and releases
 * the focus immediately after.
 */
class AudioAlertManager(context: Context) {

    companion object {
        private const val TAG = "AudioAlert"
        private const val MIN_INTERVAL_MS = 4_000L
        private const val BEEP_MS = 600
    }

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val tone: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 80)
    } catch (e: Exception) {
        null
    }

    @Volatile private var focusRequest: AudioFocusRequest? = null
    @Volatile private var lastAlertMs = 0L
    @Volatile private var lastKind: String? = null
    private var releaseRunnable: Runnable? = null

    /** Fire a critical alert; rate-limited per kind. */
    fun alert(kind: String) {
        val now = System.currentTimeMillis()
        if (kind == lastKind && now - lastAlertMs < MIN_INTERVAL_MS) return
        lastAlertMs = now
        lastKind = kind

        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { /* transient – released below */ }
                .build()
            focusRequest = req
            val res = audioManager.requestAudioFocus(req)
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
                Log.w(TAG, "Audio alert: $kind (media ducked)")
                // second beep for critical patterns
                Handler(Looper.getMainLooper()).postDelayed({
                    try { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS) } catch (ignored: Exception) {}
                }, (BEEP_MS + 150).toLong())
                releaseRunnable = Runnable {
                    try { audioManager.abandonAudioFocusRequest(req) } catch (ignored: Exception) {}
                }
                Handler(Looper.getMainLooper()).postDelayed(releaseRunnable!!, (BEEP_MS * 2 + 400).toLong())
            } else {
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, BEEP_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio alert failed: ${e.message}")
        }
    }

    fun release() {
        try {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            tone?.release()
        } catch (ignored: Exception) {}
        focusRequest = null
    }
}
