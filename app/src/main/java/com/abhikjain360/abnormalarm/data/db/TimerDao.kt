package com.abhikjain360.abnormalarm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TimerDao {
    @Query(
        "SELECT * FROM timers ORDER BY " +
            "CASE state WHEN 'RINGING' THEN 0 WHEN 'RUNNING' THEN 1 WHEN 'PAUSED' THEN 2 ELSE 3 END, " +
            "id ASC",
    )
    fun observeAll(): Flow<List<TimerEntity>>

    @Query("SELECT * FROM timers ORDER BY id ASC")
    suspend fun getAll(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE state = 'RUNNING'")
    suspend fun getRunning(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE state = 'RINGING' ORDER BY id ASC")
    suspend fun getRinging(): List<TimerEntity>

    @Query("SELECT * FROM timers WHERE id = :id")
    suspend fun get(id: Long): TimerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TimerEntity): Long

    @Update
    suspend fun update(entity: TimerEntity)

    @Query("DELETE FROM timers WHERE id = :id")
    suspend fun delete(id: Long)
}
