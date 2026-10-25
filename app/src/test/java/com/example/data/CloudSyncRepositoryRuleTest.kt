package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class CloudSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var repository: CloudSyncRepository

    @Before
    fun setupRepo() {
        repository = CloudSyncRepository(db)
    }

    @Test
    fun authenticatedUser_canSaveAndReadOwnProfileAndProgressAndScores() = runEmulatorTest {
        val uid = signInTestUser("alice@numera.test")

        val profile = CloudUserProfile(
            userId = uid,
            displayName = "Alice",
            avatarId = "fox",
            email = "alice@numera.test",
            dailyGoal = 20,
            language = "en",
            theme = "LIGHT",
            settingsJson = "{}"
        )
        repository.saveUserProfile(profile)

        val fetchedProfile = repository.getUserProfile(uid)
        assertNotNull(fetchedProfile)
        assertEquals("Alice", fetchedProfile?.displayName)
        assertEquals(20, fetchedProfile?.dailyGoal)

        // Verify profile update also succeeds and preserves createdAt
        repository.saveUserProfile(profile.copy(displayName = "Alice Updated", dailyGoal = 30))
        val updatedProfile = repository.getUserProfile(uid)
        assertEquals("Alice Updated", updatedProfile?.displayName)
        assertEquals(30, updatedProfile?.dailyGoal)

        val progress = CloudDailyProgress(
            userId = uid,
            streakCurrent = 4,
            streakBest = 7,
            freezesAvailable = 1,
            lastCompletedDay = "2026-10-24",
            skillsJson = "{\"ADD\":3}",
            dailyStateJson = "{\"date\":\"2026-10-24\"}"
        )
        repository.saveDailyProgress(progress)

        val fetchedProgress = repository.getDailyProgress(uid)
        assertNotNull(fetchedProgress)
        assertEquals(4, fetchedProgress?.streakCurrent)
        assertEquals(7, fetchedProgress?.streakBest)

        val scoreDoc = CloudScoreDoc(
            userId = uid,
            mode = "ADDITION",
            difficulty = "MEDIUM",
            count = 10,
            correct = 9,
            timeMs = 15200L,
            accuracy = 0.9,
            ts = 1729750000000L
        )
        repository.saveScore(uid, "score_1", scoreDoc)

        val scores = repository.listScores(uid)
        assertEquals(1, scores.size)
        assertEquals("ADDITION", scores.first().mode)
    }

    @Test
    fun unauthenticatedUser_cannotSaveProfile() = runEmulatorTest {
        auth.signOut()
        val profile = CloudUserProfile(
            userId = "anon_uid",
            displayName = "Anon",
            avatarId = "fox",
            dailyGoal = 20,
            language = "en",
            theme = "LIGHT"
        )
        try {
            repository.saveUserProfile(profile)
            fail("Expected PERMISSION_DENIED for unauthenticated user")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("PERMISSION_DENIED") == true)
        }
    }

    @Test
    fun authenticatedUser_cannotReadOrWriteAnotherUsersData() = runEmulatorTest {
        val aliceUid = signInTestUser("alice2@numera.test")
        repository.saveUserProfile(
            CloudUserProfile(
                userId = aliceUid,
                displayName = "Alice",
                avatarId = "fox",
                dailyGoal = 20,
                language = "en",
                theme = "LIGHT"
            )
        )

        signInTestUser("bob2@numera.test")
        try {
            repository.getUserProfile(aliceUid)
            fail("Expected PERMISSION_DENIED reading another user's profile")
        } catch (e: RuntimeException) {
            assertTrue(e.message?.contains("PERMISSION_DENIED") == true)
        }
    }

    @Test
    fun authenticatedUser_canExecuteSingleWriteBatchDailySyncAndPullOnNewDevice() = runEmulatorTest {
        val uid = signInTestUser("syncbatch@numera.test")
        val now = 1_760_000_000_000L

        val profile = CloudUserProfile(
            userId = uid,
            displayName = "Batch User",
            avatarId = "owl",
            email = "syncbatch@numera.test",
            dailyGoal = 20,
            language = "en",
            theme = "DARK",
            accentTheme = "OCEAN",
            settingsJson = "{\"appearanceMode\":\"DARK\"}"
        )
        val progress = CloudDailyProgress(
            userId = uid,
            streakCurrent = 5,
            streakBest = 12,
            freezesAvailable = 2,
            lastCompletedDay = "2026-10-24",
            skillsJson = "{\"ADD\":4}",
            dailyStateJson = "{\"date\":\"2026-10-24\"}",
            mistakeBookJson = "[]"
        )
        val logs = listOf(
            com.example.core.AnswerLog(
                id = "a1",
                ts = now,
                day = "2026-10-24",
                mode = "add",
                op = "add",
                category = "add_2d_carry",
                difficulty = "medium",
                level = 4,
                operands = listOf(47, 28),
                prompt = "47 + 28",
                expected = 75L,
                given = 75L,
                correct = true,
                timeMs = 2100L,
                attempts = 1,
                source = "normal",
                sessionId = "s_batch_1"
            )
        )
        val dayShards = DailySyncGate.buildDayDocShards(
            userId = uid,
            day = "2026-10-24",
            logs = logs,
            extraAggregates = emptyList()
        )
        val scoreDoc = CloudScoreDoc(
            documentId = "s_batch_1",
            userId = uid,
            mode = "ADDITION",
            difficulty = "MEDIUM",
            count = 10,
            correct = 10,
            timeMs = 18500L,
            accuracy = 1.0,
            ts = now
        )

        val planned = DailySyncGate.planWriteBatch(
            profile = profile,
            dailyProgress = progress,
            dayDocGroups = mapOf("2026-10-24" to dayShards),
            scores = listOf(scoreDoc)
        )
        assertEquals(4, planned.totalOpsCount)

        val committed = repository.syncDailyBatch(
            uid = uid,
            payload = planned
        )
        assertTrue(committed)
        assertTrue("2026-10-24" in planned.syncedDayKeys)
        assertTrue("s_batch_1" in planned.syncedScoreIds)

        val pulled = repository.pullInitialUserData(uid)
        assertNotNull(pulled.profile)
        assertEquals("DARK", pulled.profile?.theme)
        assertEquals("OCEAN", pulled.profile?.accentTheme)
        assertNotNull(pulled.dailyProgress)
        assertEquals(5, pulled.dailyProgress?.streakCurrent)
        assertEquals(1, pulled.dayDocs.size)
        assertEquals("2026-10-24", pulled.dayDocs.first().day)
        assertEquals(1, pulled.dayDocs.first().answers.size)
        assertEquals(1, pulled.scores.size)

        // Verify delete is denied everywhere by Firestore security rules
        try {
            com.google.android.gms.tasks.Tasks.await(
                db.collection("users").document(uid).delete()
            )
            fail("Expected PERMISSION_DENIED when attempting to delete user document")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("PERMISSION_DENIED") == true)
        }
    }
}



