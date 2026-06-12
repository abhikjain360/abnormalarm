package com.abhikjain360.abnormalarm.ui

import com.abhikjain360.abnormalarm.domain.model.RepeatEnd
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// 24-hour display throughout (no AM/PM), matching the 24-hour clock-dial picker.
private fun timeFormatter(): DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

fun LocalTime.formatTime(): String = format(timeFormatter())

/** Short human summary of a repeat rule, e.g. "Mon, Wed, Fri" or "Every 2 days". */
fun repeatSummary(rule: RepeatRule): String = when (rule) {
    RepeatRule.Once -> "Once"
    is RepeatRule.OnceOnDate -> rule.date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    is RepeatRule.EveryNDays -> if (rule.n == 1) "Daily" else "Every ${rule.n} days"
    is RepeatRule.Weekdays -> daysSummary(rule.days)
    is RepeatRule.EveryNWeeksOnDays -> "Every ${rule.n} wks · ${daysSummary(rule.days)}"
    is RepeatRule.DatesOfMonth -> "Monthly on " + rule.days.sorted().joinToString(", ")
    is RepeatRule.NthWeekdayOfMonth -> {
        val which = if (rule.ordinal == RepeatRule.LAST) "last" else ordinal(rule.ordinal)
        "Monthly · $which ${rule.day.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
    }
    is RepeatRule.EveryNMonthsOnDate ->
        (if (rule.n == 1) "Monthly" else "Every ${rule.n} months") + " on ${rule.dayOfMonth}"
    is RepeatRule.Yearly ->
        "Yearly · ${rule.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${rule.day}"
    is RepeatRule.DaysBeforeEndOfMonth ->
        if (rule.daysBefore == 0) "Last day of month" else "${rule.daysBefore}d before month-end"
}

fun endSummary(end: RepeatEnd): String = when (end) {
    RepeatEnd.Never -> "Never ends"
    is RepeatEnd.OnDate -> "Until ${end.date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
    is RepeatEnd.AfterCount -> "Stops after ${end.count}"
}

/** Friendly countdown/label for the next fire, e.g. "Tomorrow, 7:00 AM" or "in 35 min". */
fun nextFireText(next: ZonedDateTime?, now: ZonedDateTime): String {
    if (next == null) return "Not scheduled"
    val until = Duration.between(now, next)
    val timePart = next.toLocalTime().formatTime()
    return when {
        until.toMinutes() < 60 -> "in ${until.toMinutes().coerceAtLeast(0)} min"
        next.toLocalDate() == now.toLocalDate() -> "Today, $timePart"
        next.toLocalDate() == now.toLocalDate().plusDays(1) -> "Tomorrow, $timePart"
        until.toDays() < 7 ->
            "${next.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}, $timePart"
        else -> "${next.format(DateTimeFormatter.ofPattern("MMM d"))}, $timePart"
    }
}

private fun daysSummary(days: Set<java.time.DayOfWeek>): String {
    if (days.size == 7) return "Every day"
    val weekdays = setOf(
        java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.WEDNESDAY,
        java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.FRIDAY,
    )
    if (days == weekdays) return "Weekdays"
    if (days == setOf(java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY)) return "Weekends"
    return days.sortedBy { it.value }
        .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

private fun ordinal(n: Int): String = when (n) {
    1 -> "1st"; 2 -> "2nd"; 3 -> "3rd"; else -> "${n}th"
}
