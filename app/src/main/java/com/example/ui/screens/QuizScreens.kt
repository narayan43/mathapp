package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.ui.theme.NumeraPrimary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.QuestionGenerator
import com.example.model.Difficulty
import com.example.model.Problem
import com.example.model.QuizMode
import com.example.ui.FlashFeedback
import com.example.ui.NumeraUiState
import com.example.ui.NumeraViewModel
import com.example.ui.components.CustomMathKeypad
import com.example.ui.components.ModeCategoryIcon
import com.example.ui.theme.NumeraDanger
import com.example.ui.theme.NumeraSuccess
import com.example.ui.theme.NumeraWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreGameSetupScreen(
    mode: QuizMode,
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateToHome() }

    val questionOptions = listOf(10, 20, 30, 40, 50, 60)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("pre_game_setup_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigateToHome() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(6.dp))
                ModeCategoryIcon(mode = mode)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = viewModel.t(mode.titleKey),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = viewModel.t(mode.subtitleKey),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Question count selection
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = viewModel.t("setup.questions"),
                        style = MaterialTheme.typography.titleLarge
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (count in questionOptions) {
                            FilterChip(
                                selected = (uiState.setupQuestionCount == count),
                                onClick = { viewModel.updateSetupQuestionCount(count) },
                                label = {
                                    Text(
                                        text = if (count >= 40) "$count (More)" else "$count",
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                modifier = Modifier.testTag("chip_count_$count")
                            )
                        }
                    }
                }
            }

            // 5-Level Difficulty Selection
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = viewModel.t("setup.difficulty"),
                        style = MaterialTheme.typography.titleLarge
                    )
                    for (diff in Difficulty.entries) {
                        val isSelected = (uiState.setupDifficulty == diff)
                        val rangeHint = sampleRangeDescription(mode, diff)
                        Card(
                            onClick = { viewModel.updateSetupDifficulty(diff) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chip_diff_${diff.name}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = viewModel.t(diff.labelKey),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = rangeHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.startCoreQuiz(mode) },
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_quiz_btn")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = viewModel.t("setup.start"),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun sampleRangeDescription(mode: QuizMode, diff: Difficulty): String {
    return when (mode) {
        QuizMode.ADDITION -> {
            val (a, b) = QuestionGenerator.additionRange(diff)
            "$a – $b"
        }
        QuizMode.SUBTRACTION -> {
            val (a, b) = QuestionGenerator.subtractionRange(diff)
            "$a – $b"
        }
        QuizMode.MULTIPLICATION -> {
            val (a, b) = QuestionGenerator.multiplicationRange(diff)
            "$a × $b"
        }
        QuizMode.DIVISION -> {
            val (a, b) = QuestionGenerator.divisionRange(diff)
            "÷ within $a–$b"
        }
        QuizMode.SQUARE_ROOT -> when (diff) {
            Difficulty.EASY -> "√1 – √100"
            Difficulty.MEDIUM -> "√25 – √400"
            Difficulty.HARD -> "√100 – √900"
            Difficulty.CHALLENGING -> "√144 – √1600"
            Difficulty.EXPERT -> "√225 – √2500"
        }
        QuizMode.EXPONENTS -> when (diff) {
            Difficulty.EASY -> "n² (2–10)"
            Difficulty.MEDIUM -> "n² (11–20)"
            Difficulty.HARD -> "n³ (2–10)"
            Difficulty.CHALLENGING -> "n² (21–35)"
            Difficulty.EXPERT -> "n² (21–50) / n⁴"
        }
        else -> "Level ${diff.index + 1}"
    }
}

@Composable
fun ActiveQuizScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    val quiz = uiState.activeQuiz ?: return

    BackHandler { viewModel.requestBackFromQuiz() }

    // 3-2-1 "Get ready" countdown overlay (tap anywhere to skip)
    if (quiz.countdownRemaining > 0) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .clickable { viewModel.skipCountdown() }
                .testTag("countdown_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = viewModel.t("setup.get_ready"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${quiz.countdownRemaining}",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = viewModel.t("setup.tap_skip"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val currentProblem = quiz.currentProblem ?: return
    val totalCount = quiz.problems.size.coerceAtLeast(1)
    val currentNum = (quiz.currentIndex + 1).coerceAtMost(totalCount)

    val flashColor by animateColorAsState(
        targetValue = when (quiz.flashFeedback) {
            FlashFeedback.CORRECT -> NumeraSuccess.copy(alpha = 0.25f)
            FlashFeedback.WRONG -> NumeraDanger.copy(alpha = 0.28f)
            FlashFeedback.NONE -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(160),
        label = "flash_feedback_color"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("active_quiz_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header: Back, Progress "3 / 10", Running Timer (mm:ss), Correct / Wrong Counters
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.requestBackFromQuiz() },
                        modifier = Modifier.testTag("quiz_back_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "$currentNum / $totalCount",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("quiz_progress_text")
                    )
                }

                // Timer mm:ss
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Timer",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = formatTimerMmSs(quiz.elapsedMs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Correct / Wrong Counters
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "✓ ${quiz.correctCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = NumeraSuccess,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "✗ ${quiz.wrongCount}",
                        style = MaterialTheme.typography.titleMedium,
                        color = NumeraDanger,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LinearProgressIndicator(
                progress = { currentNum.toFloat() / totalCount.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }

        // Big Question Display Card
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = flashColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (quiz.isDataPractice && quiz.smartPracticePlan?.usedFallbackForFewMistakes == true && quiz.currentIndex == 0) {
                    Text(
                        text = viewModel.t("smart.fallback_notice"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag("smart_fallback_notice")
                    )
                }

                if (quiz.isDataPractice && uiState.settings.showSourceChipInSmartPractice) {
                    val isMistakeReplay = quiz.currentQuestionSource == "data-practice-mistake"
                    val sourceLabel = if (isMistakeReplay) {
                        viewModel.t("smart.source_mistake")
                    } else {
                        viewModel.t("smart.source_new")
                    }
                    val catTitle = viewModel.categoryLabel(quiz.currentQuestionCategory ?: "add_1d")
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isMistakeReplay) NumeraWarning.copy(alpha = 0.18f)
                                else NumeraPrimary.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .testTag("smart_question_source_chip"),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$sourceLabel · $catTitle",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isMistakeReplay) NumeraWarning else NumeraPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                ProblemExpressionText(
                    problem = currentProblem,
                    typedInput = quiz.typedInput
                )
            }
        }

        // Custom On-Screen Keypad
        CustomMathKeypad(
            keypadType = uiState.settings.keypadType,
            keypadSize = uiState.settings.keypadSize,
            autoConfirm = uiState.settings.autoConfirm,
            enterLabel = viewModel.t("game.enter"),
            clearLabel = viewModel.t("game.clear"),
            onDigit = { viewModel.onKeypadDigit(it) },
            onBackspace = { viewModel.onKeypadBackspace() },
            onClear = { viewModel.onKeypadClear() },
            onEnter = { viewModel.onKeypadEnter() }
        )
    }
}

@Composable
fun ProblemExpressionText(
    problem: Problem,
    typedInput: String
) {
    val answerDisplay = typedInput.ifEmpty { "?" }
    val annotated = buildAnnotatedString {
        append(problem.displayBase)
        if (!problem.superscriptExponent.isNullOrEmpty()) {
            withStyle(
                SpanStyle(
                    baselineShift = BaselineShift.Superscript,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(problem.superscriptExponent)
            }
        }
        append(" = ")
        withStyle(
            SpanStyle(
                color = if (typedInput.isEmpty()) NumeraWarning else NumeraSuccess
            )
        ) {
            append(answerDisplay)
        }
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.displayLarge.copy(
            fontSize = if (problem.displayBase.length > 12) 32.sp else 42.sp
        ),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag("problem_expression_text")
    )
}

@Composable
fun QuizResultScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateToHome() }

    val completed = uiState.lastCompletedQuiz ?: return
    val totalCount = completed.problems.size.coerceAtLeast(1)
    val correctCount = completed.correctCount
    val accuracyPct = (correctCount * 100) / totalCount
    val modeKey = "${completed.mode.name}_${completed.difficulty.name}_$totalCount"

    val matchingScores = uiState.scores.filter { it.modeKey == modeKey }
    val top5Fastest = matchingScores.sortedWith(
        compareByDescending<com.example.model.ScoreRecord> { it.correctCount }
            .thenBy { it.totalTimeMs }
    ).take(5)
    val last5Results = matchingScores.takeLast(5).reversed()

    val tableDeltaText = completed.singleTableNumber?.let { tbl ->
        val stat = uiState.tableStats[tbl]
        val prev = stat?.prevTimeMs
        if (stat != null && prev != null) {
            val delta = stat.lastTimeMs - prev
            if (delta <= 0) {
                "Table of $tbl: ${String.format("%.1fs", -delta / 1000.0)} faster than last time! (Best: ${formatTimerMmSs(stat.bestTimeMs)})"
            } else {
                "Table of $tbl: ${String.format("%.1fs", delta / 1000.0)} slower than last time (Best: ${formatTimerMmSs(stat.bestTimeMs)})"
            }
        } else if (stat != null) {
            "Table of $tbl Best Time: ${formatTimerMmSs(stat.bestTimeMs)}"
        } else null
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("quiz_result_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Summary Hero Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = NumeraWarning,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = viewModel.t("result.title"),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = viewModel.t(
                            "result.score",
                            mapOf("correct" to correctCount.toString(), "total" to totalCount.toString())
                        ),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(viewModel.t("result.time"), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = String.format("%.1fs", completed.elapsedMs / 1000.0),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(viewModel.t("result.accuracy"), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "$accuracyPct%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (tableDeltaText != null) {
                        Text(
                            text = tableDeltaText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = NumeraSuccess
                        )
                    }
                }
            }
        }

        // Smart Practice Before vs After Category Accuracy Comparison
        if (completed.isDataPractice) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_practice_delta_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = viewModel.t("smart.before_after_title"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${viewModel.t("smart.resolved_mistakes")}: ${uiState.lastSmartPracticeResolvedMistakes.size}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NumeraSuccess,
                            fontWeight = FontWeight.Bold
                        )
                        uiState.lastSmartPracticeDeltas.forEach { delta ->
                            val beforePct = ((delta.beforeAccuracy ?: delta.afterAccuracy) * 100).toInt()
                            val afterPct = (delta.afterAccuracy * 100).toInt()
                            val diff = afterPct - beforePct
                            val sign = if (diff >= 0) "+$diff%" else "$diff%"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = viewModel.categoryLabel(delta.categoryId),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "$beforePct% → $afterPct% ($sign)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (diff >= 0) NumeraSuccess else NumeraWarning
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.startSmartPractice() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(viewModel.t("smart.practice_again"))
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.navigateToHome()
                                    viewModel.selectBottomTab(com.example.ui.BottomTab.PROGRESS)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(viewModel.t("smart.view_insights"))
                            }
                        }
                    }
                }
            }
        }

        // Action Buttons: Play again, Share, Home, Practice Mistakes
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        when {
                            completed.isDataPractice -> viewModel.startSmartPractice()
                            completed.mode == QuizMode.MULTIPLICATION_TABLES -> viewModel.startTablesPractice(totalCount)
                            completed.mode == QuizMode.CUSTOMIZABLE -> viewModel.startCustomQuiz(uiState.customConfig)
                            else -> viewModel.startCoreQuiz(completed.mode)
                        }
                    },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .testTag("play_again_btn")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(viewModel.t("result.play_again"))
                }

                OutlinedButton(
                    onClick = {
                        viewModel.shareText(
                            "I scored $correctCount/$totalCount ($accuracyPct%) in ${String.format("%.1fs", completed.elapsedMs / 1000.0)} on Numera!"
                        )
                    },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }

                OutlinedButton(
                    onClick = { viewModel.navigateToHome() },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("result_home_btn")
                ) {
                    Icon(Icons.Default.Home, contentDescription = "Home")
                }
            }

            if (completed.wrongCount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { viewModel.startPracticeMistakes() },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("${viewModel.t("mode.mistakes")} (${completed.wrongCount} missed)")
                }
            }
        }

        // Review List of every question with user answer vs correct answer (wrong ones highlighted)
        item {
            Text(
                text = viewModel.t("result.review"),
                style = MaterialTheme.typography.titleLarge
            )
        }

        itemsIndexed(completed.answers) { idx, ans ->
            val expr = if (ans.problem.superscriptExponent != null) {
                "${ans.problem.displayBase}^${ans.problem.superscriptExponent}"
            } else {
                ans.problem.displayBase
            }
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (ans.isCorrect) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        NumeraDanger.copy(alpha = 0.14f)
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (ans.isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            contentDescription = null,
                            tint = if (ans.isCorrect) NumeraSuccess else NumeraDanger
                        )
                        Column {
                            Text(
                                text = "${idx + 1}. $expr = ${ans.problem.answer}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!ans.isCorrect) {
                                Text(
                                    text = "Your answer: ${ans.userAnswer}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NumeraDanger,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = String.format("%.1fs", ans.timeMs / 1000.0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Score Table: Top 5 High Scores & Last 5 Results
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = viewModel.t("result.top5"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    top5Fastest.forEachIndexed { i, rec ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("#${i + 1} · ${rec.correctCount}/${rec.questionCount} correct")
                            Text(
                                text = String.format("%.1fs", rec.totalTimeMs / 1000.0),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = viewModel.t("result.last5"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    last5Results.forEach { rec ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${rec.correctCount}/${rec.questionCount} (${(rec.accuracy * 100).toInt()}%)")
                            Text(String.format("%.1fs", rec.totalTimeMs / 1000.0))
                        }
                    }
                }
            }
        }
    }
}

fun formatTimerMmSs(elapsedMs: Long): String {
    val totalSec = (elapsedMs / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return String.format("%02d:%02d", mins, secs)
}
