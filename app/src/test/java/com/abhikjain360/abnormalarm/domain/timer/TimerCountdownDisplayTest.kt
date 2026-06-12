package com.abhikjain360.abnormalarm.domain.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimerCountdownDisplayTest {
    @Test fun displaySecondsRoundsUpPositiveRemainingTime() {
        assertEquals(5L, TimerCountdownDisplay.displaySeconds(5_000L))
        assertEquals(5L, TimerCountdownDisplay.displaySeconds(4_001L))
        assertEquals(4L, TimerCountdownDisplay.displaySeconds(4_000L))
        assertEquals(1L, TimerCountdownDisplay.displaySeconds(1L))
        assertEquals(0L, TimerCountdownDisplay.displaySeconds(0L))
    }

    @Test fun delayTargetsTheNextVisibleSecondBoundary() {
        assertEquals(1_000L, TimerCountdownDisplay.delayUntilNextDisplayChangeMillis(5_000L))
        assertEquals(999L, TimerCountdownDisplay.delayUntilNextDisplayChangeMillis(4_999L))
        assertEquals(1L, TimerCountdownDisplay.delayUntilNextDisplayChangeMillis(4_001L))
        assertEquals(1_000L, TimerCountdownDisplay.delayUntilNextDisplayChangeMillis(4_000L))
        assertEquals(1L, TimerCountdownDisplay.delayUntilNextDisplayChangeMillis(1L))
    }

    @Test fun zeroRemainingNeedsNoMoreDisplayTicks() {
        assertNull(TimerCountdownDisplay.delayUntilNextDisplayChangeMillis(0L))
        assertNull(TimerCountdownDisplay.delayUntilNextDisplayChangeMillis(-1L))
    }
}
