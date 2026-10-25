package com.example.ui.screens

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.I18nService
import com.example.model.PasswordStrength
import com.example.model.PresetAvatar
import com.example.ui.NumeraUiState
import com.example.ui.NumeraViewModel
import com.example.ui.components.PresetAvatarBadge
import com.example.ui.theme.NumeraDanger
import com.example.ui.theme.NumeraPrimary
import com.example.ui.theme.NumeraSuccess
import com.example.ui.theme.NumeraTertiary
import com.example.ui.theme.NumeraWarning
import kotlinx.coroutines.delay

private enum class AuthSubScreen {
    WELCOME,
    EMAIL
}

private enum class LegalModalType {
    NONE,
    TERMS,
    PRIVACY
}

/**
 * Option-based, one-choice-per-screen authentication flow for Numera:
 * - Screen A (WELCOME): Clean welcome screen with app logo, "Numera", tagline, animated math symbols
 *   (+ − × ÷), and 3 full-width 56dp pill buttons (Continue with Google, Continue with Email,
 *   Continue as guest) + Terms and Privacy Policy links.
 * - Screen B (EMAIL): Slide-in screen with curved top header, segmented Log in | Sign up toggle,
 *   56dp rounded inputs, inline validation, password strength bar, Forgot password modal, and
 *   bottom switch link.
 * - Screen C (GOOGLE): Triggered directly when tapping "Continue with Google".
 * - Screen D (GUEST): ModalBottomSheet explaining device-only storage with "Continue as guest"
 *   and "Sign in instead" actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInOrSignUpScreen(
    isSignUpInitial: Boolean,
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animationsEnabled = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) > 0f
        }.getOrDefault(true)
    }

    var currentSubScreen by rememberSaveable { mutableStateOf(AuthSubScreen.WELCOME) }
    var isCreateAccountMode by rememberSaveable(isSignUpInitial) { mutableStateOf(isSignUpInitial) }
    var showGuestBottomSheet by rememberSaveable { mutableStateOf(false) }
    var activeLegalModal by rememberSaveable { mutableStateOf(LegalModalType.NONE) }
    var showForgotPasswordDialog by rememberSaveable { mutableStateOf(false) }
    var resetEmailInput by rememberSaveable { mutableStateOf("") }

    // Back handling:
    // - If on EMAIL screen, Back returns to WELCOME screen.
    // - If on WELCOME screen and already authenticated (e.g. Guest linking account from Settings), Back returns to app.
    BackHandler(enabled = currentSubScreen == AuthSubScreen.EMAIL || uiState.isAuthenticated) {
        if (currentSubScreen == AuthSubScreen.EMAIL) {
            viewModel.clearAuthError()
            currentSubScreen = AuthSubScreen.WELCOME
        } else if (uiState.isAuthenticated) {
            viewModel.closeAuthHubBackToAppIfSignedIn()
        }
    }

    // Legal Terms / Privacy Policy Dialog
    if (activeLegalModal != LegalModalType.NONE) {
        val isTerms = activeLegalModal == LegalModalType.TERMS
        AlertDialog(
            onDismissRequest = { activeLegalModal = LegalModalType.NONE },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = if (isTerms) viewModel.t("auth.terms_title") else viewModel.t("auth.privacy_title"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isTerms) {
                        Text(
                            text = viewModel.t("auth.terms_summary"),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = viewModel.t("auth.privacy_summary"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { activeLegalModal = LegalModalType.NONE },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("legal_modal_ok_btn")
                ) {
                    Text(viewModel.t("auth.ok"))
                }
            }
        )
    }

    // Forgot Password Dialog
    if (showForgotPasswordDialog) {
        var resetEmailError by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = viewModel.t("auth.forgot_password"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = viewModel.t("auth.forgot_password_sub"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = resetEmailInput,
                        onValueChange = {
                            resetEmailInput = it
                            resetEmailError = null
                        },
                        label = { Text(viewModel.t("auth.email_label")) },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        isError = resetEmailError != null,
                        supportingText = resetEmailError?.let { err ->
                            { Text(err, color = NumeraDanger) }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("reset_password_email_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = resetEmailInput.trim()
                        if (trimmed.isEmpty()) {
                            resetEmailError = viewModel.t("auth.err_email_required")
                        } else if (!isValidEmailFormat(trimmed)) {
                            resetEmailError = viewModel.t("auth.err_invalid_email")
                        } else {
                            showForgotPasswordDialog = false
                            viewModel.sendPasswordResetEmail(trimmed)
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.testTag("confirm_reset_password_btn")
                ) {
                    Text(viewModel.t("auth.reset_password_btn"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showForgotPasswordDialog = false },
                    modifier = Modifier.testTag("cancel_reset_password_btn")
                ) {
                    Text(viewModel.t("auth.cancel"))
                }
            }
        )
    }

    // Screen D — Guest Mode Confirmation Bottom Sheet
    if (showGuestBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showGuestBottomSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("guest_confirm_bottom_sheet")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = viewModel.t("auth.guest_sheet_title"),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = viewModel.t("auth.guest_sheet_body"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        showGuestBottomSheet = false
                        viewModel.continueAsGuest()
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("guest_sheet_confirm_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = viewModel.t("auth.continue_guest"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        showGuestBottomSheet = false
                    },
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("guest_sheet_signin_instead_btn")
                ) {
                    Text(
                        text = viewModel.t("auth.sign_in_instead"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Soft 200ms entrance/slide transition between Screen A (Welcome) and Screen B (Email)
    AnimatedContent(
        targetState = currentSubScreen,
        transitionSpec = {
            if (!animationsEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (targetState == AuthSubScreen.EMAIL) {
                (slideInHorizontally(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> fullWidth / 3 }
                ) + fadeIn(animationSpec = tween(200))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> -fullWidth / 4 }
                    ) + fadeOut(animationSpec = tween(180)))
            } else {
                (slideInHorizontally(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    initialOffsetX = { fullWidth -> -fullWidth / 4 }
                ) + fadeIn(animationSpec = tween(200))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                        targetOffsetX = { fullWidth -> fullWidth / 3 }
                    ) + fadeOut(animationSpec = tween(180)))
            }
        },
        label = "auth_sub_screen_transition",
        modifier = modifier
            .fillMaxSize()
            .testTag("auth_hub_screen")
    ) { subScreen ->
        when (subScreen) {
            AuthSubScreen.WELCOME -> {
                WelcomeChoiceScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    animationsEnabled = animationsEnabled,
                    onContinueWithGoogle = {
                        viewModel.clearAuthError()
                        viewModel.signInWithGoogle(context)
                    },
                    onContinueWithEmail = {
                        viewModel.clearAuthError()
                        isCreateAccountMode = isSignUpInitial
                        currentSubScreen = AuthSubScreen.EMAIL
                    },
                    onContinueAsGuest = {
                        viewModel.clearAuthError()
                        showGuestBottomSheet = true
                    },
                    onOpenTerms = { activeLegalModal = LegalModalType.TERMS },
                    onOpenPrivacy = { activeLegalModal = LegalModalType.PRIVACY }
                )
            }

            AuthSubScreen.EMAIL -> {
                EmailAuthScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    isCreateAccountMode = isCreateAccountMode,
                    onToggleMode = { createMode ->
                        isCreateAccountMode = createMode
                        viewModel.clearAuthError()
                    },
                    onBackToWelcome = {
                        viewModel.clearAuthError()
                        currentSubScreen = AuthSubScreen.WELCOME
                    },
                    onForgotPassword = { prefilledEmail ->
                        resetEmailInput = prefilledEmail
                        showForgotPasswordDialog = true
                    }
                )
            }
        }
    }
}

/**
 * Screen A — "Welcome" (first screen when signed out)
 * - Top: App logo + name "Numera" + one-line tagline ("Train your mental math. 100% free.")
 * - Middle: Friendly animated math symbols (+ − × ÷) using the app theme colors
 * - Three stacked full-width buttons (56dp high, pill shape, 16dp gap):
 *   1. "Continue with Google" — white/outlined button with the official Google "G" logo
 *   2. "Continue with Email" — primary filled button with an envelope icon
 *   3. "Continue as guest" — soft tinted/text button with a person icon + caption
 * - Bottom: "By continuing you agree to the Terms and Privacy Policy" (links)
 */
