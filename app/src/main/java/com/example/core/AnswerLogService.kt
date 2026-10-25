package com.example.core

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.Difficulty
import com.example.model.MathOp
import com.example.model.Problem
import com.example.model.QuizMode
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * Exact AnswerLog structure recorded per question across all modes.
 */
data class AnswerLog(
    val id: String = UUID.randomUUID().toString(),
    val ts: Long = System.currentTimeMillis(),
    val day: String = LocalDate.now().toString(),
    val mode: String,       // 'add'|'sub'|'mul'|'div'|'mixed'|'addsub'|'muldiv'|'sqrt'|'pow'|'cbrt'|'tables'|'custom'|'memory'|'daily'|'datapractice'
    val op: String,         // 'add'|'sub'|'mul'|'div'|'sqrt'|'pow'|'cbrt'|'chain'
    val category: String,   // fine-grained type tag from CategoryRegistry.classify()
    val difficulty: String?, // 'easy'|'medium'|'hard'|'challenging'|'expert' | null
    val level: Int?,        // 1-10 for daily/adaptive questions
    val operands: List<Int>,
    val prompt: String,
    val expected: Long,
    val given: Long?,       // what user typed (null = skipped/timeout)
    val correct: Boolean,
    val timeMs: Long,       // time from question shown to answer submitted
    val attempts: Int = 1,
    val source: String,     // 'normal'|'daily'|'mistake-replay'|'data-practice-mistake'|'data-practice-new'
    val sessionId: String
)

/**
 * Rolled-up daily aggregate per (day, category) for data older than 90 days (or cloud-synced history).
 */
data class DailyCategoryAggregate(
    val day: String,
    val category: String,
    val op: String,
    val attempts: Int,
    val correct: Int,
    val totalTimeMs: Long,
    val validSpeedCount: Int
)

/**
 * Spaced-repetition Mistake Book item.
 * Reappears after 1, 3, 7, 14 days until answered correctly twice in a row (and reasonably fast),
 * then marked mastered.
 */
data class MistakeBookEntry(
    val normalizedKey: String,
    val problem: Problem,
    val category: String,
    val op: String,
    val prompt: String,
    val expected: Long,
    val lastWrongAnswer: Long?,
    val missCount: Int,
    val consecutiveCorrect: Int,
    val lastMissedAt: Long,
    val nextReviewDay: String,
    val isMastered: Boolean = false
)

class AnswerLogService(context: Context) {

