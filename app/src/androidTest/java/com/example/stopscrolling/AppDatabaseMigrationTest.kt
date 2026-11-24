
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
    fun migrate1To2_preservesExistingData() {
        val context = InstrumentationRegistry
            .getInstrumentation().targetContext

        val databaseName = "migration-test.db"

        context.deleteDatabase(databaseName)

        // Step 1: Create a Version 1 database
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

            // Insert existing Version 1 data
            oldDb.execSQL(
                """
                INSERT INTO app_groups (id, name)
                VALUES (1, 'Social Media')
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO group_apps (
                    id, groupId, packageName
                )
                VALUES (
                    1, 1, 'com.instagram.android'
                )
                """.trimIndent()
            )

            oldDb.version = 1
        } finally {
            oldDb.close()
        }

        // Step 2: Open database using Room.
        // Room needs every migration up to the latest version, not only 1 to 2.
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
            .build()

        try {
            // Step 3: Check existing AppGroup
            db.openHelper.readableDatabase.query(
                """
                SELECT id, name, timeLimit, limitType
                FROM app_groups
                """.trimIndent()
            ).use { cursor ->

                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals(
                    "Social Media",
                    cursor.getString(1)
                )
                assertEquals(0, cursor.getInt(2))
                assertEquals(
                    "HARD",
                    cursor.getString(3)
                )

                assertFalse(cursor.moveToNext())
            }

            // Step 4: Check existing GroupApp
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

        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrate3To4_preservesExistingData() {
        val context = InstrumentationRegistry
            .getInstrumentation().targetContext

        val databaseName = "migration-test-3-4.db"

        context.deleteDatabase(databaseName)

        // Step 1: Create a Version 3 database
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

            // Insert existing Version 3 data
            oldDb.execSQL(
                """
                INSERT INTO app_groups (id, name, timeLimit, limitType)
                VALUES (1, 'Social Media', 60, 'SOFT')
                """.trimIndent()
            )

            oldDb.execSQL(
                """
                INSERT INTO group_apps (id, groupId, packageName)
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

        // Step 2: Open database using Room Version 4.
        // The database is already at version 3, so only migration 3 to 4 runs.
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        )
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .build()

        try {
            // Step 3: The old group is still there and the new columns have their defaults
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

                // No weekly limit is set
                assertEquals(0, cursor.getInt(4))

                // No pending change
                assertTrue(cursor.isNull(5))
                assertTrue(cursor.isNull(6))
                assertTrue(cursor.isNull(7))
                assertTrue(cursor.isNull(8))

                assertFalse(cursor.moveToNext())
            }

            // Step 4: The old GroupApp is still there
            db.openHelper.readableDatabase.query(
                """
                SELECT id, groupId, packageName
                FROM group_apps
                """.trimIndent()
            ).use { cursor ->

                assertTrue(cursor.moveToFirst())

                assertEquals(1, cursor.getInt(0))
                assertEquals(1, cursor.getInt(1))
                assertEquals("com.instagram.android", cursor.getString(2))

                assertFalse(cursor.moveToNext())
            }

            // Step 5: The time extension of Sprint 2 is still there
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

            // Step 6: The new daily_usage table exists, it is empty, and we can save a row
            db.openHelper.readableDatabase.query(
                "SELECT COUNT(*) FROM daily_usage"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }

            db.openHelper.writableDatabase.execSQL(
                """
                INSERT INTO daily_usage (dayStartMillis, totalMillis)
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

        } finally {
            db.close()
            context.deleteDatabase(databaseName)
        }
    }
}
