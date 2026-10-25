package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    DARK("Dark"),
    LIGHT("Light")
}

enum class PalettePreset(val label: String) {
    VS_CODE("VS Code Classic"),
    GITHUB("GitHub Modern"),
    MIDNIGHT("Midnight Studio"),
    MONOKAI("Monokai Ember")
}

val IdeShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun CodeFlowTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palettePreset: PalettePreset = PalettePreset.VS_CODE,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemInDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val idePalette = if (isDark) {
        when (palettePreset) {
            PalettePreset.VS_CODE -> VsCodeDarkPalette
            PalettePreset.GITHUB -> VsCodeDarkPalette
            PalettePreset.MIDNIGHT -> MidnightDarkPalette
            PalettePreset.MONOKAI -> MonokaiDarkPalette
        }
    } else {
        VsCodeLightPalette
    }

    val materialColorScheme = if (isDark) {
        darkColorScheme(
            primary = idePalette.primary,
            onPrimary = idePalette.textPrimary,
            primaryContainer = idePalette.surfaceVariant,
            onPrimaryContainer = idePalette.textPrimary,
            secondary = idePalette.secondary,
            onSecondary = idePalette.background,
            secondaryContainer = idePalette.surface,
            onSecondaryContainer = idePalette.textPrimary,
            tertiary = idePalette.accent,
            background = idePalette.background,
            onBackground = idePalette.textPrimary,
            surface = idePalette.surface,
            onSurface = idePalette.textPrimary,
            surfaceVariant = idePalette.surfaceVariant,
            onSurfaceVariant = idePalette.textSecondary,
            outline = idePalette.border,
            outlineVariant = idePalette.borderSubtle
        )
    } else {
        lightColorScheme(
            primary = idePalette.primary,
            onPrimary = idePalette.background,
            primaryContainer = idePalette.surfaceVariant,
            onPrimaryContainer = idePalette.textPrimary,
            secondary = idePalette.secondary,
            onSecondary = idePalette.textPrimary,
            secondaryContainer = idePalette.surfaceVariant,
            onSecondaryContainer = idePalette.textPrimary,
            tertiary = idePalette.accent,
            background = idePalette.background,
            onBackground = idePalette.textPrimary,
            surface = idePalette.surface,
            onSurface = idePalette.textPrimary,
            surfaceVariant = idePalette.surfaceVariant,
            onSurfaceVariant = idePalette.textSecondary,
            outline = idePalette.border,
            outlineVariant = idePalette.borderSubtle
        )
    }

    CompositionLocalProvider(LocalIdeColors provides idePalette) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            shapes = IdeShapes,
            content = content
        )
    }
}
