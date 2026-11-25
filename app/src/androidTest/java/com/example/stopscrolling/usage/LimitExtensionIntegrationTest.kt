package com.example.stopscrolling.usage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.stopscrolling.data.AppDatabase
import com.example.stopscrolling.data.LimitType
import com.example.stopscrolling.data.repository.RoomTimeExtensionRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
class LimitExtensionIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: RoomTimeExtensionRepository

    private val utc = TimeZone.getTimeZone("UTC")

    // Fixed time so the test always uses the same day.
    private val nowMillis = 1_700_000_000_000L

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()

        repository = RoomTimeExtensionRepository(
            dao = database.timeExtensionDao(),
            timeZoneProvider = { utc }
        )
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createGroup(
        type: LimitType,
        limitMinutes: Int = 60
    ): RegulatedGroup {
        return RegulatedGroup(
            groupId = 1,
            packageNames = listOf("com.example.social"),
            name = "Social Media",
            timeLimitMinutes = limitMinutes,
            limitType = type
        )
    }

    private suspend fun evaluate(
        group: RegulatedGroup,
        usedMinutes: Int
    ): LimitCheckResult {
        val extraMinutes = repository.extraMinutesOnDay(
            groupId = group.groupId,
            nowMillis = nowMillis
        )

        return LimitEvaluator.evaluate(
            listOf(
                GroupLimitState(
                    group = group,
                    usedMillis = usedMinutes * 60_000L,
                    extraMinutes = extraMinutes
                )
            )
        )
    }

    @Test
    fun softLimit_withoutExtension_showsSoftOverlay() = runBlocking {
        val group = createGroup(LimitType.SOFT)

        val result = evaluate(group, usedMinutes = 65)

        assertEquals(
            LimitAction.SHOW_SOFT,
            result.decision?.action
        )
    }

    @Test
    fun softLimit_withExtension_allowsMoreTime() = runBlocking {
        val group = createGroup(LimitType.SOFT)

        repository.grantExtension(
            groupId = group.groupId,
            minutes = 15,
            nowMillis = nowMillis
        )

        val savedMinutes = repository.extraMinutesOnDay(
            group.groupId,
            nowMillis
        )
        assertEquals(15, savedMinutes)

        val result = evaluate(group, usedMinutes = 65)

        assertNull(result.decision)
    }

    @Test
    fun softLimit_afterExtensionExpires_showsSoftOverlay() = runBlocking {
        val group = createGroup(LimitType.SOFT)

        repository.grantExtension(
            groupId = group.groupId,
            minutes = 15,
            nowMillis = nowMillis
        )

        val result = evaluate(group, usedMinutes = 75)

        assertEquals(
            LimitAction.SHOW_SOFT,
            result.decision?.action
        )
    }

    @Test
    fun hardLimit_ignoresExtension() = runBlocking {
        val group = createGroup(LimitType.HARD)

        repository.grantExtension(
            groupId = group.groupId,
            minutes = 15,
            nowMillis = nowMillis
        )

        val result = evaluate(group, usedMinutes = 65)

        assertEquals(
            LimitAction.SHOW_HARD,
            result.decision?.action
        )
    }

    @Test
    fun noLimit_doesNotShowOverlay() = runBlocking {
        val group = createGroup(
            type = LimitType.HARD,
            limitMinutes = 0
        )

        val result = evaluate(group, usedMinutes = 120)

        assertNull(result.decision)
    }

    @Test
    fun multipleExtensions_areAddedTogether() = runBlocking {
        val group = createGroup(LimitType.SOFT)

        repository.grantExtension(
            groupId = group.groupId,
            minutes = 15,
            nowMillis = nowMillis
        )

        repository.grantExtension(
            groupId = group.groupId,
            minutes = 15,
            nowMillis = nowMillis
        )

        val savedMinutes = repository.extraMinutesOnDay(
            group.groupId,
            nowMillis
        )
        assertEquals(30, savedMinutes)

        // 60-minute limit + 30-minute extension = 90 minutes.
        val allowedResult = evaluate(group, usedMinutes = 85)
        assertNull(allowedResult.decision)

        val blockedResult = evaluate(group, usedMinutes = 90)
        assertEquals(
            LimitAction.SHOW_SOFT,
            blockedResult.decision?.action
        )
    }
}
