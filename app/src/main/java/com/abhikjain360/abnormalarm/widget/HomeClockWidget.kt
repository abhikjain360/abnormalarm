package com.abhikjain360.abnormalarm.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.abhikjain360.abnormalarm.MainActivity
import com.abhikjain360.abnormalarm.R
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.scheduling.DirectBoot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class HomeClockWidget : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    updateAllNow(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        updateAll(context.applicationContext)
    }

    override fun onEnabled(context: Context) {
        updateAll(context.applicationContext)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { HomeClockWidgetPrefs.delete(context, it) }
        updateAll(context.applicationContext)
    }

    companion object {
        private const val ACTION_REFRESH = "com.abhikjain360.abnormalarm.action.WIDGET_REFRESH"
        private const val REQUEST_REFRESH = 0x7100
        private const val REQUEST_OPEN_APP = 0x7101

        fun updateAll(context: Context) {
            CoroutineScope(Dispatchers.Default).launch { updateAllNow(context.applicationContext) }
        }

        fun update(context: Context, appWidgetId: Int) {
            CoroutineScope(Dispatchers.Default).launch {
                val appContext = context.applicationContext
                val manager = AppWidgetManager.getInstance(appContext)
                manager.updateAppWidget(appWidgetId, buildViews(appContext, appWidgetId))
                scheduleNextDateRefresh(appContext, intArrayOf(appWidgetId))
            }
        }

        private suspend fun updateAllNow(context: Context) {
            if (!DirectBoot.isUserUnlocked(context)) return
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HomeClockWidget::class.java))
            if (ids.isEmpty()) {
                cancelDateRefresh(context)
                return
            }
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, id)) }
            scheduleNextDateRefresh(context, ids)
        }

        private suspend fun buildViews(context: Context, appWidgetId: Int): RemoteViews {
            val config = HomeClockWidgetPrefs.load(context, appWidgetId)
            val views = RemoteViews(context.packageName, R.layout.widget_home_clock)
            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            views.setTextViewText(R.id.widget_next_alarm, nextAlarmText(context))
            bindZone(
                views = views,
                containerId = R.id.widget_zone_one,
                clockId = R.id.widget_zone_one_clock,
                labelId = R.id.widget_zone_one_label,
                zoneId = config.zoneOneId,
                label = config.zoneOneLabel,
            )
            bindZone(
                views = views,
                containerId = R.id.widget_zone_two,
                clockId = R.id.widget_zone_two_clock,
                labelId = R.id.widget_zone_two_label,
                zoneId = config.zoneTwoId,
                label = config.zoneTwoLabel,
            )
            views.setViewVisibility(
                R.id.widget_zone_gap,
                if (config.zoneOneId != null && config.zoneTwoId != null) View.VISIBLE else View.GONE,
            )
            return views
        }

        private fun bindZone(
            views: RemoteViews,
            containerId: Int,
            clockId: Int,
            labelId: Int,
            zoneId: String?,
            label: String,
        ) {
            if (zoneId == null) {
                views.setViewVisibility(containerId, View.GONE)
                return
            }
            views.setViewVisibility(containerId, View.VISIBLE)
            views.setString(clockId, "setTimeZone", zoneId)
            views.setTextViewText(labelId, secondaryZoneLabel(zoneId, label))
        }

        internal fun secondaryZoneLabel(
            zoneId: String,
            label: String,
            referenceInstant: Instant = Instant.now(),
            localZone: ZoneId = ZoneId.systemDefault(),
        ): String {
            val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(localZone)
            val base = label.ifBlank { defaultZoneLabel(zoneId) }
            val localDate = referenceInstant.atZone(localZone).toLocalDate()
            val zoneDate = referenceInstant.atZone(zone).toLocalDate()
            return if (zoneDate == localDate) {
                base
            } else {
                val day = zoneDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                "$base · $day"
            }
        }

        private suspend fun nextAlarmText(context: Context): String {
            val container = context.appContainer
            val next = container.alarmRepository.getEnabled()
                .mapNotNull { alarm -> container.alarmScheduler.computeTrigger(alarm) }
                .minByOrNull { it.toInstant() }
                ?: return "No alarm"
            return formatNextAlarm(next, ZonedDateTime.now(next.zone))
        }

        internal fun formatNextAlarm(next: ZonedDateTime, now: ZonedDateTime): String {
            val today = now.toLocalDate()
            val nextDate = next.toLocalDate()
            val time = next.toLocalTime().format(timeFormatter())
            return when {
                nextDate == today -> "Today $time"
                nextDate == today.plusDays(1) -> "Tomorrow $time"
                nextDate.isBefore(today.plusDays(7)) -> next.format(weekdayFormatter())
                else -> next.format(dateFormatter())
            }
        }

        private fun timeFormatter(): DateTimeFormatter =
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

        private fun weekdayFormatter(): DateTimeFormatter =
            DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())

        private fun dateFormatter(): DateTimeFormatter =
            DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context,
                REQUEST_OPEN_APP,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun scheduleNextDateRefresh(context: Context, ids: IntArray) {
            if (ids.isEmpty()) return
            val zones = buildSet {
                add(ZoneId.systemDefault())
                ids.forEach { id ->
                    val config = HomeClockWidgetPrefs.load(context, id)
                    listOfNotNull(config.zoneOneId, config.zoneTwoId).forEach { zoneId ->
                        runCatching { ZoneId.of(zoneId) }.getOrNull()?.let(::add)
                    }
                }
            }
            val nextRefresh = zones.minOf { zone ->
                ZonedDateTime.now(zone).toLocalDate().plusDays(1).atStartOfDay(zone)
                    .toInstant()
            }.plusSeconds(2)
            val intent = Intent(context, HomeClockWidget::class.java).apply { action = ACTION_REFRESH }
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_REFRESH,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            context.getSystemService(AlarmManager::class.java)
                .set(AlarmManager.RTC, nextRefresh.toEpochMilli(), pending)
        }

        private fun cancelDateRefresh(context: Context) {
            val intent = Intent(context, HomeClockWidget::class.java).apply { action = ACTION_REFRESH }
            val pending = PendingIntent.getBroadcast(
                context,
                REQUEST_REFRESH,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pending != null) {
                context.getSystemService(AlarmManager::class.java).cancel(pending)
                pending.cancel()
            }
        }
    }
}
