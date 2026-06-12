package com.abhikjain360.abnormalarm.data.calendar

import android.content.Context
import com.abhikjain360.abnormalarm.data.settings.SettingsRepository
import com.abhikjain360.abnormalarm.domain.AlarmRepository
import com.abhikjain360.abnormalarm.domain.model.Alarm
import com.abhikjain360.abnormalarm.domain.model.AlarmSource
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import com.abhikjain360.abnormalarm.scheduling.AlarmScheduler
import java.time.Instant
import java.time.ZoneId

/**
 * Materializes calendar events into alarm rows and schedules them (DESIGN.md §5).
 *
 * Reconciliation is idempotent: each run computes the desired set of calendar alarms inside the
 * rolling [HORIZON_HOURS] window and diffs it against what's stored. Existing rows are preserved
 * (so a per-instance "dismiss" — which disables the row — and skip/firedCount survive re-syncs);
 * vanished/past instances are deleted. Triggered by the ContentObserver, periodic worker, boot, app
 * foreground, and manual refresh.
 */
class CalendarSync(
    private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val calendarRepository: CalendarRepository,
    private val googleCalendarApiRepository: GoogleCalendarApiRepository,
    private val settingsRepository: SettingsRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** Run one reconciliation pass. Safe to call from any background context. */
    suspend fun sync(nowMillis: Long) {
        val settings = settingsRepository.current()
        val existing = alarmRepository.getAll().filter { it.source == AlarmSource.CALENDAR }

        // Feature off ⇒ tear down every calendar alarm.
        if (!settings.calendarFeedEnabled) {
            existing.forEach {
                alarmScheduler.cancel(it.id)
                alarmRepository.delete(it.id)
            }
            return
        }

        val windowEnd = nowMillis + HORIZON_HOURS * 3_600_000L
        val candidates = mutableListOf<CalendarAlarmCandidate>()
        val reconciledProviders = mutableSetOf<String>()

        // The legacy on-device provider may be empty on current Google Calendar builds, but keep
        // it because it still works for local/OEM/Exchange calendars when present.
        if (calendarRepository.hasPermission()) {
            candidates += calendarRepository.queryCandidates(
                nowMillis, windowEnd, settings.disabledCalendarIds,
            )
            reconciledProviders += CalendarProviders.DEVICE
        } else {
            // Permission revoked: remove stale device-provider rows while preserving Google API rows.
            reconciledProviders += CalendarProviders.DEVICE
        }

        if (settings.googleCalendarApiEnabled && settings.connectedGoogleAccounts.isNotEmpty()) {
            for (accountEmail in settings.connectedGoogleAccounts) {
                val accessToken = GoogleCalendarApiAuth.accessTokenOrNull(context, accountEmail)
                if (accessToken != null) {
                    candidates += googleCalendarApiRepository.queryCandidates(
                        accessToken,
                        accountEmail,
                        nowMillis,
                        windowEnd,
                        settings.disabledGoogleCalendarIds,
                    )
                    reconciledProviders += googleProviderKey(accountEmail)
                }
            }
        } else {
            reconciledProviders += CalendarProviders.GOOGLE
        }

        val existingByKey = existing.associateBy { keyOf(it) }
        val desiredKeys = HashSet<String>()

        for (cand in candidates) {
            val fireZdt = Instant.ofEpochMilli(cand.fireTimeMillis).atZone(zone)
            val fireDate = fireZdt.toLocalDate()
            val fireTime = fireZdt.toLocalTime().withSecond(0).withNano(0)
            val fireMillis = fireDate.atTime(fireTime).atZone(zone).toInstant().toEpochMilli()
            val key = keyOf(cand.provider, cand.calendarId, cand.eventKey, cand.instanceStartMillis, fireMillis)
            desiredKeys += key

            if (existingByKey.containsKey(key)) continue // already present (preserve its state)

            val alarm = Alarm(
                label = cand.title,
                time = fireTime,
                enabled = true,
                repeat = RepeatRule.OnceOnDate(fireDate),
                source = AlarmSource.CALENDAR,
                calendarProvider = cand.provider,
                calendarId = cand.calendarId,
                calendarEventKey = cand.eventKey,
                calendarEventId = cand.eventKey.toLongOrNull(),
                calendarInstanceStartMillis = cand.instanceStartMillis,
            )
            val id = alarmRepository.upsert(alarm)
            alarmScheduler.schedule(alarm.copy(id = id))
        }

        // Drop calendar alarms that no longer correspond to any in-window instance.
        for (stale in existing) {
            val provider = stale.calendarProvider ?: CalendarProviders.DEVICE
            val providerKey = if (provider == CalendarProviders.GOOGLE) {
                googleProviderKey(stale.calendarId?.substringBefore("|").orEmpty())
            } else {
                provider
            }
            if (provider !in reconciledProviders && providerKey !in reconciledProviders) continue
            if (keyOf(stale) !in desiredKeys) {
                alarmScheduler.cancel(stale.id)
                alarmRepository.delete(stale.id)
            }
        }
    }

    /** Per-instance dismiss (DESIGN.md §7): suppress one upcoming calendar alarm, keep the feed. */
    suspend fun dismissInstance(alarmId: Long) {
        alarmScheduler.cancel(alarmId)
        alarmRepository.setEnabled(alarmId, false)
    }

    private fun fireMillisOf(alarm: Alarm): Long {
        val date = (alarm.repeat as? RepeatRule.OnceOnDate)?.date ?: return -1L
        return date.atTime(alarm.time).atZone(zone).toInstant().toEpochMilli()
    }

    private fun keyOf(alarm: Alarm): String =
        keyOf(
            alarm.calendarProvider ?: CalendarProviders.DEVICE,
            alarm.calendarId.orEmpty(),
            alarm.calendarEventKey ?: alarm.calendarEventId?.toString().orEmpty(),
            alarm.calendarInstanceStartMillis,
            fireMillisOf(alarm),
        )

    private fun keyOf(
        provider: String,
        calendarId: String,
        eventKey: String,
        instanceStart: Long?,
        fireMillis: Long,
    ): String = "$provider:$calendarId:$eventKey:$instanceStart:${fireMillis / 60_000L}"

    private fun googleProviderKey(accountEmail: String): String = "${CalendarProviders.GOOGLE}:$accountEmail"

    companion object {
        /** Only events inside this rolling window get real exact-alarm entries (DESIGN.md §5). */
        const val HORIZON_HOURS = 48
    }
}
