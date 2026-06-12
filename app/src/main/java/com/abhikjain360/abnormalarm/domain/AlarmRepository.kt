package com.abhikjain360.abnormalarm.domain

import com.abhikjain360.abnormalarm.domain.model.Alarm
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary for alarms. The domain/scheduling layers depend only on this interface;
 * the data layer provides the Room-backed implementation.
 */
interface AlarmRepository {
    fun observeAll(): Flow<List<Alarm>>
    suspend fun getAll(): List<Alarm>
    suspend fun getEnabled(): List<Alarm>
    suspend fun get(id: Long): Alarm?
    /** Insert or update; returns the alarm's id. */
    suspend fun upsert(alarm: Alarm): Long
    suspend fun delete(id: Long)
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