@Composable
private fun WelcomeChoiceScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    animationsEnabled: Boolean,
    onContinueWithGoogle: () -> Unit,
    onContinueWithEmail: () -> Unit,
    onContinueAsGuest: () -> Unit,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("auth_welcome_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 480.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Section: Optional Back button (if linking from Guest) + Logo + App Name + One-line Tagline
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (uiState.isAuthenticated) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.closeAuthHubBackToAppIfSignedIn() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("auth_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = viewModel.t("auth.back_to_welcome")
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // App Logo Badge
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    border = BorderStroke(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)
                    ),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Calculate,
                            contentDescription = viewModel.t("app.name"),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = viewModel.t("app.name"),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = viewModel.t("auth.welcome_tagline"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("welcome_tagline_text")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Middle Section: Friendly Animated Math Symbols (+ − × ÷) using app theme colors
            AnimatedMathSymbolsIllustration(
                animationsEnabled = animationsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .testTag("welcome_math_illustration")
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Error banner if Google Sign-In returned an error
            if (uiState.authError != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = NumeraDanger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, NumeraDanger.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("auth_error_banner")
                ) {
                    Text(
                        text = uiState.authError,
                        color = NumeraDanger,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // Three Stacked Full-Width Pill Buttons (56dp high, pill shape, 16dp gap)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. "Continue with Google" — white/outlined button with official Google "G" logo
                OutlinedButton(
                    onClick = onContinueWithGoogle,
                    enabled = !uiState.authLoading,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("google_sign_in_btn")
                ) {
                    if (uiState.authLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        GoogleGLogoBadge(modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.t("auth.google_btn"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 2. "Continue with Email" — primary filled button with envelope icon
                Button(
                    onClick = onContinueWithEmail,
                    enabled = !uiState.authLoading,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("continue_with_email_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = viewModel.t("auth.continue_email"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 3. "Continue as guest" — soft tinted/text button with person icon + small caption
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onContinueAsGuest,
                        enabled = !uiState.authLoading,
                        shape = RoundedCornerShape(28.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("auth_hub_guest_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = viewModel.t("auth.continue_guest"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = viewModel.t("auth.guest_device_only_caption"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("guest_device_only_caption")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bottom: "By continuing you agree to the Terms and Privacy Policy" with accessible links
            TermsAndPrivacyFooter(
                viewModel = viewModel,
                onOpenTerms = onOpenTerms,
                onOpenPrivacy = onOpenPrivacy
            )
        }
    }
}

/**
 * Friendly illustration with animated math symbols (+ − × ÷) using the app theme colors.
 * Respects reduced-motion automatically.
 */
@Composable
private fun AnimatedMathSymbolsIllustration(
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val floatOffset by if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "math_symbols_float")
        transition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "symbol_offset_y"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val successColor = NumeraSuccess
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = surfaceVariantColor.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Subtle decorative concentric rings in background
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(
                    color = primaryColor.copy(alpha = 0.07f),
                    radius = size.minDimension * 0.46f,
                    center = center
                )
                drawCircle(
                    color = secondaryColor.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.30f,
                    center = center
                )
            }

            // 2x2 grid of themed math operator cards (+, −, ×, ÷)
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MathSymbolBadge(
                        symbol = "+",
                        color = primaryColor,
                        offsetY = floatOffset.dp
                    )
                    MathSymbolBadge(
                        symbol = "−",
                        color = secondaryColor,
                        offsetY = (-floatOffset).dp
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MathSymbolBadge(
                        symbol = "×",
                        color = tertiaryColor,
                        offsetY = (-floatOffset * 0.7f).dp
                    )
                    MathSymbolBadge(
                        symbol = "÷",
                        color = successColor,
                        offsetY = (floatOffset * 0.7f).dp
                    )
                }
            }
        }
    }
}

@Composable
private fun MathSymbolBadge(
    symbol: String,
    color: Color,
    offsetY: androidx.compose.ui.unit.Dp
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.45f)),
        modifier = Modifier
            .size(width = 76.dp, height = 64.dp)
            .offset(y = offsetY)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = symbol,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 30.sp
                ),
                color = color
            )
        }
    }
}

