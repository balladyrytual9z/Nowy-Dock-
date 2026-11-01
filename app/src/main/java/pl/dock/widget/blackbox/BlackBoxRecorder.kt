package pl.dock.widget.blackbox

import android.util.Log

/**
 * Black Box – RAM ring buffer (ArrayDeque) holding the last
 * [CAPACITY] telemetry frames (~5 minutes of driving at ~1 Hz).
 *
 * The buffer flushes into [AppDatabase] as ONE massive transaction,
 * but only when a harsh deceleration event is detected
 * (>= [TRIGGER_KMH_PER_S] km/h drop within one second).
 * Nothing is written to NAND during normal driving.
 */
class BlackBoxRecorder(private val db: AppDatabase) {

    companion object {
        private const val TAG = "BlackBox"
        const val CAPACITY = 300
        const val TRIGGER_KMH_PER_S = 10f     // harsh deceleration threshold
        const val EVENT = "HARSH_DECEL"
    }

    private val buffer = ArrayDeque<TelemetrySnapshot>(CAPACITY)

    @Volatile var lastFlushMs: Long = 0L
        private set
    @Volatile var totalFlushed: Long = 0L
        private set

    private var prevSpeed = -1
    private var prevTs = 0L

    /**
     * Feed one frame. Returns true when a harsh-deceleration event
     * triggered a flush to the database.
     */
    @Synchronized
    fun offer(frame: TelemetrySnapshot): Boolean {
        var triggered = false

        if (frame.speedKmh >= 0 && prevSpeed >= 0 && frame.speedKmh < prevSpeed) {
            val dt = (frame.timestampMs - prevTs).coerceAtLeast(1L)
            val dropPerSec = (prevSpeed - frame.speedKmh) * 1000f / dt
            if (dropPerSec >= TRIGGER_KMH_PER_S) triggered = true
        }
        if (frame.speedKmh >= 0) {
            prevSpeed = frame.speedKmh
            prevTs = frame.timestampMs
        }

        buffer.addLast(frame)
        while (buffer.size > CAPACITY) buffer.removeFirst()

        if (triggered) {
            flush(EVENT)
        }
        return triggered
    }

    /** Manual flush (whole buffer, single transaction). */
    @Synchronized
    fun flush(event: String = "") {
        if (buffer.isEmpty()) return
        val copy = ArrayList<TelemetrySnapshot>(buffer.size)
        for (i in buffer.indices) {
            val f = buffer.elementAt(i)
            copy += if (event.isEmpty()) f else f.copy(event = event)
        }
        db.insertSnapshots(copy)
        totalFlushed += copy.size
        lastFlushMs = System.currentTimeMillis()
        buffer.clear()
        Log.i(TAG, "Flushed ${copy.size} frames (event=$event)")
    }

    @Synchronized
    fun size(): Int = buffer.size
}
