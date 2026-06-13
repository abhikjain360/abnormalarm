package com.abhikjain360.abnormalarm.reliability

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.app.ApplicationStartInfo
import android.content.Context
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Read-only background-kill diagnostics for the passive "Background reliability" section
 * (DESIGN.md §12). Uses [ActivityManager.getHistoricalProcessExitReasons] (API 30+) and
 * [ActivityManager.getHistoricalProcessStartReasons] (API 35+) for *this app's own package*,
 * so no permission is required.
 *
 * These APIs can't *prevent* OEM background-killing — they let us *detect and explain* it so the
 * owner knows when allowing autostart / removing battery limits is worth it. Framing here is neutral
 * and task-oriented ("so your alarms ring on time"); it never names or blames the manufacturer.
 *
 * Everything is defensively try/catch-guarded because some OEMs return empty or odd histories.
 */
object StartupDiagnostics {

    /** Number of recent exit records to inspect (the list is most-recent-first). */
    private const val HISTORY_DEPTH = 12

    private val WHEN_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")

    /** UI-friendly summary of a single process exit. */
    data class ExitSummary(
        val reasonLabel: String,        // friendly, e.g. "Stopped by system", "Closed normally"
        val whenText: String,           // e.g. "Jun 10, 07:14"
        val isBackgroundStop: Boolean,  // true = externally stopped while backgrounded (reliability-relevant)
    )

    /** Most recent process exit, or null if there is no history (or the read failed). */
    fun lastExit(context: Context): ExitSummary? = try {
        val am = context.getSystemService(ActivityManager::class.java)
        am.getHistoricalProcessExitReasons(context.packageName, 0, HISTORY_DEPTH)
            .firstOrNull()
            ?.let { it.toSummary() }
    } catch (_: Exception) {
        null
    }

    /**
     * How many of the recent exits were external/background stops (the reliability-relevant kind).
     * Returns 0 on any failure.
     */
    fun recentBackgroundStopCount(context: Context): Int = try {
        val am = context.getSystemService(ActivityManager::class.java)
        am.getHistoricalProcessExitReasons(context.packageName, 0, HISTORY_DEPTH)
            .count { isBackgroundStop(it.reason) }
    } catch (_: Exception) {
        0
    }

    /**
     * Friendly label for the most recent process start (e.g. "Alarm", "Background work"), or null
     * if there's no history / the read failed. Helps confirm the app is being woken for alarms.
     */
    fun lastStartReason(context: Context): String? = try {
        val am = context.getSystemService(ActivityManager::class.java)
        // getHistoricalProcessStartReasons is own-package only, so it takes just maxNum (no package
        // arg), and the reason accessor is getReason() (it.reason), returning a START_REASON_* value.
        am.getHistoricalProcessStartReasons(HISTORY_DEPTH)
            .firstOrNull()
            ?.let { startReasonLabel(it.reason) }
    } catch (_: Exception) {
        null
    }

    private fun ApplicationExitInfo.toSummary() = ExitSummary(
        reasonLabel = reasonLabel(reason),
        whenText = formatWhen(timestamp),
        isBackgroundStop = isBackgroundStop(reason),
    )

    /**
     * External/background stops that are reliability-relevant: the system (or a task manager) ended
     * the process while it was backgrounded. App faults (crash/ANR), self-exit, and config changes
     * are a different category and excluded.
     */
    private fun isBackgroundStop(reason: Int): Boolean = when (reason) {
        ApplicationExitInfo.REASON_USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED,
        ApplicationExitInfo.REASON_SIGNALED,
        ApplicationExitInfo.REASON_OTHER,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED,
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        -> true

        else -> false
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_USER_REQUESTED -> "Stopped (system or task manager)"
        ApplicationExitInfo.REASON_USER_STOPPED -> "Stopped (system or task manager)"
        ApplicationExitInfo.REASON_SIGNALED -> "Stopped by system"
        ApplicationExitInfo.REASON_OTHER -> "Stopped by system"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "Stopped by system"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "Stopped to free memory"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "Stopped to free memory"
        ApplicationExitInfo.REASON_EXIT_SELF -> "Closed normally"
        ApplicationExitInfo.REASON_CRASH -> "App crashed"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "App crashed"
        ApplicationExitInfo.REASON_ANR -> "App not responding"
        else -> "Stopped"
    }

    private fun startReasonLabel(startReason: Int): String = when (startReason) {
        ApplicationStartInfo.START_REASON_ALARM -> "Alarm"
        ApplicationStartInfo.START_REASON_BROADCAST -> "Broadcast"
        ApplicationStartInfo.START_REASON_LAUNCHER -> "App launch"
        ApplicationStartInfo.START_REASON_CONTENT_PROVIDER -> "Content provider"
        ApplicationStartInfo.START_REASON_JOB -> "Background work"
        ApplicationStartInfo.START_REASON_SERVICE -> "Service"
        ApplicationStartInfo.START_REASON_BACKUP -> "Backup"
        ApplicationStartInfo.START_REASON_BOOT_COMPLETE -> "Device restart"
        else -> "System"
    }

    private fun formatWhen(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(WHEN_FORMAT)
}
