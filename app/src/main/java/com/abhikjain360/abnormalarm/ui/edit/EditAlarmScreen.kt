package com.abhikjain360.abnormalarm.ui.edit

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.domain.model.RepeatRule
import com.abhikjain360.abnormalarm.ui.endSummary
import com.abhikjain360.abnormalarm.ui.repeatSummary
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmScreen(alarmId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: EditAlarmViewModel = viewModel(
        factory = EditAlarmViewModel.factory(container.alarmRepository, container.alarmScheduler),
    )
    LaunchedEffect(alarmId) { vm.load(alarmId) }
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()

    if (!loaded) return

    // 24-hour clock dial (no AM/PM): Material 3 renders the inner 13–23 ring automatically.
    val timeState = rememberTimePickerState(
        initialHour = draft.time.hour,
        initialMinute = draft.time.minute,
        is24Hour = true,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (alarmId == 0L) "New alarm" else "Edit alarm") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel") }
                },
                actions = {
                    IconButton(onClick = {
                        vm.setTime(LocalTime.of(timeState.hour, timeState.minute))
                        vm.save { onClose() }
                    }) { Icon(Icons.Default.Check, "Save") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The clock-dial picker IS the create flow — everything else is opt-in (DESIGN.md §8).
            TimePicker(state = timeState)
            Spacer(Modifier.height(8.dp))
            AdvancedSection(vm, draft)
        }
    }
}

@Composable
private fun AdvancedSection(vm: EditAlarmViewModel, draft: com.abhikjain360.abnormalarm.domain.model.Alarm) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Advanced",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = vm::setLabel,
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                var showRepeat by remember { mutableStateOf(false) }
                SettingRow("Repeat", repeatSummary(draft.repeat)) { showRepeat = true }
                if (showRepeat) {
                    RepeatPickerDialog(
                        current = draft.repeat,
                        onPick = { vm.setRepeat(it); showRepeat = false },
                        onDismiss = { showRepeat = false },
                    )
                }

                val repeats = draft.repeat != RepeatRule.Once && draft.repeat !is RepeatRule.OnceOnDate
                if (repeats) {
                    var showEnd by remember { mutableStateOf(false) }
                    SettingRow("Ends", endSummary(draft.end)) { showEnd = true }
                    if (showEnd) {
                        EndPickerDialog(
                            current = draft.end,
                            onPick = { vm.setEnd(it); showEnd = false },
                            onDismiss = { showEnd = false },
                        )
                    }
                }

                RingtoneRow(draft.ring.soundUri, onPicked = { uri -> vm.setRing { it.copy(soundUri = uri) } })

                ToggleRow("Vibrate", draft.ring.vibrate) { on -> vm.setRing { it.copy(vibrate = on) } }
                ToggleRow("Flashlight", draft.ring.flashlight) { on -> vm.setRing { it.copy(flashlight = on) } }
                ToggleRow("Volume ramp (~30s)", draft.ring.volumeRampSeconds > 0) { on ->
                    vm.setRing { it.copy(volumeRampSeconds = if (on) 30 else 0) }
                }

                ToggleRow("Snooze", draft.ring.snoozeEnabled) { on ->
                    vm.setRing { it.copy(snoozeEnabled = on) }
                }
                if (draft.ring.snoozeEnabled) {
                    StepperRow("Snooze minutes", draft.ring.snoozeMinutes, 1, 60) { v ->
                        vm.setRing { it.copy(snoozeMinutes = v) }
                    }
                }
                StepperRow("Auto-silence minutes", draft.ring.autoSilenceMinutes, 1, 60) { v ->
                    vm.setRing { it.copy(autoSilenceMinutes = v) }
                }
            }
        }
    }
}

@Composable
private fun RingtoneRow(soundUri: String?, onPicked: (String?) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            onPicked(uri?.toString())
        }
    }
    val label = if (soundUri == null) "Default alarm sound" else "Custom sound"
    SettingRow("Sound", label) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm sound")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                soundUri?.let(Uri::parse),
            )
        }
        launcher.launch(intent)
    }
}

@Composable
internal fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StepperRow(title: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > min) onChange(value - 1) }) { Text("–") }
        Text("$value", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { if (value < max) onChange(value + 1) }) { Text("+") }
    }
}
