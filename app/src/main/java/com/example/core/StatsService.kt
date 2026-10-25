package com.example.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class InsightsDateRange(val daysBack: Int?, val labelKey: String) {
    TODAY(0, "insights.range_today"),
    DAYS_7(7, "insights.range_7d"),
    DAYS_30(30, "insights.range_30d"),
    ALL_TIME(null, "insights.range_all")
}

enum class CategorySortOrder(val labelKey: String) {
    WEAKEST("insights.sort_weakest"),
    SLOWEST("insights.sort_slowest"),
    MOST_PRACTICED("insights.sort_most_practiced"),
    LEAST_PRACTICED("insights.sort_least_practiced"),
    MOST_IMPROVED("insights.sort_most_improved")
}

enum class SpeedClass(val labelKey: String, val badgeSymbol: String) {
    FAST("insights.speed_fast", "⚡"),
    NORMAL("insights.speed_normal", "●"),
    SLOW("insights.speed_slow", "🐢"),
    NOT_ENOUGH_DATA("insights.not_enough_data", "—")
}

enum class AccuracyClass(val labelKey: String, val badgeSymbol: String) {
    STRONG("insights.acc_strong", "✓"),
    OK("insights.acc_ok", "~"),
    WEAK("insights.acc_weak", "!"),
    NOT_ENOUGH_DATA("insights.not_enough_data", "—")
}

enum class QuadrantClass(val labelKey: String) {
    MASTERED("insights.quad_mastered"),       // Fast + Accurate
    CAREFUL("insights.quad_careful"),         // Slow + Accurate
    GUESSING("insights.quad_guessing"),       // Fast + Inaccurate
    NEEDS_WORK("insights.quad_needs_work")    // Slow + Inaccurate
}

enum class StrengthWeaknessGroup {
    STRONG,
    NEEDS_PRACTICE,
    NOT_ENOUGH_DATA
}

data class CategoryStat(
    val categoryId: String,
    val op: String,
    val attempts: Int,
    val correct: Int,
    val accuracy: Double,
    val avgTimeMs: Long,
    val medianTimeMs: Long,
    val p90TimeMs: Long,
    val lastSeenTs: Long,
    val trendAccuracyDelta: Double, // last 20 vs previous 20 accuracy delta
    val trendTimeDeltaMs: Long,     // last 20 vs previous 20 avgTimeMs delta (negative = faster)
    val speedClass: SpeedClass,
    val accuracyClass: AccuracyClass,
    val quadrant: QuadrantClass,
    val weaknessScore: Double,
    val strengthGroup: StrengthWeaknessGroup = StrengthWeaknessGroup.NOT_ENOUGH_DATA
) {
    val hasEnoughData: Boolean get() = attempts >= 5
    val hasTenAttempts: Boolean get() = attempts >= 10
}

data class DailyVolumePoint(
    val day: String,
    val attempts: Int,
    val correct: Int,
    val wrong: Int,
    val totalTimeMs: Long,
    val avgTimeMs: Long,
    val accuracy: Double,
    val movingAvgAccuracy7d: Double,
    val modesPlayed: Set<String>
)

data class OperationRadarPoint(
    val opCode: String,
    val attempts: Int,
    val accuracy: Double,
    val avgTimeMs: Long,
    val speedScoreNormalized: Double // 0.0 (slow) .. 1.0 (fast)
)

data class HeatmapCell(
    val opCode: String,
    val bucketIndex: Int, // 0=Easy(L1-2), 1=Medium(L3-4), 2=Hard(L5-6), 3=Challenging(L7-8), 4=Expert(L9-10)
    val bucketLabel: String,
    val attempts: Int,
    val correct: Int,
    val accuracy: Double
)

data class InsightCardData(
    val type: String, // "slowest", "drop", "fast_op", "strong_cat"
    val i18nKey: String,
    val params: Map<String, String>,
    val categoryId: String? = null
)

data class CategoryDetailSnapshot(
    val stat: CategoryStat,
    val dailyPoints: List<DailyVolumePoint>,
    val recentMistakes: List<AnswerLog>
)

data class InsightsDashboardSnapshot(
    val dateRange: InsightsDateRange,
    val opFilter: String?,
    val totalQuestionsToday: Int,
    val totalQuestionsThisWeek: Int,
    val totalAttemptsInRange: Int,
    val totalCorrectInRange: Int,
    val overallAccuracy: Double,
    val overallAvgTimeMs: Long,
    val overallMedianTimeMs: Long,
    val practiceStreakCurrent: Int,
    val practiceStreakBest: Int,
    val practiceStreakState: com.example.model.StreakState = com.example.model.StreakState(),
    val speedChangePctVsLastWeek: Int?, // positive = % faster than last week
    val dailySeries: List<DailyVolumePoint>,
    val categoryStats: List<CategoryStat>,
    val strongCategories: List<CategoryStat> = emptyList(),
    val needsPracticeCategories: List<CategoryStat> = emptyList(),
    val notEnoughDataCategories: List<CategoryStat> = emptyList(),
    val radarPoints: List<OperationRadarPoint>,
    val heatmapCells: List<HeatmapCell>,
    val insightCards: List<InsightCardData>
)

