package com.abhikjain360.abnormalarm.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.abhikjain360.abnormalarm.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-materializes all alarms after any event that clears the OS's registered alarms
 * (DESIGN.md §12): reboot, app update, and clock/timezone changes. One code path: rescheduleAll.
 *
 * On Xiaomi/HyperOS this only runs if the app hasn't been force-stopped and Autostart is allowed
 * — handled by the one-time reliability setup (DESIGN.md §12), not solvable in code.
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> {
                val pending = goAsync()
                val container = context.applicationContext.appContainer
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        container.alarmScheduler.rescheduleAll(container.alarmRepository)
                        container.timerScheduler.rescheduleAll(container.timerRepository)
                        // Roll the calendar horizon forward and reconcile after the registry was cleared.
                        com.abhikjain360.abnormalarm.data.calendar.CalendarSyncWorker.syncNow(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
