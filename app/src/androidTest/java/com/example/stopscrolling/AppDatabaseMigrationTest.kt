package com.example.stopscrolling

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.stopscrolling.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun migrate1To5_preservesExistingData() {
        val context = InstrumentationRegistry
            .getInstrumentation().targetContext

        val databaseName = "migration-test-1-5.db"

        context.deleteDatabase(databaseName)

        val oldDb = context.openOrCreateDatabase(
            databaseName,
            Context.MODE_PRIVATE,
            null
        )

        try {
            oldDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL
                )
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS group_apps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    groupId INTEGER NOT NULL,
                    packageName TEXT NOT NULL
                )
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO app_groups (id, name)
                VALUES (1, 'Social Media')
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO group_apps (id, groupId, packageName)
                VALUES (1, 1, 'com.instagram.android')
                """.trimIndent()
            )

            oldDb.version = 1
        } finally {
            oldDb.close()
        }

        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()

        try {
            assertEquals(5, db.openHelper.readableDatabase.version)

            // Check existing AppGroup
            db.openHelper.readableDatabase.query(
                """
                SELECT id, name, timeLimit, limitType
                FROM app_groups
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals("Social Media", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals("HARD", cursor.getString(3))

                assertFalse(cursor.moveToNext())
            }

            // Check existing GroupApp
            db.openHelper.readableDatabase.query(
                """
                SELECT id, groupId, packageName
                FROM group_apps
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(
                    "com.instagram.android",
                    cursor.getString(2)
                )

                assertFalse(cursor.moveToNext())
            }

            // Check the new pending changes table
            db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM pending_group_changes"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }

        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrate3To5_preservesExistingData() {
        val context = InstrumentationRegistry
            .getInstrumentation().targetContext

        val databaseName = "migration-test-3-5.db"

        context.deleteDatabase(databaseName)

        val oldDb = context.openOrCreateDatabase(
            databaseName,
            Context.MODE_PRIVATE,
            null
        )

        try {
            oldDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    timeLimit INTEGER NOT NULL DEFAULT 0,
                    limitType TEXT NOT NULL DEFAULT 'HARD'
                )
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS group_apps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    groupId INTEGER NOT NULL,
                    packageName TEXT NOT NULL
                )
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS time_extensions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    groupId INTEGER NOT NULL,
                    dayStartMillis INTEGER NOT NULL,
                    grantedMinutes INTEGER NOT NULL
                )
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO app_groups (
                    id, name, timeLimit, limitType
                )
                VALUES (1, 'Social Media', 60, 'SOFT')
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO group_apps (
                    id, groupId, packageName
                )
                VALUES (1, 1, 'com.instagram.android')
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO time_extensions (
                    id, groupId, dayStartMillis, grantedMinutes
                )
                VALUES (1, 1, 1700000000000, 15)
                """.trimIndent()
            )

            oldDb.version = 3
        } finally {
            oldDb.close()
        }

        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        )
            .addMigrations(
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()

        try {
            assertEquals(5, db.openHelper.readableDatabase.version)

            // Check existing group and new columns
            db.openHelper.readableDatabase.query(
                """
                SELECT id, name, timeLimit, limitType,
                       weeklyLimit,
                       pendingTimeLimit, pendingLimitType,
                       pendingWeeklyLimit, pendingFrom
                FROM app_groups
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals("Social Media", cursor.getString(1))
                assertEquals(60, cursor.getInt(2))
                assertEquals("SOFT", cursor.getString(3))

                assertEquals(0, cursor.getInt(4))

                assertTrue(cursor.isNull(5))
                assertTrue(cursor.isNull(6))
                assertTrue(cursor.isNull(7))
                assertTrue(cursor.isNull(8))

                assertFalse(cursor.moveToNext())
            }

            // Check existing GroupApp
            db.openHelper.readableDatabase.query(
                """
                SELECT id, groupId, packageName
                FROM group_apps
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(
                    "com.instagram.android",
                    cursor.getString(2)
                )

                assertFalse(cursor.moveToNext())
            }

            // Check existing time extension
            db.openHelper.readableDatabase.query(
                """
                SELECT id, groupId, dayStartMillis, grantedMinutes
                FROM time_extensions
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1700000000000L, cursor.getLong(2))
                assertEquals(15, cursor.getInt(3))

                assertFalse(cursor.moveToNext())
            }

            // Check daily_usage table
            db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM daily_usage"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }

            db.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO daily_usage (
                    dayStartMillis, totalMillis
                )
                VALUES (1700000000000, 5400000)
                """.trimIndent()
            )

            db.openHelper.readableDatabase.query(
                "SELECT dayStartMillis, totalMillis FROM daily_usage"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1700000000000L, cursor.getLong(0))
                assertEquals(5400000L, cursor.getLong(1))
                assertFalse(cursor.moveToNext())
            }

            // Check pending_group_changes table
            db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM pending_group_changes"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }

        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrate4To5_preservesExistingData() {
        val context = InstrumentationRegistry
            .getInstrumentation().targetContext

        val databaseName = "migration-test-4-5.db"

        context.deleteDatabase(databaseName)

        val oldDb = context.openOrCreateDatabase(
            databaseName,
            Context.MODE_PRIVATE,
            null
        )

        try {
            // Create Version 4 schema
            oldDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_groups (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    timeLimit INTEGER NOT NULL DEFAULT 0,
                    limitType TEXT NOT NULL DEFAULT 'HARD',
                    pendingTimeLimit INTEGER,
                    pendingLimitType TEXT,
                    pendingFrom INTEGER,
                    weeklyLimit INTEGER NOT NULL DEFAULT 0,
                    pendingWeeklyLimit INTEGER
                )
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS group_apps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    groupId INTEGER NOT NULL,
                    packageName TEXT NOT NULL
                )
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS time_extensions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    groupId INTEGER NOT NULL,
                    dayStartMillis INTEGER NOT NULL,
                    grantedMinutes INTEGER NOT NULL
                )
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_usage (
                    dayStartMillis INTEGER NOT NULL,
                    totalMillis INTEGER NOT NULL,
                    PRIMARY KEY(dayStartMillis)
                )
                """.trimIndent()
            )

            // Insert existing group with pending limit changes
            oldDb.execSQL(
                """
                INSERT INTO app_groups (
                    id,
                    name,
                    timeLimit,
                    limitType,
                    pendingTimeLimit,
                    pendingLimitType,
                    pendingFrom,
                    weeklyLimit,
                    pendingWeeklyLimit
                )
                VALUES (
                    1,
                    'Social Media',
                    60,
                    'HARD',
                    90,
                    'SOFT',
                    1700000000000,
                    300,
                    400
                )
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO group_apps (
                    id, groupId, packageName
                )
                VALUES (1, 1, 'com.instagram.android')
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO time_extensions (
                    id, groupId, dayStartMillis, grantedMinutes
                )
                VALUES (1, 1, 1700000000000, 15)
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO daily_usage (
                    dayStartMillis, totalMillis
                )
                VALUES (1700000000000, 5400000)
                """.trimIndent()
            )

            oldDb.version = 4

        } finally {
            oldDb.close()
        }

        // Apply only Migration 4 to 5
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        )
            .addMigrations(
                AppDatabase.MIGRATION_4_5
            )
            .build()

        try {
            assertEquals(5, db.openHelper.readableDatabase.version)

            // Verify existing group and pending limits
            db.openHelper.readableDatabase.query(
                """
                SELECT id, name, timeLimit, limitType,
                       pendingTimeLimit, pendingLimitType,
                       pendingFrom, weeklyLimit,
                       pendingWeeklyLimit
                FROM app_groups
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals("Social Media", cursor.getString(1))
                assertEquals(60, cursor.getInt(2))
                assertEquals("HARD", cursor.getString(3))
                assertEquals(90, cursor.getInt(4))
                assertEquals("SOFT", cursor.getString(5))
                assertEquals(1700000000000L, cursor.getLong(6))
                assertEquals(300, cursor.getInt(7))
                assertEquals(400, cursor.getInt(8))

                assertFalse(cursor.moveToNext())
            }

            // Verify existing group apps
            db.openHelper.readableDatabase.query(
                """
                SELECT id, groupId, packageName
                FROM group_apps
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(
                    "com.instagram.android",
                    cursor.getString(2)
                )

                assertFalse(cursor.moveToNext())
            }

            // Verify existing time extensions
            db.openHelper.readableDatabase.query(
                """
                SELECT id, groupId, dayStartMillis, grantedMinutes
                FROM time_extensions
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals(1700000000000L, cursor.getLong(2))
                assertEquals(15, cursor.getInt(3))

                assertFalse(cursor.moveToNext())
            }

            // Verify existing daily usage
            db.openHelper.readableDatabase.query(
                "SELECT dayStartMillis, totalMillis FROM daily_usage"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1700000000000L, cursor.getLong(0))
                assertEquals(5400000L, cursor.getLong(1))

                assertFalse(cursor.moveToNext())
            }

            // Verify the new table exists and is empty
            db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM pending_group_changes"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }

            // Insert a pending app removal
            db.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO pending_group_changes (
                    groupId,
                    changeType,
                    packageName,
                    executeAtMillis
                )
                VALUES (
                    1,
                    'REMOVE_APP',
                    'com.instagram.android',
                    1700000000000
                )
                """.trimIndent()
            )

            // Verify the new table can store pending changes
            db.openHelper.readableDatabase.query(
                """
                SELECT groupId,
                       changeType,
                       packageName,
                       executeAtMillis
                FROM pending_group_changes
                """.trimIndent()
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals("REMOVE_APP", cursor.getString(1))
                assertEquals(
                    "com.instagram.android",
                    cursor.getString(2)
                )
                assertEquals(1700000000000L, cursor.getLong(3))

                assertFalse(cursor.moveToNext())
            }

        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }
}