package com.abhikjain360.abnormalarm.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.abhikjain360.abnormalarm.MainActivity
import com.abhikjain360.abnormalarm.domain.TimerRepository
import com.abhikjain360.abnormalarm.domain.model.Timer
import com.abhikjain360.abnormalarm.domain.model.TimerState

/** One exact expiry trigger per running timer; no background service while counting down. */
class TimerScheduler(private val context: Context) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(timer: Timer): Long? {
        if (timer.state != TimerState.RUNNING) {
            cancel(timer.id)
            return null
        }
        val requestedAt = timer.endAtMillis
        if (requestedAt == null) {
            cancel(timer.id)
            return null
        }
        val fireAt = requestedAt.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(fireAt, showIntent()),
            firePendingIntent(timer.id, requestedAt),
        )
        return fireAt
    }

    fun cancel(timerId: Long) {
        firePendingIntentOrNull(timerId)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    suspend fun rescheduleAll(repository: TimerRepository) {
        repository.getRunning().forEach { schedule(it) }
    }

    private fun firePendingIntent(timerId: Long, triggerMillis: Long): PendingIntent {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            action = AlarmIntents.ACTION_TIMER_FIRE
            putExtra(AlarmIntents.EXTRA_TIMER_ID, timerId)
            putExtra(AlarmIntents.EXTRA_TRIGGER_MILLIS, triggerMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            timerRequestCode(timerId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun firePendingIntentOrNull(timerId: Long): PendingIntent? {
        val intent = Intent(context, TimerReceiver::class.java).apply {
            action = AlarmIntents.ACTION_TIMER_FIRE
        }
        return PendingIntent.getBroadcast(
            context,
            timerRequestCode(timerId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun timerRequestCode(timerId: Long) = (timerId.toInt() and 0x00FFFFFF) or 0x40000000

    private fun showIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_IMMUTABLE)
    }
}
