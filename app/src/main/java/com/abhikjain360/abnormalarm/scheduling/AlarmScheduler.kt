package com.abhikjain360.abnormalarm.scheduling

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.abhikjain360.abnormalarm.MainActivity
import com.abhikjain360.abnormalarm.domain.AlarmRepository
import com.abhikjain360.abnormalarm.domain.model.Alarm
import com.abhikjain360.abnormalarm.domain.schedule.nextTrigger
import com.abhikjain360.abnormalarm.notifications.Notifications
import com.abhikjain360.abnormalarm.widget.HomeClockWidget
import java.time.Clock
import java.time.ZonedDateTime

/**
 * The reliability core (DESIGN.md §2). Every alarm — manual or calendar — is registered as a
 * SINGLE exact [AlarmManager.setAlarmClock]. We never use OS repeating alarms; on each fire the
 * [AlarmReceiver] recomputes the next occurrence and calls [schedule] again.
 *
 * `setAlarmClock` is the only API the system guarantees to deliver on time (it leaves Doze and
 * never batches), which is the entire reason this app exists.
 */
class AlarmScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Minutes before fire that the "Upcoming" notification trigger is set (DESIGN.md §7). Updated
     * from settings by the app; read synchronously here so [schedule] stays non-suspending.
     */
    @Volatile var upcomingLeadMinutes: Int = 60

    /**
     * Schedule (or replace) the next exact fire for [alarm]. Returns the fire instant (epoch
     * millis), or null if the alarm is disabled / has no further occurrences (then it's cancelled).
     */
    @SuppressLint("MissingPermission")
    fun schedule(alarm: Alarm): Long? {
        if (!alarm.enabled) {
            cancel(alarm.id)
            return null
        }
        val trigger = computeTrigger(alarm)
        if (trigger == null) {
            cancel(alarm.id)
            return null
        }
        val triggerMillis = trigger.toInstant().toEpochMilli()
        val info = AlarmManager.AlarmClockInfo(triggerMillis, showIntent())
        alarmManager.setAlarmClock(info, firePendingIntent(alarm, triggerMillis))
        ScheduleMirror.upsertAlarm(context, alarm)
        scheduleUpcoming(alarm, triggerMillis)
        if (DirectBoot.isUserUnlocked(context)) HomeClockWidget.updateAll(context)
        return triggerMillis
    }

    /**
     * Schedule a one-off snooze re-fire for [alarmId] at [fireMillis]. Uses a distinct request code
     * and the [AlarmIntents.ACTION_SNOOZE_FIRE] action so it rings again WITHOUT touching the repeat
     * series (which was already advanced when the alarm first fired).
     */
    @SuppressLint("MissingPermission")
    fun scheduleSnooze(alarmId: Long, fireMillis: Long) {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_SNOOZE_FIRE
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
        }
        val pi = PendingIntent.getBroadcast(
            context, snoozeRequestCode(alarmId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(fireMillis, showIntent()), pi)
    }

    /** Cancel any pending fire (and upcoming trigger) for [alarmId]. */
    fun cancel(alarmId: Long) {
        firePendingIntentOrNull(alarmId)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        upcomingPendingIntentOrNull(alarmId)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        snoozePendingIntentOrNull(alarmId)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
        ScheduleMirror.removeAlarm(context, alarmId)
        Notifications.cancelUpcoming(context, alarmId)
        if (DirectBoot.isUserUnlocked(context)) HomeClockWidget.updateAll(context)
    }

    /** Set a lightweight, non-wakeup trigger to post the Upcoming notification (DESIGN.md §7). */
    private fun scheduleUpcoming(alarm: Alarm, triggerMillis: Long) {
        val pi = upcomingPendingIntent(alarm.id, triggerMillis)
        alarmManager.cancel(pi)
        val at = triggerMillis - upcomingLeadMinutes * 60_000L
        if (at > System.currentTimeMillis()) {
            // The lead window hasn't started yet, so any Upcoming notification still posted is stale
            // (e.g. the user skipped/edited a repeating alarm mid-lead-window). Clear it — a fresh one
            // re-posts when the trigger below fires. On API 36+ that notification is an ongoing,
            // non-dismissible Live Update, so leaving it would strand a wrong countdown. We only clear
            // here (at > now), never when already inside the lead window, so reopening the app during
            // the lead window keeps the genuinely-showing live update intact.
            Notifications.cancelUpcoming(context, alarm.id)
            // RTC (not RTC_WAKEUP): it's just a notification, no need to leave Doze for it.
            alarmManager.set(AlarmManager.RTC, at, pi)
        }
    }

    /** Re-materialize every enabled alarm (used on boot / app-update / time change / app open). */
    suspend fun rescheduleAll(repository: AlarmRepository) {
        repository.getEnabled().forEach { schedule(it) }
    }

    /** The next fire after "now", honoring [Alarm.skipNextInstantMillis] and the end condition. */
    fun computeTrigger(alarm: Alarm): ZonedDateTime? {
        val now = ZonedDateTime.now(clock)
        val first = nextTrigger(alarm.time, alarm.repeat, alarm.end, alarm.firedCount, now, now.zone)
            ?: return null
        val skip = alarm.skipNextInstantMillis
        return if (skip != null && first.toInstant().toEpochMilli() == skip) {
            // The user pre-emptively skipped this occurrence — advance to the one after it.
            nextTrigger(alarm.time, alarm.repeat, alarm.end, alarm.firedCount, first, now.zone)
        } else {
            first
        }
    }

    private fun firePendingIntent(alarm: Alarm, triggerMillis: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_ALARM_FIRE
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmIntents.EXTRA_TRIGGER_MILLIS, triggerMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun firePendingIntentOrNull(alarmId: Long): PendingIntent? {
        // Intent equality (for PendingIntent matching) ignores extras; component + action suffice.
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_ALARM_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun upcomingPendingIntent(alarmId: Long, triggerMillis: Long): PendingIntent {
        val intent = Intent(context, UpcomingReceiver::class.java).apply {
            action = AlarmIntents.ACTION_UPCOMING
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmIntents.EXTRA_TRIGGER_MILLIS, triggerMillis)
        }
        return PendingIntent.getBroadcast(
            context, upcomingRequestCode(alarmId), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun upcomingPendingIntentOrNull(alarmId: Long): PendingIntent? {
        val intent = Intent(context, UpcomingReceiver::class.java).apply {
            action = AlarmIntents.ACTION_UPCOMING
        }
        return PendingIntent.getBroadcast(
            context, upcomingRequestCode(alarmId), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun snoozePendingIntentOrNull(alarmId: Long): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmIntents.ACTION_SNOOZE_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            snoozeRequestCode(alarmId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // Distinct request-code namespaces so fire / upcoming / snooze intents never collide.
    private fun upcomingRequestCode(alarmId: Long) = (alarmId.toInt() and 0x00FFFFFF) or 0x20000000
    private fun snoozeRequestCode(alarmId: Long) = (alarmId.toInt() and 0x00FFFFFF) or 0x30000000

    /** Shown when the user taps the system's next-alarm chip; opens the app. */
    private fun showIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
