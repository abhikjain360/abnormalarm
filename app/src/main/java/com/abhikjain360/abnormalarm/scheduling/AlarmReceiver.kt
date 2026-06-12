package com.abhikjain360.abnormalarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import com.abhikjain360.abnormalarm.ring.RingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when an exact alarm goes off. The OS cold-starts the process to deliver this even if the
 * app was never running (DESIGN.md §2).
 *
 * Order matters for crash-safety: we recompute + reschedule the NEXT occurrence *before* we start
 * ringing, so a crash mid-ring can never break the repeat series.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        val triggerMillis = intent.getLongExtra(AlarmIntents.EXTRA_TRIGGER_MILLIS, -1L)
        if (alarmId < 0L) return

        val appContext = context.applicationContext
        if (!DirectBoot.isUserUnlocked(appContext)) {
            onReceiveDirectBoot(appContext, intent, alarmId, triggerMillis)
            return
        }

        // A snoozed re-fire just rings again — the repeat series was already advanced on first fire.
        if (intent.action == AlarmIntents.ACTION_SNOOZE_FIRE) {
            RingService.start(appContext, alarmId)
            return
        }
        if (intent.action != AlarmIntents.ACTION_ALARM_FIRE) return

        val pending = goAsync()
        val container = appContext.appContainer

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val alarm = container.alarmRepository.get(alarmId) ?: return@launch
                if (!alarm.enabled) return@launch

                val skipped = alarm.skipNextInstantMillis != null &&
                    alarm.skipNextInstantMillis == triggerMillis
                val isOnce = alarm.repeat == RepeatRule.Once || alarm.repeat is RepeatRule.OnceOnDate

                // Advance bookkeeping: count this fire (unless skipped), consume any skip flag,
                // and let one-time alarms disable themselves.
                val advanced = alarm.copy(
                    firedCount = alarm.firedCount + if (skipped) 0 else 1,
                    skipNextInstantMillis = null,
                    enabled = !isOnce,
                )
                container.alarmRepository.upsert(advanced)

                // Reschedule the next occurrence first. If none remains (end condition), disable.
                if (advanced.enabled) {
                    val next = container.alarmScheduler.schedule(advanced)
                    if (next == null) container.alarmRepository.setEnabled(advanced.id, false)
                } else {
                    container.alarmScheduler.cancel(advanced.id)
                }

                // Then ring — unless the user had pre-emptively skipped exactly this occurrence.
                if (!skipped) RingService.start(appContext, alarmId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun onReceiveDirectBoot(context: Context, intent: Intent, alarmId: Long, triggerMillis: Long) {
        if (intent.action == AlarmIntents.ACTION_SNOOZE_FIRE) {
            RingService.start(context, alarmId)
            return
        }
        if (intent.action != AlarmIntents.ACTION_ALARM_FIRE) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val alarm = ScheduleMirror.getAlarm(context, alarmId) ?: return@launch
                if (!alarm.enabled) return@launch

                val skipped = alarm.skipNextInstantMillis != null &&
                    alarm.skipNextInstantMillis == triggerMillis
                val isOnce = alarm.repeat == RepeatRule.Once || alarm.repeat is RepeatRule.OnceOnDate
                val advanced = alarm.copy(
                    firedCount = alarm.firedCount + if (skipped) 0 else 1,
                    skipNextInstantMillis = null,
                    enabled = !isOnce,
                )

                val scheduler = AlarmScheduler(context)
                if (advanced.enabled) {
                    ScheduleMirror.upsertAlarm(context, advanced)
                    val next = scheduler.schedule(advanced)
                    if (next == null) {
                        val disabled = advanced.copy(enabled = false)
                        scheduler.cancel(disabled.id)
                        ScheduleMirror.upsertAlarm(context, disabled)
                    }
                } else {
                    scheduler.cancel(advanced.id)
                    ScheduleMirror.upsertAlarm(context, advanced)
                }

                if (!skipped) RingService.start(context, alarmId)
            } finally {
                pending.finish()
            }
        }
    }
}