    companion object {
        private const val DB_NAME = "numera_answer_logs.db"
        private const val DB_VERSION = 1
        const val MAX_SPEED_TIME_MS = 60_000L

        val SPACED_INTERVAL_DAYS = listOf(1L, 3L, 7L, 14L)

        fun quizModeToLogCode(mode: QuizMode, isDataPractice: Boolean = false): String {
            if (isDataPractice) return "datapractice"
            return when (mode) {
                QuizMode.ADDITION -> "add"
                QuizMode.SUBTRACTION -> "sub"
                QuizMode.MULTIPLICATION -> "mul"
                QuizMode.DIVISION -> "div"
                QuizMode.MIXED -> "mixed"
                QuizMode.ADD_SUB -> "addsub"
                QuizMode.MULT_DIV -> "muldiv"
                QuizMode.SQUARE_ROOT -> "sqrt"
                QuizMode.EXPONENTS -> "pow"
                QuizMode.CUBE_ROOT -> "cbrt"
                QuizMode.MULTIPLICATION_TABLES -> "tables"
                QuizMode.CUSTOMIZABLE -> "custom"
                QuizMode.ARITHMETIC_MEMORY -> "memory"
                QuizMode.PRACTICE_MISTAKES -> "datapractice"
            }
        }

        fun difficultyToLogCode(diff: Difficulty?): String? = diff?.name?.lowercase()

        /**
         * Pure spaced-repetition scheduling function:
         * - When a question is missed, consecutiveCorrect resets to 0 and nextReviewDay is today + 1 day
         *   (or today so it's immediately eligible in Mistake Book / Smart Practice).
         * - When answered correctly once (consecutiveCorrect == 1), schedules +3 days (or +1/+3/+7/+14 by stage).
         * - When answered correctly twice in a row (and reasonably fast), marks isMastered = true.
         */
        fun computeSpacedRepetitionUpdate(
            existing: MistakeBookEntry?,
            problem: Problem,
            category: String,
            op: String,
            givenAnswer: Long?,
            isCorrect: Boolean,
            timeMs: Long,
            userMedianMs: Long = 3500L,
            todayStr: String = LocalDate.now().toString(),
            nowMs: Long = System.currentTimeMillis()
        ): MistakeBookEntry? {
            val today = runCatching { LocalDate.parse(todayStr) }.getOrElse { LocalDate.now() }
            val fastThresholdMs = (userMedianMs * 1.5).toLong().coerceIn(4000L, 12000L)
            val isReasonablyFast = timeMs in 1..fastThresholdMs

            if (!isCorrect) {
                val prevMisses = existing?.missCount ?: 0
                val nextDay = today.plusDays(SPACED_INTERVAL_DAYS[0]).toString()
                return MistakeBookEntry(
                    normalizedKey = problem.normalizedKey,
                    problem = problem,
                    category = category,
                    op = op,
                    prompt = problemToPrompt(problem),
                    expected = problem.answer,
                    lastWrongAnswer = givenAnswer,
                    missCount = prevMisses + 1,
                    consecutiveCorrect = 0,
                    lastMissedAt = nowMs,
                    nextReviewDay = nextDay,
                    isMastered = false
                )
            } else {
                // Correct answer: only update if it's already in the Mistake Book
                if (existing == null || existing.isMastered) return existing
                val newConsecutive = if (isReasonablyFast) {
                    existing.consecutiveCorrect + 1
                } else {
                    // Answered right, but slowly: keep at most 1 streak so they must answer fast once more
                    existing.consecutiveCorrect.coerceAtLeast(1)
                }
                val mastered = newConsecutive >= 2 && isReasonablyFast
                val intervalIdx = newConsecutive.coerceIn(0, SPACED_INTERVAL_DAYS.lastIndex)
                val nextDay = today.plusDays(SPACED_INTERVAL_DAYS[intervalIdx]).toString()
                return existing.copy(
                    consecutiveCorrect = newConsecutive,
                    nextReviewDay = nextDay,
                    isMastered = mastered
                )
            }
        }

        fun problemToPrompt(problem: Problem): String {
            return if (problem.superscriptExponent != null) {
                "${problem.displayBase}^${problem.superscriptExponent}"
            } else {
                problem.displayBase
            }
        }

        /**
         * Pure function for 90-day roll-up calculation:
         * Splits raw logs into (recentLogsWithin90Days, rolledUpDailyAggregates).
         */
        fun rollUpLogsPure(
            rawLogs: List<AnswerLog>,
            existingAggregates: List<DailyCategoryAggregate>,
            todayStr: String
        ): Pair<List<AnswerLog>, List<DailyCategoryAggregate>> {
            val today = runCatching { LocalDate.parse(todayStr) }.getOrElse { LocalDate.now() }
            val cutoffDate = today.minusDays(90).toString()

            val recent = mutableListOf<AnswerLog>()
            val oldToRoll = mutableListOf<AnswerLog>()
            for (log in rawLogs) {
                if (log.day < cutoffDate) {
                    oldToRoll.add(log)
                } else {
                    recent.add(log)
                }
            }

            if (oldToRoll.isEmpty()) {
                return recent to existingAggregates
            }

            val aggMap = existingAggregates.associateBy { "${it.day}|${it.category}" }.toMutableMap()
            for (log in oldToRoll) {
                val key = "${log.day}|${log.category}"
                val prev = aggMap[key]
                val validSpeed = log.timeMs in 1..MAX_SPEED_TIME_MS
                val addTime = if (validSpeed) log.timeMs else 0L
                val addSpeedCount = if (validSpeed) 1 else 0
                if (prev == null) {
                    aggMap[key] = DailyCategoryAggregate(
                        day = log.day,
                        category = log.category,
                        op = log.op,
                        attempts = 1,
                        correct = if (log.correct) 1 else 0,
                        totalTimeMs = addTime,
                        validSpeedCount = addSpeedCount
                    )
                } else {
                    aggMap[key] = prev.copy(
                        attempts = prev.attempts + 1,
                        correct = prev.correct + (if (log.correct) 1 else 0),
                        totalTimeMs = prev.totalTimeMs + addTime,
                        validSpeedCount = prev.validSpeedCount + addSpeedCount
                    )
                }
            }

            return recent to aggMap.values.sortedWith(compareBy({ it.day }, { it.category }))
        }

        /**
         * Encodes an AnswerLog into a compact JSON array string matching:
         * [ts, op, category, level, expected, given, correct, timeMs, id, prompt, difficulty, source, mode, sessionId]
         */
        fun encodeCompactAnswerEntry(log: AnswerLog): String {
            fun esc(s: String): String = buildString(s.length + 4) {
                append('"')
                for (c in s) {
                    when (c) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(c)
                    }
                }
                append('"')
            }
            val lvlStr = log.level?.toString() ?: "null"
            val givenStr = log.given?.toString() ?: "null"
            val diffStr = log.difficulty?.let { esc(it) } ?: "null"
            return "[${log.ts},${esc(log.op)},${esc(log.category)},$lvlStr,${log.expected},$givenStr,${log.correct},${log.timeMs},${esc(log.id)},${esc(log.prompt)},$diffStr,${esc(log.source)},${esc(log.mode)},${esc(log.sessionId)}]"
        }


