package com.abhikjain360.abnormalarm.data.db

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.abhikjain360.abnormalarm.domain.model.RingSettings
import com.abhikjain360.abnormalarm.domain.model.Timer
import com.abhikjain360.abnormalarm.domain.model.TimerState

/** Room mirror of [Timer]. */
@Entity(tableName = "timers")
data class TimerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val label: String,
    val durationMillis: Long,
    val state: TimerState,
    val endAtMillis: Long?,
    val remainingMillis: Long?,
    @Embedded(prefix = "ring_") val ring: RingSettings,
)

fun TimerEntity.toDomain(): Timer = Timer(
    id = id,
    label = label,
    durationMillis = durationMillis,
    state = state,
    endAtMillis = endAtMillis,
    remainingMillis = remainingMillis,
    ring = ring,
)

fun Timer.toEntity(): TimerEntity = TimerEntity(
    id = id,
    label = label,
    durationMillis = durationMillis,
    state = state,
    endAtMillis = endAtMillis,
    remainingMillis = remainingMillis,
    ring = ring,
)
