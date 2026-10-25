package com.example.core

import com.example.model.AnsweredProblem
import com.example.model.DailyState
import com.example.model.DayStatus
import com.example.model.LevelChangeInfo
import com.example.model.MathOp
import com.example.model.Problem
import com.example.model.SkillOp
import com.example.model.SkillState
import com.example.model.StreakState
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToLong

/**
 * Pure, unit-tested Adaptive Daily Workout Engine:
 * - Deterministic seed = "date|installId"
 * - Weakness scoring & weighted operation selection
 * - Warm-up (first 2 at level - 1), middle offsets [-1, 0, 0, +1], finisher (last at level + 1)
 * - No more than 2 consecutive identical operations
 * - Carry/borrow-aware question generation across levels 1..10
 * - EMA updates (alpha = 0.2), level up/down rules, and 7-day Streak Freeze logic
 */
object DailyEngine {

    private val TARGET_TIMES_MS: Map<SkillOp, IntArray> = mapOf(
        SkillOp.ADD to intArrayOf(3000, 3200, 3500, 4000, 4500, 5000, 6000, 7500, 9000, 11000),
        SkillOp.SUB to intArrayOf(3200, 3500, 3800, 4500, 5000, 5500, 6500, 8000, 9500, 11500),
        SkillOp.MUL to intArrayOf(3000, 3200, 3500, 5000, 6000, 7000, 9000, 11000, 13000, 15000),
        SkillOp.DIV to intArrayOf(3200, 3500, 3800, 5500, 6500, 7500, 9500, 11500, 13500, 15500),
        SkillOp.SQRT to intArrayOf(2500, 2800, 3000, 4000, 4500, 5000, 6500, 7500, 9000, 10500),
        SkillOp.POW to intArrayOf(2500, 2800, 3000, 4000, 5000, 6500, 8000, 9500, 11000, 13000)
    )

    fun targetTimeMs(op: SkillOp, level: Int): Long {
        val arr = TARGET_TIMES_MS[op] ?: return 5000L
        val idx = (level - 1).coerceIn(0, arr.lastIndex)
        return arr[idx].toLong()
    }