object StatsService {

    private const val HALF_LIFE_DAYS = 14.0
    private val DECAY_LAMBDA = ln(2.0) / HALF_LIFE_DAYS
    val RADAR_OPS = listOf("add", "sub", "mul", "div", "sqrt", "pow")

    /**
     * Computes the median of a list of valid durations (excluding > 60000ms).
     */
    fun computeMedianMs(timesMs: List<Long>): Long {
        val valid = timesMs.filter { it in 1..AnswerLogService.MAX_SPEED_TIME_MS }.sorted()
        if (valid.isEmpty()) return 3000L
        val mid = valid.size / 2
        return if (valid.size % 2 == 1) {
            valid[mid]
        } else {
            (valid[mid - 1] + valid[mid]) / 2L
        }
    }

    fun computeP90Ms(timesMs: List<Long>): Long {
        val valid = timesMs.filter { it in 1..AnswerLogService.MAX_SPEED_TIME_MS }.sorted()
        if (valid.isEmpty()) return 5000L
        val idx = ((valid.size - 1) * 0.90).roundToInt().coerceIn(0, valid.lastIndex)
        return valid[idx]
    }

    /**
     * Speed class relative to the user's own overall median:
     * Fast (< 0.8 * myMedian), Normal, Slow (> 1.25 * myMedian).
     * Categories with < 5 attempts return NOT_ENOUGH_DATA.
     */
    fun classifySpeed(avgTimeMs: Long, myMedianMs: Long, attempts: Int): SpeedClass {
        if (attempts < 5) return SpeedClass.NOT_ENOUGH_DATA
        val safeMedian = myMedianMs.coerceAtLeast(500L).toDouble()
        return when {
            avgTimeMs < safeMedian * 0.80 -> SpeedClass.FAST
            avgTimeMs > safeMedian * 1.25 -> SpeedClass.SLOW
            else -> SpeedClass.NORMAL
        }
    }

    /**
     * Accuracy class:
     * Strong >= 90%, OK 70-89%, Weak < 70%.
     * Categories with < 5 attempts return NOT_ENOUGH_DATA.
     */
    fun classifyAccuracy(accuracy: Double, attempts: Int): AccuracyClass {
        if (attempts < 5) return AccuracyClass.NOT_ENOUGH_DATA
        return when {
            accuracy >= 0.90 -> AccuracyClass.STRONG
            accuracy >= 0.70 -> AccuracyClass.OK
            else -> AccuracyClass.WEAK
        }
    }

    /**
     * Quadrant classification (x = avg time, y = accuracy):
     * - Mastered (fast/normal + accurate >= 85%)
     * - Careful (slow + accurate >= 85%)
     * - Guessing (fast/normal + inaccurate < 85%)
     * - Needs work (slow + inaccurate < 85%)
     */
    fun classifyQuadrant(avgTimeMs: Long, myMedianMs: Long, accuracy: Double): QuadrantClass {
        val isSlow = avgTimeMs > (myMedianMs.coerceAtLeast(500L) * 1.10)
        val isAccurate = accuracy >= 0.85
        return when {
            !isSlow && isAccurate -> QuadrantClass.MASTERED
            isSlow && isAccurate -> QuadrantClass.CAREFUL
            !isSlow && !isAccurate -> QuadrantClass.GUESSING
            else -> QuadrantClass.NEEDS_WORK
        }
    }

    /**
     * Weakness score per category:
     * 0.6 * (1 - accuracy) + 0.4 * clamp((avgTime - myMedian) / myMedian, 0, 1)
     * with exponential recency weight (half-life ~14 days) so recent performance matters more.
     */
    fun computeWeaknessScore(
        logsForCategory: List<AnswerLog>,
        aggregatesForCategory: List<DailyCategoryAggregate>,
        myMedianMs: Long,
        todayStr: String
    ): Double {
        val today = runCatching { LocalDate.parse(todayStr) }.getOrElse { LocalDate.now() }
        val safeMedian = myMedianMs.coerceAtLeast(500L).toDouble()

        var weightedAttempts = 0.0
        var weightedCorrect = 0.0
        var weightedTimeSum = 0.0
        var weightedSpeedCount = 0.0

        for (log in logsForCategory) {
            val logDate = runCatching { LocalDate.parse(log.day) }.getOrElse { today }
            val ageDays = ChronoUnit.DAYS.between(logDate, today).coerceAtLeast(0L).toDouble()
            val weight = kotlin.math.exp(-DECAY_LAMBDA * ageDays)
            weightedAttempts += weight
            if (log.correct) weightedCorrect += weight
            if (log.timeMs in 1..AnswerLogService.MAX_SPEED_TIME_MS) {
                weightedTimeSum += log.timeMs * weight
                weightedSpeedCount += weight
            }
        }

        for (agg in aggregatesForCategory) {
            val aggDate = runCatching { LocalDate.parse(agg.day) }.getOrElse { today.minusDays(91) }
            val ageDays = ChronoUnit.DAYS.between(aggDate, today).coerceAtLeast(0L).toDouble()
            val weight = kotlin.math.exp(-DECAY_LAMBDA * ageDays)
            weightedAttempts += agg.attempts * weight
            weightedCorrect += agg.correct * weight
            if (agg.validSpeedCount > 0) {
                weightedTimeSum += agg.totalTimeMs * weight
                weightedSpeedCount += agg.validSpeedCount * weight
            }
        }

        if (weightedAttempts <= 0.0) return 0.0
        val recencyAcc = (weightedCorrect / weightedAttempts).coerceIn(0.0, 1.0)
        val recencyAvgTime = if (weightedSpeedCount > 0.0) {
            weightedTimeSum / weightedSpeedCount
        } else {
            safeMedian
        }
        val slowRatio = ((recencyAvgTime - safeMedian) / safeMedian).coerceIn(0.0, 1.0)
        return (0.6 * (1.0 - recencyAcc) + 0.4 * slowRatio).coerceIn(0.0, 1.0)
    }

