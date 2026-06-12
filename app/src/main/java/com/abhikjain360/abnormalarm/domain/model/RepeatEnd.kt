package com.abhikjain360.abnormalarm.domain.model

import java.time.LocalDate

/**
 * Optional stop condition for a repeating alarm (DESIGN.md §4). Default [Never].
 * Applied by [com.abhikjain360.abnormalarm.domain.schedule.nextTrigger], not by the raw
 * recurrence pattern.
 */
sealed interface RepeatEnd {

    /** Repeat indefinitely until the user turns the alarm off. */
    data object Never : RepeatEnd

    /** Stop after [date] (inclusive) — no occurrence later than this date. */
    data class OnDate(val date: LocalDate) : RepeatEnd

    /** Stop after [count] total fires. */
    data class AfterCount(val count: Int) : RepeatEnd
}
