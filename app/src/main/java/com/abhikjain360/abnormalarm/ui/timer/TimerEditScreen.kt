package com.abhikjain360.abnormalarm.ui.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.domain.timer.TimerDurationInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerEditScreen(timerId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: TimerEditViewModel = viewModel(
        factory = TimerEditViewModel.factory(
            container.timerRepository,
            container.timerScheduler,
            context.applicationContext,
        ),
    )
    LaunchedEffect(timerId) { vm.load(timerId) }
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val digits by vm.digits.collectAsStateWithLifecycle()
    val label by vm.label.collectAsStateWithLifecycle()
    if (!loaded) return

    val durationSeconds = TimerDurationInput.secondsFromDigits(digits)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (timerId == 0L) "New timer" else "Edit timer") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cancel") }
                },
                actions = {
                    IconButton(
                        enabled = durationSeconds > 0L,
                        onClick = { vm.save { onClose() } },
                    ) { Icon(Icons.Default.Check, "Save") }
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
            Text(
                text = TimerDurationInput.formatSeconds(durationSeconds),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            TimerNumpad(
                onToken = vm::append,
                onBackspace = vm::backspace,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = label,
                onValueChange = vm::setLabel,
                label = { Text("Label") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun TimerNumpad(onToken: (String) -> Unit, onBackspace: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { token ->
                    NumpadButton(label = token, modifier = Modifier.weight(1f)) { onToken(token) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            NumpadButton(label = "00", modifier = Modifier.weight(1f)) { onToken("00") }
            NumpadButton(label = "0", modifier = Modifier.weight(1f)) { onToken("0") }
            TextButton(
                onClick = onBackspace,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(1.65f),
            ) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace")
            }
        }
    }
}

@Composable
private fun NumpadButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.aspectRatio(1.65f),
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
    }
}
