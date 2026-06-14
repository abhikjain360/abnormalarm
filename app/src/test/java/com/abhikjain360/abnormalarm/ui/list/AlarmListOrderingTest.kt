package com.abhikjain360.abnormalarm.ui.list

import com.abhikjain360.abnormalarm.domain.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmListOrderingTest {
    private val utc = ZoneId.of("UTC")

    @Test fun ordersByActualNextFireInsteadOfTimeOfDay() {
        val tomorrowAt1400 = alarm(id = 1L, hour = 14)
        val todayAt1700 = alarm(id = 2L, hour = 17)

        val rows = alarmRowsSortedByNextFire(listOf(tomorrowAt1400, todayAt1700)) { alarm ->
            when (alarm.id) {
                tomorrowAt1400.id -> at(2026, 6, 13, 14)
                todayAt1700.id -> at(2026, 6, 12, 17)
                else -> error("Unexpected alarm id ${alarm.id}")
            }
        }

        assertEquals(listOf(todayAt1700.id, tomorrowAt1400.id), rows.map { it.alarm.id })
    }

    @Test fun disabledAlarmsSortAfterScheduledAlarms() {
        val disabledSooner = alarm(id = 1L, hour = 9, enabled = false)
        val enabledLater = alarm(id = 2L, hour = 10)

        val rows = alarmRowsSortedByNextFire(listOf(disabledSooner, enabledLater)) { alarm ->
            when (alarm.id) {
                disabledSooner.id -> at(2026, 6, 12, 9)
                enabledLater.id -> at(2026, 6, 12, 10)
                else -> error("Unexpected alarm id ${alarm.id}")
            }
        }

        assertEquals(listOf(enabledLater.id, disabledSooner.id), rows.map { it.alarm.id })
        assertNull(rows.last().nextFire)
    }

    private fun alarm(id: Long, hour: Int, enabled: Boolean = true): Alarm =
        Alarm(
            id = id,
            time = LocalTime.of(hour, 0),
            enabled = enabled,
        )

    private fun at(year: Int, month: Int, day: Int, hour: Int): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, 0, 0, 0, utc)
}
