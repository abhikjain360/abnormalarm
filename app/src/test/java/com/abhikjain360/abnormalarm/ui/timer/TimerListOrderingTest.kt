package com.abhikjain360.abnormalarm.ui.timer

import com.abhikjain360.abnormalarm.domain.model.Timer
import com.abhikjain360.abnormalarm.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerListOrderingTest {

    @Test fun runningTimersOrderByExpiryInstantNotPresetDuration() {
        val longPresetFiresSooner = timer(id = 1L, durationMillis = 60 * 60_000L, endAtMillis = 1_000L)
        val shortPresetFiresLater = timer(id = 2L, durationMillis = 60_000L, endAtMillis = 5_000L)

        val sorted = timersSortedByExpiry(listOf(shortPresetFiresLater, longPresetFiresSooner))

        assertEquals(listOf(1L, 2L), sorted.map { it.id })
    }

    @Test fun ringingFirstRunningNextThenPausedThenIdle() {
        val idle = timer(id = 1L, state = TimerState.IDLE)
        val running = timer(id = 2L, state = TimerState.RUNNING, endAtMillis = 5_000L)
        val paused = timer(id = 3L, state = TimerState.PAUSED, remainingMillis = 1_000L)
        val ringing = timer(id = 4L, state = TimerState.RINGING)

        val sorted = timersSortedByExpiry(listOf(idle, paused, running, ringing))

        assertEquals(listOf(4L, 2L, 3L, 1L), sorted.map { it.id })
    }

    @Test fun idleTimersOrderByPresetDuration() {
        val tenMin = timer(id = 1L, state = TimerState.IDLE, durationMillis = 10 * 60_000L)
        val oneMin = timer(id = 2L, state = TimerState.IDLE, durationMillis = 60_000L)

        val sorted = timersSortedByExpiry(listOf(tenMin, oneMin))

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test fun pausedTimersOrderByRemainingTime() {
        val moreLeft = timer(id = 1L, state = TimerState.PAUSED, remainingMillis = 90_000L)
        val lessLeft = timer(id = 2L, state = TimerState.PAUSED, remainingMillis = 30_000L)

        val sorted = timersSortedByExpiry(listOf(moreLeft, lessLeft))

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    @Test fun idIsStableTiebreakWhenExpiryTies() {
        val a = timer(id = 5L, state = TimerState.RUNNING, endAtMillis = 1_000L)
        val b = timer(id = 3L, state = TimerState.RUNNING, endAtMillis = 1_000L)

        val sorted = timersSortedByExpiry(listOf(a, b))

        assertEquals(listOf(3L, 5L), sorted.map { it.id })
    }

    private fun timer(
        id: Long,
        durationMillis: Long = 60_000L,
        state: TimerState = TimerState.RUNNING,
        endAtMillis: Long? = null,
        remainingMillis: Long? = null,
    ): Timer = Timer(
        id = id,
        durationMillis = durationMillis,
        state = state,
        endAtMillis = endAtMillis,
        remainingMillis = remainingMillis,
    )
}
