package com.example.model

enum class MathOp(val symbol: String, val labelKey: String) {
    ADD("+", "mode.addition"),
    SUB("−", "mode.subtraction"),
    MUL("×", "mode.multiplication"),
    DIV("÷", "mode.division"),
    SQRT("√", "mode.sqrt"),
    POW("^", "mode.exponents"),
    CBRT("∛", "mode.cbrt")
}

enum class SkillOp(val mathOp: MathOp, val labelKey: String) {
    ADD(MathOp.ADD, "mode.addition"),
    SUB(MathOp.SUB, "mode.subtraction"),
    MUL(MathOp.MUL, "mode.multiplication"),
    DIV(MathOp.DIV, "mode.division"),
    SQRT(MathOp.SQRT, "mode.sqrt"),
    POW(MathOp.POW, "mode.exponents");

    val titleKey: String get() = labelKey
}

enum class DayStatus {
    DONE,
    FROZEN,
    MISSED,
    TODAY,
    FUTURE
}

enum class Difficulty(val index: Int, val labelKey: String) {
    EASY(0, "diff.easy"),
    MEDIUM(1, "diff.medium"),
    HARD(2, "diff.hard"),
    CHALLENGING(3, "diff.challenging"),
    EXPERT(4, "diff.expert");

    companion object {
        fun fromIndex(idx: Int): Difficulty = entries.getOrElse(idx.coerceIn(0, 4)) { EASY }
    }
}

enum class QuizMode(
    val id: String,
    val titleKey: String,
    val subtitleKey: String,
    val category: String
) {
    ADDITION("addition", "mode.addition", "mode.addition_sub", "basics"),
    SUBTRACTION("subtraction", "mode.subtraction", "mode.subtraction_sub", "basics"),
    MULTIPLICATION("multiplication", "mode.multiplication", "mode.multiplication_sub", "basics"),
    DIVISION("division", "mode.division", "mode.division_sub", "basics"),

    MIXED("mixed", "mode.mixed", "mode.mixed_sub", "combined"),
    ADD_SUB("add_sub", "mode.add_sub", "mode.add_sub_sub", "combined"),
    MULT_DIV("mult_div", "mode.mult_div", "mode.mult_div_sub", "combined"),

    SQUARE_ROOT("sqrt", "mode.sqrt", "mode.sqrt_sub", "advanced"),
    EXPONENTS("exponents", "mode.exponents", "mode.exponents_sub", "advanced"),
    CUBE_ROOT("cbrt", "mode.cbrt", "mode.cbrt_sub", "advanced"),
    MULTIPLICATION_TABLES("tables", "mode.tables", "mode.tables_sub", "advanced"),
    CUSTOMIZABLE("custom", "mode.custom", "mode.custom_sub", "advanced"),

    ARITHMETIC_MEMORY("memory", "mode.memory", "mode.memory_sub", "game"),
    PRACTICE_MISTAKES("mistakes", "mode.mistakes", "mode.mistakes_sub", "mistakes")
}

data class Problem(
    val id: String,
    val op: MathOp,
    val a: Int,
    val b: Int,
    val answer: Long,
    val displayBase: String,
    val superscriptExponent: String? = null,
    val level: Int = 1,
    val normalizedKey: String = "${op.name}:${minOf(a, b)}:${maxOf(a, b)}"
) {
    val digitCount: Int
        get() = answer.toString().length
}

