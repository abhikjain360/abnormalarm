package com.abhikjain360.abnormalarm.ui.timer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.domain.model.Timer
import com.abhikjain360.abnormalarm.domain.model.TimerState
import com.abhikjain360.abnormalarm.domain.timer.TimerCountdownDisplay
import com.abhikjain360.abnormalarm.domain.timer.TimerDurationInput
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerListScreen(
    onAlarms: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: TimerListViewModel = viewModel(
        factory = TimerListViewModel.factory(
            container.timerRepository,
            container.timerScheduler,
            context.applicationContext,
        ),
    )
    val timers by vm.timers.collectAsStateWithLifecycle()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(timers) {
        while (true) {
            val now = System.currentTimeMillis()
            nowMillis = now
            val nextDelay = timers.nextDisplayDelayMillis(now)
            if (nextDelay == null) awaitCancellation()
            delay(nextDelay)
        }
    }

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
                PrimaryTabRow(selectedTabIndex = 1) {
                    Tab(selected = false, onClick = onAlarms, text = { Text("Alarms") })
                    Tab(selected = true, onClick = {}, text = { Text("Timers") })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "New timer")
            }
        },
    ) { padding ->
        if (timers.isEmpty()) {
            EmptyTimerState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(timers, key = { it.id }) { timer ->
                    TimerCard(
                        timer = timer,
                        nowMillis = nowMillis,
                        onStart = { vm.start(timer.id) },
                        onPause = { vm.pause(timer.id) },
                        onReset = { vm.reset(timer.id) },
                        onDelete = { vm.delete(timer.id) },
                        onEdit = { onEdit(timer.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimerCard(
    timer: Timer,
    nowMillis: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val canEdit = timer.state == TimerState.IDLE || timer.state == TimerState.PAUSED
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canEdit, onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timerDisplay(timer, nowMillis),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (timer.label.isNotBlank()) {
                    Text(
                        text = timer.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stateText(timer.state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (timer.state) {
                TimerState.IDLE -> {
                    IconButton(onClick = onStart) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start timer")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit timer")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete timer")
                    }
                }
                TimerState.RUNNING -> {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause timer")
                    }
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop timer")
                    }
                }
                TimerState.PAUSED -> {
                    IconButton(onClick = onStart) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume timer")
                    }
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.Stop, contentDescription = "Reset timer")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit timer")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete timer")
                    }
                }
                TimerState.RINGING -> {
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.Stop, contentDescription = "Dismiss timer")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTimerState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "No timers yet",
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

private fun timerDisplay(timer: Timer, nowMillis: Long): String {
    val remaining = when (timer.state) {
        TimerState.RUNNING -> {
            val displayNow = maxOf(nowMillis, System.currentTimeMillis())
            ((timer.endAtMillis ?: displayNow) - displayNow)
                .coerceIn(0L, timer.durationMillis)
        }
        TimerState.PAUSED -> timer.remainingMillis ?: timer.durationMillis
        TimerState.RINGING -> 0L
        TimerState.IDLE -> timer.durationMillis
    }
    return TimerDurationInput.formatSeconds(TimerCountdownDisplay.displaySeconds(remaining))
}

private fun stateText(state: TimerState): String = when (state) {
    TimerState.IDLE -> "Saved"
    TimerState.RUNNING -> "Running"
    TimerState.PAUSED -> "Paused"
    TimerState.RINGING -> "Finished"
}

private fun List<Timer>.nextDisplayDelayMillis(nowMillis: Long): Long? =
    asSequence()
        .filter { it.state == TimerState.RUNNING && it.endAtMillis != null }
        .mapNotNull { timer ->
            val remaining = ((timer.endAtMillis ?: nowMillis) - nowMillis)
                .coerceIn(0L, timer.durationMillis)
            TimerCountdownDisplay.delayUntilNextDisplayChangeMillis(remaining)
        }
        .minOrNull()