        /**
         * Decodes a compact JSON array string back into an AnswerLog.
         */
        fun decodeCompactAnswerEntry(raw: String, fallbackDay: String): AnswerLog? {
            return runCatching {
                val arr = JSONArray(raw)
                if (arr.length() < 8) return null
                val ts = arr.getLong(0)
                val op = arr.optString(1, "add")
                val category = arr.optString(2, "add_1d")
                val level = if (arr.isNull(3)) null else arr.optInt(3, 1)
                val expected = arr.getLong(4)
                val given = if (arr.isNull(5)) null else arr.optLong(5, expected)
                val correct = when (val c = arr.opt(6)) {
                    is Boolean -> c
                    is Number -> c.toInt() != 0
                    else -> true
                }
                val timeMs = arr.optLong(7, 1000L)
                val id = if (arr.length() > 8 && !arr.isNull(8)) {
                    arr.optString(8).ifBlank { "${ts}_${op}_${category}_$expected" }
                } else {
                    "${ts}_${op}_${category}_$expected"
                }
                val prompt = if (arr.length() > 9 && !arr.isNull(9)) {
                    arr.optString(9, "$op = $expected")
                } else {
                    "$op = $expected"
                }
                val difficulty = if (arr.length() > 10 && !arr.isNull(10)) {
                    arr.optString(10).ifBlank { null }
                } else null
                val source = if (arr.length() > 11 && !arr.isNull(11)) {
                    arr.optString(11, "normal")
                } else "normal"
                val mode = if (arr.length() > 12 && !arr.isNull(12)) {
                    arr.optString(12, op)
                } else op
                val sessionId = if (arr.length() > 13 && !arr.isNull(13)) {
                    arr.optString(13, "cloud_sync")
                } else "cloud_sync"

                AnswerLog(
                    id = id,
                    ts = ts,
                    day = fallbackDay.substringBefore("_"),
                    mode = mode,
                    op = op,
                    category = category,
                    difficulty = difficulty,
                    level = level,
                    operands = emptyList(),
                    prompt = prompt,
                    expected = expected,
                    given = given,
                    correct = correct,
                    timeMs = timeMs,
                    attempts = 1,
                    source = source,
                    sessionId = sessionId
                )
            }.getOrNull()
        }
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS answer_logs (
                    id TEXT PRIMARY KEY,
                    user_id TEXT NOT NULL,
                    ts INTEGER NOT NULL,
                    day TEXT NOT NULL,
                    mode TEXT NOT NULL,
                    op TEXT NOT NULL,
                    category TEXT NOT NULL,
                    difficulty TEXT,
                    level INTEGER,
                    operands_json TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    expected INTEGER NOT NULL,
                    given INTEGER,
                    correct INTEGER NOT NULL,
                    time_ms INTEGER NOT NULL,
                    attempts INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    session_id TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_user_day ON answer_logs(user_id, day)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_user_cat ON answer_logs(user_id, category)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_user_op ON answer_logs(user_id, op)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_logs_user_ts ON answer_logs(user_id, ts DESC)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS daily_aggregates (
                    user_id TEXT NOT NULL,
                    day TEXT NOT NULL,
                    category TEXT NOT NULL,
                    op TEXT NOT NULL,
                    attempts INTEGER NOT NULL,
                    correct INTEGER NOT NULL,
                    total_time_ms INTEGER NOT NULL,
                    valid_speed_count INTEGER NOT NULL,
                    PRIMARY KEY (user_id, day, category)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_agg_user_day ON daily_aggregates(user_id, day)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS mistake_book (
                    user_id TEXT NOT NULL,
                    normalized_key TEXT NOT NULL,
                    problem_json TEXT NOT NULL,
                    category TEXT NOT NULL,
                    op TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    expected INTEGER NOT NULL,
                    last_wrong_answer INTEGER,
                    miss_count INTEGER NOT NULL,
                    consecutive_correct INTEGER NOT NULL,
                    last_missed_at INTEGER NOT NULL,
                    next_review_day TEXT NOT NULL,
                    is_mastered INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY (user_id, normalized_key)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_mistake_user_mastered ON mistake_book(user_id, is_mastered)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            onCreate(db)
        }
    }

    private val dbHelper = DbHelper(context.applicationContext)

    /**
     * Records a single AnswerLog and updates the spaced-repetition Mistake Book if applicable.
     */
    fun record(
        userId: String,
        log: AnswerLog,
        problemForMistakeBook: Problem? = null,
        userMedianMs: Long = 3500L,
        recordingEnabled: Boolean = true
    ) {
        if (!recordingEnabled) return
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put("id", log.id)
                put("user_id", userId)
                put("ts", log.ts)
                put("day", log.day)
                put("mode", log.mode)
                put("op", log.op)
                put("category", log.category)
                if (log.difficulty != null) put("difficulty", log.difficulty) else putNull("difficulty")
                if (log.level != null) put("level", log.level) else putNull("level")
                val opsArr = JSONArray()
                log.operands.forEach { opsArr.put(it) }
                put("operands_json", opsArr.toString())
                put("prompt", log.prompt)
                put("expected", log.expected)
                if (log.given != null) put("given", log.given) else putNull("given")
                put("correct", if (log.correct) 1 else 0)
                put("time_ms", log.timeMs)
                put("attempts", log.attempts)
                put("source", log.source)
                put("session_id", log.sessionId)
            }
            db.insertWithOnConflict("answer_logs", null, values, SQLiteDatabase.CONFLICT_REPLACE)

            if (problemForMistakeBook != null) {
                val existing = getMistakeEntryInternal(db, userId, problemForMistakeBook.normalizedKey)
                val updated = computeSpacedRepetitionUpdate(
                    existing = existing,
                    problem = problemForMistakeBook,
                    category = log.category,
                    op = log.op,
                    givenAnswer = log.given,
                    isCorrect = log.correct,
                    timeMs = log.timeMs,
                    userMedianMs = userMedianMs,
                    todayStr = log.day,
                    nowMs = log.ts
                )
                if (updated != null) {
                    upsertMistakeEntryInternal(db, userId, updated)
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Rolls raw logs older than 90 days into daily_aggregates per (day, category) so charts work forever without bloat.
     */
    fun rollUpOlderThan90Days(userId: String, todayStr: String = LocalDate.now().toString()) {
        val today = runCatching { LocalDate.parse(todayStr) }.getOrElse { LocalDate.now() }
        val cutoffDate = today.minusDays(90).toString()
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val oldLogs = mutableListOf<AnswerLog>()
            db.rawQuery(
                "SELECT * FROM answer_logs WHERE user_id = ? AND day < ?",
                arrayOf(userId, cutoffDate)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    oldLogs.add(cursorToAnswerLog(cursor))
                }
            }
            if (oldLogs.isNotEmpty()) {
                val existingAggs = loadDailyAggregates(userId)
                val (_, mergedAggs) = rollUpLogsPure(oldLogs, existingAggs, todayStr)
                for (agg in mergedAggs) {
                    if (agg.day < cutoffDate) {
                        upsertAggregateInternal(db, userId, agg)
                    }
                }
                db.delete("answer_logs", "user_id = ? AND day < ?", arrayOf(userId, cutoffDate))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadAnswerLogs(userId: String, limit: Int = 50000): List<AnswerLog> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<AnswerLog>()
        db.rawQuery(
            "SELECT * FROM answer_logs WHERE user_id = ? ORDER BY ts DESC LIMIT ?",
            arrayOf(userId, limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToAnswerLog(cursor))
            }
        }
        return list
    }

    fun loadDailyAggregates(userId: String): List<DailyCategoryAggregate> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<DailyCategoryAggregate>()
        db.rawQuery(
            "SELECT day, category, op, attempts, correct, total_time_ms, valid_speed_count FROM daily_aggregates WHERE user_id = ? ORDER BY day ASC",
            arrayOf(userId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    DailyCategoryAggregate(
                        day = cursor.getString(0),
                        category = cursor.getString(1),
                        op = cursor.getString(2),
                        attempts = cursor.getInt(3),
                        correct = cursor.getInt(4),
                        totalTimeMs = cursor.getLong(5),
                        validSpeedCount = cursor.getInt(6)
                    )
                )
            }
        }
        return list
    }

