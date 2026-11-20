package com.example.stopscrolling.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AppGroup::class,
        GroupApp::class,
        TimeExtension::class,
        DailyUsage::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(LimitTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appGroupDao(): AppGroupDao

    abstract fun timeExtensionDao(): TimeExtensionDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_groups ADD COLUMN timeLimit INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE app_groups ADD COLUMN limitType TEXT NOT NULL DEFAULT 'HARD'"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS time_extensions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        groupId INTEGER NOT NULL,
                        dayStartMillis INTEGER NOT NULL,
                        grantedMinutes INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        // Sprint 3: new columns for the pending changes (US-15, US-13, US-09)
        // and the new table for the daily totals (US-27).
        // Old rows are kept: every new column has a default value or can be NULL.
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // US-15: pending limit change (NULL = no pending change)
                db.execSQL("ALTER TABLE app_groups ADD COLUMN pendingTimeLimit INTEGER")
                db.execSQL("ALTER TABLE app_groups ADD COLUMN pendingLimitType TEXT")
                db.execSQL("ALTER TABLE app_groups ADD COLUMN pendingFrom INTEGER")

                // US-13: weekly limit (0 = no weekly limit) and its pending change
                db.execSQL(
                    "ALTER TABLE app_groups ADD COLUMN weeklyLimit INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE app_groups ADD COLUMN pendingWeeklyLimit INTEGER")

                // US-09: postponed deletion of a group or removal of an app (0 = false)
                db.execSQL(
                    "ALTER TABLE app_groups ADD COLUMN pendingDeletion INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE group_apps ADD COLUMN pendingRemoval INTEGER NOT NULL DEFAULT 0"
                )

                // US-27: total usage of each finished day
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_usage (
                        dayStartMillis INTEGER NOT NULL,
                        totalMillis INTEGER NOT NULL,
                        PRIMARY KEY(dayStartMillis)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stopscrolling.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}