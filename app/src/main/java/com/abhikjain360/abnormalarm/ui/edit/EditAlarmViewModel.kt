package com.abhikjain360.abnormalarm.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abhikjain360.abnormalarm.domain.AlarmRepository
import com.abhikjain360.abnormalarm.domain.model.Alarm
import com.abhikjain360.abnormalarm.domain.model.RepeatEnd
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import com.abhikjain360.abnormalarm.domain.model.RingSettings
import com.abhikjain360.abnormalarm.scheduling.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Backs the create/edit screen. A brand-new alarm (id 0) starts at the current time with every
 * "Advanced" field on its default — the creation flow asks for nothing but the time
 * ([[alarm-creation-defaults-principle]] / DESIGN.md §8).
 */
class EditAlarmViewModel(
    private val repository: AlarmRepository,
    private val scheduler: AlarmScheduler,
) : ViewModel() {

    private val _draft = MutableStateFlow(Alarm(time = LocalTime.of(7, 0)))
    val draft: StateFlow<Alarm> = _draft.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Load an existing alarm, or keep the default draft for a new one (id == 0). */
    fun load(id: Long) {
        if (_loaded.value) return
        if (id == 0L) {
            _loaded.value = true
            return
        }
        viewModelScope.launch {
            repository.get(id)?.let { _draft.value = it }
            _loaded.value = true
        }
    }

    fun setTime(time: LocalTime) = _draft.update { it.copy(time = time) }
    fun setLabel(label: String) = _draft.update { it.copy(label = label) }
    fun setRepeat(rule: RepeatRule) = _draft.update { it.copy(repeat = rule) }
    fun setEnd(end: RepeatEnd) = _draft.update { it.copy(end = end) }
    fun setRing(transform: (RingSettings) -> RingSettings) =
        _draft.update { it.copy(ring = transform(it.ring)) }

    /** Persist the draft and (re)schedule it. Invokes [onDone] with the saved id on completion. */
    fun save(onDone: (Long) -> Unit) = viewModelScope.launch {
        val draft = _draft.value.copy(enabled = true)
        val id = repository.upsert(draft)
        val saved = draft.copy(id = id)
        scheduler.schedule(saved)
        onDone(id)
    }

    companion object {
        fun factory(repository: AlarmRepository, scheduler: AlarmScheduler): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EditAlarmViewModel(repository, scheduler) as T
            }
    }
}
