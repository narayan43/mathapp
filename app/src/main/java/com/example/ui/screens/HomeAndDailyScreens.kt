package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.QuizMode
import com.example.ui.BottomTab
import com.example.ui.NumeraUiState
import com.example.ui.NumeraViewModel
import com.example.ui.components.ModeCategoryIcon
import com.example.ui.components.PresetAvatarBadge
import com.example.ui.theme.NumeraDanger
import com.example.ui.theme.NumeraPrimary
import com.example.ui.theme.NumeraSuccess
import com.example.ui.theme.NumeraTertiary
import com.example.ui.theme.NumeraWarning

@Composable
fun HomeScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    val basicsModes = remember {
        listOf(
            QuizMode.ADDITION,
            QuizMode.SUBTRACTION,
            QuizMode.MULTIPLICATION,
            QuizMode.DIVISION
        )
    }
    val combinedModes = remember {
        listOf(
            QuizMode.MIXED,
            QuizMode.ADD_SUB,
            QuizMode.MULT_DIV
        )
    }
    val advancedModes = remember {
        listOf(
            QuizMode.SQUARE_ROOT,
            QuizMode.EXPONENTS,
            QuizMode.CUBE_ROOT,
            QuizMode.MULTIPLICATION_TABLES,
            QuizMode.CUSTOMIZABLE
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = viewModel.t("app.name"),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = viewModel.t("app.tagline"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SurfaceStreakBadge(
                        streakCount = uiState.dashboardSnapshot.practiceStreakCurrent,
                        onClick = { viewModel.selectBottomTab(BottomTab.PROGRESS) }
                    )

                    if (uiState.userProfile.isGuest) {
                        FilledTonalButton(
                            onClick = { viewModel.openAuthHub(isSignUp = true) },
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("header_sign_in_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Sign In",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Guest (${uiState.guestDaysRemaining}d)", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        PresetAvatarBadge(
                            avatarId = uiState.userProfile.avatarId,
                            displayName = uiState.userProfile.displayName,
                            size = 38.dp,
                            modifier = Modifier
                                .clickable { viewModel.selectBottomTab(BottomTab.SETTINGS) }
                                .testTag("header_avatar_badge")
                        )
                    }
                }
            }
        }

        if (uiState.userProfile.isGuest) {
            item {
                Card(
                    onClick = { viewModel.openAuthHub(isSignUp = true) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("guest_trial_banner_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Guest Mode (${uiState.guestDaysRemaining} days remaining)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = viewModel.t("auth.guest_card_sub"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = { viewModel.openAuthHub(isSignUp = true) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 36.dp)
                        ) {
                            Text(viewModel.t("auth.backup_btn"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // 1. "Practice from my data" Card (50% past mistakes + 50% adaptive weak/new areas)
        item {
            SmartPracticeBannerCard(
                uiState = uiState,
                viewModel = viewModel,
                testTag = "home_smart_practice_card"
            )
        }

        // Practice Mistakes Card if user has saved mistakes (100% free & instant)
        if (uiState.mistakes.isNotEmpty()) {
            item {
                Card(
                    onClick = { viewModel.startPracticeMistakes() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = NumeraDanger.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .testTag("practice_mistakes_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            ModeCategoryIcon(
                                mode = QuizMode.PRACTICE_MISTAKES,
                                tint = NumeraDanger,
                                modifier = Modifier.size(56.dp)
                            )
                            Column {
                                Text(
                                    text = "${viewModel.t("mode.mistakes")} (${uiState.mistakes.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = viewModel.t("mode.mistakes_sub"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Start Mistakes Replay",
                            tint = NumeraDanger
                        )
                    }
                }
            }
        }

        // 2. Basics Section
        item {
            SectionTitle(title = viewModel.t("sec.basics"))
            Spacer(modifier = Modifier.height(10.dp))
            ModeGridTwoColumn(
                modes = basicsModes,
                accentColor = NumeraPrimary,
                viewModel = viewModel
            )
        }

        // 3. Combined Section
        item {
            SectionTitle(title = viewModel.t("sec.combined"))
            Spacer(modifier = Modifier.height(10.dp))
            ModeGridTwoColumn(
                modes = combinedModes,
                accentColor = NumeraSuccess,
                viewModel = viewModel
            )
        }

        // 4. Advanced Section
        item {
            SectionTitle(title = viewModel.t("sec.advanced"))
            Spacer(modifier = Modifier.height(10.dp))
            ModeGridTwoColumn(
                modes = advancedModes,
                accentColor = NumeraTertiary,
                viewModel = viewModel
            )
        }

        // 5. Game Modes Section (Arithmetic Memory)
        item {
            SectionTitle(title = viewModel.t("sec.games"))
            Spacer(modifier = Modifier.height(10.dp))
            ElevatedCard(
                onClick = { viewModel.openModeSetup(QuizMode.ARITHMETIC_MEMORY) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("card_mode_ARITHMETIC_MEMORY")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ModeCategoryIcon(
                        mode = QuizMode.ARITHMETIC_MEMORY,
                        tint = NumeraWarning,
                        modifier = Modifier.size(56.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = viewModel.t(QuizMode.ARITHMETIC_MEMORY.titleKey),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                            fontWeight = FontWeight.Bold,
                            maxLines = 2
                        )
                        Text(
                            text = viewModel.t(QuizMode.ARITHMETIC_MEMORY.subtitleKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Memory",
                        tint = NumeraWarning
                    )
                }
            }
        }

        // 6. Footer Section: Progress & Settings Quick Links
        item {
            SectionTitle(title = viewModel.t("sec.more"))
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.selectBottomTab(BottomTab.PROGRESS) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(viewModel.t("nav.progress"))
                }
                OutlinedButton(
                    onClick = { viewModel.selectBottomTab(BottomTab.SETTINGS) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(viewModel.t("nav.settings"))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SmartPracticeBannerCard(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    testTag: String = "home_smart_practice_card"
) {
    val dueMistakesCount = uiState.mistakeBook.size
    val weakestCat = uiState.dashboardSnapshot.categoryStats
        .filter { it.attempts >= 5 }
        .maxByOrNull { it.weaknessScore }

    ElevatedCard(
        onClick = { viewModel.openSmartPracticeSetup(null) },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NumeraPrimary.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NumeraPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = viewModel.t("smart.btn_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2
                    )
                    Text(
                        text = viewModel.t("smart.btn_sub"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        maxLines = 2
                    )
                    if (dueMistakesCount > 0 || weakestCat != null) {
                        val badgeStr = buildString {
                            if (dueMistakesCount > 0) append("$dueMistakesCount due mistakes")
                            if (weakestCat != null) {
                                if (isNotEmpty()) append(" · ")
                                append("Focus: ${viewModel.categoryLabel(weakestCat.categoryId)}")
                            }
                        }
                        Text(
                            text = badgeStr,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NumeraPrimary
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = viewModel.t("smart.btn_title"),
                tint = NumeraPrimary
            )
        }
    }
}

@Composable
private fun SurfaceStreakBadge(streakCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(NumeraWarning.copy(alpha = 0.18f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("home_streak_badge"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.LocalFireDepartment,
            contentDescription = "Streak",
            tint = NumeraWarning,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "$streakCount",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun ModeGridTwoColumn(
    modes: List<QuizMode>,
    accentColor: Color,
    viewModel: NumeraViewModel
) {
    val rows = modes.chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (rowModes in rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (mode in rowModes) {
                    ElevatedCard(
                        onClick = { viewModel.openModeSetup(mode) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 148.dp)
                            .testTag("card_mode_${mode.name}")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                ModeCategoryIcon(
                                    mode = mode,
                                    tint = accentColor,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = viewModel.t(mode.titleKey),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 15.sp,
                                        lineHeight = 19.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Clip
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = viewModel.t(mode.subtitleKey),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                minLines = 2,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (rowModes.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
