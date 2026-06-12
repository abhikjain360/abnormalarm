package com.abhikjain360.abnormalarm.domain.model

/** Lifecycle for a saved timer. Running timers own one exact OS expiry trigger. */
enum class TimerState { IDLE, RUNNING, PAUSED, RINGING }

/**
 * A saved countdown timer. The saved [durationMillis] is the preset duration; running timers persist
 * [endAtMillis] so they survive process death and reboot without a background service.
 */
data class Timer(
    val id: Long = 0L,
    val label: String = "",
    val durationMillis: Long,
    val state: TimerState = TimerState.IDLE,
    val endAtMillis: Long? = null,
    val remainingMillis: Long? = null,
    val ring: RingSettings = RingSettings(snoozeEnabled = false),
)
