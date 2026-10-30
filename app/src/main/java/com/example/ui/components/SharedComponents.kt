package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DayStatus
import com.example.model.KeypadSize
import com.example.model.KeypadType
import com.example.model.PresetAvatar
import com.example.model.QuizMode
import com.example.ui.theme.NumeraDanger
import com.example.ui.theme.NumeraSuccess

/**
 * Custom on-screen Math Keypad supporting Phone (1-2-3 top) and Calculator (7-8-9 top) layouts,
 * Normal & Large button heights, Clear, Backspace, and Enter.
 */
@Composable
fun CustomMathKeypad(
    keypadType: KeypadType,
    keypadSize: KeypadSize,
    autoConfirm: Boolean,
    enterLabel: String,
    clearLabel: String,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rowHeight: Dp = if (keypadSize == KeypadSize.LARGE) 64.dp else 52.dp
    val rows = if (keypadType == KeypadType.PHONE) {
        listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9)
        )
    } else {
        listOf(
            listOf(7, 8, 9),
            listOf(4, 5, 6),
            listOf(1, 2, 3)
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("custom_math_keypad"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (digit in row) {
                    KeypadDigitButton(
                        digit = digit,
                        height = rowHeight,
                        onClick = { onDigit(digit) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("key_$digit")
                    )
                }
            }
        }

        // Bottom row: Clear, 0, Backspace
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = onClear,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(rowHeight)
                    .testTag("key_clear")
            ) {
                Text(
                    text = clearLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            KeypadDigitButton(
                digit = 0,
                height = rowHeight,
                onClick = { onDigit(0) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("key_0")
            )

            FilledTonalButton(
                onClick = onBackspace,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(rowHeight)
                    .testTag("key_backspace")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace"
                )
            }
        }

        // Enter button (always visible so user can submit early or when Auto Confirm is off)
        Button(
            onClick = onEnter,
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (autoConfirm) 50.dp else 56.dp)
                .testTag("key_enter")
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = enterLabel,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (autoConfirm) "$enterLabel (Auto-Confirm ON)" else enterLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun KeypadDigitButton(
    digit: Int,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = modifier.height(height)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = digit.toString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 24.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Original flat-line SVG/Canvas artwork for each math training category.
 */
@Composable
fun ModeCategoryIcon(
    mode: QuizMode,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier.size(56.dp)
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.22f))
            .border(1.5.dp, tint.copy(alpha = 0.42f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)

            when (mode) {
                QuizMode.ADDITION -> {
                    drawLine(tint, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.82f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), stroke.width, StrokeCap.Round)
                }
                QuizMode.SUBTRACTION -> {
                    drawLine(tint, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), stroke.width, StrokeCap.Round)
                }
                QuizMode.MULTIPLICATION -> {
                    drawLine(tint, Offset(w * 0.24f, h * 0.24f), Offset(w * 0.76f, h * 0.76f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.76f, h * 0.24f), Offset(w * 0.24f, h * 0.76f), stroke.width, StrokeCap.Round)
                }
                QuizMode.DIVISION -> {
                    drawLine(tint, Offset(w * 0.18f, h * 0.5f), Offset(w * 0.82f, h * 0.5f), stroke.width, StrokeCap.Round)
                    drawCircle(tint, radius = 2.6.dp.toPx(), center = Offset(w * 0.5f, h * 0.24f))
                    drawCircle(tint, radius = 2.6.dp.toPx(), center = Offset(w * 0.5f, h * 0.76f))
                }
                QuizMode.MIXED -> {
                    // 4 mini quadrants (+, -, x, ÷)
                    drawLine(tint, Offset(w * 0.28f, h * 0.16f), Offset(w * 0.28f, h * 0.40f), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.16f, h * 0.28f), Offset(w * 0.40f, h * 0.28f), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.60f, h * 0.28f), Offset(w * 0.84f, h * 0.28f), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.18f, h * 0.62f), Offset(w * 0.38f, h * 0.82f), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.38f, h * 0.62f), Offset(w * 0.18f, h * 0.82f), 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(tint, radius = 2.2.dp.toPx(), center = Offset(w * 0.72f, h * 0.72f))
                }
                QuizMode.ADD_SUB -> {
                    drawLine(tint, Offset(w * 0.5f, h * 0.14f), Offset(w * 0.5f, h * 0.54f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.24f, h * 0.34f), Offset(w * 0.76f, h * 0.34f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.24f, h * 0.78f), Offset(w * 0.76f, h * 0.78f), stroke.width, StrokeCap.Round)
                }
                QuizMode.MULT_DIV -> {
                    drawLine(tint, Offset(w * 0.25f, h * 0.16f), Offset(w * 0.75f, h * 0.52f), 2.2.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.75f, h * 0.16f), Offset(w * 0.25f, h * 0.52f), 2.2.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.22f, h * 0.78f), Offset(w * 0.78f, h * 0.78f), 2.2.dp.toPx(), StrokeCap.Round)
                }
                QuizMode.SQUARE_ROOT, QuizMode.CUBE_ROOT -> {
                    drawLine(tint, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.30f, h * 0.82f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.30f, h * 0.82f), Offset(w * 0.52f, h * 0.20f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.52f, h * 0.20f), Offset(w * 0.88f, h * 0.20f), stroke.width, StrokeCap.Round)
                }
                QuizMode.EXPONENTS -> {
                    drawLine(tint, Offset(w * 0.2f, h * 0.75f), Offset(w * 0.5f, h * 0.30f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(w * 0.5f, h * 0.30f), Offset(w * 0.8f, h * 0.75f), stroke.width, StrokeCap.Round)
                    drawCircle(tint, radius = 2.4.dp.toPx(), center = Offset(w * 0.78f, h * 0.24f))
                }
                QuizMode.MULTIPLICATION_TABLES -> {
                    for (r in 0..2) {
                        for (c in 0..2) {
                            drawCircle(
                                color = tint,
                                radius = 2.1.dp.toPx(),
                                center = Offset(w * (0.22f + c * 0.28f), h * (0.22f + r * 0.28f))
                            )
                        }
                    }
                }
                QuizMode.CUSTOMIZABLE -> {
                    drawLine(tint, Offset(w * 0.15f, h * 0.3f), Offset(w * 0.85f, h * 0.3f), 2.2.dp.toPx(), StrokeCap.Round)
                    drawCircle(tint, radius = 3.2.dp.toPx(), center = Offset(w * 0.38f, h * 0.3f))
                    drawLine(tint, Offset(w * 0.15f, h * 0.7f), Offset(w * 0.85f, h * 0.7f), 2.2.dp.toPx(), StrokeCap.Round)
                    drawCircle(tint, radius = 3.2.dp.toPx(), center = Offset(w * 0.66f, h * 0.7f))
                }
                QuizMode.ARITHMETIC_MEMORY -> {
                    val p1 = Offset(w * 0.22f, h * 0.72f)
                    val p2 = Offset(w * 0.50f, h * 0.28f)
                    val p3 = Offset(w * 0.78f, h * 0.65f)
                    drawLine(tint, p1, p2, 2.2.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, p2, p3, 2.2.dp.toPx(), StrokeCap.Round)
                    drawCircle(tint, 3.2.dp.toPx(), p1)
                    drawCircle(tint, 3.2.dp.toPx(), p2)
                    drawCircle(tint, 3.2.dp.toPx(), p3)
                }
                else -> {
                    drawCircle(tint, radius = w * 0.32f, style = stroke)
                }
            }
        }
    }
}

