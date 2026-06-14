package com.abhikjain360.abnormalarm.ring

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.ui.formatTime
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abhikjain360.abnormalarm.domain.timer.TimerDurationInput
import com.abhikjain360.abnormalarm.scheduling.AlarmIntents
import com.abhikjain360.abnormalarm.scheduling.DirectBoot
import com.abhikjain360.abnormalarm.scheduling.ScheduleMirror
import com.abhikjain360.abnormalarm.ui.theme.AbnormalarmTheme

/**
 * Full-screen alarm screen shown over the lock screen (DESIGN.md §6). Minimal for now —
 * dismiss/snooze wired to [RingService]. NEXT SESSION: show alarm label/time, a real snooze flow,
 * and polish per the design.
 */
class RingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen and wake the display (belt-and-suspenders with the manifest attrs).
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // Keep the screen on for as long as the ring screen is up so it can't doze back off mid-ring.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        render(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
    }

    private fun render(intent: Intent) {
        val kind = intent.getStringExtra(AlarmIntents.EXTRA_RING_KIND) ?: AlarmIntents.RING_KIND_ALARM
        val id = when (kind) {
            AlarmIntents.RING_KIND_TIMER -> intent.getLongExtra(AlarmIntents.EXTRA_TIMER_ID, -1L)
            else -> intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        }

        setContent {
            AbnormalarmTheme {
                RingScreen(
                    kind = kind,
                    id = id,
                    onDismiss = {
                        if (kind == AlarmIntents.RING_KIND_TIMER) {
                            RingService.dismissTimer(this, id)
                        } else {
                            RingService.dismiss(this)
                        }
                        finish()
                    },
                    onSnooze = {
                        RingService.snooze(this)
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun RingScreen(kind: String, id: Long, onDismiss: () -> Unit, onSnooze: () -> Unit) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("") }
    var primaryText by remember { mutableStateOf("") }
    var snoozeEnabled by remember { mutableStateOf(kind != AlarmIntents.RING_KIND_TIMER) }
    LaunchedEffect(kind, id) {
        if (id >= 0L) {
            if (kind == AlarmIntents.RING_KIND_TIMER) {
                val timer = if (DirectBoot.isUserUnlocked(context)) {
                    context.appContainer.timerRepository.get(id)
                } else {
                    ScheduleMirror.getTimer(context, id)
                }
                timer?.let {
                    label = timer.label.ifBlank { "Timer" }
                    primaryText = TimerDurationInput.formatSeconds(timer.durationMillis / 1000L)
                    snoozeEnabled = false
                }
            } else {
                val alarm = if (DirectBoot.isUserUnlocked(context)) {
                    context.appContainer.alarmRepository.get(id)
                } else {
                    ScheduleMirror.getAlarm(context, id)
                }
                alarm?.let {
                    label = alarm.label.ifBlank { "Alarm" }
                    primaryText = alarm.time.formatTime()
                    snoozeEnabled = alarm.ring.snoozeEnabled
                }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (primaryText.isNotEmpty()) {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = label.ifBlank { if (kind == AlarmIntents.RING_KIND_TIMER) "Timer" else "Alarm" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(48.dp))
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Dismiss") }
            if (snoozeEnabled) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) { Text("Snooze") }
            }
        }
    }
}
