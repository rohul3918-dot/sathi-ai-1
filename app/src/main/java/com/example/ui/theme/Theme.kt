package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = BiolumCyan,
    onPrimary = BiolumCyanDark,
    primaryContainer = BiolumCyanContainer,
    onPrimaryContainer = BiolumCyanOnContainer,
    secondary = WarmAmber,
    onSecondary = WarmAmberDark,
    secondaryContainer = WarmAmberContainer,
    onSecondaryContainer = WarmAmberOnContainer,
    tertiary = SoftPeriwinkle,
    onTertiary = SoftPeriwinkleDark,
    background = MidnightBg,
    onBackground = Color(0xFFEAF2FF),
    surface = MidnightSurface,
    onSurface = Color(0xFFEAF2FF),
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = Color(0xFFAEC0DE),
    outline = MidnightOutline,
    error = CrimsonCoral,
    onError = Color(0xFF3E000A)
)

private val LightColorScheme = lightColorScheme(
    primary = TealPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = TealContainerLight,
    onPrimaryContainer = TealOnContainerLight,
    secondary = AmberSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = AmberContainerLight,
    onSecondaryContainer = AmberOnContainerLight,
    tertiary = Color(0xFF3E52B5),
    onTertiary = Color.White,
    background = DaylightBg,
    onBackground = Color(0xFF0E1B2E),
    surface = DaylightSurface,
    onSurface = Color(0xFF0E1B2E),
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = Color(0xFF425470),
    outline = DaylightOutline,
    error = Color(0xFFC62828),
    onError = Color.White
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun SathiAiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
