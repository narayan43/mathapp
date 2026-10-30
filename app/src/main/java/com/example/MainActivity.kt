package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.util.Log
import androidx.activity.viewModels
import com.example.ui.BottomTab
import com.example.ui.NumeraViewModel
import com.example.ui.ScreenRoute
import com.example.ui.components.GuestExpiredPromptDialog
import com.example.ui.components.GuestMergePromptDialog
import com.example.ui.screens.ActiveQuizScreen
import com.example.ui.screens.CategoryDetailScreen
import com.example.ui.screens.CustomSetupScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoryGameScreen
import com.example.ui.screens.MemoryResultScreen
import com.example.ui.screens.MemorySetupScreen
import com.example.ui.screens.PreGameSetupScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.QuizResultScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignInOrSignUpScreen
import com.example.ui.screens.SmartPracticeSetupDialog
import com.example.ui.screens.TablesHubScreen
import com.example.ui.screens.VerifyEmailScreen
import com.example.ui.theme.NumeraTheme

class MainActivity : ComponentActivity() {

    private val viewModel: NumeraViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Install crash logging hook so any unexpected runtime errors are captured in logs
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("NumeraCrash", "Fatal exception in Numera on ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Initialize Firebase safely if not already done by provider
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(this)
            }
        }

        // Apply edge-to-edge layout safely
        runCatching {
            enableEdgeToEdge()
        }

        // Apply persisted appearance background
        runCatching {
            val prefs = applicationContext.getSharedPreferences("numera_prefs", MODE_PRIVATE)
            val mode = prefs.getString("APPEARANCE_MODE", "LIGHT") ?: "LIGHT"
            val bgArgb = if (mode == "DARK") 0xFF151B21.toInt() else 0xFFF4F7FA.toInt()
            window.decorView.setBackgroundColor(bgArgb)
        }

        setContent {
            NumeraApp(viewModel = viewModel)
        }
    }
}

@Composable
fun NumeraApp(viewModel: NumeraViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.setQuizTimerPaused(true)
                Lifecycle.Event.ON_STOP -> viewModel.onAppBackgrounded()
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.setQuizTimerPaused(false)
                    viewModel.onAppResumed()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        val msg = uiState.toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.showToast(null)
        }
    }

    NumeraTheme(
        appearanceMode = uiState.settings.appearanceMode,
        themeId = uiState.settings.themeId
    ) {
        if (uiState.showGuestMergePrompt) {
            GuestMergePromptDialog(
                title = viewModel.t("auth.merge_title"),
                body = viewModel.t("auth.merge_body"),
                mergeButtonText = viewModel.t("auth.merge_keep"),
                cloudOnlyButtonText = viewModel.t("auth.merge_discard"),
                onMergeConfirm = { viewModel.resolveGuestMergeChoice(mergeLocalIntoCloud = true) },
                onCloudOnlyConfirm = { viewModel.resolveGuestMergeChoice(mergeLocalIntoCloud = false) }
            )
        }

        if (uiState.showGuestExpiredDialog) {
            val context = LocalContext.current
            GuestExpiredPromptDialog(
                title = viewModel.t("auth.guest_card_title"),
                body = viewModel.t("auth.guest_card_sub"),
                onSignInWithGoogle = { viewModel.signInWithGoogle(context) },
                onSignInWithEmail = {
                    viewModel.dismissGuestExpiredDialog()
                    viewModel.openAuthHub(isSignUp = true)
                }
            )
        }

        if (uiState.showSmartPracticeSheet) {
            SmartPracticeSetupDialog(
                uiState = uiState,
                viewModel = viewModel
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (uiState.route == ScreenRoute.MainTabs) {
                    NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                        NavigationBarItem(
                            selected = (uiState.currentTab == BottomTab.HOME),
                            onClick = { viewModel.selectBottomTab(BottomTab.HOME) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.currentTab == BottomTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = viewModel.t("nav.home")
                                )
                            },
                            label = { Text(viewModel.t("nav.home")) },
                            modifier = Modifier.testTag("nav_tab_home")
                        )
                        NavigationBarItem(
                            selected = (uiState.currentTab == BottomTab.PROGRESS),
                            onClick = { viewModel.selectBottomTab(BottomTab.PROGRESS) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.currentTab == BottomTab.PROGRESS) Icons.Filled.TrendingUp else Icons.Outlined.TrendingUp,
                                    contentDescription = viewModel.t("nav.progress")
                                )
                            },
                            label = { Text(viewModel.t("nav.progress")) },
                            modifier = Modifier.testTag("nav_tab_progress")
                        )
                        NavigationBarItem(
                            selected = (uiState.currentTab == BottomTab.SETTINGS),
                            onClick = { viewModel.selectBottomTab(BottomTab.SETTINGS) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.currentTab == BottomTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                                    contentDescription = viewModel.t("nav.settings")
                                )
                            },
                            label = { Text(viewModel.t("nav.settings")) },
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                }
            }
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)

            when (val route = uiState.route) {
                is ScreenRoute.AuthHub -> SignInOrSignUpScreen(route.isSignUpMode, uiState, viewModel, contentModifier)
                is ScreenRoute.VerifyEmail -> VerifyEmailScreen(uiState, viewModel, contentModifier)
                is ScreenRoute.ProfileSetup -> ProfileSetupScreen(uiState, viewModel, contentModifier)
                is ScreenRoute.MainTabs -> {
                    when (uiState.currentTab) {
                        BottomTab.HOME -> HomeScreen(uiState, viewModel, contentModifier)
                        BottomTab.PROGRESS -> ProgressScreen(uiState, viewModel, contentModifier)
                        BottomTab.SETTINGS -> SettingsScreen(uiState, viewModel, contentModifier)
                    }
                }
                is ScreenRoute.PreGameSetup -> PreGameSetupScreen(route.mode, uiState, viewModel, contentModifier)
                is ScreenRoute.ActiveQuiz -> ActiveQuizScreen(uiState, viewModel, contentModifier)
                is ScreenRoute.QuizResult -> QuizResultScreen(uiState, viewModel, contentModifier)
                is ScreenRoute.MemorySetup -> MemorySetupScreen(uiState, viewModel, contentModifier)
                is ScreenRoute.MemoryGame -> MemoryGameScreen(uiState, viewModel, contentModifier)
                is ScreenRoute.MemoryResult -> MemoryResultScreen(uiState, viewModel, contentModifier)
                is ScreenRoute.TablesHub -> TablesHubScreen(uiState, viewModel, contentModifier)
                is ScreenRoute.CustomSetup -> CustomSetupScreen(uiState, viewModel, contentModifier)
                is ScreenRoute.CategoryDetail -> CategoryDetailScreen(uiState, viewModel, contentModifier)
            }
        }
    }
}

