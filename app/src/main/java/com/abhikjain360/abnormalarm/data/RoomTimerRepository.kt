package com.abhikjain360.abnormalarm.data

import com.abhikjain360.abnormalarm.data.db.TimerDao
import com.abhikjain360.abnormalarm.data.db.toDomain
import com.abhikjain360.abnormalarm.data.db.toEntity
import com.abhikjain360.abnormalarm.domain.TimerRepository
import com.abhikjain360.abnormalarm.domain.model.Timer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Durable [TimerRepository] backed by Room. */
class RoomTimerRepository(private val dao: TimerDao) : TimerRepository {
    override fun observeAll(): Flow<List<Timer>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Timer> = dao.getAll().map { it.toDomain() }

    override suspend fun getRunning(): List<Timer> = dao.getRunning().map { it.toDomain() }

    override suspend fun getRinging(): List<Timer> = dao.getRinging().map { it.toDomain() }

    override suspend fun get(id: Long): Timer? = dao.get(id)?.toDomain()

    override suspend fun upsert(timer: Timer): Long {
        val entity = timer.toEntity()
        return if (timer.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            timer.id
        }
    }

    override suspend fun delete(id: Long) = dao.delete(id)
}
