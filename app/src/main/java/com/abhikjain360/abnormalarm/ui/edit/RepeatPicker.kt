package com.abhikjain360.abnormalarm.ui.edit

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abhikjain360.abnormalarm.domain.model.RepeatEnd
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatPickerDialog(
    current: RepeatRule,
    onPick: (RepeatRule) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(modeOf(current)) }

    // Parameter state, seeded from the current rule where it matches.
    var n by remember { mutableStateOf((current as? RepeatRule.EveryNDays)?.n
        ?: (current as? RepeatRule.EveryNWeeksOnDays)?.n
        ?: (current as? RepeatRule.EveryNMonthsOnDate)?.n ?: 1) }
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
    var ordinal by remember { mutableStateOf((current as? RepeatRule.NthWeekdayOfMonth)?.ordinal ?: 1) }
    var nthDay by remember { mutableStateOf((current as? RepeatRule.NthWeekdayOfMonth)?.day ?: DayOfWeek.MONDAY) }
    var dayOfMonth by remember { mutableStateOf((current as? RepeatRule.EveryNMonthsOnDate)?.dayOfMonth ?: 1) }
    var yMonth by remember { mutableStateOf((current as? RepeatRule.Yearly)?.month ?: Month.JANUARY) }
    var yDay by remember { mutableStateOf((current as? RepeatRule.Yearly)?.day ?: 1) }
    var daysBefore by remember { mutableStateOf((current as? RepeatRule.DaysBeforeEndOfMonth)?.daysBefore ?: 0) }

    fun build(): RepeatRule = when (mode) {
        Mode.ONCE -> RepeatRule.Once
        Mode.EVERY_N_DAYS -> RepeatRule.EveryNDays(n.coerceAtLeast(1), LocalDate.now())
        Mode.WEEKDAYS -> RepeatRule.Weekdays(selectedDays.filterValues { it }.keys.ifEmpty { setOf(DayOfWeek.MONDAY) })
        Mode.EVERY_N_WEEKS -> RepeatRule.EveryNWeeksOnDays(
            n.coerceAtLeast(1),
            selectedDays.filterValues { it }.keys.ifEmpty { setOf(DayOfWeek.MONDAY) },
            LocalDate.now().with(DayOfWeek.MONDAY),
        )
        Mode.DATES_OF_MONTH -> RepeatRule.DatesOfMonth(selectedDates.filterValues { it }.keys.ifEmpty { setOf(1) })
        Mode.NTH_WEEKDAY -> RepeatRule.NthWeekdayOfMonth(ordinal, nthDay)
        Mode.EVERY_N_MONTHS -> RepeatRule.EveryNMonthsOnDate(n.coerceAtLeast(1), dayOfMonth, YearMonth.now())
        Mode.YEARLY -> RepeatRule.Yearly(yMonth, yDay)
        Mode.DAYS_BEFORE_EOM -> RepeatRule.DaysBeforeEndOfMonth(daysBefore)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(build()) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Repeat") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Mode.entries.forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == m, onClick = { mode = m })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Text(m.label)
                    }
                }

                when (mode) {
                    Mode.EVERY_N_DAYS, Mode.EVERY_N_WEEKS, Mode.EVERY_N_MONTHS ->
                        Stepper("N = ", n, 1, 52) { n = it }
                    else -> {}
                }
                if (mode == Mode.WEEKDAYS || mode == Mode.EVERY_N_WEEKS) {
                    DayChips(selectedDays)
                }
                if (mode == Mode.DATES_OF_MONTH) {
                    FlowRow(modifier = Modifier.fillMaxWidth()) {
                        (1..31).forEach { d ->
                            FilterChip(
                                selected = selectedDates[d] == true,
                                onClick = { selectedDates[d] = !(selectedDates[d] ?: false) },
                                label = { Text("$d") },
                                modifier = Modifier.padding(2.dp),
                            )
                        }
                    }
                }
                if (mode == Mode.NTH_WEEKDAY) {
                    val ordinals = listOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", RepeatRule.LAST to "Last")
                    FlowRow {
                        ordinals.forEach { (v, lbl) ->
                            FilterChip(ordinal == v, { ordinal = v }, { Text(lbl) }, Modifier.padding(2.dp))
                        }
                    }
                    DaySingleChips(nthDay) { nthDay = it }
                }
                if (mode == Mode.EVERY_N_MONTHS) {
                    Stepper("Day of month ", dayOfMonth, 1, 31) { dayOfMonth = it }
                }
                if (mode == Mode.YEARLY) {
                    FlowRow {
                        Month.entries.forEach { m ->
                            FilterChip(
                                yMonth == m, { yMonth = m },
                                { Text(m.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
                                Modifier.padding(2.dp),
                            )
                        }
                    }
                    Stepper("Day ", yDay, 1, 31) { yDay = it }
                }
                if (mode == Mode.DAYS_BEFORE_EOM) {
                    Stepper("Days before end (0 = last day) ", daysBefore, 0, 27) { daysBefore = it }
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayChips(selected: MutableMap<DayOfWeek, Boolean>) {
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = selected[d] == true,
                onClick = { selected[d] = !(selected[d] ?: false) },
                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DaySingleChips(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DayOfWeek.entries.forEach { d ->
            FilterChip(
                selected = selected == d,
                onClick = { onSelect(d) },
                label = { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) },
            )
        }
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = { if (value > min) onChange(value - 1) }) { Text("–") }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { if (value < max) onChange(value + 1) }) { Text("+") }
    }
}

@Composable
fun EndPickerDialog(
    current: RepeatEnd,
    onPick: (RepeatEnd) -> Unit,
    onDismiss: () -> Unit,
) {
    var afterCount by remember { mutableStateOf((current as? RepeatEnd.AfterCount)?.count ?: 10) }
    var selection by remember { mutableStateOf(if (current is RepeatEnd.AfterCount) 1 else 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onPick(if (selection == 1) RepeatEnd.AfterCount(afterCount) else RepeatEnd.Never)
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Ends") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(selection == 0) { selection = 0 },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selection == 0, onClick = { selection = 0 })
                    Text("Never")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().selectable(selection == 1) { selection = 1 },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selection == 1, onClick = { selection = 1 })
                    Text("After count")
                }
                if (selection == 1) Stepper("Count ", afterCount, 1, 999) { afterCount = it }
            }
        },
    )
}