    /**
     * Builds the deterministic daily question set for [date] and [installId].
     */
    fun buildDailySet(
        date: String,
        installId: String,
        setLength: Int,
        enabledOps: Set<SkillOp>,
        skills: Map<SkillOp, SkillState>,
        recentQuestionKeys: Collection<String> = emptySet(),
        needsWorkCategories: Set<String> = emptySet()
    ): List<Problem> {
        val activeOps = if (enabledOps.size >= 2) {
            SkillOp.entries.filter { it in enabledOps }
        } else {
            listOf(SkillOp.ADD, SkillOp.SUB)
        }

        val prng = SeededPrng("$date|$installId|$setLength|${activeOps.joinToString(",") { it.name }}")

        // Map Needs-Work categories to their parent SkillOp
        val needsWorkByOp: Map<SkillOp, List<String>> = needsWorkCategories
            .map { it to CategoryRegistry.getCategoryInfo(it) }
            .mapNotNull { (catId, info) ->
                val sOp = when (info.mathOp) {
                    MathOp.ADD -> SkillOp.ADD
                    MathOp.SUB -> SkillOp.SUB
                    MathOp.MUL -> SkillOp.MUL
                    MathOp.DIV -> SkillOp.DIV
                    MathOp.SQRT -> SkillOp.SQRT
                    MathOp.POW, MathOp.CBRT -> SkillOp.POW
                    null -> null
                }
                if (sOp != null) sOp to catId else null
            }
            .groupBy({ it.first }, { it.second })

        // 1. Compute weakness score per enabled op (also biasing toward operations with Needs-Work categories)
        val rawWeaknesses = activeOps.map { op ->
            val st = skills[op] ?: SkillState(op = op)
            val target = targetTimeMs(op, st.level).toDouble()
            val slowness = if (st.totalAnswered > 0) {
                ((st.recentTimeMs - target) / target).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val baseWeakness = (1.0 - st.recentAccuracy.coerceIn(0.0, 1.0)) + 0.5 * slowness
            val lowSampleBoost = if (st.totalAnswered < 10) 0.15 else 0.0
            val needsWorkBoost = if (!needsWorkByOp[op].isNullOrEmpty()) 0.25 else 0.0
            (baseWeakness + lowSampleBoost + needsWorkBoost).coerceAtLeast(0.05)
        }

        val weaknessSum = rawWeaknesses.sum().takeIf { it > 0.0 } ?: 1.0
        val normalizedWeaknesses = rawWeaknesses.map { it / weaknessSum }
        val uniform = 1.0 / activeOps.size.toDouble()

        // 2. Op weight = 0.4 * uniform + 0.6 * normalizedWeakness; normalize to sum 1
        val rawWeights = normalizedWeaknesses.map { w -> 0.4 * uniform + 0.6 * w }
        val weightSum = rawWeights.sum().takeIf { it > 0.0 } ?: 1.0
        val weights = rawWeights.map { it / weightSum }

        // 3. Guarantee at least one question per op if setLength >= activeOps.size
        val chosenOps = mutableListOf<SkillOp>()
        if (setLength >= activeOps.size) {
            chosenOps.addAll(activeOps)
        }
        while (chosenOps.size < setLength) {
            val idx = prng.weightedIndex(weights)
            chosenOps.add(activeOps[idx])
        }

        val shuffledOps = prng.shuffle(chosenOps).toMutableList()
        val orderedOps = enforceMaxTwoInARow(shuffledOps, activeOps, prng)

        // 4. Per-question level & 5. De-duplication
        val usedKeys = recentQuestionKeys.toMutableSet()
        val problems = mutableListOf<Problem>()
        val middleOffsets = listOf(-1, 0, 0, 1)

        for (i in 0 until setLength) {
            val skillOp = orderedOps[i]
            val baseLevel = (skills[skillOp]?.level ?: 2).coerceIn(1, 10)
            val offset = when {
                i < 2 -> -1 // Warm-up: first 2 questions = level - 1
                i == setLength - 1 -> 1 // Finisher: last question = level + 1
                else -> prng.pick(middleOffsets)
            }
            val qLevel = (baseLevel + offset).coerceIn(1, 10)
            val weakCatsForOp = needsWorkByOp[skillOp].orEmpty()

            var generated: Problem? = null
            for (attempt in 0 until 20) {
                val candidate = if (weakCatsForOp.isNotEmpty() && i >= 2 && i % 2 == 0) {
                    val targetCat = weakCatsForOp[i % weakCatsForOp.size]
                    CategoryRegistry.generateProblemForCategory(targetCat, qLevel, prng).copy(
                        id = "daily_$i",
                        level = qLevel
                    )
                } else {
                    generateSkillQuestion(skillOp, qLevel, i, prng)
                }
                if (candidate.normalizedKey !in usedKeys || attempt == 19) {
                    generated = candidate
                    usedKeys.add(candidate.normalizedKey)
                    break
                }
            }
            problems.add(generated ?: generateSkillQuestion(skillOp, qLevel, i, prng))
        }

        return problems
    }

    /**
     * Fixes the list of operations so the same operation never appears more than 2 times in a row.
     */
    fun enforceMaxTwoInARow(
        ops: MutableList<SkillOp>,
        activeOps: List<SkillOp>,
        prng: SeededPrng
    ): List<SkillOp> {
        if (activeOps.size < 2 || ops.size < 3) return ops
        for (pass in 0 until 5) {
            for (i in 2 until ops.size) {
                if (ops[i] == ops[i - 1] && ops[i] == ops[i - 2]) {
                    // Find a swap target j that has a different op and doesn't create a triple
                    var swapped = false
                    for (j in ops.indices) {
                        if (ops[j] != ops[i] && canPlaceAt(ops, j, ops[i]) && canPlaceAt(ops, i, ops[j])) {
                            val tmp = ops[i]
                            ops[i] = ops[j]
                            ops[j] = tmp
                            swapped = true
                            break
                        }
                    }
                    if (!swapped) {
                        val alternatives = activeOps.filter { it != ops[i] }
                        if (alternatives.isNotEmpty()) {
                            ops[i] = prng.pick(alternatives)
                        }
                    }
                }
            }
        }
        return ops
    }

    private fun canPlaceAt(list: List<SkillOp>, index: Int, candidate: SkillOp): Boolean {
        val left2 = index >= 2 && list[index - 1] == candidate && list[index - 2] == candidate
        val right2 = index <= list.size - 3 && list[index + 1] == candidate && list[index + 2] == candidate
        val mid = index >= 1 && index <= list.size - 2 && list[index - 1] == candidate && list[index + 1] == candidate
        return !left2 && !right2 && !mid
    }

    /**
     * Generates a single question for [skillOp] at [level] (1..10) according to specification.
     */
    fun generateSkillQuestion(
        skillOp: SkillOp,
        level: Int,
        index: Int,
        prng: SeededPrng
    ): Problem {
        val lvl = level.coerceIn(1, 10)
        return when (skillOp) {
            SkillOp.ADD -> generateAddByLevel(lvl, index, prng)
            SkillOp.SUB -> generateSubByLevel(lvl, index, prng)
            SkillOp.MUL -> generateMulByLevel(lvl, index, prng)
            SkillOp.DIV -> generateDivByLevel(lvl, index, prng)
            SkillOp.SQRT -> generateSqrtByLevel(lvl, index, prng)
            SkillOp.POW -> generatePowByLevel(lvl, index, prng)
        }
    }

    private fun generateAddByLevel(level: Int, index: Int, prng: SeededPrng): Problem {
        var a = 1
        var b = 1
        for (attempt in 0 until 35) {
            when (level) {
                1, 2, 3 -> {
                    val maxSum = when (level) {
                        1 -> 7
                        2 -> 9
                        else -> 12
                    }
                    a = prng.int(1, maxSum - 1)
                    b = prng.int(1, (maxSum - a).coerceAtLeast(1))
                    if (level <= 2 && a + b > 9) b = (9 - a).coerceAtLeast(1)
                    break
                }
                4 -> {
                    // Two-digit + two-digit, no carry
                    val a1 = prng.int(1, 7)
                    val a0 = prng.int(0, 8)
                    val b1 = prng.int(1, (8 - a1).coerceAtLeast(1))
                    val b0 = prng.int(0, (9 - a0).coerceAtLeast(0))
                    a = a1 * 10 + a0
                    b = b1 * 10 + b0
                    break
                }
                5, 6 -> {
                    // Two-digit + two-digit, 1 carry
                    a = prng.int(14, 79)
                    b = prng.int(14, 79)
                    if (countAdditionCarries(a, b) >= 1) break
                }
                7, 8 -> {
                    // Three-digit + two-digit with carries
                    a = prng.int(115, 799)
                    b = prng.int(25, 98)
                    if (countAdditionCarries(a, b) >= 1) break
                }
                else -> {
                    // L9-10: Three-digit + three-digit with 1-2 carries
                    a = prng.int(125, 899)
                    b = prng.int(125, 899)
                    if (countAdditionCarries(a, b) >= 1) break
                }
            }
        }
        return QuestionGenerator.buildStandardProblem(
            id = "daily_$index",
            op = MathOp.ADD,
            a = a,
            b = b,
            answer = a.toLong() + b.toLong(),
            level = level
        )
    }

    private fun generateSubByLevel(level: Int, index: Int, prng: SeededPrng): Problem {
        var a = 5
        var b = 2
        for (attempt in 0 until 35) {
            when (level) {
                1, 2, 3 -> {
                    a = prng.int(3, if (level == 1) 7 else 9)
                    b = prng.int(1, a - 1)
                    break
                }
                4 -> {
                    // Two-digit no borrow
                    val a1 = prng.int(2, 9)
                    val a0 = prng.int(2, 9)
                    val b1 = prng.int(1, a1 - 1)
                    val b0 = prng.int(0, a0)
                    a = a1 * 10 + a0
                    b = b1 * 10 + b0
                    break
                }
                5, 6 -> {
                    // Two-digit one borrow
                    val a1 = prng.int(3, 9)
                    val a0 = prng.int(0, 7)
                    val b1 = prng.int(1, a1 - 1)
                    val b0 = prng.int(a0 + 1, 9)
                    a = a1 * 10 + a0
                    b = b1 * 10 + b0
                    break
                }
                7, 8 -> {
                    // Three-digit - two-digit with borrow
                    a = prng.int(110, 650)
                    b = prng.int(18, 98)
                    if (a > b && countSubtractionBorrows(a, b) >= 1) break
                }
                else -> {
                    // L9-10: Three-digit - three-digit with >= 2 borrows
                    val a2 = prng.int(4, 9)
                    val a1 = prng.int(0, 6)
                    val a0 = prng.int(0, 6)
                    val b2 = prng.int(1, a2 - 1)
                    val b1 = prng.int(a1 + 1, 9)
                    val b0 = prng.int(a0 + 1, 9)
                    a = a2 * 100 + a1 * 10 + a0
                    b = b2 * 100 + b1 * 10 + b0
                    break
                }
            }
        }
        if (b > a) {
            val tmp = a
            a = b
            b = tmp
        }
        return QuestionGenerator.buildStandardProblem(
            id = "daily_$index",
            op = MathOp.SUB,
            a = a,
            b = b,
            answer = (a - b).toLong(),
            level = level
        )
    }

    private fun generateMulByLevel(level: Int, index: Int, prng: SeededPrng): Problem {
        val (a, b) = when (level) {
            1 -> prng.int(2, 5) to prng.int(2, 10)
            2 -> prng.int(2, 7) to prng.int(2, 10)
            3 -> prng.int(2, 10) to prng.int(2, 10)
            4 -> prng.int(11, 25) to prng.int(2, 5)
            5 -> prng.int(12, 39) to prng.int(2, 7)
            6 -> prng.int(12, 59) to prng.int(2, 9)
            7 -> prng.int(11, 20) to prng.int(11, 50)
            8 -> prng.int(11, 20) to prng.int(11, 99)
            9 -> prng.int(21, 59) to prng.int(12, 59)
            else -> prng.int(25, 99) to prng.int(21, 99)
        }
        return QuestionGenerator.buildStandardProblem(
            id = "daily_$index",
            op = MathOp.MUL,
            a = a,
            b = b,
            answer = a.toLong() * b.toLong(),
            level = level
        )
    }

    private fun generateDivByLevel(level: Int, index: Int, prng: SeededPrng): Problem {
        // Built from multiplication: answer = a, divisor = b, dividend = a * b (always exact)
        val mulProblem = generateMulByLevel(level, index, prng)
        val answer = mulProblem.a
        val divisor = mulProblem.b
        val dividend = answer * divisor
        return QuestionGenerator.buildStandardProblem(
            id = "daily_$index",
            op = MathOp.DIV,
            a = dividend,
            b = divisor,
            answer = answer.toLong(),
            level = level
        )
    }

    private fun generateSqrtByLevel(level: Int, index: Int, prng: SeededPrng): Problem {
        val (minR, maxR) = when (level) {
            1, 2 -> 2 to 8
            3, 4 -> 4 to 12
            5, 6 -> 8 to 20
            7, 8 -> 12 to 32
            else -> 16 to 50
        }
        val root = prng.int(minR, maxR)
        val sq = root * root
        return Problem(
            id = "daily_$index",
            op = MathOp.SQRT,
            a = sq,
            b = 2,
            answer = root.toLong(),
            displayBase = "√$sq",
            superscriptExponent = null,
            level = level,
            normalizedKey = "SQRT:$sq"
        )
    }

    private fun generatePowByLevel(level: Int, index: Int, prng: SeededPrng): Problem {
        val (base, exp) = when (level) {
            1, 2 -> prng.int(2, 7) to 2
            3, 4 -> prng.int(5, 12) to 2
            5, 6 -> prng.int(11, 20) to 2
            7, 8 -> prng.int(2, 9) to 3
            else -> if (prng.next() < 0.5) prng.int(21, 40) to 2 else prng.int(2, 8) to 4
        }
        var ans = 1L
        repeat(exp) { ans *= base.toLong() }
        return Problem(
            id = "daily_$index",
            op = MathOp.POW,
            a = base,
            b = exp,
            answer = ans,
            displayBase = "$base",
            superscriptExponent = "$exp",
            level = level,
            normalizedKey = "POW:$base^$exp"
        )
    }

    fun countAdditionCarries(a: Int, b: Int): Int {
        var x = a
        var y = b
        var carry = 0
        var count = 0
        while (x > 0 || y > 0) {
            val sum = (x % 10) + (y % 10) + carry
            if (sum >= 10) {
                carry = 1
                count++
            } else {
                carry = 0
            }
            x /= 10
            y /= 10
        }
        return count
    }

    fun countSubtractionBorrows(a: Int, b: Int): Int {
        var x = a
        var y = b
        var borrow = 0
        var count = 0
        while (x > 0 || y > 0) {
            val d1 = (x % 10) - borrow
            val d2 = y % 10
            if (d1 < d2) {
                borrow = 1
                count++
            } else {
                borrow = 0
            }
            x /= 10
            y /= 10
        }
        return count
    }

    /**
     * Evaluates a completed Daily Workout set:
     * - Updates skill levels (requires >= 2 answers for that op today:
     *   accuracy >= 0.9 && avgTime <= target -> level + 1;
     *   accuracy < 0.6 -> level - 1; else unchanged)
     * - Updates EMAs with alpha = 0.2
     * - Updates Streak & Freeze state (including 7-day milestones and single-day missed freeze rescue)
     */
    /**
     * Updates the 6 adaptive skill levels (Addition, Subtraction, Multiplication, Division, Square Root, Exponents; Lv 1-10)
     * from any quiz session's answers using accuracy and speed targets.
     */
    fun updateSkillsFromQuizAnswers(
        currentSkills: Map<SkillOp, SkillState>,
        sessionAnswers: List<AnsweredProblem>
    ): Pair<Map<SkillOp, SkillState>, List<LevelChangeInfo>> {
        val alpha = 0.2
        val updatedSkills = currentSkills.toMutableMap()
        val levelChanges = mutableListOf<LevelChangeInfo>()

        val byOp = sessionAnswers.mapNotNull { answered ->
            val skillOp = SkillOp.entries.firstOrNull { it.mathOp == answered.problem.op }
            if (skillOp != null) skillOp to answered else null
        }.groupBy({ it.first }, { it.second })

        for ((skillOp, answers) in byOp) {
            if (answers.isEmpty()) continue
            val prev = updatedSkills[skillOp] ?: SkillState(op = skillOp)
            val correctCount = answers.count { it.isCorrect }
            val sessionAcc = correctCount.toDouble() / answers.size.toDouble()
            val sessionAvgTime = answers.map { it.timeMs }.average()
            val target = targetTimeMs(skillOp, prev.level)

            val newAcc = if (prev.totalAnswered == 0) {
                sessionAcc
            } else {
                (1.0 - alpha) * prev.recentAccuracy + alpha * sessionAcc
            }
            val newTime = if (prev.totalAnswered == 0) {
                sessionAvgTime
            } else {
                (1.0 - alpha) * prev.recentTimeMs + alpha * sessionAvgTime
            }

            var newLevel = prev.level
            if (answers.size >= 2) {
                if (sessionAcc >= 0.9 && sessionAvgTime <= target.toDouble()) {
                    newLevel = (prev.level + 1).coerceAtMost(10)
                } else if (sessionAcc < 0.6) {
                    newLevel = (prev.level - 1).coerceAtLeast(1)
                }
            }

            updatedSkills[skillOp] = prev.copy(
                level = newLevel,
                recentAccuracy = newAcc,
                recentTimeMs = newTime,
                totalAnswered = prev.totalAnswered + answers.size
            )

            levelChanges.add(
                LevelChangeInfo(
                    op = skillOp,
                    oldLevel = prev.level,
                    newLevel = newLevel,
                    correctCount = correctCount,
                    totalCount = answers.size,
                    avgTimeMs = sessionAvgTime.roundToLong(),
                    targetTimeMs = target
                )
            )
        }
        return updatedSkills to levelChanges
    }

    fun completeDailyWorkout(
        state: DailyState,
        todayDate: String = state.date
    ): DailyState {
        if (state.isCompletedToday) return state

        val (updatedSkills, levelChanges) = updateSkillsFromQuizAnswers(state.skills, state.todayAnswers)
        val updatedStreak = updateStreakOnCompletion(state.streak, todayDate)

        val todayKeys = state.todayProblems.map { it.normalizedKey }
        val combinedRecentKeys = (state.recentQuestionKeys + todayKeys).takeLast(120)

        return state.copy(
            skills = updatedSkills,
            streak = updatedStreak,
            isCompletedToday = true,
            lastLevelChanges = levelChanges,
            recentQuestionKeys = combinedRecentKeys,
            rebuiltNotice = false
        )
    }

    fun updateStreakOnCompletion(streak: StreakState, todayDateStr: String): StreakState {
        if (streak.lastCompletedDate == todayDateStr) return streak

        val today = runCatching { LocalDate.parse(todayDateStr) }.getOrNull()
        val lastDate = streak.lastCompletedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        var newCurrent = 1
        var freezes = streak.freezesAvailable
        val frozenDates = streak.frozenDates.toMutableSet()
        var savedByFreeze = false

        if (today != null && lastDate != null) {
            val daysBetween = ChronoUnit.DAYS.between(lastDate, today)
            when {
                daysBetween == 1L -> {
                    newCurrent = streak.current + 1
                }
                daysBetween == 2L && freezes > 0 -> {
                    // Auto-consume 1 freeze for the single missed day (yesterday)
                    freezes -= 1
                    val missedDay = today.minusDays(1).toString()
                    frozenDates.add(missedDay)
                    newCurrent = streak.current + 1
                    savedByFreeze = true
                }
                else -> {
                    newCurrent = 1
                }
            }
        } else {
            newCurrent = 1
        }

        // Every 7-day milestone grants +1 freeze (max 2 stored)
        if (newCurrent > 0 && newCurrent % 7 == 0) {
            freezes = (freezes + 1).coerceAtMost(2)
        }

        val newBest = maxOf(streak.best, newCurrent)
        val completed = streak.completedDates + todayDateStr

        return streak.copy(
            current = newCurrent,
            best = newBest,
            freezesAvailable = freezes,
            lastCompletedDate = todayDateStr,
            frozenDates = frozenDates,
            completedDates = completed,
            savedByFreezeToday = savedByFreeze
        )
    }

    /**
     * Checks if a freeze should be auto-applied on app launch when yesterday was missed
     * and the day before yesterday was completed.
     */
    fun checkAndApplyFreezeForNewDay(streak: StreakState, todayDateStr: String): StreakState {
        val today = runCatching { LocalDate.parse(todayDateStr) }.getOrNull() ?: return streak
        val lastDate = streak.lastCompletedDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return streak
        val daysBetween = ChronoUnit.DAYS.between(lastDate, today)
        val yesterdayStr = today.minusDays(1).toString()

        if (daysBetween == 2L && streak.freezesAvailable > 0 && yesterdayStr !in streak.frozenDates) {
            return streak.copy(
                freezesAvailable = streak.freezesAvailable - 1,
                frozenDates = streak.frozenDates + yesterdayStr,
                savedByFreezeToday = true
            )
        } else if (daysBetween > 2L) {
            return streak.copy(
                current = 0,
                savedByFreezeToday = false
            )
        }
        return streak
    }

    /**
     * Computes the Monday..Sunday week strip statuses for the week containing [todayDateStr].
     */
    fun buildWeekStrip(
        streak: StreakState,
        todayDateStr: String
    ): List<Pair<String, DayStatus>> {
        val today = runCatching { LocalDate.parse(todayDateStr) }.getOrElse { LocalDate.now() }
        val dayOfWeekVal = today.dayOfWeek.value // 1 = Mon .. 7 = Sun
        val monday = today.minusDays((dayOfWeekVal - 1).toLong())
        val labels = listOf("M", "T", "W", "T", "F", "S", "S")

        return (0..6).map { offset ->
            val d = monday.plusDays(offset.toLong())
            val dStr = d.toString()
            val status = when {
                dStr in streak.completedDates -> DayStatus.DONE
                dStr in streak.frozenDates -> DayStatus.FROZEN
                d.isEqual(today) -> DayStatus.TODAY
                d.isAfter(today) -> DayStatus.FUTURE
                else -> DayStatus.MISSED
            }
            labels[offset] to status
        }
    }
}
