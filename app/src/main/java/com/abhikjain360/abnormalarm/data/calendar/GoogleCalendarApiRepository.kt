package com.abhikjain360.abnormalarm.data.calendar

import android.accounts.Account
import android.content.Context
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class GoogleApiCalendar(
    val accountEmail: String,
    val id: String,
    val displayName: String,
    val color: Int,
)

data class GoogleAccountInfo(
    val email: String,
    val displayName: String,
)

object GoogleCalendarApiAuth {
    const val CALENDAR_READONLY_SCOPE = "https://www.googleapis.com/auth/calendar.readonly"

    private const val GOOGLE_ACCOUNT_TYPE = "com.google"

    val requestedScopes: List<Scope> = listOf(
        Scope(CALENDAR_READONLY_SCOPE),
        Scope("openid"),
        Scope("email"),
    )

    fun authorizationRequest(
        accountEmail: String? = null,
        selectAccount: Boolean = false,
    ): AuthorizationRequest {
        val builder = AuthorizationRequest.builder()
            .setRequestedScopes(requestedScopes)
        if (accountEmail != null) builder.setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
        if (selectAccount) builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        return builder.build()
    }

    /**
     * Returns a cached/refreshed short-lived access token when consent already exists. If Google
     * needs user interaction, returns null so background sync can leave existing rows untouched.
     */
    suspend fun accessTokenOrNull(context: Context, accountEmail: String? = null): String? = runCatching {
        val result = Identity.getAuthorizationClient(context)
            .authorize(authorizationRequest(accountEmail = accountEmail))
            .await()
        if (result.hasResolution()) null else result.accessToken
    }.getOrNull()
}

