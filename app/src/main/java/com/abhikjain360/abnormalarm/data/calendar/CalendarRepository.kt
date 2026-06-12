package com.abhikjain360.abnormalarm.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/** A calendar synced on the device (DESIGN.md §5). */
data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
)

/**
 * One alarm to materialize from a calendar event (DESIGN.md §5). One per usable reminder row;
 * a single event can yield several (e.g. a 10-min and a 1-day reminder).
 */
data class CalendarAlarmCandidate(
    val provider: String,
    val calendarId: String,
    val eventKey: String,
    val instanceStartMillis: Long,
    /** When the alarm should fire = instance start − reminder offset. */
    val fireTimeMillis: Long,
    val title: String,
)

/**
 * Reads the device's calendars via [CalendarContract] — the already-synced Google Calendar, no
 * OAuth, no network (DESIGN.md §5). All queries are read-only and gated on [READ_CALENDAR].
 */
class CalendarRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** All calendars on the device, for the per-calendar toggle list in settings. */
    fun queryCalendars(): List<DeviceCalendar> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        val out = mutableListOf<DeviceCalendar>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                out += DeviceCalendar(
                    id = c.getLong(0),
                    displayName = c.getString(1) ?: "(unnamed)",
                    accountName = c.getString(2) ?: "",
                    color = if (c.isNull(3)) 0 else c.getInt(3),
                )
            }
        }
        return out
    }

    /**
     * Events in [windowStartMillis, windowEndMillis) that qualify (DESIGN.md §5 selection rules),
     * expanded into one [CalendarAlarmCandidate] per usable reminder (with a 5-min fallback), and
     * skipping calendars in [disabledCalendarIds]. Only candidates whose fire time is still in the
     * future relative to [windowStartMillis] are returned.
     */
    fun queryCandidates(
        windowStartMillis: Long,
        windowEndMillis: Long,
        disabledCalendarIds: Set<Long>,
    ): List<CalendarAlarmCandidate> {
        if (!hasPermission()) return emptyList()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.HAS_ALARM,
        )

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, windowStartMillis)
        android.content.ContentUris.appendId(builder, windowEndMillis)

        val out = mutableListOf<CalendarAlarmCandidate>()
        context.contentResolver.query(builder.build(), projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val calendarId = c.getLong(1)
                if (calendarId in disabledCalendarIds) continue
                if (c.getInt(4) == 1) continue // all-day excluded

                val status = c.getInt(5)
                if (!attendeeStatusQualifies(status)) continue

                val eventId = c.getLong(0)
                val begin = c.getLong(2)
                val title = c.getString(3) ?: "(busy)"
                val hasAlarm = c.getInt(6) == 1

                val offsets = if (hasAlarm) reminderOffsetMinutes(eventId) else emptyList()
                val effective = offsets.ifEmpty { listOf(FALLBACK_LEAD_MINUTES) }
                for (mins in effective) {
                    val fire = begin - mins * 60_000L
                    if (fire > windowStartMillis && fire < windowEndMillis) {
                        out += CalendarAlarmCandidate(
                            provider = CalendarProviders.DEVICE,
                            calendarId = calendarId.toString(),
                            eventKey = eventId.toString(),
                            instanceStartMillis = begin,
                            fireTimeMillis = fire,
                            title = title,
                        )
                    }
                }
            }
        }
        return out
    }

    /** Usable reminder offsets (minutes-before) for an event: METHOD_ALERT/DEFAULT only. */
    private fun reminderOffsetMinutes(eventId: Long): List<Int> {
        val projection = arrayOf(CalendarContract.Reminders.MINUTES, CalendarContract.Reminders.METHOD)
        val out = mutableListOf<Int>()
        context.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            projection,
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { c: Cursor ->
            while (c.moveToNext()) {
                val method = c.getInt(1)
                if (method != CalendarContract.Reminders.METHOD_ALERT &&
                    method != CalendarContract.Reminders.METHOD_DEFAULT
                ) {
                    continue
                }
                val mins = c.getInt(0)
                // MINUTES_DEFAULT (-1) ⇒ calendar default we can't resolve here; use the fallback.
                out += if (mins < 0) FALLBACK_LEAD_MINUTES else mins
            }
        }
        return out
    }

    private fun attendeeStatusQualifies(status: Int): Boolean = when (status) {
        CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED,
        CalendarContract.Attendees.ATTENDEE_STATUS_NONE,
        -> true
        // DECLINED / INVITED / TENTATIVE excluded (DESIGN.md §5).
        else -> false
    }

    companion object {
        /** DESIGN.md §5: events with no usable reminder fall back to one alarm 5 min before. */
        const val FALLBACK_LEAD_MINUTES = 5
    }
}
