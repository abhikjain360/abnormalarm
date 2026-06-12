package com.abhikjain360.abnormalarm.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Snapshot of all global settings (DESIGN.md §7/§8/§12). */
data class AppSettings(
    val calendarFeedEnabled: Boolean = false,
    /** Calendar ids the user has toggled OFF. All calendars are ON by default (DESIGN.md §5). */
    val disabledCalendarIds: Set<Long> = emptySet(),
    /** Google Calendar API backend, used when Google events are not exposed through CalendarContract. */
    val googleCalendarApiEnabled: Boolean = false,
    /** Google accounts explicitly connected for Google Calendar API sync. */
    val connectedGoogleAccounts: Set<String> = emptySet(),
    /** Google Calendar API calendar ids toggled OFF. */
    val disabledGoogleCalendarIds: Set<String> = emptySet(),
    /** Minutes before fire that the "Upcoming" notification appears (DESIGN.md §7). */
    val upcomingLeadMinutes: Int = 60,
    /** Set once after the first-run reliability sheet is shown — never auto-shown again (§12). */
    val reliabilityPromptShown: Boolean = false,
)

/**
 * Global preferences over Jetpack DataStore. Per-alarm config lives on the [com.abhikjain360
 * .abnormalarm.domain.model.Alarm]; this is for app-wide knobs only.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            calendarFeedEnabled = p[KEY_CAL_ENABLED] ?: false,
            disabledCalendarIds = (p[KEY_CAL_DISABLED] ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet(),
            googleCalendarApiEnabled = p[KEY_GOOGLE_CAL_ENABLED] ?: false,
            connectedGoogleAccounts = p[KEY_GOOGLE_ACCOUNTS] ?: emptySet(),
            disabledGoogleCalendarIds = p[KEY_GOOGLE_CAL_DISABLED] ?: emptySet(),
            upcomingLeadMinutes = p[KEY_LEAD_MINUTES] ?: 60,
            reliabilityPromptShown = p[KEY_RELIABILITY_SHOWN] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setCalendarFeedEnabled(enabled: Boolean) =
        context.dataStore.edit { it[KEY_CAL_ENABLED] = enabled }.let {}

    suspend fun setCalendarEnabled(calendarId: Long, enabled: Boolean) {
        context.dataStore.edit { p ->
            val current = (p[KEY_CAL_DISABLED] ?: emptySet()).toMutableSet()
            if (enabled) current.remove(calendarId.toString()) else current.add(calendarId.toString())
            p[KEY_CAL_DISABLED] = current
        }
    }

    suspend fun setGoogleCalendarApiEnabled(enabled: Boolean) =
        context.dataStore.edit { it[KEY_GOOGLE_CAL_ENABLED] = enabled }.let {}

    suspend fun setGoogleAccountConnected(email: String, connected: Boolean) {
        context.dataStore.edit { p ->
            val accounts = (p[KEY_GOOGLE_ACCOUNTS] ?: emptySet()).toMutableSet()
            if (connected) {
                accounts.add(email)
            } else {
                accounts.remove(email)
                val disabled = (p[KEY_GOOGLE_CAL_DISABLED] ?: emptySet())
                    .filterNot { it.startsWith("$email|") }
                    .toSet()
                p[KEY_GOOGLE_CAL_DISABLED] = disabled
            }
            p[KEY_GOOGLE_ACCOUNTS] = accounts
            p[KEY_GOOGLE_CAL_ENABLED] = accounts.isNotEmpty()
        }
    }

    suspend fun clearGoogleAccounts() {
        context.dataStore.edit { p ->
            p[KEY_GOOGLE_ACCOUNTS] = emptySet()
            p[KEY_GOOGLE_CAL_DISABLED] = emptySet()
            p[KEY_GOOGLE_CAL_ENABLED] = false
        }
    }

    suspend fun setGoogleCalendarEnabled(calendarId: String, enabled: Boolean) {
        context.dataStore.edit { p ->
            val current = (p[KEY_GOOGLE_CAL_DISABLED] ?: emptySet()).toMutableSet()
            if (enabled) current.remove(calendarId) else current.add(calendarId)
            p[KEY_GOOGLE_CAL_DISABLED] = current
        }
    }

    suspend fun setUpcomingLeadMinutes(minutes: Int) =
        context.dataStore.edit { it[KEY_LEAD_MINUTES] = minutes }.let {}

    suspend fun setReliabilityPromptShown(shown: Boolean) =
        context.dataStore.edit { it[KEY_RELIABILITY_SHOWN] = shown }.let {}

    private companion object {
        val KEY_CAL_ENABLED = booleanPreferencesKey("calendar_feed_enabled")
        val KEY_CAL_DISABLED = stringSetPreferencesKey("calendar_disabled_ids")
        val KEY_GOOGLE_CAL_ENABLED = booleanPreferencesKey("google_calendar_api_enabled")
        val KEY_GOOGLE_ACCOUNTS = stringSetPreferencesKey("google_calendar_accounts")
        val KEY_GOOGLE_CAL_DISABLED = stringSetPreferencesKey("google_calendar_disabled_ids")
        val KEY_LEAD_MINUTES = intPreferencesKey("upcoming_lead_minutes")
        val KEY_RELIABILITY_SHOWN = booleanPreferencesKey("reliability_prompt_shown")
    }
}
