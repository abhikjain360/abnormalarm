package com.abhikjain360.abnormalarm.ui.edit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abhikjain360.abnormalarm.domain.model.RepeatEnd
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import com.abhikjain360.abnormalarm.ui.repeatSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle

private enum class Mode(val label: String) {
    ONCE("Once"),
    EVERY_N_DAYS("Every N days"),
    WEEKDAYS("Days of week"),
    EVERY_N_WEEKS("Every N weeks on days"),
    DATES_OF_MONTH("Dates of month"),
    NTH_WEEKDAY("Nth weekday of month"),
    EVERY_N_MONTHS("Every N months on a date"),
    YEARLY("Yearly"),
    DAYS_BEFORE_EOM("Days before month-end"),
}

/** Only [Mode.ONCE] needs no further configuration; every other mode drills into a detail screen. */
private val Mode.hasConfig: Boolean get() = this != Mode.ONCE

private fun modeOf(rule: RepeatRule): Mode = when (rule) {
    RepeatRule.Once, is RepeatRule.OnceOnDate -> Mode.ONCE
    is RepeatRule.EveryNDays -> Mode.EVERY_N_DAYS
    is RepeatRule.Weekdays -> Mode.WEEKDAYS
    is RepeatRule.EveryNWeeksOnDays -> Mode.EVERY_N_WEEKS
    is RepeatRule.DatesOfMonth -> Mode.DATES_OF_MONTH
    is RepeatRule.NthWeekdayOfMonth -> Mode.NTH_WEEKDAY
    is RepeatRule.EveryNMonthsOnDate -> Mode.EVERY_N_MONTHS
    is RepeatRule.Yearly -> Mode.YEARLY
    is RepeatRule.DaysBeforeEndOfMonth -> Mode.DAYS_BEFORE_EOM
}

/**
 * Two-step repeat picker. The first step is a list of modes; tapping a configurable mode drills
 * into a dedicated detail step where that mode's options get the full dialog width (no cramped
 * inline controls). [Mode.ONCE] needs no options, so it is committed straight from the list.
 */
