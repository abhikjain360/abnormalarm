package com.abhikjain360.abnormalarm.ring

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.abhikjain360.abnormalarm.R
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.domain.model.Alarm
import com.abhikjain360.abnormalarm.domain.model.RingSettings
import com.abhikjain360.abnormalarm.domain.model.Timer
import com.abhikjain360.abnormalarm.domain.model.TimerState
import com.abhikjain360.abnormalarm.domain.timer.TimerDurationInput
import com.abhikjain360.abnormalarm.notifications.Notifications
import com.abhikjain360.abnormalarm.scheduling.AlarmIntents
import com.abhikjain360.abnormalarm.scheduling.AlarmScheduler
import com.abhikjain360.abnormalarm.scheduling.DirectBoot
import com.abhikjain360.abnormalarm.scheduling.ScheduleMirror
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service that keeps an alarm ringing even if the activity is dismissed (DESIGN.md §6).
 *
 * Declared with foregroundServiceType="mediaPlayback" (we play alarm audio). It's started from
 * [com.abhikjain360.abnormalarm.scheduling.AlarmReceiver]; apps holding USE_EXACT_ALARM are exempt
 * from the background-FGS-start restriction when started from an exact alarm.
 */
class RingService : Service() {

    companion object {
        private const val NOTIF_ID = 0xA1A0

        fun start(context: Context, alarmId: Long) {
            context.startForegroundService(
                Intent(context, RingService::class.java).apply {
                    action = AlarmIntents.ACTION_RING_START
                    putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmIntents.EXTRA_RING_KIND, AlarmIntents.RING_KIND_ALARM)
                },
            )
        }

        fun startTimer(context: Context, timerId: Long) {
            context.startForegroundService(
                Intent(context, RingService::class.java).apply {
                    action = AlarmIntents.ACTION_TIMER_RING_START
                    putExtra(AlarmIntents.EXTRA_TIMER_ID, timerId)
                    putExtra(AlarmIntents.EXTRA_RING_KIND, AlarmIntents.RING_KIND_TIMER)
                },
            )
        }

        fun dismiss(context: Context) {
            context.startService(Intent(context, RingService::class.java).apply { action = AlarmIntents.ACTION_DISMISS })
        }

        fun snooze(context: Context) {
            context.startService(Intent(context, RingService::class.java).apply { action = AlarmIntents.ACTION_SNOOZE })
        }

