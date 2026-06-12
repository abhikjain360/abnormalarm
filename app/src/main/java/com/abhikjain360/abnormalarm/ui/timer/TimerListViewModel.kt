package com.abhikjain360.abnormalarm.ui.timer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.abhikjain360.abnormalarm.domain.TimerRepository
import com.abhikjain360.abnormalarm.domain.model.Timer
import com.abhikjain360.abnormalarm.domain.model.TimerState
import com.abhikjain360.abnormalarm.ring.RingService
import com.abhikjain360.abnormalarm.scheduling.TimerScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TimerListViewModel(
    private val repository: TimerRepository,
    private val scheduler: TimerScheduler,
    private val appContext: Context,
) : ViewModel() {
    val timers: StateFlow<List<Timer>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun start(id: Long) = viewModelScope.launch {
        val timer = repository.get(id) ?: return@launch
        val duration = when (timer.state) {
            TimerState.PAUSED -> timer.remainingMillis ?: timer.durationMillis
            else -> timer.durationMillis
        }.coerceAtLeast(1_000L)
        val updated = timer.copy(
            state = TimerState.RUNNING,
            endAtMillis = System.currentTimeMillis() + duration,
            remainingMillis = null,
        )
        repository.upsert(updated)
        scheduler.schedule(updated)
    }

    fun pause(id: Long) = viewModelScope.launch {
        val timer = repository.get(id) ?: return@launch
        if (timer.state != TimerState.RUNNING) return@launch
        val remaining = ((timer.endAtMillis ?: System.currentTimeMillis()) - System.currentTimeMillis())
            .coerceAtLeast(1_000L)
        val updated = timer.copy(
            state = TimerState.PAUSED,
            endAtMillis = null,
            remainingMillis = remaining,
        )
        repository.upsert(updated)
        scheduler.cancel(id)
    }

    fun reset(id: Long) = viewModelScope.launch {
        val timer = repository.get(id) ?: return@launch
        scheduler.cancel(id)
        if (timer.state == TimerState.RINGING) {
            repository.upsert(timer.toIdle())
            RingService.dismissTimer(appContext, id)
        } else {
            repository.upsert(timer.toIdle())
        }
    }

    fun delete(id: Long) = viewModelScope.launch {
        val timer = repository.get(id) ?: return@launch
        if (timer.state == TimerState.RUNNING || timer.state == TimerState.RINGING) return@launch
        scheduler.cancel(id)
        repository.delete(id)
    }

    private fun Timer.toIdle(): Timer = copy(
        state = TimerState.IDLE,
        endAtMillis = null,
        remainingMillis = null,
    )

    companion object {
        fun factory(
            repository: TimerRepository,
            scheduler: TimerScheduler,
            appContext: Context,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TimerListViewModel(repository, scheduler, appContext.applicationContext) as T
        }
    }
}
