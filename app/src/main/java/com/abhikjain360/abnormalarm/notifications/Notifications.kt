package com.abhikjain360.abnormalarm.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.abhikjain360.abnormalarm.R
import com.abhikjain360.abnormalarm.scheduling.AlarmIntents
import com.abhikjain360.abnormalarm.scheduling.UpcomingReceiver

/** Notification channels + posters (DESIGN.md §7). Channels created once in `AbnormalarmApp`. */
object Notifications {
    const val CHANNEL_RINGING = "ringing"
    const val CHANNEL_UPCOMING = "upcoming"
    const val CHANNEL_MISSED = "missed"

    /** Notification ids. Upcoming/missed are namespaced per item so several can coexist. */
    private const val UPCOMING_ID_BASE = 0xB000
    private const val MISSED_ID_BASE = 0xC000
    private const val TIMER_FINISHED_ID_BASE = 0xD000

    private fun upcomingId(alarmId: Long) = UPCOMING_ID_BASE + alarmId.toInt()
    private fun missedId(alarmId: Long) = MISSED_ID_BASE + alarmId.toInt()
    private fun timerFinishedId(timerId: Long) = TIMER_FINISHED_ID_BASE + timerId.toInt()

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        // Ringing: high importance so the full-screen intent is honored. No channel sound/vibration
        // — the Ringer owns audio (USAGE_ALARM) and vibration so we don't double up.
        val ringing = NotificationChannel(
            CHANNEL_RINGING, "Alarm and timer ringing", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "The full-screen ringing screen shown when an alarm or timer goes off"
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        // DEFAULT importance (not LOW) so the Android 16 (API 36) promoted-ongoing live-update chip
        // posted from postUpcoming() is eligible to show in the status bar — a live update needs at
        // least DEFAULT importance. Silenced (no sound, no vibration) so the upcoming notification
        // stays quiet up to an hour before the alarm. Safe to change importance here: this app is
        // pre-release v0.1.0 with no installed base, so there is no channel-migration concern.
        val upcoming = NotificationChannel(
            CHANNEL_UPCOMING, "Upcoming alarm", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Your next alarm shortly before it rings, with a Skip action"
            setSound(null, null)
            enableVibration(false)
        }

        val missed = NotificationChannel(
            CHANNEL_MISSED, "Missed alarm and timer", NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Posted when an alarm or timer auto-silenced before you dismissed it"
        }

        nm.createNotificationChannels(listOf(ringing, upcoming, missed))
    }

    /** Post the "Upcoming" notification with a Skip action (DESIGN.md §7). */
    fun postUpcoming(context: Context, alarmId: Long, triggerMillis: Long, label: String, whenText: String) {
        val title = label.ifBlank { "Alarm" }
        val skipIntent = Intent(context, UpcomingReceiver::class.java).apply {
            action = AlarmIntents.ACTION_SKIP
            putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmIntents.EXTRA_TRIGGER_MILLIS, triggerMillis)
        }
        val skip = PendingIntent.getBroadcast(
            context,
            skipRequestCode(alarmId),
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_UPCOMING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Upcoming: $title")
            .setContentText(whenText)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, "Skip", skip)
        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 (API 36) "Live Updates": promote to an ongoing status-bar chip with a live
            // chronometer counting down to the fire time. setUsesChronometer + setChronometerCountDown
            // ticks against setWhen(triggerMillis) entirely in the system UI, so we poll nothing.
            // Ongoing (not auto-cancel — the two conflict); RingService cancels it when ringing begins.
            builder
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setWhen(triggerMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
        } else {
            // Pre-API-36 behavior: a plain dismissible reminder, no chip, no countdown.
            builder.setAutoCancel(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .notify(upcomingId(alarmId), builder.build())
    }

    fun cancelUpcoming(context: Context, alarmId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(upcomingId(alarmId))
    }

    /** Post a "Missed alarm" notification after auto-silence (DESIGN.md §7). */
    fun postMissed(context: Context, alarmId: Long, label: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Missed alarm")
            .setContentText(label.ifBlank { "An alarm rang but wasn't dismissed." })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(missedId(alarmId), notification)
    }

    fun postTimerFinished(context: Context, timerId: Long, label: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Timer finished")
            .setContentText(label.ifBlank { "A timer finished but wasn't dismissed." })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(timerFinishedId(timerId), notification)
    }

    /** API 34+ may withhold full-screen-intent permission; degrade to heads-up if so (DESIGN.md §10). */
    fun canUseFullScreenIntent(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun skipRequestCode(alarmId: Long) = (alarmId.toInt() and 0x00FFFFFF) or 0x10000000
}
