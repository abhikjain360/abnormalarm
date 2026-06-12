package com.abhikjain360.abnormalarm.domain.timer

/** Pure countdown display helpers for HH:MM:SS timer rows. */
object TimerCountdownDisplay {
    fun displaySeconds(remainingMillis: Long): Long =
        (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L

    /**
     * Milliseconds until [displaySeconds] will change. Null means the displayed value is already 0.
     */
    fun delayUntilNextDisplayChangeMillis(remainingMillis: Long): Long? {
        val remaining = remainingMillis.coerceAtLeast(0L)
        if (remaining == 0L) return null
        val seconds = displaySeconds(remaining)
        val nextThreshold = (seconds - 1L).coerceAtLeast(0L) * 1_000L
        return (remaining - nextThreshold).coerceAtLeast(1L)
    }
}
