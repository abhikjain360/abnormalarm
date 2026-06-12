package com.abhikjain360.abnormalarm.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

/**
 * The set of repeat modes (DESIGN.md §4). Pure Kotlin / java.time — NO Android imports,
 * so the recurrence math in [com.abhikjain360.abnormalarm.domain.schedule.nextOccurrence]
 * is unit-testable on the plain JVM.
 *
 * Interval-based rules carry an *anchor* so "every N" cycles are deterministic regardless of
 * when they're evaluated.
 */
sealed interface RepeatRule {

    /** Fires once at the next occurrence of the time, then the alarm disables itself. */
    data object Once : RepeatRule

    /**
     * Fires once on a specific calendar [date] (at the alarm's time-of-day), then disables itself.
     * Used for calendar-sourced alarms (DESIGN.md §5), where each event instance is a fixed-date
     * one-shot rather than a recurring time-of-day. Manual alarms use [Once].
     */
    data class OnceOnDate(val date: LocalDate) : RepeatRule

    /** Every [n] days from [anchor] (n=1 ⇒ daily). [n] >= 1. */
    data class EveryNDays(val n: Int, val anchor: LocalDate) : RepeatRule

    /** On the given weekdays every week (e.g. Mon/Wed/Fri). [days] non-empty. */
    data class Weekdays(val days: Set<DayOfWeek>) : RepeatRule

    /**
     * On [days] but only every [n]th week, counted in whole weeks from [anchorWeekMonday]
     * (which MUST be a Monday). e.g. every 2nd week on Tue/Thu.
     */
    data class EveryNWeeksOnDays(
        val n: Int,
        val days: Set<DayOfWeek>,
        val anchorWeekMonday: LocalDate,
    ) : RepeatRule

    /**
     * On the given calendar dates each month (1..31). Months that lack a given date are
     * SKIPPED (e.g. the 31st simply never fires in February) — see [DaysBeforeEndOfMonth]
     * for end-anchored behaviour instead.
     */
    data class DatesOfMonth(val days: Set<Int>) : RepeatRule

    /**
     * The [ordinal]-th [day] of each month (e.g. 2nd Tuesday). [ordinal] is 1..4, or [LAST]
     * for the last occurrence in the month.
     */
    data class NthWeekdayOfMonth(val ordinal: Int, val day: DayOfWeek) : RepeatRule

    /** Every [n] months on [dayOfMonth], from [anchor]. Short months are skipped (as [DatesOfMonth]). */
    data class EveryNMonthsOnDate(val n: Int, val dayOfMonth: Int, val anchor: YearMonth) : RepeatRule

    /** Once a year on [month]/[day] (Feb 29 ⇒ fires only in leap years). */
    data class Yearly(val month: Month, val day: Int) : RepeatRule

    /** [daysBefore] days before the end of each month. 0 = the last day of the month. */
    data class DaysBeforeEndOfMonth(val daysBefore: Int) : RepeatRule

    companion object {
        /** Sentinel [NthWeekdayOfMonth.ordinal] meaning "the last such weekday of the month". */
        const val LAST = -1
    }
}
