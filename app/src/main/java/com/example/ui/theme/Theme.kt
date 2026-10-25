package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkIdeColorScheme = darkColorScheme(
    primary = IdePrimary,
    onPrimary = TextPrimary,
    primaryContainer = IdeSurfaceVariant,
    onPrimaryContainer = TextPrimary,
    secondary = IdeSecondary,
    onSecondary = IdeBackground,
    secondaryContainer = IdeSurface,
    onSecondaryContainer = TextPrimary,
    tertiary = IdeAccent,
    background = IdeBackground,
    onBackground = TextPrimary,
    surface = IdeSidebar,
    onSurface = TextPrimary,
    surfaceVariant = IdeSurface,
    onSurfaceVariant = TextSecondary,
    outline = IdeBorder,
    outlineVariant = IdeBorderSubtle
)

val IdeShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

@Composable
fun CodeFlowTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkIdeColorScheme,
        typography = Typography,
        shapes = IdeShapes,
        content = content
    )
}
