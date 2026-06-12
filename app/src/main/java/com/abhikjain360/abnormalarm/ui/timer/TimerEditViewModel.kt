package com.abhikjain360.abnormalarm.ui.timer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abhikjain360.abnormalarm.domain.TimerRepository
import com.abhikjain360.abnormalarm.domain.model.RingSettings
import com.abhikjain360.abnormalarm.domain.model.Timer
import com.abhikjain360.abnormalarm.domain.model.TimerState
import com.abhikjain360.abnormalarm.domain.timer.TimerDurationInput
import com.abhikjain360.abnormalarm.ring.RingService
import com.abhikjain360.abnormalarm.scheduling.TimerScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TimerEditViewModel(
    private val repository: TimerRepository,
    private val scheduler: TimerScheduler,
    private val appContext: Context,
) : ViewModel() {
    private var existing: Timer? = null

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _digits = MutableStateFlow("")
    val digits: StateFlow<String> = _digits.asStateFlow()

    private val _label = MutableStateFlow("")
    val label: StateFlow<String> = _label.asStateFlow()

    fun load(id: Long) {
        if (_loaded.value) return
        if (id == 0L) {
            _loaded.value = true
            return
        }
        viewModelScope.launch {
            existing = repository.get(id)
            existing?.let { timer ->
                _digits.value = TimerDurationInput.digitsFromSeconds(timer.durationMillis / 1000L)
                _label.value = timer.label
            }
            _loaded.value = true
        }
    }

    fun append(token: String) = _digits.update { TimerDurationInput.append(it, token) }
    fun backspace() = _digits.update { TimerDurationInput.backspace(it) }
    fun setLabel(label: String) = _label.update { label }

    fun durationMillis(): Long = TimerDurationInput.secondsFromDigits(_digits.value) * 1000L

    fun save(onDone: (Long) -> Unit) = viewModelScope.launch {
        val duration = durationMillis()
        if (duration <= 0L) return@launch
        val previous = existing
        if (previous != null) {
            scheduler.cancel(previous.id)
            if (previous.state == TimerState.RINGING) RingService.dismissTimer(appContext, previous.id)
        }
        val timer = (previous ?: Timer(durationMillis = duration)).copy(
            label = _label.value.trim(),
            durationMillis = duration,
            state = TimerState.IDLE,
            endAtMillis = null,
            remainingMillis = null,
            ring = previous?.ring ?: RingSettings(snoozeEnabled = false),
        )
        val id = repository.upsert(timer)
        onDone(id)
    }

    companion object {
        fun factory(
            repository: TimerRepository,
            scheduler: TimerScheduler,
            appContext: Context,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TimerEditViewModel(repository, scheduler, appContext.applicationContext) as T
        }
    }
}
