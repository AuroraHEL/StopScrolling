
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

        // Step 2: Open database using Room Version 2
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            databaseName
        )
            .addMigrations(AppDatabase.MIGRATION_1_2)
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
}
