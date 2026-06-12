package com.abhikjain360.abnormalarm.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

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
}