/** Minimal REST client for the read-only Google Calendar API surface Abnormalarm needs. */
class GoogleCalendarApiRepository(
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun accountInfo(accessToken: String): GoogleAccountInfo = withContext(Dispatchers.IO) {
        val json = getJson("https://www.googleapis.com/oauth2/v3/userinfo", accessToken)
        GoogleAccountInfo(
            email = json.getString("email"),
            displayName = json.optString("name").ifBlank { json.getString("email") },
        )
    }

    suspend fun queryCalendars(
        accessToken: String,
        accountEmail: String,
    ): List<GoogleApiCalendar> = withContext(Dispatchers.IO) {
        val out = mutableListOf<GoogleApiCalendar>()
        var pageToken: String? = null
        do {
            val url = buildUrl(
                "https://www.googleapis.com/calendar/v3/users/me/calendarList",
                mapOf(
                    "maxResults" to "250",
                    "showHidden" to "false",
                    "pageToken" to pageToken,
                ),
            )
            val json = getJson(url, accessToken)
            json.optJSONArray("items").orEmpty().forEachObject { cal ->
                val accessRole = cal.optString("accessRole")
                if (accessRole == "none" || accessRole == "freeBusyReader") return@forEachObject
                out += GoogleApiCalendar(
                    accountEmail = accountEmail,
                    id = cal.getString("id"),
                    displayName = cal.optString("summary").ifBlank { cal.getString("id") },
                    color = parseColor(cal.optString("backgroundColor")),
                )
            }
            pageToken = json.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        out
    }

    suspend fun queryCandidates(
        accessToken: String,
        accountEmail: String,
        windowStartMillis: Long,
        windowEndMillis: Long,
        disabledCalendarIds: Set<String>,
    ): List<CalendarAlarmCandidate> = withContext(Dispatchers.IO) {
        val calendars = queryCalendars(accessToken, accountEmail)
            .filter { googleCalendarKey(it.accountEmail, it.id) !in disabledCalendarIds }
        val out = mutableListOf<CalendarAlarmCandidate>()
        for (calendar in calendars) {
            out += queryCalendarCandidates(
                accessToken,
                calendar.accountEmail,
                calendar.id,
                windowStartMillis,
                windowEndMillis,
            )
        }
        out
    }

    private fun queryCalendarCandidates(
        accessToken: String,
        accountEmail: String,
        calendarId: String,
        windowStartMillis: Long,
        windowEndMillis: Long,
    ): List<CalendarAlarmCandidate> {
        val out = mutableListOf<CalendarAlarmCandidate>()
        val calendarPathId = URLEncoder.encode(calendarId, Charsets.UTF_8.name()).replace("+", "%20")
        var pageToken: String? = null
        do {
            val url = buildUrl(
                "https://www.googleapis.com/calendar/v3/calendars/$calendarPathId/events",
                mapOf(
                    "singleEvents" to "true",
                    "orderBy" to "startTime",
                    "timeMin" to Instant.ofEpochMilli(windowStartMillis).toString(),
                    "timeMax" to Instant.ofEpochMilli(windowEndMillis).toString(),
                    "maxResults" to "2500",
                    "pageToken" to pageToken,
                ),
            )
            val json = getJson(url, accessToken)
            val defaultReminderOffsets = json.optJSONArray("defaultReminders")
                .popupOffsets()
                .ifEmpty { listOf(CalendarRepository.FALLBACK_LEAD_MINUTES) }

            json.optJSONArray("items").orEmpty().forEachObject { event ->
                if (event.optString("status") == "cancelled") return@forEachObject
                if (!selfAttendeeStatusQualifies(event.optJSONArray("attendees"))) return@forEachObject

                val start = event.optJSONObject("start") ?: return@forEachObject
                val startMillis = startMillis(start) ?: return@forEachObject // all-day events have date, not dateTime
                val title = event.optString("summary").ifBlank { "(busy)" }
                val eventId = event.optString("id").ifBlank { event.optString("iCalUID") }
                if (eventId.isBlank()) return@forEachObject

                val offsets = reminderOffsets(event.optJSONObject("reminders"), defaultReminderOffsets)
                for (mins in offsets) {
                    val fire = startMillis - mins * 60_000L
                    if (fire > windowStartMillis && fire < windowEndMillis) {
                        out += CalendarAlarmCandidate(
                            provider = CalendarProviders.GOOGLE,
                            calendarId = googleCalendarKey(accountEmail, calendarId),
                            eventKey = eventId,
                            instanceStartMillis = startMillis,
                            fireTimeMillis = fire,
                            title = title,
                        )
                    }
                }
            }
            pageToken = json.optString("nextPageToken").ifBlank { null }
        } while (pageToken != null)
        return out
    }

    private fun getJson(url: String, accessToken: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }
        val status = connection.responseCode
        val body = if (status in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            throw IOException("Google Calendar API $status: $error")
        }
        return JSONObject(body)
    }

    private fun buildUrl(base: String, params: Map<String, String?>): String {
        val builder = Uri.parse(base).buildUpon()
        for ((key, value) in params) {
            if (!value.isNullOrBlank()) builder.appendQueryParameter(key, value)
        }
        return builder.build().toString()
    }

    private fun reminderOffsets(reminders: JSONObject?, defaultOffsets: List<Int>): List<Int> {
        if (reminders == null || reminders.optBoolean("useDefault", true)) return defaultOffsets
        return reminders.optJSONArray("overrides")
            .popupOffsets()
            .ifEmpty { listOf(CalendarRepository.FALLBACK_LEAD_MINUTES) }
    }

    private fun JSONArray?.popupOffsets(): List<Int> {
        if (this == null) return emptyList()
        val out = mutableListOf<Int>()
        forEachObject { reminder ->
            if (reminder.optString("method") == "popup") {
                out += reminder.optInt("minutes", CalendarRepository.FALLBACK_LEAD_MINUTES)
            }
        }
        return out.distinct()
    }

    private fun startMillis(start: JSONObject): Long? {
        val dateTime = start.optString("dateTime")
        if (dateTime.isBlank()) return null
        return runCatching { OffsetDateTime.parse(dateTime).toInstant().toEpochMilli() }
            .recoverCatching {
                LocalDate.parse(dateTime).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            .getOrNull()
    }

    private fun selfAttendeeStatusQualifies(attendees: JSONArray?): Boolean {
        if (attendees == null) return true
        attendees.forEachObject { attendee ->
            if (attendee.optBoolean("self", false)) {
                return when (attendee.optString("responseStatus")) {
                    "accepted", "needsAction", "" -> true
                    else -> false
                }
            }
        }
        return true
    }

    private fun parseColor(color: String): Int {
        if (!color.startsWith("#") || color.length != 7) return 0
        return runCatching { 0xFF000000.toInt() or color.drop(1).toInt(16) }.getOrDefault(0)
    }

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) {
            optJSONObject(i)?.let(block)
        }
    }

    private fun JSONArray?.orEmpty(): JSONArray = this ?: JSONArray()

    companion object {
        fun googleCalendarKey(accountEmail: String, calendarId: String): String =
            "$accountEmail|$calendarId"
    }
}
