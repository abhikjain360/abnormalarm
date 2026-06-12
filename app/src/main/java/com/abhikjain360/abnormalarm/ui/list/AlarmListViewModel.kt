package com.abhikjain360.abnormalarm.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abhikjain360.abnormalarm.domain.AlarmRepository
import com.abhikjain360.abnormalarm.domain.model.Alarm
import com.abhikjain360.abnormalarm.domain.model.AlarmSource
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import com.abhikjain360.abnormalarm.scheduling.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** One alarm as the list renders it: the domain alarm plus its computed next fire instant. */
data class AlarmRow(val alarm: Alarm, val nextFire: ZonedDateTime?) {
    val isCalendar: Boolean get() = alarm.source == AlarmSource.CALENDAR
}

class AlarmListViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    val rows: StateFlow<List<AlarmRow>> = repository.observeAll()
        .map { alarms -> alarms.map { AlarmRow(it, scheduler.computeTrigger(it)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        repository.setEnabled(id, enabled)
        val alarm = repository.get(id) ?: return@launch
        if (enabled) scheduler.schedule(alarm) else scheduler.cancel(id)
    }

    fun delete(id: Long) = viewModelScope.launch {
        scheduler.cancel(id)
        repository.delete(id)
    }

    /** Pre-emptively skip the next occurrence of an alarm (DESIGN.md §7). */
    fun skipNext(id: Long) = viewModelScope.launch {
        val alarm = repository.get(id) ?: return@launch
        val next = scheduler.computeTrigger(alarm) ?: return@launch
        val oneShot = alarm.repeat == RepeatRule.Once || alarm.repeat is RepeatRule.OnceOnDate
        val updated = if (oneShot) {
            alarm.copy(enabled = false, skipNextInstantMillis = null)
        } else {
            alarm.copy(skipNextInstantMillis = next.toInstant().toEpochMilli())
        }
        repository.upsert(updated)
        if (oneShot) scheduler.cancel(id) else scheduler.schedule(updated)
    }

    companion object {
        fun factory(repository: AlarmRepository, scheduler: AlarmScheduler): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AlarmListViewModel(repository, scheduler) as T
            }
    }
}