@Composable
fun RepeatPickerDialog(
    current: RepeatRule,
    onPick: (RepeatRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(modeOf(current)) }
    var inDetail by remember { mutableStateOf(false) }

    // Parameter state, seeded from the current rule where it matches.
    var nDays by remember { mutableIntStateOf((current as? RepeatRule.EveryNDays)?.n ?: 1) }
    var nWeeks by remember { mutableIntStateOf((current as? RepeatRule.EveryNWeeksOnDays)?.n ?: 2) }
    var nMonths by remember { mutableIntStateOf((current as? RepeatRule.EveryNMonthsOnDate)?.n ?: 2) }
    val selectedDays = remember {
        mutableStateMapOf<DayOfWeek, Boolean>().apply {
            val seed = (current as? RepeatRule.Weekdays)?.days
                ?: (current as? RepeatRule.EveryNWeeksOnDays)?.days ?: emptySet()
            DayOfWeek.entries.forEach { put(it, it in seed) }
        }
    }
    val selectedDates = remember {
        mutableStateMapOf<Int, Boolean>().apply {
            val seed = (current as? RepeatRule.DatesOfMonth)?.days ?: setOf(1)
            (1..31).forEach { put(it, it in seed) }
        }
    }
    var ordinal by remember { mutableIntStateOf((current as? RepeatRule.NthWeekdayOfMonth)?.ordinal ?: 1) }
    var nthDay by remember { mutableStateOf((current as? RepeatRule.NthWeekdayOfMonth)?.day ?: DayOfWeek.MONDAY) }
    var dayOfMonth by remember { mutableIntStateOf((current as? RepeatRule.EveryNMonthsOnDate)?.dayOfMonth ?: 1) }
    var yMonth by remember { mutableStateOf((current as? RepeatRule.Yearly)?.month ?: Month.JANUARY) }
    var yDay by remember { mutableIntStateOf((current as? RepeatRule.Yearly)?.day ?: 1) }
    var daysBefore by remember { mutableIntStateOf((current as? RepeatRule.DaysBeforeEndOfMonth)?.daysBefore ?: 0) }
    val maxYearlyDay = yMonth.length(true)
    LaunchedEffect(yMonth) {
        if (yDay > maxYearlyDay) yDay = maxYearlyDay
    }

    fun build(): RepeatRule = when (mode) {
        Mode.ONCE -> RepeatRule.Once
        Mode.EVERY_N_DAYS -> RepeatRule.EveryNDays(nDays.coerceAtLeast(1), LocalDate.now())
        Mode.WEEKDAYS -> RepeatRule.Weekdays(selectedDays.filterValues { it }.keys.ifEmpty { setOf(DayOfWeek.MONDAY) })
        Mode.EVERY_N_WEEKS -> RepeatRule.EveryNWeeksOnDays(
            nWeeks.coerceAtLeast(1),
            selectedDays.filterValues { it }.keys.ifEmpty { setOf(DayOfWeek.MONDAY) },
            LocalDate.now().with(DayOfWeek.MONDAY),
        )
        Mode.DATES_OF_MONTH -> RepeatRule.DatesOfMonth(selectedDates.filterValues { it }.keys.ifEmpty { setOf(1) })
        Mode.NTH_WEEKDAY -> RepeatRule.NthWeekdayOfMonth(ordinal, nthDay)
        Mode.EVERY_N_MONTHS -> RepeatRule.EveryNMonthsOnDate(nMonths.coerceAtLeast(1), dayOfMonth, YearMonth.now())
        Mode.YEARLY -> RepeatRule.Yearly(yMonth, yDay.coerceIn(1, maxYearlyDay))
        Mode.DAYS_BEFORE_EOM -> RepeatRule.DaysBeforeEndOfMonth(daysBefore)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            // Only the detail step commits; the list step either drills in or commits "Once" on tap.
            if (inDetail) TextButton(onClick = { onPick(build()) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = {
            if (inDetail) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { inDetail = false }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to list")
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(mode.label)
                }
            } else {
                Text("Repeat")
            }
        },
        text = {
            AnimatedContent(
                targetState = inDetail,
                transitionSpec = {
                    val dir = if (targetState) 1 else -1   // detail slides in from the right, list from the left
                    (slideInHorizontally { dir * it / 4 } + fadeIn()) togetherWith
                        (slideOutHorizontally { -dir * it / 4 } + fadeOut())
                },
                label = "repeat-step",
            ) { detail ->
                if (detail) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 460.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        PreviewChip(repeatSummary(build()))
                        when (mode) {
                            Mode.EVERY_N_DAYS ->
                                EveryStepper(nDays, "day", "days", max = 99) { nDays = it }

                            Mode.EVERY_N_WEEKS -> {
                                EveryStepper(nWeeks, "week", "weeks", max = 52) { nWeeks = it }
                                Field("Repeat on") { WeekdayChips(selectedDays) }
                            }

                            Mode.WEEKDAYS -> Field("Repeat on") { WeekdayChips(selectedDays) }

                            Mode.DATES_OF_MONTH -> Field("Days of the month") { MonthDateGrid(selectedDates) }

                            Mode.NTH_WEEKDAY -> {
                                Field("Which occurrence") { OrdinalChips(ordinal) { ordinal = it } }
                                Field("Weekday") { SingleWeekdayChips(nthDay) { nthDay = it } }
                            }

                            Mode.EVERY_N_MONTHS -> {
                                EveryStepper(nMonths, "month", "months", max = 24) { nMonths = it }
                                LabeledStepper("On day", dayOfMonth, 1, 31) { dayOfMonth = it }
                            }

                            Mode.YEARLY -> {
                                Field("Month") { MonthChips(yMonth) { yMonth = it } }
                                LabeledStepper("Day", yDay, 1, maxYearlyDay) { yDay = it }
                            }

                            Mode.DAYS_BEFORE_EOM -> {
                                LabeledStepper("Days before end", daysBefore, 0, 27) { daysBefore = it }
                                Text(
                                    "0 = the last day of the month",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Mode.ONCE -> {}
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Mode.entries.forEach { m ->
                            ModeRow(
                                label = m.label,
                                selected = m == mode,
                                subtitle = if (m == mode && m.hasConfig) repeatSummary(build()) else null,
                                showChevron = m.hasConfig,
                                onClick = {
                                    if (m.hasConfig) {
                                        mode = m
                                        inDetail = true
                                    } else {
                                        mode = m
                                        onPick(RepeatRule.Once)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ModeRow(
    label: String,
    selected: Boolean,
    subtitle: String?,
    showChevron: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (selected && subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A labelled group: a small caption above its control(s). */
@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun PreviewChip(summary: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** "Every  [−] N [+]  unit" — the prominent interval control for the "every N" modes. */
@Composable
private fun EveryStepper(value: Int, singular: String, plural: String, max: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Every", style = MaterialTheme.typography.bodyLarge)
        PillStepper(value, 1, max, onChange)
        Text(if (value == 1) singular else plural, style = MaterialTheme.typography.bodyLarge)
    }
}

/** "label  [−] N [+]" — a stepper with a leading label, value to its right. */
@Composable
private fun LabeledStepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        PillStepper(value, min, max, onChange)
    }
}

/** Compact [−] value [+] stepper with rounded tonal buttons that disable at the bounds. */
@Composable
private fun PillStepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { if (value > min) onChange(value - 1) },
            enabled = value > min,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) { Icon(Icons.Default.Remove, contentDescription = "Decrease") }
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 32.dp).padding(horizontal = 6.dp),
        )
        FilledTonalIconButton(
            onClick = { if (value < max) onChange(value + 1) },
            enabled = value < max,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(),
        ) { Icon(Icons.Default.Add, contentDescription = "Increase") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayChips(selected: MutableMap<DayOfWeek, Boolean>) {
    val locale = LocalLocale.current.platformLocale
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = selected[d] == true,
                onClick = { selected[d] = !(selected[d] ?: false) },
                label = { Text(d.getDisplayName(TextStyle.SHORT, locale)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SingleWeekdayChips(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = selected == d,
                onClick = { onSelect(d) },
                label = { Text(d.getDisplayName(TextStyle.SHORT, locale)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrdinalChips(ordinal: Int, onSelect: (Int) -> Unit) {
    val ordinals = listOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", RepeatRule.LAST to "Last")
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ordinals.forEach { (v, lbl) ->
            FilterChip(selected = ordinal == v, onClick = { onSelect(v) }, label = { Text(lbl) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthChips(selected: Month, onSelect: (Month) -> Unit) {
    val locale = LocalLocale.current.platformLocale
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Month.entries.forEach { m ->
            FilterChip(
                selected = selected == m,
                onClick = { onSelect(m) },
                label = { Text(m.getDisplayName(TextStyle.SHORT, locale)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDateGrid(selected: MutableMap<Int, Boolean>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        (1..31).forEach { d ->
            FilterChip(
                selected = selected[d] == true,
                onClick = { selected[d] = !(selected[d] ?: false) },
                label = {
                    Box(modifier = Modifier.widthIn(min = 16.dp), contentAlignment = Alignment.Center) {
                        Text("$d")
                    }
                },
            )
        }
    }
}

@Composable
fun EndPickerDialog(
    current: RepeatEnd,
    onPick: (RepeatEnd) -> Unit,
    onDismiss: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val initialEndDate = remember(current) { (current as? RepeatEnd.OnDate)?.date ?: today.plusMonths(1) }
    var afterCount by remember { mutableIntStateOf((current as? RepeatEnd.AfterCount)?.count ?: 10) }
    var endYear by remember { mutableIntStateOf(initialEndDate.year) }
    var endMonth by remember { mutableIntStateOf(initialEndDate.monthValue) }
    var endDay by remember { mutableIntStateOf(initialEndDate.dayOfMonth) }
    var selection by remember {
        mutableIntStateOf(
            when (current) {
                is RepeatEnd.AfterCount -> 1
                is RepeatEnd.OnDate -> 2
                RepeatEnd.Never -> 0
            },
        )
    }
    val minEndYear = minOf(today.year, initialEndDate.year)
    val maxEndYear = maxOf(today.year + 50, initialEndDate.year)
    val maxEndDay = YearMonth.of(endYear, endMonth).lengthOfMonth()
    LaunchedEffect(maxEndDay) {
        if (endDay > maxEndDay) endDay = maxEndDay
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(
                    when (selection) {
                        1 -> RepeatEnd.AfterCount(afterCount)
                        2 -> RepeatEnd.OnDate(LocalDate.of(endYear, endMonth, endDay.coerceIn(1, maxEndDay)))
                        else -> RepeatEnd.Never
                    },
                )
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Ends") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                EndOption("Never", selected = selection == 0) { selection = 0 }
                EndOption("After count", selected = selection == 1) { selection = 1 }
                if (selection == 1) {
                    Column(modifier = Modifier.padding(start = 48.dp, bottom = 8.dp)) {
                        LabeledStepper("Count", afterCount, 1, 999) { afterCount = it }
                    }
                }
                EndOption("On date", selected = selection == 2) { selection = 2 }
                if (selection == 2) {
                    Column(
                        modifier = Modifier.padding(start = 48.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        LabeledStepper("Year", endYear, minEndYear, maxEndYear) { endYear = it }
                        LabeledStepper("Month", endMonth, 1, 12) { endMonth = it }
                        LabeledStepper("Day", endDay, 1, maxEndDay) { endDay = it }
                    }
                }
            }
        },
    )
}

@Composable
private fun EndOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