        fun dismissTimer(context: Context, timerId: Long) {
            context.startService(
                Intent(context, RingService::class.java).apply {
                    action = AlarmIntents.ACTION_TIMER_DISMISS
                    putExtra(AlarmIntents.EXTRA_TIMER_ID, timerId)
                    putExtra(AlarmIntents.EXTRA_RING_KIND, AlarmIntents.RING_KIND_TIMER)
                },
            )
        }
    }

    private data class ActiveRing(val kind: String, val id: Long)

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ringer: Ringer? = null
    private var active: ActiveRing? = null
    private var autoSilenceJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AlarmIntents.ACTION_RING_START -> onRingStart(intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L))
            AlarmIntents.ACTION_TIMER_RING_START ->
                onTimerRingStart(intent.getLongExtra(AlarmIntents.EXTRA_TIMER_ID, -1L))
            AlarmIntents.ACTION_TIMER_DISMISS ->
                onTimerDismiss(intent.getLongExtra(AlarmIntents.EXTRA_TIMER_ID, -1L))
            AlarmIntents.ACTION_SNOOZE -> onSnooze()
            else -> onAlarmDismiss() // DISMISS or unknown
        }
        return START_NOT_STICKY
    }

    private fun onRingStart(id: Long) {
        if (id < 0L) return
        active = ActiveRing(AlarmIntents.RING_KIND_ALARM, id)
        // startForeground must be called promptly (within ~5s of startForegroundService).
        startForeground(
            NOTIF_ID,
            buildAlarmRingingNotification(id),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        scope.launch {
            val alarm = loadAlarm(id)
            val settings = alarm?.ring ?: RingSettings()
            startRinger(settings)
            // Auto-silence: stop ringing after the configured window and flag the alarm missed.
            scheduleAutoSilence(active, settings) {
                Notifications.postMissed(applicationContext, id, alarm?.label.orEmpty())
                finishCurrentAndContinue()
            }
        }
    }

    private fun onTimerRingStart(id: Long) {
        if (id < 0L) return
        // Do not steal the full-screen surface from an alarm; the persisted timer will be picked up
        // after the alarm is dismissed.
        if (active?.kind == AlarmIntents.RING_KIND_ALARM && ringer != null) return
        scope.launch { showTimer(id, restartRinger = ringer == null || active?.id != id) }
    }

    private suspend fun showTimer(id: Long, restartRinger: Boolean) {
        val timer = loadTimer(id) ?: return
        active = ActiveRing(AlarmIntents.RING_KIND_TIMER, id)
        startForeground(
            NOTIF_ID,
            buildTimerRingingNotification(timer),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        if (restartRinger) startRinger(timer.ring)
        scheduleAutoSilence(active, timer.ring) {
            saveTimer(timer.toIdle())
            Notifications.postTimerFinished(applicationContext, id, timer.displayLabel())
            finishCurrentAndContinue()
        }
    }

    /** Schedule a one-off re-fire at now + snoozeMinutes (DESIGN.md §6), then stop ringing. */
    private fun onSnooze() {
        val id = active?.takeIf { it.kind == AlarmIntents.RING_KIND_ALARM }?.id ?: -1L
        scope.launch {
            if (id >= 0L) {
                val minutes = loadAlarm(id)?.ring?.snoozeMinutes ?: RingSettings().snoozeMinutes
                val fireAt = System.currentTimeMillis() + minutes * 60_000L
                val scheduler = if (DirectBoot.isUserUnlocked(applicationContext)) {
                    applicationContext.appContainer.alarmScheduler
                } else {
                    AlarmScheduler(applicationContext)
                }
                scheduler.scheduleSnooze(id, fireAt)
            }
            finishCurrentAndContinue()
        }
    }

    private fun onAlarmDismiss() {
        finishCurrentAndContinue()
    }

    private fun onTimerDismiss(id: Long) {
        scope.launch {
            val shouldFinishCurrent = active == null ||
                active == ActiveRing(AlarmIntents.RING_KIND_TIMER, id)
            if (id >= 0L) {
                loadTimer(id)?.let { saveTimer(it.toIdle()) }
            }
            if (shouldFinishCurrent) finishCurrentAndContinue()
        }
    }

    private fun startRinger(settings: RingSettings) {
        ringer?.stop()
        ringer = Ringer(this@RingService).also { it.start(settings) }
    }

    private fun scheduleAutoSilence(target: ActiveRing?, settings: RingSettings, block: suspend () -> Unit) {
        autoSilenceJob?.cancel()
        autoSilenceJob = scope.launch {
            delay(settings.autoSilenceMinutes * 60_000L)
            if (target == active) block()
        }
    }

    private fun finishCurrentAndContinue() {
        autoSilenceJob?.cancel()
        autoSilenceJob = null
        val old = active
        active = null
        scope.launch {
            val nextTimer = ringingTimers()
                .firstOrNull { old?.kind != AlarmIntents.RING_KIND_TIMER || it.id != old.id }
            if (nextTimer != null) {
                showTimer(nextTimer.id, restartRinger = true)
            } else {
                stopRingingCompletely()
            }
        }
    }

    private fun stopRingingCompletely() {
        autoSilenceJob?.cancel()
        autoSilenceJob = null
        ringer?.stop()
        ringer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        ringer?.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildAlarmRingingNotification(id: Long): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            id.toInt(),
            Intent(this, RingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmIntents.EXTRA_ALARM_ID, id)
                putExtra(AlarmIntents.EXTRA_RING_KIND, AlarmIntents.RING_KIND_ALARM)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getService(
            this,
            1,
            Intent(this, RingService::class.java).apply { action = AlarmIntents.ACTION_DISMISS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Alarm")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Dismiss", dismiss)
            .build()
    }

    private fun buildTimerRingingNotification(timer: Timer): Notification {
        val fullScreen = PendingIntent.getActivity(
            this,
            timerNotificationRequestCode(timer.id),
            Intent(this, RingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AlarmIntents.EXTRA_TIMER_ID, timer.id)
                putExtra(AlarmIntents.EXTRA_RING_KIND, AlarmIntents.RING_KIND_TIMER)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getService(
            this,
            timerDismissRequestCode(timer.id),
            Intent(this, RingService::class.java).apply {
                action = AlarmIntents.ACTION_TIMER_DISMISS
                putExtra(AlarmIntents.EXTRA_TIMER_ID, timer.id)
                putExtra(AlarmIntents.EXTRA_RING_KIND, AlarmIntents.RING_KIND_TIMER)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Timer")
            .setContentText(timer.displayLabel())
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Dismiss", dismiss)
            .build()
    }

    private fun Timer.toIdle(): Timer = copy(
        state = TimerState.IDLE,
        endAtMillis = null,
        remainingMillis = null,
    )

    private fun Timer.displayLabel(): String =
        label.ifBlank { TimerDurationInput.formatSeconds(durationMillis / 1000L) }

    private suspend fun loadAlarm(id: Long): Alarm? = withContext(Dispatchers.Default) {
        if (DirectBoot.isUserUnlocked(applicationContext)) {
            applicationContext.appContainer.alarmRepository.get(id)
        } else {
            ScheduleMirror.getAlarm(applicationContext, id)
        }
    }

    private suspend fun loadTimer(id: Long): Timer? = withContext(Dispatchers.Default) {
        if (DirectBoot.isUserUnlocked(applicationContext)) {
            applicationContext.appContainer.timerRepository.get(id)
        } else {
            ScheduleMirror.getTimer(applicationContext, id)
        }
    }

    private suspend fun saveTimer(timer: Timer) = withContext(Dispatchers.Default) {
        if (DirectBoot.isUserUnlocked(applicationContext)) {
            applicationContext.appContainer.timerRepository.upsert(timer)
            if (timer.state == TimerState.RUNNING || timer.state == TimerState.RINGING) {
                ScheduleMirror.upsertTimer(applicationContext, timer)
            } else {
                ScheduleMirror.removeTimer(applicationContext, timer.id)
            }
        } else {
            ScheduleMirror.upsertTimer(applicationContext, timer)
        }
    }

    private suspend fun ringingTimers(): List<Timer> = withContext(Dispatchers.Default) {
        if (DirectBoot.isUserUnlocked(applicationContext)) {
            applicationContext.appContainer.timerRepository.getRinging()
        } else {
            ScheduleMirror.getRingingTimers(applicationContext)
        }
    }

    private fun timerNotificationRequestCode(timerId: Long) =
        (timerId.toInt() and 0x00FFFFFF) or 0x50000000

    private fun timerDismissRequestCode(timerId: Long) =
        (timerId.toInt() and 0x00FFFFFF) or 0x60000000
}
