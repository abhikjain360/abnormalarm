package com.abhikjain360.abnormalarm.scheduling

import android.content.Context
import android.os.UserManager

/** Small helpers for code paths that may run before credential-protected storage is unlocked. */
object DirectBoot {
    fun isUserUnlocked(context: Context): Boolean =
        context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
}
