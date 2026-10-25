package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CustomConfig
import com.example.model.Difficulty
import com.example.model.MathOp
import com.example.model.QuizMode
import com.example.ui.MemoryPhase
import com.example.ui.NumeraUiState
import com.example.ui.NumeraViewModel
import com.example.ui.components.CustomMathKeypad
import com.example.ui.components.ModeCategoryIcon
import com.example.ui.theme.NumeraDanger
import com.example.ui.theme.NumeraSuccess
import com.example.ui.theme.NumeraWarning

// =========================================================================
// 1. ARITHMETIC MEMORY ("Follow the chain")
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemorySetupScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateToHome() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("memory_setup_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigateToHome() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(6.dp))
                ModeCategoryIcon(mode = QuizMode.ARITHMETIC_MEMORY, tint = NumeraWarning)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = viewModel.t("mode.memory"),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = viewModel.t("mode.memory_sub"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = NumeraWarning.copy(alpha = 0.14f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = viewModel.t("memory.hint"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }

            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Chain Length (Steps)", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (steps in 5..10) {
                            FilterChip(
                                selected = (uiState.memoryStepsCount == steps),
                                onClick = { viewModel.updateMemoryStepsCount(steps) },
                                label = { Text("$steps steps") }
                            )
                        }
                    }
                }
            }

            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(viewModel.t("setup.difficulty"), style = MaterialTheme.typography.titleLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (diff in Difficulty.entries) {
                            FilterChip(
                                selected = (uiState.memoryDifficulty == diff),
                                onClick = { viewModel.updateMemoryDifficulty(diff) },
                                label = { Text(viewModel.t(diff.labelKey)) }
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.startMemoryGame() },
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_memory_btn")
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(viewModel.t("setup.start"), style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
fun MemoryGameScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateToHome() }

    val mem = uiState.activeMemory ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("memory_game_screen"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateToHome() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = when (mem.phase) {
                    MemoryPhase.SHOW_START -> viewModel.t("memory.remember")
                    MemoryPhase.SHOW_STEP -> "Step ${mem.currentStepIndex + 1} / ${mem.chain.steps.size}"
                    MemoryPhase.ENTER_ANSWER -> viewModel.t("memory.enter_final")
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 12.dp)
                .clickable(enabled = mem.phase != MemoryPhase.ENTER_ANSWER) {
                    viewModel.advanceMemoryStepManually()
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = Pair(mem.phase, mem.currentStepIndex),
                    label = "memory_step_anim"
                ) { (phase, idx) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when (phase) {
                            MemoryPhase.SHOW_START -> {
                                Text(
                                    text = viewModel.t("memory.remember"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${mem.chain.startNumber}",
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 68.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "(Tap card to advance faster)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            MemoryPhase.SHOW_STEP -> {
                                val step = mem.chain.steps.getOrNull(idx)
                                Text(
                                    text = viewModel.t("memory.apply"),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = step?.displayStep ?: "",
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp),
                                    color = NumeraWarning,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            MemoryPhase.ENTER_ANSWER -> {
                                Text(
                                    text = viewModel.t("memory.enter_final"),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = mem.typedInput.ifEmpty { "?" },
                                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 58.sp),
                                    color = NumeraSuccess,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        if (mem.phase == MemoryPhase.ENTER_ANSWER) {
            CustomMathKeypad(
                keypadType = uiState.settings.keypadType,
                keypadSize = uiState.settings.keypadSize,
                autoConfirm = false,
                enterLabel = viewModel.t("game.enter"),
                clearLabel = viewModel.t("game.clear"),
                onDigit = { viewModel.onKeypadDigit(it) },
                onBackspace = { viewModel.onKeypadBackspace() },
                onClear = { viewModel.onKeypadClear() },
                onEnter = { viewModel.onKeypadEnter() }
            )
        }
    }
}

@Composable
fun MemoryResultScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateToHome() }

    val mem = uiState.activeMemory ?: return
    val revealedIndices = remember { mutableStateListOf<Int>() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("memory_result_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (mem.isCorrect) {
                        NumeraSuccess.copy(alpha = 0.16f)
                    } else {
                        NumeraDanger.copy(alpha = 0.16f)
                    }
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
                        imageVector = if (mem.isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = null,
                        tint = if (mem.isCorrect) NumeraSuccess else NumeraDanger,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (mem.isCorrect) "Chain Mastered!" else "Close! Check the Chain",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Correct Final Answer: ${mem.chain.finalAnswer} · Your Answer: ${mem.userFinalAnswer ?: "-"}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = viewModel.t("memory.step_by_step"),
                    style = MaterialTheme.typography.titleLarge
                )
                FilledTonalButton(
                    onClick = {
                        revealedIndices.clear()
                        revealedIndices.addAll(mem.chain.steps.indices)
                    }
                ) {
                    Text("Reveal All")
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Start Number", fontWeight = FontWeight.Bold)
                    Text("${mem.chain.startNumber}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        items(mem.chain.steps.size) { idx ->
            val step = mem.chain.steps[idx]
            val isRevealed = idx in revealedIndices
            Card(
                onClick = {
                    if (isRevealed) revealedIndices.remove(idx) else revealedIndices.add(idx)
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("memory_step_row_$idx")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Step ${idx + 1}: ${step.previousValue} ${step.displayStep}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Reveal",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isRevealed) "= ${step.runningValue}" else "Tap to reveal",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { viewModel.startMemoryGame() },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(viewModel.t("result.play_again"))
                }
                OutlinedButton(
                    onClick = { viewModel.navigateToHome() },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                ) {
                    Text(viewModel.t("result.home"))
                }
            }
        }
    }
}

// =========================================================================
// 2. MULTIPLICATION TABLES (Learn + Practice 1–50)
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TablesHubScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateToHome() }

    val revealedMultipliers = remember(uiState.activeLearnTable) {
        mutableStateListOf<Int>()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("tables_hub_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateToHome() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = viewModel.t("mode.tables"),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        TabRow(selectedTabIndex = uiState.tablesTabIndex) {
            Tab(
                selected = (uiState.tablesTabIndex == 0),
                onClick = { viewModel.setTablesSubTab(0) },
                text = { Text(viewModel.t("tables.learn"), fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = (uiState.tablesTabIndex == 1),
                onClick = { viewModel.setTablesSubTab(1) },
                text = { Text(viewModel.t("tables.practice"), fontWeight = FontWeight.Bold) }
            )
        }

        if (uiState.tablesTabIndex == 0) {
            // LEARN MODE: Scrollable Table selector 1..50 + "Table of N" rows N x 1..12 with tap to reveal
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Select Table (1–50): Table of ${uiState.activeLearnTable}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items((1..50).toList()) { num ->
                            FilterChip(
                                selected = (uiState.activeLearnTable == num),
                                onClick = { viewModel.setActiveLearnTable(num) },
                                label = { Text("×$num", fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.t("tables.tap_reveal"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    if (revealedMultipliers.size == 12) {
                                        revealedMultipliers.clear()
                                    } else {
                                        revealedMultipliers.clear()
                                        revealedMultipliers.addAll(1..12)
                                    }
                                }
                            ) {
                                Text(if (revealedMultipliers.size == 12) "Hide All" else "Reveal All")
                            }
                            Button(
                                onClick = {
                                    viewModel.selectAllTables(false)
                                    // Practice this table specifically
                                    for (t in 1..50) {
                                        if ((t in uiState.selectedTables) != (t == uiState.activeLearnTable)) {
                                            viewModel.toggleSelectedTable(t)
                                        }
                                    }
                                    viewModel.startTablesPractice(12)
                                }
                            ) {
                                Text("Practice ×${uiState.activeLearnTable}")
                            }
                        }
                    }
                }

                items((1..12).toList()) { k ->
                    val n = uiState.activeLearnTable
                    val revealed = k in revealedMultipliers
                    Card(
                        onClick = {
                            if (revealed) revealedMultipliers.remove(k) else revealedMultipliers.add(k)
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$n × $k",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (revealed) "= ${n * k}" else "Tap to reveal",
                                style = MaterialTheme.typography.titleLarge,
                                color = if (revealed) NumeraSuccess else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // PRACTICE MODE: Multi-select chips 1..50 + best times per table
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = viewModel.t(
                                "tables.selected",
                                mapOf("count" to uiState.selectedTables.size.toString())
                            ),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = (uiState.selectedTables.size == 50),
                                onClick = {
                                    viewModel.selectAllTables(uiState.selectedTables.size < 50)
                                },
                                label = { Text(viewModel.t("tables.all")) }
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = { viewModel.startTablesPractice(12) },
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_tables_practice_btn")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Practice Selected Tables (${uiState.selectedTables.size})")
                    }
                }

                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (num in 1..50) {
                            val selected = num in uiState.selectedTables
                            val stat = uiState.tableStats[num]
                            val labelStr = if (stat != null) {
                                "$num (${String.format("%.0fs", stat.bestTimeMs / 1000.0)})"
                            } else {
                                "$num"
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.toggleSelectedTable(num) },
                                label = { Text(labelStr, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. CUSTOMIZABLE MODE
// =========================================================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomSetupScreen(
    uiState: NumeraUiState,
    viewModel: NumeraViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.navigateToHome() }

    val initial = uiState.customConfig
    var selectedOps by remember { mutableStateOf(initial.ops) }
    var minAStr by remember { mutableStateOf(initial.minA.toString()) }
    var maxAStr by remember { mutableStateOf(initial.maxA.toString()) }
    var minBStr by remember { mutableStateOf(initial.minB.toString()) }
    var maxBStr by remember { mutableStateOf(initial.maxB.toString()) }
    var questionCount by remember { mutableIntStateOf(initial.questionCount) }
    var timeLimitMinutes by remember { mutableStateOf(initial.timeLimitMinutes) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("custom_setup_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.navigateToHome() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(6.dp))
                ModeCategoryIcon(mode = QuizMode.CUSTOMIZABLE)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = viewModel.t("mode.custom"),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = viewModel.t("mode.custom_sub"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Operations (+, −, ×, ÷)
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Operations", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(MathOp.ADD, MathOp.SUB, MathOp.MUL, MathOp.DIV).forEach { op ->
                            val isSel = op in selectedOps
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    if (isSel && selectedOps.size > 1) {
                                        selectedOps = selectedOps - op
                                    } else if (!isSel) {
                                        selectedOps = selectedOps + op
                                    }
                                },
                                label = {
                                    Text("${op.symbol} ${viewModel.t(op.labelKey)}", fontWeight = FontWeight.Bold)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Number ranges for each operand ("Numbers")
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Numbers (Operand Ranges)", style = MaterialTheme.typography.titleLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = minAStr,
                            onValueChange = { if (it.all { c -> c.isDigit() }) minAStr = it.take(6) },
                            label = { Text("First Min") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxAStr,
                            onValueChange = { if (it.all { c -> c.isDigit() }) maxAStr = it.take(6) },
                            label = { Text("First Max") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = minBStr,
                            onValueChange = { if (it.all { c -> c.isDigit() }) minBStr = it.take(6) },
                            label = { Text("Second Min") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = maxBStr,
                            onValueChange = { if (it.all { c -> c.isDigit() }) maxBStr = it.take(6) },
                            label = { Text("Second Max") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Question Count & Optional Time Limit (1, 2, 3, 5 minutes)
        item {
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(viewModel.t("setup.questions"), style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 30, 40, 50).forEach { c ->
                            FilterChip(
                                selected = (questionCount == c),
                                onClick = { questionCount = c },
                                label = { Text("$c") }
                            )
                        }
                    }

                    Text("Time Limit (Optional)", style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null, 1, 2, 3, 5).forEach { mins ->
                            FilterChip(
                                selected = (timeLimitMinutes == mins),
                                onClick = { timeLimitMinutes = mins },
                                label = { Text(if (mins == null) "No Limit" else "$mins min") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val minA = (minAStr.toIntOrNull() ?: 2).coerceAtLeast(1)
                    val maxA = (maxAStr.toIntOrNull() ?: 50).coerceAtLeast(minA)
                    val minB = (minBStr.toIntOrNull() ?: 2).coerceAtLeast(1)
                    val maxB = (maxBStr.toIntOrNull() ?: 50).coerceAtLeast(minB)
                    viewModel.startCustomQuiz(
                        CustomConfig(
                            ops = selectedOps,
                            minA = minA,
                            maxA = maxA,
                            minB = minB,
                            maxB = maxB,
                            questionCount = questionCount,
                            timeLimitMinutes = timeLimitMinutes
                        )
                    )
                },
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_custom_quiz_btn")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(viewModel.t("setup.start"), style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}
