package com.abhikjain360.abnormalarm.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY time ASC, id ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms ORDER BY time ASC, id ASC")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getEnabled(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AlarmEntity): Long

    @Update
    suspend fun update(entity: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    // --- Calendar sync helpers (DESIGN.md §5) ---

    @Query("SELECT * FROM alarms WHERE source = 'CALENDAR'")
    suspend fun getCalendarAlarms(): List<AlarmEntity>

    @Query(
        "SELECT * FROM alarms WHERE source = 'CALENDAR' " +
            "AND calendarEventId = :eventId AND calendarInstanceStartMillis = :instanceStart",
    )
    suspend fun findCalendarAlarm(eventId: Long, instanceStart: Long): AlarmEntity?

    @Query("DELETE FROM alarms WHERE source = 'CALENDAR' AND id IN (:ids)")
    suspend fun deleteCalendarByIds(ids: List<Long>)
}
