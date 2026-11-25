package com.example.stopscrolling.usage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardLimitWarningStoreTest {

    private lateinit var context: Context
    private lateinit var store: HardLimitWarningStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Clear previous test data.
        context.getSharedPreferences(
            "hard_limit_warnings",
            Context.MODE_PRIVATE
        ).edit().clear().commit()

        store = HardLimitWarningStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences(
            "hard_limit_warnings",
            Context.MODE_PRIVATE
        ).edit().clear().commit()
    }

    @Test
    fun warningHasNotBeenSentInitially() {
        assertFalse(store.wasWarnedToday(groupId = 1))
    }

    @Test
    fun warningIsRememberedAfterBeingMarked() {
        store.markWarnedToday(groupId = 1)

        assertTrue(store.wasWarnedToday(groupId = 1))
    }

    @Test
    fun differentGroupsHaveIndependentWarnings() {
        store.markWarnedToday(groupId = 1)

        assertTrue(store.wasWarnedToday(groupId = 1))
        assertFalse(store.wasWarnedToday(groupId = 2))
    }

    @Test
    fun dailyAndWeeklyWarningsAreStoredSeparately() {
        store.markWarnedToday(
            groupId = 1,
            limitType = "daily"
        )

        assertTrue(
            store.wasWarnedToday(
                groupId = 1,
                limitType = "daily"
            )
        )

        assertFalse(
            store.wasWarnedToday(
                groupId = 1,
                limitType = "weekly"
            )
        )
    }

    @Test
    fun warningSurvivesStoreRecreation() {
        store.markWarnedToday(groupId = 1)

        val recreatedStore = HardLimitWarningStore(context)

        assertTrue(recreatedStore.wasWarnedToday(groupId = 1))
    }
}