    /**
     * Classifies a category into Strong, Needs practice, or Not enough data yet:
     * a) Strong — no need to practice: accuracy >= 90% and avgTime <= myMedian, >= 10 attempts.
     * b) Needs practice: >= 10 attempts and (accuracy < 70% OR avgTime > myMedian * 1.25, or not yet Strong).
     * c) Not enough data yet: < 10 attempts.
     */
    fun classifyStrengthWeakness(
        attempts: Int,
        accuracy: Double,
        avgTimeMs: Long,
        myMedianMs: Long
    ): StrengthWeaknessGroup {
        if (attempts < 10) return StrengthWeaknessGroup.NOT_ENOUGH_DATA
        val safeMedian = myMedianMs.coerceAtLeast(500L)
        if (accuracy >= 0.90 && avgTimeMs <= safeMedian) {
            return StrengthWeaknessGroup.STRONG
        }
        return StrengthWeaknessGroup.NEEDS_PRACTICE
    }

    /**
     * Computes full Practice Streak state (current, best, freezes earned every 7-day milestone up to max 2,
     * auto-used when a single day is missed, completedDates, frozenDates) directly from per-day answer counts.
     * A day counts when the user answers at least 10 questions in any mode.
     */
    fun computePracticeStreakStateFromDayCounts(
        questionsPerDay: Map<String, Int>,
        todayStr: String = LocalDate.now().toString()
    ): com.example.model.StreakState {
        val qualifyingDays = questionsPerDay.filterValues { it >= 10 }.keys
        if (qualifyingDays.isEmpty()) return com.example.model.StreakState()

        val today = runCatching { LocalDate.parse(todayStr) }.getOrElse { LocalDate.now() }
        val parsedQualifying = qualifyingDays.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        val firstDate = parsedQualifying.minOrNull() ?: return com.example.model.StreakState()

        var running = 0
        var best = 0
        var freezes = 0
        val completedDates = mutableSetOf<String>()
        val frozenDates = mutableSetOf<String>()
        var lastCompletedDate: String? = null
        var savedByFreezeToday = false
        val rewardedMilestones = mutableSetOf<Int>()

        var d = firstDate
        while (!d.isAfter(today)) {
            val dStr = d.toString()
            if (d in parsedQualifying) {
                completedDates.add(dStr)
                running += 1
                lastCompletedDate = dStr
                if (running > best) best = running
                if (running > 0 && running % 7 == 0 && running !in rewardedMilestones) {
                    rewardedMilestones.add(running)
                    freezes = (freezes + 1).coerceAtMost(2)
                }
            } else if (d.isBefore(today)) {
                val prevCompleted = d.minusDays(1) in parsedQualifying
                val nextCompletedOrToday = (d.plusDays(1) in parsedQualifying) || d.plusDays(1).isEqual(today)
                if (running > 0 && freezes > 0 && prevCompleted && nextCompletedOrToday) {
                    // Single missed day rescued by a streak freeze
                    freezes -= 1
                    frozenDates.add(dStr)
                    if (d.plusDays(1).isEqual(today)) {
                        savedByFreezeToday = true
                    }
                } else {
                    running = 0
                    rewardedMilestones.clear()
                }
            }
            d = d.plusDays(1)
        }

        return com.example.model.StreakState(
            current = running,
            best = maxOf(best, running),
            freezesAvailable = freezes,
            lastCompletedDate = lastCompletedDate,
            frozenDates = frozenDates,
            completedDates = completedDates,
            savedByFreezeToday = savedByFreezeToday
        )
    }

