package com.abhikjain360.abnormalarm.domain.schedule

import com.abhikjain360.abnormalarm.domain.model.RepeatEnd
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * The heart of Abnormalarm's repeat engine (DESIGN.md §4).
 *
 * Pure function with NO Android dependencies (unit-tested on the JVM): given a [timeOfDay]
 * and a recurrence [rule], returns the earliest [ZonedDateTime] **strictly after** [after]
 * whose date satisfies the rule, with the wall-clock time set to [timeOfDay] resolved in
 * [zone]. DST gaps/overlaps are resolved by `java.time` (a spring-forward gap shifts the
 * fire time forward by the gap; that's accepted and intentional).
 *
 * This models only the recurrence *pattern*; stop conditions live in [nextTrigger].
 *
 * Returns null only if nothing matches within [HORIZON_DAYS] — not expected for valid rules,
 * but a hard backstop against pathological inputs causing an unbounded loop.
 */
fun nextOccurrence(
    timeOfDay: LocalTime,
    rule: RepeatRule,
    after: ZonedDateTime,
    zone: ZoneId = after.zone,
): ZonedDateTime? {
    val afterInstant = after.toInstant()
    var date = after.withZoneSameInstant(zone).toLocalDate()
    var iterations = 0
    while (iterations <= HORIZON_DAYS) {
        if (matchesDate(rule, date)) {
            val candidate = ZonedDateTime.of(date, timeOfDay, zone)
            if (candidate.toInstant().isAfter(afterInstant)) return candidate
        }
        date = date.plusDays(1)
        iterations++
    }
    return null
}

/**
 * [nextOccurrence] with the [end] condition applied. [firedCount] is how many times this alarm
 * has already fired (for [RepeatEnd.AfterCount]). Returns null when the alarm should stop.
 */
fun nextTrigger(
    timeOfDay: LocalTime,
    rule: RepeatRule,
    end: RepeatEnd,
    firedCount: Int,
    after: ZonedDateTime,
    zone: ZoneId = after.zone,
): ZonedDateTime? {
    if (end is RepeatEnd.AfterCount && firedCount >= end.count) return null
    val next = nextOccurrence(timeOfDay, rule, after, zone) ?: return null
    if (end is RepeatEnd.OnDate && next.toLocalDate().isAfter(end.date)) return null
    return next
}

/** True if [d] is a date on which [rule] fires. */
private fun matchesDate(rule: RepeatRule, d: LocalDate): Boolean = when (rule) {
    RepeatRule.Once -> true

    is RepeatRule.OnceOnDate -> d == rule.date

    is RepeatRule.EveryNDays -> {
        val diff = ChronoUnit.DAYS.between(rule.anchor, d)
        diff >= 0 && diff % rule.n == 0L
    }

    is RepeatRule.Weekdays -> d.dayOfWeek in rule.days

    is RepeatRule.EveryNWeeksOnDays -> {
        if (d.dayOfWeek !in rule.days) {
            false
        } else {
            val mondayOfD = d.minusDays((d.dayOfWeek.value - 1).toLong())
            val daysBetween = ChronoUnit.DAYS.between(rule.anchorWeekMonday, mondayOfD)
            daysBetween >= 0 && (daysBetween / 7) % rule.n == 0L
        }
    }

    is RepeatRule.DatesOfMonth -> d.dayOfMonth in rule.days

    is RepeatRule.NthWeekdayOfMonth -> {
        val target = if (rule.ordinal == RepeatRule.LAST) {
            d.with(TemporalAdjusters.lastInMonth(rule.day))
        } else {
            d.with(TemporalAdjusters.dayOfWeekInMonth(rule.ordinal, rule.day))
        }
        target == d
    }

    is RepeatRule.EveryNMonthsOnDate -> {
        if (d.dayOfMonth != rule.dayOfMonth) {
            false
        } else {
            val months = ChronoUnit.MONTHS.between(rule.anchor, YearMonth.from(d))
            months >= 0 && months % rule.n == 0L
        }
    }

    is RepeatRule.Yearly -> d.month == rule.month && d.dayOfMonth == rule.day

    is RepeatRule.DaysBeforeEndOfMonth -> {
        val target = d.lengthOfMonth() - rule.daysBefore
        target >= 1 && d.dayOfMonth == target
    }
}

/** ~200 years of days. Realistic rules resolve within days; this only bounds pathological input. */
const val HORIZON_DAYS: Int = 366 * 200
