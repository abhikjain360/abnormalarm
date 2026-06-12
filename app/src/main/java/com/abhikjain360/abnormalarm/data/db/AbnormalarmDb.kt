package com.abhikjain360.abnormalarm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [AlarmEntity::class, TimerEntity::class], version = 3, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AbnormalarmDb : RoomDatabase() {

    abstract fun alarmDao(): AlarmDao
    abstract fun timerDao(): TimerDao

    companion object {
        @Volatile private var instance: AbnormalarmDb? = null

        fun get(context: Context): AbnormalarmDb =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AbnormalarmDb::class.java,
                    "abnormalarm.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN calendarProvider TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN calendarId TEXT")
                db.execSQL("ALTER TABLE alarms ADD COLUMN calendarEventKey TEXT")
                db.execSQL(
                    "UPDATE alarms SET " +
                        "calendarProvider = 'device', " +
                        "calendarEventKey = CAST(calendarEventId AS TEXT) " +
                        "WHERE source = 'CALENDAR' AND calendarEventId IS NOT NULL",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_alarms_calendarProvider_calendarId_" +
                        "calendarEventKey_calendarInstanceStartMillis ON alarms " +
                        "(calendarProvider, calendarId, calendarEventKey, calendarInstanceStartMillis)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS timers (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "label TEXT NOT NULL, " +
                        "durationMillis INTEGER NOT NULL, " +
                        "state TEXT NOT NULL, " +
                        "endAtMillis INTEGER, " +
                        "remainingMillis INTEGER, " +
                        "ring_soundUri TEXT, " +
                        "ring_volumeRampSeconds INTEGER NOT NULL, " +
                        "ring_vibrate INTEGER NOT NULL, " +
                        "ring_flashlight INTEGER NOT NULL, " +
                        "ring_snoozeEnabled INTEGER NOT NULL, " +
                        "ring_snoozeMinutes INTEGER NOT NULL, " +
                        "ring_autoSilenceMinutes INTEGER NOT NULL" +
                        ")",
                )
            }
        }
    }
}
