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
        DailyUsage::class,
        PendingGroupChange::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(LimitTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appGroupDao(): AppGroupDao

    abstract fun timeExtensionDao(): TimeExtensionDao

    abstract fun pendingGroupChangeDao(): PendingGroupChangeDao

    abstract fun dailyUsageDao(): DailyUsageDao

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

        // Sprint 3: new columns for the pending changes (US-15, US-13)
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

        // US-09: postponed group deletion and app removal
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_group_changes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        groupId INTEGER NOT NULL,
                        changeType TEXT NOT NULL,
                        packageName TEXT,
                        executeAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS
                    index_pending_group_changes_groupId_packageName
                    ON pending_group_changes (groupId, packageName)
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
