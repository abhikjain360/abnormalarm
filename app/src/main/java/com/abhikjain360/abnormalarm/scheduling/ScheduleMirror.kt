package com.abhikjain360.abnormalarm.scheduling

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.abhikjain360.abnormalarm.data.db.Converters
import com.abhikjain360.abnormalarm.domain.AlarmRepository
import com.abhikjain360.abnormalarm.domain.TimerRepository
import com.abhikjain360.abnormalarm.domain.model.Alarm
import com.abhikjain360.abnormalarm.domain.model.AlarmSource
import com.abhikjain360.abnormalarm.domain.model.RingSettings
import com.abhikjain360.abnormalarm.domain.model.Timer
import com.abhikjain360.abnormalarm.domain.model.TimerState
import org.json.JSONObject
import java.time.LocalTime

/**
 * Device-protected mirror of just enough alarm/timer state to re-register and ring after a reboot
 * before the user unlocks the device. Room and DataStore remain the normal source of truth once
 * credential-protected storage is available.
 */
object ScheduleMirror {
    private const val PREFS = "direct_boot_schedule"
    private const val ALARM_IDS = "alarm_ids"
    private const val TIMER_IDS = "timer_ids"
    private const val ALARM_PREFIX = "alarm."
    private const val TIMER_PREFIX = "timer."

    private val converters = Converters()

    // commit = true is intentional: this mirror is the direct-boot fallback for exact schedules.
    fun upsertAlarm(context: Context, alarm: Alarm) {
        if (alarm.id <= 0L) return
        val p = prefs(context)
        p.edit(commit = true) {
            putString(alarmKey(alarm.id), alarm.toJson().toString())
            putId(p, ALARM_IDS, alarm.id)
        }
    }

    fun removeAlarm(context: Context, alarmId: Long) {
        val p = prefs(context)
        p.edit(commit = true) {
            remove(alarmKey(alarmId))
            removeId(p, ALARM_IDS, alarmId)
        }
    }

    fun getAlarm(context: Context, alarmId: Long): Alarm? =
        prefs(context).getString(alarmKey(alarmId), null)?.let(::alarmFromJson)

