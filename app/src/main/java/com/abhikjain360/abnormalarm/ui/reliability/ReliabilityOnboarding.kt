package com.abhikjain360.abnormalarm.ui.reliability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.reliability.Reliability
import kotlinx.coroutines.launch

/**
 * First-run, device-aware reliability sheet (DESIGN.md §12). Shown EXACTLY ONCE — a persisted flag
 * (`reliabilityPromptShown`) is set the first time we consider it, so it never auto-appears again
 * regardless of outcome. Only surfaces if something actionable is detected (battery limits and/or
 * an OEM autostart manager). The Settings status row is the ongoing, no-nagging path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityOnboarding() {
    val context = LocalContext.current
    val container = context.appContainer
    val scope = rememberCoroutineScope()

    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val settings = container.settingsRepository.current()
        if (settings.reliabilityPromptShown) return@LaunchedEffect
        val actionable = !Reliability.isIgnoringBatteryOptimizations(context) ||
            !Reliability.canDrawOverlays(context) ||
            Reliability.hasOemAutostartManager()
        // Mark shown regardless — we only ever consider it once (the §12 guarantee).
        container.settingsRepository.setReliabilityPromptShown(true)
        if (actionable) show = true
    }

    if (!show) return

    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = { show = false }, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Keep alarms on time", style = MaterialTheme.typography.headlineSmall)
            Text(
                "To make sure your alarms and timers ring exactly on time and open full-screen, let " +
                    "Abnormalarm start automatically, run without battery limits, and display over " +
                    "other apps.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!Reliability.isIgnoringBatteryOptimizations(context)) {
                Button(
                    onClick = { Reliability.requestIgnoreBatteryOptimizations(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Run without battery limits") }
            }
            if (!Reliability.canDrawOverlays(context)) {
                Button(
                    onClick = { Reliability.requestDrawOverlays(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Allow full-screen ring screen") }
            }
            if (Reliability.hasOemAutostartManager()) {
                OutlinedButton(
                    onClick = { Reliability.openAutostartSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open autostart settings") }
            }

            Text(
                "Autostart can't be enabled or checked by the app itself. If you already enabled " +
                    "it in system settings, mark it done under Settings → Background reliability.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { scope.launch { sheetState.hide(); show = false } },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Done") }
        }
    }
}
