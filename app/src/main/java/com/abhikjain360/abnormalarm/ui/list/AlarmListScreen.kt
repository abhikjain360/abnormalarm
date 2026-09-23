package com.abhikjain360.abnormalarm.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.ui.formatTime
import com.abhikjain360.abnormalarm.ui.nextFireText
import com.abhikjain360.abnormalarm.ui.repeatSummary
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import kotlin.math.abs

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
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, contentDescription = "New alarm")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.alarm.id }) { row ->
                    AlarmCard(
                        modifier = Modifier.animateItem(),
                        row = row,
                        onToggle = { vm.setEnabled(row.alarm.id, it) },
                        onSkip = {
                            // Pin the viewport: remember the first visible card other than the
                            // skipped one and, once the reorder lands, restore it to the same
                            // pixel offset — otherwise LazyColumn's key-based scroll retention
                            // follows the skipped alarm when it is the first visible item and
                            // the whole screen jumps. If the skipped card sat above the anchor,
                            // the computed offset is negative and clamps to the list start,
                            // i.e. the anchor smoothly becomes the new top card.
                            val visible = listState.layoutInfo.visibleItemsInfo
                            val anchor = visible.firstOrNull { it.key != row.alarm.id }
                            vm.skipNext(row.alarm.id)
                            if (anchor != null) {
                                val anchorScrollOffset = visible.first().offset +
                                    listState.firstVisibleItemScrollOffset - anchor.offset
                                val skippedId = row.alarm.id
                                val skippedRow = row
                                scope.launch {
                                    val landed = withTimeoutOrNull(2_000) {
                                        snapshotFlow { rows }.first { newRows ->
                                            newRows.firstOrNull { it.alarm.id == skippedId } != skippedRow
                                        }
                                    }
                                    if (landed != null) {
                                        val newIndex = rows.indexOfFirst { it.alarm.id == anchor.key }
                                        if (newIndex >= 0) {
                                            listState.scrollToItem(newIndex, anchorScrollOffset)
                                        }
                                    }
                                }
                            }
                        },
                        onDelete = {
                            val deleted = row.alarm
                            vm.delete(deleted.id)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Alarm deleted",
                                    actionLabel = "Undo",
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) vm.restore(deleted)
                            }
                        },
                        onClick = { if (!row.isCalendar) onEdit(row.alarm.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmCard(
    row: AlarmRow,
    onToggle: (Boolean) -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Swipe right = skip the next ring (only meaningful while enabled); swipe left = delete.
    // Calendar rows aren't deletable — CalendarSync would just recreate them on the next pass.
    val canSkip = row.alarm.enabled
    val canDelete = !row.isCalendar
    // confirmValueChange fires *mid-drag* when the swipe crosses the dismiss threshold, so
    // skipArmed only records intent. The skip itself must wait until the finger is up and the
    // card has snapped back to rest — otherwise the resulting list reorder moves the card
    // while the gesture is still in flight. Dragging back to the origin before releasing
    // disarms it (the user changed their mind).
    var skipArmed by remember { mutableStateOf(false) }
    var pointerDown by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                // Skip snaps back instead of dismissing (the alarm stays in the list).
                SwipeToDismissBoxValue.StartToEnd -> {
                    if (canSkip) skipArmed = true
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> canDelete
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    // Delete once the dismiss animation has fully settled (the row then leaves via the data
    // flow). Keying on currentValue instead would fire mid-drag: currentValue flips to
    // EndToStart as soon as the swipe crosses the threshold, removing the row under the finger.
    LaunchedEffect(dismissState.settledValue) {
        if (dismissState.settledValue == SwipeToDismissBoxValue.EndToStart) onDelete()
    }
    LaunchedEffect(skipArmed) {
        if (!skipArmed) return@LaunchedEffect
        // Wait for the card to return to rest, then fire only if the pointer is already up
        // (a committed swipe whose snap-back just finished). If the offset is back at ~0
        // while the finger is still down, the user dragged back — disarm without skipping.
        // (dismissState.progress is useless here: after the vetoed settle it reads 1f.)
        val stillDown = snapshotFlow { pointerDown to dismissState.requireOffset() }
            .first { (_, offset) -> abs(offset) < 2f }
            .first
        skipArmed = false
        if (!stillDown) onSkip()
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier
            .fillMaxWidth()
            // Initial-pass observer only (nothing is consumed): tracks whether the finger is
            // still down so the skip above can tell "released past the threshold" apart from
            // "dragged back to the origin". Placed before anchoredDraggable in the chain so it
            // sees the raw down/up events first.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pointerDown = true
                    // Watch the raw pressed state until every pointer is physically up.
                    // (waitForUpOrCancellation bails early here: the card's clickable consumes
                    // the down event, which it treats as a cancellation.)
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.all { !it.pressed }) break
                    }
                    pointerDown = false
                }
            },
        enableDismissFromStartToEnd = canSkip,
        enableDismissFromEndToStart = canDelete,
        backgroundContent = { SwipeBackground(dismissState.dismissDirection) },
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
                Switch(checked = row.alarm.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

/** The colored reveal shown behind a card while it's being swiped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue) {
    if (direction == SwipeToDismissBoxValue.Settled) {
        Box(Modifier.fillMaxSize())
        return
    }
    val isSkip = direction == SwipeToDismissBoxValue.StartToEnd
    val color = if (isSkip) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val onColor = if (isSkip) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CardDefaults.shape)
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = if (isSkip) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isSkip) {
                Icon(Icons.Default.SkipNext, contentDescription = null, tint = onColor)
                Text("Skip next", color = onColor, style = MaterialTheme.typography.labelLarge)
            } else {
                Text("Delete", color = onColor, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.Delete, contentDescription = null, tint = onColor)
            }
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
