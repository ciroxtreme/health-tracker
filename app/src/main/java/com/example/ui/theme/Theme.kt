package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Harmonized Color System for Both Dark & Light Modes
data class HarmonizedColors(
    val isDark: Boolean,
    val background: Color,
    val cardBackground: Color,
    val cardBorder: Color,
    val headerBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val pillActiveBg: Color,
    val pillActiveText: Color,
    val pillInactiveBg: Color,
    val pillInactiveText: Color,
    val infoBlueBg: Color,
    val infoBlueText: Color,
    val infoPurpleBg: Color,
    val infoPurpleText: Color,
    val amberBorder: Color,
    val amberBg: Color
)

val LightHarmonizedColors = HarmonizedColors(
    isDark = false,
    background = Color(0xFFF8FAFC),
    cardBackground = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE2E8F0),
    headerBackground = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF94A3B8),
    pillActiveBg = Color(0xFF0F172A),
    pillActiveText = Color(0xFFFFFFFF),
    pillInactiveBg = Color(0xFFF1F5F9),
    pillInactiveText = Color(0xFF334155),
    infoBlueBg = Color(0xFFF0F7FF),
    infoBlueText = Color(0xFF1E40AF),
    infoPurpleBg = Color(0xFFF5F3FF),
    infoPurpleText = Color(0xFF5B21B6),
    amberBorder = Color(0xFFFDE68A),
    amberBg = Color(0xFFFEF3C7)
)

val DarkHarmonizedColors = HarmonizedColors(
    isDark = true,
    background = Color(0xFF090D16),
    cardBackground = Color(0xFF111827),
    cardBorder = Color(0xFF1F293D),
    headerBackground = Color(0xFF111827),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    pillActiveBg = Color(0xFF38BDF8),
    pillActiveText = Color(0xFF0F172A),
    pillInactiveBg = Color(0xFF1E293B),
    pillInactiveText = Color(0xFFCBD5E1),
    infoBlueBg = Color(0xFF172554),
    infoBlueText = Color(0xFF93C5FD),
    infoPurpleBg = Color(0xFF2E1065),
    infoPurpleText = Color(0xFFD8B4FE),
    amberBorder = Color(0xFF78350F),
    amberBg = Color(0xFF451A03)
)

val LocalHarmonizedColors = staticCompositionLocalOf { LightHarmonizedColors }

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF042F2E),
    background = Color(0xFF090D16),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF1E293B),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC),
    outline = Color(0xFF1F293D)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0F172A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E8F0),
    onPrimaryContainer = Color(0xFF0F172A),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F5F9),
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A),
    outline = Color(0xFFE2E8F0)
)

@Composable
fun HealthTrackerTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val harmonized = if (darkTheme) DarkHarmonizedColors else LightHarmonizedColors

    CompositionLocalProvider(LocalHarmonizedColors provides harmonized) {
        MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
    }
}
