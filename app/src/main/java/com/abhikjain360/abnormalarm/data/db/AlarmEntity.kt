package com.abhikjain360.abnormalarm.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.abhikjain360.abnormalarm.domain.model.Alarm
import com.abhikjain360.abnormalarm.domain.model.AlarmSource
import com.abhikjain360.abnormalarm.domain.model.RepeatEnd
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import com.abhikjain360.abnormalarm.domain.model.RingSettings
import java.time.LocalTime

/**
 * Room mirror of the pure-domain [Alarm] (DESIGN.md §3 — entities live in `data/`, never `domain/`).
 * [RingSettings] is `@Embedded` (its fields become columns); [repeat]/[end]/[time]/[source] use the
 * [Converters]. Calendar identity columns are indexed for sync upserts.
 */
@Entity(
    tableName = "alarms",
    indices = [
        Index(value = ["calendarEventId", "calendarInstanceStartMillis"]),
        Index(value = ["calendarProvider", "calendarId", "calendarEventKey", "calendarInstanceStartMillis"]),
    ],
)
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val time: LocalTime,
    val enabled: Boolean,
    val repeat: RepeatRule,
    val end: RepeatEnd,
    @Embedded(prefix = "ring_") val ring: RingSettings,
    val source: AlarmSource,
    val skipNextInstantMillis: Long?,
    val firedCount: Int,
    val calendarProvider: String?,
    val calendarId: String?,
    val calendarEventKey: String?,
    val calendarEventId: Long?,
    val calendarInstanceStartMillis: Long?,
)

fun AlarmEntity.toDomain(): Alarm = Alarm(
    id = id,
    label = label,
    time = time,
    enabled = enabled,
    repeat = repeat,
    end = end,
    ring = ring,
    source = source,
    skipNextInstantMillis = skipNextInstantMillis,
    firedCount = firedCount,
    calendarProvider = calendarProvider,
    calendarId = calendarId,
    calendarEventKey = calendarEventKey,
    calendarEventId = calendarEventId,
    calendarInstanceStartMillis = calendarInstanceStartMillis,
)

fun Alarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    label = label,
    time = time,
    enabled = enabled,
    repeat = repeat,
    end = end,
    ring = ring,
    source = source,
    skipNextInstantMillis = skipNextInstantMillis,
    firedCount = firedCount,
    calendarProvider = calendarProvider,
    calendarId = calendarId,
    calendarEventKey = calendarEventKey,
    calendarEventId = calendarEventId,
    calendarInstanceStartMillis = calendarInstanceStartMillis,
)
