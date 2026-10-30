package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.model.AppThemeId
import com.example.model.AppearanceMode

/**
 * Extra semantic tokens exposed across all screens, charts, keypads, cards, and inputs.
 */
data class NumeraExtendedColors(
    val isDark: Boolean,
    val background: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val primaryAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color
)

val LocalNumeraColors = staticCompositionLocalOf {
    NumeraExtendedColors(
        isDark = false,
        background = NumeraLightBg,
        surfaceCard = NumeraCardLight,
        surfaceElevated = NumeraLightElevated,
        borderSubtle = NumeraLightBorder,
        textPrimary = NumeraDark,
        textSecondary = NumeraMedium,
        textDisabled = Color(0xFF90A0AC),
        primaryAccent = NumeraPrimary,
        success = NumeraSuccess,
        warning = NumeraWarning,
        danger = NumeraDanger
    )
}

fun resolveIsDarkMode(
    appearanceMode: AppearanceMode,
    accentThemeId: AppThemeId,
    systemDark: Boolean
): Boolean {
    return when (appearanceMode) {
        AppearanceMode.DARK -> true
        AppearanceMode.LIGHT -> false
        AppearanceMode.SYSTEM -> systemDark
    }
}

private data class AccentPalette(
    val lightPrimary: Color,
    val lightSecondary: Color,
    val lightTertiary: Color,
    val lightContainer: Color,
    val lightBg: Color,
    val darkPrimary: Color,
    val darkSecondary: Color,
    val darkContainer: Color
)

private fun accentPaletteFor(themeId: AppThemeId): AccentPalette {
    return when (themeId) {
        AppThemeId.LIGHT, AppThemeId.DARK, AppThemeId.BLACK_AMOLED -> AccentPalette(
            lightPrimary = NumeraPrimary,
            lightSecondary = NumeraSecondary,
            lightTertiary = NumeraTertiary,
            lightContainer = Color(0xFFD6EEF8),
            lightBg = NumeraLightBg,
            darkPrimary = SoftDarkPrimaryAccent,
            darkSecondary = Color(0xFF7EC0DF),
            darkContainer = Color(0xFF1F384A)
        )
        AppThemeId.DARK_GREEN, AppThemeId.LIGHT_GREEN -> AccentPalette(
            lightPrimary = MintPrimary,
            lightSecondary = Color(0xFF42B097),
            lightTertiary = Color(0xFF0D5948),
            lightContainer = Color(0xFFC8F0E6),
            lightBg = MintLightBg,
            darkPrimary = SoftDarkSuccess,
            darkSecondary = Color(0xFF6AD1BA),
            darkContainer = Color(0xFF1D3B35)
        )
        AppThemeId.DARK_BLUE -> AccentPalette(
            lightPrimary = Color(0xFF2868B5),
            lightSecondary = Color(0xFF5792D9),
            lightTertiary = Color(0xFF123B6E),
            lightContainer = Color(0xFFD6E6FA),
            lightBg = Color(0xFFEDF2F8),
            darkPrimary = NavyPrimary,
            darkSecondary = Color(0xFF8AB8F0),
            darkContainer = Color(0xFF1E324D)
        )
        AppThemeId.DARK_PURPLE, AppThemeId.LIGHT_PURPLE -> AccentPalette(
            lightPrimary = LavenderPrimary,
            lightSecondary = Color(0xFF8F65C9),
            lightTertiary = Color(0xFF441E75),
            lightContainer = Color(0xFFE4D6FF),
            lightBg = LavenderLightBg,
            darkPrimary = VioletPrimary,
            darkSecondary = Color(0xFFB99DF5),
            darkContainer = Color(0xFF2B2442)
        )
        AppThemeId.LIGHT_RED -> AccentPalette(
            lightPrimary = CoralPrimary,
            lightSecondary = Color(0xFFE86F7C),
            lightTertiary = Color(0xFF7D1924),
            lightContainer = Color(0xFFFFD9DD),
            lightBg = CoralLightBg,
            darkPrimary = CoralDarkPrimary,
            darkSecondary = Color(0xFFEB8F97),
            darkContainer = Color(0xFF3D2329)
        )
        AppThemeId.LIGHT_ORANGE -> AccentPalette(
            lightPrimary = SolarPrimary,
            lightSecondary = Color(0xFFF29B4B),
            lightTertiary = Color(0xFF7A3E06),
            lightContainer = Color(0xFFFFE3C7),
            lightBg = SolarLightBg,
            darkPrimary = SolarDarkPrimary,
            darkSecondary = Color(0xFFEDC26B),
            darkContainer = Color(0xFF3B301E)
        )
    }
}

