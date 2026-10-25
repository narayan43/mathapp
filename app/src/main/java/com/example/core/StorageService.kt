package com.example.core

import android.content.Context
import android.content.SharedPreferences
import com.example.model.AnsweredProblem
import com.example.model.AppThemeId
import com.example.model.AppearanceMode
import com.example.model.CustomConfig
import com.example.model.DailyState
import com.example.model.Difficulty
import com.example.model.KeypadSize
import com.example.model.KeypadType
import com.example.model.LevelChangeInfo
import com.example.model.MathOp
import com.example.model.Problem
import com.example.model.QuizMode
import com.example.model.ScoreRecord
import com.example.model.SettingsState
import com.example.model.SkillOp
import com.example.model.SkillState
import com.example.model.StreakState
import com.example.model.TableStat
import com.example.model.UserProfileState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LocalSyncQueue(
    val lastSyncDay: String? = null,
    val lastSyncedTimestamp: Long? = null,
    val lastManualSyncMs: Long? = null,
    val dirtyProfile: Boolean = false,
    val dirtyDailyProgress: Boolean = false,
    val dirtyDays: Set<String> = emptySet(),
    val dirtyScoreIds: Set<String> = emptySet(),
    val initialPullDone: Boolean = false,
    val localUpdatedAtMs: Long = 0L
) {
    val hasDirtyChanges: Boolean
        get() = dirtyProfile || dirtyDailyProgress || dirtyDays.isNotEmpty() || dirtyScoreIds.isNotEmpty()

    fun pendingItemsCount(dirtyAnswerCount: Int = 0): Int {
        val metaCount = (if (dirtyProfile) 1 else 0) + (if (dirtyDailyProgress) 1 else 0) + dirtyScoreIds.size
        val dayOrAnswerCount = if (dirtyAnswerCount > 0) dirtyAnswerCount else dirtyDays.size
        return metaCount + dayOrAnswerCount
    }
}

/**
 * StorageService providing async getItem/setItem and structured persistence
 * scoped by userId ("guest" or authenticated uid).
 */
