package com.abhikjain360.abnormalarm.domain.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerDurationInputTest {
    @Test fun digitsFillSecondsFromRight() {
        assertEquals(1L, TimerDurationInput.secondsFromDigits("1"))
        assertEquals(12L, TimerDurationInput.secondsFromDigits("12"))
        assertEquals(83L, TimerDurationInput.secondsFromDigits("123"))
        assertEquals(754L, TimerDurationInput.secondsFromDigits("1234"))
    }

    @Test fun doubleZeroAppendsTwoZeros() {
        val digits = TimerDurationInput.append("5", "00")
        assertEquals("500", digits)
        assertEquals(5 * 60L, TimerDurationInput.secondsFromDigits(digits))
    }

    @Test fun emptyZeroInputStaysEmpty() {
        assertEquals("", TimerDurationInput.append("", "0"))
        assertEquals("", TimerDurationInput.append("", "00"))
    }

    @Test fun savedDurationRoundTripsToDigits() {
        val seconds = 1 * 3600L + 2 * 60L + 3L
        val digits = TimerDurationInput.digitsFromSeconds(seconds)
        assertEquals("10203", digits)
        assertEquals(seconds, TimerDurationInput.secondsFromDigits(digits))
    }

    @Test fun durationDisplayIsNormalized() {
        assertEquals("00:01:39", TimerDurationInput.formatSeconds(99))
        assertEquals("02:03:04", TimerDurationInput.formatSeconds(2 * 3600L + 3 * 60L + 4L))
    }
}
