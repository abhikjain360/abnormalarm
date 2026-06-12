package com.abhikjain360.abnormalarm.data

import com.abhikjain360.abnormalarm.data.db.AlarmDao
import com.abhikjain360.abnormalarm.data.db.toDomain
import com.abhikjain360.abnormalarm.data.db.toEntity
import com.abhikjain360.abnormalarm.domain.AlarmRepository
import com.abhikjain360.abnormalarm.domain.model.Alarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Durable [AlarmRepository] backed by Room (DESIGN.md §3). Replaces the in-memory stub so alarms
 * survive process death — the foundation of the whole reliability story.
 */
class RoomAlarmRepository(private val dao: AlarmDao) : AlarmRepository {

    override fun observeAll(): Flow<List<Alarm>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Alarm> = dao.getAll().map { it.toDomain() }

    override suspend fun getEnabled(): List<Alarm> = dao.getEnabled().map { it.toDomain() }

    override suspend fun get(id: Long): Alarm? = dao.get(id)?.toDomain()

    override suspend fun upsert(alarm: Alarm): Long {
        val entity = alarm.toEntity()
        return if (alarm.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            alarm.id
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
}