    /**
     * Computes full Practice Streak state directly from AnswerLogs + rolled-up DailyCategoryAggregates.
     */
    fun computePracticeStreakState(
        rawLogs: List<AnswerLog>,
        rolledAggregates: List<DailyCategoryAggregate> = emptyList(),
        todayStr: String = LocalDate.now().toString()
    ): com.example.model.StreakState {
        val rawDays = rawLogs.groupBy { it.day }.mapValues { it.value.size }
        val allTimeDayCounts = rawDays.toMutableMap()
        for (agg in rolledAggregates) {
            if (agg.day !in rawDays) {
                allTimeDayCounts[agg.day] = (allTimeDayCounts[agg.day] ?: 0) + agg.attempts
            }
        }
        return computePracticeStreakStateFromDayCounts(allTimeDayCounts, todayStr)
    }

    /**
     * Practice streak: any day with >= 10 answered questions counts.
     * Returns (currentPracticeStreak, bestPracticeStreak).
     */
    fun computePracticeStreak(
        questionsPerDay: Map<String, Int>,
        todayStr: String
    ): Pair<Int, Int> {
        val state = computePracticeStreakStateFromDayCounts(questionsPerDay, todayStr)
        return state.current to state.best
    }

    /**
     * Builds the full InsightsDashboardSnapshot from raw AnswerLogs + rolled-up DailyCategoryAggregates.
     */
    fun buildDashboardSnapshot(
        rawLogs: List<AnswerLog>,
        rolledAggregates: List<DailyCategoryAggregate>,
        dateRange: InsightsDateRange,
        opFilter: String? = null,
        todayStr: String = LocalDate.now().toString()
    ): InsightsDashboardSnapshot {
        val today = runCatching { LocalDate.parse(todayStr) }.getOrElse { LocalDate.now() }
        val minDateStr = dateRange.daysBack?.let { days ->
            if (days == 0) todayStr else today.minusDays((days - 1).toLong()).toString()
        }
        val weekStartStr = today.minusDays(6).toString()

        // All-time per-day question counts (for Practice Streak & Today/Week tiles)
        val rawDayCounts = rawLogs.groupBy { it.day }.mapValues { it.value.size }
        val allTimeDayCounts = rawDayCounts.toMutableMap()
        for (agg in rolledAggregates) {
            if (agg.day !in rawDayCounts) {
                allTimeDayCounts[agg.day] = (allTimeDayCounts[agg.day] ?: 0) + agg.attempts
            }
        }

        val totalToday = allTimeDayCounts[todayStr] ?: 0
        val totalThisWeek = allTimeDayCounts.entries
            .filter { it.key in weekStartStr..todayStr }
            .sumOf { it.value }
        val practiceStreakState = computePracticeStreakStateFromDayCounts(allTimeDayCounts, todayStr)
        val practiceCur = practiceStreakState.current
        val practiceBest = practiceStreakState.best

        // Filter logs & aggregates by dateRange and opFilter
        val filteredLogs = rawLogs.filter { log ->
            (minDateStr == null || log.day >= minDateStr) &&
                (opFilter == null || log.op == opFilter)
        }
        val filteredAggs = rolledAggregates.filter { agg ->
            (minDateStr == null || agg.day >= minDateStr) &&
                (opFilter == null || agg.op == opFilter)
        }

        // Overall median & average time across all valid logs in range (fallback to all logs if range empty)
        val validTimesInRange = filteredLogs.map { it.timeMs }.filter { it in 1..AnswerLogService.MAX_SPEED_TIME_MS }
        val allValidTimes = rawLogs.map { it.timeMs }.filter { it in 1..AnswerLogService.MAX_SPEED_TIME_MS }
        val myMedianMs = computeMedianMs(validTimesInRange.ifEmpty { allValidTimes })

        val totalAttemptsInRange = filteredLogs.size + filteredAggs.sumOf { it.attempts }
        val totalCorrectInRange = filteredLogs.count { it.correct } + filteredAggs.sumOf { it.correct }
        val overallAccuracy = if (totalAttemptsInRange > 0) {
            totalCorrectInRange.toDouble() / totalAttemptsInRange.toDouble()
        } else 0.0

        val totalSpeedTimeMs = validTimesInRange.sum() + filteredAggs.sumOf { it.totalTimeMs }
        val totalSpeedCount = validTimesInRange.size + filteredAggs.sumOf { it.validSpeedCount }
        val overallAvgTimeMs = if (totalSpeedCount > 0) {
            (totalSpeedTimeMs.toDouble() / totalSpeedCount.toDouble()).roundToLong()
        } else 0L

        // Per-category stats
        val logsByCat = filteredLogs.groupBy { it.category }
        val aggsByCat = filteredAggs.groupBy { it.category }
        val allCategoryIds = (logsByCat.keys + aggsByCat.keys).toSet()

        val categoryStats = allCategoryIds.map { catId ->
            val catLogs = (logsByCat[catId] ?: emptyList()).sortedBy { it.ts }
            val catAggs = aggsByCat[catId] ?: emptyList()
            val catInfo = CategoryRegistry.getCategoryInfo(catId)

            val attempts = catLogs.size + catAggs.sumOf { it.attempts }
            val correct = catLogs.count { it.correct } + catAggs.sumOf { it.correct }
            val acc = if (attempts > 0) correct.toDouble() / attempts.toDouble() else 0.0

            val validCatTimes = catLogs.map { it.timeMs }.filter { it in 1..AnswerLogService.MAX_SPEED_TIME_MS }
            val catSpeedSum = validCatTimes.sum() + catAggs.sumOf { it.totalTimeMs }
            val catSpeedCount = validCatTimes.size + catAggs.sumOf { it.validSpeedCount }
            val avgTime = if (catSpeedCount > 0) (catSpeedSum.toDouble() / catSpeedCount).roundToLong() else myMedianMs
            val medianTime = if (validCatTimes.isNotEmpty()) computeMedianMs(validCatTimes) else avgTime
            val p90Time = if (validCatTimes.isNotEmpty()) computeP90Ms(validCatTimes) else avgTime
            val lastSeen = catLogs.maxOfOrNull { it.ts } ?: 0L

            // Trend: compare last 20 attempts vs previous 20 attempts
            val recent20 = catLogs.takeLast(20)
            val prev20 = if (catLogs.size > 20) {
                catLogs.dropLast(20).takeLast(20)
            } else emptyList()

            val trendAccDelta = if (recent20.size >= 5 && prev20.size >= 5) {
                val rAcc = recent20.count { it.correct }.toDouble() / recent20.size
                val pAcc = prev20.count { it.correct }.toDouble() / prev20.size
                rAcc - pAcc
            } else 0.0

            val trendTimeDelta = if (recent20.size >= 5 && prev20.size >= 5) {
                val rValid = recent20.map { it.timeMs }.filter { it in 1..AnswerLogService.MAX_SPEED_TIME_MS }
                val pValid = prev20.map { it.timeMs }.filter { it in 1..AnswerLogService.MAX_SPEED_TIME_MS }
                if (rValid.isNotEmpty() && pValid.isNotEmpty()) {
                    rValid.average().roundToLong() - pValid.average().roundToLong()
                } else 0L
            } else 0L

            val speedClass = classifySpeed(avgTime, myMedianMs, attempts)
            val accClass = classifyAccuracy(acc, attempts)
            val quadrant = classifyQuadrant(avgTime, myMedianMs, acc)
            val weakness = computeWeaknessScore(catLogs, catAggs, myMedianMs, todayStr)
            val strengthGroup = classifyStrengthWeakness(attempts, acc, avgTime, myMedianMs)

            CategoryStat(
                categoryId = catId,
                op = catInfo.opCode,
                attempts = attempts,
                correct = correct,
                accuracy = acc,
                avgTimeMs = avgTime,
                medianTimeMs = medianTime,
                p90TimeMs = p90Time,
                lastSeenTs = lastSeen,
                trendAccuracyDelta = trendAccDelta,
                trendTimeDeltaMs = trendTimeDelta,
                speedClass = speedClass,
                accuracyClass = accClass,
                quadrant = quadrant,
                weaknessScore = weakness,
                strengthGroup = strengthGroup
            )
        }

        // Compute the three Strength & Weakness groups:
        // a) Strong (accuracy >= 90% and avgTime <= myMedian, >= 10 attempts)
        val strongCategories = categoryStats
            .filter { it.strengthGroup == StrengthWeaknessGroup.STRONG }
            .sortedWith(compareByDescending<CategoryStat> { it.accuracy }.thenBy { it.avgTimeMs })

        // b) Needs practice (>= 10 attempts and not Strong, sorted by weaknessScore descending)
        val needsPracticeCategories = categoryStats
            .filter { it.strengthGroup == StrengthWeaknessGroup.NEEDS_PRACTICE }
            .sortedWith(compareByDescending<CategoryStat> { it.weaknessScore }.thenBy { it.accuracy })

        // c) Not enough data yet (< 10 attempts): attempted categories with < 10 attempts + unattempted core categories
        val attemptedUnderTen = categoryStats
            .filter { it.strengthGroup == StrengthWeaknessGroup.NOT_ENOUGH_DATA }
            .sortedByDescending { it.attempts }
        val unattemptedCore = CategoryRegistry.CORE_CATEGORIES
            .filter { info -> (opFilter == null || info.opCode == opFilter) && info.id !in allCategoryIds }
            .take(6)
            .map { info ->
                CategoryStat(
                    categoryId = info.id,
                    op = info.opCode,
                    attempts = 0,
                    correct = 0,
                    accuracy = 0.0,
                    avgTimeMs = 0L,
                    medianTimeMs = 0L,
                    p90TimeMs = 0L,
                    lastSeenTs = 0L,
                    trendAccuracyDelta = 0.0,
                    trendTimeDeltaMs = 0L,
                    speedClass = SpeedClass.NOT_ENOUGH_DATA,
                    accuracyClass = AccuracyClass.NOT_ENOUGH_DATA,
                    quadrant = QuadrantClass.NEEDS_WORK,
                    weaknessScore = 0.5,
                    strengthGroup = StrengthWeaknessGroup.NOT_ENOUGH_DATA
                )
            }
        val notEnoughDataCategories = (attemptedUnderTen + unattemptedCore).take(10)

        // Daily series (last N days, default 14 days for Today/7d and 30 days for 30d/All)
        val chartDaysCount = when (dateRange) {
            InsightsDateRange.TODAY, InsightsDateRange.DAYS_7 -> 14
            InsightsDateRange.DAYS_30, InsightsDateRange.ALL_TIME -> 30
        }
        val logsByDay = rawLogs.filter { opFilter == null || it.op == opFilter }.groupBy { it.day }
        val aggsByDay = rolledAggregates.filter { opFilter == null || it.op == opFilter }.groupBy { it.day }

        val rawDayPoints = (chartDaysCount - 1 downTo 0).map { offset ->
            val dStr = today.minusDays(offset.toLong()).toString()
            val dLogs = logsByDay[dStr] ?: emptyList()
            val dAggs = aggsByDay[dStr] ?: emptyList()
            val att = dLogs.size + dAggs.sumOf { it.attempts }
            val cor = dLogs.count { it.correct } + dAggs.sumOf { it.correct }
            val wrg = (att - cor).coerceAtLeast(0)
            val validTimes = dLogs.map { it.timeMs }.filter { it in 1..AnswerLogService.MAX_SPEED_TIME_MS }
            val totTime = dLogs.sumOf { it.timeMs.coerceAtMost(AnswerLogService.MAX_SPEED_TIME_MS) } + dAggs.sumOf { it.totalTimeMs }
            val spdCount = validTimes.size + dAggs.sumOf { it.validSpeedCount }
            val avgT = if (spdCount > 0) ((validTimes.sum() + dAggs.sumOf { it.totalTimeMs }).toDouble() / spdCount).roundToLong() else 0L
            val acc = if (att > 0) cor.toDouble() / att.toDouble() else 0.0
            val modes = dLogs.map { it.mode }.toSet()
            DailyVolumePoint(
                day = dStr,
                attempts = att,
                correct = cor,
                wrong = wrg,
                totalTimeMs = totTime,
                avgTimeMs = avgT,
                accuracy = acc,
                movingAvgAccuracy7d = acc,
                modesPlayed = modes
            )
        }

        // Compute 7-day moving average accuracy across rawDayPoints
        val dailySeries = rawDayPoints.mapIndexed { idx, pt ->
            val window = rawDayPoints.subList((idx - 6).coerceAtLeast(0), idx + 1)
                .filter { it.attempts > 0 }
            val ma = if (window.isNotEmpty()) {
                window.sumOf { it.correct }.toDouble() / window.sumOf { it.attempts }.toDouble()
            } else 0.0
            pt.copy(movingAvgAccuracy7d = ma)
        }

        // Speed change % vs last week (positive = faster than last week)
        val thisWeekLogs = rawLogs.filter {
            it.day in today.minusDays(6).toString()..todayStr &&
                it.timeMs in 1..AnswerLogService.MAX_SPEED_TIME_MS
        }
        val prevWeekLogs = rawLogs.filter {
            it.day in today.minusDays(13).toString()..today.minusDays(7).toString() &&
                it.timeMs in 1..AnswerLogService.MAX_SPEED_TIME_MS
        }
        val speedChangePct = if (thisWeekLogs.size >= 5 && prevWeekLogs.size >= 5) {
            val thisAvg = thisWeekLogs.map { it.timeMs }.average()
            val prevAvg = prevWeekLogs.map { it.timeMs }.average()
            if (prevAvg > 0) {
                (((prevAvg - thisAvg) / prevAvg) * 100.0).roundToInt()
            } else null
        } else null

        // Radar chart points across 6 core operations (add, sub, mul, div, sqrt, pow)
        val radarPoints = RADAR_OPS.map { opCode ->
            val opLogs = filteredLogs.filter { it.op == opCode }
            val opAggs = filteredAggs.filter { it.op == opCode }
            val att = opLogs.size + opAggs.sumOf { it.attempts }
            val cor = opLogs.count { it.correct } + opAggs.sumOf { it.correct }
            val acc = if (att > 0) cor.toDouble() / att.toDouble() else 0.0
            val vTimes = opLogs.map { it.timeMs }.filter { it in 1..AnswerLogService.MAX_SPEED_TIME_MS }
            val spdSum = vTimes.sum() + opAggs.sumOf { it.totalTimeMs }
            val spdCnt = vTimes.size + opAggs.sumOf { it.validSpeedCount }
            val avgT = if (spdCnt > 0) (spdSum.toDouble() / spdCnt).roundToLong() else 0L
            // Normalize speed score: myMedianMs * 0.6 -> 1.0, myMedianMs * 2.0 -> 0.2
            val speedNorm = if (att == 0 || avgT <= 0L) {
                0.0
            } else {
                val ratio = myMedianMs.coerceAtLeast(1000L).toDouble() / avgT.toDouble()
                (ratio * 0.7).coerceIn(0.15, 1.0)
            }
            OperationRadarPoint(
                opCode = opCode,
                attempts = att,
                accuracy = acc,
                avgTimeMs = avgT,
                speedScoreNormalized = speedNorm
            )
        }

        // Heatmap cells: rows = operations (add, sub, mul, div, sqrt, pow), cols = 5 difficulty/level buckets
        val bucketNames = listOf("Easy", "Medium", "Hard", "Challenging", "Expert")
        val heatmapCells = mutableListOf<HeatmapCell>()
        for (opCode in RADAR_OPS) {
            val opLogs = filteredLogs.filter { it.op == opCode }
            for (bIdx in 0..4) {
                val cellLogs = opLogs.filter { log ->
                    resolveDifficultyBucket(log) == bIdx
                }
                val att = cellLogs.size
                val cor = cellLogs.count { it.correct }
                val acc = if (att > 0) cor.toDouble() / att.toDouble() else 0.0
                heatmapCells.add(
                    HeatmapCell(
                        opCode = opCode,
                        bucketIndex = bIdx,
                        bucketLabel = bucketNames[bIdx],
                        attempts = att,
                        correct = cor,
                        accuracy = acc
                    )
                )
            }
        }

        // Auto-generated Insight Cards (max 3)
        val insightCards = generateInsightCards(categoryStats, radarPoints, myMedianMs)

        return InsightsDashboardSnapshot(
            dateRange = dateRange,
            opFilter = opFilter,
            totalQuestionsToday = totalToday,
            totalQuestionsThisWeek = totalThisWeek,
            totalAttemptsInRange = totalAttemptsInRange,
            totalCorrectInRange = totalCorrectInRange,
            overallAccuracy = overallAccuracy,
            overallAvgTimeMs = overallAvgTimeMs,
            overallMedianTimeMs = myMedianMs,
            practiceStreakCurrent = practiceCur,
            practiceStreakBest = practiceBest,
            practiceStreakState = practiceStreakState,
            speedChangePctVsLastWeek = speedChangePct,
            dailySeries = dailySeries,
            categoryStats = categoryStats,
            strongCategories = strongCategories,
            needsPracticeCategories = needsPracticeCategories,
            notEnoughDataCategories = notEnoughDataCategories,
            radarPoints = radarPoints,
            heatmapCells = heatmapCells,
            insightCards = insightCards
        )
    }

