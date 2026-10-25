package com.example.data

import com.example.core.AnswerLog
import com.example.core.AnswerLogService
import com.example.core.DailyCategoryAggregate
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.google.firebase.firestore.toObject
import kotlinx.coroutines.tasks.await
import java.util.Date

data class CloudUserProfile(
    @DocumentId val documentId: String = "",
    val userId: String = "",
    val displayName: String = "",
    val avatarId: String = "fox",
    val photoURL: String? = null,
    val email: String? = null,
    val provider: String = "google",
    val dailyGoal: Int = 20,
    val language: String = "en",
    val theme: String = "LIGHT",
    val accentTheme: String = "LIGHT",
    val settingsJson: String = "{}",
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

data class CloudDailyProgress(
    @DocumentId val documentId: String = "",
    val userId: String = "",
    val streakCurrent: Int = 0,
    val streakBest: Int = 0,
    val freezesAvailable: Int = 0,
    val lastCompletedDay: String? = null,
    val skillsJson: String = "{}",
    val dailyStateJson: String = "{}",
    val mistakeBookJson: String = "{}",
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

data class CloudDaySyncDoc(
    @DocumentId val documentId: String = "",
    val userId: String = "",
    val day: String = "",
    val part: Int = 1,
    val totals: Map<String, Long> = emptyMap(),
    val categories: Map<String, Map<String, Long>> = emptyMap(),
    val answers: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

data class CloudScoreDoc(
    @DocumentId val documentId: String = "",
    val userId: String = "",
    val mode: String = "ADDITION",
    val difficulty: String = "EASY",
    val count: Int = 10,
    val correct: Int = 0,
    val timeMs: Long = 0L,
    val accuracy: Double = 0.0,
    val ts: Long = 0L,
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null
)

data class PlannedBatchPayload(
    val profile: CloudUserProfile? = null,
    val dailyProgress: CloudDailyProgress? = null,
    val dayDocs: List<CloudDaySyncDoc> = emptyList(),
    val scores: List<CloudScoreDoc> = emptyList(),
    val syncedDayKeys: Set<String> = emptySet(),
    val syncedScoreIds: Set<String> = emptySet(),
    val totalOpsCount: Int = 0,
    val syncedAnswersCount: Int = 0
)

data class PulledCloudData(
    val profile: CloudUserProfile? = null,
    val dailyProgress: CloudDailyProgress? = null,
    val dayDocs: List<CloudDaySyncDoc> = emptyList(),
    val scores: List<CloudScoreDoc> = emptyList()
)

/**
 * Pure, deterministic gate and batch-sharding logic for the once-per-day Firestore sync.
 */
object DailySyncGate {
    const val MAX_ANSWERS_PER_DAY_DOC = 1500
    const val MAX_BATCH_OPS = 500
    const val MANUAL_SYNC_COOLDOWN_MS = 300_000L // 5 minutes
    const val AUTO_SYNC_INTERVAL_MS = 86_400_000L // 24 hours

    /**
     * Returns true ONLY when all daily automatic sync conditions are met:
     * - User is signed in (including guest/anonymous users; email users must have emailVerified == true)
     * - Device is online
     * - There are unsynced local changes (hasDirtyChanges == true)
     * - At most once per calendar day (lastSyncDay != todayStr) OR >= 24h since last sync
     */
    fun shouldRunDailySync(
        isAuthenticated: Boolean,
        isGuest: Boolean = false,
        isOnline: Boolean,
        lastSyncDay: String?,
        todayStr: String,
        hasDirtyChanges: Boolean = true,
        emailVerified: Boolean = true,
        lastSyncedMs: Long? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isAuthenticated) return false
        if (!isGuest && !emailVerified) return false
        if (!isOnline) return false
        if (todayStr.isBlank()) return false
        if (!hasDirtyChanges) return false
        val isNewCalendarDay = (lastSyncDay != todayStr)
        val isAfter24Hours = (lastSyncedMs != null && lastSyncedMs > 0L && (nowMs - lastSyncedMs) >= AUTO_SYNC_INTERVAL_MS)
        return isNewCalendarDay || isAfter24Hours
    }

    /**
     * Manual "Push my data now" button is rate-limited with a 5-minute cooldown.
     * Supports all signed-in users (including guest/anonymous; email users require emailVerified).
     */
    fun canRunManualSync(
        isAuthenticated: Boolean,
        isGuest: Boolean = false,
        isOnline: Boolean,
        lastManualSyncMs: Long?,
        nowMs: Long,
        emailVerified: Boolean = true
    ): Boolean {
        if (!isAuthenticated || !isOnline) return false
        if (!isGuest && !emailVerified) return false
        if (lastManualSyncMs == null || lastManualSyncMs <= 0L) return true
        return (nowMs - lastManualSyncMs) >= MANUAL_SYNC_COOLDOWN_MS
    }

    fun remainingManualCooldownSeconds(lastManualSyncMs: Long?, nowMs: Long): Long {
        if (lastManualSyncMs == null || lastManualSyncMs <= 0L) return 0L
        val elapsed = nowMs - lastManualSyncMs
        if (elapsed >= MANUAL_SYNC_COOLDOWN_MS) return 0L
        return ((MANUAL_SYNC_COOLDOWN_MS - elapsed) + 999L) / 1000L
    }

    /**
     * Builds compact day documents for `/users/{uid}/days/{YYYY-MM-DD}`.
     * If a single day has > 1500 answers, splits into `days/{date}`, `days/{date}_2`, etc.
     */
    fun buildDayDocShards(
        userId: String,
        day: String,
        logs: List<AnswerLog>,
        extraAggregates: List<DailyCategoryAggregate> = emptyList()
    ): List<CloudDaySyncDoc> {
        val cleanDay = day.substringBefore("_")
        val catMap = mutableMapOf<String, MutableMap<String, Long>>()

        var totalQuestions = 0L
        var totalCorrect = 0L
        var totalTimeMs = 0L

        if (logs.isNotEmpty()) {
            for (log in logs) {
                totalQuestions += 1L
                if (log.correct) totalCorrect += 1L
                val validTime = if (log.timeMs in 1..AnswerLogService.MAX_SPEED_TIME_MS) log.timeMs else 0L
                totalTimeMs += validTime

                val entry = catMap.getOrPut(log.category) {
                    mutableMapOf("attempts" to 0L, "correct" to 0L, "totalTimeMs" to 0L)
                }
                entry["attempts"] = (entry["attempts"] ?: 0L) + 1L
                if (log.correct) {
                    entry["correct"] = (entry["correct"] ?: 0L) + 1L
                }
                entry["totalTimeMs"] = (entry["totalTimeMs"] ?: 0L) + validTime
            }
        } else {
            for (agg in extraAggregates) {
                totalQuestions += agg.attempts.toLong()
                totalCorrect += agg.correct.toLong()
                totalTimeMs += agg.totalTimeMs
                catMap[agg.category] = mutableMapOf(
                    "attempts" to agg.attempts.toLong(),
                    "correct" to agg.correct.toLong(),
                    "totalTimeMs" to agg.totalTimeMs
                )
            }
        }

        val totals = mapOf(
            "questions" to totalQuestions,
            "correct" to totalCorrect,
            "timeMs" to totalTimeMs
        )
        val categoriesImm = catMap.mapValues { it.value.toMap() }

        val encodedAnswers = logs.map { AnswerLogService.encodeCompactAnswerEntry(it) }
        if (encodedAnswers.isEmpty()) {
            return listOf(
                CloudDaySyncDoc(
                    documentId = cleanDay,
                    userId = userId,
                    day = cleanDay,
                    part = 1,
                    totals = totals,
                    categories = categoriesImm,
                    answers = emptyList()
                )
            )
        }

        val chunks = encodedAnswers.chunked(MAX_ANSWERS_PER_DAY_DOC)
        return chunks.mapIndexed { idx, chunk ->
            val partNum = idx + 1
            val docId = if (partNum == 1) cleanDay else "${cleanDay}_$partNum"
            CloudDaySyncDoc(
                documentId = docId,
                userId = userId,
                day = cleanDay,
                part = partNum,
                totals = totals,
                categories = categoriesImm,
                answers = chunk
            )
        }
    }

    /**
     * Plans a single Firestore WriteBatch capped at [maxOps] (default 500 ops).
     */
    fun planWriteBatch(
        profile: CloudUserProfile?,
        dailyProgress: CloudDailyProgress?,
        dayDocGroups: Map<String, List<CloudDaySyncDoc>>,
        scores: List<CloudScoreDoc>,
        maxOps: Int = MAX_BATCH_OPS
    ): PlannedBatchPayload {
        var remaining = maxOps.coerceIn(1, MAX_BATCH_OPS)
        val includeProfile = if (profile != null && remaining >= 1) {
            remaining -= 1
            profile
        } else null

        val includeDaily = if (dailyProgress != null && remaining >= 1) {
            remaining -= 1
            dailyProgress
        } else null

        val chosenDayDocs = mutableListOf<CloudDaySyncDoc>()
        val syncedDays = mutableSetOf<String>()
        for ((dayKey, shards) in dayDocGroups) {
            if (shards.isEmpty()) continue
            if (shards.size <= remaining) {
                chosenDayDocs.addAll(shards)
                syncedDays.add(dayKey)
                remaining -= shards.size
            } else {
                break
            }
        }

        val chosenScores = mutableListOf<CloudScoreDoc>()
        val syncedScores = mutableSetOf<String>()
        for (score in scores) {
            if (remaining >= 1) {
                chosenScores.add(score)
                val id = score.documentId.ifBlank { "${score.ts}" }
                syncedScores.add(id)
                remaining -= 1
            } else {
                break
            }
        }

        val totalOps = (if (includeProfile != null) 1 else 0) +
            (if (includeDaily != null) 1 else 0) +
            chosenDayDocs.size +
            chosenScores.size

        val totalAnswers = chosenDayDocs.sumOf { doc ->
            if (doc.answers.isNotEmpty()) {
                doc.answers.size
            } else {
                (doc.totals["questions"] ?: 0L).toInt()
            }
        }

        return PlannedBatchPayload(
            profile = includeProfile,
            dailyProgress = includeDaily,
            dayDocs = chosenDayDocs,
            scores = chosenScores,
            syncedDayKeys = syncedDays,
            syncedScoreIds = syncedScores,
            totalOpsCount = totalOps,
            syncedAnswersCount = totalAnswers
        )
    }
}

class CloudSyncRepository(
    private val db: FirebaseFirestore
) {
    init {
        // Disable Firestore disk persistence: local SQLite + SharedPreferences is the source of truth
        runCatching {
            val current = db.firestoreSettings
            db.firestoreSettings = firestoreSettings {
                host = current.host
                isSslEnabled = current.isSslEnabled
                setLocalCacheSettings(memoryCacheSettings {})
            }
        }
    }


    var lastSyncErrorMessage: String? = null
        private set

    /**
     * Executes ONE Firestore WriteBatch (max 500 operations) containing only changed data.
     * Never calls any Firestore delete from the client.
     */
    suspend fun syncDailyBatch(uid: String, payload: PlannedBatchPayload): Boolean {
        lastSyncErrorMessage = null
        if (payload.totalOpsCount <= 0) return true
        val userPath = "users/$uid"
        return try {
            val batch = db.batch()
            val userRef = db.collection("users").document(uid)

            payload.profile?.let { prof ->
                batch.set(userRef, prof.copy(userId = uid), SetOptions.merge())
            }

            payload.dailyProgress?.let { prog ->
                val dailyRef = userRef.collection("progress").document("daily")
                batch.set(dailyRef, prog.copy(userId = uid), SetOptions.merge())
            }

            for (dayDoc in payload.dayDocs) {
                val docId = dayDoc.documentId.ifBlank {
                    if (dayDoc.part <= 1) dayDoc.day else "${dayDoc.day}_${dayDoc.part}"
                }
                val dayRef = userRef.collection("days").document(docId)
                batch.set(dayRef, dayDoc.copy(userId = uid), SetOptions.merge())
            }

            for (scoreDoc in payload.scores) {
                val scoreId = scoreDoc.documentId.ifBlank { "score_${scoreDoc.ts}" }
                val scoreRef = userRef.collection("scores").document(scoreId)
                batch.set(scoreRef, scoreDoc.copy(userId = uid), SetOptions.merge())
            }

            batch.commit().await()
            true
        } catch (e: Exception) {
            lastSyncErrorMessage = e.message ?: e.javaClass.simpleName
            runCatching {
                android.util.Log.e("NumeraSync", "Firestore syncDailyBatch failed for $userPath: ${e.message}", e)
            }
            handleFirestoreError(e, OperationType.WRITE, userPath)
            false
        }
    }

    /**
     * One-time pull on login on a new device: pulls profile, progress/daily, last 30 days docs, and scores.
     */
    suspend fun pullInitialUserData(uid: String, daysLimit: Int = 30): PulledCloudData {
        val profile = getUserProfile(uid)
        val daily = getDailyProgress(uid)
        val days = listDaySyncDocs(uid, daysLimit)
        val scores = listScores(uid)
        return PulledCloudData(
            profile = profile,
            dailyProgress = daily,
            dayDocs = days,
            scores = scores
        )
    }

    suspend fun getUserProfile(uid: String): CloudUserProfile? {
        val path = "users/$uid"
        return try {
            val snap = db.collection("users").document(uid).get().await()
            if (snap.exists()) snap.toObject<CloudUserProfile>() else null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun saveUserProfile(profile: CloudUserProfile) {
        val path = "users/${profile.userId}"
        try {
            val docRef = db.collection("users").document(profile.userId)
            docRef.set(profile, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun getDailyProgress(uid: String): CloudDailyProgress? {
        val path = "users/$uid/progress/daily"
        return try {
            val snap = db.collection("users").document(uid)
                .collection("progress").document("daily")
                .get().await()
            if (snap.exists()) snap.toObject<CloudDailyProgress>() else null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun saveDailyProgress(progress: CloudDailyProgress) {
        val path = "users/${progress.userId}/progress/daily"
        try {
            val docRef = db.collection("users").document(progress.userId)
                .collection("progress").document("daily")
            docRef.set(progress, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun saveDaySyncDoc(uid: String, docId: String, dayDoc: CloudDaySyncDoc) {
        val path = "users/$uid/days/$docId"
        try {
            db.collection("users").document(uid)
                .collection("days").document(docId)
                .set(dayDoc.copy(userId = uid), SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
        }
    }

    suspend fun getDaySyncDoc(uid: String, docId: String): CloudDaySyncDoc? {
        val path = "users/$uid/days/$docId"
        return try {
            val snap = db.collection("users").document(uid)
                .collection("days").document(docId)
                .get().await()
            if (snap.exists()) snap.toObject<CloudDaySyncDoc>() else null
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            null
        }
    }

    suspend fun listDaySyncDocs(uid: String, limit: Int = 30): List<CloudDaySyncDoc> {
        val path = "users/$uid/days"
        return try {
            val snap = db.collection("users").document(uid)
                .collection("days")
                .whereEqualTo("userId", uid)
                .orderBy("day", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get().await()
            snap.documents.mapNotNull { it.toObject<CloudDaySyncDoc>() }
        } catch (e: Exception) {
            // Fallback if composite index is still building
            try {
                val fallbackSnap = db.collection("users").document(uid)
                    .collection("days")
                    .whereEqualTo("userId", uid)
                    .limit(limit.toLong())
                    .get().await()
                fallbackSnap.documents.mapNotNull { it.toObject<CloudDaySyncDoc>() }
                    .sortedByDescending { it.day }
            } catch (e2: Exception) {
                handleFirestoreError(e2, OperationType.LIST, path)
                emptyList()
            }
        }
    }

    suspend fun saveScore(uid: String, scoreId: String, scoreDoc: CloudScoreDoc) {
        val path = "users/$uid/scores/$scoreId"
        try {
            db.collection("users").document(uid)
                .collection("scores").document(scoreId)
                .set(scoreDoc.copy(userId = uid), SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
        }
    }

    suspend fun listScores(uid: String): List<CloudScoreDoc> {
        val path = "users/$uid/scores"
        return try {
            val snap = db.collection("users").document(uid)
                .collection("scores")
                .whereEqualTo("userId", uid)
                .get().await()
            snap.documents.mapNotNull { it.toObject<CloudScoreDoc>() }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            emptyList()
        }
    }
}