data class AnsweredProblem(
    val problem: Problem,
    val userAnswer: Long,
    val isCorrect: Boolean,
    val timeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

data class MistakeItem(
    val problem: Problem,
    val wrongAnswer: Long,
    val missedCount: Int = 1,
    val lastMissedAt: Long = System.currentTimeMillis()
)

data class MemoryStep(
    val op: MathOp,
    val operand: Int,
    val previousValue: Int,
    val runningValue: Int,
    val displayStep: String = "${op.symbol} $operand"
)

data class MemoryChain(
    val startNumber: Int,
    val steps: List<MemoryStep>,
    val finalAnswer: Int,
    val difficulty: Difficulty = Difficulty.EASY
)

data class CustomConfig(
    val ops: Set<MathOp> = setOf(MathOp.ADD, MathOp.SUB, MathOp.MUL, MathOp.DIV),
    val minA: Int = 2,
    val maxA: Int = 50,
    val minB: Int = 2,
    val maxB: Int = 50,
    val questionCount: Int = 10,
    val timeLimitMinutes: Int? = null
)

data class SkillState(
    val op: SkillOp,
    val level: Int = 3,
    val recentAccuracy: Double = 0.75,
    val recentTimeMs: Double = 3800.0,
    val totalAnswered: Int = 0
)

data class StreakState(
    val current: Int = 0,
    val best: Int = 0,
    val freezesAvailable: Int = 0,
    val lastCompletedDate: String? = null,
    val frozenDates: Set<String> = emptySet(),
    val completedDates: Set<String> = emptySet(),
    val savedByFreezeToday: Boolean = false
)

data class LevelChangeInfo(
    val op: SkillOp,
    val oldLevel: Int,
    val newLevel: Int,
    val correctCount: Int = 0,
    val totalCount: Int = 0,
    val avgTimeMs: Long = 0L,
    val targetTimeMs: Long = 0L
)

data class DailyState(
    val date: String,
    val installId: String,
    val setLength: Int = 12,
    val enabledOps: Set<SkillOp> = SkillOp.entries.toSet(),
    val streak: StreakState = StreakState(),
    val skills: Map<SkillOp, SkillState> = SkillOp.entries.associateWith { SkillState(op = it) },
    val todayProblems: List<Problem> = emptyList(),
    val todayAnswers: List<AnsweredProblem> = emptyList(),
    val isCompletedToday: Boolean = false,
    val yesterdayAvgTimeMs: Long? = null,
    val yesterdayAccuracy: Double? = null,
    val recentQuestionKeys: List<String> = emptyList(),
    val lastLevelChanges: List<LevelChangeInfo> = emptyList(),
    val rebuiltNotice: Boolean = false
)

enum class KeypadType { PHONE, CALCULATOR }
enum class KeypadSize { NORMAL, LARGE }

/**
 * Primary Appearance Mode (Settings -> Appearance -> Theme):
 * Light, Dark, or System. Defaults to LIGHT on first install.
 */
enum class AppearanceMode(val labelKey: String) {
    LIGHT("theme.mode_light"),
    DARK("theme.mode_dark"),
    SYSTEM("theme.mode_system")
}

/**
 * Cloud Sync Status shown in the Account card.
 */
enum class SyncStatusState(val labelKey: String) {
    SYNCED("auth.sync_status_synced"),
    PENDING("auth.sync_status_pending"),
    OFFLINE("auth.sync_status_offline")
}

/**
 * Accent colour themes (each supports both Light and soft Blue-Grey Dark mode variants).
 */
enum class AppThemeId(
    val labelKey: String,
    val isDark: Boolean
) {
    LIGHT("theme.light", false),
    DARK("theme.dark", true),
    BLACK_AMOLED("theme.black", true),
    DARK_GREEN("theme.dark_green", true),
    LIGHT_GREEN("theme.light_green", false),
    DARK_BLUE("theme.dark_blue", true),
    DARK_PURPLE("theme.dark_purple", true),
    LIGHT_PURPLE("theme.light_purple", false),
    LIGHT_RED("theme.light_red", false),
    LIGHT_ORANGE("theme.light_orange", false)
}

data class SettingsState(
    val autoConfirm: Boolean = true,
    val keypadType: KeypadType = KeypadType.PHONE,
    val keypadSize: KeypadSize = KeypadSize.NORMAL,
    val soundsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val languageCode: String = "en",
    val appearanceMode: AppearanceMode = AppearanceMode.LIGHT,
    val themeId: AppThemeId = AppThemeId.LIGHT,
    val dailySetLength: Int = 12,
    val dailyEnabledOps: Set<SkillOp> = SkillOp.entries.toSet(),
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 19,
    val reminderMinute: Int = 0,
    val streakWarningEnabled: Boolean = true,
    val reminderDays: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val sameTimeForAllDays: Boolean = true,
    val perDayTimes: Map<Int, String> = mapOf(
        1 to "19:00", 2 to "19:00", 3 to "19:00", 4 to "19:00",
        5 to "19:00", 6 to "19:00", 7 to "19:00"
    ),
    val dailyGoalQuestions: Int = 30,
    val recordAnswersEnabled: Boolean = true,
    val showSourceChipInSmartPractice: Boolean = true,
    val mistakeReminderEnabled: Boolean = true,
    val weeklySummaryReminderEnabled: Boolean = true
)

data class ScoreRecord(
    val id: String,
    val modeKey: String,
    val mode: QuizMode,
    val difficulty: Difficulty,
    val questionCount: Int,
    val correctCount: Int,
    val totalTimeMs: Long,
    val accuracy: Double,
    val timestamp: Long
)

data class TableStat(
    val tableNumber: Int,
    val bestTimeMs: Long,
    val lastTimeMs: Long,
    val prevTimeMs: Long? = null
)

enum class PresetAvatar(val id: String, val label: String, val badgeText: String, val colorHex: Long) {
    FOX("fox", "Fox", "FX", 0xFFFF6D00),
    OWL("owl", "Owl", "OW", 0xFF7C4DFF),
    TIGER("tiger", "Tiger", "TG", 0xFFFF9100),
    DOLPHIN("dolphin", "Dolphin", "DL", 0xFF00B0FF),
    PANDA("panda", "Panda", "PD", 0xFF00C853),
    FALCON("falcon", "Falcon", "FL", 0xFF2979FF),
    KOALA("koala", "Koala", "KL", 0xFF8D6E63),
    LION("lion", "Lion", "LN", 0xFFFFAB00);

    companion object {
        fun fromId(id: String): PresetAvatar =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FOX
    }
}

data class UserProfileState(
    val userId: String = "guest",
    val isGuest: Boolean = true,
    val displayName: String = "",
    val avatarId: String = "fox",
    val email: String? = null,
    val provider: String = "guest", // "google", "email", or "guest"
    val emailVerified: Boolean = true,
    val dailyGoal: Int = 20,
    val appearanceMode: AppearanceMode = AppearanceMode.LIGHT,
    val lastSyncedTimestamp: Long? = null,
    val lastSyncDay: String? = null,
    val isProfileComplete: Boolean = false
)

enum class PasswordStrength {
    TOO_SHORT,
    WEAK,
    FAIR,
    STRONG;

    companion object {
        fun evaluate(password: String): PasswordStrength {
            if (password.length < 8) return TOO_SHORT
            var classes = 0
            if (password.any { it.isLowerCase() }) classes++
            if (password.any { it.isUpperCase() }) classes++
            if (password.any { it.isDigit() }) classes++
            if (password.any { !it.isLetterOrDigit() }) classes++
            return when {
                password.length >= 10 && classes >= 3 -> STRONG
                classes >= 3 || (password.length >= 10 && classes >= 2) -> STRONG
                classes >= 2 -> FAIR
                else -> WEAK
            }
        }
    }
}

data class FirebaseEnvConfig(
    val apiKey: String,
    val projectId: String,
    val appId: String,
    val webClientId: String,
    val firestoreDatabaseId: String
) {
    val authDomain: String
        get() = if (projectId.isNotBlank()) "$projectId.firebaseapp.com" else ""
}
