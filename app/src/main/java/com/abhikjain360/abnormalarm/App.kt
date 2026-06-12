package com.abhikjain360.abnormalarm

import android.app.Application
import android.content.Context
import com.abhikjain360.abnormalarm.scheduling.DirectBoot
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
    @Volatile private var _container: AppContainer? = null
    val container: AppContainer
        get() = _container ?: initializeUnlockedContainer()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        if (!DirectBoot.isUserUnlocked(this)) return
        initializeUnlockedContainer()
    }

    @Synchronized
    private fun initializeUnlockedContainer(): AppContainer {
        _container?.let { return it }
        check(DirectBoot.isUserUnlocked(this)) {
            "Credential-protected app container is unavailable before user unlock"
        }
        val created = DefaultAppContainer(this)
        _container = created
        // Calendar feed (no-ops cheaply until the feature is enabled / permission granted).
        created.calendarObserver.register()
        CalendarSyncWorker.schedulePeriodic(this)
        // Reconcile persisted state with the OS registry on process start. This is cheap and covers
        // cases where Android cleared registered alarms/timers before the user reopened the app.
        appScope.launch {
            created.alarmScheduler.upcomingLeadMinutes =
                created.settingsRepository.current().upcomingLeadMinutes
            created.alarmScheduler.rescheduleAll(created.alarmRepository)
            created.timerScheduler.rescheduleAll(created.timerRepository)
        }
        // Keep the scheduler's upcoming-notification lead in sync with later settings changes.
        appScope.launch {
            created.settingsRepository.settings.collect {
                created.alarmScheduler.upcomingLeadMinutes = it.upcomingLeadMinutes
            }
        }
        HomeClockWidget.updateAll(this)
        return created
    }
}

/** Convenience accessor for the app-wide [AppContainer] from any [Context]. */
val Context.appContainer: AppContainer
    get() = (applicationContext as AbnormalarmApp).container
