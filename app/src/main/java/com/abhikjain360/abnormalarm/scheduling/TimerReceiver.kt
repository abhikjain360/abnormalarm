package com.abhikjain360.abnormalarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.domain.model.TimerState
import com.abhikjain360.abnormalarm.ring.RingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a running timer reaches its persisted end time. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntents.ACTION_TIMER_FIRE) return
        val timerId = intent.getLongExtra(AlarmIntents.EXTRA_TIMER_ID, -1L)
        val triggerMillis = intent.getLongExtra(AlarmIntents.EXTRA_TRIGGER_MILLIS, -1L)
        if (timerId < 0L) return

        val appContext = context.applicationContext
        if (!DirectBoot.isUserUnlocked(appContext)) {
            onReceiveDirectBoot(appContext, timerId, triggerMillis)
            return
        }

        val pending = goAsync()
        val container = appContext.appContainer
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val timer = container.timerRepository.get(timerId) ?: return@launch
                if (timer.state != TimerState.RUNNING) return@launch

                val expectedEnd = timer.endAtMillis ?: return@launch
                if (triggerMillis != expectedEnd) {
                    container.timerScheduler.schedule(timer)
                    return@launch
                }

                val ringing = timer.copy(
                    state = TimerState.RINGING,
                    endAtMillis = null,
                    remainingMillis = null,
                )
                container.timerRepository.upsert(ringing)
                ScheduleMirror.upsertTimer(appContext, ringing)
                RingService.startTimer(appContext, timerId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun onReceiveDirectBoot(context: Context, timerId: Long, triggerMillis: Long) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val timer = ScheduleMirror.getTimer(context, timerId) ?: return@launch
                if (timer.state != TimerState.RUNNING) return@launch
                val expectedEnd = timer.endAtMillis ?: return@launch
                if (triggerMillis != expectedEnd) {
                    TimerScheduler(context).schedule(timer)
                    return@launch
                }
                ScheduleMirror.upsertTimer(
                    context,
                    timer.copy(
                        state = TimerState.RINGING,
                        endAtMillis = null,
                        remainingMillis = null,
                    ),
                )
                RingService.startTimer(context, timerId)
            } finally {
                pending.finish()
            }
        }
    }
}
