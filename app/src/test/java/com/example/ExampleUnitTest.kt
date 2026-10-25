package com.example

import com.example.core.AuthErrorCode
import com.example.core.AuthService
import com.example.core.AuthUser
import com.example.core.DailyEngine
import com.example.core.QuestionGenerator
import com.example.core.SeededPrng
import com.example.core.StorageService
import com.example.model.AnsweredProblem
import com.example.model.DailyState
import com.example.model.Difficulty
import com.example.model.FirebaseEnvConfig
import com.example.model.MathOp
import com.example.model.PasswordStrength
import com.example.model.Problem
import com.example.model.QuizMode
import com.example.model.ScoreRecord
import com.example.model.SkillOp
import com.example.model.SkillState
import com.example.model.StreakState
import com.example.model.UserProfileState
import com.example.ui.AuthGuard
import com.example.ui.ScreenRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `seeded prng is deterministic and respects inclusive ranges`() {
        val prng1 = SeededPrng("2026-10-23|user123")
        val prng2 = SeededPrng("2026-10-23|user123")

        val seq1 = (1..30).map { prng1.int(5, 15) }
        val seq2 = (1..30).map { prng2.int(5, 15) }
        assertEquals(seq1, seq2)
        assertTrue(seq1.all { it in 5..15 })
    }

    @Test
    fun `addition generator respects difficulty operand ranges`() {
        val prng = SeededPrng(42L)
        for (diff in Difficulty.entries) {
            val (min, max) = QuestionGenerator.additionRange(diff)
            val questions = QuestionGenerator.generateQuizQuestions(QuizMode.ADDITION, diff, 25, prng)
            for (q in questions) {
                assertTrue("a=${q.a} not in $min..$max for $diff", q.a in min..max)
                assertTrue("b=${q.b} not in $min..$max for $diff", q.b in min..max)
                assertEquals(q.a.toLong() + q.b.toLong(), q.answer)
            }
        }
    }

    @Test
    fun `subtraction generator never produces negative answers and respects ranges`() {
        val prng = SeededPrng(99L)
        for (diff in Difficulty.entries) {
            val (min, max) = QuestionGenerator.subtractionRange(diff)
            val questions = QuestionGenerator.generateQuizQuestions(QuizMode.SUBTRACTION, diff, 30, prng)
            for (q in questions) {
                assertTrue(q.a in min..max)
                assertTrue(q.b in min..q.a)
                assertTrue("Subtraction answer was negative: ${q.answer}", q.answer >= 0L)
                assertEquals((q.a - q.b).toLong(), q.answer)
            }
        }
    }

    @Test
    fun `division generator always produces exact integer quotients and non-trivial divisors`() {
        val prng = SeededPrng(12345L)
        for (diff in Difficulty.entries) {
            val questions = QuestionGenerator.generateQuizQuestions(QuizMode.DIVISION, diff, 30, prng)
            for (q in questions) {
                assertTrue("Divisor must be > 1, got ${q.b}", q.b > 1)
                assertTrue("Divisor must be < dividend (${q.a}), got ${q.b}", q.b < q.a)
                assertEquals("Division must be exact: ${q.a} / ${q.b}", 0, q.a % q.b)
                assertEquals((q.a / q.b).toLong(), q.answer)
            }
        }
    }

    @Test
    fun `quiz generator never repeats the same operand pair twice in a row`() {
        val prng = SeededPrng(777L)
        val questions = QuestionGenerator.generateQuizQuestions(QuizMode.MULTIPLICATION, Difficulty.EASY, 40, prng)
        for (i in 1 until questions.size) {
            val prev = questions[i - 1]
            val cur = questions[i]
            val samePair = (prev.a == cur.a && prev.b == cur.b)
            assertFalse("Repeated operand pair at index $i: ${cur.a}, ${cur.b}", samePair)
        }
    }

    @Test
    fun `arithmetic memory chain obeys caps, exact division, and non-negative values`() {
        val prng = SeededPrng(2026L)
        for (diff in Difficulty.entries) {
            val chain = QuestionGenerator.generateMemoryChain(diff, stepsCount = 8, prng = prng)
            assertEquals(8, chain.steps.size)
            var expectedRunning = chain.startNumber
            for (step in chain.steps) {
                assertEquals(expectedRunning, step.previousValue)
                assertTrue("Running value became negative: ${step.runningValue}", step.runningValue >= 0)
                if (step.op == MathOp.DIV && step.previousValue > 0) {
                    assertEquals(0, step.previousValue % step.operand)
                }
                expectedRunning = step.runningValue
            }
            assertEquals(expectedRunning, chain.finalAnswer)
        }
    }

    @Test
    fun `daily engine builds deterministic set and enforces max 2 identical ops in a row`() {
        val skills = SkillOp.entries.associateWith { SkillState(op = it, level = 4) }
        val enabled = SkillOp.entries.toSet()

        val set1 = DailyEngine.buildDailySet("2026-10-23", "inst_abc", 16, enabled, skills)
        val set2 = DailyEngine.buildDailySet("2026-10-23", "inst_abc", 16, enabled, skills)

        assertEquals(16, set1.size)
        assertEquals(set1.map { it.displayBase }, set2.map { it.displayBase })

        for (i in 2 until set1.size) {
            val tripleSame = (set1[i].op == set1[i - 1].op && set1[i].op == set1[i - 2].op)
            assertFalse("Found 3 identical ops in a row at index $i", tripleSame)
        }
    }

    @Test
    fun `daily engine levels up when accuracy ge 90 percent and avg time le target`() {
        val initialSkill = SkillState(op = SkillOp.ADD, level = 3, recentAccuracy = 0.7, recentTimeMs = 4000.0, totalAnswered = 5)
        val p1 = Problem("1", MathOp.ADD, 4, 5, 9, "4 + 5", level = 3, normalizedKey = "ADD:4:5")
        val p2 = Problem("2", MathOp.ADD, 3, 6, 9, "3 + 6", level = 3, normalizedKey = "ADD:3:6")

        val answers = listOf(
            AnsweredProblem(p1, 9, isCorrect = true, timeMs = 2000L),
            AnsweredProblem(p2, 9, isCorrect = true, timeMs = 2000L)
        )
        val state = DailyState(
            date = "2026-10-23",
            installId = "test",
            skills = mapOf(SkillOp.ADD to initialSkill),
            todayProblems = listOf(p1, p2),
            todayAnswers = answers
        )

        val updated = DailyEngine.completeDailyWorkout(state, "2026-10-23")
        val newAddSkill = updated.skills[SkillOp.ADD]!!
        assertEquals(4, newAddSkill.level)
        assertTrue(newAddSkill.recentAccuracy > 0.7)
    }

    @Test
    fun `daily engine levels down when accuracy below 60 percent`() {
        val initialSkill = SkillState(op = SkillOp.MUL, level = 5, recentAccuracy = 0.8, recentTimeMs = 5000.0, totalAnswered = 8)
        val p1 = Problem("1", MathOp.MUL, 6, 7, 42, "6 × 7", level = 5, normalizedKey = "MUL:6:7")
        val p2 = Problem("2", MathOp.MUL, 8, 9, 72, "8 × 9", level = 5, normalizedKey = "MUL:8:9")

        val answers = listOf(
            AnsweredProblem(p1, 40, isCorrect = false, timeMs = 3000L),
            AnsweredProblem(p2, 70, isCorrect = false, timeMs = 3000L)
        )
        val state = DailyState(
            date = "2026-10-23",
            installId = "test",
            skills = mapOf(SkillOp.MUL to initialSkill),
            todayProblems = listOf(p1, p2),
            todayAnswers = answers
        )

        val updated = DailyEngine.completeDailyWorkout(state, "2026-10-23")
        assertEquals(4, updated.skills[SkillOp.MUL]!!.level)
    }

    @Test
    fun `streak grants freeze at 7-day milestone and auto-consumes freeze on single missed day`() {
        var streak = StreakState(
            current = 6,
            best = 6,
            freezesAvailable = 0,
            lastCompletedDate = "2026-10-19",
            completedDates = setOf("2026-10-19")
        )

        streak = DailyEngine.updateStreakOnCompletion(streak, "2026-10-20")
        assertEquals(7, streak.current)
        assertEquals(1, streak.freezesAvailable)

        streak = DailyEngine.updateStreakOnCompletion(streak, "2026-10-22")
        assertEquals(8, streak.current)
        assertEquals(0, streak.freezesAvailable)
        assertTrue(streak.savedByFreezeToday)
        assertTrue("2026-10-21" in streak.frozenDates)
    }

    @Test
    fun `guest to account merge takes higher skill levels, longer streaks, and unions scores`() {
        val guestDaily = DailyState(
            date = "2026-10-24",
            installId = "guest",
            streak = StreakState(
                current = 5,
                best = 9,
                freezesAvailable = 2,
                completedDates = setOf("2026-10-20", "2026-10-21")
            ),
            skills = mapOf(
                SkillOp.ADD to SkillState(SkillOp.ADD, level = 7, recentAccuracy = 0.92, recentTimeMs = 2100.0, totalAnswered = 40),
                SkillOp.SUB to SkillState(SkillOp.SUB, level = 2, recentAccuracy = 0.70, recentTimeMs = 4200.0, totalAnswered = 10)
            )
        )
        val accountDaily = DailyState(
            date = "2026-10-24",
            installId = "uid_123",
            streak = StreakState(
                current = 3,
                best = 6,
                freezesAvailable = 1,
                completedDates = setOf("2026-10-22", "2026-10-23")
            ),
            skills = mapOf(
                SkillOp.ADD to SkillState(SkillOp.ADD, level = 4, recentAccuracy = 0.85, recentTimeMs = 2900.0, totalAnswered = 20),
                SkillOp.SUB to SkillState(SkillOp.SUB, level = 6, recentAccuracy = 0.88, recentTimeMs = 2600.0, totalAnswered = 35)
            )
        )

        val mergedDaily = StorageService.mergeDailyStates(guestDaily, accountDaily)
        assertEquals(5, mergedDaily.streak.current)
        assertEquals(9, mergedDaily.streak.best)
        assertEquals(2, mergedDaily.streak.freezesAvailable)
        assertEquals(4, mergedDaily.streak.completedDates.size)
        assertEquals(7, mergedDaily.skills[SkillOp.ADD]?.level)
        assertEquals(6, mergedDaily.skills[SkillOp.SUB]?.level)

        val guestScores = listOf(
            ScoreRecord("s1", "ADDITION_EASY_10", QuizMode.ADDITION, Difficulty.EASY, 10, 10, 14000L, 1.0, 100L),
            ScoreRecord("s2", "MULTIPLICATION_HARD_10", QuizMode.MULTIPLICATION, Difficulty.HARD, 10, 9, 22000L, 0.9, 200L)
        )
        val accountScores = listOf(
            ScoreRecord("s2", "MULTIPLICATION_HARD_10", QuizMode.MULTIPLICATION, Difficulty.HARD, 10, 9, 22000L, 0.9, 200L),
            ScoreRecord("s3", "DIVISION_EASY_10", QuizMode.DIVISION, Difficulty.EASY, 10, 10, 18000L, 1.0, 300L)
        )
        val mergedScores = StorageService.mergeScores(guestScores, accountScores)
        assertEquals(3, mergedScores.size)
    }

    @Test
    fun `AuthGuard restricts signed-out users to AuthHub and routes signed-in users appropriately`() {
        val signedOutProfile = UserProfileState(userId = "guest", isGuest = true)
        assertFalse(AuthGuard.canAccessProtectedRoute(null))
        assertEquals(
            ScreenRoute.AuthHub(isSignUpMode = false),
            AuthGuard.resolveRoute(null, signedOutProfile, ScreenRoute.MainTabs)
        )

        // Signed-in Guest user -> MainTabs
        val guestUser = AuthUser(
            uid = "guest_abc123",
            displayName = "Guest",
            email = null,
            photoUrl = null,
            provider = "guest",
            emailVerified = true
        )
        val guestProfile = UserProfileState(userId = "guest_abc123", isGuest = true, displayName = "Guest")
        assertTrue(AuthGuard.canAccessProtectedRoute(guestUser))
        assertEquals(
            ScreenRoute.MainTabs,
            AuthGuard.resolveRoute(guestUser, guestProfile, ScreenRoute.AuthHub(false))
        )

        // Unverified Email user -> VerifyEmail
        val unverifiedEmailUser = AuthUser(
            uid = "uid_email_1",
            displayName = "Alex",
            email = "alex@example.com",
            photoUrl = null,
            provider = "email",
            emailVerified = false
        )
        val emailProfile = UserProfileState(
            userId = "uid_email_1",
            isGuest = false,
            displayName = "Alex",
            email = "alex@example.com",
            provider = "email",
            emailVerified = false
        )
        assertEquals(
            ScreenRoute.VerifyEmail,
            AuthGuard.resolveRoute(unverifiedEmailUser, emailProfile, ScreenRoute.MainTabs)
        )

        // Verified Email user -> MainTabs
        val verifiedEmailUser = unverifiedEmailUser.copy(emailVerified = true)
        val verifiedProfile = emailProfile.copy(emailVerified = true, isProfileComplete = true)
        assertEquals(
            ScreenRoute.MainTabs,
            AuthGuard.resolveRoute(verifiedEmailUser, verifiedProfile, ScreenRoute.VerifyEmail)
        )

        // New Google user with blank displayName & incomplete profile -> ProfileSetup
        val newGoogleUser = AuthUser(
            uid = "uid_google_1",
            displayName = "",
            email = "new@example.com",
            photoUrl = null,
            provider = "google",
            emailVerified = true
        )
        val incompleteGoogleProfile = UserProfileState(
            userId = "uid_google_1",
            isGuest = false,
            displayName = "",
            provider = "google",
            isProfileComplete = false
        )
        assertEquals(
            ScreenRoute.ProfileSetup,
            AuthGuard.resolveRoute(newGoogleUser, incompleteGoogleProfile, ScreenRoute.MainTabs)
        )
    }

    @Test
    fun `PasswordStrength evaluates minimum 8 characters and complexity tiers`() {
        assertEquals(PasswordStrength.TOO_SHORT, PasswordStrength.evaluate("short7"))
        assertEquals(PasswordStrength.WEAK, PasswordStrength.evaluate("abcdefgh"))
        assertEquals(PasswordStrength.FAIR, PasswordStrength.evaluate("abcdefgh1"))
        assertEquals(PasswordStrength.STRONG, PasswordStrength.evaluate("Numera#2026"))
    }

    @Test
    fun `FirebaseEnvConfig derives authDomain from projectId and maps friendly auth errors`() {
        val cfg = FirebaseEnvConfig(
            apiKey = "test_key",
            projectId = "numera-math-prod",
            appId = "1:123:android:abc",
            webClientId = "123-xyz",
            firestoreDatabaseId = "(default)"
        )
        assertEquals("numera-math-prod.firebaseapp.com", cfg.authDomain)

        val wrongPw = AuthService.mapExceptionToAuthFlowException(
            RuntimeException("ERROR_WRONG_PASSWORD: The password is invalid")
        )
        assertEquals(AuthErrorCode.WRONG_PASSWORD, wrongPw.code)

        val emailInUse = AuthService.mapExceptionToAuthFlowException(
            RuntimeException("ERROR_EMAIL_ALREADY_IN_USE")
        )
        assertEquals(AuthErrorCode.EMAIL_ALREADY_IN_USE, emailInUse.code)
    }

    @Test
    fun `CategoryRegistry classifies all operations and operand combinations accurately`() {
        assertEquals("add_1d", com.example.core.CategoryRegistry.classify("add", listOf(4, 5)))
        assertEquals("add_2d_nocarry", com.example.core.CategoryRegistry.classify("add", listOf(23, 45)))
        assertEquals("add_2d_carry", com.example.core.CategoryRegistry.classify("add", listOf(27, 45)))
        assertEquals("add_3d", com.example.core.CategoryRegistry.classify("add", listOf(120, 340)))
        assertEquals("add_3d_carry", com.example.core.CategoryRegistry.classify("add", listOf(185, 247)))
        assertEquals("add_large", com.example.core.CategoryRegistry.classify("add", listOf(1200, 3400)))

        assertEquals("sub_1d", com.example.core.CategoryRegistry.classify("sub", listOf(8, 3)))
        assertEquals("sub_2d_noborrow", com.example.core.CategoryRegistry.classify("sub", listOf(68, 25)))
        assertEquals("sub_2d_borrow", com.example.core.CategoryRegistry.classify("sub", listOf(62, 27)))
        assertEquals("sub_3d", com.example.core.CategoryRegistry.classify("sub", listOf(589, 234)))
        assertEquals("sub_3d_borrow", com.example.core.CategoryRegistry.classify("sub", listOf(512, 238)))
        assertEquals("sub_large", com.example.core.CategoryRegistry.classify("sub", listOf(4500, 1200)))

        assertEquals("mul_by_10_100", com.example.core.CategoryRegistry.classify("mul", listOf(47, 10)))
        assertEquals("mul_table_2_5", com.example.core.CategoryRegistry.classify("mul", listOf(4, 8)))
        assertEquals("mul_table_6_9", com.example.core.CategoryRegistry.classify("mul", listOf(7, 8)))
        assertEquals("mul_table_10_12", com.example.core.CategoryRegistry.classify("mul", listOf(11, 12)))
        assertEquals("mul_2dx1d", com.example.core.CategoryRegistry.classify("mul", listOf(47, 8)))
        assertEquals("mul_2dx2d", com.example.core.CategoryRegistry.classify("mul", listOf(34, 56)))
        assertEquals("mul_3dx2d", com.example.core.CategoryRegistry.classify("mul", listOf(145, 24)))
        assertEquals("mul_large", com.example.core.CategoryRegistry.classify("mul", listOf(1200, 35)))
        assertEquals("table_17", com.example.core.CategoryRegistry.classify("mul", listOf(17, 6), singleTableNumber = 17))

        assertEquals("div_by_1d", com.example.core.CategoryRegistry.classify("div", listOf(56, 7)))
        assertEquals("div_by_2d", com.example.core.CategoryRegistry.classify("div", listOf(360, 15)))
        assertEquals("div_large", com.example.core.CategoryRegistry.classify("div", listOf(2400, 120)))

        assertEquals("sqrt_small", com.example.core.CategoryRegistry.classify("sqrt", listOf(81)))
        assertEquals("sqrt_medium", com.example.core.CategoryRegistry.classify("sqrt", listOf(625)))
        assertEquals("sqrt_large", com.example.core.CategoryRegistry.classify("sqrt", listOf(1600)))

        assertEquals("pow2_small", com.example.core.CategoryRegistry.classify("pow", listOf(12, 2)))
        assertEquals("pow2_large", com.example.core.CategoryRegistry.classify("pow", listOf(24, 2)))
        assertEquals("pow3", com.example.core.CategoryRegistry.classify("pow", listOf(5, 3)))
        assertEquals("pow4", com.example.core.CategoryRegistry.classify("pow", listOf(3, 4)))
        assertEquals("cbrt_all", com.example.core.CategoryRegistry.classify("cbrt", listOf(27, 3)))

        assertEquals("chain_short", com.example.core.CategoryRegistry.classify("chain", listOf(10, 5), chainStepCount = 4))
        assertEquals("chain_long", com.example.core.CategoryRegistry.classify("chain", listOf(10, 5), chainStepCount = 8))
    }

    @Test
    fun `StatsService computes median, p90, discards timeMs gt 60s from speed, and classifies quadrants`() {
        val now = 1_760_000_000_000L
        // 6 answers in mul_2dx1d: 5 wrong/slow + 1 distracted (>60s) correct
        val logs = listOf(
            com.example.core.AnswerLog("1", now - 5000, "2026-10-23", "mul", "mul", "mul_2dx1d", "medium", null, listOf(47, 8), "47 × 8", 376, 366, false, 6200, 1, "normal", "s1"),
            com.example.core.AnswerLog("2", now - 4000, "2026-10-23", "mul", "mul", "mul_2dx1d", "medium", null, listOf(36, 7), "36 × 7", 252, 242, false, 5800, 1, "normal", "s1"),
            com.example.core.AnswerLog("3", now - 3000, "2026-10-23", "mul", "mul", "mul_2dx1d", "medium", null, listOf(28, 6), "28 × 6", 168, 168, true, 5400, 1, "normal", "s1"),
            com.example.core.AnswerLog("4", now - 2000, "2026-10-23", "mul", "mul", "mul_2dx1d", "medium", null, listOf(53, 9), "53 × 9", 477, 467, false, 6500, 1, "normal", "s1"),
            com.example.core.AnswerLog("5", now - 1000, "2026-10-23", "mul", "mul", "mul_2dx1d", "medium", null, listOf(64, 4), "64 × 4", 256, 256, true, 5000, 1, "normal", "s1"),
            // Distracted answer (> 60s): should count toward accuracy (3/6 = 50%), NOT toward speed average
            com.example.core.AnswerLog("6", now - 500, "2026-10-23", "mul", "mul", "mul_2dx1d", "medium", null, listOf(19, 5), "19 × 5", 95, 95, true, 85_000, 1, "normal", "s1"),
            // Fast mastered category add_1d
            com.example.core.AnswerLog("7", now - 500, "2026-10-23", "add", "add", "add_1d", "easy", null, listOf(4, 5), "4 + 5", 9, 9, true, 1200, 1, "normal", "s2"),
            com.example.core.AnswerLog("8", now - 400, "2026-10-23", "add", "add", "add_1d", "easy", null, listOf(6, 3), "6 + 3", 9, 9, true, 1100, 1, "normal", "s2"),
            com.example.core.AnswerLog("9", now - 300, "2026-10-23", "add", "add", "add_1d", "easy", null, listOf(7, 2), "7 + 2", 9, 9, true, 1300, 1, "normal", "s2"),
            com.example.core.AnswerLog("10", now - 200, "2026-10-23", "add", "add", "add_1d", "easy", null, listOf(8, 1), "8 + 1", 9, 9, true, 1250, 1, "normal", "s2"),
            com.example.core.AnswerLog("11", now - 100, "2026-10-23", "add", "add", "add_1d", "easy", null, listOf(5, 3), "5 + 3", 8, 8, true, 1150, 1, "normal", "s2")
        )

        val summary = com.example.core.StatsService.buildDashboardSnapshot(
            rawLogs = logs,
            rolledAggregates = emptyList(),
            dateRange = com.example.core.InsightsDateRange.ALL_TIME,
            opFilter = null,
            todayStr = "2026-10-23"
        )

        val mulStat = summary.categoryStats.first { it.categoryId == "mul_2dx1d" }
        assertEquals(6, mulStat.attempts)
        assertEquals(3, mulStat.correct)
        assertEquals(0.5, mulStat.accuracy, 0.001)
        // Speed avg should only average the 5 valid times: (6200+5800+5400+6500+5000)/5 = 5780
        assertEquals(5780L, mulStat.avgTimeMs)
        assertEquals(com.example.core.QuadrantClass.NEEDS_WORK, mulStat.quadrant)

        val addStat = summary.categoryStats.first { it.categoryId == "add_1d" }
        assertEquals(1.0, addStat.accuracy, 0.001)
        assertEquals(com.example.core.QuadrantClass.MASTERED, addStat.quadrant)
        assertTrue(mulStat.weaknessScore > addStat.weaknessScore)
        // 11 questions answered on 2026-10-23 >= 10 threshold -> practiceStreakCurrent = 1
        assertEquals(1, summary.practiceStreakCurrent)
    }

    @Test
    fun `AnswerLogService spaced repetition intervals and 90-day rollup work accurately`() {
        val now = 1_760_000_000_000L
        val prob = Problem("p1", MathOp.MUL, 47, 8, 376L, "47 × 8")
        val entry1 = com.example.core.AnswerLogService.computeSpacedRepetitionUpdate(
            existing = null,
            problem = prob,
            category = "mul_2dx1d",
            op = "mul",
            givenAnswer = 366L,
            isCorrect = false,
            timeMs = 4200L,
            userMedianMs = 3500L,
            todayStr = "2026-10-23",
            nowMs = now
        )!!
        assertEquals(1, entry1.missCount)
        assertEquals(0, entry1.consecutiveCorrect)
        assertFalse(entry1.isMastered)
        assertEquals("2026-10-24", entry1.nextReviewDay) // +1 day interval

        // First fast correct replay -> advances to +3 day interval, consecutiveCorrect = 1
        val entry2 = com.example.core.AnswerLogService.computeSpacedRepetitionUpdate(
            existing = entry1,
            problem = prob,
            category = "mul_2dx1d",
            op = "mul",
            givenAnswer = 376L,
            isCorrect = true,
            timeMs = 3100L,
            userMedianMs = 3500L,
            todayStr = "2026-10-24",
            nowMs = now + 1000L
        )!!
        assertEquals(1, entry2.consecutiveCorrect)
        assertFalse(entry2.isMastered)
        assertEquals("2026-10-27", entry2.nextReviewDay)

        // Second fast correct replay -> mastered!
        val entry3 = com.example.core.AnswerLogService.computeSpacedRepetitionUpdate(
            existing = entry2,
            problem = prob,
            category = "mul_2dx1d",
            op = "mul",
            givenAnswer = 376L,
            isCorrect = true,
            timeMs = 2900L,
            userMedianMs = 3500L,
            todayStr = "2026-10-27",
            nowMs = now + 2000L
        )!!
        assertEquals(2, entry3.consecutiveCorrect)
        assertTrue(entry3.isMastered)

        // Test 90-day rollup
        val oldTs = now - 100L * 86_400_000L
        val logTemplate = com.example.core.AnswerLog(
            id = "w1", ts = now, day = "2026-10-23", mode = "mul", op = "mul",
            category = "mul_2dx1d", difficulty = "medium", level = null,
            operands = listOf(47, 8), prompt = "47 × 8", expected = 376,
            given = 366, correct = false, timeMs = 4200, attempts = 1, source = "normal", sessionId = "s1"
        )
        val oldLog1 = logTemplate.copy(id = "old1", ts = oldTs, day = "2026-07-01", timeMs = 4000, correct = true)
        val oldLog2 = logTemplate.copy(id = "old2", ts = oldTs + 1000, day = "2026-07-01", timeMs = 6000, correct = false)
        val recentLog = logTemplate.copy(id = "rec1", ts = now, day = "2026-10-23")

        val (kept, aggs) = com.example.core.AnswerLogService.rollUpLogsPure(
            rawLogs = listOf(oldLog1, oldLog2, recentLog),
            existingAggregates = emptyList(),
            todayStr = "2026-10-23"
        )
        assertEquals(1, kept.size)
        assertEquals("rec1", kept.first().id)
        assertEquals(1, aggs.size)
        assertEquals(2, aggs.first().attempts)
        assertEquals(1, aggs.first().correct)
        assertEquals(10000L, aggs.first().totalTimeMs)
    }

    @Test
    fun `SmartPracticeService builds 50-50 split when mistakes exist and falls back cleanly when 0 mistakes exist`() {
        val now = 1_760_000_000_000L
        val dueMistakes = (1..12).map { i ->
            val p = Problem("p_$i", MathOp.MUL, 10 + i, 8, ((10 + i) * 8).toLong(), "${10 + i} × 8")
            com.example.core.MistakeBookEntry(
                normalizedKey = p.normalizedKey,
                problem = p,
                category = "mul_2dx1d",
                op = "mul",
                prompt = "${10 + i} × 8",
                expected = ((10 + i) * 8).toLong(),
                lastWrongAnswer = 100L,
                missCount = 2,
                consecutiveCorrect = 0,
                lastMissedAt = now - i * 1000L,
                nextReviewDay = "2026-10-23",
                isMastered = false
            )
        }

        val planWithMistakes = com.example.core.SmartPracticeService.build(
            questionCount = 20,
            mistakeBook = dueMistakes,
            categoryStats = emptyList(),
            todayStr = "2026-10-23",
            prng = SeededPrng(42L)
        )
        assertEquals(20, planWithMistakes.items.size)
        val mistakeCount = planWithMistakes.items.count { it.source == "data-practice-mistake" }
        val newCount = planWithMistakes.items.count { it.source == "data-practice-new" }
        assertEquals(10, mistakeCount)
        assertEquals(10, newCount)
        assertFalse(planWithMistakes.usedFallbackForFewMistakes)

        // Fallback when 0 mistakes exist
        val planZeroMistakes = com.example.core.SmartPracticeService.build(
            questionCount = 20,
            mistakeBook = emptyList(),
            categoryStats = emptyList(),
            todayStr = "2026-10-23",
            prng = SeededPrng(99L)
        )
        assertEquals(20, planZeroMistakes.items.size)
        assertEquals(0, planZeroMistakes.items.count { it.source == "data-practice-mistake" })
        assertEquals(20, planZeroMistakes.items.count { it.source == "data-practice-new" })
        assertTrue(planZeroMistakes.usedFallbackForFewMistakes)
    }

    @Test
    fun `DailySyncGate runs once per day for both signed-in and guest users, skips unverified email or offline, and enforces 5-minute manual cooldown`() {
        // 1. Signed-in user, online, dirty data, never synced today -> should run
        assertTrue(
            com.example.data.DailySyncGate.shouldRunDailySync(
                isAuthenticated = true,
                isGuest = false,
                emailVerified = true,
                isOnline = true,
                lastSyncDay = "2026-10-23",
                todayStr = "2026-10-24",
                hasDirtyChanges = true
            )
        )

        // 2. Already synced today (lastSyncDay == today) -> skips even if app is reopened many times
        assertFalse(
            com.example.data.DailySyncGate.shouldRunDailySync(
                isAuthenticated = true,
                isGuest = false,
                emailVerified = true,
                isOnline = true,
                lastSyncDay = "2026-10-24",
                todayStr = "2026-10-24",
                hasDirtyChanges = true
            )
        )

        // 3. Guest (anonymous) user -> NOW SYNCS TOO under their anonymous uid!
        assertTrue(
            com.example.data.DailySyncGate.shouldRunDailySync(
                isAuthenticated = true,
                isGuest = true,
                emailVerified = false,
                isOnline = true,
                lastSyncDay = "",
                todayStr = "2026-10-24",
                hasDirtyChanges = true
            )
        )

        // 4. Unverified non-guest email user -> skips cloud sync until emailVerified == true
        assertFalse(
            com.example.data.DailySyncGate.shouldRunDailySync(
                isAuthenticated = true,
                isGuest = false,
                emailVerified = false,
                isOnline = true,
                lastSyncDay = "",
                todayStr = "2026-10-24",
                hasDirtyChanges = true
            )
        )

        // 5. Offline device -> skips until back online
        assertFalse(
            com.example.data.DailySyncGate.shouldRunDailySync(
                isAuthenticated = true,
                isGuest = false,
                emailVerified = true,
                isOnline = false,
                lastSyncDay = "2026-10-23",
                todayStr = "2026-10-24",
                hasDirtyChanges = true
            )
        )

        // 6. Failed attempt does NOT update lastSyncDay, so next app open/close on the same day retries
        val lastSyncDayAfterFailure = "2026-10-23" // unchanged on failure
        assertTrue(
            com.example.data.DailySyncGate.shouldRunDailySync(
                isAuthenticated = true,
                isGuest = false,
                emailVerified = true,
                isOnline = true,
                lastSyncDay = lastSyncDayAfterFailure,
                todayStr = "2026-10-24",
                hasDirtyChanges = true
            )
        )

        // 7. Manual "Push my data now" button is rate-limited to 5 minutes (300_000 ms) and works for guests too
        val t0 = 1_760_000_000_000L
        assertEquals(300_000L, com.example.data.DailySyncGate.MANUAL_SYNC_COOLDOWN_MS)
        assertTrue(com.example.data.DailySyncGate.canRunManualSync(true, isGuest = true, emailVerified = false, isOnline = true, lastManualSyncMs = 0L, nowMs = t0))
        assertFalse(com.example.data.DailySyncGate.canRunManualSync(true, isGuest = false, emailVerified = true, isOnline = true, lastManualSyncMs = t0, nowMs = t0 + 120_000L)) // 2m later -> blocked
        assertTrue(com.example.data.DailySyncGate.canRunManualSync(true, isGuest = false, emailVerified = true, isOnline = true, lastManualSyncMs = t0, nowMs = t0 + 300_001L)) // >5m later -> allowed
    }

    @Test
    fun `practice streak calculation from answer logs awards 7-day freeze and rescues single missed day`() {
        val now = 1_760_000_000_000L
        // Build 7 consecutive days (2026-10-15..2026-10-21) with 10 answers each -> 7-day streak & 1 freeze earned
        // Skip 2026-10-22 (0 answers -> rescued by freeze!), then 10 answers on 2026-10-23 and 2026-10-24
        val activeDays = listOf(
            "2026-10-15", "2026-10-16", "2026-10-17", "2026-10-18",
            "2026-10-19", "2026-10-20", "2026-10-21",
            // 2026-10-22 missed!
            "2026-10-23", "2026-10-24"
        )
        val logs = mutableListOf<com.example.core.AnswerLog>()
        var idCounter = 1
        for (day in activeDays) {
            repeat(10) {
                logs.add(
                    com.example.core.AnswerLog(
                        id = "s_${idCounter++}",
                        ts = now + idCounter,
                        day = day,
                        mode = "add",
                        op = "add",
                        category = "add_1d",
                        difficulty = "easy",
                        level = 2,
                        operands = listOf(3, 4),
                        prompt = "3 + 4",
                        expected = 7L,
                        given = 7L,
                        correct = true,
                        timeMs = 1500L,
                        attempts = 1,
                        source = "normal",
                        sessionId = "sess_$day"
                    )
                )
            }
        }
        // Also add a day with only 5 questions (below the 10-question threshold) on 2026-10-10
        repeat(5) {
            logs.add(
                com.example.core.AnswerLog(
                    id = "sub10_${idCounter++}",
                    ts = now,
                    day = "2026-10-10",
                    mode = "add",
                    op = "add",
                    category = "add_1d",
                    difficulty = "easy",
                    level = 1,
                    operands = listOf(1, 2),
                    prompt = "1 + 2",
                    expected = 3L,
                    given = 3L,
                    correct = true,
                    timeMs = 1200L,
                    attempts = 1,
                    source = "normal",
                    sessionId = "sess_short"
                )
            )
        }

        val streakState = com.example.core.StatsService.computePracticeStreakState(
            rawLogs = logs,
            rolledAggregates = emptyList(),
            todayStr = "2026-10-24"
        )
        // 7 days + rescued missed day + 2 more days = 9-day current streak!
        assertEquals(9, streakState.current)
        assertEquals(9, streakState.best)
        // 1 freeze earned at day 7, 1 freeze consumed on 2026-10-22 -> 0 freezes remaining
        assertEquals(0, streakState.freezesAvailable)
        assertTrue("2026-10-22" in streakState.frozenDates)
        assertFalse("2026-10-10" in streakState.completedDates)
    }

    @Test
    fun `strength and weakness classification groups categories into Strong, Needs practice, and Not enough data`() {
        val now = 1_760_000_000_000L
        val logs = mutableListOf<com.example.core.AnswerLog>()
        var idx = 1

        // 1. Strong category: add_1d -> 12 attempts, 100% accuracy, 1100ms avg (<= median)
        repeat(12) {
            logs.add(
                com.example.core.AnswerLog(
                    id = "sw_${idx++}", ts = now + idx, day = "2026-10-24",
                    mode = "add", op = "add", category = "add_1d", difficulty = "easy", level = 2,
                    operands = listOf(4, 5), prompt = "4 + 5", expected = 9, given = 9,
                    correct = true, timeMs = 1100L, attempts = 1, source = "normal", sessionId = "s1"
                )
            )
        }

        // 2. Weak category (accuracy < 80%): mul_2dx1d -> 10 attempts, 60% accuracy, 4800ms avg
        repeat(10) { i ->
            val ok = i < 6
            logs.add(
                com.example.core.AnswerLog(
                    id = "sw_${idx++}", ts = now + idx, day = "2026-10-24",
                    mode = "mul", op = "mul", category = "mul_2dx1d", difficulty = "medium", level = 4,
                    operands = listOf(47, 8), prompt = "47 × 8", expected = 376, given = if (ok) 376 else 366,
                    correct = ok, timeMs = 4800L, attempts = 1, source = "normal", sessionId = "s1"
                )
            )
        }

        // 3. Not enough data (< 10 attempts): div_by_1d -> 4 attempts
        repeat(4) {
            logs.add(
                com.example.core.AnswerLog(
                    id = "sw_${idx++}", ts = now + idx, day = "2026-10-24",
                    mode = "div", op = "div", category = "div_by_1d", difficulty = "easy", level = 2,
                    operands = listOf(56, 7), prompt = "56 ÷ 7", expected = 8, given = 8,
                    correct = true, timeMs = 2000L, attempts = 1, source = "normal", sessionId = "s1"
                )
            )
        }

        val snapshot = com.example.core.StatsService.buildDashboardSnapshot(
            rawLogs = logs,
            rolledAggregates = emptyList(),
            dateRange = com.example.core.InsightsDateRange.ALL_TIME,
            opFilter = null,
            todayStr = "2026-10-24"
        )

        val addStat = snapshot.categoryStats.first { it.categoryId == "add_1d" }
        val mulStat = snapshot.categoryStats.first { it.categoryId == "mul_2dx1d" }
        val divStat = snapshot.categoryStats.first { it.categoryId == "div_by_1d" }

        assertEquals(com.example.core.StrengthWeaknessGroup.STRONG, addStat.strengthGroup)
        assertEquals(com.example.core.StrengthWeaknessGroup.NEEDS_PRACTICE, mulStat.strengthGroup)
        assertEquals(com.example.core.StrengthWeaknessGroup.NOT_ENOUGH_DATA, divStat.strengthGroup)
        assertEquals(listOf("add_1d"), snapshot.strongCategories.map { it.categoryId })
        assertEquals(listOf("mul_2dx1d"), snapshot.needsPracticeCategories.map { it.categoryId })
        assertEquals("div_by_1d", snapshot.notEnoughDataCategories.first().categoryId)
        val summaryCard = snapshot.insightCards.first { it.type == "fastest_slowest" }
        assertEquals("add_1d", summaryCard.params["fast"])
        assertEquals("mul_2dx1d", summaryCard.params["slow"])
    }

    @Test
    fun `no Firestore delete calls or destructive reset methods exist in production code and firestore rules deny delete`() {
        val rulesFile = listOf(
            java.io.File("firestore.rules"),
            java.io.File("../firestore.rules")
        ).firstOrNull { it.exists() }
        assertTrue("firestore.rules must exist", rulesFile != null)
        val rulesText = rulesFile!!.readText()
        assertTrue("firestore.rules must explicitly deny delete", rulesText.contains("allow delete: if false;"))

        val srcDir = listOf(
            java.io.File("src/main/java"),
            java.io.File("app/src/main/java")
        ).firstOrNull { it.exists() }
        assertTrue("src/main/java must exist", srcDir != null)

        val forbiddenDeleteCall = Regex("""\.\s*delete\s*\(\s*\)""")
        srcDir!!.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                assertFalse(
                    "Found forbidden .delete() call in ${file.name}",
                    forbiddenDeleteCall.containsMatchIn(text)
                )
                assertFalse(
                    "Found removed deleteAllUserData in ${file.name}",
                    text.contains("deleteAllUserData")
                )
            }
    }

    @Test
    fun `DailySyncGate splits day documents exceeding 1500 answers into days_date_2 and caps WriteBatch at 500 ops`() {
        val now = 1_760_000_000_000L
        val logs = (1..1650).map { i ->
            com.example.core.AnswerLog(
                id = "ans_$i",
                ts = now + i,
                day = "2026-10-24",
                mode = "add",
                op = "add",
                category = "add_1d",
                difficulty = "easy",
                level = 2,
                operands = listOf(3, 4),
                prompt = "3 + 4",
                expected = 7L,
                given = 7L,
                correct = true,
                timeMs = 1200L,
                attempts = 1,
                source = "normal",
                sessionId = "sess_1"
            )
        }

        val shards = com.example.data.DailySyncGate.buildDayDocShards(
            userId = "uid_123",
            day = "2026-10-24",
            logs = logs,
            extraAggregates = emptyList()
        )
        assertEquals(2, shards.size)
        assertEquals("2026-10-24", shards[0].documentId)
        assertEquals("2026-10-24", shards[0].day)
        assertEquals(1, shards[0].part)
        assertEquals(1500, shards[0].answers.size)
        assertEquals(1650, shards[0].totals["questions"]?.toInt())

        assertEquals("2026-10-24_2", shards[1].documentId)
        assertEquals("2026-10-24", shards[1].day)
        assertEquals(2, shards[1].part)
        assertEquals(150, shards[1].answers.size)

        // Verify WriteBatch planner caps total ops at 500
        val manyScoreDocs = (1..520).map { i ->
            com.example.data.CloudScoreDoc(
                documentId = "score_$i",
                userId = "uid_123",
                mode = "ADDITION",
                difficulty = "EASY",
                count = 10,
                correct = 10,
                timeMs = 12000L,
                accuracy = 1.0,
                ts = now + i
            )
        }
        val plan = com.example.data.DailySyncGate.planWriteBatch(
            profile = com.example.data.CloudUserProfile(userId = "uid_123", displayName = "Alex"),
            dailyProgress = com.example.data.CloudDailyProgress(userId = "uid_123", streakCurrent = 3),
            dayDocGroups = mapOf("2026-10-24" to shards),
            scores = manyScoreDocs
        )
        assertEquals(500, plan.totalOpsCount)
        assertTrue(plan.profile != null)
        assertTrue(plan.dailyProgress != null)
        assertEquals(2, plan.dayDocs.size)
        assertEquals(496, plan.scores.size)
    }

    @Test
    fun `AppearanceMode defaults to LIGHT on first install and resolves dark mode accurately`() {
        val defaultSettings = com.example.model.SettingsState()
        assertEquals(com.example.model.AppearanceMode.LIGHT, defaultSettings.appearanceMode)

        assertFalse(
            com.example.ui.theme.resolveIsDarkMode(
                com.example.model.AppearanceMode.LIGHT,
                com.example.model.AppThemeId.LIGHT,
                systemDark = true
            )
        )
        assertTrue(
            com.example.ui.theme.resolveIsDarkMode(
                com.example.model.AppearanceMode.DARK,
                com.example.model.AppThemeId.LIGHT,
                systemDark = false
            )
        )
        assertTrue(
            com.example.ui.theme.resolveIsDarkMode(
                com.example.model.AppearanceMode.SYSTEM,
                com.example.model.AppThemeId.LIGHT,
                systemDark = true
            )
        )
        assertFalse(
            com.example.ui.theme.resolveIsDarkMode(
                com.example.model.AppearanceMode.SYSTEM,
                com.example.model.AppThemeId.LIGHT,
                systemDark = false
            )
        )
    }
}




