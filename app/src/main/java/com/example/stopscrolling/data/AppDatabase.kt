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
        PendingGroupChange::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(LimitTypeConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appGroupDao(): AppGroupDao

    abstract fun timeExtensionDao(): TimeExtensionDao

    abstract fun pendingGroupChangeDao(): PendingGroupChangeDao

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

        val MIGRATION_3_4 = object : Migration(3, 4) {
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

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stopscrolling.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4
                    )
                    .build()
                    .also { instance = it }
            }
        }
    }
}