    fun sortCategories(stats: List<CategoryStat>, order: CategorySortOrder): List<CategoryStat> {
        return when (order) {
            CategorySortOrder.WEAKEST -> stats.sortedWith(
                compareByDescending<CategoryStat> { it.hasEnoughData }
                    .thenByDescending { it.weaknessScore }
                    .thenBy { it.accuracy }
            )
            CategorySortOrder.SLOWEST -> stats.sortedWith(
                compareByDescending<CategoryStat> { it.hasEnoughData }
                    .thenByDescending { it.avgTimeMs }
            )
            CategorySortOrder.MOST_PRACTICED -> stats.sortedByDescending { it.attempts }
            CategorySortOrder.LEAST_PRACTICED -> stats.sortedBy { it.attempts }
            CategorySortOrder.MOST_IMPROVED -> stats.sortedWith(
                compareByDescending<CategoryStat> { it.trendAccuracyDelta }
                    .thenBy { it.trendTimeDeltaMs }
            )
        }
    }

    fun buildCategoryDetail(
        categoryId: String,
        rawLogs: List<AnswerLog>,
        rolledAggregates: List<DailyCategoryAggregate>,
        myMedianMs: Long,
        todayStr: String = LocalDate.now().toString()
    ): CategoryDetailSnapshot {
        val catLogs = rawLogs.filter { it.category == categoryId }.sortedBy { it.ts }
        val catAggs = rolledAggregates.filter { it.category == categoryId }
        val snapshot = buildDashboardSnapshot(
            rawLogs = catLogs,
            rolledAggregates = catAggs,
            dateRange = InsightsDateRange.ALL_TIME,
            todayStr = todayStr
        )
        val stat = snapshot.categoryStats.firstOrNull { it.categoryId == categoryId } ?: CategoryStat(
            categoryId = categoryId,
            op = CategoryRegistry.getCategoryInfo(categoryId).opCode,
            attempts = 0,
            correct = 0,
            accuracy = 0.0,
            avgTimeMs = 0L,
            medianTimeMs = 0L,
            p90TimeMs = 0L,
            lastSeenTs = 0L,
            trendAccuracyDelta = 0.0,
            trendTimeDeltaMs = 0L,
            speedClass = SpeedClass.NOT_ENOUGH_DATA,
            accuracyClass = AccuracyClass.NOT_ENOUGH_DATA,
            quadrant = QuadrantClass.NEEDS_WORK,
            weaknessScore = 0.5
        )
        val recentMistakes = catLogs.filter { !it.correct }.sortedByDescending { it.ts }.take(10)
        val activeDays = snapshot.dailySeries.filter { it.attempts > 0 }
        return CategoryDetailSnapshot(
            stat = stat,
            dailyPoints = activeDays.ifEmpty { snapshot.dailySeries.takeLast(7) },
            recentMistakes = recentMistakes
        )
    }

