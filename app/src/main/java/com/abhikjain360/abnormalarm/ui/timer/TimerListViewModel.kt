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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Order timers the way the alarm list orders alarms: soonest to go off first.
 *
 * RINGING timers are going off right now, so they sit at the top; RUNNING timers follow, ordered by
 * their exact expiry instant ([Timer.endAtMillis]). PAUSED and IDLE timers aren't counting down —
 * like a disabled alarm they sink below the active ones — ordered by how long they would run for
 * (remaining time, then preset duration). [Timer.id] is the final, stable tiebreak.
 */
internal fun timersSortedByExpiry(timers: List<Timer>): List<Timer> =
    timers.sortedWith(
        compareBy<Timer> { stateRank(it.state) }
            .thenBy { expiryWithinState(it) }
            .thenBy { it.id },
    )

private fun stateRank(state: TimerState): Int = when (state) {
    TimerState.RINGING -> 0
    TimerState.RUNNING -> 1
    TimerState.PAUSED -> 2
    TimerState.IDLE -> 3
}

/** Within a state bucket, the smaller value goes off (or would go off) sooner. */
private fun expiryWithinState(timer: Timer): Long = when (timer.state) {
    TimerState.RINGING -> 0L
    TimerState.RUNNING -> timer.endAtMillis ?: Long.MAX_VALUE
    TimerState.PAUSED -> timer.remainingMillis ?: timer.durationMillis
    TimerState.IDLE -> timer.durationMillis
}

class TimerListViewModel(
    private val repository: TimerRepository,
    private val scheduler: TimerScheduler,
    private val appContext: Context,
) : ViewModel() {
    val timers: StateFlow<List<Timer>> = repository.observeAll()
        .map(::timersSortedByExpiry)
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