class StorageService(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("numera_prefs", Context.MODE_PRIVATE)

    private var activeUserId: String = prefs.getString(KEY_ACTIVE_USER_ID, "guest") ?: "guest"

    companion object {
        const val KEY_DAILY_WORKOUT = "DAILY_WORKOUT"
        const val KEY_SETTINGS = "SETTINGS"
        const val KEY_SCORES = "SCORES"
        const val KEY_MISTAKES = "MISTAKES"
        const val KEY_THEME = "THEME"
        const val KEY_APPEARANCE_MODE = "APPEARANCE_MODE"
        const val KEY_LANGUAGE = "LANGUAGE"
        const val KEY_INSTALL_ID = "INSTALL_ID"
        const val KEY_CUSTOM_CONFIG = "CUSTOM_CONFIG"
        const val KEY_TABLE_STATS = "TABLE_STATS"
        const val KEY_ONBOARDING_DONE = "ONBOARDING_DONE"
        const val KEY_ACTIVE_USER_ID = "ACTIVE_USER_ID"
        const val KEY_USER_PROFILE = "USER_PROFILE"
        const val KEY_SYNC_QUEUE = "SYNC_QUEUE"

        /**
         * Pure merge function combining local guest DailyState & ScoreRecords with cloud state.
         * Takes higher skill level per operation, max bestStreak, union of completed/frozen dates,
         * and union of scores deduplicated by id.
         */
        fun mergeDailyStates(local: DailyState, cloud: DailyState): DailyState {
            val mergedSkills = SkillOp.entries.associateWith { op ->
                val lSkill = local.skills[op] ?: SkillState(op = op)
                val cSkill = cloud.skills[op] ?: SkillState(op = op)
                if (lSkill.level >= cSkill.level) {
                    lSkill.copy(totalAnswered = maxOf(lSkill.totalAnswered, cSkill.totalAnswered))
                } else {
                    cSkill.copy(totalAnswered = maxOf(lSkill.totalAnswered, cSkill.totalAnswered))
                }
            }
            val mergedCompletedDates = local.streak.completedDates + cloud.streak.completedDates
            val mergedFrozenDates = local.streak.frozenDates + cloud.streak.frozenDates
            val mergedCurrent = maxOf(local.streak.current, cloud.streak.current)
            val mergedBest = maxOf(local.streak.best, cloud.streak.best, mergedCurrent)
            val mergedFreezes = maxOf(local.streak.freezesAvailable, cloud.streak.freezesAvailable).coerceAtMost(2)
            val mergedLastDate = listOfNotNull(local.streak.lastCompletedDate, cloud.streak.lastCompletedDate)
                .maxOrNull()

            val mergedStreak = StreakState(
                current = mergedCurrent,
                best = mergedBest,
                freezesAvailable = mergedFreezes,
                lastCompletedDate = mergedLastDate,
                frozenDates = mergedFrozenDates,
                completedDates = mergedCompletedDates,
                savedByFreezeToday = local.streak.savedByFreezeToday || cloud.streak.savedByFreezeToday
            )

            return if (local.todayAnswers.size >= cloud.todayAnswers.size) {
                local.copy(skills = mergedSkills, streak = mergedStreak)
            } else {
                cloud.copy(skills = mergedSkills, streak = mergedStreak)
            }
        }

        fun mergeScores(localScores: List<ScoreRecord>, cloudScores: List<ScoreRecord>): List<ScoreRecord> {
            return (localScores + cloudScores)
                .distinctBy { it.id }
                .sortedBy { it.timestamp }
                .takeLast(200)
        }
    }

    fun setActiveUser(userId: String) {
        activeUserId = userId.ifBlank { "guest" }
        prefs.edit().putString(KEY_ACTIVE_USER_ID, activeUserId).apply()
    }

    fun getActiveUser(): String = activeUserId

    private fun scopedKey(baseKey: String, userId: String = activeUserId): String {
        return if (userId == "guest") baseKey else "${baseKey}_$userId"
    }

    suspend fun getItem(key: String): String? = withContext(Dispatchers.IO) {
        prefs.getString(key, null)
    }

    suspend fun setItem(key: String, value: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key, value).apply()
    }

    fun getItemSync(key: String): String? = prefs.getString(key, null)

    fun setItemSync(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun removeItemSync(key: String) {
        prefs.edit().remove(key).apply()
    }

    fun isOnboardingDone(): Boolean {
        return getItemSync(KEY_ONBOARDING_DONE) == "true"
    }

    fun setOnboardingDone(done: Boolean) {
        setItemSync(KEY_ONBOARDING_DONE, if (done) "true" else "false")
    }

    fun getOrCreateInstallId(): String {
        val existing = getItemSync(KEY_INSTALL_ID)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString().take(12)
        setItemSync(KEY_INSTALL_ID, created)
        return created
    }

    fun hasGuestProgress(guestUserId: String = activeUserId): Boolean {
        val checkIds = listOf(guestUserId, "guest").distinct()
        for (uid in checkIds) {
            val guestScores = loadScoresForUser(uid)
            if (guestScores.isNotEmpty()) return true
            val rawDaily = getItemSync(scopedKey(KEY_DAILY_WORKOUT, uid)) ?: continue
            val hasDaily = runCatching {
                val o = JSONObject(rawDaily)
                val ans = o.optJSONArray("todayAnswers")
                val streakObj = o.optJSONObject("streak")
                val curStreak = streakObj?.optInt("current", 0) ?: 0
                val bestStreak = streakObj?.optInt("best", 0) ?: 0
                (ans != null && ans.length() > 0) || curStreak > 0 || bestStreak > 0
            }.getOrDefault(false)
            if (hasDaily) return true
        }
        return false
    }

    fun copyUserLocalData(fromUserId: String, toUserId: String) {
        if (fromUserId == toUserId) return
        listOf(
            KEY_DAILY_WORKOUT,
            KEY_SETTINGS,
            KEY_SCORES,
            KEY_MISTAKES,
            KEY_CUSTOM_CONFIG,
            KEY_TABLE_STATS
        ).forEach { baseKey ->
            val raw = getItemSync(scopedKey(baseKey, fromUserId))
            if (!raw.isNullOrBlank()) {
                setItemSync(scopedKey(baseKey, toUserId), raw)
            }
        }
    }

    fun clearUserLocalData(userId: String) {
        listOf(
            KEY_DAILY_WORKOUT,
            KEY_SETTINGS,
            KEY_SCORES,
            KEY_MISTAKES,
            KEY_CUSTOM_CONFIG,
            KEY_TABLE_STATS,
            KEY_USER_PROFILE,
            KEY_SYNC_QUEUE
        ).forEach { baseKey ->
            removeItemSync(scopedKey(baseKey, userId))
        }
    }

    fun loadSyncQueue(userId: String = activeUserId): LocalSyncQueue {
        val raw = getItemSync(scopedKey(KEY_SYNC_QUEUE, userId)) ?: return LocalSyncQueue()
        return runCatching {
            val o = JSONObject(raw)
            val daysArr = o.optJSONArray("dirtyDays")
            val dirtyDays = mutableSetOf<String>()
            if (daysArr != null) {
                for (i in 0 until daysArr.length()) {
                    val d = daysArr.optString(i)
                    if (d.isNotBlank()) dirtyDays.add(d)
                }
            }
            val scoresArr = o.optJSONArray("dirtyScoreIds")
            val dirtyScoreIds = mutableSetOf<String>()
            if (scoresArr != null) {
                for (i in 0 until scoresArr.length()) {
                    val s = scoresArr.optString(i)
                    if (s.isNotBlank()) dirtyScoreIds.add(s)
                }
            }
            LocalSyncQueue(
                lastSyncDay = o.optString("lastSyncDay", "").takeIf { it.isNotBlank() },
                lastSyncedTimestamp = o.optLong("lastSyncedTimestamp", -1L).takeIf { it > 0L },
                lastManualSyncMs = o.optLong("lastManualSyncMs", -1L).takeIf { it > 0L },
                dirtyProfile = o.optBoolean("dirtyProfile", false),
                dirtyDailyProgress = o.optBoolean("dirtyDailyProgress", false),
                dirtyDays = dirtyDays,
                dirtyScoreIds = dirtyScoreIds,
                initialPullDone = o.optBoolean("initialPullDone", false),
                localUpdatedAtMs = o.optLong("localUpdatedAtMs", 0L)
            )
        }.getOrDefault(LocalSyncQueue())
    }

    fun saveSyncQueue(queue: LocalSyncQueue, userId: String = activeUserId) {
        val o = JSONObject().apply {
            put("lastSyncDay", queue.lastSyncDay ?: "")
            put("lastSyncedTimestamp", queue.lastSyncedTimestamp ?: -1L)
            put("lastManualSyncMs", queue.lastManualSyncMs ?: -1L)
            put("dirtyProfile", queue.dirtyProfile)
            put("dirtyDailyProgress", queue.dirtyDailyProgress)
            put("dirtyDays", JSONArray(queue.dirtyDays.toList()))
            put("dirtyScoreIds", JSONArray(queue.dirtyScoreIds.toList()))
            put("initialPullDone", queue.initialPullDone)
            put("localUpdatedAtMs", queue.localUpdatedAtMs)
        }
        setItemSync(scopedKey(KEY_SYNC_QUEUE, userId), o.toString())
    }

    fun markProfileDirty(userId: String = activeUserId, nowMs: Long = System.currentTimeMillis()) {
        val q = loadSyncQueue(userId)
        saveSyncQueue(q.copy(dirtyProfile = true, localUpdatedAtMs = nowMs), userId)
    }

    fun markDailyProgressDirty(userId: String = activeUserId, nowMs: Long = System.currentTimeMillis()) {
        val q = loadSyncQueue(userId)
        saveSyncQueue(q.copy(dirtyDailyProgress = true, localUpdatedAtMs = nowMs), userId)
    }

    fun markDayDirty(day: String, userId: String = activeUserId, nowMs: Long = System.currentTimeMillis()) {
        if (day.isBlank()) return
        val q = loadSyncQueue(userId)
        saveSyncQueue(q.copy(dirtyDays = q.dirtyDays + day, localUpdatedAtMs = nowMs), userId)
    }

    fun markScoreDirty(scoreId: String, userId: String = activeUserId, nowMs: Long = System.currentTimeMillis()) {
        if (scoreId.isBlank()) return
        val q = loadSyncQueue(userId)
        saveSyncQueue(q.copy(dirtyScoreIds = q.dirtyScoreIds + scoreId, localUpdatedAtMs = nowMs), userId)
    }

    fun markAllDirtyForUser(
        days: Set<String>,
        scoreIds: Set<String>,
        userId: String = activeUserId,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val q = loadSyncQueue(userId)
        saveSyncQueue(
            q.copy(
                dirtyProfile = true,
                dirtyDailyProgress = true,
                dirtyDays = q.dirtyDays + days,
                dirtyScoreIds = q.dirtyScoreIds + scoreIds,
                localUpdatedAtMs = nowMs
            ),
            userId
        )
    }

    fun clearDirtyFlagsAfterSync(
        syncDay: String,
        syncedAtMs: Long,
        isManual: Boolean,
        syncedProfile: Boolean,
        syncedDailyProgress: Boolean,
        syncedDays: Set<String>,
        syncedScoreIds: Set<String>,
        userId: String = activeUserId
    ): LocalSyncQueue {
        val q = loadSyncQueue(userId)
        val updated = q.copy(
            lastSyncDay = syncDay,
            lastSyncedTimestamp = syncedAtMs,
            lastManualSyncMs = if (isManual) syncedAtMs else q.lastManualSyncMs,
            dirtyProfile = if (syncedProfile) false else q.dirtyProfile,
            dirtyDailyProgress = if (syncedDailyProgress) false else q.dirtyDailyProgress,
            dirtyDays = q.dirtyDays - syncedDays,
            dirtyScoreIds = q.dirtyScoreIds - syncedScoreIds
        )
        saveSyncQueue(updated, userId)
        return updated
    }

    fun markInitialPullDone(userId: String = activeUserId) {
        val q = loadSyncQueue(userId)
        saveSyncQueue(q.copy(initialPullDone = true), userId)
    }

    fun loadUserProfile(userId: String = activeUserId): UserProfileState {
        val isGuestDefault = userId == "guest" || userId.startsWith("guest_")
        val raw = getItemSync(scopedKey(KEY_USER_PROFILE, userId))
        if (raw.isNullOrBlank()) {
            return UserProfileState(userId = userId, isGuest = isGuestDefault)
        }
        return runCatching {
            val o = JSONObject(raw)
            val syncQueue = loadSyncQueue(userId)
            val lastSyncedTs = o.optLong("lastSyncedTimestamp", -1L).takeIf { it > 0L }
                ?: syncQueue.lastSyncedTimestamp
            val lastSyncDay = o.optString("lastSyncDay", "").takeIf { it.isNotBlank() }
                ?: syncQueue.lastSyncDay
            val appearanceMode = runCatching {
                AppearanceMode.valueOf(o.optString("appearanceMode", AppearanceMode.LIGHT.name))
            }.getOrDefault(AppearanceMode.LIGHT)
            UserProfileState(
                userId = o.optString("userId", userId),
                isGuest = o.optBoolean("isGuest", isGuestDefault),
                displayName = o.optString("displayName", ""),
                avatarId = o.optString("avatarId", "fox"),
                email = o.optString("email", "").takeIf { it.isNotBlank() },
                provider = o.optString("provider", if (isGuestDefault) "guest" else "google"),
                emailVerified = o.optBoolean("emailVerified", true),
                dailyGoal = o.optInt("dailyGoal", 20),
                lastSyncedTimestamp = lastSyncedTs,
                lastSyncDay = lastSyncDay,
                isProfileComplete = o.optBoolean("isProfileComplete", false),
                appearanceMode = appearanceMode
            )
        }.getOrDefault(UserProfileState(userId = userId, isGuest = isGuestDefault))
    }

    fun saveUserProfile(profile: UserProfileState, userId: String = profile.userId) {
        val o = JSONObject().apply {
            put("userId", profile.userId)
            put("isGuest", profile.isGuest)
            put("displayName", profile.displayName)
            put("avatarId", profile.avatarId)
            put("email", profile.email ?: "")
            put("provider", profile.provider)
            put("emailVerified", profile.emailVerified)
            put("dailyGoal", profile.dailyGoal)
            put("lastSyncedTimestamp", profile.lastSyncedTimestamp ?: -1L)
            put("lastSyncDay", profile.lastSyncDay ?: "")
            put("isProfileComplete", profile.isProfileComplete)
            put("appearanceMode", profile.appearanceMode.name)
        }
        setItemSync(scopedKey(KEY_USER_PROFILE, userId), o.toString())
    }

    fun loadSettings(userId: String = activeUserId): SettingsState {
        val raw = getItemSync(scopedKey(KEY_SETTINGS, userId)) ?: getItemSync(KEY_SETTINGS)
        val savedTheme = getItemSync(KEY_THEME)?.let {
            runCatching { AppThemeId.valueOf(it) }.getOrNull()
        }
        val savedAppearance = getItemSync(KEY_APPEARANCE_MODE)?.let {
            runCatching { AppearanceMode.valueOf(it) }.getOrNull()
        }
        val savedLang = getItemSync(KEY_LANGUAGE)

        if (raw.isNullOrBlank()) {
            return SettingsState(
                themeId = savedTheme ?: AppThemeId.LIGHT,
                appearanceMode = savedAppearance ?: AppearanceMode.LIGHT,
                languageCode = savedLang ?: "en"
            )
        }
        return parseSettingsJson(raw, savedTheme, savedLang, savedAppearance)
    }

    fun parseSettingsJson(
        raw: String,
        savedTheme: AppThemeId? = null,
        savedLang: String? = null,
        savedAppearance: AppearanceMode? = null
    ): SettingsState {
        return runCatching {
            val obj = JSONObject(raw)
            val opsArr = obj.optJSONArray("dailyEnabledOps")
            val enabledOps = mutableSetOf<SkillOp>()
            if (opsArr != null) {
                for (i in 0 until opsArr.length()) {
                    runCatching { SkillOp.valueOf(opsArr.getString(i)) }.getOrNull()?.let {
                        enabledOps.add(it)
                    }
                }
            }
            if (enabledOps.size < 2) {
                enabledOps.addAll(SkillOp.entries)
            }

            val daysArr = obj.optJSONArray("reminderDays")
            val days = mutableSetOf<Int>()
            if (daysArr != null) {
                for (i in 0 until daysArr.length()) {
                    days.add(daysArr.getInt(i))
                }
            } else {
                days.addAll(listOf(1, 2, 3, 4, 5, 6, 7))
            }

            val perDayObj = obj.optJSONObject("perDayTimes")
            val perDayMap = mutableMapOf<Int, String>()
            for (d in 1..7) {
                perDayMap[d] = perDayObj?.optString(d.toString(), "19:00") ?: "19:00"
            }

            val themeId = runCatching {
                AppThemeId.valueOf(obj.optString("themeId", savedTheme?.name ?: AppThemeId.LIGHT.name))
            }.getOrDefault(savedTheme ?: AppThemeId.LIGHT)

            val appearanceMode = runCatching {
                AppearanceMode.valueOf(
                    obj.optString("appearanceMode", savedAppearance?.name ?: AppearanceMode.LIGHT.name)
                )
            }.getOrDefault(savedAppearance ?: AppearanceMode.LIGHT)

            val langCode = obj.optString("languageCode", savedLang ?: "en")

            SettingsState(
                autoConfirm = obj.optBoolean("autoConfirm", true),
                keypadType = runCatching {
                    KeypadType.valueOf(obj.optString("keypadType", KeypadType.PHONE.name))
                }.getOrDefault(KeypadType.PHONE),
                keypadSize = runCatching {
                    KeypadSize.valueOf(obj.optString("keypadSize", KeypadSize.NORMAL.name))
                }.getOrDefault(KeypadSize.NORMAL),
                soundsEnabled = obj.optBoolean("soundsEnabled", true),
                hapticsEnabled = obj.optBoolean("hapticsEnabled", true),
                languageCode = langCode,
                themeId = themeId,
                appearanceMode = appearanceMode,
                dailySetLength = obj.optInt("dailySetLength", 12),
                dailyEnabledOps = enabledOps,
                reminderEnabled = obj.optBoolean("reminderEnabled", true),
                reminderHour = obj.optInt("reminderHour", 19),
                reminderMinute = obj.optInt("reminderMinute", 0),
                streakWarningEnabled = obj.optBoolean("streakWarningEnabled", true),
                reminderDays = days,
                sameTimeForAllDays = obj.optBoolean("sameTimeForAllDays", true),
                perDayTimes = perDayMap,
                dailyGoalQuestions = obj.optInt("dailyGoalQuestions", 30),
                recordAnswersEnabled = obj.optBoolean("recordAnswersEnabled", true),
                showSourceChipInSmartPractice = obj.optBoolean("showSourceChipInSmartPractice", true),
                mistakeReminderEnabled = obj.optBoolean("mistakeReminderEnabled", true),
                weeklySummaryReminderEnabled = obj.optBoolean("weeklySummaryReminderEnabled", true)
            )
        }.getOrDefault(SettingsState())
    }

    fun settingsToJson(settings: SettingsState): String {
        return JSONObject().apply {
            put("autoConfirm", settings.autoConfirm)
            put("keypadType", settings.keypadType.name)
            put("keypadSize", settings.keypadSize.name)
            put("soundsEnabled", settings.soundsEnabled)
            put("hapticsEnabled", settings.hapticsEnabled)
            put("languageCode", settings.languageCode)
            put("themeId", settings.themeId.name)
            put("appearanceMode", settings.appearanceMode.name)
            put("dailySetLength", settings.dailySetLength)
            put("dailyEnabledOps", JSONArray(settings.dailyEnabledOps.map { it.name }))
            put("reminderEnabled", settings.reminderEnabled)
            put("reminderHour", settings.reminderHour)
            put("reminderMinute", settings.reminderMinute)
            put("streakWarningEnabled", settings.streakWarningEnabled)
            put("reminderDays", JSONArray(settings.reminderDays.toList()))
            put("sameTimeForAllDays", settings.sameTimeForAllDays)
            val perDayObj = JSONObject()
            settings.perDayTimes.forEach { (d, t) -> perDayObj.put(d.toString(), t) }
            put("perDayTimes", perDayObj)
            put("dailyGoalQuestions", settings.dailyGoalQuestions)
            put("recordAnswersEnabled", settings.recordAnswersEnabled)
            put("showSourceChipInSmartPractice", settings.showSourceChipInSmartPractice)
            put("mistakeReminderEnabled", settings.mistakeReminderEnabled)
            put("weeklySummaryReminderEnabled", settings.weeklySummaryReminderEnabled)
        }.toString()
    }

    fun saveSettings(settings: SettingsState, userId: String = activeUserId) {
        val json = settingsToJson(settings)
        setItemSync(scopedKey(KEY_SETTINGS, userId), json)
        setItemSync(KEY_THEME, settings.themeId.name)
        setItemSync(KEY_APPEARANCE_MODE, settings.appearanceMode.name)
        setItemSync(KEY_LANGUAGE, settings.languageCode)
    }

    fun loadScores(): List<ScoreRecord> = loadScoresForUser(activeUserId)

    fun loadScoresForUser(userId: String): List<ScoreRecord> {
        val raw = getItemSync(scopedKey(KEY_SCORES, userId)) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<ScoreRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    ScoreRecord(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        modeKey = o.optString("modeKey", ""),
                        mode = runCatching { QuizMode.valueOf(o.optString("mode")) }.getOrDefault(QuizMode.ADDITION),
                        difficulty = runCatching { Difficulty.valueOf(o.optString("difficulty")) }.getOrDefault(Difficulty.EASY),
                        questionCount = o.optInt("questionCount", 10),
                        correctCount = o.optInt("correctCount", 0),
                        totalTimeMs = o.optLong("totalTimeMs", 0L),
                        accuracy = o.optDouble("accuracy", 0.0),
                        timestamp = o.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveScores(scores: List<ScoreRecord>, userId: String = activeUserId) {
        val arr = JSONArray()
        scores.takeLast(200).forEach { s ->
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("modeKey", s.modeKey)
                    put("mode", s.mode.name)
                    put("difficulty", s.difficulty.name)
                    put("questionCount", s.questionCount)
                    put("correctCount", s.correctCount)
                    put("totalTimeMs", s.totalTimeMs)
                    put("accuracy", s.accuracy)
                    put("timestamp", s.timestamp)
                }
            )
        }
        setItemSync(scopedKey(KEY_SCORES, userId), arr.toString())
    }

    fun loadMistakes(userId: String = activeUserId): List<Problem> {
        val raw = getItemSync(scopedKey(KEY_MISTAKES, userId)) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = mutableListOf<Problem>()
            for (i in 0 until arr.length()) {
                list.add(jsonToProblem(arr.getJSONObject(i)))
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveMistakes(problems: List<Problem>, userId: String = activeUserId) {
        val arr = JSONArray()
        problems.distinctBy { it.normalizedKey.ifEmpty { it.displayBase } }.takeLast(100).forEach {
            arr.put(problemToJson(it))
        }
        setItemSync(scopedKey(KEY_MISTAKES, userId), arr.toString())
    }

    fun loadCustomConfig(userId: String = activeUserId): CustomConfig {
        val raw = getItemSync(scopedKey(KEY_CUSTOM_CONFIG, userId)) ?: return CustomConfig()
        return runCatching {
            val o = JSONObject(raw)
            val opsArr = o.optJSONArray("ops")
            val ops = mutableSetOf<MathOp>()
            if (opsArr != null) {
                for (i in 0 until opsArr.length()) {
                    runCatching { MathOp.valueOf(opsArr.getString(i)) }.getOrNull()?.let { ops.add(it) }
                }
            }
            if (ops.isEmpty()) ops.addAll(listOf(MathOp.ADD, MathOp.SUB, MathOp.MUL, MathOp.DIV))
            val timeLim = o.optInt("timeLimitMinutes", -1).takeIf { it > 0 }
            CustomConfig(
                ops = ops,
                minA = o.optInt("minA", 2),
                maxA = o.optInt("maxA", 50),
                minB = o.optInt("minB", 2),
                maxB = o.optInt("maxB", 50),
                questionCount = o.optInt("questionCount", 10),
                timeLimitMinutes = timeLim
            )
        }.getOrDefault(CustomConfig())
    }

    fun saveCustomConfig(config: CustomConfig, userId: String = activeUserId) {
        val o = JSONObject().apply {
            put("ops", JSONArray(config.ops.map { it.name }))
            put("minA", config.minA)
            put("maxA", config.maxA)
            put("minB", config.minB)
            put("maxB", config.maxB)
            put("questionCount", config.questionCount)
            put("timeLimitMinutes", config.timeLimitMinutes ?: -1)
        }
        setItemSync(scopedKey(KEY_CUSTOM_CONFIG, userId), o.toString())
    }

    fun loadTableStats(userId: String = activeUserId): Map<Int, TableStat> {
        val raw = getItemSync(scopedKey(KEY_TABLE_STATS, userId)) ?: return emptyMap()
        return runCatching {
            val arr = JSONArray(raw)
            val map = mutableMapOf<Int, TableStat>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val num = o.getInt("tableNumber")
                val prev = o.optLong("prevTimeMs", -1L).takeIf { it > 0L }
                map[num] = TableStat(
                    tableNumber = num,
                    bestTimeMs = o.getLong("bestTimeMs"),
                    lastTimeMs = o.getLong("lastTimeMs"),
                    prevTimeMs = prev
                )
            }
            map
        }.getOrDefault(emptyMap())
    }

    fun saveTableStats(stats: Map<Int, TableStat>, userId: String = activeUserId) {
        val arr = JSONArray()
        stats.values.forEach { st ->
            arr.put(
                JSONObject().apply {
                    put("tableNumber", st.tableNumber)
                    put("bestTimeMs", st.bestTimeMs)
                    put("lastTimeMs", st.lastTimeMs)
                    put("prevTimeMs", st.prevTimeMs ?: -1L)
                }
            )
        }
        setItemSync(scopedKey(KEY_TABLE_STATS, userId), arr.toString())
    }

    fun loadDailyState(todayDate: String, settings: SettingsState, userId: String = activeUserId): DailyState? {
        val raw = getItemSync(scopedKey(KEY_DAILY_WORKOUT, userId)) ?: return null
        return parseDailyStateJson(raw, todayDate, settings)
    }

    fun parseDailyStateJson(raw: String, todayDate: String, settings: SettingsState): DailyState? {
        return runCatching {
            val o = JSONObject(raw)
            val savedDate = o.optString("date", todayDate)
            val installId = o.optString("installId", getOrCreateInstallId())
            val setLength = o.optInt("setLength", settings.dailySetLength)

            val opsArr = o.optJSONArray("enabledOps")
            val enabledOps = mutableSetOf<SkillOp>()
            if (opsArr != null) {
                for (i in 0 until opsArr.length()) {
                    runCatching { SkillOp.valueOf(opsArr.getString(i)) }.getOrNull()?.let { enabledOps.add(it) }
                }
            }
            if (enabledOps.size < 2) enabledOps.addAll(settings.dailyEnabledOps)

            val skillsObj = o.optJSONObject("skills")
            val skills = mutableMapOf<SkillOp, SkillState>()
            for (op in SkillOp.entries) {
                val so = skillsObj?.optJSONObject(op.name)
                skills[op] = if (so != null) {
                    SkillState(
                        op = op,
                        level = so.optInt("level", 2),
                        recentAccuracy = so.optDouble("recentAccuracy", 0.7),
                        recentTimeMs = so.optDouble("recentTimeMs", 4000.0),
                        totalAnswered = so.optInt("totalAnswered", 0)
                    )
                } else {
                    SkillState(op = op)
                }
            }

            val streakObj = o.optJSONObject("streak")
            val streak = if (streakObj != null) {
                val frozenArr = streakObj.optJSONArray("frozenDates")
                val frozen = mutableSetOf<String>()
                if (frozenArr != null) {
                    for (i in 0 until frozenArr.length()) frozen.add(frozenArr.getString(i))
                }
                val compArr = streakObj.optJSONArray("completedDates")
                val completed = mutableSetOf<String>()
                if (compArr != null) {
                    for (i in 0 until compArr.length()) completed.add(compArr.getString(i))
                }
                StreakState(
                    current = streakObj.optInt("current", 0),
                    best = streakObj.optInt("best", 0),
                    freezesAvailable = streakObj.optInt("freezesAvailable", 0),
                    lastCompletedDate = streakObj.optString("lastCompletedDate", "").takeIf { it.isNotBlank() },
                    frozenDates = frozen,
                    completedDates = completed,
                    savedByFreezeToday = streakObj.optBoolean("savedByFreezeToday", false)
                )
            } else {
                StreakState()
            }

            val probArr = o.optJSONArray("todayProblems")
            val problems = mutableListOf<Problem>()
            if (probArr != null) {
                for (i in 0 until probArr.length()) {
                    problems.add(jsonToProblem(probArr.getJSONObject(i)))
                }
            }

            val ansArr = o.optJSONArray("todayAnswers")
            val answers = mutableListOf<AnsweredProblem>()
            if (ansArr != null) {
                for (i in 0 until ansArr.length()) {
                    val ao = ansArr.getJSONObject(i)
                    answers.add(
                        AnsweredProblem(
                            problem = jsonToProblem(ao.getJSONObject("problem")),
                            userAnswer = ao.getLong("userAnswer"),
                            isCorrect = ao.getBoolean("isCorrect"),
                            timeMs = ao.getLong("timeMs")
                        )
                    )
                }
            }

            val recKeysArr = o.optJSONArray("recentQuestionKeys")
            val recKeys = mutableListOf<String>()
            if (recKeysArr != null) {
                for (i in 0 until recKeysArr.length()) recKeys.add(recKeysArr.getString(i))
            }

            val lvlChangesArr = o.optJSONArray("lastLevelChanges")
            val lvlChanges = mutableListOf<LevelChangeInfo>()
            if (lvlChangesArr != null) {
                for (i in 0 until lvlChangesArr.length()) {
                    val lo = lvlChangesArr.getJSONObject(i)
                    lvlChanges.add(
                        LevelChangeInfo(
                            op = runCatching { SkillOp.valueOf(lo.getString("op")) }.getOrDefault(SkillOp.ADD),
                            oldLevel = lo.getInt("oldLevel"),
                            newLevel = lo.getInt("newLevel"),
                            correctCount = lo.getInt("correctCount"),
                            totalCount = lo.getInt("totalCount"),
                            avgTimeMs = lo.getLong("avgTimeMs"),
                            targetTimeMs = lo.getLong("targetTimeMs")
                        )
                    )
                }
            }

            DailyState(
                date = savedDate,
                installId = installId,
                setLength = setLength,
                enabledOps = enabledOps,
                skills = skills,
                streak = streak,
                todayProblems = problems,
                todayAnswers = answers,
                isCompletedToday = o.optBoolean("isCompletedToday", false),
                yesterdayAvgTimeMs = o.optLong("yesterdayAvgTimeMs", -1L).takeIf { it > 0L },
                yesterdayAccuracy = o.optDouble("yesterdayAccuracy", -1.0).takeIf { it >= 0.0 },
                recentQuestionKeys = recKeys,
                lastLevelChanges = lvlChanges,
                rebuiltNotice = o.optBoolean("rebuiltNotice", false)
            )
        }.getOrNull()
    }

    fun dailyStateToJson(state: DailyState): String {
        val o = JSONObject().apply {
            put("date", state.date)
            put("installId", state.installId)
            put("setLength", state.setLength)
            put("enabledOps", JSONArray(state.enabledOps.map { it.name }))

            val skillsObj = JSONObject()
            state.skills.forEach { (op, st) ->
                skillsObj.put(
                    op.name,
                    JSONObject().apply {
                        put("level", st.level)
                        put("recentAccuracy", st.recentAccuracy)
                        put("recentTimeMs", st.recentTimeMs)
                        put("totalAnswered", st.totalAnswered)
                    }
                )
            }
            put("skills", skillsObj)

            put(
                "streak",
                JSONObject().apply {
                    put("current", state.streak.current)
                    put("best", state.streak.best)
                    put("freezesAvailable", state.streak.freezesAvailable)
                    put("lastCompletedDate", state.streak.lastCompletedDate ?: "")
                    put("frozenDates", JSONArray(state.streak.frozenDates.toList()))
                    put("completedDates", JSONArray(state.streak.completedDates.toList()))
                    put("savedByFreezeToday", state.streak.savedByFreezeToday)
                }
            )

            val probArr = JSONArray()
            state.todayProblems.forEach { probArr.put(problemToJson(it)) }
            put("todayProblems", probArr)

            val ansArr = JSONArray()
            state.todayAnswers.forEach { ans ->
                ansArr.put(
                    JSONObject().apply {
                        put("problem", problemToJson(ans.problem))
                        put("userAnswer", ans.userAnswer)
                        put("isCorrect", ans.isCorrect)
                        put("timeMs", ans.timeMs)
                    }
                )
            }
            put("todayAnswers", ansArr)
            put("isCompletedToday", state.isCompletedToday)
            put("yesterdayAvgTimeMs", state.yesterdayAvgTimeMs ?: -1L)
            put("yesterdayAccuracy", state.yesterdayAccuracy ?: -1.0)
            put("recentQuestionKeys", JSONArray(state.recentQuestionKeys))

            val lvlArr = JSONArray()
            state.lastLevelChanges.forEach { lc ->
                lvlArr.put(
                    JSONObject().apply {
                        put("op", lc.op.name)
                        put("oldLevel", lc.oldLevel)
                        put("newLevel", lc.newLevel)
                        put("correctCount", lc.correctCount)
                        put("totalCount", lc.totalCount)
                        put("avgTimeMs", lc.avgTimeMs)
                        put("targetTimeMs", lc.targetTimeMs)
                    }
                )
            }
            put("lastLevelChanges", lvlArr)
            put("rebuiltNotice", state.rebuiltNotice)
        }
        return o.toString()
    }

    fun saveDailyState(state: DailyState, userId: String = activeUserId) {
        setItemSync(scopedKey(KEY_DAILY_WORKOUT, userId), dailyStateToJson(state))
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

    private fun jsonToProblem(o: JSONObject): Problem = Problem(
        id = o.optString("id", "q"),
        op = runCatching { MathOp.valueOf(o.optString("op", "ADD")) }.getOrDefault(MathOp.ADD),
        a = o.optInt("a", 1),
        b = o.optInt("b", 1),
        answer = o.optLong("answer", 2L),
        displayBase = o.optString("displayBase", "1 + 1"),
        superscriptExponent = o.optString("superscriptExponent", "").takeIf { it.isNotEmpty() },
        level = o.optInt("level", 1),
        normalizedKey = o.optString("normalizedKey", "")
    )
}
