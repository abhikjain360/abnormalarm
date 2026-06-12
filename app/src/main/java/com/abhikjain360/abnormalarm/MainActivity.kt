package com.abhikjain360.abnormalarm

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.abhikjain360.abnormalarm.data.calendar.CalendarSyncWorker
import com.abhikjain360.abnormalarm.ui.edit.EditAlarmScreen
import com.abhikjain360.abnormalarm.ui.list.AlarmListScreen
import com.abhikjain360.abnormalarm.ui.reliability.ReliabilityOnboarding
import com.abhikjain360.abnormalarm.ui.settings.SettingsScreen
import com.abhikjain360.abnormalarm.ui.theme.AbnormalarmTheme
import com.abhikjain360.abnormalarm.ui.timer.TimerEditScreen
import com.abhikjain360.abnormalarm.ui.timer.TimerListScreen

/** Single-activity Compose host (DESIGN.md §3/§8): alarm list, create/edit, and settings. */
class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()
        setContent {
            AbnormalarmTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppNav()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Foregrounding the app is a calendar refresh trigger (DESIGN.md §5). This matters for
        // Google API calendars because Android does not notify us when remote events change.
        CalendarSyncWorker.syncNow(this)
    }

    private fun ensureNotificationPermission() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "list") {
        composable("list") {
            AlarmListScreen(
                onCreate = { nav.navigate("edit/0") },
                onEdit = { id -> nav.navigate("edit/$id") },
                onTimers = { nav.navigate("timers") },
                onSettings = { nav.navigate("settings") },
            )
            // One-time, device-aware reliability sheet (DESIGN.md §12) — shown over the list once.
            ReliabilityOnboarding()
        }
        composable(
            route = "edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            EditAlarmScreen(alarmId = id, onClose = { nav.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable("timers") {
            TimerListScreen(
                onAlarms = { nav.navigate("list") { popUpTo("list") { inclusive = false } } },
                onCreate = { nav.navigate("timerEdit/0") },
                onEdit = { id -> nav.navigate("timerEdit/$id") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable(
            route = "timerEdit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            TimerEditScreen(timerId = id, onClose = { nav.popBackStack() })
        }
    }
}
