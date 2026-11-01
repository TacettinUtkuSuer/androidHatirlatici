package com.utkusuer.hatirlatici.ui.theme

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

// Monokrom palet + tek, kısık bir vurgu (arduvaz mavisi). Uyarı rengi yalnızca gecikmede.
private val Ink = Color(0xFF15171A)
private val Graphite = Color(0xFF2A2D31)
private val Slate = Color(0xFF5B6168)
private val Mist = Color(0xFFE4E5E3)
private val Paper = Color(0xFFF6F6F4)
private val Accent = Color(0xFF3E5368)
private val AccentDark = Color(0xFFA9BACB)
private val Overdue = Color(0xFF9A3B32)

private val LightColors = lightColorScheme(
    primary = Ink, onPrimary = Paper,
    secondary = Accent, onSecondary = Paper,
    tertiary = Accent,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Mist, onSurfaceVariant = Slate,
    surfaceContainer = Color(0xFFEDEDEB),
    outline = Color(0xFFC9CBCB), outlineVariant = Mist,
    error = Overdue,
)

private val DarkColors = darkColorScheme(
    primary = Paper, onPrimary = Ink,
    secondary = AccentDark, onSecondary = Ink,
    tertiary = AccentDark,
    background = Color(0xFF0F1012), onBackground = Color(0xFFE8E8E6),
    surface = Color(0xFF0F1012), onSurface = Color(0xFFE8E8E6),
    surfaceVariant = Graphite, onSurfaceVariant = Color(0xFF9EA3A9),
    surfaceContainer = Color(0xFF1A1C1F),
    outline = Color(0xFF3A3D42), outlineVariant = Graphite,
    error = Color(0xFFE0A49C),
)

private val AppTypography = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
fun HatirlaticiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
