package com.abhikjain360.abnormalarm.domain

import com.abhikjain360.abnormalarm.domain.model.Timer
import kotlinx.coroutines.flow.Flow

/** Persistence boundary for saved countdown timers. */
interface TimerRepository {
    fun observeAll(): Flow<List<Timer>>
    suspend fun getAll(): List<Timer>
    suspend fun getRunning(): List<Timer>
    suspend fun getRinging(): List<Timer>
    suspend fun get(id: Long): Timer?
    /** Insert or update; returns the timer's id. */
    suspend fun upsert(timer: Timer): Long
    suspend fun delete(id: Long)
}