fun colorSchemeForTheme(
    themeId: AppThemeId,
    isDark: Boolean = themeId.isDark
): ColorScheme {
    val accent = accentPaletteFor(themeId)
    return if (isDark) {
        // Soft Muted Blue-Grey Dark Palette (#151B21 bg, #1E262E surface, #26303A elevated, #313C47 border, #E3E9EE text, #9AA8B5 secondary)
        darkColorScheme(
            primary = accent.darkPrimary,
            onPrimary = Color(0xFF0A1922),
            primaryContainer = accent.darkContainer,
            onPrimaryContainer = SoftDarkTextPrimary,
            secondary = accent.darkSecondary,
            onSecondary = Color(0xFF0A1922),
            secondaryContainer = SoftDarkElevated,
            onSecondaryContainer = SoftDarkTextPrimary,
            tertiary = SoftDarkWarning,
            onTertiary = Color(0xFF1C1404),
            background = SoftDarkBg,
            onBackground = SoftDarkTextPrimary,
            surface = SoftDarkSurface,
            onSurface = SoftDarkTextPrimary,
            surfaceVariant = SoftDarkElevated,
            onSurfaceVariant = SoftDarkTextSecondary,
            surfaceContainerLowest = SoftDarkBg,
            surfaceContainerLow = SoftDarkSurface,
            surfaceContainer = SoftDarkSurface,
            surfaceContainerHigh = SoftDarkElevated,
            surfaceContainerHighest = SoftDarkElevated,
            outline = SoftDarkBorder,
            outlineVariant = SoftDarkBorder.copy(alpha = 0.75f),
            error = SoftDarkDanger,
            onError = Color(0xFF1F090C)
        )
    } else {
        lightColorScheme(
            primary = accent.lightPrimary,
            onPrimary = Color.White,
            primaryContainer = accent.lightContainer,
            onPrimaryContainer = accent.lightTertiary,
            secondary = accent.lightSecondary,
            onSecondary = NumeraDark,
            secondaryContainer = NumeraLightElevated,
            onSecondaryContainer = NumeraDark,
            tertiary = accent.lightTertiary,
            onTertiary = Color.White,
            background = accent.lightBg,
            onBackground = NumeraDark,
            surface = NumeraCardLight,
            onSurface = NumeraDark,
            surfaceVariant = NumeraLightElevated,
            onSurfaceVariant = NumeraMedium,
            surfaceContainerLowest = NumeraCardLight,
            surfaceContainerLow = NumeraCardLight,
            surfaceContainer = NumeraCardLight,
            surfaceContainerHigh = NumeraLightElevated,
            surfaceContainerHighest = NumeraLightElevated,
            outline = NumeraLightBorder,
            outlineVariant = NumeraLightBorder.copy(alpha = 0.75f),
            error = NumeraDanger,
            onError = Color.White
        )
    }
}

@Composable
private fun animateColor(target: Color): Color {
    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 200),
        label = "theme_color_transition"
    ).value
}

@Composable
fun NumeraTheme(
    appearanceMode: AppearanceMode = AppearanceMode.LIGHT,
    themeId: AppThemeId = AppThemeId.LIGHT,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val isDark = resolveIsDarkMode(appearanceMode, themeId, systemDark)
    val targetScheme = colorSchemeForTheme(themeId = themeId, isDark = isDark)

    // Smooth 200ms transition across theme switches
    val animatedScheme = targetScheme.copy(
        primary = animateColor(targetScheme.primary),
        onPrimary = animateColor(targetScheme.onPrimary),
        primaryContainer = animateColor(targetScheme.primaryContainer),
        onPrimaryContainer = animateColor(targetScheme.onPrimaryContainer),
        secondary = animateColor(targetScheme.secondary),
        onSecondary = animateColor(targetScheme.onSecondary),
        secondaryContainer = animateColor(targetScheme.secondaryContainer),
        onSecondaryContainer = animateColor(targetScheme.onSecondaryContainer),
        tertiary = animateColor(targetScheme.tertiary),
        background = animateColor(targetScheme.background),
        onBackground = animateColor(targetScheme.onBackground),
        surface = animateColor(targetScheme.surface),
        onSurface = animateColor(targetScheme.onSurface),
        surfaceVariant = animateColor(targetScheme.surfaceVariant),
        onSurfaceVariant = animateColor(targetScheme.onSurfaceVariant),
        surfaceContainerLowest = animateColor(targetScheme.surfaceContainerLowest),
        surfaceContainerLow = animateColor(targetScheme.surfaceContainerLow),
        surfaceContainer = animateColor(targetScheme.surfaceContainer),
        surfaceContainerHigh = animateColor(targetScheme.surfaceContainerHigh),
        surfaceContainerHighest = animateColor(targetScheme.surfaceContainerHighest),
        outline = animateColor(targetScheme.outline),
        outlineVariant = animateColor(targetScheme.outlineVariant),
        error = animateColor(targetScheme.error)
    )

    val extendedColors = NumeraExtendedColors(
        isDark = isDark,
        background = animatedScheme.background,
        surfaceCard = animatedScheme.surface,
        surfaceElevated = animatedScheme.surfaceVariant,
        borderSubtle = animatedScheme.outline,
        textPrimary = animatedScheme.onSurface,
        textSecondary = animatedScheme.onSurfaceVariant,
        textDisabled = if (isDark) SoftDarkTextDisabled else Color(0xFF90A0AC),
        primaryAccent = animatedScheme.primary,
        success = SoftDarkSuccess,
        warning = SoftDarkWarning,
        danger = SoftDarkDanger
    )

    // Sync Android status bar and navigation bar colors + icon brightness with active theme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            runCatching {
                @Suppress("DEPRECATION")
                window.statusBarColor = targetScheme.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = targetScheme.surface.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    CompositionLocalProvider(LocalNumeraColors provides extendedColors) {
        MaterialTheme(
            colorScheme = animatedScheme,
            typography = Typography,
            content = content
        )
    }
}