/**
 * Week strip (M T W T F S S) displaying done / today / missed / frozen / future states.
 */
@Composable
fun WeekStripBar(
    weekItems: List<Pair<String, DayStatus>>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekItems.forEachIndexed { idx, (label, status) ->
            val bgColor by animateColorAsState(
                targetValue = when (status) {
                    DayStatus.DONE -> NumeraSuccess
                    DayStatus.FROZEN -> Color(0xFF4CB5E3)
                    DayStatus.TODAY -> MaterialTheme.colorScheme.primaryContainer
                    DayStatus.MISSED -> NumeraDanger.copy(alpha = 0.18f)
                    DayStatus.FUTURE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                },
                animationSpec = tween(250),
                label = "week_bg_$idx"
            )
            val contentColor = when (status) {
                DayStatus.DONE, DayStatus.FROZEN -> Color.White
                DayStatus.TODAY -> MaterialTheme.colorScheme.primary
                DayStatus.MISSED -> NumeraDanger
                DayStatus.FUTURE -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(bgColor)
                        .then(
                            if (status == DayStatus.TODAY) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val symbol = when (status) {
                        DayStatus.DONE -> "✓"
                        DayStatus.FROZEN -> "❄"
                        DayStatus.TODAY -> "•"
                        DayStatus.MISSED -> "×"
                        DayStatus.FUTURE -> ""
                    }
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Circular avatar badge displaying either user initials or a preset geometric animal avatar.
 */
@Composable
fun PresetAvatarBadge(
    avatarId: String,
    displayName: String = "",
    size: Dp = 48.dp,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val preset = PresetAvatar.fromId(avatarId)
    val bgColor = Color(preset.colorHex)
    val initials = displayName.trim()
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { preset.badgeText }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.22f))
            .border(
                width = if (isSelected) 3.dp else 1.5.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else bgColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = bgColor
        )
    }
}

/**
 * Dialog asking the user whether to merge local guest progress into an existing cloud account
 * or use cloud data only.
 */
@Composable
fun GuestMergePromptDialog(
    title: String,
    body: String,
    mergeButtonText: String,
    cloudOnlyButtonText: String,
    onMergeConfirm: () -> Unit,
    onCloudOnlyConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onMergeConfirm,
        icon = {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(
                onClick = onMergeConfirm,
                modifier = Modifier.testTag("merge_guest_confirm_btn")
            ) {
                Text(mergeButtonText)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCloudOnlyConfirm,
                modifier = Modifier.testTag("merge_cloud_only_btn")
            ) {
                Text(cloudOnlyButtonText)
            }
        }
    )
}

/**
 * Dialog shown when the 7-day guest trial has expired, directing the user to connect with Google or Email
 * to keep their progress and workouts.
 */
@Composable
fun GuestExpiredPromptDialog(
    title: String,
    body: String,
    onSignInWithGoogle: () -> Unit,
    onSignInWithEmail: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Modal: require login */ },
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(
                onClick = onSignInWithGoogle,
                modifier = Modifier.testTag("expired_google_signin_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Google Sign-In")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onSignInWithEmail,
                modifier = Modifier.testTag("expired_email_signin_btn")
            ) {
                Text("Email / Password")
            }
        }
    )
}