    fun getAlarms(context: Context): List<Alarm> {
        val p = prefs(context)
        return p.getStringSet(ALARM_IDS, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .mapNotNull { id -> p.getString(alarmKey(id), null)?.let(::alarmFromJson) }
    }

    fun upsertTimer(context: Context, timer: Timer) {
        if (timer.id <= 0L) return
        val p = prefs(context)
        p.edit(commit = true) {
            putString(timerKey(timer.id), timer.toJson().toString())
            putId(p, TIMER_IDS, timer.id)
        }
    }

    fun removeTimer(context: Context, timerId: Long) {
        val p = prefs(context)
        p.edit(commit = true) {
            remove(timerKey(timerId))
            removeId(p, TIMER_IDS, timerId)
        }
    }

    fun getTimer(context: Context, timerId: Long): Timer? =
        prefs(context).getString(timerKey(timerId), null)?.let(::timerFromJson)

    fun getTimers(context: Context): List<Timer> {
        val p = prefs(context)
        return p.getStringSet(TIMER_IDS, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .mapNotNull { id -> p.getString(timerKey(id), null)?.let(::timerFromJson) }
    }

    fun getRingingTimers(context: Context): List<Timer> =
        getTimers(context).filter { it.state == TimerState.RINGING }.sortedBy { it.id }

    suspend fun reconcileToRepositories(
        context: Context,
        alarmRepository: AlarmRepository,
        timerRepository: TimerRepository,
    ) {
        for (mirrored in getAlarms(context)) {
            val persisted = alarmRepository.get(mirrored.id)
            if (persisted == null) {
                removeAlarm(context, mirrored.id)
                continue
            }
            alarmRepository.upsert(
                persisted.copy(
                    enabled = mirrored.enabled,
                    skipNextInstantMillis = mirrored.skipNextInstantMillis,
                    firedCount = mirrored.firedCount,
                ),
            )
            if (!mirrored.enabled) removeAlarm(context, mirrored.id)
        }
        for (mirrored in getTimers(context)) {
            val persisted = timerRepository.get(mirrored.id)
            if (persisted == null) {
                removeTimer(context, mirrored.id)
                continue
            }
            timerRepository.upsert(
                persisted.copy(
                    state = mirrored.state,
                    endAtMillis = mirrored.endAtMillis,
                    remainingMillis = mirrored.remainingMillis,
                ),
            )
            if (mirrored.state != TimerState.RUNNING && mirrored.state != TimerState.RINGING) {
                removeTimer(context, mirrored.id)
            }
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun alarmKey(id: Long): String = "$ALARM_PREFIX$id"

    private fun timerKey(id: Long): String = "$TIMER_PREFIX$id"

    private fun SharedPreferences.Editor.putId(
        prefs: SharedPreferences,
        key: String,
        id: Long,
    ): SharedPreferences.Editor {
        val current = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        current += id.toString()
        return putStringSet(key, current)
    }

    private fun SharedPreferences.Editor.removeId(
        prefs: SharedPreferences,
        key: String,
        id: Long,
    ): SharedPreferences.Editor {
        val current = prefs.getStringSet(key, emptySet()).orEmpty().toMutableSet()
        current -= id.toString()
        return putStringSet(key, current)
    }

    private fun Alarm.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("time", time.toSecondOfDay())
        .put("enabled", enabled)
        .put("repeat", converters.ruleToString(repeat))
        .put("end", converters.endToString(end))
        .put("ring", ring.toJson())
        .put("source", source.name)
        .putNullable("skipNextInstantMillis", skipNextInstantMillis)
        .put("firedCount", firedCount)
        .putNullable("calendarProvider", calendarProvider)
        .putNullable("calendarId", calendarId)
        .putNullable("calendarEventKey", calendarEventKey)
        .putNullable("calendarEventId", calendarEventId)
        .putNullable("calendarInstanceStartMillis", calendarInstanceStartMillis)

    private fun alarmFromJson(raw: String): Alarm? = runCatching {
        val json = JSONObject(raw)
        Alarm(
            id = json.getLong("id"),
            label = json.optString("label"),
            time = LocalTime.ofSecondOfDay(json.getInt("time").toLong()),
            enabled = json.optBoolean("enabled", true),
            repeat = converters.stringToRule(json.getString("repeat")),
            end = converters.stringToEnd(json.optString("end", "never")),
            ring = ringFromJson(json.optJSONObject("ring")),
            source = runCatching { AlarmSource.valueOf(json.optString("source", "MANUAL")) }
                .getOrDefault(AlarmSource.MANUAL),
            skipNextInstantMillis = json.optLongOrNull("skipNextInstantMillis"),
            firedCount = json.optInt("firedCount", 0),
            calendarProvider = json.optStringOrNull("calendarProvider"),
            calendarId = json.optStringOrNull("calendarId"),
            calendarEventKey = json.optStringOrNull("calendarEventKey"),
            calendarEventId = json.optLongOrNull("calendarEventId"),
            calendarInstanceStartMillis = json.optLongOrNull("calendarInstanceStartMillis"),
        )
    }.getOrNull()

    private fun Timer.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("durationMillis", durationMillis)
        .put("state", state.name)
        .putNullable("endAtMillis", endAtMillis)
        .putNullable("remainingMillis", remainingMillis)
        .put("ring", ring.toJson())

    private fun timerFromJson(raw: String): Timer? = runCatching {
        val json = JSONObject(raw)
        Timer(
            id = json.getLong("id"),
            label = json.optString("label"),
            durationMillis = json.getLong("durationMillis"),
            state = runCatching { TimerState.valueOf(json.optString("state", "IDLE")) }
                .getOrDefault(TimerState.IDLE),
            endAtMillis = json.optLongOrNull("endAtMillis"),
            remainingMillis = json.optLongOrNull("remainingMillis"),
            ring = ringFromJson(json.optJSONObject("ring")),
        )
    }.getOrNull()

    private fun RingSettings.toJson(): JSONObject = JSONObject()
        .putNullable("soundUri", soundUri)
        .put("volumeRampSeconds", volumeRampSeconds)
        .put("vibrate", vibrate)
        .put("flashlight", flashlight)
        .put("snoozeEnabled", snoozeEnabled)
        .put("snoozeMinutes", snoozeMinutes)
        .put("autoSilenceMinutes", autoSilenceMinutes)

    private fun ringFromJson(json: JSONObject?): RingSettings {
        if (json == null) return RingSettings()
        return RingSettings(
            soundUri = json.optStringOrNull("soundUri"),
            volumeRampSeconds = json.optInt("volumeRampSeconds", 0),
            vibrate = json.optBoolean("vibrate", true),
            flashlight = json.optBoolean("flashlight", false),
            snoozeEnabled = json.optBoolean("snoozeEnabled", true),
            snoozeMinutes = json.optInt("snoozeMinutes", 10),
            autoSilenceMinutes = json.optInt("autoSilenceMinutes", 10),
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        if (value == null) put(key, JSONObject.NULL) else put(key, value)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null
}