    private fun resolveDifficultyBucket(log: AnswerLog): Int {
        return when (log.difficulty) {
            "easy" -> 0
            "medium" -> 1
            "hard" -> 2
            "challenging" -> 3
            "expert" -> 4
            else -> {
                val lvl = log.level ?: 3
                ((lvl - 1) / 2).coerceIn(0, 4)
            }
        }
    }

    private fun generateInsightCards(
        categoryStats: List<CategoryStat>,
        radarPoints: List<OperationRadarPoint>,
        myMedianMs: Long
    ): List<InsightCardData> {
        val qualified = categoryStats.filter { it.hasEnoughData }
        if (qualified.isEmpty()) return emptyList()

        val cards = mutableListOf<InsightCardData>()

        // 0. One-line "fastest at X and slowest at Y" summary sentence
        val fastestCat = qualified.filter { it.avgTimeMs > 0L }.minByOrNull { it.avgTimeMs }
        val slowestCat = qualified.filter { it.avgTimeMs > 0L }.maxByOrNull { it.avgTimeMs }
        if (fastestCat != null && slowestCat != null && fastestCat.categoryId != slowestCat.categoryId) {
            cards.add(
                InsightCardData(
                    type = "fastest_slowest",
                    i18nKey = "insights.card_fastest_slowest",
                    params = mapOf(
                        "fast" to fastestCat.categoryId,
                        "fastCat" to fastestCat.categoryId,
                        "slow" to slowestCat.categoryId,
                        "slowCat" to slowestCat.categoryId
                    ),
                    categoryId = slowestCat.categoryId
                )
            )
        }

        // 1. Slowest category
        val slowest = qualified.maxByOrNull { it.avgTimeMs }
        if (slowest != null && slowest.avgTimeMs > 0L) {
            cards.add(
                InsightCardData(
                    type = "slowest",
                    i18nKey = "insights.card_slowest",
                    params = mapOf(
                        "category" to slowest.categoryId,
                        "sec" to String.format("%.1f", slowest.avgTimeMs / 1000.0)
                    ),
                    categoryId = slowest.categoryId
                )
            )
        }

        // 2. Accuracy drop or weakest category
        val dropped = qualified.filter { it.trendAccuracyDelta <= -0.05 }
            .minByOrNull { it.trendAccuracyDelta }
        if (dropped != null) {
            val dropPct = (kotlin.math.abs(dropped.trendAccuracyDelta) * 100).roundToInt()
            cards.add(
                InsightCardData(
                    type = "drop",
                    i18nKey = "insights.card_acc_drop",
                    params = mapOf(
                        "category" to dropped.categoryId,
                        "pct" to dropPct.toString()
                    ),
                    categoryId = dropped.categoryId
                )
            )
        } else {
            val weakest = qualified.maxByOrNull { it.weaknessScore }
            if (weakest != null && weakest.accuracy < 0.85) {
                cards.add(
                    InsightCardData(
                        type = "weakest",
                        i18nKey = "insights.card_weakest",
                        params = mapOf(
                            "category" to weakest.categoryId,
                            "pct" to (weakest.accuracy * 100).roundToInt().toString()
                        ),
                        categoryId = weakest.categoryId
                    )
                )
            }
        }

        // 3. Fastest operation compared to overall median
        val fastestOp = radarPoints.filter { it.attempts >= 5 && it.avgTimeMs > 0 }
            .minByOrNull { it.avgTimeMs }
        if (fastestOp != null && myMedianMs > 0 && fastestOp.avgTimeMs < myMedianMs) {
            val pctFaster = (((myMedianMs - fastestOp.avgTimeMs).toDouble() / myMedianMs) * 100.0)
                .roundToInt().coerceAtLeast(5)
            cards.add(
                InsightCardData(
                    type = "fast_op",
                    i18nKey = "insights.card_faster_op",
                    params = mapOf(
                        "op" to fastestOp.opCode,
                        "pct" to pctFaster.toString()
                    )
                )
            )
        } else {
            val strongest = qualified.maxByOrNull { it.accuracy }
            if (strongest != null) {
                cards.add(
                    InsightCardData(
                        type = "strong_cat",
                        i18nKey = "insights.card_strongest",
                        params = mapOf(
                            "category" to strongest.categoryId,
                            "pct" to (strongest.accuracy * 100).roundToInt().toString()
                        ),
                        categoryId = strongest.categoryId
                    )
                )
            }
        }

        return cards.take(3)
    }
}
