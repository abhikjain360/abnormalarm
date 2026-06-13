package com.abhikjain360.abnormalarm.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class HomeClockWidgetTest {
    private val zone = ZoneId.of("UTC")

    @Test fun formatsTodayAndTomorrowNextAlarmText() {
        val now = ZonedDateTime.of(2026, 6, 12, 18, 54, 0, 0, zone)
        assertEquals(
            "Today 19:00",
            HomeClockWidget.formatNextAlarm(
                ZonedDateTime.of(2026, 6, 12, 19, 0, 0, 0, zone),
                now,
            ),
        )
        assertEquals(
            "Tomorrow 07:30",
            HomeClockWidget.formatNextAlarm(
                ZonedDateTime.of(2026, 6, 13, 7, 30, 0, 0, zone),
                now,
            ),
        )
    }

    @Test fun defaultZoneLabelUsesLastPathSegment() {
        assertEquals("Los Angeles", defaultZoneLabel("America/Los_Angeles"))
        assertEquals("Delhi", defaultZoneLabel("Asia/Kolkata"))
    }

    @Test fun secondaryZoneLabelAddsDayOnlyWhenDateDiffers() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals(
                "Tokyo · Sat",
                HomeClockWidget.secondaryZoneLabel(
                    zoneId = "Asia/Tokyo",
                    label = "Tokyo",
                    referenceInstant = Instant.parse("2026-06-12T23:30:00Z"),
                    localZone = ZoneId.of("UTC"),
                ),
            )
            assertEquals(
                "London",
                HomeClockWidget.secondaryZoneLabel(
                    zoneId = "Europe/London",
                    label = "",
                    referenceInstant = Instant.parse("2026-06-12T12:00:00Z"),
                    localZone = ZoneId.of("UTC"),
                ),
            )
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
