package com.abhikjain360.abnormalarm.reliability

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Device-aware background-reliability helpers (DESIGN.md §12). Stock-Android-first, with OEM
 * deep-links (notably MIUI/HyperOS autostart) gated on [Build.MANUFACTURER] and every OEM intent
 * try/catch-guarded so a button never dead-ends — it falls back to this app's App-Info screen.
 *
 * Neutral framing only ("so your alarms ring on time"); never names or blames the manufacturer.
 */
object Reliability {

    /** Battery-optimization exemption IS queryable, so this step is shown only when not yet granted. */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * True if the OEM is one we know ships an aggressive autostart manager (MIUI/HyperOS family).
     *
     * This only detects that a manual autostart screen probably exists. Android/OEMs do not expose
     * a stable public API for reading whether this app's autostart toggle is currently enabled.
     */
    fun hasOemAutostartManager(): Boolean {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "poco", "redmi", "oppo", "vivo", "oneplus", "realme").any { it in m }
    }

    /** Opens the system "ignore battery optimizations" dialog; falls back to App-Info. */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        @Suppress("BatteryLife")
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!tryStart(context, intent)) openAppInfo(context)
    }

    /** Deep-links to the OEM autostart screen when present; otherwise App-Info. */
    fun openAutostartSettings(context: Context) {
        for (component in OEM_AUTOSTART_COMPONENTS) {
            val intent = Intent().apply {
                this.component = component
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (tryStart(context, intent)) return
        }
        openAppInfo(context)
    }

    fun openAppInfo(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tryStart(context, intent)
    }

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: Exception) {
        false
    }

    // Component names vary across HyperOS/MIUI versions — we try each in turn (DESIGN.md §12).
    private val OEM_AUTOSTART_COMPONENTS = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
    )
}
