package pl.dock.widget.session

import pl.dock.widget.repository.EngineStateLabel

/**
 * Cranking / low-voltage protection.
 *
 * Block PID traffic when:
 *  - voltage < 11.0 V  OR
 *  - RPM in 1..450 (cranking)
 * Resume when RPM > 600 AND voltage > 13.2 V.
 */
class EngineState {

    @Volatile var voltage: Float = Float.NaN
        private set
    @Volatile var rpm: Int = -1
        private set
    @Volatile var label: EngineStateLabel = EngineStateLabel.UNKNOWN
        private set

    /** True when PID polling must be suppressed. */
    @Volatile var protected: Boolean = false
        private set

    fun updateVoltage(v: Float) {
        voltage = v
        recompute()
    }

    fun updateRpm(r: Int) {
        rpm = r
        recompute()
    }

    private fun recompute() {
        val lowVolt = !voltage.isNaN() && voltage < 11.0f
        val cranking = rpm in 1..450
        val running = rpm > 600 && !voltage.isNaN() && voltage > 13.2f

        when {
            lowVolt || cranking -> {
                protected = true
                label = if (cranking) EngineStateLabel.CRANKING else EngineStateLabel.PROTECTED
            }
            running -> {
                protected = false
                label = EngineStateLabel.RUNNING
            }
            rpm == 0 || (rpm < 0 && !voltage.isNaN() && voltage < 12.0f) -> {
                protected = false
                label = EngineStateLabel.OFF
            }
            else -> {
                // transitional â keep previous protected flag
                if (!protected) label = EngineStateLabel.UNKNOWN
            }
        }
    }

    fun reset() {
        voltage = Float.NaN
        rpm = -1
        label = EngineStateLabel.UNKNOWN
        protected = false
    }
}

