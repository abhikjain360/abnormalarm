package com.abhikjain360.abnormalarm.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.ui.formatTime
import com.abhikjain360.abnormalarm.ui.nextFireText
import com.abhikjain360.abnormalarm.ui.repeatSummary
import androidx.compose.ui.platform.LocalContext
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmListScreen(
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onTimers: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: AlarmListViewModel = viewModel(
        factory = AlarmListViewModel.factory(container.alarmRepository, container.alarmScheduler),
    )
    val rows by vm.rows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Abnormalarm") },
                    actions = {
                        IconButton(onClick = onSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
                PrimaryTabRow(selectedTabIndex = 0) {
                    Tab(selected = true, onClick = {}, text = { Text("Alarms") })
                    Tab(selected = false, onClick = onTimers, text = { Text("Timers") })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "New alarm")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.alarm.id }) { row ->
                    AlarmCard(
                        row = row,
                        onToggle = { vm.setEnabled(row.alarm.id, it) },
                        onSkip = { vm.skipNext(row.alarm.id) },
                        onClick = { if (!row.isCalendar) onEdit(row.alarm.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    row: AlarmRow,
    onToggle: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row.isCalendar) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "From calendar",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                    Text(
                        text = row.alarm.time.formatTime(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (row.alarm.label.isNotBlank()) {
                    Text(
                        text = row.alarm.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = if (row.isCalendar) {
                        nextFireText(row.nextFire, ZonedDateTime.now())
                    } else {
                        "${repeatSummary(row.alarm.repeat)} · ${nextFireText(row.nextFire, ZonedDateTime.now())}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.alarm.enabled) {
                IconButton(onClick = onSkip) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Skip next")
                }
            }
            Switch(checked = row.alarm.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No alarms yet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "Tap + to add one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
