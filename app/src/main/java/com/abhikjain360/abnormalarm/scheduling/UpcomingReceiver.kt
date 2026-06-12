package com.abhikjain360.abnormalarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abhikjain360.abnormalarm.appContainer
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

        val pending = goAsync()
        val appContext = context.applicationContext
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
                        val updated = alarm.copy(skipNextInstantMillis = triggerMillis)
                        container.alarmRepository.upsert(updated)
                        container.alarmScheduler.schedule(updated)
                        Notifications.cancelUpcoming(appContext, alarmId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
