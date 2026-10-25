package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.core.I18nService
import com.example.model.AppThemeId
import com.example.model.AppearanceMode
import com.example.model.KeypadSize
import com.example.model.KeypadType
import com.example.model.QuizMode
import com.example.model.SkillOp
import com.example.model.SyncStatusState
import com.example.ui.NumeraUiState
import com.example.ui.NumeraViewModel
import com.example.ui.components.PresetAvatarBadge
import com.example.ui.theme.NumeraDanger
import com.example.ui.theme.NumeraPrimary
import com.example.ui.theme.NumeraSuccess
import com.example.ui.theme.NumeraWarning
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

// =========================================================================
// 1. PROGRESS / INSIGHTS SCREEN (Delegates to InsightsDashboardScreen)
// =========================================================================

@Composable
fun ProgressScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    InsightsDashboardScreen(
        uiState = uiState,
        viewModel = viewModel,
        modifier = modifier
    )
}

// =========================================================================
// 2. SETTINGS SCREEN (Includes Account / Profile + Data & Privacy Sections)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings = uiState.settings
    val profile = uiState.userProfile
    var showRateDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.showToast("Notification permission granted!")
        } else {
            viewModel.showToast("Notifications disabled in system settings.")
        }
    }

    if (uiState.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLogoutConfirm() },
            title = { Text(viewModel.t("auth.logout_confirm_title")) },
            text = { Text(viewModel.t("auth.logout_confirm_body")) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmLogout(context) },
                    modifier = Modifier.testTag("confirm_logout_btn")
                ) {
                    Text(viewModel.t("auth.log_out"))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissLogoutConfirm() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRateDialog) {
        AlertDialog(
            onDismissRequest = { showRateDialog = false },
            title = { Text("Enjoying Numera?") },
            text = {
                Text("Are you enjoying training your mental math speed with Numera?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRateDialog = false
                        viewModel.showToast("Thank you for rating Numera 5 stars! ⭐")
                    }
                ) {
                    Text("Yes, Love It! ⭐")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRateDialog = false
                        viewModel.showToast("Thanks! Send us your suggestions anytime.")
                    }
                ) {
                    Text("Could Be Better")
                }
            }
        )
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy · 100% Free & Private") },
            text = {
                Text(viewModel.t("auth.privacy_summary"))
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = viewModel.t("nav.settings"),
                style = MaterialTheme.typography.headlineLarge
            )
        }

        // 0. Account / Profile Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (profile.isGuest) {
                    GuestBackupPromptCard(
                        viewModel = viewModel,
                        testTag = "settings_guest_backup_card"
                    )
                }
                AccountProfileSettingsCard(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }
        }

        // 1. Data & Privacy (Answer Recording, Source Chip, Export JSON/CSV, Reset Statistics)
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = viewModel.t("settings.data_section"),
                        style = MaterialTheme.typography.titleLarge
                    )

                    SettingToggleRow(
                        title = viewModel.t("settings.record_answers"),
                        subtitle = viewModel.t("settings.record_answers_sub"),
                        checked = settings.recordAnswersEnabled,
                        onCheckedChange = { viewModel.updateDataSettings(recordAnswersEnabled = it) },
                        testTag = "toggle_record_answers"
                    )

                    SettingToggleRow(
                        title = viewModel.t("settings.show_source_chip"),
                        subtitle = viewModel.t("settings.show_source_chip_sub"),
                        checked = settings.showSourceChipInSmartPractice,
                        onCheckedChange = { viewModel.updateDataSettings(showSourceChipInSmartPractice = it) },
                        testTag = "toggle_show_source_chip"
                    )

                    Text(
                        text = "Daily Question Goal",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (goal in listOf(15, 30, 50, 100)) {
                            FilterChip(
                                selected = (settings.dailyGoalQuestions == goal),
                                onClick = { viewModel.updateDataSettings(dailyGoalQuestions = goal) },
                                label = { Text("$goal / day") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.exportUserData("json") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_json_btn")
                        ) {
                            Text(viewModel.t("settings.export_json"))
                        }
                        OutlinedButton(
                            onClick = { viewModel.exportUserData("csv") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_csv_btn")
                        ) {
                            Text(viewModel.t("settings.export_csv"))
                        }
                    }
                }
            }
        }

        // 2. Game Settings: Auto Confirm, Keypad type, Keypad size, Sounds, Haptics
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(viewModel.t("settings.game"), style = MaterialTheme.typography.titleLarge)

                    SettingToggleRow(
                        title = viewModel.t("settings.auto_confirm"),
                        subtitle = "Auto-submit as soon as digit count matches the answer",
                        checked = settings.autoConfirm,
                        onCheckedChange = { viewModel.updateGameSettings(autoConfirm = it) },
                        testTag = "toggle_auto_confirm"
                    )

                    Text(viewModel.t("settings.keypad_type"), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = (settings.keypadType == KeypadType.PHONE),
                            onClick = { viewModel.updateGameSettings(keypadType = KeypadType.PHONE) },
                            label = { Text("Phone (1-2-3 top)") },
                            modifier = Modifier.testTag("chip_keypad_phone")
                        )
                        FilterChip(
                            selected = (settings.keypadType == KeypadType.CALCULATOR),
                            onClick = { viewModel.updateGameSettings(keypadType = KeypadType.CALCULATOR) },
                            label = { Text("Calculator (7-8-9 top)") },
                            modifier = Modifier.testTag("chip_keypad_calc")
                        )
                    }

                    Text(viewModel.t("settings.keypad_size"), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = (settings.keypadSize == KeypadSize.NORMAL),
                            onClick = { viewModel.updateGameSettings(keypadSize = KeypadSize.NORMAL) },
                            label = { Text("Normal") }
                        )
                        FilterChip(
                            selected = (settings.keypadSize == KeypadSize.LARGE),
                            onClick = { viewModel.updateGameSettings(keypadSize = KeypadSize.LARGE) },
                            label = { Text("Large") }
                        )
                    }

                    SettingToggleRow(
                        title = viewModel.t("settings.sounds"),
                        subtitle = "Success chime and error audio feedback",
                        checked = settings.soundsEnabled,
                        onCheckedChange = { viewModel.updateGameSettings(soundsEnabled = it) },
                        testTag = "toggle_sounds"
                    )

                    SettingToggleRow(
                        title = viewModel.t("settings.haptics"),
                        subtitle = "Vibrate on wrong answer & subtle keypad tap",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { viewModel.updateGameSettings(hapticsEnabled = it) },
                        testTag = "toggle_haptics"
                    )
                }
            }
        }

        // 3. Appearance: Theme (Light / Dark / System), Accent colour (10 Themes), & Language (17 languages)
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(viewModel.t("settings.appearance"), style = MaterialTheme.typography.titleLarge)

                    Text(
                        text = viewModel.t("settings.theme"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (mode in AppearanceMode.entries) {
                            FilterChip(
                                selected = (settings.appearanceMode == mode),
                                onClick = { viewModel.selectAppearanceMode(mode) },
                                label = { Text(viewModel.t(mode.labelKey)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("appearance_mode_${mode.name}")
                            )
                        }
                    }

                    Text(
                        text = "${viewModel.t("settings.language")} (17 Languages)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(I18nService.SUPPORTED_LANGUAGES) { lang ->
                            FilterChip(
                                selected = (settings.languageCode == lang.code),
                                onClick = { viewModel.selectLanguage(lang.code) },
                                label = { Text("${lang.nativeName} (${lang.code})") },
                                modifier = Modifier.testTag("lang_chip_${lang.code}")
                            )
                        }
                    }

                    Text(
                        text = viewModel.t("settings.accent_color"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (theme in AppThemeId.entries) {
                            FilterChip(
                                selected = (settings.themeId == theme),
                                onClick = { viewModel.selectTheme(theme) },
                                label = { Text(viewModel.t(theme.labelKey)) },
                                modifier = Modifier.testTag("theme_chip_${theme.name}")
                            )
                        }
                    }
                }
            }
        }

        // 4. Notifications: Practice reminder (time + days), Mistake review, Weekly summary
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(viewModel.t("settings.notifications"), style = MaterialTheme.typography.titleLarge)

                    SettingToggleRow(
                        title = viewModel.t("settings.practice_reminder"),
                        subtitle = viewModel.t(
                            "settings.practice_reminder_sub",
                            mapOf("time" to String.format("%02d:00", settings.reminderHour))
                        ),
                        checked = settings.reminderEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!hasPerm) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                            viewModel.updatePracticeReminder(reminderEnabled = enabled)
                        },
                        testTag = "toggle_practice_reminder"
                    )

                    Text(
                        text = viewModel.t("settings.practice_reminder_time"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (hr in listOf(8, 13, 18, 19, 20, 21)) {
                            FilterChip(
                                selected = (settings.reminderHour == hr),
                                onClick = { viewModel.updatePracticeReminder(reminderHour = hr, reminderMinute = 0) },
                                label = { Text(String.format("%02d:00", hr)) },
                                modifier = Modifier.testTag("reminder_hour_$hr")
                            )
                        }
                    }

                    Text(
                        text = viewModel.t("settings.practice_reminder_days"),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val dayLabels = listOf(
                        1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu",
                        5 to "Fri", 6 to "Sat", 7 to "Sun"
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((dayNum, name) in dayLabels) {
                            FilterChip(
                                selected = dayNum in settings.reminderDays,
                                onClick = { viewModel.toggleReminderDay(dayNum) },
                                label = { Text(name) },
                                modifier = Modifier.testTag("reminder_day_$dayNum")
                            )
                        }
                    }

                    HorizontalDivider()

                    SettingToggleRow(
                        title = "Spaced-Repetition Mistake Reminder",
                        subtitle = "Notify when due mistakes are ready for review",
                        checked = settings.mistakeReminderEnabled,
                        onCheckedChange = { viewModel.updateDataSettings(mistakeReminderEnabled = it) },
                        testTag = "toggle_mistake_reminder"
                    )

                    SettingToggleRow(
                        title = "Weekly Insights Summary (Sun 18:00)",
                        subtitle = "Weekly question count, accuracy, and speed improvement",
                        checked = settings.weeklySummaryReminderEnabled,
                        onCheckedChange = { viewModel.updateDataSettings(weeklySummaryReminderEnabled = it) },
                        testTag = "toggle_weekly_summary"
                    )
                }
            }
        }

        // 5. Support & Feedback
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(viewModel.t("settings.support"), style = MaterialTheme.typography.titleLarge)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showRateDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rate App")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.shareText("Feedback for Numera Android v1.0:")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Feedback")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPrivacyDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Privacy")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.shareText("Train your mental math speed with Numera! 💡🔥")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Share App")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuestBackupPromptCard(
    viewModel: NumeraViewModel,
    testTag: String
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                    Text(
                        text = viewModel.t("auth.guest_banner"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = viewModel.t("auth.guest_card_sub"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = { viewModel.openAuthHub(isSignUp = true) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${testTag}_btn")
            ) {
                Text(viewModel.t("auth.backup_btn"), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AccountProfileSettingsCard(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel
) {
    val profile = uiState.userProfile
    val syncTimeFormatted = remember(profile.lastSyncedTimestamp) {
        val ts = profile.lastSyncedTimestamp
        if (ts == null || ts <= 0L) {
            null
        } else {
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts))
        }
    }
    val methodBadge = when {
        profile.isGuest || profile.provider == "guest" -> viewModel.t("auth.method_guest")
        profile.provider == "email" -> viewModel.t("auth.method_email")
        else -> viewModel.t("auth.method_google")
    }
    val providerDetail = when {
        profile.isGuest -> "${viewModel.t("auth.sign_in_method")}: $methodBadge"
        !profile.email.isNullOrBlank() -> "$methodBadge · ${profile.email}"
        else -> "${viewModel.t("auth.sign_in_method")}: $methodBadge"
    }
    val (statusLabel, statusBg, statusFg) = when (uiState.syncStatus) {
        SyncStatusState.SYNCED -> Triple(
            viewModel.t("auth.sync_status_synced"),
            NumeraSuccess.copy(alpha = 0.16f),
            NumeraSuccess
        )
        SyncStatusState.PENDING -> Triple(
            viewModel.t("auth.sync_status_pending"),
            NumeraWarning.copy(alpha = 0.18f),
            NumeraWarning
        )
        SyncStatusState.OFFLINE -> Triple(
            viewModel.t("auth.sync_status_offline"),
            NumeraDanger.copy(alpha = 0.16f),
            NumeraDanger
        )
    }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_profile_settings_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    PresetAvatarBadge(
                        avatarId = profile.avatarId,
                        displayName = profile.displayName.ifBlank { if (profile.isGuest) "Guest" else "Numera Player" },
                        size = 54.dp,
                        isSelected = true
                    )
                    Column {
                        Text(
                            text = profile.displayName.ifBlank { if (profile.isGuest) "Guest" else "Numera Player" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = providerDetail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Daily Goal: ${profile.dailyGoal} questions/day",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = { viewModel.openEditProfile() },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("edit_profile_btn")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
            }

            // Sync status chip, Last synced, Pending items count & Push my data now button
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = statusBg,
                            modifier = Modifier.testTag("sync_status_chip")
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusFg,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            text = viewModel.t(
                                "auth.pending_items",
                                mapOf("count" to uiState.pendingSyncItemsCount.toString())
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("sync_pending_items_text")
                        )
                    }
                }

                Text(
                    text = viewModel.t(
                        "auth.last_synced",
                        mapOf("time" to (syncTimeFormatted ?: viewModel.t("auth.never_synced")))
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("sync_last_synced_text")
                )

                if (!uiState.lastSyncErrorMessage.isNullOrBlank()) {
                    Text(
                        text = uiState.lastSyncErrorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = NumeraDanger,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("sync_error_text")
                    )
                }

                Button(
                    onClick = { viewModel.pushMyDataNow() },
                    enabled = !uiState.isSyncing,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sync_now_btn")
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.isSyncing) "Syncing..." else viewModel.t("auth.push_data_now"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Log out action (sole session exit button)
            OutlinedButton(
                onClick = { viewModel.requestLogout() },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("logout_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(viewModel.t("auth.log_out"), fontWeight = FontWeight.Bold)
            }

            HorizontalDivider()

            // Request account deletion row (opens email composer to <MY_SUPPORT_EMAIL> with uid)
            val activityContext = LocalContext.current
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.requestAccountDeletionEmail(activityContext) }
                    .testTag("request_account_deletion_row")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = viewModel.t("auth.request_deletion"),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = viewModel.t("auth.request_deletion_note"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = viewModel.t("auth.request_deletion"),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
