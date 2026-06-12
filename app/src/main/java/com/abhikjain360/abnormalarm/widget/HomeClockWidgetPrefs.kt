package com.abhikjain360.abnormalarm.widget

import android.content.Context
import androidx.core.content.edit
import java.time.ZoneId

data class HomeClockWidgetConfig(
    val zoneOneId: String? = null,
    val zoneOneLabel: String = "",
    val zoneTwoId: String? = null,
    val zoneTwoLabel: String = "",
)

object HomeClockWidgetPrefs {
    private const val NAME = "home_clock_widgets"
    private const val KEY_ZONE_ONE_ID = "zone_one_id"
    private const val KEY_ZONE_ONE_LABEL = "zone_one_label"
    private const val KEY_ZONE_TWO_ID = "zone_two_id"
    private const val KEY_ZONE_TWO_LABEL = "zone_two_label"

    fun load(context: Context, appWidgetId: Int): HomeClockWidgetConfig {
        val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return HomeClockWidgetConfig(
            zoneOneId = prefs.getString(key(appWidgetId, KEY_ZONE_ONE_ID), null),
            zoneOneLabel = prefs.getString(key(appWidgetId, KEY_ZONE_ONE_LABEL), "").orEmpty(),
            zoneTwoId = prefs.getString(key(appWidgetId, KEY_ZONE_TWO_ID), null),
            zoneTwoLabel = prefs.getString(key(appWidgetId, KEY_ZONE_TWO_LABEL), "").orEmpty(),
        )
    }

    fun save(context: Context, appWidgetId: Int, config: HomeClockWidgetConfig) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            putNullableString(key(appWidgetId, KEY_ZONE_ONE_ID), config.zoneOneId)
            putString(key(appWidgetId, KEY_ZONE_ONE_LABEL), config.zoneOneLabel)
            putNullableString(key(appWidgetId, KEY_ZONE_TWO_ID), config.zoneTwoId)
            putString(key(appWidgetId, KEY_ZONE_TWO_LABEL), config.zoneTwoLabel)
        }
    }

    fun delete(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit {
            remove(key(appWidgetId, KEY_ZONE_ONE_ID))
            remove(key(appWidgetId, KEY_ZONE_ONE_LABEL))
            remove(key(appWidgetId, KEY_ZONE_TWO_ID))
            remove(key(appWidgetId, KEY_ZONE_TWO_LABEL))
        }
    }

    private fun key(appWidgetId: Int, name: String): String = "$appWidgetId.$name"

    private fun android.content.SharedPreferences.Editor.putNullableString(
        key: String,
        value: String?,
    ): android.content.SharedPreferences.Editor =
        if (value == null) remove(key) else putString(key, value)
}

private val zoneLabelOverrides = mapOf(
    "Asia/Kolkata" to "Delhi",
)

fun defaultZoneLabel(zoneId: String): String {
    zoneLabelOverrides[zoneId]?.let { return it }
    val raw = zoneId.substringAfterLast('/').replace('_', ' ')
    return raw.ifBlank { ZoneId.of(zoneId).id }
}
