package com.abhikjain360.abnormalarm.domain.model

import java.time.LocalTime

/** Where an alarm came from. Calendar alarms are read-only (owned by the calendar event). */
enum class AlarmSource { MANUAL, CALENDAR }

/**
 * Per-alarm ringing configuration (DESIGN.md §6). Defaults match the agreed creation-flow
 * defaults — see [[alarm-creation-defaults-principle]]: the user sets only the time; everything
 * here defaults silently.
 */
data class RingSettings(
    /** null = system default alarm ringtone; otherwise a content:// ringtone URI string. */
    val soundUri: String? = null,
    /** Seconds to ramp from quiet to full. 0 = blast at full system alarm volume immediately. */
    val volumeRampSeconds: Int = 0,
    val vibrate: Boolean = true,
    /** Strobe the camera flash while ringing (no CAMERA permission required). */
    val flashlight: Boolean = false,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 10,
    /** Stop ringing + mark missed after this many minutes if untouched. */
    val autoSilenceMinutes: Int = 10,
)

/**
 * A single alarm. Pure domain model (no Android / Room types) so it's shared across layers.
 * The Room entity in the data layer mirrors this; map between them at the repository boundary.
 *
 * Reliability model (DESIGN.md §2): an alarm is ALWAYS scheduled as a single exact
 * `setAlarmClock` for [nextTrigger]; on fire we recompute the next one. [firedCount] supports
 * [RepeatEnd.AfterCount]; [skipNextInstantMillis] supports per-alarm "skip next occurrence".
 */
data class Alarm(
    val id: Long = 0L,
    val label: String = "",
    val time: LocalTime,
    val enabled: Boolean = true,
    val repeat: RepeatRule = RepeatRule.Once,
    val end: RepeatEnd = RepeatEnd.Never,
    val ring: RingSettings = RingSettings(),
    val source: AlarmSource = AlarmSource.MANUAL,
    /** Epoch millis of an upcoming fire the user chose to skip; cleared once that fire passes. */
    val skipNextInstantMillis: Long? = null,
    val firedCount: Int = 0,
    // --- Calendar-sourced alarms only (null for MANUAL) ---
    /** Calendar backend that owns this row ("device" or "google"). */
    val calendarProvider: String? = null,
    /** Backend-specific calendar id. For Google this is usually an email or group id. */
    val calendarId: String? = null,
    /** Backend-specific event id. String because Google event ids are not numeric. */
    val calendarEventKey: String? = null,
    // Legacy local CalendarContract identity retained for migration/backward compatibility.
    val calendarEventId: Long? = null,
    /** Start-of-event epoch millis, to distinguish recurring-event instances. */
    val calendarInstanceStartMillis: Long? = null,
)
