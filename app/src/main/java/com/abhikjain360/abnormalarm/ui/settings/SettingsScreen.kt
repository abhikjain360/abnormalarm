package com.abhikjain360.abnormalarm.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.IntentSenderRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.data.calendar.GoogleCalendarApiAuth
import com.abhikjain360.abnormalarm.data.calendar.GoogleCalendarApiRepository
import com.abhikjain360.abnormalarm.notifications.Notifications
import com.abhikjain360.abnormalarm.reliability.Reliability
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val vm: SettingsViewModel = viewModel(
        factory = SettingsViewModel.factory(
            context.applicationContext,
            container.settingsRepository,
            container.calendarRepository,
            container.googleCalendarApiRepository,
        ),
    )
    val settings by vm.settings.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val googleCalendars by vm.googleCalendars.collectAsStateWithLifecycle()
    val googleCalendarStatus by vm.googleCalendarStatus.collectAsStateWithLifecycle()
    var hasDeviceCalendarPermission by remember { mutableStateOf(container.calendarRepository.hasPermission()) }

    val calendarPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasDeviceCalendarPermission = granted
        if (granted) {
            vm.setCalendarFeedEnabled(true)
            vm.refreshCalendars()
        }
    }

    val googleAuthorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            vm.reportGoogleCalendarStatus("Authorization cancelled")
            return@rememberLauncherForActivityResult
        }
        try {
            val authorizationResult = Identity.getAuthorizationClient(context)
                .getAuthorizationResultFromIntent(result.data)
            val token = authorizationResult.accessToken
            if (token == null) {
                vm.reportGoogleCalendarStatus("No access token returned")
            } else {
                vm.connectGoogleCalendar(token)
            }
        } catch (e: ApiException) {
            vm.reportGoogleCalendarStatus(e.localizedMessage ?: "Authorization failed")
        }
    }

    fun requestGoogleCalendarAccess() {
        vm.reportGoogleCalendarStatus("Opening Google")
        Identity.getAuthorizationClient(context)
            .authorize(GoogleCalendarApiAuth.authorizationRequest(selectAccount = true))
            .addOnSuccessListener { authorizationResult ->
                if (authorizationResult.hasResolution()) {
                    val pendingIntent = authorizationResult.pendingIntent
                    if (pendingIntent == null) {
                        vm.reportGoogleCalendarStatus("Authorization unavailable")
                    } else {
                        googleAuthorizationLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
                        )
                    }
                } else {
                    val token = authorizationResult.accessToken
                    if (token == null) {
                        vm.reportGoogleCalendarStatus("No access token returned")
                    } else {
                        vm.connectGoogleCalendar(token)
                    }
                }
            }
            .addOnFailureListener { e ->
                vm.reportGoogleCalendarStatus(e.localizedMessage ?: "Authorization failed")
            }
    }

    LaunchedEffect(settings.calendarFeedEnabled) {
        if (settings.calendarFeedEnabled) vm.refreshCalendars()
    }

    LaunchedEffect(settings.googleCalendarApiEnabled) {
        if (settings.googleCalendarApiEnabled) vm.refreshGoogleCalendarsSilently()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Calendar")
            ToggleRow("Calendar alarms", checked = settings.calendarFeedEnabled) { enable ->
                vm.setCalendarFeedEnabled(enable)
            }
            if (settings.calendarFeedEnabled) {
                SectionTitle("Google Calendar API")
                StatusRow(
                    "Google accounts",
                    googleCalendarStatus ?: if (settings.googleCalendarApiEnabled) "Checking" else "Disconnected",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { requestGoogleCalendarAccess() }) { Text("Add account") }
                    if (settings.googleCalendarApiEnabled) {
                        OutlinedButton(onClick = { vm.refreshGoogleCalendarsAndSync() }) { Text("Refresh") }
                    }
                }
                if (settings.connectedGoogleAccounts.isNotEmpty()) {
                    OutlinedButton(onClick = { vm.disconnectGoogleCalendar() }) { Text("Disconnect all") }
                }
                val calendarsByAccount = googleCalendars.groupBy { it.accountEmail }
                settings.connectedGoogleAccounts.sorted().forEach { account ->
                    HorizontalDivider()
                    StatusRow("Account", account)
                    OutlinedButton(onClick = { vm.disconnectGoogleAccount(account) }) {
                        Text("Disconnect")
                    }
                    val accountCalendars = calendarsByAccount[account].orEmpty()
                    if (accountCalendars.isEmpty()) {
                        StatusRow("Calendars", "Needs refresh or authorization")
                    } else {
                        accountCalendars.forEach { cal ->
                            val key = GoogleCalendarApiRepository.googleCalendarKey(cal.accountEmail, cal.id)
                            ToggleRow(
                                title = cal.displayName,
                                subtitle = cal.id,
                                checked = key !in settings.disabledGoogleCalendarIds,
                            ) { on -> vm.setGoogleCalendarEnabled(key, on) }
                        }
                    }
                }

                HorizontalDivider()
                SectionTitle("Device Calendars")
                if (!hasDeviceCalendarPermission) {
                    OutlinedButton(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) {
                        Text("Allow device calendars")
                    }
                } else {
                    calendars.forEach { cal ->
                        ToggleRow(
                            title = cal.displayName,
                            subtitle = cal.accountName,
                            checked = cal.id !in settings.disabledCalendarIds,
                        ) { on -> vm.setCalendarEnabled(cal.id, on) }
                    }
                }
            }

            HorizontalDivider()
            SectionTitle("Notifications")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Upcoming lead time", modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    vm.setLeadMinutes((settings.upcomingLeadMinutes - 15).coerceAtLeast(0))
                }) { Text("–") }
                Text("${settings.upcomingLeadMinutes} min")
                IconButton(onClick = {
                    vm.setLeadMinutes((settings.upcomingLeadMinutes + 15).coerceAtMost(720))
                }) { Text("+") }
            }

            HorizontalDivider()
            ReliabilitySection()
        }
    }
}

/** Passive "Background reliability" status row (DESIGN.md §12) — never pops a dialog by itself. */
@Composable
private fun ReliabilitySection() {
    val context = LocalContext.current
    SectionTitle("Background reliability")

    var batteryOk by remember { mutableStateOf(Reliability.isIgnoringBatteryOptimizations(context)) }
    val nextAlarm = remember {
        context.getSystemService(AlarmManager::class.java).nextAlarmClock != null
    }

    StatusRow("Runs without battery limits", if (batteryOk) "On" else "Restricted")
    if (!batteryOk) {
        Button(onClick = {
            Reliability.requestIgnoreBatteryOptimizations(context)
            batteryOk = Reliability.isIgnoringBatteryOptimizations(context)
        }) { Text("Allow") }
    }

    StatusRow("Next alarm registered", if (nextAlarm) "Yes" else "None")

    // Full-screen-intent gate (DESIGN.md §10/§13): if withheld, the ring screen degrades to a
    // heads-up notification, so offer a deep-link to re-enable it.
    val fsiOk = remember { Notifications.canUseFullScreenIntent(context) }
    StatusRow("Full-screen alarm screen", if (fsiOk) "Allowed" else "Limited")
    if (!fsiOk) {
        Button(onClick = {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure { Reliability.openAppInfo(context) }
        }) { Text("Allow full-screen") }
    }

    if (Reliability.hasOemAutostart()) {
        StatusRow("Autostart", "Open settings")
        Button(onClick = { Reliability.openAutostartSettings(context) }) { Text("Open autostart") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun StatusRow(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
