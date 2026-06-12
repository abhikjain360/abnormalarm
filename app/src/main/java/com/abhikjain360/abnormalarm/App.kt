package com.abhikjain360.abnormalarm

import android.app.Application
import android.content.Context
import com.abhikjain360.abnormalarm.data.RoomAlarmRepository
import com.abhikjain360.abnormalarm.data.RoomTimerRepository
import com.abhikjain360.abnormalarm.data.calendar.CalendarObserver
import com.abhikjain360.abnormalarm.data.calendar.CalendarRepository
import com.abhikjain360.abnormalarm.data.calendar.CalendarSync
import com.abhikjain360.abnormalarm.data.calendar.CalendarSyncWorker
import com.abhikjain360.abnormalarm.data.calendar.GoogleCalendarApiRepository
import com.abhikjain360.abnormalarm.data.db.AbnormalarmDb
import com.abhikjain360.abnormalarm.data.settings.SettingsRepository
import com.abhikjain360.abnormalarm.domain.AlarmRepository
import com.abhikjain360.abnormalarm.domain.TimerRepository
import com.abhikjain360.abnormalarm.notifications.Notifications
import com.abhikjain360.abnormalarm.scheduling.AlarmScheduler
import com.abhikjain360.abnormalarm.scheduling.TimerScheduler
import com.abhikjain360.abnormalarm.widget.HomeClockWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Tiny manual DI container (no Hilt — keeps the app lean, DESIGN.md §3/§12). Exposes the
 * singletons that receivers/services/UI need.
 */
interface AppContainer {
    val alarmRepository: AlarmRepository
    val alarmScheduler: AlarmScheduler
    val timerRepository: TimerRepository
    val timerScheduler: TimerScheduler
    val settingsRepository: SettingsRepository
    val calendarRepository: CalendarRepository
    val googleCalendarApiRepository: GoogleCalendarApiRepository
    val calendarSync: CalendarSync
    val calendarObserver: CalendarObserver
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val db = AbnormalarmDb.get(appContext)

    override val alarmRepository: AlarmRepository = RoomAlarmRepository(db.alarmDao())
    override val alarmScheduler: AlarmScheduler = AlarmScheduler(appContext)
    override val timerRepository: TimerRepository = RoomTimerRepository(db.timerDao())
    override val timerScheduler: TimerScheduler = TimerScheduler(appContext)
    override val settingsRepository: SettingsRepository = SettingsRepository(appContext)
    override val calendarRepository: CalendarRepository = CalendarRepository(appContext)
    override val googleCalendarApiRepository: GoogleCalendarApiRepository = GoogleCalendarApiRepository()
    override val calendarSync: CalendarSync = CalendarSync(
        appContext,
        alarmRepository,
        alarmScheduler,
        calendarRepository,
        googleCalendarApiRepository,
        settingsRepository,
    )
    override val calendarObserver: CalendarObserver = CalendarObserver(appContext)
}

class AbnormalarmApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        Notifications.createChannels(this)
        // Calendar feed (no-ops cheaply until the feature is enabled / permission granted).
        container.calendarObserver.register()
        CalendarSyncWorker.schedulePeriodic(this)
        // Keep the scheduler's upcoming-notification lead in sync with the user's setting.
        appScope.launch {
            container.settingsRepository.settings.collect {
                container.alarmScheduler.upcomingLeadMinutes = it.upcomingLeadMinutes
            }
        }
        HomeClockWidget.updateAll(this)
    }
}

/** Convenience accessor for the app-wide [AppContainer] from any [Context]. */
val Context.appContainer: AppContainer
    get() = (applicationContext as AbnormalarmApp).container
