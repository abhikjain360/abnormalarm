package com.abhikjain360.abnormalarm.scheduling

/** Intent actions + extras for the alarm pipeline. Kept in one place to avoid string drift. */
object AlarmIntents {
    const val ACTION_ALARM_FIRE = "com.abhikjain360.abnormalarm.action.ALARM_FIRE"
    /** A snoozed re-fire: ring again WITHOUT advancing the repeat series (already rescheduled). */
    const val ACTION_SNOOZE_FIRE = "com.abhikjain360.abnormalarm.action.SNOOZE_FIRE"
    const val ACTION_RING_START = "com.abhikjain360.abnormalarm.action.RING_START"
    const val ACTION_TIMER_FIRE = "com.abhikjain360.abnormalarm.action.TIMER_FIRE"
    const val ACTION_TIMER_RING_START = "com.abhikjain360.abnormalarm.action.TIMER_RING_START"
    const val ACTION_TIMER_DISMISS = "com.abhikjain360.abnormalarm.action.TIMER_DISMISS"
    const val ACTION_DISMISS = "com.abhikjain360.abnormalarm.action.DISMISS"
    const val ACTION_SNOOZE = "com.abhikjain360.abnormalarm.action.SNOOZE"
    /** Posted by the lead-window timer (DESIGN.md §7). */
    const val ACTION_UPCOMING = "com.abhikjain360.abnormalarm.action.UPCOMING"
    /** Skip-next from the upcoming notification (DESIGN.md §7). */
    const val ACTION_SKIP = "com.abhikjain360.abnormalarm.action.SKIP"

    const val EXTRA_ALARM_ID = "com.abhikjain360.abnormalarm.extra.ALARM_ID"
    const val EXTRA_TIMER_ID = "com.abhikjain360.abnormalarm.extra.TIMER_ID"
    const val EXTRA_TRIGGER_MILLIS = "com.abhikjain360.abnormalarm.extra.TRIGGER_MILLIS"
    const val EXTRA_RING_KIND = "com.abhikjain360.abnormalarm.extra.RING_KIND"
    const val RING_KIND_ALARM = "alarm"
    const val RING_KIND_TIMER = "timer"
}