@Composable
private fun TermsAndPrivacyFooter(
    viewModel: NumeraViewModel,
    onOpenTerms: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("terms_privacy_notice"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val annotated = buildAnnotatedString {
            append(viewModel.t("auth.terms_prefix"))
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(viewModel.t("auth.terms_link"))
            }
            append(viewModel.t("auth.terms_and"))
            withStyle(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                )
            ) {
                append(viewModel.t("auth.privacy_link"))
            }
        }

        Text(
            text = annotated,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenPrivacy() }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onOpenTerms,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("terms_link_btn")
            ) {
                Text(
                    text = viewModel.t("auth.terms_link"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "·",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onOpenPrivacy,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("privacy_link_btn")
            ) {
                Text(
                    text = viewModel.t("auth.privacy_link"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Screen B — "Email" (opens when the user taps Continue with Email, slide-in with a back arrow)
 * - Large rounded top header with curved bottom edge, title "Welcome back" (Log in) or "Create account" (Sign up)
 * - Segmented toggle under the title: Log in | Sign up
 * - Log in tab: Email, Password (eye toggle), "Forgot password?" link, big primary "Log in" button
 * - Sign up tab: Full name, Email, Password (with strength bar), Confirm password, big primary "Create account" button
 * - Inline validation under fields, loading spinner inside button, translated error messages
 * - Bottom text link to switch: "Don't have an account? Sign up" / "Already have an account? Log in"
 * - Back arrow returns to the Welcome screen
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EmailAuthScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    isCreateAccountMode: Boolean,
    onToggleMode: (Boolean) -> Unit,
    onBackToWelcome: () -> Unit,
    onForgotPassword: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var fullNameInput by rememberSaveable {
        mutableStateOf(
            uiState.userProfile.displayName.takeIf { it.isNotBlank() && it != "Guest" } ?: ""
        )
    }
    var emailInput by rememberSaveable { mutableStateOf(uiState.userProfile.email ?: "") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var confirmPasswordInput by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    // Inline validation error states
    var nameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmPasswordError by remember { mutableStateOf<String?>(null) }

    val passwordStrength = remember(passwordInput) {
        PasswordStrength.evaluate(passwordInput)
    }

    fun clearInlineErrors() {
        nameError = null
        emailError = null
        passwordError = null
        confirmPasswordError = null
    }

    fun validateAndSubmit() {
        clearInlineErrors()
        viewModel.clearAuthError()
        focusManager.clearFocus()

        val trimmedName = fullNameInput.trim()
        val trimmedEmail = emailInput.trim()
        var valid = true

        if (isCreateAccountMode && trimmedName.length < 2) {
            nameError = viewModel.t("auth.err_name_required")
            valid = false
        }

        if (trimmedEmail.isEmpty()) {
            emailError = viewModel.t("auth.err_email_required")
            valid = false
        } else if (!isValidEmailFormat(trimmedEmail)) {
            emailError = viewModel.t("auth.err_invalid_email")
            valid = false
        }

        if (passwordInput.isEmpty()) {
            passwordError = viewModel.t("auth.err_password_required")
            valid = false
        } else if (isCreateAccountMode && passwordInput.length < 8) {
            passwordError = viewModel.t("auth.err_weak_password")
            valid = false
        }

        if (isCreateAccountMode && confirmPasswordInput != passwordInput) {
            confirmPasswordError = viewModel.t("auth.err_password_mismatch")
            valid = false
        }

        if (!valid) return

        if (isCreateAccountMode) {
            viewModel.signUpWithEmail(
                displayName = trimmedName,
                email = trimmedEmail,
                password = passwordInput
            )
        } else {
            viewModel.signInWithEmail(
                email = trimmedEmail,
                password = passwordInput
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .imePadding()
            .testTag("email_auth_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large rounded top header with curved bottom edge
        CurvedEmailHeader(
            title = if (isCreateAccountMode) {
                viewModel.t("auth.email_signup_title")
            } else {
                viewModel.t("auth.email_login_title")
            },
            subtitle = if (isCreateAccountMode) {
                viewModel.t("auth.email_signup_sub")
            } else {
                viewModel.t("auth.email_login_sub")
            },
            isCreateAccountMode = isCreateAccountMode,
            logInLabel = viewModel.t("auth.log_in"),
            signUpLabel = viewModel.t("auth.sign_up"),
            backContentDescription = viewModel.t("auth.back_to_welcome"),
            onBackClick = onBackToWelcome,
            onSelectMode = { createMode ->
                clearInlineErrors()
                onToggleMode(createMode)
            }
        )

        // Form Card container (20dp radius, generous spacing, max 480dp width for tablets)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Translated Auth Error Banner (wrong password, user not found, email in use, offline, etc.)
            if (uiState.authError != null) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = NumeraDanger.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, NumeraDanger.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_error_banner")
                ) {
                    Text(
                        text = uiState.authError,
                        color = NumeraDanger,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            ElevatedCard(
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_auth_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Full name field (Sign up tab)
                    if (isCreateAccountMode) {
                        OutlinedTextField(
                            value = fullNameInput,
                            onValueChange = {
                                if (it.length <= 40) {
                                    fullNameInput = it
                                    nameError = null
                                }
                            },
                            label = { Text(viewModel.t("auth.full_name_label")) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null)
                            },
                            isError = nameError != null,
                            supportingText = nameError?.let { err ->
                                {
                                    Text(
                                        text = err,
                                        color = NumeraDanger,
                                        modifier = Modifier.testTag("name_field_error")
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .semantics {
                                    contentDescription = viewModel.t("auth.full_name_label")
                                }
                                .testTag("email_display_name_input")
                        )
                    }

                    // Email field (both tabs)
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            emailError = null
                        },
                        label = { Text(viewModel.t("auth.email_label")) },
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null)
                        },
                        isError = emailError != null,
                        supportingText = emailError?.let { err ->
                            {
                                Text(
                                    text = err,
                                    color = NumeraDanger,
                                    modifier = Modifier.testTag("email_field_error")
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .semantics {
                                contentDescription = viewModel.t("auth.email_label")
                            }
                            .testTag("email_address_input")
                    )

                    // Password field with show/hide eye toggle
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = {
                            passwordInput = it
                            passwordError = null
                        },
                        label = { Text(viewModel.t("auth.password_label")) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("password_visibility_toggle")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (passwordVisible) {
                                        viewModel.t("auth.hide_password")
                                    } else {
                                        viewModel.t("auth.show_password")
                                    }
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        isError = passwordError != null,
                        supportingText = passwordError?.let { err ->
                            {
                                Text(
                                    text = err,
                                    color = NumeraDanger,
                                    modifier = Modifier.testTag("password_field_error")
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (isCreateAccountMode) ImeAction.Next else ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!isCreateAccountMode) {
                                    validateAndSubmit()
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .semantics {
                                contentDescription = viewModel.t("auth.password_label")
                            }
                            .testTag("email_password_input")
                    )

                    // Password strength bar (shown on Sign up tab)
                    if (isCreateAccountMode) {
                        PasswordStrengthMeter(
                            passwordInput = passwordInput,
                            passwordStrength = passwordStrength,
                            viewModel = viewModel
                        )
                    }

                    // Confirm password field (shown on Sign up tab)
                    if (isCreateAccountMode) {
                        OutlinedTextField(
                            value = confirmPasswordInput,
                            onValueChange = {
                                confirmPasswordInput = it
                                confirmPasswordError = null
                            },
                            label = { Text(viewModel.t("auth.confirm_password_label")) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = { confirmPasswordVisible = !confirmPasswordVisible },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("confirm_password_visibility_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (confirmPasswordVisible) {
                                            Icons.Default.VisibilityOff
                                        } else {
                                            Icons.Default.Visibility
                                        },
                                        contentDescription = if (confirmPasswordVisible) {
                                            viewModel.t("auth.hide_password")
                                        } else {
                                            viewModel.t("auth.show_password")
                                        }
                                    )
                                }
                            },
                            visualTransformation = if (confirmPasswordVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                            isError = confirmPasswordError != null,
                            supportingText = confirmPasswordError?.let { err ->
                                {
                                    Text(
                                        text = err,
                                        color = NumeraDanger,
                                        modifier = Modifier.testTag("confirm_password_field_error")
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { validateAndSubmit() }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .semantics {
                                    contentDescription = viewModel.t("auth.confirm_password_label")
                                }
                                .testTag("email_confirm_password_input")
                        )
                    }

                    // "Forgot password?" link (Log in tab)
                    if (!isCreateAccountMode) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = { onForgotPassword(emailInput) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("forgot_password_btn")
                            ) {
                                Text(
                                    text = viewModel.t("auth.forgot_password"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Big Primary Action Button ("Log in" or "Create account") with loading spinner
                    Button(
                        onClick = { validateAndSubmit() },
                        enabled = !uiState.authLoading,
                        shape = RoundedCornerShape(28.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("email_submit_btn")
                    ) {
                        if (uiState.authLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(22.dp)
                                    .testTag("email_submit_spinner"),
                                strokeWidth = 2.5.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Text(
                            text = if (isCreateAccountMode) {
                                viewModel.t("auth.create_account")
                            } else {
                                viewModel.t("auth.log_in")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Bottom text link to switch: "Don't have an account? Sign up" / "Already have an account? Log in"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        clearInlineErrors()
                        onToggleMode(!isCreateAccountMode)
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("email_bottom_switch_link")
                ) {
                    Text(
                        text = if (isCreateAccountMode) {
                            viewModel.t("auth.switch_to_login")
                        } else {
                            viewModel.t("auth.switch_to_signup")
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Large rounded top header with curved bottom edge, back arrow, title, and segmented Log in | Sign up toggle.
 */
@Composable
private fun CurvedEmailHeader(
    title: String,
    subtitle: String,
    isCreateAccountMode: Boolean,
    logInLabel: String,
    signUpLabel: String,
    backContentDescription: String,
    onBackClick: () -> Unit,
    onSelectMode: (Boolean) -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.18f),
                        secondaryColor.copy(alpha = 0.10f),
                        surfaceVariantColor.copy(alpha = 0.65f)
                    )
                )
            )
    ) {
        // Subtle decorative curved wave on the bottom edge of header
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            val wavePath = Path().apply {
                moveTo(0f, size.height * 0.78f)
                quadraticTo(
                    size.width * 0.5f,
                    size.height * 1.05f,
                    size.width,
                    size.height * 0.82f
                )
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = wavePath,
                color = primaryColor.copy(alpha = 0.06f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .align(Alignment.Center)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Back arrow row (48dp touch target)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .testTag("email_screen_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backContentDescription,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Header Title ("Welcome back" or "Create account") & Subtitle
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("email_header_title")
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Segmented toggle under the title: Log in | Sign up
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val tabs = listOf(
                    false to logInLabel,
                    true to signUpLabel
                )
                for ((isCreate, tabTitle) in tabs) {
                    val selected = (isCreateAccountMode == isCreate)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .clickable { onSelectMode(isCreate) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag(if (isCreate) "tab_create_account" else "tab_log_in"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 4-segment password strength indicator bar + translated hint.
 */
@Composable
private fun PasswordStrengthMeter(
    passwordInput: String,
    passwordStrength: PasswordStrength,
    viewModel: NumeraViewModel
) {
    val (activeBars, strengthColor, strengthText) = when {
        passwordInput.isEmpty() -> Triple(
            0,
            MaterialTheme.colorScheme.onSurfaceVariant,
            viewModel.t("auth.password_hint")
        )
        passwordStrength == PasswordStrength.TOO_SHORT -> Triple(
            1,
            NumeraDanger,
            viewModel.t("auth.password_hint")
        )
        passwordStrength == PasswordStrength.WEAK -> Triple(
            2,
            NumeraWarning,
            viewModel.t("auth.strength_weak")
        )
        passwordStrength == PasswordStrength.FAIR -> Triple(
            3,
            Color(0xFF4CB5E3),
            viewModel.t("auth.strength_fair")
        )
        else -> Triple(
            4,
            NumeraSuccess,
            viewModel.t("auth.strength_strong")
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("password_strength_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (index in 1..4) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (index <= activeBars) {
                                strengthColor
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                )
            }
        }
        Text(
            text = strengthText,
            style = MaterialTheme.typography.bodySmall,
            color = strengthColor,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun isValidEmailFormat(email: String): Boolean {
    val trimmed = email.trim()
    val atIndex = trimmed.indexOf('@')
    if (atIndex <= 0 || atIndex == trimmed.lastIndex) return false
    val domain = trimmed.substring(atIndex + 1)
    return domain.contains('.') && !domain.startsWith('.') && !domain.endsWith('.')
}

/**
 * Official 4-color Google "G" vector badge rendered on Canvas.
 */
@Composable
private fun GoogleGLogoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White)
            .padding(3.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.22f
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)

            // Red top arc
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = -145f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Yellow left arc
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 145f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Green bottom arc
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 45f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Blue right arc + crossbar
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = -15f,
                sweepAngle = 60f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            drawLine(
                color = Color(0xFF4285F4),
                start = Offset(size.width * 0.5f, size.height * 0.5f),
                end = Offset(size.width - inset * 0.4f, size.height * 0.5f),
                strokeWidth = strokeWidth
            )
        }
    }
}

/**
 * 2. Verify Your Email Screen:
 * Shown after Email/Password sign-up (or when an unverified email user signs in).
 * Uses Firebase's standard verification LINK email (sendEmailVerification).
 * Includes "Resend" (with 60s cooldown), "I've verified" (reloads user), and "Change email" buttons,
 * plus the Spam/Promotions hint.
 */
@Composable
fun VerifyEmailScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val emailAddress = uiState.userProfile.email ?: ""
    var resendCooldownSec by rememberSaveable { mutableIntStateOf(60) }

    LaunchedEffect(resendCooldownSec) {
        if (resendCooldownSec > 0) {
            delay(1000L)
            resendCooldownSec -= 1
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("verify_email_screen"),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(NumeraPrimary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkEmailRead,
                        contentDescription = null,
                        tint = NumeraPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Text(
                    text = viewModel.t("auth.verify_email_title"),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                if (emailAddress.isNotBlank()) {
                    Text(
                        text = emailAddress,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }

                Text(
                    text = viewModel.t("auth.verify_email_sub", mapOf("email" to emailAddress)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("verify_email_explanation")
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("verify_spam_hint_box")
                ) {
                    Text(
                        text = viewModel.t("auth.verify_spam_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                if (uiState.authError != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NumeraDanger.copy(alpha = 0.14f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = uiState.authError,
                            color = NumeraDanger,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = { viewModel.confirmEmailVerified() },
                    enabled = !uiState.authLoading,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("ive_verified_btn")
                ) {
                    if (uiState.authLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = viewModel.t("auth.ive_verified"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = {
                        resendCooldownSec = 60
                        viewModel.resendVerificationEmail()
                    },
                    enabled = !uiState.authLoading && resendCooldownSec == 0,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("resend_verification_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (resendCooldownSec > 0) {
                            viewModel.t("auth.resend_cooldown", mapOf("sec" to resendCooldownSec.toString()))
                        } else {
                            viewModel.t("auth.resend_verification")
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = { viewModel.changeEmailFromVerification() },
                    enabled = !uiState.authLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("verify_change_email_btn")
                ) {
                    Text(
                        text = viewModel.t("auth.change_email"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        TextButton(
            onClick = { viewModel.confirmLogout(context) },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("verify_back_to_login_btn")
        ) {
            Text(viewModel.t("auth.log_out"))
        }
    }
}

/**
 * 3. First-Time / Edit Profile Setup Screen:
 * - Display name (prefilled from Google / Email, 2–30 chars).
 * - Avatar picker (8 preset flat-vector geometric/animal avatars or initials).
 * - Daily goal: 10 / 20 / 30 / 50 questions per day (default 20).
 * - Language (pre-selected from current language).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (uiState.userProfile.displayName.isNotBlank()) {
            viewModel.navigateToHome()
        }
    }

    var nameInput by remember(uiState.userProfile.displayName) {
        mutableStateOf(uiState.userProfile.displayName.ifBlank { "Math Sprinter" })
    }
    var selectedAvatarId by remember(uiState.userProfile.avatarId) {
        mutableStateOf(uiState.userProfile.avatarId.ifBlank { "fox" })
    }
    var selectedDailyGoal by remember(uiState.userProfile.dailyGoal) {
        mutableIntStateOf(uiState.userProfile.dailyGoal.takeIf { it in listOf(10, 20, 30, 50) } ?: 20)
    }
    var selectedLang by remember(uiState.settings.languageCode) {
        mutableStateOf(uiState.settings.languageCode)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_setup_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text(
                text = viewModel.t("auth.setup_title"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Avatar Preview & Display Name
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    PresetAvatarBadge(
                        avatarId = selectedAvatarId,
                        displayName = nameInput,
                        size = 76.dp,
                        isSelected = true
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { if (it.length <= 30) nameInput = it },
                        label = { Text(viewModel.t("auth.display_name")) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .testTag("profile_display_name_input")
                    )

                    if (uiState.authError != null) {
                        Text(
                            text = uiState.authError,
                            color = NumeraDanger,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Preset Avatar Picker (8 presets)
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = viewModel.t("auth.choose_avatar"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (preset in PresetAvatar.entries) {
                            val selected = (selectedAvatarId == preset.id)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedAvatarId = preset.id }
                                    .padding(6.dp)
                                    .testTag("avatar_option_${preset.id}")
                            ) {
                                PresetAvatarBadge(
                                    avatarId = preset.id,
                                    displayName = "",
                                    size = 52.dp,
                                    isSelected = selected
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = preset.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Daily Goal (10 / 20 / 30 / 50 questions per day)
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = viewModel.t("auth.daily_goal"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(10, 20, 30, 50).forEach { goal ->
                            FilterChip(
                                selected = (selectedDailyGoal == goal),
                                onClick = { selectedDailyGoal = goal },
                                label = { Text("$goal / day") },
                                modifier = Modifier.testTag("daily_goal_chip_$goal")
                            )
                        }
                    }
                }
            }
        }

        // Language Selector
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = viewModel.t("settings.language"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(I18nService.SUPPORTED_LANGUAGES) { lang ->
                            FilterChip(
                                selected = (selectedLang == lang.code),
                                onClick = { selectedLang = lang.code },
                                label = { Text("${lang.nativeName} (${lang.code})") }
                            )
                        }
                    }
                }
            }
        }

        // Save Profile Button
        item {
            Button(
                onClick = {
                    viewModel.completeProfileSetup(
                        displayName = nameInput,
                        avatarId = selectedAvatarId,
                        dailyGoal = selectedDailyGoal,
                        langCode = selectedLang
                    )
                },
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NumeraTertiary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("save_profile_setup_btn")
            ) {
                Text(
                    text = viewModel.t("auth.save_profile"),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