    fun loadAnswerLogsForDay(userId: String, day: String): List<AnswerLog> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<AnswerLog>()
        db.rawQuery(
            "SELECT * FROM answer_logs WHERE user_id = ? AND day = ? ORDER BY ts ASC",
            arrayOf(userId, day)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(cursorToAnswerLog(cursor))
            }
        }
        return list
    }

    fun loadDailyAggregatesForDay(userId: String, day: String): List<DailyCategoryAggregate> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<DailyCategoryAggregate>()
        db.rawQuery(
            "SELECT day, category, op, attempts, correct, total_time_ms, valid_speed_count FROM daily_aggregates WHERE user_id = ? AND day = ?",
            arrayOf(userId, day)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                list.add(
                    DailyCategoryAggregate(
                        day = cursor.getString(0),
                        category = cursor.getString(1),
                        op = cursor.getString(2),
                        attempts = cursor.getInt(3),
                        correct = cursor.getInt(4),
                        totalTimeMs = cursor.getLong(5),
                        validSpeedCount = cursor.getInt(6)
                    )
                )
            }
        }
        return list
    }

    fun getDistinctDaysForUser(userId: String): Set<String> {
        val db = dbHelper.readableDatabase
        val days = mutableSetOf<String>()
        db.rawQuery(
            "SELECT DISTINCT day FROM answer_logs WHERE user_id = ?",
            arrayOf(userId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { days.add(it) }
            }
        }
        db.rawQuery(
            "SELECT DISTINCT day FROM daily_aggregates WHERE user_id = ?",
            arrayOf(userId)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { days.add(it) }
            }
        }
        return days
    }

    fun mergePulledDayData(
        userId: String,
        day: String,
        compactAnswers: List<String>,
        categoryAggs: Map<String, Map<String, Long>>
    ) {
        val cleanDay = day.substringBefore("_")
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            var insertedLogs = 0
            for (raw in compactAnswers) {
                val log = decodeCompactAnswerEntry(raw, cleanDay) ?: continue
                val cv = ContentValues().apply {
                    put("id", log.id)
                    put("user_id", userId)
                    put("ts", log.ts)
                    put("day", log.day)
                    put("mode", log.mode)
                    put("op", log.op)
                    put("category", log.category)
                    if (log.difficulty != null) put("difficulty", log.difficulty) else putNull("difficulty")
                    if (log.level != null) put("level", log.level) else putNull("level")
                    val opsArr = JSONArray()
                    log.operands.forEach { opsArr.put(it) }
                    put("operands_json", opsArr.toString())
                    put("prompt", log.prompt)
                    put("expected", log.expected)
                    if (log.given != null) put("given", log.given) else putNull("given")
                    put("correct", if (log.correct) 1 else 0)
                    put("time_ms", log.timeMs)
                    put("attempts", log.attempts)
                    put("source", log.source)
                    put("session_id", log.sessionId)
                }
                val res = db.insertWithOnConflict("answer_logs", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                if (res != -1L) insertedLogs++
            }

            // If no raw answers were present in the day doc (e.g., aggregate-only day), merge category aggregates
            if (compactAnswers.isEmpty() && categoryAggs.isNotEmpty()) {
                for ((category, stats) in categoryAggs) {
                    val attempts = (stats["attempts"] ?: 0L).toInt()
                    val correct = (stats["correct"] ?: 0L).toInt()
                    val totalTimeMs = stats["totalTimeMs"] ?: 0L
                    if (attempts > 0) {
                        val opCode = category.substringBefore("_").ifBlank { "add" }
                        upsertAggregateInternal(
                            db,
                            userId,
                            DailyCategoryAggregate(
                                day = cleanDay,
                                category = category,
                                op = opCode,
                                attempts = attempts,
                                correct = correct,
                                totalTimeMs = totalTimeMs,
                                validSpeedCount = attempts
                            )
                        )
                    }
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadMistakeBook(userId: String, includeMastered: Boolean = false): List<MistakeBookEntry> {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<MistakeBookEntry>()
        val sql = if (includeMastered) {
            "SELECT * FROM mistake_book WHERE user_id = ? ORDER BY miss_count DESC, last_missed_at DESC"
        } else {
            "SELECT * FROM mistake_book WHERE user_id = ? AND is_mastered = 0 ORDER BY miss_count DESC, last_missed_at DESC"
        }
        db.rawQuery(sql, arrayOf(userId)).use { cursor ->
            while (cursor.moveToNext()) {
                cursorToMistakeEntry(cursor)?.let { list.add(it) }
            }
        }
        return list
    }

    fun markMistakeMastered(userId: String, normalizedKey: String) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("is_mastered", 1)
            put("consecutive_correct", 2)
        }
        db.update("mistake_book", cv, "user_id = ? AND normalized_key = ?", arrayOf(userId, normalizedKey))
    }

    fun deleteMistakeEntry(userId: String, normalizedKey: String) {
        val db = dbHelper.writableDatabase
        db.delete("mistake_book", "user_id = ? AND normalized_key = ?", arrayOf(userId, normalizedKey))
    }

    fun seedMistakeBookFromLegacyMistakes(userId: String, legacyMistakes: List<Problem>) {
        if (legacyMistakes.isEmpty()) return
        val existingKeys = loadMistakeBook(userId, includeMastered = true).map { it.normalizedKey }.toSet()
        val db = dbHelper.writableDatabase
        val todayStr = LocalDate.now().toString()
        db.beginTransaction()
        try {
            for (prob in legacyMistakes) {
                if (prob.normalizedKey !in existingKeys) {
                    val cat = CategoryRegistry.classify(prob.op, listOf(prob.a, prob.b))
                    val opCode = CategoryRegistry.mathOpToOpCode(prob.op)
                    val entry = MistakeBookEntry(
                        normalizedKey = prob.normalizedKey,
                        problem = prob,
                        category = cat,
                        op = opCode,
                        prompt = problemToPrompt(prob),
                        expected = prob.answer,
                        lastWrongAnswer = null,
                        missCount = 1,
                        consecutiveCorrect = 0,
                        lastMissedAt = System.currentTimeMillis(),
                        nextReviewDay = todayStr,
                        isMastered = false
                    )
                    upsertMistakeEntryInternal(db, userId, entry)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Copies/merges analytics data from [fromUserId] (e.g. Guest UID) into [toUserId] (signed-in UID).
     */
    fun copyOrMergeUserAnalytics(fromUserId: String, toUserId: String, deleteSource: Boolean = false) {
        if (fromUserId == toUserId) return
        val fromLogs = loadAnswerLogs(fromUserId)
        val fromAggs = loadDailyAggregates(fromUserId)
        val fromMistakes = loadMistakeBook(fromUserId, includeMastered = true)

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            for (log in fromLogs) {
                val cv = ContentValues().apply {
                    put("id", log.id)
                    put("user_id", toUserId)
                    put("ts", log.ts)
                    put("day", log.day)
                    put("mode", log.mode)
                    put("op", log.op)
                    put("category", log.category)
                    if (log.difficulty != null) put("difficulty", log.difficulty) else putNull("difficulty")
                    if (log.level != null) put("level", log.level) else putNull("level")
                    val opsArr = JSONArray()
                    log.operands.forEach { opsArr.put(it) }
                    put("operands_json", opsArr.toString())
                    put("prompt", log.prompt)
                    put("expected", log.expected)
                    if (log.given != null) put("given", log.given) else putNull("given")
                    put("correct", if (log.correct) 1 else 0)
                    put("time_ms", log.timeMs)
                    put("attempts", log.attempts)
                    put("source", log.source)
                    put("session_id", log.sessionId)
                }
                db.insertWithOnConflict("answer_logs", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            for (agg in fromAggs) {
                upsertAggregateInternal(db, toUserId, agg)
            }
            for (m in fromMistakes) {
                val existing = getMistakeEntryInternal(db, toUserId, m.normalizedKey)
                val merged = if (existing == null) {
                    m
                } else {
                    existing.copy(
                        missCount = maxOf(existing.missCount, m.missCount),
                        lastMissedAt = maxOf(existing.lastMissedAt, m.lastMissedAt),
                        isMastered = existing.isMastered && m.isMastered
                    )
                }
                upsertMistakeEntryInternal(db, toUserId, merged)
            }
            if (deleteSource) {
                db.delete("answer_logs", "user_id = ?", arrayOf(fromUserId))
                db.delete("daily_aggregates", "user_id = ?", arrayOf(fromUserId))
                db.delete("mistake_book", "user_id = ?", arrayOf(fromUserId))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun resetStatistics(userId: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("answer_logs", "user_id = ?", arrayOf(userId))
            db.delete("daily_aggregates", "user_id = ?", arrayOf(userId))
            db.delete("mistake_book", "user_id = ?", arrayOf(userId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Exports all AnswerLogs, DailyAggregates, and MistakeBook entries as formatted JSON.
     */
    fun exportDataAsJson(userId: String): String {
        val logs = loadAnswerLogs(userId)
        val aggs = loadDailyAggregates(userId)
        val mistakes = loadMistakeBook(userId, includeMastered = true)

        val root = JSONObject()
        root.put("userId", userId)
        root.put("exportedAt", System.currentTimeMillis())

        val logsArr = JSONArray()
        for (l in logs) {
            logsArr.put(
                JSONObject().apply {
                    put("id", l.id)
                    put("ts", l.ts)
                    put("day", l.day)
                    put("mode", l.mode)
                    put("op", l.op)
                    put("category", l.category)
                    put("difficulty", l.difficulty ?: JSONObject.NULL)
                    put("level", l.level ?: JSONObject.NULL)
                    val ops = JSONArray()
                    l.operands.forEach { ops.put(it) }
                    put("operands", ops)
                    put("prompt", l.prompt)
                    put("expected", l.expected)
                    put("given", l.given ?: JSONObject.NULL)
                    put("correct", l.correct)
                    put("timeMs", l.timeMs)
                    put("attempts", l.attempts)
                    put("source", l.source)
                    put("sessionId", l.sessionId)
                }
            )
        }
        root.put("answerLogs", logsArr)

        val aggsArr = JSONArray()
        for (a in aggs) {
            aggsArr.put(
                JSONObject().apply {
                    put("day", a.day)
                    put("category", a.category)
                    put("op", a.op)
                    put("attempts", a.attempts)
                    put("correct", a.correct)
                    put("totalTimeMs", a.totalTimeMs)
                    put("validSpeedCount", a.validSpeedCount)
                }
            )
        }
        root.put("dailyAggregates", aggsArr)

        val mistakesArr = JSONArray()
        for (m in mistakes) {
            mistakesArr.put(mistakeEntryToJson(m))
        }
        root.put("mistakeBook", mistakesArr)

        return root.toString(2)
    }

    /**
     * Exports AnswerLogs as CSV.
     */
    fun exportDataAsCsv(userId: String): String {
        val logs = loadAnswerLogs(userId)
        val sb = StringBuilder()
        sb.appendLine("id,ts,day,mode,op,category,difficulty,level,operands,prompt,expected,given,correct,timeMs,attempts,source,sessionId")
        for (l in logs) {
            val opsStr = l.operands.joinToString(";")
            val safePrompt = l.prompt.replace("\"", "\"\"")
            sb.appendLine(
                "${l.id},${l.ts},${l.day},${l.mode},${l.op},${l.category},${l.difficulty ?: ""},${l.level ?: ""},\"$opsStr\",\"$safePrompt\",${l.expected},${l.given ?: ""},${l.correct},${l.timeMs},${l.attempts},${l.source},${l.sessionId}"
            )
        }
        return sb.toString()
    }

    /**
     * Serializes the Mistake Book + compact daily category aggregates for cloud backup in DailyState/DailyProgress.
     */
    fun serializeCloudAnalyticsSnapshot(userId: String): String {
        val mistakes = loadMistakeBook(userId, includeMastered = false).take(80)
        val root = JSONObject()
        val mArr = JSONArray()
        mistakes.forEach { mArr.put(mistakeEntryToJson(it)) }
        root.put("mistakes", mArr)
        return root.toString()
    }

    fun restoreCloudAnalyticsSnapshot(userId: String, snapshotJson: String) {
        if (snapshotJson.isBlank()) return
        runCatching {
            val root = JSONObject(snapshotJson)
            val mArr = root.optJSONArray("mistakes") ?: return
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                for (i in 0 until mArr.length()) {
                    val obj = mArr.getJSONObject(i)
                    val entry = jsonToMistakeEntry(obj)
                    if (entry != null) {
                        val existing = getMistakeEntryInternal(db, userId, entry.normalizedKey)
                        val toSave = if (existing == null) {
                            entry
                        } else {
                            existing.copy(
                                missCount = maxOf(existing.missCount, entry.missCount),
                                consecutiveCorrect = maxOf(existing.consecutiveCorrect, entry.consecutiveCorrect),
                                lastMissedAt = maxOf(existing.lastMissedAt, entry.lastMissedAt),
                                isMastered = existing.isMastered || entry.isMastered
                            )
                        }
                        upsertMistakeEntryInternal(db, userId, toSave)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun getMistakeEntryInternal(
        db: SQLiteDatabase,
        userId: String,
        normalizedKey: String
    ): MistakeBookEntry? {
        db.rawQuery(
            "SELECT * FROM mistake_book WHERE user_id = ? AND normalized_key = ?",
            arrayOf(userId, normalizedKey)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursorToMistakeEntry(cursor)
            }
        }
        return null
    }

    private fun upsertMistakeEntryInternal(
        db: SQLiteDatabase,
        userId: String,
        entry: MistakeBookEntry
    ) {
        val cv = ContentValues().apply {
            put("user_id", userId)
            put("normalized_key", entry.normalizedKey)
            put("problem_json", problemToJson(entry.problem).toString())
            put("category", entry.category)
            put("op", entry.op)
            put("prompt", entry.prompt)
            put("expected", entry.expected)
            if (entry.lastWrongAnswer != null) put("last_wrong_answer", entry.lastWrongAnswer) else putNull("last_wrong_answer")
            put("miss_count", entry.missCount)
            put("consecutive_correct", entry.consecutiveCorrect)
            put("last_missed_at", entry.lastMissedAt)
            put("next_review_day", entry.nextReviewDay)
            put("is_mastered", if (entry.isMastered) 1 else 0)
        }
        db.insertWithOnConflict("mistake_book", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun upsertAggregateInternal(
        db: SQLiteDatabase,
        userId: String,
        agg: DailyCategoryAggregate
    ) {
        val cv = ContentValues().apply {
            put("user_id", userId)
            put("day", agg.day)
            put("category", agg.category)
            put("op", agg.op)
            put("attempts", agg.attempts)
            put("correct", agg.correct)
            put("total_time_ms", agg.totalTimeMs)
            put("valid_speed_count", agg.validSpeedCount)
        }
        db.insertWithOnConflict("daily_aggregates", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun cursorToAnswerLog(cursor: android.database.Cursor): AnswerLog {
        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        val ts = cursor.getLong(cursor.getColumnIndexOrThrow("ts"))
        val day = cursor.getString(cursor.getColumnIndexOrThrow("day"))
        val mode = cursor.getString(cursor.getColumnIndexOrThrow("mode"))
        val op = cursor.getString(cursor.getColumnIndexOrThrow("op"))
        val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
        val diffIdx = cursor.getColumnIndexOrThrow("difficulty")
        val difficulty = if (cursor.isNull(diffIdx)) null else cursor.getString(diffIdx)
        val lvlIdx = cursor.getColumnIndexOrThrow("level")
        val level = if (cursor.isNull(lvlIdx)) null else cursor.getInt(lvlIdx)
        val opsJson = cursor.getString(cursor.getColumnIndexOrThrow("operands_json"))
        val opsArr = runCatching { JSONArray(opsJson) }.getOrNull()
        val operands = mutableListOf<Int>()
        if (opsArr != null) {
            for (i in 0 until opsArr.length()) {
                operands.add(opsArr.optInt(i))
            }
        }
        val prompt = cursor.getString(cursor.getColumnIndexOrThrow("prompt"))
        val expected = cursor.getLong(cursor.getColumnIndexOrThrow("expected"))
        val givenIdx = cursor.getColumnIndexOrThrow("given")
        val given = if (cursor.isNull(givenIdx)) null else cursor.getLong(givenIdx)
        val correct = cursor.getInt(cursor.getColumnIndexOrThrow("correct")) == 1
        val timeMs = cursor.getLong(cursor.getColumnIndexOrThrow("time_ms"))
        val attempts = cursor.getInt(cursor.getColumnIndexOrThrow("attempts"))
        val source = cursor.getString(cursor.getColumnIndexOrThrow("source"))
        val sessionId = cursor.getString(cursor.getColumnIndexOrThrow("session_id"))

        return AnswerLog(
            id = id,
            ts = ts,
            day = day,
            mode = mode,
            op = op,
            category = category,
            difficulty = difficulty,
            level = level,
            operands = operands,
            prompt = prompt,
            expected = expected,
            given = given,
            correct = correct,
            timeMs = timeMs,
            attempts = attempts,
            source = source,
            sessionId = sessionId
        )
    }

    private fun cursorToMistakeEntry(cursor: android.database.Cursor): MistakeBookEntry? {
        val normKey = cursor.getString(cursor.getColumnIndexOrThrow("normalized_key"))
        val probJsonStr = cursor.getString(cursor.getColumnIndexOrThrow("problem_json"))
        val problem = runCatching { jsonToProblem(JSONObject(probJsonStr)) }.getOrNull() ?: return null
        val category = cursor.getString(cursor.getColumnIndexOrThrow("category"))
        val op = cursor.getString(cursor.getColumnIndexOrThrow("op"))
        val prompt = cursor.getString(cursor.getColumnIndexOrThrow("prompt"))
        val expected = cursor.getLong(cursor.getColumnIndexOrThrow("expected"))
        val wrongIdx = cursor.getColumnIndexOrThrow("last_wrong_answer")
        val lastWrong = if (cursor.isNull(wrongIdx)) null else cursor.getLong(wrongIdx)
        val missCount = cursor.getInt(cursor.getColumnIndexOrThrow("miss_count"))
        val consecutiveCorrect = cursor.getInt(cursor.getColumnIndexOrThrow("consecutive_correct"))
        val lastMissedAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_missed_at"))
        val nextReviewDay = cursor.getString(cursor.getColumnIndexOrThrow("next_review_day"))
        val isMastered = cursor.getInt(cursor.getColumnIndexOrThrow("is_mastered")) == 1

        return MistakeBookEntry(
            normalizedKey = normKey,
            problem = problem,
            category = category,
            op = op,
            prompt = prompt,
            expected = expected,
            lastWrongAnswer = lastWrong,
            missCount = missCount,
            consecutiveCorrect = consecutiveCorrect,
            lastMissedAt = lastMissedAt,
            nextReviewDay = nextReviewDay,
            isMastered = isMastered
        )
    }

    private fun problemToJson(p: Problem): JSONObject = JSONObject().apply {
        put("id", p.id)
        put("op", p.op.name)
        put("a", p.a)
        put("b", p.b)
        put("answer", p.answer)
        put("displayBase", p.displayBase)
        put("superscriptExponent", p.superscriptExponent ?: "")
        put("level", p.level)
        put("normalizedKey", p.normalizedKey)
    }

    private fun jsonToProblem(obj: JSONObject): Problem {
        val sup = obj.optString("superscriptExponent", "").ifEmpty { null }
        return Problem(
            id = obj.optString("id", UUID.randomUUID().toString()),
            op = runCatching { MathOp.valueOf(obj.getString("op")) }.getOrDefault(MathOp.ADD),
            a = obj.optInt("a", 1),
            b = obj.optInt("b", 1),
            answer = obj.optLong("answer", 2L),
            displayBase = obj.optString("displayBase", "1 + 1"),
            superscriptExponent = sup,
            level = obj.optInt("level", 1),
            normalizedKey = obj.optString("normalizedKey", "ADD:1:1")
        )
    }

    private fun mistakeEntryToJson(m: MistakeBookEntry): JSONObject = JSONObject().apply {
        put("normalizedKey", m.normalizedKey)
        put("problem", problemToJson(m.problem))
        put("category", m.category)
        put("op", m.op)
        put("prompt", m.prompt)
        put("expected", m.expected)
        put("lastWrongAnswer", m.lastWrongAnswer ?: JSONObject.NULL)
        put("missCount", m.missCount)
        put("consecutiveCorrect", m.consecutiveCorrect)
        put("lastMissedAt", m.lastMissedAt)
        put("nextReviewDay", m.nextReviewDay)
        put("isMastered", m.isMastered)
    }

    private fun jsonToMistakeEntry(obj: JSONObject): MistakeBookEntry? {
        val probObj = obj.optJSONObject("problem") ?: return null
        val prob = runCatching { jsonToProblem(probObj) }.getOrNull() ?: return null
        return MistakeBookEntry(
            normalizedKey = obj.optString("normalizedKey", prob.normalizedKey),
            problem = prob,
            category = obj.optString("category", CategoryRegistry.classify(prob.op, listOf(prob.a, prob.b))),
            op = obj.optString("op", CategoryRegistry.mathOpToOpCode(prob.op)),
            prompt = obj.optString("prompt", problemToPrompt(prob)),
            expected = obj.optLong("expected", prob.answer),
            lastWrongAnswer = if (obj.isNull("lastWrongAnswer")) null else obj.optLong("lastWrongAnswer"),
            missCount = obj.optInt("missCount", 1),
            consecutiveCorrect = obj.optInt("consecutiveCorrect", 0),
            lastMissedAt = obj.optLong("lastMissedAt", System.currentTimeMillis()),
            nextReviewDay = obj.optString("nextReviewDay", LocalDate.now().toString()),
            isMastered = obj.optBoolean("isMastered", false)
        )
    }
}
