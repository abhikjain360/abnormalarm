package com.abhikjain360.abnormalarm.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.data.calendar.CalendarRepository
import com.abhikjain360.abnormalarm.data.calendar.CalendarSyncWorker
import com.abhikjain360.abnormalarm.data.calendar.DeviceCalendar
import com.abhikjain360.abnormalarm.data.calendar.GoogleApiCalendar
import com.abhikjain360.abnormalarm.data.calendar.GoogleCalendarApiAuth
import com.abhikjain360.abnormalarm.data.calendar.GoogleCalendarApiRepository
import com.abhikjain360.abnormalarm.data.settings.AppSettings
import com.abhikjain360.abnormalarm.data.settings.SettingsRepository
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val calendarRepository: CalendarRepository,
    private val googleCalendarApiRepository: GoogleCalendarApiRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _calendars = MutableStateFlow<List<DeviceCalendar>>(emptyList())
    val calendars: StateFlow<List<DeviceCalendar>> = _calendars.asStateFlow()

    private val _googleCalendars = MutableStateFlow<List<GoogleApiCalendar>>(emptyList())
    val googleCalendars: StateFlow<List<GoogleApiCalendar>> = _googleCalendars.asStateFlow()

    private val _googleCalendarStatus = MutableStateFlow<String?>(null)
    val googleCalendarStatus: StateFlow<String?> = _googleCalendarStatus.asStateFlow()

    fun refreshCalendars() = viewModelScope.launch {
        _calendars.value = withContext(Dispatchers.IO) { calendarRepository.queryCalendars() }
    }

    fun refreshGoogleCalendarsSilently() = viewModelScope.launch {
        refreshGoogleCalendars(settingsRepository.current().connectedGoogleAccounts)
    }

    fun refreshGoogleCalendarsAndSync() = viewModelScope.launch {
        refreshGoogleCalendars(settingsRepository.current().connectedGoogleAccounts)
        CalendarSyncWorker.syncNow(appContext)
    }

    fun reportGoogleCalendarStatus(message: String) {
        _googleCalendarStatus.value = message
    }

    fun connectGoogleCalendar(accessToken: String) = viewModelScope.launch {
        _googleCalendarStatus.value = "Connecting"
        runCatching {
            withContext(Dispatchers.IO) {
                googleCalendarApiRepository.accountInfo(accessToken)
            }
        }.onSuccess { account ->
            settingsRepository.setCalendarFeedEnabled(true)
            settingsRepository.setGoogleAccountConnected(account.email, true)
            refreshGoogleCalendars(settingsRepository.current().connectedGoogleAccounts)
            CalendarSyncWorker.syncNow(appContext)
        }.onFailure {
            _googleCalendarStatus.value = it.message ?: "Account lookup failed"
        }
    }

    fun disconnectGoogleCalendar() = viewModelScope.launch {
        settingsRepository.clearGoogleAccounts()
        _googleCalendars.value = emptyList()
        _googleCalendarStatus.value = "Disconnected"
        CalendarSyncWorker.syncNow(appContext)
    }

    fun disconnectGoogleAccount(accountEmail: String) = viewModelScope.launch {
        settingsRepository.setGoogleAccountConnected(accountEmail, false)
        refreshGoogleCalendars(settingsRepository.current().connectedGoogleAccounts)
        CalendarSyncWorker.syncNow(appContext)
    }

    private suspend fun refreshGoogleCalendars(accounts: Set<String>) {
        _googleCalendarStatus.value = "Refreshing"
        val calendars = mutableListOf<GoogleApiCalendar>()
        val failed = mutableListOf<String>()
        for (account in accounts.sorted()) {
            val token = GoogleCalendarApiAuth.accessTokenOrNull(appContext, account)
            if (token == null) {
                failed += account
                continue
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    googleCalendarApiRepository.queryCalendars(token, account)
                }
            }.onSuccess {
                calendars += it
            }.onFailure {
                failed += account
            }
        }
        _googleCalendars.value = calendars
        _googleCalendarStatus.value = when {
            accounts.isEmpty() -> "No accounts"
            failed.isEmpty() -> "Connected: ${accounts.size}"
            calendars.isEmpty() -> "Authorization needed"
            else -> "Some accounts need authorization"
        }
    }

    /** Enable/disable the whole calendar feed, then kick a sync to materialize or tear down alarms. */
    fun setCalendarFeedEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setCalendarFeedEnabled(enabled)
        if (enabled) {
            // Permission was just granted — register the observer now (it no-ops at startup without it).
            appContext.appContainer.calendarObserver.register()
            refreshCalendars()
        }
        CalendarSyncWorker.syncNow(appContext)
    }

    fun setCalendarEnabled(calendarId: Long, enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setCalendarEnabled(calendarId, enabled)
        CalendarSyncWorker.syncNow(appContext)
    }

    fun setGoogleCalendarEnabled(calendarId: String, enabled: Boolean) = viewModelScope.launch {
        settingsRepository.setGoogleCalendarEnabled(calendarId, enabled)
        CalendarSyncWorker.syncNow(appContext)
    }

    fun setLeadMinutes(minutes: Int) = viewModelScope.launch {
        settingsRepository.setUpcomingLeadMinutes(minutes)
        val container = appContext.appContainer
        container.alarmScheduler.upcomingLeadMinutes = minutes
        container.alarmScheduler.rescheduleAll(container.alarmRepository)
    }

    companion object {
        fun factory(
            appContext: Context,
            settingsRepository: SettingsRepository,
            calendarRepository: CalendarRepository,
            googleCalendarApiRepository: GoogleCalendarApiRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(
                    appContext,
                    settingsRepository,
                    calendarRepository,
                    googleCalendarApiRepository,
                ) as T
        }
    }
}
