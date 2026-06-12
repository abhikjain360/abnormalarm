package com.abhikjain360.abnormalarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import com.abhikjain360.abnormalarm.notifications.Notifications
import com.abhikjain360.abnormalarm.ui.formatTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Handles the lead-window "Upcoming" notification and its Skip action (DESIGN.md §7).
 *  - [AlarmIntents.ACTION_UPCOMING]: post the notification a configurable window before the fire.
 *  - [AlarmIntents.ACTION_SKIP]: pre-emptively skip exactly that occurrence and reschedule.
 */
class UpcomingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        val triggerMillis = intent.getLongExtra(AlarmIntents.EXTRA_TRIGGER_MILLIS, -1L)
        if (alarmId < 0L) return

        val appContext = context.applicationContext
        if (!DirectBoot.isUserUnlocked(appContext)) {
            onReceiveDirectBoot(appContext, intent, alarmId, triggerMillis)
            return
        }

        val pending = goAsync()
        val container = appContext.appContainer

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val alarm = container.alarmRepository.get(alarmId) ?: return@launch
                if (!alarm.enabled) return@launch

                when (intent.action) {
                    AlarmIntents.ACTION_UPCOMING -> {
                        val whenText = Instant.ofEpochMilli(triggerMillis)
                            .atZone(ZoneId.systemDefault()).toLocalTime().formatTime()
                        Notifications.postUpcoming(appContext, alarmId, triggerMillis, alarm.label, whenText)
                    }
                    AlarmIntents.ACTION_SKIP -> {
                        val oneShot = alarm.repeat == RepeatRule.Once || alarm.repeat is RepeatRule.OnceOnDate
                        val updated = if (oneShot) {
                            alarm.copy(enabled = false, skipNextInstantMillis = null)
                        } else {
                            alarm.copy(skipNextInstantMillis = triggerMillis)
                        }
                        container.alarmRepository.upsert(updated)
                        if (oneShot) {
                            container.alarmScheduler.cancel(updated.id)
                        } else {
                            container.alarmScheduler.schedule(updated)
                        }
                        Notifications.cancelUpcoming(appContext, alarmId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun onReceiveDirectBoot(context: Context, intent: Intent, alarmId: Long, triggerMillis: Long) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val alarm = ScheduleMirror.getAlarm(context, alarmId) ?: return@launch
                if (!alarm.enabled) return@launch

                when (intent.action) {
                    AlarmIntents.ACTION_UPCOMING -> {
                        val whenText = Instant.ofEpochMilli(triggerMillis)
                            .atZone(ZoneId.systemDefault()).toLocalTime().formatTime()
                        Notifications.postUpcoming(context, alarmId, triggerMillis, alarm.label, whenText)
                    }
                    AlarmIntents.ACTION_SKIP -> {
                        val oneShot = alarm.repeat == RepeatRule.Once || alarm.repeat is RepeatRule.OnceOnDate
                        val updated = if (oneShot) {
                            alarm.copy(enabled = false, skipNextInstantMillis = null)
                        } else {
                            alarm.copy(skipNextInstantMillis = triggerMillis)
                        }
                        val scheduler = AlarmScheduler(context)
                        if (oneShot) {
                            scheduler.cancel(updated.id)
                            ScheduleMirror.upsertAlarm(context, updated)
                        } else {
                            ScheduleMirror.upsertAlarm(context, updated)
                            scheduler.schedule(updated)
                        }
                        Notifications.cancelUpcoming(context, alarmId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
