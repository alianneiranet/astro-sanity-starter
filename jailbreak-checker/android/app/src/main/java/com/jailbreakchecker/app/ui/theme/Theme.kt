package com.jailbreakchecker.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CC4FF),
    onPrimary = Color(0xFF00213A),
    secondary = Color(0xFFB4C8E0),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E9EF),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE6E9EF),
    surfaceVariant = Color(0xFF1B2028),
    onSurfaceVariant = Color(0xFFAEB6C2),
    surfaceContainer = Color(0xFF161A21),
    surfaceContainerHigh = Color(0xFF1D222B),
    surfaceContainerHighest = Color(0xFF252B35),
    outline = Color(0xFF3A424E),
    outlineVariant = Color(0xFF2A303A),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0061A4),
    onPrimary = Color.White,
    secondary = Color(0xFF526070),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF14181E),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF14181E),
    surfaceVariant = Color(0xFFE6E9EE),
    onSurfaceVariant = Color(0xFF4A525E),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F2F5),
    surfaceContainerHighest = Color(0xFFE8EBEF),
    outline = Color(0xFFC3C9D2),
    outlineVariant = Color(0xFFDCE0E6),
)

private val AppTypography = Typography().run {
    copy(
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    )
}

@Composable
fun JailbreakTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
