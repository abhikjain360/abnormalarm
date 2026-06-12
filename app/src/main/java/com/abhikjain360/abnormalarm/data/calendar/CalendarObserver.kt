package com.abhikjain360.abnormalarm.data.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

/**
 * Registers a [ContentObserver] on the calendar provider so a sync runs the instant the Google
 * sync adapter changes anything (DESIGN.md §5). Lives as long as the process does; the periodic
 * worker + boot receiver cover the times the process isn't running.
 */
class CalendarObserver(private val context: Context) {

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            CalendarSyncWorker.syncNow(context)
        }
    }
    private var registered = false

    /**
     * Register the observer — but only if [READ_CALENDAR] is granted (registering without it throws
     * a [SecurityException]). Safe to call repeatedly: a no-op once registered, and idempotent so it
     * can be re-invoked after the permission is granted. Guarded so it can never crash app startup.
     */
    fun register() {
        if (registered) return
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            registered = true
        }
    }

    fun unregister() {
        if (!registered) return
        context.contentResolver.unregisterContentObserver(observer)
        registered = false
    }
}
