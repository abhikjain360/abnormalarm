package com.abhikjain360.abnormalarm.domain.schedule

import com.abhikjain360.abnormalarm.domain.model.RepeatEnd
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Exhaustive tests for the repeat engine (DESIGN.md §4). All edge cases that bite alarm apps
 * live here: short-month skipping, leap years, last-weekday, DST, and stop conditions.
 */
class NextOccurrenceTest {

    private val utc = ZoneId.of("UTC")
    private val t07 = LocalTime.of(7, 0)
    private val t08 = LocalTime.of(8, 0)
    private val t09 = LocalTime.of(9, 0)

    private fun at(date: String, time: String) =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), utc)

    private fun assertNext(expected: String, actual: ZonedDateTime?) {
        assertEquals(expected, actual?.let { "${it.toLocalDate()}T${it.toLocalTime()}" })
    }

    @Test fun once_timePassedToday_movesToTomorrow() =
        assertNext("2026-06-13T09:00", nextOccurrence(t09, RepeatRule.Once, at("2026-06-12", "10:00")))

    @Test fun once_timeAheadToday_staysToday() =
        assertNext("2026-06-12T11:00", nextOccurrence(LocalTime.of(11, 0), RepeatRule.Once, at("2026-06-12", "10:00")))

    @Test fun onceOnDate_firesOnThatDateOnly() =
        assertNext(
            "2026-06-20T07:00",
            nextOccurrence(t07, RepeatRule.OnceOnDate(LocalDate.parse("2026-06-20")), at("2026-06-12", "10:00")),
        )

    @Test fun onceOnDate_pastDate_neverFires() =
        assertNull(
            nextOccurrence(t07, RepeatRule.OnceOnDate(LocalDate.parse("2026-06-01")), at("2026-06-12", "10:00")),
        )

    @Test fun everyNDays_respectsAnchorAndPassedTime() =
        assertNext(
            "2026-06-14T08:00",
            nextOccurrence(t08, RepeatRule.EveryNDays(2, LocalDate.parse("2026-06-10")), at("2026-06-12", "10:00")),
        )

    @Test fun weekdays_skipsToNextSelectedDay() =
        assertNext(
            "2026-06-15T07:00",
            nextOccurrence(t07, RepeatRule.Weekdays(setOf(MONDAY, WEDNESDAY, FRIDAY)), at("2026-06-12", "10:00")),
        )

    @Test fun everyNWeeks_onlyActiveWeeks() =
        assertNext(
            "2026-06-23T07:00",
            nextOccurrence(
                t07,
                RepeatRule.EveryNWeeksOnDays(2, setOf(TUESDAY), LocalDate.parse("2026-06-08")),
                at("2026-06-12", "10:00"),
            ),
        )

    @Test fun datesOfMonth_skipsMonthsWithoutThatDate() =
        assertNext(
            "2026-03-31T08:00",
            nextOccurrence(t08, RepeatRule.DatesOfMonth(setOf(31)), at("2026-02-01", "00:00")),
        )

    @Test fun nthWeekday_secondTuesday() =
        assertNext(
            "2026-07-14T07:00",
            nextOccurrence(t07, RepeatRule.NthWeekdayOfMonth(2, TUESDAY), at("2026-06-12", "10:00")),
        )

    @Test fun nthWeekday_lastFriday() =
        assertNext(
            "2026-06-26T07:00",
            nextOccurrence(t07, RepeatRule.NthWeekdayOfMonth(RepeatRule.LAST, FRIDAY), at("2026-06-12", "10:00")),
        )

    @Test fun everyNMonths_onDate() =
        assertNext(
            "2026-07-15T08:00",
            nextOccurrence(t08, RepeatRule.EveryNMonthsOnDate(3, 15, YearMonth.of(2026, 1)), at("2026-06-12", "10:00")),
        )

    @Test fun everyNMonths_skipsShortMonths() =
        assertNext(
            "2026-03-31T08:00",
            nextOccurrence(t08, RepeatRule.EveryNMonthsOnDate(1, 31, YearMonth.of(2026, 1)), at("2026-02-01", "00:00")),
        )

    @Test fun yearly_feb29_onlyLeapYears() =
        assertNext(
            "2028-02-29T08:00",
            nextOccurrence(t08, RepeatRule.Yearly(Month.FEBRUARY, 29), at("2026-03-01", "00:00")),
        )

    @Test fun daysBeforeEndOfMonth_zeroIsLastDay() =
        assertNext(
            "2026-06-30T08:00",
            nextOccurrence(t08, RepeatRule.DaysBeforeEndOfMonth(0), at("2026-06-12", "10:00")),
        )

    @Test fun daysBeforeEndOfMonth_nonLeapFebruary() =
        assertNext(
            "2027-02-27T08:00",
            nextOccurrence(t08, RepeatRule.DaysBeforeEndOfMonth(1), at("2027-02-01", "00:00")),
        )

    @Test fun end_afterCountReached_returnsNull() =
        assertNull(
            nextTrigger(
                t08, RepeatRule.EveryNDays(1, LocalDate.parse("2026-01-01")),
                RepeatEnd.AfterCount(3), firedCount = 3, after = at("2026-06-12", "10:00"),
            ),
        )

    @Test fun end_onDatePast_returnsNull() =
        assertNull(
            nextTrigger(
                t08, RepeatRule.Weekdays(setOf(MONDAY)),
                RepeatEnd.OnDate(LocalDate.parse("2026-06-12")), firedCount = 0, after = at("2026-06-12", "10:00"),
            ),
        )

    @Test fun dst_springForwardShiftsIntoGap() {
        val berlin = ZoneId.of("Europe/Berlin")
        val next = nextOccurrence(
            LocalTime.of(2, 30),
            RepeatRule.EveryNDays(1, LocalDate.parse("2026-01-01")),
            ZonedDateTime.of(LocalDate.parse("2026-03-28"), LocalTime.of(12, 0), berlin),
            berlin,
        )
        // 02:30 doesn't exist on 2026-03-29 in Berlin (clocks jump 02:00->03:00); java.time
        // resolves the gap forward to 03:30.
        assertEquals(LocalDate.parse("2026-03-29"), next?.toLocalDate())
        assertEquals(LocalTime.of(3, 30), next?.toLocalTime())
    }
}
