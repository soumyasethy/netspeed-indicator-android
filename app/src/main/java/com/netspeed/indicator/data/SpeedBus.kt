package com.netspeed.indicator.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A single live speed reading shared from the service to the UI. The hero,
 * previews and live themes all observe this one [StateFlow] and ease toward it.
 */
data class LiveSpeed(
    val running: Boolean = false,
    val downBytesPerSec: Long = 0L,
    val upBytesPerSec: Long = 0L,
    val todayBytes: Long = 0L,
    val peakBytesPerSec: Long = 0L,    // highest download rate seen today
    val lifetimeBytes: Long = 0L,      // all-time total (down+up), for the odometer
    /** True when the OS force-groups our notifications into one icon (e.g. One UI),
     *  so the side-by-side style fell back from two icons to one wide icon. */
    val dualIconsBlocked: Boolean = false,
) {
    /** Download speed in MB/s (binary), the unit the tier engine speaks. */
    val downMBps: Float get() = downBytesPerSec / 1_048_576f
}

/**
 * In-process channel from [com.netspeed.indicator.service.SpeedMeterService] to
 * the settings screen's live-preview card.
 *
 * A simple shared [StateFlow] is used rather than a bound Service: the only
 * cross-component data is one tiny immutable snapshot per second, and the UI is
 * a passive observer. Critically, the service is the *only* writer and it clears
 * state to `running = false` on stop/pause, so the preview can never show a
 * stale frozen speed after the ticker dies.
 */
object SpeedBus {
    private val _state = MutableStateFlow(LiveSpeed())
    val state: StateFlow<LiveSpeed> = _state.asStateFlow()

    fun publish(speed: LiveSpeed) {
        _state.value = speed
    }

    fun markStopped() {
        _state.value = LiveSpeed(running = false)
    }
}
