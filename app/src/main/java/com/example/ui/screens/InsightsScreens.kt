package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.AccuracyClass
import com.example.core.AnswerLog
import com.example.core.CategoryRegistry
import com.example.core.CategorySortOrder
import com.example.core.CategoryStat
import com.example.core.DailyVolumePoint
import com.example.core.HeatmapCell
import com.example.core.InsightCardData
import com.example.core.InsightsDateRange
import com.example.core.MistakeBookEntry
import com.example.core.OperationRadarPoint
import com.example.core.QuadrantClass
import com.example.core.SpeedClass
import com.example.core.StatsService
import com.example.ui.NumeraUiState
import com.example.ui.NumeraViewModel
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import com.example.core.DailyEngine
import com.example.model.SkillOp
import com.example.model.SyncStatusState
import com.example.ui.components.WeekStripBar
import com.example.ui.theme.NumeraDanger
import com.example.ui.theme.NumeraPrimary
import com.example.ui.theme.NumeraSuccess
import com.example.ui.theme.NumeraWarning
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// =========================================================================
// 1. MAIN INSIGHTS DASHBOARD SCREEN (Overview · Categories · History)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsightsDashboardScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    val snap = uiState.dashboardSnapshot
    val sortedCategories = remember(snap.categoryStats, uiState.categorySortOrder) {
        StatsService.sortCategories(snap.categoryStats, uiState.categorySortOrder)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("insights_dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Redesigned Header: Title on its own line (+ small sync chip), full-width 52dp pill button below
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = viewModel.t("insights.title"),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    InsightsSyncChip(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }

                Button(
                    onClick = { viewModel.openSmartPracticeSetup(null) },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("insights_smart_practice_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = viewModel.t("smart.btn_title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Top Segmented Sub-Tab Bar: Overview · Categories · History
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf(
                        0 to viewModel.t("insights.tab_overview"),
                        1 to viewModel.t("insights.tab_categories"),
                        2 to viewModel.t("insights.tab_history")
                    )
                    for ((idx, title) in tabs) {
                        val selected = uiState.insightsSubTab == idx
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setInsightsSubTab(idx) }
                                .testTag("insights_subtab_$idx")
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Date Range Filter Chips + Operation Filter Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(InsightsDateRange.entries) { range ->
                        FilterChip(
                            selected = (uiState.insightsDateRange == range),
                            onClick = { viewModel.setInsightsDateRange(range) },
                            label = { Text(viewModel.t(range.labelKey)) },
                            modifier = Modifier.testTag("range_chip_${range.name}")
                        )
                    }
                }
                val opOptions = listOf(
                    null to viewModel.t("insights.filter_all_ops"),
                    "add" to "+",
                    "sub" to "−",
                    "mul" to "×",
                    "div" to "÷",
                    "sqrt" to "√",
                    "pow" to "xⁿ"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(opOptions) { (opCode, label) ->
                        FilterChip(
                            selected = (uiState.insightsOpFilter == opCode),
                            onClick = { viewModel.setInsightsOpFilter(opCode) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("op_filter_${opCode ?: "all"}")
                        )
                    }
                }
            }
        }

        when (uiState.insightsSubTab) {
            0 -> {
                // =============================================================
                // SUB-TAB 0: OVERVIEW
                // =============================================================
                // 1. Streak Card at the top of Overview (current, best, freezes ❄ n/2, week strip M T W T F S S)
                item {
                    OverviewStreakCard(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }

                // 2. Summary Tiles
                item {
                    OverviewSummaryRow(uiState = uiState, viewModel = viewModel)
                }

                // 3. Auto-Generated Plain-Language Insight Sentences
                if (snap.insightCards.isNotEmpty()) {
                    item {
                        InsightCardsSection(
                            cards = snap.insightCards,
                            viewModel = viewModel
                        )
                    }
                }

                // 4. Questions Practiced per Day Bar Chart with Daily Goal Line
                item {
                    QuestionsPerDayChartCard(
                        dailySeries = snap.dailySeries,
                        dailyGoal = uiState.settings.dailyGoalQuestions,
                        viewModel = viewModel
                    )
                }

                // 5. Correct vs Wrong Stacked Bar + Donut Summary
                item {
                    CorrectVsWrongChartCard(
                        dailySeries = snap.dailySeries,
                        totalCorrect = snap.totalCorrectInRange,
                        totalAttempts = snap.totalAttemptsInRange,
                        viewModel = viewModel
                    )
                }

                // 6. Overall Accuracy Over Time (Daily + 7d Moving Average)
                item {
                    AccuracyTrendChartCard(
                        dailySeries = snap.dailySeries,
                        overallAccuracy = snap.overallAccuracy,
                        viewModel = viewModel
                    )
                }

                // 7. Overall Speed Over Time (Avg Time/Question + % Faster Badge)
                item {
                    SpeedTrendChartCard(
                        dailySeries = snap.dailySeries,
                        overallAvgTimeMs = snap.overallAvgTimeMs,
                        speedChangePct = snap.speedChangePctVsLastWeek,
                        viewModel = viewModel
                    )
                }

                // 8. 12-Week Activity Heat Grid (shaded by question volume: 0, 1-19, 20-49, 50+)
                item {
                    VolumeTwelveWeekHeatGridCard(
                        dailySeries = snap.dailySeries,
                        completedDailyDates = snap.practiceStreakState.completedDates,
                        todayStr = LocalDate.now().toString(),
                        viewModel = viewModel
                    )
                }

                // 9. Your Skill Levels Card (the 6 operations with Lv x/10 progress bars & accuracy)
                item {
                    YourSkillLevelsCard(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }

                // 10. Strengths & Weaknesses Section (Strong, Needs practice, Not enough data yet, or Empty state)
                item {
                    StrengthsAndWeaknessesSection(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }

            1 -> {
                // =============================================================
                // SUB-TAB 1: CATEGORIES
                // =============================================================
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(CategorySortOrder.entries) { order ->
                            FilterChip(
                                selected = (uiState.categorySortOrder == order),
                                onClick = { viewModel.setCategorySortOrder(order) },
                                label = { Text(viewModel.t(order.labelKey)) },
                                modifier = Modifier.testTag("sort_chip_${order.name}")
                            )
                        }
                    }
                }

                // Operation Radar Chart (6 axes: +, −, ×, ÷, √, xⁿ)
                item {
                    OperationRadarChartCard(
                        radarPoints = snap.radarPoints,
                        viewModel = viewModel
                    )
                }

                // Speed × Accuracy Quadrant Scatter Plot
                item {
                    SpeedAccuracyQuadrantCard(
                        categories = snap.categoryStats,
                        medianTimeMs = snap.overallMedianTimeMs,
                        viewModel = viewModel
                    )
                }

                // Operation × Difficulty Heatmap
                item {
                    OperationDifficultyHeatmapCard(
                        cells = snap.heatmapCells,
                        viewModel = viewModel
                    )
                }

                // Accuracy by Category Horizontal Bars (Worst -> Best)
                item {
                    CategoryAccuracyBarsCard(
                        categories = snap.categoryStats,
                        viewModel = viewModel
                    )
                }

                // Speed by Category Horizontal Bars (Slowest -> Fastest + Median Line)
                item {
                    CategorySpeedBarsCard(
                        categories = snap.categoryStats,
                        medianMs = snap.overallMedianTimeMs,
                        viewModel = viewModel
                    )
                }

                // Sortable Category Performance Table
                item {
                    Text(
                        text = viewModel.t("insights.category_table"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(sortedCategories, key = { it.categoryId }) { catStat ->
                    CategoryPerformanceRowCard(
                        stat = catStat,
                        viewModel = viewModel,
                        onClick = { viewModel.openCategoryDetail(catStat.categoryId) }
                    )
                }
            }

            2 -> {
                // =============================================================
                // SUB-TAB 2: HISTORY & MISTAKE BOOK
                // =============================================================
                item {
                    MistakeBookSectionCard(
                        mistakeBook = uiState.mistakeBook,
                        viewModel = viewModel
                    )
                }

                // Filter Chips for Daily Answer History: All · Wrong only · Slow only
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.t("insights.tab_history"),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val filters = listOf(
                                "all" to viewModel.t("insights.history_filter_all"),
                                "wrong" to viewModel.t("insights.history_filter_wrong"),
                                "slow" to viewModel.t("insights.history_filter_slow")
                            )
                            for ((code, label) in filters) {
                                FilterChip(
                                    selected = (uiState.historyFilter == code),
                                    onClick = { viewModel.setHistoryFilter(code) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("history_filter_$code")
                                )
                            }
                        }
                    }
                }

                val logsByDay = uiState.rawAnswerLogs
                    .filter { log ->
                        when (uiState.historyFilter) {
                            "wrong" -> !log.correct
                            "slow" -> log.timeMs > (snap.overallMedianTimeMs * 1.25).toLong()
                            else -> true
                        }
                    }
                    .groupBy { it.day }
                    .toList()
                    .sortedByDescending { it.first }

                if (logsByDay.isEmpty()) {
                    item {
                        ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = viewModel.t("insights.empty_history"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                } else {
                    items(logsByDay, key = { it.first }) { (dayStr, dayLogs) ->
                        ExpandableDayHistoryCard(
                            dayStr = dayStr,
                            logs = dayLogs,
                            isExpanded = (uiState.expandedHistoryDay == dayStr),
                            onToggle = { viewModel.toggleHistoryDayExpanded(dayStr) },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. OVERVIEW SUB-COMPONENTS & CHARTS
// =========================================================================

@Composable
private fun InsightsSyncChip(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel
) {
    val pending = uiState.pendingSyncItemsCount
    val (bg, fg, label) = when {
        uiState.isSyncing -> Triple(
            NumeraPrimary.copy(alpha = 0.16f),
            NumeraPrimary,
            "Syncing..."
        )
        uiState.syncStatus == SyncStatusState.OFFLINE -> Triple(
            NumeraDanger.copy(alpha = 0.16f),
            NumeraDanger,
            viewModel.t("auth.sync_status_offline")
        )
        pending > 0 -> Triple(
            NumeraWarning.copy(alpha = 0.18f),
            NumeraWarning,
            "${viewModel.t("auth.push_data_now")} ($pending)"
        )
        else -> Triple(
            NumeraSuccess.copy(alpha = 0.16f),
            NumeraSuccess,
            viewModel.t("auth.sync_status_synced")
        )
    }

    Surface(
        onClick = { viewModel.pushMyDataNow() },
        enabled = !uiState.isSyncing,
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = Modifier.testTag("insights_sync_chip")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (uiState.isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = fg
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = fg
            )
        }
    }
}

@Composable
private fun OverviewStreakCard(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel
) {
    val streak = uiState.dashboardSnapshot.practiceStreakState
    val todayStr = remember { LocalDate.now().toString() }
    val weekItems = remember(streak, todayStr) {
        DailyEngine.buildWeekStrip(streak, todayStr)
    }

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_streak_card")
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = NumeraWarning,
                        modifier = Modifier.size(26.dp)
                    )
                    Column {
                        Text(
                            text = viewModel.t("daily.streak_days", mapOf("count" to streak.current.toString())),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = viewModel.t("insights.streak_rule_sub"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "Best ${streak.best}d · ❄ ${streak.freezesAvailable}/2",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (streak.savedByFreezeToday) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF4CB5E3).copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = viewModel.t("daily.freeze_saved"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CB5E3),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            WeekStripBar(weekItems = weekItems)
        }
    }
}

@Composable
private fun YourSkillLevelsCard(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel
) {
    val skills = uiState.dailyState.skills
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("insights_skill_levels_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = viewModel.t("insights.skill_levels_title"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            for (skillOp in SkillOp.entries) {
                val st = skills[skillOp]
                val lvl = st?.level ?: 2
                val accPct = ((st?.recentAccuracy ?: 0.7) * 100).roundToInt()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.t(skillOp.labelKey),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Lv $lvl/10 · $accPct%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (lvl / 10f).coerceIn(0.1f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun StrengthsAndWeaknessesSection(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel
) {
    val snap = uiState.dashboardSnapshot
    val hasAnyAnswers = snap.totalAttemptsInRange > 0 || uiState.rawAnswerLogs.isNotEmpty()

    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("strengths_weaknesses_section")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = viewModel.t("insights.sw_title"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (!hasAnyAnswers) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("strengths_weaknesses_empty")
                ) {
                    Text(
                        text = viewModel.t("insights.sw_empty"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
                return@Column
            }

            // One-line insight sentence if available
            val fastestSlowestCard = snap.insightCards.firstOrNull { it.type == "fastest_slowest" }
                ?: snap.insightCards.firstOrNull()
            if (fastestSlowestCard != null) {
                val resolvedParams = fastestSlowestCard.params.mapValues { (k, v) ->
                    when (k) {
                        "category", "fast", "slow", "fastCat", "slowCat" -> viewModel.categoryLabel(v)
                        "op" -> CategoryRegistry.opCodeToSymbol(v)
                        else -> v
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = viewModel.t(fastestSlowestCard.i18nKey, resolvedParams),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Group A: Strong — no need to practice (green)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = viewModel.t("insights.sw_strong_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NumeraSuccess
                )
                if (snap.strongCategories.isEmpty()) {
                    Text(
                        text = viewModel.t("insights.sw_none_yet"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    for (stat in snap.strongCategories) {
                        val accPct = (stat.accuracy * 100).roundToInt()
                        val avgSec = String.format("%.1fs", stat.avgTimeMs / 1000.0)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NumeraSuccess.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openCategoryDetail(stat.categoryId) }
                                .testTag("sw_strong_row_${stat.categoryId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = viewModel.categoryLabel(stat.categoryId),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$accPct% accuracy · $avgSec avg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Strong",
                                    tint = NumeraSuccess,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Group B: Needs practice (red/amber, sorted by weakness score)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = viewModel.t("insights.sw_needs_practice_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NumeraDanger
                )
                if (snap.needsPracticeCategories.isEmpty()) {
                    Text(
                        text = viewModel.t("insights.sw_no_weaknesses"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    for (stat in snap.needsPracticeCategories) {
                        val accPct = (stat.accuracy * 100).roundToInt()
                        val avgSec = String.format("%.1fs", stat.avgTimeMs / 1000.0)
                        val trendArrow = when {
                            stat.trendAccuracyDelta >= 0.04 || stat.trendTimeDeltaMs <= -300L -> "↑"
                            stat.trendAccuracyDelta <= -0.04 || stat.trendTimeDeltaMs >= 400L -> "↓"
                            else -> "→"
                        }
                        val rowColor = if (stat.accuracy < 0.70) NumeraDanger else NumeraWarning
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = rowColor.copy(alpha = 0.12f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sw_needs_practice_row_${stat.categoryId}")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.openCategoryDetail(stat.categoryId) }
                                ) {
                                    Text(
                                        text = viewModel.categoryLabel(stat.categoryId),
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$accPct% · $avgSec · Trend $trendArrow",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                FilledTonalButton(
                                    onClick = {
                                        viewModel.startSmartPractice(
                                            questionCount = 20,
                                            categoryFilter = stat.categoryId
                                        )
                                    },
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("sw_practice_${stat.categoryId}")
                                ) {
                                    Text(
                                        text = viewModel.t("insights.sw_practice_btn"),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Group C: Not enough data yet (< 10 attempts)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = viewModel.t("insights.sw_not_enough_title"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                for (stat in snap.notEnoughDataCategories.take(8)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sw_not_enough_row_${stat.categoryId}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = viewModel.categoryLabel(stat.categoryId),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${stat.attempts}/10 attempts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.startSmartPractice(
                                        questionCount = 10,
                                        categoryFilter = stat.categoryId
                                    )
                                },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("sw_try_${stat.categoryId}")
                            ) {
                                Text(
                                    text = viewModel.t("insights.sw_try_it"),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewSummaryRow(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel
) {
    val snap = uiState.dashboardSnapshot
    val accPct = (snap.overallAccuracy * 100).roundToInt()
    val avgSec = snap.overallAvgTimeMs / 1000.0

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricTile(
                value = "${snap.totalQuestionsToday}",
                label = viewModel.t("insights.questions_today"),
                color = NumeraPrimary
            )
            MetricTile(
                value = "${snap.totalQuestionsThisWeek}",
                label = viewModel.t("insights.questions_week"),
                color = NumeraPrimary
            )
            MetricTile(
                value = "$accPct%",
                label = viewModel.t("insights.accuracy"),
                color = if (accPct >= 85) NumeraSuccess else if (accPct >= 70) NumeraWarning else NumeraDanger
            )
            MetricTile(
                value = String.format("%.1fs", avgSec),
                label = viewModel.t("insights.avg_speed"),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun MetricTile(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InsightCardsSection(
    cards: List<InsightCardData>,
    viewModel: NumeraViewModel
) {
    ElevatedCard(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = viewModel.t("insights.insights_header"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            for (card in cards) {
                val resolvedParams = card.params.mapValues { (k, v) ->
                    when (k) {
                        "category", "fastCat", "slowCat" -> viewModel.categoryLabel(v)
                        "op" -> CategoryRegistry.opCodeToSymbol(v)
                        else -> v
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = card.categoryId != null) {
                            card.categoryId?.let { viewModel.openCategoryDetail(it) }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.t(card.i18nKey, resolvedParams),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (card.categoryId != null) {
                            Text(
                                text = "→",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestionsPerDayChartCard(
    dailySeries: List<DailyVolumePoint>,
    dailyGoal: Int,
    viewModel: NumeraViewModel
) {
    val visibleDays = dailySeries.takeLast(14)
    val barColor = MaterialTheme.colorScheme.primary
    val goalColor = NumeraWarning

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.t("insights.daily_volume_chart"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${viewModel.t("insights.daily_goal_line", mapOf("goal" to dailyGoal.toString()))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = goalColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(top = 6.dp)
            ) {
                if (visibleDays.isEmpty()) return@Canvas
                val maxQ = maxOf(visibleDays.maxOf { it.attempts }, dailyGoal, 10).toFloat()
                val slotW = size.width / visibleDays.size.toFloat()
                val barW = (slotW * 0.62f).coerceAtLeast(4f)

                for (i in visibleDays.indices) {
                    val pt = visibleDays[i]
                    val ratio = (pt.attempts / maxQ).coerceIn(0.03f, 1f)
                    val barH = size.height * ratio
                    val x = i * slotW + (slotW - barW) / 2f
                    val y = size.height - barH
                    drawRoundRect(
                        color = if (pt.attempts >= dailyGoal) NumeraSuccess else barColor,
                        topLeft = Offset(x, y),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                }

                // Dashed horizontal goal line
                val goalY = size.height - (dailyGoal / maxQ).coerceIn(0f, 1f) * size.height
                drawLine(
                    color = goalColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            }
        }
    }
}

@Composable
private fun CorrectVsWrongChartCard(
    dailySeries: List<DailyVolumePoint>,
    totalCorrect: Int,
    totalAttempts: Int,
    viewModel: NumeraViewModel
) {
    val visibleDays = dailySeries.takeLast(14)
    val totalWrong = (totalAttempts - totalCorrect).coerceAtLeast(0)
    val correctRatio = if (totalAttempts > 0) totalCorrect.toFloat() / totalAttempts.toFloat() else 0f

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = viewModel.t("insights.correct_vs_wrong"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Stacked Bar Chart
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                ) {
                    if (visibleDays.isEmpty()) return@Canvas
                    val maxQ = visibleDays.maxOf { it.attempts }.coerceAtLeast(10).toFloat()
                    val slotW = size.width / visibleDays.size.toFloat()
                    val barW = (slotW * 0.62f).coerceAtLeast(4f)

                    for (i in visibleDays.indices) {
                        val pt = visibleDays[i]
                        if (pt.attempts <= 0) continue
                        val corH = (pt.correct / maxQ) * size.height
                        val wrgH = (pt.wrong / maxQ) * size.height
                        val x = i * slotW + (slotW - barW) / 2f
                        val yCorrect = size.height - corH
                        val yWrong = yCorrect - wrgH

                        if (corH > 0f) {
                            drawRoundRect(
                                color = NumeraSuccess,
                                topLeft = Offset(x, yCorrect),
                                size = Size(barW, corH),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }
                        if (wrgH > 0f) {
                            drawRoundRect(
                                color = NumeraDanger,
                                topLeft = Offset(x, yWrong),
                                size = Size(barW, wrgH),
                                cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }
                    }
                }

                // Donut Chart Summary
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(96.dp)
                ) {
                    Canvas(modifier = Modifier.size(86.dp)) {
                        val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(
                            color = NumeraDanger.copy(alpha = 0.35f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = stroke
                        )
                        drawArc(
                            color = NumeraSuccess,
                            startAngle = -90f,
                            sweepAngle = 360f * correctRatio,
                            useCenter = false,
                            style = stroke
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${(correctRatio * 100).roundToInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$totalCorrect✓ / $totalWrong✗",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccuracyTrendChartCard(
    dailySeries: List<DailyVolumePoint>,
    overallAccuracy: Double,
    viewModel: NumeraViewModel
) {
    val activeDays = dailySeries.filter { it.attempts > 0 }.takeLast(20)
    val lineColor = NumeraSuccess
    val maColor = MaterialTheme.colorScheme.primary

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.t("insights.accuracy_trend"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(overallAccuracy * 100).roundToInt()}% (7d MA)",
                    style = MaterialTheme.typography.bodySmall,
                    color = maColor,
                    fontWeight = FontWeight.Bold
                )
            }

            if (activeDays.isEmpty()) {
                Text(
                    text = viewModel.t("insights.not_enough_data"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val n = activeDays.size
                    val stepX = if (n > 1) size.width / (n - 1).toFloat() else size.width / 2f

                    val maPath = Path()
                    for (i in activeDays.indices) {
                        val pt = activeDays[i]
                        val x = if (n > 1) i * stepX else size.width / 2f
                        val yAcc = size.height - (pt.accuracy.toFloat().coerceIn(0f, 1f) * size.height)
                        val yMa = size.height - (pt.movingAvgAccuracy7d.toFloat().coerceIn(0f, 1f) * size.height)

                        drawCircle(
                            color = lineColor,
                            radius = 4.dp.toPx(),
                            center = Offset(x, yAcc)
                        )
                        if (i == 0) maPath.moveTo(x, yMa) else maPath.lineTo(x, yMa)
                    }
                    if (n > 1) {
                        drawPath(
                            path = maPath,
                            color = maColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedTrendChartCard(
    dailySeries: List<DailyVolumePoint>,
    overallAvgTimeMs: Long,
    speedChangePct: Int?,
    viewModel: NumeraViewModel
) {
    val activeDays = dailySeries.filter { it.attempts > 0 && it.avgTimeMs > 0 }.takeLast(20)
    val speedColor = MaterialTheme.colorScheme.tertiary

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.t("insights.speed_trend"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (speedChangePct != null && speedChangePct > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NumeraSuccess.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "⚡ ${viewModel.t("insights.faster_than_last_week", mapOf("pct" to speedChangePct.toString()))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = NumeraSuccess,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Text(
                        text = String.format("%.1fs avg", overallAvgTimeMs / 1000.0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (activeDays.isEmpty()) {
                Text(
                    text = viewModel.t("insights.not_enough_data"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                ) {
                    val maxMs = activeDays.maxOf { it.avgTimeMs }.coerceAtLeast(2000L).toFloat()
                    val minMs = (activeDays.minOf { it.avgTimeMs } * 0.8f).coerceAtLeast(200f)
                    val span = (maxMs - minMs).coerceAtLeast(500f)
                    val n = activeDays.size
                    val stepX = if (n > 1) size.width / (n - 1).toFloat() else size.width / 2f

                    val path = Path()
                    for (i in activeDays.indices) {
                        val pt = activeDays[i]
                        val x = if (n > 1) i * stepX else size.width / 2f
                        val norm = ((pt.avgTimeMs - minMs) / span).coerceIn(0.05f, 0.95f)
                        val y = norm * size.height
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        drawCircle(color = speedColor, radius = 4.dp.toPx(), center = Offset(x, y))
                    }
                    if (n > 1) {
                        drawPath(
                            path = path,
                            color = speedColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeTwelveWeekHeatGridCard(
    dailySeries: List<DailyVolumePoint>,
    completedDailyDates: Set<String>,
    todayStr: String,
    viewModel: NumeraViewModel
) {
    val volumeMap = remember(dailySeries) {
        dailySeries.associate { it.day to it.attempts }
    }
    val today = remember(todayStr) {
        runCatching { LocalDate.parse(todayStr) }.getOrElse { LocalDate.now() }
    }

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = viewModel.t("progress.activity_grid"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (weekOffset in 11 downTo 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (dayInWeek in 6 downTo 0) {
                            val daysAgo = weekOffset * 7 + dayInWeek
                            val dStr = today.minusDays(daysAgo.toLong()).toString()
                            val count = volumeMap[dStr] ?: if (dStr in completedDailyDates) 20 else 0
                            val cellColor = when {
                                count >= 50 -> NumeraSuccess
                                count >= 20 -> NumeraSuccess.copy(alpha = 0.72f)
                                count >= 1 -> NumeraSuccess.copy(alpha = 0.38f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(cellColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. CATEGORIES SUB-COMPONENTS (Radar, Quadrant, Heatmap, Bars, Table)
// =========================================================================

@Composable
private fun OperationRadarChartCard(
    radarPoints: List<OperationRadarPoint>,
    viewModel: NumeraViewModel
) {
    val accColor = NumeraPrimary
    val speedColor = NumeraSuccess
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.t("insights.op_radar"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("● Accuracy", style = MaterialTheme.typography.labelSmall, color = accColor)
                    Text("● Speed", style = MaterialTheme.typography.labelSmall, color = speedColor)
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
            ) {
                Canvas(modifier = Modifier.size(170.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = size.minDimension / 2.2f
                    val n = radarPoints.size.coerceAtLeast(3)

                    // Concentric radar rings
                    for (ring in 1..3) {
                        drawCircle(
                            color = gridColor,
                            radius = radius * (ring / 3f),
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    val accPath = Path()
                    val spdPath = Path()

                    for (i in radarPoints.indices) {
                        val angle = (-Math.PI / 2.0) + (2.0 * Math.PI * i / n)
                        val axisX = cx + (radius * cos(angle)).toFloat()
                        val axisY = cy + (radius * sin(angle)).toFloat()
                        drawLine(
                            color = gridColor,
                            start = Offset(cx, cy),
                            end = Offset(axisX, axisY),
                            strokeWidth = 1.dp.toPx()
                        )

                        val pt = radarPoints[i]
                        val accR = radius * pt.accuracy.toFloat().coerceIn(0.15f, 1f)
                        val spdR = radius * pt.speedScoreNormalized.toFloat().coerceIn(0.15f, 1f)
                        val ax = cx + (accR * cos(angle)).toFloat()
                        val ay = cy + (accR * sin(angle)).toFloat()
                        val sx = cx + (spdR * cos(angle)).toFloat()
                        val sy = cy + (spdR * sin(angle)).toFloat()

                        if (i == 0) {
                            accPath.moveTo(ax, ay)
                            spdPath.moveTo(sx, sy)
                        } else {
                            accPath.lineTo(ax, ay)
                            spdPath.lineTo(sx, sy)
                        }
                    }
                    accPath.close()
                    spdPath.close()

                    drawPath(path = accPath, color = accColor.copy(alpha = 0.22f))
                    drawPath(path = accPath, color = accColor, style = Stroke(width = 2.5.dp.toPx()))
                    drawPath(path = spdPath, color = speedColor, style = Stroke(width = 2.dp.toPx()))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (pt in radarPoints) {
                    val sym = CategoryRegistry.opCodeToSymbol(pt.opCode)
                    Text(
                        text = "$sym ${(pt.accuracy * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedAccuracyQuadrantCard(
    categories: List<CategoryStat>,
    medianTimeMs: Long,
    viewModel: NumeraViewModel
) {
    val activeCats = categories.filter { it.attempts > 0 }
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = viewModel.t("insights.quadrant_chart"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("↖ ${viewModel.t("insights.quad_mastered")}", style = MaterialTheme.typography.labelSmall, color = NumeraSuccess)
                Text("↗ ${viewModel.t("insights.quad_careful")}", style = MaterialTheme.typography.labelSmall, color = NumeraPrimary)
                Text("↙ ${viewModel.t("insights.quad_guessing")}", style = MaterialTheme.typography.labelSmall, color = NumeraWarning)
                Text("↘ ${viewModel.t("insights.quad_needs_work")}", style = MaterialTheme.typography.labelSmall, color = NumeraDanger)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                val midX = size.width / 2f
                val midY = size.height * 0.25f // 85% accuracy line
                drawLine(color = gridColor, start = Offset(midX, 0f), end = Offset(midX, size.height), strokeWidth = 1.dp.toPx())
                drawLine(color = gridColor, start = Offset(0f, midY), end = Offset(size.width, midY), strokeWidth = 1.dp.toPx())

                val maxTime = (medianTimeMs * 2.0f).coerceAtLeast(4000f)
                for (cat in activeCats) {
                    val xRatio = (cat.avgTimeMs.toFloat() / maxTime).coerceIn(0.06f, 0.94f)
                    val yRatio = 1f - cat.accuracy.toFloat().coerceIn(0.06f, 0.94f)
                    val dotColor = when (cat.quadrant) {
                        QuadrantClass.MASTERED -> NumeraSuccess
                        QuadrantClass.CAREFUL -> NumeraPrimary
                        QuadrantClass.GUESSING -> NumeraWarning
                        QuadrantClass.NEEDS_WORK -> NumeraDanger
                    }
                    drawCircle(
                        color = dotColor,
                        radius = 6.dp.toPx(),
                        center = Offset(xRatio * size.width, yRatio * size.height)
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationDifficultyHeatmapCard(
    cells: List<HeatmapCell>,
    viewModel: NumeraViewModel
) {
    val ops = StatsService.RADAR_OPS
    val buckets = listOf("L1-2", "L3-4", "L5-6", "L7-8", "L9-10")

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = viewModel.t("insights.heatmap_chart"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Spacer(modifier = Modifier.width(36.dp))
                for (b in buckets) {
                    Text(
                        text = b,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }

            for (opCode in ops) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = CategoryRegistry.opCodeToSymbol(opCode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp)
                    )
                    for (bIdx in 0..4) {
                        val cell = cells.firstOrNull { it.opCode == opCode && it.bucketIndex == bIdx }
                        val att = cell?.attempts ?: 0
                        val acc = cell?.accuracy ?: 0.0
                        val bg = when {
                            att == 0 -> MaterialTheme.colorScheme.surfaceVariant
                            acc >= 0.85 -> NumeraSuccess.copy(alpha = 0.75f)
                            acc >= 0.70 -> NumeraWarning.copy(alpha = 0.75f)
                            else -> NumeraDanger.copy(alpha = 0.75f)
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(bg)
                        ) {
                            Text(
                                text = if (att > 0) "${(acc * 100).roundToInt()}%" else "—",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryAccuracyBarsCard(
    categories: List<CategoryStat>,
    viewModel: NumeraViewModel
) {
    val worstFirst = categories.filter { it.attempts > 0 }.sortedBy { it.accuracy }.take(8)
    if (worstFirst.isEmpty()) return

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "${viewModel.t("insights.accuracy")} (Worst → Best)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            for (cat in worstFirst) {
                val pct = (cat.accuracy * 100).roundToInt()
                val barColor = when (cat.accuracyClass) {
                    AccuracyClass.STRONG -> NumeraSuccess
                    AccuracyClass.OK -> NumeraWarning
                    AccuracyClass.WEAK -> NumeraDanger
                    AccuracyClass.NOT_ENOUGH_DATA -> MaterialTheme.colorScheme.primary
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { viewModel.openCategoryDetail(cat.categoryId) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = viewModel.categoryLabel(cat.categoryId),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$pct% (${cat.attempts})",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { cat.accuracy.toFloat().coerceIn(0.04f, 1f) },
                        color = barColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySpeedBarsCard(
    categories: List<CategoryStat>,
    medianMs: Long,
    viewModel: NumeraViewModel
) {
    val slowestFirst = categories.filter { it.attempts > 0 && it.avgTimeMs > 0 }
        .sortedByDescending { it.avgTimeMs }
        .take(8)
    if (slowestFirst.isEmpty()) return
    val maxMs = slowestFirst.maxOf { it.avgTimeMs }.coerceAtLeast(2000L).toFloat()

    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${viewModel.t("insights.avg_speed")} (Slowest → Fastest)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${viewModel.t("insights.median_speed")}: ${String.format("%.1fs", medianMs / 1000.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            for (cat in slowestFirst) {
                val ratio = (cat.avgTimeMs.toFloat() / maxMs).coerceIn(0.08f, 1f)
                val barColor = when (cat.speedClass) {
                    SpeedClass.FAST -> NumeraSuccess
                    SpeedClass.NORMAL -> NumeraPrimary
                    SpeedClass.SLOW -> NumeraWarning
                    SpeedClass.NOT_ENOUGH_DATA -> MaterialTheme.colorScheme.secondary
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { viewModel.openCategoryDetail(cat.categoryId) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = viewModel.categoryLabel(cat.categoryId),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${cat.speedClass.badgeSymbol} ${String.format("%.1fs", cat.avgTimeMs / 1000.0)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        color = barColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPerformanceRowCard(
    stat: CategoryStat,
    viewModel: NumeraViewModel,
    onClick: () -> Unit
) {
    val opSym = CategoryRegistry.opCodeToSymbol(stat.op)
    val accPct = (stat.accuracy * 100).roundToInt()
    val avgSec = stat.avgTimeMs / 1000.0
    val trendArrow = when {
        stat.trendAccuracyDelta >= 0.04 || stat.trendTimeDeltaMs <= -300L -> "↑"
        stat.trendAccuracyDelta <= -0.04 || stat.trendTimeDeltaMs >= 400L -> "↓"
        else -> "→"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("category_row_${stat.categoryId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = opSym,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column {
                        Text(
                            text = viewModel.categoryLabel(stat.categoryId),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (stat.hasEnoughData) {
                                "${stat.attempts} attempts · ${viewModel.t(stat.quadrant.labelKey)} · Trend $trendArrow"
                            } else {
                                "${stat.attempts} attempts · ${viewModel.t("insights.not_enough_data")}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (stat.attempts > 0) "$accPct%" else "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (stat.accuracyClass) {
                            AccuracyClass.STRONG -> NumeraSuccess
                            AccuracyClass.OK -> NumeraWarning
                            AccuracyClass.WEAK -> NumeraDanger
                            AccuracyClass.NOT_ENOUGH_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = if (stat.avgTimeMs > 0) "${stat.speedClass.badgeSymbol} ${String.format("%.1fs", avgSec)}" else "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =========================================================================
// 4. HISTORY & MISTAKE BOOK SUB-COMPONENTS
// =========================================================================

@Composable
private fun MistakeBookSectionCard(
    mistakeBook: List<MistakeBookEntry>,
    viewModel: NumeraViewModel
) {
    ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${viewModel.t("insights.mistake_book")} (${mistakeBook.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = viewModel.t("insights.mistake_book_sub"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (mistakeBook.isNotEmpty()) {
                    FilledTonalButton(
                        onClick = { viewModel.openSmartPracticeSetup(null) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(viewModel.t("smart.start_btn"))
                    }
                }
            }

            if (mistakeBook.isEmpty()) {
                Text(
                    text = viewModel.t("daily.no_mistakes"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = NumeraSuccess
                )
            } else {
                for (entry in mistakeBook.take(15)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${entry.prompt} = ${entry.expected}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${viewModel.categoryLabel(entry.category)} · Missed ${entry.missCount}× · Due ${entry.nextReviewDay}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.markMistakeMastered(entry.normalizedKey) },
                                modifier = Modifier.testTag("master_mistake_${entry.normalizedKey}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = viewModel.t("insights.mark_mastered"),
                                    tint = NumeraSuccess
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandableDayHistoryCard(
    dayStr: String,
    logs: List<AnswerLog>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    viewModel: NumeraViewModel
) {
    val total = logs.size
    val correct = logs.count { it.correct }
    val wrong = total - correct
    val accPct = if (total > 0) ((correct.toDouble() / total) * 100).roundToInt() else 0
    val avgSec = if (total > 0) logs.map { it.timeMs }.average() / 1000.0 else 0.0

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .testTag("history_day_$dayStr")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = dayStr,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$total questions · $correct✓ $wrong✗ · $accPct% · ${String.format("%.1fs", avgSec)} avg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (isExpanded) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (log in logs.take(40)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (log.correct) Icons.Default.CheckCircle else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (log.correct) NumeraSuccess else NumeraDanger,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (log.correct) {
                                        "${log.prompt} = ${log.expected}"
                                    } else {
                                        "${log.prompt} = ${log.expected} (typed ${log.given ?: "—"})"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = String.format("%.1fs", log.timeMs / 1000.0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. CATEGORY DETAIL SCREEN (ScreenRoute.CategoryDetail)
// =========================================================================

@Composable
fun CategoryDetailScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.closeCategoryDetail() }

    val detail = uiState.activeCategoryDetail
    if (detail == null) {
        viewModel.closeCategoryDetail()
        return
    }
    val stat = detail.stat
    val accPct = (stat.accuracy * 100).roundToInt()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("category_detail_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.closeCategoryDetail() },
                    modifier = Modifier.testTag("category_detail_back_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = viewModel.categoryLabel(stat.categoryId),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${viewModel.t(stat.quadrant.labelKey)} · ${stat.attempts} attempts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Practice This Category CTA
        item {
            Button(
                onClick = { viewModel.startSmartPractice(questionCount = 20, categoryFilter = stat.categoryId) },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("practice_this_category_btn")
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = viewModel.t("insights.practice_this_cat"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Detailed Metrics Card
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricTile(
                        value = "$accPct%",
                        label = viewModel.t("insights.accuracy"),
                        color = if (accPct >= 85) NumeraSuccess else if (accPct >= 70) NumeraWarning else NumeraDanger
                    )
                    MetricTile(
                        value = String.format("%.1fs", stat.avgTimeMs / 1000.0),
                        label = viewModel.t("insights.avg_speed"),
                        color = NumeraPrimary
                    )
                    MetricTile(
                        value = String.format("%.1fs", stat.medianTimeMs / 1000.0),
                        label = viewModel.t("insights.median_speed"),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    MetricTile(
                        value = String.format("%.1fs", stat.p90TimeMs / 1000.0),
                        label = viewModel.t("insights.p90_speed"),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        // Category Accuracy Trend Chart
        item {
            AccuracyTrendChartCard(
                dailySeries = detail.dailyPoints,
                overallAccuracy = stat.accuracy,
                viewModel = viewModel
            )
        }

        // Last 10 Mistakes in this Category
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = viewModel.t("insights.last_10_mistakes"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (detail.recentMistakes.isEmpty()) {
                        Text(
                            text = viewModel.t("daily.no_mistakes"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = NumeraSuccess
                        )
                    } else {
                        for (m in detail.recentMistakes) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${m.prompt} = ${m.expected} (typed ${m.given ?: "—"})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${m.day} · ${String.format("%.1fs", m.timeMs / 1000.0)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 6. SMART PRACTICE SETUP DIALOG (10 / 20 / 30 questions + Category Filter)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartPracticeSetupDialog(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel
) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissSmartPracticeSetup() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(viewModel.t("smart.setup_title"), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = viewModel.t("smart.btn_sub"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = viewModel.t("smart.question_count"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (cnt in listOf(10, 20, 30)) {
                        FilterChip(
                            selected = (uiState.smartPracticeCount == cnt),
                            onClick = { viewModel.updateSmartPracticeCount(cnt) },
                            label = { Text("$cnt") },
                            modifier = Modifier.testTag("smart_count_$cnt")
                        )
                    }
                }

                Text(
                    text = viewModel.t("smart.filter_category"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = (uiState.smartPracticeCategoryFilter == null),
                            onClick = { viewModel.updateSmartPracticeCategoryFilter(null) },
                            label = { Text(viewModel.t("smart.all_categories")) }
                        )
                    }
                    val topWeak = uiState.dashboardSnapshot.categoryStats
                        .sortedByDescending { it.weaknessScore }
                        .take(8)
                    items(topWeak, key = { it.categoryId }) { cat ->
                        FilterChip(
                            selected = (uiState.smartPracticeCategoryFilter == cat.categoryId),
                            onClick = { viewModel.updateSmartPracticeCategoryFilter(cat.categoryId) },
                            label = { Text(viewModel.categoryLabel(cat.categoryId)) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.startSmartPractice(
                        questionCount = uiState.smartPracticeCount,
                        categoryFilter = uiState.smartPracticeCategoryFilter
                    )
                },
                modifier = Modifier.testTag("start_smart_practice_confirm_btn")
            ) {
                Text(viewModel.t("smart.start_btn"), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissSmartPracticeSetup() }) {
                Text("Cancel")
            }
        }
    )
}
