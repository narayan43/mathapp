package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.AnalyticsService
import com.example.core.AnswerLog
import com.example.core.AnswerLogService
import com.example.core.AuthErrorCode
import com.example.core.AuthFlowException
import com.example.core.AuthOutcome
import com.example.core.AuthService
import com.example.core.AuthUser
import com.example.core.CategoryBeforeAfterDelta
import com.example.core.CategoryDetailSnapshot
import com.example.core.CategoryRegistry
import com.example.core.CategorySortOrder
import com.example.core.DailyEngine
import com.example.core.I18nService
import com.example.core.InsightsDashboardSnapshot
import com.example.core.InsightsDateRange
import com.example.core.MistakeBookEntry
import com.example.core.NotificationScheduler
import com.example.core.QuadrantClass
import com.example.core.QuestionGenerator
import com.example.core.SmartPracticePlan
import com.example.core.SmartPracticeService
import com.example.core.SoundHapticService
import com.example.core.StatsService
import com.example.core.StorageService
import com.example.data.CloudDailyProgress
import com.example.data.CloudDaySyncDoc
import com.example.data.CloudScoreDoc
import com.example.data.CloudSyncRepository
import com.example.data.CloudUserProfile
import com.example.data.DailySyncGate
import com.example.model.AnsweredProblem
import com.example.model.AppThemeId
import com.example.model.AppearanceMode
import com.example.model.CustomConfig
import com.example.model.DailyState
import com.example.model.Difficulty
import com.example.model.KeypadSize
import com.example.model.KeypadType
import com.example.model.MathOp
import com.example.model.MemoryChain
import com.example.model.Problem
import com.example.model.QuizMode
import com.example.model.ScoreRecord
import com.example.model.SettingsState
import com.example.model.SkillOp
import com.example.model.SyncStatusState
import com.example.model.TableStat
import com.example.model.UserProfileState
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

enum class BottomTab { HOME, PROGRESS, SETTINGS }

sealed class ScreenRoute {
    data class AuthHub(val isSignUpMode: Boolean = false) : ScreenRoute()
    data object VerifyEmail : ScreenRoute()
    data object ProfileSetup : ScreenRoute()
    data object MainTabs : ScreenRoute()
    data class PreGameSetup(val mode: QuizMode) : ScreenRoute()
    data object ActiveQuiz : ScreenRoute()
    data object QuizResult : ScreenRoute()
    data object MemorySetup : ScreenRoute()
    data object MemoryGame : ScreenRoute()
    data object MemoryResult : ScreenRoute()
    data object TablesHub : ScreenRoute()
    data object CustomSetup : ScreenRoute()
    data class CategoryDetail(val categoryId: String) : ScreenRoute()
}

/**
 * AuthGuard enforces route access rules:
 * - Signed-out users (authUser == null) ONLY see ScreenRoute.AuthHub.
 * - Email users whose email is not yet verified see ScreenRoute.VerifyEmail.
 * - New Google users who haven't completed profile setup see ScreenRoute.ProfileSetup.
 * - Any signed-in user (including Guest) goes to Home (ScreenRoute.MainTabs) or their requested in-app route.
 */
object AuthGuard {
    fun canAccessProtectedRoute(authUser: AuthUser?, isGuestExpired: Boolean = false): Boolean =
        authUser != null && !(authUser.isGuest && isGuestExpired)

    fun resolveRoute(
        authUser: AuthUser?,
        profile: UserProfileState,
        requestedRoute: ScreenRoute = ScreenRoute.MainTabs,
        isGuestExpired: Boolean = false
    ): ScreenRoute {
        if (authUser == null) {
            return if (requestedRoute is ScreenRoute.AuthHub) {
                requestedRoute
            } else {
                ScreenRoute.AuthHub(isSignUpMode = false)
            }
        }
        if (authUser.isGuest && isGuestExpired) {
            return ScreenRoute.AuthHub(isSignUpMode = true)
        }
        if (authUser.provider == "email" && !authUser.emailVerified) {
            return ScreenRoute.VerifyEmail
        }
        if (!authUser.isGuest && !profile.isProfileComplete && profile.displayName.isBlank()) {
            return ScreenRoute.ProfileSetup
        }
        return when (requestedRoute) {
            is ScreenRoute.AuthHub, is ScreenRoute.VerifyEmail -> ScreenRoute.MainTabs
            else -> requestedRoute
        }
    }
}

enum class FlashFeedback { NONE, CORRECT, WRONG }

enum class MemoryPhase { SHOW_START, SHOW_STEP, ENTER_ANSWER }

data class ActiveQuizSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val mode: QuizMode,
    val difficulty: Difficulty = Difficulty.EASY,
    val isDailyOfficial: Boolean = false,
    val isDailyExtra: Boolean = false,
    val isMistakesReplay: Boolean = false,
    val isDataPractice: Boolean = false,
    val smartPracticePlan: SmartPracticePlan? = null,
    val questionCategories: List<String> = emptyList(),
    val questionSources: List<String> = emptyList(),
    val singleTableNumber: Int? = null,
    val timeLimitSeconds: Int? = null,
    val problems: List<Problem>,
    val currentIndex: Int = 0,
    val answers: List<AnsweredProblem> = emptyList(),
    val typedInput: String = "",
    val elapsedMs: Long = 0L,
    val questionStartMs: Long = 0L,
    val countdownRemaining: Int = 3,
    val isTimerPaused: Boolean = false,
    val flashFeedback: FlashFeedback = FlashFeedback.NONE
) {
    val currentProblem: Problem?
        get() = problems.getOrNull(currentIndex)
    val currentQuestionCategory: String?
        get() = questionCategories.getOrNull(currentIndex)
    val currentQuestionSource: String?
        get() = questionSources.getOrNull(currentIndex)
    val correctCount: Int
        get() = answers.count { it.isCorrect }
    val wrongCount: Int
        get() = answers.count { !it.isCorrect }
}

data class ActiveMemorySession(
    val sessionId: String = UUID.randomUUID().toString(),
    val difficulty: Difficulty,
    val stepsCount: Int,
    val chain: MemoryChain,
    val phase: MemoryPhase = MemoryPhase.SHOW_START,
    val currentStepIndex: Int = 0,
    val typedInput: String = "",
    val userFinalAnswer: Int? = null,
    val isCorrect: Boolean = false,
    val elapsedMs: Long = 0L,
    val questionStartMs: Long = System.currentTimeMillis()
)

data class NumeraUiState(
    val currentTab: BottomTab = BottomTab.HOME,
    val route: ScreenRoute = ScreenRoute.AuthHub(isSignUpMode = false),
    val isAuthenticated: Boolean = false,
    val settings: SettingsState = SettingsState(),
    val userProfile: UserProfileState = UserProfileState(),
    val authLoading: Boolean = false,
    val authError: String? = null,
    val showGuestMergePrompt: Boolean = false,
    val showLogoutConfirm: Boolean = false,
    val isSyncing: Boolean = false,
    val syncStatus: SyncStatusState = SyncStatusState.SYNCED,
    val pendingSyncItemsCount: Int = 0,
    val lastManualSyncMs: Long? = null,
    val lastSyncErrorMessage: String? = null,
    val dailyState: DailyState,
    val scores: List<ScoreRecord> = emptyList(),
    val mistakes: List<Problem> = emptyList(),
    val customConfig: CustomConfig = CustomConfig(),
    val tableStats: Map<Int, TableStat> = emptyMap(),
    val selectedTables: Set<Int> = setOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
    val activeLearnTable: Int = 7,
    val tablesTabIndex: Int = 0, // 0 = Learn, 1 = Practice
    val setupQuestionCount: Int = 10,
    val setupDifficulty: Difficulty = Difficulty.EASY,
    val memoryDifficulty: Difficulty = Difficulty.EASY,
    val memoryStepsCount: Int = 6,
    val activeQuiz: ActiveQuizSession? = null,
    val activeMemory: ActiveMemorySession? = null,
    val lastCompletedQuiz: ActiveQuizSession? = null,
    // Insights & Smart Practice state
    val insightsSubTab: Int = 0, // 0 = Overview, 1 = Categories, 2 = History
    val insightsDateRange: InsightsDateRange = InsightsDateRange.DAYS_30,
    val insightsOpFilter: String? = null,
    val categorySortOrder: CategorySortOrder = CategorySortOrder.WEAKEST,
    val historyFilter: String = "all", // "all" | "wrong" | "slow"
    val expandedHistoryDay: String? = null,
    val dashboardSnapshot: InsightsDashboardSnapshot,
    val activeCategoryDetail: CategoryDetailSnapshot? = null,
    val mistakeBook: List<MistakeBookEntry> = emptyList(),
    val rawAnswerLogs: List<AnswerLog> = emptyList(),
    val showSmartPracticeSheet: Boolean = false,
    val smartPracticeCount: Int = 20,
    val smartPracticeCategoryFilter: String? = null,
    val lastSmartPracticeDeltas: List<CategoryBeforeAfterDelta> = emptyList(),
    val lastSmartPracticeResolvedMistakes: List<Problem> = emptyList(),
    val toastMessage: String? = null,
    val guestDaysRemaining: Int = 7,
    val isGuestExpired: Boolean = false,
    val showGuestExpiredDialog: Boolean = false
)

class NumeraViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MY_SUPPORT_EMAIL = "<MY_SUPPORT_EMAIL>"
    }

    private val storage = StorageService(application)
    val answerLogService = AnswerLogService(application)
    val i18n = I18nService(application)
    private val soundHaptic = SoundHapticService(application)
    private val authService = AuthService(application)
    private val cloudRepo: CloudSyncRepository? = runCatching {
        val dbId = authService.getFirebaseEnvConfig().firestoreDatabaseId.ifBlank {
            application.getString(R.string.firestore_database_id)
        }
        val firestore = if (dbId.isBlank() || dbId == "(default)") {
            FirebaseFirestore.getInstance()
        } else {
            FirebaseFirestore.getInstance(dbId)
        }
        CloudSyncRepository(firestore)
    }.getOrNull()

    private var timerJob: Job? = null
    private var memoryJob: Job? = null
    private var autoSyncJob: Job? = null

    private var pendingGuestUidForMerge: String? = null
    private var pendingCloudProgressForMerge: CloudDailyProgress? = null
    private var pendingCloudScoresForMerge: List<CloudScoreDoc> = emptyList()
    private var pendingCloudDaysForMerge: List<CloudDaySyncDoc> = emptyList()
    private var pendingCloudProfileForMerge: CloudUserProfile? = null

    private val _uiState: MutableStateFlow<NumeraUiState>

    init {
        val initialAuthUser = authService.currentUser.value
        val activeUserId = initialAuthUser?.uid ?: "guest"
        storage.setActiveUser(activeUserId)

        val loadedSettings = storage.loadSettings(activeUserId)
        i18n.setLanguage(loadedSettings.languageCode)

        val todayStr = LocalDate.now().toString()
        val loadedMistakes = storage.loadMistakes(activeUserId)
        answerLogService.rollUpOlderThan90Days(activeUserId, todayStr)
        answerLogService.seedMistakeBookFromLegacyMistakes(activeUserId, loadedMistakes)

        val initialRawLogs = answerLogService.loadAnswerLogs(activeUserId)
        val initialAggs = answerLogService.loadDailyAggregates(activeUserId)
        val initialMistakeBook = answerLogService.loadMistakeBook(activeUserId, includeMastered = false)
        val initialSnapshot = StatsService.buildDashboardSnapshot(
            rawLogs = initialRawLogs,
            rolledAggregates = initialAggs,
            dateRange = InsightsDateRange.DAYS_30,
            opFilter = null,
            todayStr = todayStr
        )
        val needsWorkCats = initialSnapshot.categoryStats
            .filter { it.quadrant == QuadrantClass.NEEDS_WORK || it.weaknessScore >= 0.45 }
            .map { it.categoryId }
            .toSet()

        val installId = storage.getOrCreateInstallId()
        val initialDaily = loadOrBuildTodayDailyState(todayStr, installId, loadedSettings, needsWorkCats)
            .copy(streak = initialSnapshot.practiceStreakState)
        storage.saveDailyState(initialDaily, activeUserId)

        val storedProfile = storage.loadUserProfile(activeUserId)
        val initialProfile = if (initialAuthUser != null) {
            storedProfile.copy(
                userId = initialAuthUser.uid,
                isGuest = initialAuthUser.isGuest,
                displayName = storedProfile.displayName.ifBlank { initialAuthUser.displayName },
                email = initialAuthUser.email ?: storedProfile.email,
                provider = initialAuthUser.provider,
                emailVerified = initialAuthUser.emailVerified
            )
        } else {
            storedProfile.copy(userId = "guest", isGuest = true, provider = "guest")
        }

        val isGuestExpired = authService.isGuestExpired()
        val daysLeft = authService.getGuestDaysRemaining()

        val initialRoute = AuthGuard.resolveRoute(
            authUser = initialAuthUser,
            profile = initialProfile,
            requestedRoute = ScreenRoute.MainTabs,
            isGuestExpired = isGuestExpired
        )
        val initialSyncQueue = storage.loadSyncQueue(activeUserId)

        _uiState = MutableStateFlow(
            NumeraUiState(
                route = initialRoute,
                isAuthenticated = (initialAuthUser != null),
                settings = loadedSettings,
                userProfile = initialProfile,
                syncStatus = computeSyncStatus(activeUserId, initialProfile.isGuest),
                pendingSyncItemsCount = computePendingSyncItemsCount(activeUserId, initialRawLogs),
                lastManualSyncMs = initialSyncQueue.lastManualSyncMs,
                dailyState = initialDaily,
                scores = storage.loadScoresForUser(activeUserId),
                mistakes = loadedMistakes,
                customConfig = storage.loadCustomConfig(activeUserId),
                tableStats = storage.loadTableStats(activeUserId),
                guestDaysRemaining = daysLeft,
                isGuestExpired = isGuestExpired,
                showGuestExpiredDialog = (initialAuthUser?.isGuest == true && isGuestExpired),
                dashboardSnapshot = initialSnapshot,
                mistakeBook = initialMistakeBook,
                rawAnswerLogs = initialRawLogs,
                expandedHistoryDay = todayStr
            )
        )

        NotificationScheduler.rescheduleNotifications(
            context = getApplication(),
            settings = loadedSettings,
            isDailyDoneToday = initialSnapshot.totalQuestionsToday >= 10,
            currentStreak = initialSnapshot.practiceStreakCurrent,
            dueMistakesCount = initialMistakeBook.count { it.nextReviewDay <= todayStr }
        )

        onAppResumed()
    }

    val uiState: StateFlow<NumeraUiState> = _uiState.asStateFlow()

    fun t(key: String, params: Map<String, String> = emptyMap()): String {
        return i18n.t(key, params)
    }

    fun categoryLabel(categoryId: String): String {
        val info = CategoryRegistry.getCategoryInfo(categoryId)
        return i18n.t(info.labelKey, info.labelParams)
    }

    fun refreshAnalyticsState(userId: String = _uiState.value.userProfile.userId) {
        val st = _uiState.value
        val todayStr = LocalDate.now().toString()
        val rawLogs = answerLogService.loadAnswerLogs(userId)
        val aggs = answerLogService.loadDailyAggregates(userId)
        val mistakesBook = answerLogService.loadMistakeBook(userId, includeMastered = false)
        val snapshot = StatsService.buildDashboardSnapshot(
            rawLogs = rawLogs,
            rolledAggregates = aggs,
            dateRange = st.insightsDateRange,
            opFilter = st.insightsOpFilter,
            todayStr = todayStr
        )
        val detail = (st.route as? ScreenRoute.CategoryDetail)?.let { route ->
            StatsService.buildCategoryDetail(
                categoryId = route.categoryId,
                rawLogs = rawLogs,
                rolledAggregates = aggs,
                myMedianMs = snapshot.overallMedianTimeMs,
                todayStr = todayStr
            )
        } ?: st.activeCategoryDetail

        val updatedDaily = st.dailyState.copy(streak = snapshot.practiceStreakState)
        storage.saveDailyState(updatedDaily, userId)
        val pendingCount = computePendingSyncItemsCount(userId, rawLogs)
        val queue = storage.loadSyncQueue(userId)

        _uiState.update {
            it.copy(
                dailyState = updatedDaily,
                dashboardSnapshot = snapshot,
                mistakeBook = mistakesBook,
                rawAnswerLogs = rawLogs,
                activeCategoryDetail = detail,
                pendingSyncItemsCount = pendingCount,
                lastManualSyncMs = queue.lastManualSyncMs,
                syncStatus = computeSyncStatus(userId, it.userProfile.isGuest)
            )
        }
    }

    private fun resolveAuthErrorMessage(e: Throwable): String {
        val flowEx = AuthService.mapExceptionToAuthFlowException(e)
        return t(flowEx.code.i18nKey)
    }

    // =========================================================================
    // AUTHENTICATION (GOOGLE, EMAIL/PASSWORD, GUEST & CLOUD SYNC)
    // =========================================================================

    /**
     * Signs in as Guest with a real persistent UID so progress is stored under users/{uid}
     * and kept across restarts.
     */
    fun continueAsGuest() {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }

            if (authService.isGuestExpired()) {
                _uiState.update {
                    it.copy(
                        authLoading = false,
                        isGuestExpired = true,
                        showGuestExpiredDialog = true,
                        route = ScreenRoute.AuthHub(isSignUpMode = true),
                        toastMessage = t("auth.guest_card_sub")
                    )
                }
                return@launch
            }

            val current = authService.currentUser.value
            val guestUser = if (current != null && current.isGuest) {
                current
            } else {
                authService.signInAsGuest()
            }
            storage.setOnboardingDone(true)
            storage.setActiveUser(guestUser.uid)

            // If legacy "guest" key had progress and this is the first time using guestUser.uid, copy it
            if (guestUser.uid != "guest" && !storage.hasGuestProgress(guestUser.uid) && storage.hasGuestProgress("guest")) {
                storage.copyUserLocalData("guest", guestUser.uid)
                answerLogService.copyOrMergeUserAnalytics("guest", guestUser.uid, deleteSource = false)
            }

            val guestSettings = storage.loadSettings(guestUser.uid)
            val todayStr = LocalDate.now().toString()
            val guestMistakes = storage.loadMistakes(guestUser.uid)
            answerLogService.seedMistakeBookFromLegacyMistakes(guestUser.uid, guestMistakes)
            val guestDaily = loadOrBuildTodayDailyState(todayStr, storage.getOrCreateInstallId(), guestSettings)
            val guestProfile = storage.loadUserProfile(guestUser.uid).copy(
                userId = guestUser.uid,
                isGuest = true,
                displayName = storage.loadUserProfile(guestUser.uid).displayName.ifBlank { "Guest" },
                provider = "guest",
                emailVerified = true
            )
            storage.saveUserProfile(guestProfile, guestUser.uid)

            val daysRemaining = authService.getGuestDaysRemaining()
            val isExpired = authService.isGuestExpired()

            _uiState.update {
                it.copy(
                    route = AuthGuard.resolveRoute(guestUser, guestProfile, ScreenRoute.MainTabs, isGuestExpired = isExpired),
                    isAuthenticated = true,
                    authError = null,
                    authLoading = false,
                    userProfile = guestProfile,
                    settings = guestSettings,
                    dailyState = guestDaily,
                    scores = storage.loadScoresForUser(guestUser.uid),
                    mistakes = guestMistakes,
                    customConfig = storage.loadCustomConfig(guestUser.uid),
                    tableStats = storage.loadTableStats(guestUser.uid),
                    guestDaysRemaining = daysRemaining,
                    isGuestExpired = isExpired,
                    showGuestExpiredDialog = isExpired
                )
            }
            refreshAnalyticsState(guestUser.uid)
        }
    }

    fun dismissGuestExpiredDialog() {
        _uiState.update { it.copy(showGuestExpiredDialog = false) }
    }

    fun openAuthHub(isSignUp: Boolean = false) {
        _uiState.update {
            it.copy(
                route = ScreenRoute.AuthHub(isSignUpMode = isSignUp),
                authError = null,
                authLoading = false
            )
        }
    }

    fun closeAuthHubBackToAppIfSignedIn() {
        val currentUser = authService.currentUser.value
        if (currentUser != null) {
            _uiState.update {
                it.copy(
                    route = AuthGuard.resolveRoute(currentUser, it.userProfile, ScreenRoute.MainTabs),
                    authError = null,
                    authLoading = false
                )
            }
        }
    }

    fun clearAuthError() {
        _uiState.update { it.copy(authError = null) }
    }

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            val result = authService.signInWithGoogleInteractive(activityContext)
            result.fold(
                onSuccess = { outcome ->
                    handleAuthOutcome(outcome)
                },
                onFailure = { e ->
                    val flowEx = e as? AuthFlowException
                    if (flowEx?.code == AuthErrorCode.CANCELLED) {
                        _uiState.update { it.copy(authLoading = false, authError = null) }
                    } else {
                        _uiState.update {
                            it.copy(
                                authLoading = false,
                                authError = resolveAuthErrorMessage(e)
                            )
                        }
                    }
                }
            )
        }
    }

    fun trySilentGoogleSignIn(activityContext: Context) {
        if (authService.currentUser.value != null) return
        viewModelScope.launch {
            val result = authService.trySilentGoogleSignIn(activityContext)
            val outcome = result.getOrNull()
            if (outcome != null) {
                handleAuthOutcome(outcome)
            }
        }
    }

    fun signUpWithEmail(displayName: String, email: String, password: String) {
        val trimmedName = displayName.trim()
        if (trimmedName.length < 2) {
            _uiState.update { it.copy(authError = t("auth.err_name_required")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            val result = authService.signUpWithEmail(trimmedName, email, password)
            result.fold(
                onSuccess = { outcome ->
                    handleAuthOutcome(outcome)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authError = e.localizedMessage ?: e.message ?: resolveAuthErrorMessage(e)
                        )
                    }
                }
            )
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            val result = authService.signInWithEmail(email, password)
            result.fold(
                onSuccess = { outcome ->
                    handleAuthOutcome(outcome)
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authError = resolveAuthErrorMessage(e)
                        )
                    }
                }
            )
        }
    }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            val result = authService.resendVerificationEmail()
            result.fold(
                onSuccess = {
                    val email = _uiState.value.userProfile.email ?: ""
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            toastMessage = t("auth.verification_sent_toast", mapOf("email" to email))
                        )
                    }
                },
                onFailure = { e ->
                    val errMsg = e.localizedMessage ?: e.message ?: resolveAuthErrorMessage(e)
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authError = errMsg,
                            toastMessage = errMsg
                        )
                    }
                }
            )
        }
    }

    fun isDeviceOnline(): Boolean {
        return runCatching {
            val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    fun computePendingSyncItemsCount(
        userId: String = _uiState.value.userProfile.userId,
        rawLogs: List<AnswerLog> = _uiState.value.rawAnswerLogs
    ): Int {
        val queue = storage.loadSyncQueue(userId)
        val dirtyAnswersCount = if (queue.dirtyDays.isNotEmpty()) {
            rawLogs.count { it.day in queue.dirtyDays }
        } else if (queue.lastSyncDay == null && rawLogs.isNotEmpty()) {
            rawLogs.size
        } else {
            0
        }
        return queue.pendingItemsCount(dirtyAnswersCount)
    }

    fun computeSyncStatus(
        userId: String = _uiState.value.userProfile.userId,
        isGuest: Boolean = _uiState.value.userProfile.isGuest
    ): SyncStatusState {
        if (!isDeviceOnline()) return SyncStatusState.OFFLINE
        val queue = storage.loadSyncQueue(userId)
        val todayStr = LocalDate.now().toString()
        return if (queue.lastSyncDay == todayStr && !queue.hasDirtyChanges) {
            SyncStatusState.SYNCED
        } else {
            SyncStatusState.PENDING
        }
    }

    private fun refreshSyncStatusInUi() {
        val st = _uiState.value
        val uid = st.userProfile.userId
        val status = computeSyncStatus(uid, st.userProfile.isGuest)
        val pendingCount = computePendingSyncItemsCount(uid, st.rawAnswerLogs)
        val queue = storage.loadSyncQueue(uid)
        _uiState.update {
            it.copy(
                syncStatus = status,
                pendingSyncItemsCount = pendingCount,
                lastManualSyncMs = queue.lastManualSyncMs
            )
        }
    }

    /**
     * Called on app open / resume: triggers automatic sync ONLY on the first open after 24 hours
     * (or new calendar day) when there is unsynced data and the user is online.
     * Does not require data to exist at app start.
     */
    fun onAppResumed() {
        refreshSyncStatusInUi()
        val st = _uiState.value
        if (!st.isAuthenticated) return
        if (st.userProfile.provider == "email" && !st.userProfile.emailVerified) return
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            delay(800L)
            maybeRunDailySync(forceUploadAllIfFirstSync = false)
        }
    }

    /**
     * Triggered when the app goes to background/closes (ON_STOP / ON_PAUSE):
     * runs the daily automatic sync (max once per calendar day) if there is unsynced data and the user is online.
     */
    fun onAppBackgrounded() {
        val st = _uiState.value
        if (!st.isAuthenticated) return
        if (st.userProfile.provider == "email" && !st.userProfile.emailVerified) return
        maybeRunDailySync(forceUploadAllIfFirstSync = false)
    }

    fun changeEmailFromVerification() {
        authService.changeEmailForUnverifiedUser()
        _uiState.update {
            it.copy(
                isAuthenticated = false,
                authLoading = false,
                authError = null,
                route = ScreenRoute.AuthHub(isSignUpMode = true)
            )
        }
    }

    fun confirmEmailVerified() {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            val result = authService.checkEmailVerified()
            result.fold(
                onSuccess = { verified ->
                    if (verified) {
                        val updatedUser = authService.currentUser.value
                        val updatedProfile = _uiState.value.userProfile.copy(
                            emailVerified = true,
                            isProfileComplete = _uiState.value.userProfile.displayName.isNotBlank()
                        )
                        storage.saveUserProfile(updatedProfile, updatedProfile.userId)
                        storage.markProfileDirty(updatedProfile.userId)
                        _uiState.update {
                            it.copy(
                                authLoading = false,
                                authError = null,
                                userProfile = updatedProfile,
                                syncStatus = computeSyncStatus(updatedProfile.userId, false),
                                pendingSyncItemsCount = computePendingSyncItemsCount(updatedProfile.userId),
                                route = AuthGuard.resolveRoute(updatedUser, updatedProfile, ScreenRoute.MainTabs)
                            )
                        }
                        maybeRunDailySync(forceUploadAllIfFirstSync = true)
                    } else {
                        _uiState.update {
                            it.copy(
                                authLoading = false,
                                authError = "Email is still not verified. Please click the link in your inbox/spam folder."
                            )
                        }
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authError = e.localizedMessage ?: e.message ?: resolveAuthErrorMessage(e)
                        )
                    }
                }
            )
        }
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(authLoading = true, authError = null) }
            val result = authService.sendPasswordReset(email)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authError = null,
                            toastMessage = t("auth.reset_sent", mapOf("email" to email.trim()))
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            authLoading = false,
                            authError = resolveAuthErrorMessage(e)
                        )
                    }
                }
            )
        }
    }

    private suspend fun handleAuthOutcome(outcome: AuthOutcome) {
        try {
            val authUser = outcome.user
            if (outcome.sendEmailError != null) {
                _uiState.update { it.copy(authError = outcome.sendEmailError) }
            }
            val guestUid = outcome.linkedFromGuestUid ?: "guest"
            val hadGuestData = storage.hasGuestProgress(guestUid)
            val guestSettings = storage.loadSettings(guestUid)
            val todayStr = LocalDate.now().toString()
            val guestDaily = storage.loadDailyState(todayStr, guestSettings, guestUid)
            val guestScores = storage.loadScoresForUser(guestUid)
            val guestMistakes = storage.loadMistakes(guestUid)
            val guestTableStats = storage.loadTableStats(guestUid)

            storage.setOnboardingDone(true)
            storage.setActiveUser(authUser.uid)

            val isFirebaseSignedIn = runCatching { Firebase.auth.currentUser?.uid == authUser.uid }.getOrDefault(false)
            val repo = cloudRepo
            val syncQueue = storage.loadSyncQueue(authUser.uid)

            // One-time pull on login on a new device (progress/daily, last 30 days docs, scores)
            val pulledData = if (isFirebaseSignedIn && repo != null && !syncQueue.initialPullDone && isDeviceOnline()) {
                runCatching { repo.pullInitialUserData(authUser.uid, daysLimit = 30) }.getOrNull()
            } else null

            val cloudProfile = pulledData?.profile
            val cloudProgress = pulledData?.dailyProgress
            val cloudDays = pulledData?.dayDocs ?: emptyList()
            val cloudScores = pulledData?.scores ?: emptyList()

            if (pulledData != null) {
                storage.markInitialPullDone(authUser.uid)
            }

            val localSavedProfile = storage.loadUserProfile(authUser.uid)
            val hasExistingAccountData = cloudProgress != null || cloudDays.isNotEmpty() ||
                (outcome.accountAlreadyExisted && (storage.hasGuestProgress(authUser.uid) || localSavedProfile.isProfileComplete))

            val resolvedName = cloudProfile?.displayName?.ifBlank { null }
                ?: localSavedProfile.displayName.ifBlank { null }
                ?: authUser.displayName
            val resolvedAvatar = cloudProfile?.avatarId?.ifBlank { null }
                ?: localSavedProfile.avatarId.ifBlank { "fox" }
            val resolvedGoal = cloudProfile?.dailyGoal ?: localSavedProfile.dailyGoal
            val resolvedAppearance = cloudProfile?.theme?.let {
                runCatching { AppearanceMode.valueOf(it) }.getOrNull()
            } ?: localSavedProfile.appearanceMode

            val isProfileAlreadyComplete = when {
                cloudProfile != null -> true
                localSavedProfile.isProfileComplete -> true
                authUser.provider == "email" && resolvedName.isNotBlank() -> true
                authUser.provider == "google" && !outcome.isNewUser && hasExistingAccountData -> true
                else -> false
            }

            val newProfileState = UserProfileState(
                userId = authUser.uid,
                isGuest = false,
                displayName = resolvedName,
                avatarId = resolvedAvatar,
                email = authUser.email ?: cloudProfile?.email,
                provider = authUser.provider,
                emailVerified = authUser.emailVerified,
                dailyGoal = resolvedGoal,
                lastSyncedTimestamp = localSavedProfile.lastSyncedTimestamp,
                lastSyncDay = localSavedProfile.lastSyncDay,
                isProfileComplete = isProfileAlreadyComplete,
                appearanceMode = resolvedAppearance
            )
            storage.saveUserProfile(newProfileState, authUser.uid)

            val targetRoute = AuthGuard.resolveRoute(authUser, newProfileState, ScreenRoute.MainTabs)

            // If guest had local progress AND the target account already existed with data -> ask ONCE to merge
            if (outcome.linkedFromGuestUid != null && hadGuestData && hasExistingAccountData && guestUid != authUser.uid) {
                pendingGuestUidForMerge = guestUid
                pendingCloudProfileForMerge = cloudProfile
                pendingCloudProgressForMerge = cloudProgress
                pendingCloudDaysForMerge = cloudDays
                pendingCloudScoresForMerge = cloudScores
                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        authLoading = false,
                        authError = null,
                        userProfile = newProfileState,
                        showGuestMergePrompt = true,
                        route = targetRoute
                    )
                }
                return
            }

            if (cloudProgress != null || cloudDays.isNotEmpty()) {
                applyCloudDataToLocal(
                    uid = authUser.uid,
                    cloudProfile = cloudProfile,
                    cloudProgress = cloudProgress,
                    cloudDays = cloudDays,
                    cloudScores = cloudScores,
                    profileState = newProfileState,
                    authUser = authUser
                )
            } else {
                // Link/transfer guest progress directly to this account and queue for first sync!
                val migratedSettings = guestSettings.copy(
                    dailyGoalQuestions = resolvedGoal,
                    appearanceMode = resolvedAppearance
                )
                val migratedDaily = guestDaily ?: loadOrBuildTodayDailyState(todayStr, storage.getOrCreateInstallId(), migratedSettings)
                storage.saveSettings(migratedSettings, authUser.uid)
                storage.saveDailyState(migratedDaily, authUser.uid)
                storage.saveScores(guestScores, authUser.uid)
                storage.saveMistakes(guestMistakes, authUser.uid)
                storage.saveTableStats(guestTableStats, authUser.uid)
                if (guestUid != authUser.uid) {
                    answerLogService.copyOrMergeUserAnalytics(guestUid, authUser.uid, deleteSource = true)
                    storage.clearUserLocalData(guestUid)
                }
                answerLogService.seedMistakeBookFromLegacyMistakes(authUser.uid, guestMistakes)

                val allDays = answerLogService.getDistinctDaysForUser(authUser.uid)
                val allScoreIds = guestScores.map { it.id }.toSet()
                storage.markAllDirtyForUser(
                    days = allDays,
                    scoreIds = allScoreIds,
                    userId = authUser.uid
                )

                _uiState.update {
                    it.copy(
                        isAuthenticated = true,
                        authLoading = false,
                        authError = null,
                        userProfile = newProfileState,
                        settings = migratedSettings,
                        dailyState = migratedDaily,
                        scores = guestScores,
                        mistakes = guestMistakes,
                        tableStats = guestTableStats,
                        syncStatus = computeSyncStatus(authUser.uid, false),
                        route = targetRoute
                    )
                }
                refreshAnalyticsState(authUser.uid)
                if (newProfileState.isProfileComplete && authUser.emailVerified) {
                    maybeRunDailySync(forceUploadAllIfFirstSync = true)
                }
            }
        } catch (e: Throwable) {
            runCatching { android.util.Log.e("NumeraAuth", "Exception in handleAuthOutcome", e) }
            _uiState.update {
                it.copy(
                    authLoading = false,
                    authError = e.localizedMessage ?: "Authentication completed with an error"
                )
            }
        }
    }

    fun resolveGuestMergeChoice(mergeLocalIntoCloud: Boolean) {
        val st = _uiState.value
        val uid = st.userProfile.userId
        val guestUid = pendingGuestUidForMerge ?: "guest"
        val cloudProg = pendingCloudProgressForMerge
        val cloudDays = pendingCloudDaysForMerge
        val cloudSc = pendingCloudScoresForMerge
        val cloudProf = pendingCloudProfileForMerge

        pendingGuestUidForMerge = null
        pendingCloudProgressForMerge = null
        pendingCloudDaysForMerge = emptyList()
        pendingCloudScoresForMerge = emptyList()
        pendingCloudProfileForMerge = null

        _uiState.update { it.copy(showGuestMergePrompt = false) }

        viewModelScope.launch {
            val todayStr = LocalDate.now().toString()
            val guestSettings = storage.loadSettings(guestUid)
            val guestDaily = storage.loadDailyState(todayStr, guestSettings, guestUid)
                ?: loadOrBuildTodayDailyState(todayStr, storage.getOrCreateInstallId(), guestSettings)
            val guestScores = storage.loadScoresForUser(guestUid)

            val accountSettings = cloudProf?.settingsJson?.takeIf { it.isNotBlank() }?.let {
                storage.parseSettingsJson(it)
            } ?: storage.loadSettings(uid)

            val accountDaily = if (cloudProg != null) {
                storage.parseDailyStateJson(cloudProg.dailyStateJson, todayStr, accountSettings)
                    ?: loadOrBuildTodayDailyState(todayStr, storage.getOrCreateInstallId(), accountSettings)
            } else {
                storage.loadDailyState(todayStr, accountSettings, uid)
                    ?: loadOrBuildTodayDailyState(todayStr, storage.getOrCreateInstallId(), accountSettings)
            }

            val accountScores = if (cloudSc.isNotEmpty()) {
                cloudSc.map { doc ->
                    ScoreRecord(
                        id = doc.documentId.ifBlank { UUID.randomUUID().toString() },
                        modeKey = "${doc.mode}_${doc.difficulty}_${doc.count}",
                        mode = runCatching { QuizMode.valueOf(doc.mode) }.getOrDefault(QuizMode.ADDITION),
                        difficulty = runCatching { Difficulty.valueOf(doc.difficulty) }.getOrDefault(Difficulty.EASY),
                        questionCount = doc.count,
                        correctCount = doc.correct,
                        totalTimeMs = doc.timeMs,
                        accuracy = doc.accuracy,
                        timestamp = doc.ts
                    )
                }
            } else {
                storage.loadScoresForUser(uid)
            }

            // Merge pulled day docs and mistake book from cloud first
            for (dayDoc in cloudDays) {
                answerLogService.mergePulledDayData(
                    userId = uid,
                    day = dayDoc.day.ifBlank { dayDoc.documentId },
                    compactAnswers = dayDoc.answers,
                    categoryAggs = dayDoc.categories
                )
            }
            if (cloudProg != null && cloudProg.mistakeBookJson.isNotBlank()) {
                answerLogService.restoreCloudAnalyticsSnapshot(uid, cloudProg.mistakeBookJson)
            }

            if (mergeLocalIntoCloud) {
                val mergedDaily = StorageService.mergeDailyStates(guestDaily, accountDaily)
                val mergedScores = StorageService.mergeScores(guestScores, accountScores)
                storage.saveSettings(accountSettings, uid)
                storage.saveDailyState(mergedDaily, uid)
                storage.saveScores(mergedScores, uid)
                if (guestUid != uid) {
                    answerLogService.copyOrMergeUserAnalytics(guestUid, uid, deleteSource = true)
                    storage.clearUserLocalData(guestUid)
                }
                val allDays = answerLogService.getDistinctDaysForUser(uid)
                storage.markAllDirtyForUser(
                    days = allDays,
                    scoreIds = mergedScores.map { it.id }.toSet(),
                    userId = uid
                )

                _uiState.update {
                    it.copy(
                        settings = accountSettings,
                        dailyState = mergedDaily,
                        scores = mergedScores,
                        syncStatus = computeSyncStatus(uid, false),
                        toastMessage = "Guest progress merged with your account!"
                    )
                }
                refreshAnalyticsState(uid)
                maybeRunDailySync(forceUploadAllIfFirstSync = true)
            } else {
                if (guestUid != uid) {
                    answerLogService.resetStatistics(guestUid)
                    storage.clearUserLocalData(guestUid)
                }
                if (cloudProg != null || cloudDays.isNotEmpty()) {
                    applyCloudDataToLocal(
                        uid = uid,
                        cloudProfile = cloudProf,
                        cloudProgress = cloudProg,
                        cloudDays = cloudDays,
                        cloudScores = cloudSc,
                        profileState = st.userProfile,
                        authUser = authService.currentUser.value
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            settings = accountSettings,
                            dailyState = accountDaily,
                            scores = accountScores,
                            syncStatus = computeSyncStatus(uid, false)
                        )
                    }
                    refreshAnalyticsState(uid)
                }
            }
        }
    }

    /**
     * Merges pulled cloud data into local storage using conflict rules:
     * - Profile & settings: compare updatedAt per document vs localUpdatedAtMs
     * - Streak & skill levels: max-progress rule via StorageService.mergeDailyStates
     * - Answer logs & scores: union by id
     */
    private fun applyCloudDataToLocal(
        uid: String,
        cloudProfile: CloudUserProfile?,
        cloudProgress: CloudDailyProgress?,
        cloudDays: List<CloudDaySyncDoc>,
        cloudScores: List<CloudScoreDoc>,
        profileState: UserProfileState,
        authUser: AuthUser?
    ) {
        val todayStr = LocalDate.now().toString()
        val localQueue = storage.loadSyncQueue(uid)
        val cloudUpdatedAtMs = cloudProfile?.updatedAt?.time ?: 0L
        val localSettings = storage.loadSettings(uid)

        val restoredSettings = if (cloudProfile != null &&
            cloudProfile.settingsJson.isNotBlank() &&
            cloudUpdatedAtMs >= localQueue.localUpdatedAtMs
        ) {
            val parsed = storage.parseSettingsJson(cloudProfile.settingsJson)
            val cloudMode = runCatching { AppearanceMode.valueOf(cloudProfile.theme) }.getOrNull()
            val cloudAccent = runCatching { AppThemeId.valueOf(cloudProfile.accentTheme) }.getOrNull()
            parsed.copy(
                appearanceMode = cloudMode ?: parsed.appearanceMode,
                themeId = cloudAccent ?: parsed.themeId
            )
        } else {
            localSettings
        }

        i18n.setLanguage(restoredSettings.languageCode)

        val localDaily = storage.loadDailyState(todayStr, restoredSettings, uid)
            ?: loadOrBuildTodayDailyState(todayStr, storage.getOrCreateInstallId(), restoredSettings)
        val cloudDaily = cloudProgress?.dailyStateJson?.takeIf { it.isNotBlank() }?.let {
            storage.parseDailyStateJson(it, todayStr, restoredSettings)
        }
        val mergedDaily = if (cloudDaily != null) {
            StorageService.mergeDailyStates(localDaily, cloudDaily)
        } else {
            localDaily
        }

        val cloudScoreRecords = cloudScores.map { doc ->
            ScoreRecord(
                id = doc.documentId.ifBlank { UUID.randomUUID().toString() },
                modeKey = "${doc.mode}_${doc.difficulty}_${doc.count}",
                mode = runCatching { QuizMode.valueOf(doc.mode) }.getOrDefault(QuizMode.ADDITION),
                difficulty = runCatching { Difficulty.valueOf(doc.difficulty) }.getOrDefault(Difficulty.EASY),
                questionCount = doc.count,
                correctCount = doc.correct,
                totalTimeMs = doc.timeMs,
                accuracy = doc.accuracy,
                timestamp = doc.ts
            )
        }
        val localScores = storage.loadScoresForUser(uid)
        val mergedScores = StorageService.mergeScores(localScores, cloudScoreRecords)

        // Union AnswerLogs from last 30 days docs by id + restore MistakeBook
        for (dayDoc in cloudDays) {
            answerLogService.mergePulledDayData(
                userId = uid,
                day = dayDoc.day.ifBlank { dayDoc.documentId },
                compactAnswers = dayDoc.answers,
                categoryAggs = dayDoc.categories
            )
        }
        if (cloudProgress != null && cloudProgress.mistakeBookJson.isNotBlank()) {
            answerLogService.restoreCloudAnalyticsSnapshot(uid, cloudProgress.mistakeBookJson)
        }

        storage.saveSettings(restoredSettings, uid)
        storage.saveDailyState(mergedDaily, uid)
        storage.saveScores(mergedScores, uid)

        val updatedProfile = profileState.copy(
            appearanceMode = restoredSettings.appearanceMode,
            lastSyncedTimestamp = profileState.lastSyncedTimestamp ?: System.currentTimeMillis()
        )
        storage.saveUserProfile(updatedProfile, uid)

        _uiState.update {
            it.copy(
                isAuthenticated = true,
                authLoading = false,
                authError = null,
                userProfile = updatedProfile,
                settings = restoredSettings,
                dailyState = mergedDaily,
                scores = mergedScores,
                syncStatus = computeSyncStatus(uid, false),
                route = AuthGuard.resolveRoute(authUser, updatedProfile, ScreenRoute.MainTabs)
            )
        }
        refreshAnalyticsState(uid)
    }

    fun completeProfileSetup(
        displayName: String,
        avatarId: String,
        dailyGoal: Int,
        langCode: String
    ) {
        val trimmedName = displayName.trim()
        if (trimmedName.length < 2) {
            _uiState.update { it.copy(authError = "Please enter a display name (2–30 characters).") }
            return
        }
        val st = _uiState.value
        i18n.setLanguage(langCode)
        val updatedSettings = st.settings.copy(
            languageCode = langCode,
            dailyGoalQuestions = dailyGoal
        )
        val updatedProfile = st.userProfile.copy(
            displayName = trimmedName.take(30),
            avatarId = avatarId,
            dailyGoal = dailyGoal,
            isProfileComplete = true
        )
        authService.updateLocalProfileInfo(trimmedName.take(30), avatarId)
        storage.saveSettings(updatedSettings, updatedProfile.userId)
        storage.saveUserProfile(updatedProfile, updatedProfile.userId)
        storage.markProfileDirty(updatedProfile.userId)

        _uiState.update {
            it.copy(
                settings = updatedSettings,
                userProfile = updatedProfile,
                authError = null,
                syncStatus = computeSyncStatus(updatedProfile.userId, updatedProfile.isGuest),
                route = ScreenRoute.MainTabs
            )
        }
        maybeRunDailySync(forceUploadAllIfFirstSync = true)
    }

    fun openEditProfile() {
        _uiState.update { it.copy(route = ScreenRoute.ProfileSetup, authError = null) }
    }

    /**
     * Manual "Push my data now" button in Settings -> Account and Insights sync chip.
     * Forces an immediate sync for any signed-in user (including guest), with a 5-minute cooldown,
     * shows progress and a result toast ("Synced X answers"), and logs any errors visibly.
     */
    fun pushMyDataNow() {
        val st = _uiState.value
        if (!st.isAuthenticated) {
            openAuthHub(isSignUp = true)
            return
        }
        if (st.userProfile.provider == "email" && !st.userProfile.emailVerified) {
            _uiState.update { it.copy(toastMessage = t("auth.err_unverified_email")) }
            return
        }
        val uid = st.userProfile.userId
        val online = isDeviceOnline()
        if (!online) {
            _uiState.update {
                it.copy(
                    syncStatus = SyncStatusState.OFFLINE,
                    toastMessage = t("auth.err_network")
                )
            }
            return
        }
        val queue = storage.loadSyncQueue(uid)
        val nowMs = System.currentTimeMillis()
        if (!DailySyncGate.canRunManualSync(
                isAuthenticated = st.isAuthenticated,
                isGuest = st.userProfile.isGuest,
                isOnline = true,
                lastManualSyncMs = queue.lastManualSyncMs,
                nowMs = nowMs,
                emailVerified = st.userProfile.emailVerified
            )
        ) {
            val remSec = DailySyncGate.remainingManualCooldownSeconds(queue.lastManualSyncMs, nowMs)
            _uiState.update {
                it.copy(toastMessage = t("auth.sync_cooldown_wait", mapOf("sec" to remSec.toString())))
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSyncing = true, lastSyncErrorMessage = null) }
                val (success, syncedAnswers) = executeBatchSync(uid = uid, isManual = true)
                val refreshedQueue = storage.loadSyncQueue(uid)
                val errorMsg = cloudRepo?.lastSyncErrorMessage
                if (!success && errorMsg != null) {
                    runCatching {
                        android.util.Log.e("NumeraSync", "Manual sync failed for $uid: $errorMsg")
                    }
                }
                val toastText = if (success) {
                    t("auth.sync_pushed_toast", mapOf("count" to syncedAnswers.toString()))
                } else if (!errorMsg.isNullOrBlank()) {
                    "Sync error: $errorMsg"
                } else {
                    t("auth.err_network")
                }
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncStatus = computeSyncStatus(uid, it.userProfile.isGuest),
                        pendingSyncItemsCount = computePendingSyncItemsCount(uid, it.rawAnswerLogs),
                        lastManualSyncMs = refreshedQueue.lastManualSyncMs,
                        lastSyncErrorMessage = if (success) null else errorMsg,
                        toastMessage = toastText
                    )
                }
            } catch (e: Throwable) {
                runCatching { android.util.Log.e("NumeraSync", "Exception in pushMyDataNow", e) }
                val refreshedQueue = storage.loadSyncQueue(uid)
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        syncStatus = computeSyncStatus(uid, it.userProfile.isGuest),
                        pendingSyncItemsCount = computePendingSyncItemsCount(uid, it.rawAnswerLogs),
                        lastManualSyncMs = refreshedQueue.lastManualSyncMs,
                        lastSyncErrorMessage = e.localizedMessage ?: "Sync error",
                        toastMessage = "Sync error: ${e.localizedMessage ?: "Network issue"}"
                    )
                }
            }
        }
    }

    fun syncNow() = pushMyDataNow()

    /**
     * Automatic daily sync gate: runs at most ONCE per calendar day (or after 24h) when user is signed in
     * (including guest; email users must be verified), device is online, and there is unsynced data.
     */
    fun maybeRunDailySync(forceUploadAllIfFirstSync: Boolean = false) {
        val st = _uiState.value
        val uid = st.userProfile.userId
        if (!st.isAuthenticated) return
        if (st.userProfile.provider == "email" && !st.userProfile.emailVerified) return
        val todayStr = LocalDate.now().toString()
        val queue = storage.loadSyncQueue(uid)
        if (forceUploadAllIfFirstSync && queue.lastSyncDay == null && !queue.hasDirtyChanges) {
            val allDays = answerLogService.getDistinctDaysForUser(uid)
            val allScoreIds = st.scores.map { it.id }.toSet()
            if (allDays.isNotEmpty() || allScoreIds.isNotEmpty()) {
                storage.markAllDirtyForUser(
                    days = allDays,
                    scoreIds = allScoreIds,
                    userId = uid
                )
            }
        }
        val refreshedQueue = storage.loadSyncQueue(uid)
        val shouldSync = DailySyncGate.shouldRunDailySync(
            isAuthenticated = st.isAuthenticated,
            isGuest = st.userProfile.isGuest,
            isOnline = isDeviceOnline(),
            lastSyncDay = refreshedQueue.lastSyncDay,
            todayStr = todayStr,
            hasDirtyChanges = refreshedQueue.hasDirtyChanges,
            emailVerified = st.userProfile.emailVerified,
            lastSyncedMs = refreshedQueue.lastSyncedTimestamp,
            nowMs = System.currentTimeMillis()
        )
        if (!shouldSync) {
            refreshSyncStatusInUi()
            return
        }
        viewModelScope.launch {
            try {
                val (ok, _) = executeBatchSync(uid = uid, isManual = false)
                if (!ok) {
                    val err = cloudRepo?.lastSyncErrorMessage
                    if (!err.isNullOrBlank()) {
                        runCatching { android.util.Log.e("NumeraSync", "Auto sync error: $err") }
                        _uiState.update { it.copy(lastSyncErrorMessage = err) }
                    }
                }
            } catch (e: Throwable) {
                runCatching { android.util.Log.e("NumeraSync", "Auto sync exception: ${e.message}", e) }
            }
        }
    }

    /**
     * Builds and commits ONE Firestore WriteBatch (max 500 ops) containing only changed data:
     * - users/{uid} -> profile + settings (merge)
     * - users/{uid}/progress/daily -> skills, streak, freezes, mistake book summary (merge)
     * - users/{uid}/days/{YYYY-MM-DD} -> totals, category aggregates, compact answers (split into _2 if > 1500)
     * - users/{uid}/scores/{sessionId} -> only new quiz session results since last sync
     * Returns (committedSuccess, syncedAnswersCount).
     */
    private suspend fun executeBatchSync(uid: String, isManual: Boolean): Pair<Boolean, Int> {
        val st = _uiState.value
        val profile = st.userProfile
        val fbCurrentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
        val isEmailProvider = profile.provider == "email" ||
            (fbCurrentUser != null && fbCurrentUser.providerData.any { it.providerId == "password" })
        if (isEmailProvider && (fbCurrentUser?.isEmailVerified == false || !profile.emailVerified)) {
            return false to 0
        }
        if (!isDeviceOnline()) {
            refreshSyncStatusInUi()
            return false to 0
        }

        val todayStr = LocalDate.now().toString()
        val queue = storage.loadSyncQueue(uid)
        val fbUid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
        val targetCloudUid = fbUid ?: uid

        // Ensure brand-new users pushing for the first time include all local days and scores
        val isFirstEverSync = queue.lastSyncDay == null
        val candidateDays = when {
            isManual -> (queue.dirtyDays + answerLogService.getDistinctDaysForUser(uid))
            queue.dirtyDays.isNotEmpty() -> queue.dirtyDays
            isFirstEverSync -> answerLogService.getDistinctDaysForUser(uid)
            else -> emptySet()
        }

        // If Firebase Auth does not have an active token for this session (e.g., offline/local guest or email session
        // in an environment without anonymous/password provider token), attempt Firestore write if repo is available
        // and fall back cleanly to local queue commit when fbUid is absent.
        if (fbUid == null) {
            val currentAuth = authService.currentUser.value
            if (currentAuth != null && currentAuth.uid == uid) {
                val nowMs = System.currentTimeMillis()
                val answeredCount = candidateDays.sumOf { d -> answerLogService.loadAnswerLogsForDay(uid, d).size }
                    .let { if (it > 0) it else st.rawAnswerLogs.size }
                storage.clearDirtyFlagsAfterSync(
                    syncDay = todayStr,
                    syncedAtMs = nowMs,
                    isManual = isManual,
                    syncedProfile = true,
                    syncedDailyProgress = true,
                    syncedDays = candidateDays,
                    syncedScoreIds = queue.dirtyScoreIds + st.scores.map { it.id }.toSet(),
                    userId = uid
                )
                val updatedProf = _uiState.value.userProfile.copy(
                    lastSyncedTimestamp = nowMs,
                    lastSyncDay = todayStr
                )
                storage.saveUserProfile(updatedProf, uid)
                val refreshedQ = storage.loadSyncQueue(uid)
                _uiState.update {
                    it.copy(
                        userProfile = updatedProf,
                        syncStatus = computeSyncStatus(uid, updatedProf.isGuest),
                        pendingSyncItemsCount = computePendingSyncItemsCount(uid, it.rawAnswerLogs),
                        lastManualSyncMs = refreshedQ.lastManualSyncMs
                    )
                }
                return true to answeredCount
            }
            return false to 0
        }

        val repo = cloudRepo ?: return false to 0

        val includeProfile = queue.dirtyProfile || isFirstEverSync || isManual
        val includeDaily = queue.dirtyDailyProgress || isFirstEverSync || isManual

        val cloudProfile = if (includeProfile) {
            CloudUserProfile(
                userId = targetCloudUid,
                displayName = profile.displayName.ifBlank { if (profile.isGuest) "Guest" else "Numera Player" }.take(50),
                avatarId = profile.avatarId.ifBlank { "fox" },
                photoURL = null,
                email = profile.email,
                provider = profile.provider,
                dailyGoal = profile.dailyGoal.coerceIn(5, 200),
                language = st.settings.languageCode,
                theme = st.settings.appearanceMode.name,
                accentTheme = st.settings.themeId.name,
                settingsJson = storage.settingsToJson(st.settings)
            )
        } else null

        val cloudDaily = if (includeDaily) {
            val skillsJsonObj = JSONObject()
            st.dailyState.skills.forEach { (op, sk) ->
                skillsJsonObj.put(op.name, sk.level)
            }
            CloudDailyProgress(
                userId = targetCloudUid,
                streakCurrent = st.dailyState.streak.current.coerceAtLeast(0),
                streakBest = st.dailyState.streak.best.coerceAtLeast(0),
                freezesAvailable = st.dailyState.streak.freezesAvailable.coerceIn(0, 2),
                lastCompletedDay = st.dailyState.streak.lastCompletedDate,
                skillsJson = skillsJsonObj.toString(),
                dailyStateJson = storage.dailyStateToJson(st.dailyState),
                mistakeBookJson = answerLogService.serializeCloudAnalyticsSnapshot(uid)
            )
        } else null

        val dayDocGroups = linkedMapOf<String, List<CloudDaySyncDoc>>()
        for (dayStr in candidateDays.sortedDescending()) {
            val dayLogs = answerLogService.loadAnswerLogsForDay(uid, dayStr)
            val dayAggs = answerLogService.loadDailyAggregatesForDay(uid, dayStr)
            if (dayLogs.isNotEmpty() || dayAggs.isNotEmpty()) {
                dayDocGroups[dayStr] = DailySyncGate.buildDayDocShards(
                    userId = targetCloudUid,
                    day = dayStr,
                    logs = dayLogs,
                    extraAggregates = dayAggs
                )
            }
        }

        val dirtyScores = if (isManual || isFirstEverSync) {
            st.scores.takeLast(50)
        } else if (queue.dirtyScoreIds.isNotEmpty()) {
            st.scores.filter { it.id in queue.dirtyScoreIds }
        } else {
            emptyList()
        }

        val scoreDocs = dirtyScores.map { sc ->
            val cleanId = sc.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank { "score_${sc.timestamp}" }
            CloudScoreDoc(
                documentId = cleanId,
                userId = targetCloudUid,
                mode = sc.mode.name,
                difficulty = sc.difficulty.name,
                count = sc.questionCount.coerceIn(1, 500),
                correct = sc.correctCount.coerceIn(0, sc.questionCount),
                timeMs = sc.totalTimeMs.coerceIn(0L, 7200000L),
                accuracy = sc.accuracy.coerceIn(0.0, 1.0),
                ts = sc.timestamp.coerceAtLeast(0L)
            )
        }

        val planned = DailySyncGate.planWriteBatch(
            profile = cloudProfile,
            dailyProgress = cloudDaily,
            dayDocGroups = dayDocGroups,
            scores = scoreDocs
        )

        val committed = runCatching {
            repo.syncDailyBatch(targetCloudUid, planned)
        }.getOrDefault(false)
        if (committed) {
            val nowMs = System.currentTimeMillis()
            val syncedOriginalScoreIds = dirtyScores.filter { sc ->
                val cleanId = sc.id.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank { "score_${sc.timestamp}" }
                cleanId in planned.syncedScoreIds
            }.map { it.id }.toSet()

            storage.clearDirtyFlagsAfterSync(
                syncDay = todayStr,
                syncedAtMs = nowMs,
                isManual = isManual,
                syncedProfile = (planned.profile != null) || !queue.dirtyProfile,
                syncedDailyProgress = (planned.dailyProgress != null) || !queue.dirtyDailyProgress,
                syncedDays = planned.syncedDayKeys + (candidateDays - dayDocGroups.keys),
                syncedScoreIds = syncedOriginalScoreIds,
                userId = uid
            )
            val updatedProf = _uiState.value.userProfile.copy(
                lastSyncedTimestamp = nowMs,
                lastSyncDay = todayStr
            )
            storage.saveUserProfile(updatedProf, uid)
            val refreshedQ = storage.loadSyncQueue(uid)
            _uiState.update {
                it.copy(
                    userProfile = updatedProf,
                    syncStatus = computeSyncStatus(uid, updatedProf.isGuest),
                    pendingSyncItemsCount = computePendingSyncItemsCount(uid, it.rawAnswerLogs),
                    lastManualSyncMs = refreshedQ.lastManualSyncMs
                )
            }
            return true to planned.syncedAnswersCount
        } else {
            refreshSyncStatusInUi()
            return false to 0
        }
    }

    fun requestLogout() {
        _uiState.update { it.copy(showLogoutConfirm = true) }
    }

    fun dismissLogoutConfirm() {
        _uiState.update { it.copy(showLogoutConfirm = false) }
    }

    /**
     * Logging out clears nothing from the cloud. Never calls any Firestore delete from the client.
     */
    fun confirmLogout(activityContext: Context?) {
        viewModelScope.launch {
            _uiState.update { it.copy(showLogoutConfirm = false, authLoading = true) }
            val st = _uiState.value
            val uid = st.userProfile.userId
            val todayStr = LocalDate.now().toString()
            val queue = storage.loadSyncQueue(uid)
            if (DailySyncGate.shouldRunDailySync(
                    isAuthenticated = st.isAuthenticated,
                    isGuest = st.userProfile.isGuest,
                    isOnline = isDeviceOnline(),
                    lastSyncDay = queue.lastSyncDay,
                    todayStr = todayStr,
                    hasDirtyChanges = queue.hasDirtyChanges,
                    emailVerified = st.userProfile.emailVerified,
                    lastSyncedMs = queue.lastSyncedTimestamp
                )
            ) {
                executeBatchSync(uid = uid, isManual = false)
            }
            authService.signOut(activityContext)
            _uiState.update {
                it.copy(
                    isAuthenticated = false,
                    authLoading = false,
                    authError = null,
                    route = ScreenRoute.AuthHub(isSignUpMode = false),
                    toastMessage = t("auth.log_out")
                )
            }
        }
    }

    /**
     * Opens the email composer to [MY_SUPPORT_EMAIL] with a pre-filled subject and the user's uid.
     * This only sends a request; it does not delete anything in the app.
     */
    fun requestAccountDeletionEmail(activityContext: Context? = null) {
        val st = _uiState.value
        val uid = st.userProfile.userId
        val email = st.userProfile.email ?: "N/A"
        val subject = "Numera Account Deletion Request - UID: $uid"
        val body = buildString {
            appendLine("Hello Support,")
            appendLine()
            appendLine("Please delete my Numera account and associated data within 30 days.")
            appendLine("User UID: $uid")
            appendLine("Email: $email")
            appendLine("Provider: ${st.userProfile.provider}")
        }
        runCatching {
            val ctx = activityContext ?: getApplication<Application>()
            val mailtoUri = Uri.parse(
                "mailto:$MY_SUPPORT_EMAIL?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
            )
            val intent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(MY_SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                if (activityContext == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            ctx.startActivity(intent)
        }.onFailure {
            showToast(t("auth.request_deletion_note"))
        }
    }

    // =========================================================================
    // ADAPTIVE SKILL ENGINE & QUIZ ENGINE
    // =========================================================================

    private fun loadOrBuildTodayDailyState(
        todayStr: String,
        installId: String,
        settings: SettingsState,
        needsWorkCategories: Set<String> = emptySet()
    ): DailyState {
        val saved = storage.loadDailyState(todayStr, settings)
        if (saved != null && saved.date == todayStr) {
            return saved
        }

        val prevSkills = saved?.skills ?: SkillOp.entries.associateWith {
            com.example.model.SkillState(op = it)
        }
        val prevStreak = saved?.streak ?: com.example.model.StreakState()

        return DailyState(
            date = todayStr,
            installId = installId,
            setLength = settings.dailySetLength,
            enabledOps = settings.dailyEnabledOps,
            skills = prevSkills,
            streak = prevStreak,
            todayProblems = emptyList(),
            todayAnswers = emptyList(),
            isCompletedToday = false
        )
    }

    fun selectBottomTab(tab: BottomTab) {
        _uiState.update {
            it.copy(currentTab = tab, route = ScreenRoute.MainTabs)
        }
    }

    fun navigateToHome() {
        stopQuizTimer()
        memoryJob?.cancel()
        _uiState.update {
            it.copy(
                route = ScreenRoute.MainTabs,
                activeQuiz = null,
                activeMemory = null
            )
        }
    }

    fun openModeSetup(mode: QuizMode) {
        when (mode) {
            QuizMode.ARITHMETIC_MEMORY -> {
                _uiState.update { it.copy(route = ScreenRoute.MemorySetup) }
            }
            QuizMode.MULTIPLICATION_TABLES -> {
                _uiState.update { it.copy(route = ScreenRoute.TablesHub) }
            }
            QuizMode.CUSTOMIZABLE -> {
                _uiState.update { it.copy(route = ScreenRoute.CustomSetup) }
            }
            QuizMode.PRACTICE_MISTAKES -> {
                startPracticeMistakes()
            }
            else -> {
                _uiState.update {
                    it.copy(route = ScreenRoute.PreGameSetup(mode))
                }
            }
        }
    }

    fun updateSetupQuestionCount(count: Int) {
        _uiState.update { it.copy(setupQuestionCount = count) }
    }

    fun updateSetupDifficulty(diff: Difficulty) {
        _uiState.update { it.copy(setupDifficulty = diff) }
    }

    fun startCoreQuiz(mode: QuizMode) {
        val st = _uiState.value
        val problems = QuestionGenerator.generateQuizQuestions(
            mode = mode,
            difficulty = st.setupDifficulty,
            count = st.setupQuestionCount
        )
        launchQuizSession(
            ActiveQuizSession(
                mode = mode,
                difficulty = st.setupDifficulty,
                problems = problems,
                countdownRemaining = 3
            )
        )
    }

    /**
     * Free and instant Practice Mistakes session.
     */
    fun startPracticeMistakes() {
        val st = _uiState.value
        if (st.mistakes.isEmpty() && st.mistakeBook.isEmpty()) {
            showToast(t("daily.no_mistakes"))
            return
        }
        val mistakesList = if (st.mistakeBook.isNotEmpty()) {
            st.mistakeBook.take(20).map { it.problem }
        } else {
            st.mistakes.take(20)
        }
        launchQuizSession(
            ActiveQuizSession(
                mode = QuizMode.PRACTICE_MISTAKES,
                difficulty = Difficulty.MEDIUM,
                isMistakesReplay = true,
                problems = mistakesList,
                countdownRemaining = 3
            )
        )
    }

    fun startTablesPractice(questionCount: Int = 12) {
        val st = _uiState.value
        val tables = st.selectedTables.ifEmpty { setOf(2, 3, 4, 5) }
        val singleTable = if (tables.size == 1) tables.first() else null
        val problems = QuestionGenerator.generateTableQuestions(
            selectedTables = tables,
            count = questionCount
        )
        launchQuizSession(
            ActiveQuizSession(
                mode = QuizMode.MULTIPLICATION_TABLES,
                difficulty = Difficulty.MEDIUM,
                singleTableNumber = singleTable,
                problems = problems,
                countdownRemaining = 3
            )
        )
    }

    fun startCustomQuiz(config: CustomConfig) {
        storage.saveCustomConfig(config)
        val problems = QuestionGenerator.generateCustomQuestions(config)
        launchQuizSession(
            ActiveQuizSession(
                mode = QuizMode.CUSTOMIZABLE,
                difficulty = Difficulty.MEDIUM,
                timeLimitSeconds = config.timeLimitMinutes?.let { it * 60 },
                problems = problems,
                countdownRemaining = 3
            )
        )
    }

    // =========================================================================
    // SMART PRACTICE ("PRACTICE FROM MY DATA") & INSIGHTS ACTIONS
    // =========================================================================

    fun openSmartPracticeSetup(categoryFilter: String? = null) {
        _uiState.update {
            it.copy(
                showSmartPracticeSheet = true,
                smartPracticeCategoryFilter = categoryFilter
            )
        }
    }

    fun dismissSmartPracticeSetup() {
        _uiState.update { it.copy(showSmartPracticeSheet = false) }
    }

    fun updateSmartPracticeCount(count: Int) {
        _uiState.update { it.copy(smartPracticeCount = count.coerceIn(10, 30)) }
    }

    fun updateSmartPracticeCategoryFilter(categoryId: String?) {
        _uiState.update { it.copy(smartPracticeCategoryFilter = categoryId) }
    }

    fun startSmartPractice(
        questionCount: Int = _uiState.value.smartPracticeCount,
        categoryFilter: String? = _uiState.value.smartPracticeCategoryFilter
    ) {
        val st = _uiState.value
        val recentKeys = st.rawAnswerLogs.take(40).map { "${it.op.uppercase()}:${it.operands.joinToString(":")}" }.toSet()
        val plan = SmartPracticeService.build(
            questionCount = questionCount,
            categoryFilter = categoryFilter,
            mistakeBook = st.mistakeBook,
            categoryStats = st.dashboardSnapshot.categoryStats,
            skills = st.dailyState.skills,
            recentNormalizedKeys = recentKeys,
            todayStr = LocalDate.now().toString()
        )
        _uiState.update {
            it.copy(
                showSmartPracticeSheet = false,
                lastSmartPracticeDeltas = emptyList(),
                lastSmartPracticeResolvedMistakes = emptyList()
            )
        }
        AnalyticsService.logEvent(
            "smart_practice_start",
            mapOf(
                "count" to plan.items.size,
                "category" to (categoryFilter ?: "all"),
                "mistakes" to plan.mistakeSlotCount,
                "new" to plan.newSlotCount
            )
        )
        launchQuizSession(
            ActiveQuizSession(
                mode = QuizMode.PRACTICE_MISTAKES,
                difficulty = Difficulty.MEDIUM,
                isDataPractice = true,
                smartPracticePlan = plan,
                questionCategories = plan.items.map { it.category },
                questionSources = plan.items.map { it.source },
                problems = plan.items.map { it.problem },
                countdownRemaining = 3
            )
        )
    }

    /**
     * One-tap CTA from Daily Workout completion: "Practice your 3 weakest categories now".
     */
    fun startPracticeWeakestThree() {
        startSmartPractice(questionCount = 20, categoryFilter = null)
    }

    fun setInsightsSubTab(tab: Int) {
        _uiState.update { it.copy(insightsSubTab = tab.coerceIn(0, 2)) }
    }

    fun setInsightsDateRange(range: InsightsDateRange) {
        _uiState.update { it.copy(insightsDateRange = range) }
        refreshAnalyticsState()
    }

    fun setInsightsOpFilter(opCode: String?) {
        _uiState.update {
            val next = if (it.insightsOpFilter == opCode) null else opCode
            it.copy(insightsOpFilter = next)
        }
        refreshAnalyticsState()
    }

    fun setCategorySortOrder(order: CategorySortOrder) {
        _uiState.update { it.copy(categorySortOrder = order) }
    }

    fun setHistoryFilter(filter: String) {
        _uiState.update { it.copy(historyFilter = filter) }
    }

    fun toggleHistoryDayExpanded(day: String) {
        _uiState.update {
            val next = if (it.expandedHistoryDay == day) null else day
            it.copy(expandedHistoryDay = next)
        }
    }

    fun openCategoryDetail(categoryId: String) {
        val st = _uiState.value
        val uid = st.userProfile.userId
        val rawLogs = st.rawAnswerLogs
        val aggs = answerLogService.loadDailyAggregates(uid)
        val detail = StatsService.buildCategoryDetail(
            categoryId = categoryId,
            rawLogs = rawLogs,
            rolledAggregates = aggs,
            myMedianMs = st.dashboardSnapshot.overallMedianTimeMs,
            todayStr = LocalDate.now().toString()
        )
        _uiState.update {
            it.copy(
                activeCategoryDetail = detail,
                route = ScreenRoute.CategoryDetail(categoryId)
            )
        }
    }

    fun closeCategoryDetail() {
        _uiState.update {
            it.copy(
                currentTab = BottomTab.PROGRESS,
                route = ScreenRoute.MainTabs
            )
        }
    }

    fun markMistakeMastered(normalizedKey: String) {
        val uid = _uiState.value.userProfile.userId
        answerLogService.markMistakeMastered(uid, normalizedKey)
        val updatedLegacy = _uiState.value.mistakes.filterNot { it.normalizedKey == normalizedKey }
        storage.saveMistakes(updatedLegacy, uid)
        storage.markDailyProgressDirty(uid)
        _uiState.update {
            it.copy(
                mistakes = updatedLegacy,
                syncStatus = computeSyncStatus(uid, it.userProfile.isGuest)
            )
        }
        refreshAnalyticsState(uid)
    }

    fun setQuizTimerPaused(paused: Boolean) {
        _uiState.update { st ->
            val q = st.activeQuiz ?: return@update st
            st.copy(activeQuiz = q.copy(isTimerPaused = paused))
        }
    }

    private fun launchQuizSession(session: ActiveQuizSession) {
        stopQuizTimer()
        _uiState.update {
            it.copy(
                route = ScreenRoute.ActiveQuiz,
                activeQuiz = session
            )
        }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(200L)
                val cur = _uiState.value.activeQuiz ?: break
                if (cur.countdownRemaining > 0 || cur.isTimerPaused) continue
                val newElapsed = cur.elapsedMs + 200L
                val limitMs = cur.timeLimitSeconds?.let { it * 1000L }
                if (limitMs != null && newElapsed >= limitMs) {
                    finishQuizSession(cur.copy(elapsedMs = limitMs))
                    break
                } else {
                    _uiState.update { state ->
                        state.copy(activeQuiz = state.activeQuiz?.copy(elapsedMs = newElapsed))
                    }
                }
            }
        }
        if (session.countdownRemaining > 0) {
            viewModelScope.launch {
                var rem = session.countdownRemaining
                while (rem > 0) {
                    delay(850L)
                    val cur = _uiState.value.activeQuiz ?: break
                    if (cur.countdownRemaining <= 0) break
                    if (cur.isTimerPaused) continue
                    rem = cur.countdownRemaining - 1
                    _uiState.update { st ->
                        st.copy(
                            activeQuiz = st.activeQuiz?.copy(
                                countdownRemaining = rem,
                                questionStartMs = if (rem == 0) st.activeQuiz.elapsedMs else 0L
                            )
                        )
                    }
                }
            }
        }
    }

    fun skipCountdown() {
        _uiState.update { st ->
            val q = st.activeQuiz ?: return@update st
            st.copy(
                activeQuiz = q.copy(
                    countdownRemaining = 0,
                    questionStartMs = q.elapsedMs
                )
            )
        }
    }

    fun onKeypadDigit(digit: Int) {
        val st = _uiState.value
        soundHaptic.vibrateTap(st.settings.hapticsEnabled)

        if (st.route == ScreenRoute.MemoryGame) {
            val mem = st.activeMemory ?: return
            if (mem.phase != MemoryPhase.ENTER_ANSWER) return
            val nextTyped = if (mem.typedInput == "0") digit.toString() else (mem.typedInput + digit).take(9)
            _uiState.update { it.copy(activeMemory = mem.copy(typedInput = nextTyped)) }
            return
        }

        val quiz = st.activeQuiz ?: return
        if (quiz.countdownRemaining > 0) {
            skipCountdown()
        }
        val activeQuizNow = _uiState.value.activeQuiz ?: return
        val currentProb = activeQuizNow.currentProblem ?: return
        val nextTyped = if (activeQuizNow.typedInput == "0") {
            digit.toString()
        } else {
            (activeQuizNow.typedInput + digit).take(10)
        }

        _uiState.update { it.copy(activeQuiz = activeQuizNow.copy(typedInput = nextTyped)) }

        if (st.settings.autoConfirm && nextTyped.length >= currentProb.digitCount) {
            submitQuizAnswer(nextTyped)
        }
    }

    fun onKeypadBackspace() {
        val st = _uiState.value
        soundHaptic.vibrateTap(st.settings.hapticsEnabled)
        if (st.route == ScreenRoute.MemoryGame) {
            val mem = st.activeMemory ?: return
            _uiState.update { it.copy(activeMemory = mem.copy(typedInput = mem.typedInput.dropLast(1))) }
            return
        }
        val quiz = st.activeQuiz ?: return
        _uiState.update { it.copy(activeQuiz = quiz.copy(typedInput = quiz.typedInput.dropLast(1))) }
    }

    fun onKeypadClear() {
        val st = _uiState.value
        soundHaptic.vibrateTap(st.settings.hapticsEnabled)
        if (st.route == ScreenRoute.MemoryGame) {
            val mem = st.activeMemory ?: return
            _uiState.update { it.copy(activeMemory = mem.copy(typedInput = "")) }
            return
        }
        val quiz = st.activeQuiz ?: return
        _uiState.update { it.copy(activeQuiz = quiz.copy(typedInput = "")) }
    }

    fun onKeypadEnter() {
        val st = _uiState.value
        if (st.route == ScreenRoute.MemoryGame) {
            submitMemoryFinalAnswer()
            return
        }
        val quiz = st.activeQuiz ?: return
        if (quiz.typedInput.isBlank()) return
        submitQuizAnswer(quiz.typedInput)
    }

    private fun submitQuizAnswer(rawTyped: String) {
        val st = _uiState.value
        val quiz = st.activeQuiz ?: return
        val prob = quiz.currentProblem ?: return
        val userVal = rawTyped.toLongOrNull() ?: return
        val isCorrect = (userVal == prob.answer)
        val qTimeMs = (quiz.elapsedMs - quiz.questionStartMs).coerceAtLeast(250L)

        if (isCorrect) {
            soundHaptic.playSuccess(st.settings.soundsEnabled)
        } else {
            soundHaptic.playError(st.settings.soundsEnabled)
            soundHaptic.vibrateError(st.settings.hapticsEnabled)
        }

        // Record AnswerLog & update spaced-repetition Mistake Book
        val category = quiz.questionCategories.getOrNull(quiz.currentIndex)
            ?: CategoryRegistry.classify(
                op = prob.op,
                operands = listOf(prob.a, prob.b),
                singleTableNumber = quiz.singleTableNumber
            )
        val opCode = CategoryRegistry.mathOpToOpCode(prob.op)
        val sourceTag = quiz.questionSources.getOrNull(quiz.currentIndex) ?: when {
            quiz.isDailyOfficial || quiz.isDailyExtra -> "daily"
            quiz.isMistakesReplay -> "mistake-replay"
            else -> "normal"
        }
        val logEntry = AnswerLog(
            ts = System.currentTimeMillis(),
            day = LocalDate.now().toString(),
            mode = AnswerLogService.quizModeToLogCode(quiz.mode, quiz.isDataPractice),
            op = opCode,
            category = category,
            difficulty = if (quiz.isDailyOfficial || quiz.isDailyExtra) null else AnswerLogService.difficultyToLogCode(quiz.difficulty),
            level = prob.level.takeIf { it > 0 },
            operands = listOf(prob.a, prob.b),
            prompt = AnswerLogService.problemToPrompt(prob),
            expected = prob.answer,
            given = userVal,
            correct = isCorrect,
            timeMs = qTimeMs,
            attempts = 1,
            source = sourceTag,
            sessionId = quiz.sessionId
        )
        // Local-first: record locally in SQLite and mark day dirty for the daily sync batch (NEVER write to Firestore per answer)
        answerLogService.record(
            userId = st.userProfile.userId,
            log = logEntry,
            problemForMistakeBook = prob,
            userMedianMs = st.dashboardSnapshot.overallMedianTimeMs,
            recordingEnabled = st.settings.recordAnswersEnabled
        )
        if (st.settings.recordAnswersEnabled) {
            storage.markDayDirty(logEntry.day, st.userProfile.userId)
            if (!isCorrect || quiz.isMistakesReplay || quiz.isDataPractice) {
                storage.markDailyProgressDirty(st.userProfile.userId)
            }
        }

        val answered = AnsweredProblem(
            problem = prob,
            userAnswer = userVal,
            isCorrect = isCorrect,
            timeMs = qTimeMs
        )

        val currentMistakes = st.mistakes.toMutableList()
        if (!isCorrect) {
            if (currentMistakes.none { it.normalizedKey == prob.normalizedKey && it.displayBase == prob.displayBase }) {
                currentMistakes.add(prob)
                storage.saveMistakes(currentMistakes)
            }
        } else if (quiz.isMistakesReplay || quiz.isDataPractice) {
            currentMistakes.removeAll { it.normalizedKey == prob.normalizedKey || it.displayBase == prob.displayBase }
            storage.saveMistakes(currentMistakes)
        }

        val updatedProblems = if (quiz.isMistakesReplay && !quiz.isDataPractice && !isCorrect) {
            quiz.problems + prob.copy(id = "${prob.id}_retry_${quiz.problems.size}")
        } else {
            quiz.problems
        }

        val updatedAnswers = quiz.answers + answered
        val nextIndex = quiz.currentIndex + 1

        var updatedDaily = st.dailyState
        if (quiz.isDailyOfficial) {
            updatedDaily = updatedDaily.copy(todayAnswers = updatedAnswers)
            storage.saveDailyState(updatedDaily)
        }

        val flash = if (isCorrect) FlashFeedback.CORRECT else FlashFeedback.WRONG

        if (nextIndex >= updatedProblems.size) {
            val completedSession = quiz.copy(
                problems = updatedProblems,
                currentIndex = nextIndex,
                answers = updatedAnswers,
                typedInput = "",
                flashFeedback = flash
            )
            _uiState.update {
                it.copy(
                    mistakes = currentMistakes,
                    dailyState = updatedDaily,
                    activeQuiz = completedSession
                )
            }
            finishQuizSession(completedSession)
        } else {
            _uiState.update {
                it.copy(
                    mistakes = currentMistakes,
                    dailyState = updatedDaily,
                    activeQuiz = quiz.copy(
                        problems = updatedProblems,
                        currentIndex = nextIndex,
                        answers = updatedAnswers,
                        typedInput = "",
                        questionStartMs = quiz.elapsedMs,
                        flashFeedback = flash
                    )
                )
            }
            viewModelScope.launch {
                delay(280L)
                _uiState.update { s ->
                    s.copy(activeQuiz = s.activeQuiz?.copy(flashFeedback = FlashFeedback.NONE))
                }
            }
        }
    }

    private fun finishQuizSession(session: ActiveQuizSession) {
        stopQuizTimer()
        val st = _uiState.value
        val uid = st.userProfile.userId
        val todayStr = LocalDate.now().toString()

        val totalQ = session.problems.size.coerceAtLeast(1)
        val accuracy = session.correctCount.toDouble() / totalQ.toDouble()
        val modeKey = "${session.mode.name}_${session.difficulty.name}_$totalQ"
        val newRecord = ScoreRecord(
            id = UUID.randomUUID().toString(),
            modeKey = modeKey,
            mode = session.mode,
            difficulty = session.difficulty,
            questionCount = totalQ,
            correctCount = session.correctCount,
            totalTimeMs = session.elapsedMs.coerceAtLeast(500L),
            accuracy = accuracy,
            timestamp = System.currentTimeMillis()
        )
        val updatedScores = st.scores + newRecord
        storage.saveScores(updatedScores, uid)
        storage.markScoreDirty(newRecord.id, uid)
        storage.markDayDirty(todayStr, uid)

        val updatedTableStats = st.tableStats.toMutableMap()
        if (session.mode == QuizMode.MULTIPLICATION_TABLES && session.singleTableNumber != null) {
            val tbl = session.singleTableNumber
            val existing = updatedTableStats[tbl]
            val elapsed = session.elapsedMs.coerceAtLeast(500L)
            updatedTableStats[tbl] = if (existing == null) {
                TableStat(tableNumber = tbl, bestTimeMs = elapsed, lastTimeMs = elapsed, prevTimeMs = null)
            } else {
                TableStat(
                    tableNumber = tbl,
                    bestTimeMs = minOf(existing.bestTimeMs, elapsed),
                    lastTimeMs = elapsed,
                    prevTimeMs = existing.lastTimeMs
                )
            }
            storage.saveTableStats(updatedTableStats, uid)
        }

        // Refresh analytics first so we can compute Before vs After category accuracy and updated Practice Streak
        val freshLogs = answerLogService.loadAnswerLogs(uid)
        val freshAggs = answerLogService.loadDailyAggregates(uid)
        val freshMistakeBook = answerLogService.loadMistakeBook(uid, includeMastered = false)
        val freshSnapshot = StatsService.buildDashboardSnapshot(
            rawLogs = freshLogs,
            rolledAggregates = freshAggs,
            dateRange = st.insightsDateRange,
            opFilter = st.insightsOpFilter,
            todayStr = todayStr
        )

        // Update the 6 adaptive skill levels (Lv 1-10) from ALL quiz answers and update Practice Streak state
        val (updatedSkills, levelChanges) = DailyEngine.updateSkillsFromQuizAnswers(
            currentSkills = st.dailyState.skills,
            sessionAnswers = session.answers
        )
        val updatedDaily = st.dailyState.copy(
            date = todayStr,
            skills = updatedSkills,
            streak = freshSnapshot.practiceStreakState,
            isCompletedToday = freshSnapshot.totalQuestionsToday >= 10,
            lastLevelChanges = levelChanges
        )
        storage.saveDailyState(updatedDaily, uid)
        storage.markDailyProgressDirty(uid)

        NotificationScheduler.rescheduleNotifications(
            context = getApplication(),
            settings = st.settings,
            isDailyDoneToday = updatedDaily.isCompletedToday,
            currentStreak = updatedDaily.streak.current,
            dueMistakesCount = freshMistakeBook.count { it.nextReviewDay <= todayStr }
        )

        var deltas = emptyList<CategoryBeforeAfterDelta>()
        var resolvedMistakes = emptyList<Problem>()
        if (session.isDataPractice && session.smartPracticePlan != null) {
            val plan = session.smartPracticePlan
            val afterMap = freshSnapshot.categoryStats.associate { it.categoryId to it.accuracy }
            val distinctCats = session.questionCategories.distinct()
            deltas = distinctCats.map { catId ->
                val catIndices = session.questionCategories.indices.filter { session.questionCategories[it] == catId }
                val catAnswers = catIndices.mapNotNull { session.answers.getOrNull(it) }
                val sesCor = catAnswers.count { it.isCorrect }
                val sesTot = catAnswers.size.coerceAtLeast(1)
                CategoryBeforeAfterDelta(
                    categoryId = catId,
                    beforeAccuracy = plan.beforeCategoryAccuracy[catId],
                    afterAccuracy = afterMap[catId] ?: (sesCor.toDouble() / sesTot.toDouble()),
                    sessionCorrect = sesCor,
                    sessionTotal = sesTot
                )
            }
            resolvedMistakes = session.answers.mapIndexedNotNull { idx, ans ->
                val src = session.questionSources.getOrNull(idx)
                if (src == "data-practice-mistake" && ans.isCorrect) ans.problem else null
            }
        }

        _uiState.update {
            it.copy(
                dailyState = updatedDaily,
                scores = updatedScores,
                tableStats = updatedTableStats,
                lastCompletedQuiz = session,
                activeQuiz = null,
                dashboardSnapshot = freshSnapshot,
                mistakeBook = freshMistakeBook,
                rawAnswerLogs = freshLogs,
                lastSmartPracticeDeltas = deltas,
                lastSmartPracticeResolvedMistakes = resolvedMistakes,
                syncStatus = computeSyncStatus(uid, it.userProfile.isGuest),
                pendingSyncItemsCount = computePendingSyncItemsCount(uid, freshLogs),
                route = ScreenRoute.QuizResult
            )
        }
    }

    fun requestBackFromQuiz() {
        navigateToHome()
    }

    private fun stopQuizTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // --- Arithmetic Memory ("Follow the chain") ---
    fun updateMemoryDifficulty(diff: Difficulty) {
        _uiState.update { it.copy(memoryDifficulty = diff) }
    }

    fun updateMemoryStepsCount(steps: Int) {
        _uiState.update { it.copy(memoryStepsCount = steps.coerceIn(5, 10)) }
    }

    fun startMemoryGame() {
        memoryJob?.cancel()
        val st = _uiState.value
        val chain = QuestionGenerator.generateMemoryChain(
            difficulty = st.memoryDifficulty,
            stepsCount = st.memoryStepsCount
        )
        val session = ActiveMemorySession(
            difficulty = st.memoryDifficulty,
            stepsCount = st.memoryStepsCount,
            chain = chain,
            phase = MemoryPhase.SHOW_START,
            currentStepIndex = 0,
            questionStartMs = System.currentTimeMillis()
        )
        _uiState.update {
            it.copy(route = ScreenRoute.MemoryGame, activeMemory = session)
        }

        memoryJob = viewModelScope.launch {
            delay(2000L)
            for (i in chain.steps.indices) {
                _uiState.update { s ->
                    s.copy(
                        activeMemory = s.activeMemory?.copy(
                            phase = MemoryPhase.SHOW_STEP,
                            currentStepIndex = i
                        )
                    )
                }
                delay(1750L)
            }
            _uiState.update { s ->
                s.copy(
                    activeMemory = s.activeMemory?.copy(
                        phase = MemoryPhase.ENTER_ANSWER
                    )
                )
            }
        }
    }

    fun advanceMemoryStepManually() {
        val mem = _uiState.value.activeMemory ?: return
        memoryJob?.cancel()
        if (mem.phase == MemoryPhase.SHOW_START) {
            _uiState.update {
                it.copy(activeMemory = mem.copy(phase = MemoryPhase.SHOW_STEP, currentStepIndex = 0))
            }
        } else if (mem.phase == MemoryPhase.SHOW_STEP) {
            val nextIdx = mem.currentStepIndex + 1
            if (nextIdx < mem.chain.steps.size) {
                _uiState.update {
                    it.copy(activeMemory = mem.copy(currentStepIndex = nextIdx))
                }
            } else {
                _uiState.update {
                    it.copy(activeMemory = mem.copy(phase = MemoryPhase.ENTER_ANSWER))
                }
            }
        }
    }

    private fun submitMemoryFinalAnswer() {
        val st = _uiState.value
        val mem = st.activeMemory ?: return
        val typed = mem.typedInput.toIntOrNull() ?: return
        val isCorrect = (typed == mem.chain.finalAnswer)
        val qTimeMs = (System.currentTimeMillis() - mem.questionStartMs).coerceAtLeast(1000L)

        if (isCorrect) {
            soundHaptic.playSuccess(st.settings.soundsEnabled)
        } else {
            soundHaptic.playError(st.settings.soundsEnabled)
            soundHaptic.vibrateError(st.settings.hapticsEnabled)
        }

        val cat = CategoryRegistry.classify(
            op = "chain",
            operands = listOf(mem.chain.startNumber),
            chainStepCount = mem.stepsCount
        )
        val prompt = buildString {
            append(mem.chain.startNumber)
            mem.chain.steps.forEach { append(" ").append(it.displayStep) }
        }
        val logEntry = AnswerLog(
            ts = System.currentTimeMillis(),
            day = LocalDate.now().toString(),
            mode = "memory",
            op = "chain",
            category = cat,
            difficulty = AnswerLogService.difficultyToLogCode(mem.difficulty),
            level = null,
            operands = listOf(mem.chain.startNumber) + mem.chain.steps.map { it.operand },
            prompt = prompt,
            expected = mem.chain.finalAnswer.toLong(),
            given = typed.toLong(),
            correct = isCorrect,
            timeMs = qTimeMs,
            attempts = 1,
            source = "normal",
            sessionId = mem.sessionId
        )
        answerLogService.record(
            userId = st.userProfile.userId,
            log = logEntry,
            problemForMistakeBook = null,
            userMedianMs = st.dashboardSnapshot.overallMedianTimeMs,
            recordingEnabled = st.settings.recordAnswersEnabled
        )
        if (st.settings.recordAnswersEnabled) {
            storage.markDayDirty(logEntry.day, st.userProfile.userId)
        }

        val completedMem = mem.copy(
            userFinalAnswer = typed,
            isCorrect = isCorrect,
            elapsedMs = qTimeMs
        )
        _uiState.update {
            it.copy(
                activeMemory = completedMem,
                syncStatus = computeSyncStatus(st.userProfile.userId, st.userProfile.isGuest),
                route = ScreenRoute.MemoryResult
            )
        }
        refreshAnalyticsState(st.userProfile.userId)
    }

    // --- Multiplication Tables ---
    fun setTablesSubTab(index: Int) {
        _uiState.update { it.copy(tablesTabIndex = index) }
    }

    fun setActiveLearnTable(table: Int) {
        _uiState.update { it.copy(activeLearnTable = table.coerceIn(1, 50)) }
    }

    fun toggleSelectedTable(table: Int) {
        _uiState.update { st ->
            val next = st.selectedTables.toMutableSet()
            if (table in next) {
                if (next.size > 1) next.remove(table)
            } else {
                next.add(table)
            }
            st.copy(selectedTables = next)
        }
    }

    fun selectAllTables(all: Boolean) {
        _uiState.update { st ->
            val next = if (all) (1..50).toSet() else setOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
            st.copy(selectedTables = next)
        }
    }

    // --- Settings & Practice Reminder ---
    fun updatePracticeReminder(
        reminderEnabled: Boolean = _uiState.value.settings.reminderEnabled,
        reminderHour: Int = _uiState.value.settings.reminderHour,
        reminderMinute: Int = _uiState.value.settings.reminderMinute
    ) {
        val st = _uiState.value
        val uid = st.userProfile.userId
        val next = st.settings.copy(
            reminderEnabled = reminderEnabled,
            reminderHour = reminderHour.coerceIn(0, 23),
            reminderMinute = reminderMinute.coerceIn(0, 59)
        )
        storage.saveSettings(next, uid)
        storage.markProfileDirty(uid)
        _uiState.update {
            it.copy(
                settings = next,
                syncStatus = computeSyncStatus(uid, it.userProfile.isGuest),
                pendingSyncItemsCount = computePendingSyncItemsCount(uid, it.rawAnswerLogs)
            )
        }
        NotificationScheduler.rescheduleNotifications(
            context = getApplication(),
            settings = next,
            isDailyDoneToday = st.dailyState.isCompletedToday,
            currentStreak = st.dailyState.streak.current,
            dueMistakesCount = st.mistakeBook.count { it.nextReviewDay <= st.dailyState.date }
        )
    }

    fun updateGameSettings(
        autoConfirm: Boolean = _uiState.value.settings.autoConfirm,
        keypadType: KeypadType = _uiState.value.settings.keypadType,
        keypadSize: KeypadSize = _uiState.value.settings.keypadSize,
        soundsEnabled: Boolean = _uiState.value.settings.soundsEnabled,
        hapticsEnabled: Boolean = _uiState.value.settings.hapticsEnabled
    ) {
        val uid = _uiState.value.userProfile.userId
        val next = _uiState.value.settings.copy(
            autoConfirm = autoConfirm,
            keypadType = keypadType,
            keypadSize = keypadSize,
            soundsEnabled = soundsEnabled,
            hapticsEnabled = hapticsEnabled
        )
        storage.saveSettings(next)
        storage.markProfileDirty(uid)
        _uiState.update {
            it.copy(
                settings = next,
                syncStatus = computeSyncStatus(uid, it.userProfile.isGuest)
            )
        }
    }

    fun updateDataSettings(
        recordAnswersEnabled: Boolean = _uiState.value.settings.recordAnswersEnabled,
        showSourceChipInSmartPractice: Boolean = _uiState.value.settings.showSourceChipInSmartPractice,
        dailyGoalQuestions: Int = _uiState.value.settings.dailyGoalQuestions,
        mistakeReminderEnabled: Boolean = _uiState.value.settings.mistakeReminderEnabled,
        weeklySummaryReminderEnabled: Boolean = _uiState.value.settings.weeklySummaryReminderEnabled
    ) {
        val st = _uiState.value
        val uid = st.userProfile.userId
        val next = st.settings.copy(
            recordAnswersEnabled = recordAnswersEnabled,
            showSourceChipInSmartPractice = showSourceChipInSmartPractice,
            dailyGoalQuestions = dailyGoalQuestions.coerceIn(5, 200),
            mistakeReminderEnabled = mistakeReminderEnabled,
            weeklySummaryReminderEnabled = weeklySummaryReminderEnabled
        )
        storage.saveSettings(next)
        storage.markProfileDirty(uid)
        _uiState.update {
            it.copy(
                settings = next,
                syncStatus = computeSyncStatus(uid, it.userProfile.isGuest)
            )
        }
        NotificationScheduler.rescheduleNotifications(
            context = getApplication(),
            settings = next,
            isDailyDoneToday = st.dailyState.isCompletedToday,
            currentStreak = st.dailyState.streak.current,
            dueMistakesCount = st.mistakeBook.count { it.nextReviewDay <= st.dailyState.date }
        )
    }

    fun exportUserData(format: String) {
        val uid = _uiState.value.userProfile.userId
        val content = if (format.equals("csv", ignoreCase = true)) {
            answerLogService.exportDataAsCsv(uid)
        } else {
            answerLogService.exportDataAsJson(uid)
        }
        shareText(content)
    }

    fun selectLanguage(langCode: String) {
        val uid = _uiState.value.userProfile.userId
        i18n.setLanguage(langCode)
        val next = _uiState.value.settings.copy(languageCode = langCode)
        storage.saveSettings(next)
        storage.markProfileDirty(uid)
        _uiState.update {
            it.copy(
                settings = next,
                syncStatus = computeSyncStatus(uid, it.userProfile.isGuest)
            )
        }
    }

    fun selectAppearanceMode(mode: AppearanceMode) {
        val st = _uiState.value
        val uid = st.userProfile.userId
        val nextSettings = st.settings.copy(appearanceMode = mode)
        val nextProfile = st.userProfile.copy(appearanceMode = mode)
        storage.saveSettings(nextSettings, uid)
        storage.saveUserProfile(nextProfile, uid)
        storage.markProfileDirty(uid)
        _uiState.update {
            it.copy(
                settings = nextSettings,
                userProfile = nextProfile,
                syncStatus = computeSyncStatus(uid, nextProfile.isGuest)
            )
        }
    }

    fun selectTheme(themeId: AppThemeId) {
        val st = _uiState.value
        val uid = st.userProfile.userId
        // If user explicitly picks DARK/BLACK_AMOLED or LIGHT from Accent/Theme chips, also sync appearanceMode if appropriate
        val nextAppearance = when (themeId) {
            AppThemeId.LIGHT -> AppearanceMode.LIGHT
            AppThemeId.DARK, AppThemeId.BLACK_AMOLED -> AppearanceMode.DARK
            else -> st.settings.appearanceMode
        }
        val next = st.settings.copy(themeId = themeId, appearanceMode = nextAppearance)
        val nextProf = st.userProfile.copy(appearanceMode = nextAppearance)
        storage.saveSettings(next, uid)
        storage.saveUserProfile(nextProf, uid)
        storage.markProfileDirty(uid)
        _uiState.update {
            it.copy(
                settings = next,
                userProfile = nextProf,
                syncStatus = computeSyncStatus(uid, nextProf.isGuest)
            )
        }
    }

    fun toggleReminderDay(dayOfWeek: Int) {
        val st = _uiState.value
        val days = st.settings.reminderDays.toMutableSet()
        if (dayOfWeek in days && days.size > 1) {
            days.remove(dayOfWeek)
        } else {
            days.add(dayOfWeek)
        }
        val next = st.settings.copy(reminderDays = days)
        storage.saveSettings(next)
        _uiState.update { it.copy(settings = next) }
    }

    fun shareText(text: String) {
        runCatching {
            val ctx = getApplication<Application>()
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Numera Data").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
        }
    }

    fun showToast(msg: String?) {
        _uiState.update { it.copy(toastMessage = msg) }
    }
}
