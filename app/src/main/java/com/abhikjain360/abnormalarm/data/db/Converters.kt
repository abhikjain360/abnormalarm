package com.abhikjain360.abnormalarm.data.db

import androidx.room.TypeConverter
import com.abhikjain360.abnormalarm.domain.model.AlarmSource
import com.abhikjain360.abnormalarm.domain.model.RepeatEnd
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import com.abhikjain360.abnormalarm.domain.model.TimerState
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth

/**
 * Room type converters for the domain types that aren't primitives. Encoding is a compact,
 * pipe-delimited tagged string — deterministic and dependency-free (no kotlinx-serialization).
 * The domain stays Android-free (DESIGN.md §3); all (de)serialization lives here in `data/`.
 *
 * Schema note: changing any encoding here is a DB migration. Keep tags stable.
 */
class Converters {

    // --- LocalTime: seconds-of-day ---
    @TypeConverter fun timeToInt(t: LocalTime): Int = t.toSecondOfDay()
    @TypeConverter fun intToTime(s: Int): LocalTime = LocalTime.ofSecondOfDay(s.toLong())

    // --- AlarmSource ---
    @TypeConverter fun sourceToString(s: AlarmSource): String = s.name
    @TypeConverter fun stringToSource(s: String): AlarmSource = AlarmSource.valueOf(s)

    // --- TimerState ---
    @TypeConverter fun timerStateToString(s: TimerState): String = s.name
    @TypeConverter fun stringToTimerState(s: String): TimerState = TimerState.valueOf(s)

    // --- RepeatRule ---
    @TypeConverter
    fun ruleToString(rule: RepeatRule): String = when (rule) {
        RepeatRule.Once -> "once"
        is RepeatRule.OnceOnDate -> "onceOnDate|${rule.date.toEpochDay()}"
        is RepeatRule.EveryNDays -> "everyNDays|${rule.n}|${rule.anchor.toEpochDay()}"
        is RepeatRule.Weekdays -> "weekdays|${daysToCsv(rule.days)}"
        is RepeatRule.EveryNWeeksOnDays ->
            "everyNWeeks|${rule.n}|${daysToCsv(rule.days)}|${rule.anchorWeekMonday.toEpochDay()}"
        is RepeatRule.DatesOfMonth -> "datesOfMonth|${rule.days.sorted().joinToString(",")}"
        is RepeatRule.NthWeekdayOfMonth -> "nthWeekday|${rule.ordinal}|${rule.day.value}"
        is RepeatRule.EveryNMonthsOnDate ->
            "everyNMonths|${rule.n}|${rule.dayOfMonth}|${rule.anchor.year}|${rule.anchor.monthValue}"
        is RepeatRule.Yearly -> "yearly|${rule.month.value}|${rule.day}"
        is RepeatRule.DaysBeforeEndOfMonth -> "daysBeforeEom|${rule.daysBefore}"
    }

    @TypeConverter
    fun stringToRule(s: String): RepeatRule {
        val p = s.split("|")
        return when (p[0]) {
            "once" -> RepeatRule.Once
            "onceOnDate" -> RepeatRule.OnceOnDate(LocalDate.ofEpochDay(p[1].toLong()))
            "everyNDays" -> RepeatRule.EveryNDays(p[1].toInt(), LocalDate.ofEpochDay(p[2].toLong()))
            "weekdays" -> RepeatRule.Weekdays(csvToDays(p.getOrElse(1) { "" }))
            "everyNWeeks" -> RepeatRule.EveryNWeeksOnDays(
                p[1].toInt(), csvToDays(p[2]), LocalDate.ofEpochDay(p[3].toLong()),
            )
            "datesOfMonth" -> RepeatRule.DatesOfMonth(csvToInts(p.getOrElse(1) { "" }))
            "nthWeekday" -> RepeatRule.NthWeekdayOfMonth(p[1].toInt(), DayOfWeek.of(p[2].toInt()))
            "everyNMonths" -> RepeatRule.EveryNMonthsOnDate(
                p[1].toInt(), p[2].toInt(), YearMonth.of(p[3].toInt(), p[4].toInt()),
            )
            "yearly" -> RepeatRule.Yearly(Month.of(p[1].toInt()), p[2].toInt())
            "daysBeforeEom" -> RepeatRule.DaysBeforeEndOfMonth(p[1].toInt())
            else -> error("Unknown RepeatRule encoding: $s")
        }
    }

    // --- RepeatEnd ---
    @TypeConverter
    fun endToString(end: RepeatEnd): String = when (end) {
        RepeatEnd.Never -> "never"
        is RepeatEnd.OnDate -> "onDate|${end.date.toEpochDay()}"
        is RepeatEnd.AfterCount -> "afterCount|${end.count}"
    }

    @TypeConverter
    fun stringToEnd(s: String): RepeatEnd {
        val p = s.split("|")
        return when (p[0]) {
            "never" -> RepeatEnd.Never
            "onDate" -> RepeatEnd.OnDate(LocalDate.ofEpochDay(p[1].toLong()))
            "afterCount" -> RepeatEnd.AfterCount(p[1].toInt())
            else -> error("Unknown RepeatEnd encoding: $s")
        }
    }

    private fun daysToCsv(days: Set<DayOfWeek>): String =
        days.map { it.value }.sorted().joinToString(",")

    private fun csvToDays(csv: String): Set<DayOfWeek> =
        if (csv.isBlank()) emptySet() else csv.split(",").map { DayOfWeek.of(it.toInt()) }.toSet()

    private fun csvToInts(csv: String): Set<Int> =
        if (csv.isBlank()) emptySet() else csv.split(",").map { it.toInt() }.toSet()
}
