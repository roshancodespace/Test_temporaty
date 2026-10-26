package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class IdeColorPalette(
    val background: Color,
    val sidebar: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val border: Color,
    val borderSubtle: Color,
    val primary: Color,
    val accent: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val statusSuccess: Color = Color(0xFF4CAF50),
    val statusWarning: Color = Color(0xFFCCA700),
    val statusError: Color = Color(0xFFF14C4C),
    val statusInfo: Color = Color(0xFF3794FF),
    val isDark: Boolean = true
)

// 1. VS Code Dark Modern (Newer obsidian flat grey with electric blue)
val VsCodeDarkModernPalette = IdeColorPalette(
    background = Color(0xFF181818),
    sidebar = Color(0xFF1F1F1F),
    surface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFF2B2B2B),
    border = Color(0xFF333333),
    borderSubtle = Color(0xFF252525),
    primary = Color(0xFF0078D4),
    accent = Color(0xFF29B6F6),
    secondary = Color(0xFF38BDF8),
    textPrimary = Color(0xFFCCCCCC),
    textSecondary = Color(0xFF999999),
    textMuted = Color(0xFF6E6E6E),
    statusSuccess = Color(0xFF4CAF50),
    statusWarning = Color(0xFFFFB74D),
    statusError = Color(0xFFF44336),
    isDark = true
)

// 2. VS Code Dark Classic (Classic dark purple-slate tint with cyan)
val VsCodeDarkClassicPalette = IdeColorPalette(
    background = Color(0xFF1E1E1E),
    sidebar = Color(0xFF252526),
    surface = Color(0xFF252526),
    surfaceVariant = Color(0xFF2D2D2D),
    border = Color(0xFF383838),
    borderSubtle = Color(0xFF2A2A2A),
    primary = Color(0xFF007ACC),
    accent = Color(0xFF00E5FF),
    secondary = Color(0xFF4FC1FF),
    textPrimary = Color(0xFFD4D4D4),
    textSecondary = Color(0xFF9E9E9E),
    textMuted = Color(0xFF6E7681),
    statusSuccess = Color(0xFF89D185),
    statusWarning = Color(0xFFCCA700),
    statusError = Color(0xFFF14C4C),
    isDark = true
)

// 3. GitHub Dark Modern (Deep slate blue dark theme)
val GitHubDarkPalette = IdeColorPalette(
    background = Color(0xFF0D1117),
    sidebar = Color(0xFF161B22),
    surface = Color(0xFF161B22),
    surfaceVariant = Color(0xFF21262D),
    border = Color(0xFF30363D),
    borderSubtle = Color(0xFF21262D),
    primary = Color(0xFF238636),
    accent = Color(0xFF2F81F7),
    secondary = Color(0xFF58A6FF),
    textPrimary = Color(0xFFC9D1D9),
    textSecondary = Color(0xFF8B949E),
    textMuted = Color(0xFF484F58),
    statusSuccess = Color(0xFF3FB950),
    statusWarning = Color(0xFFD29922),
    statusError = Color(0xFFF85149),
    isDark = true
)

// 4. VS Code / GitHub Clean Light
val VsCodeLightPalette = IdeColorPalette(
    background = Color(0xFFF6F8FA),
    sidebar = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEF2F6),
    border = Color(0xFFD0D7DE),
    borderSubtle = Color(0xFFE6E8EB),
    primary = Color(0xFF0969DA),
    accent = Color(0xFF0550AE),
    secondary = Color(0xFF218BFF),
    textPrimary = Color(0xFF1F2328),
    textSecondary = Color(0xFF57606A),
    textMuted = Color(0xFF8C959F),
    statusSuccess = Color(0xFF1A7F37),
    statusWarning = Color(0xFF9A6700),
    statusError = Color(0xFFCF222E),
    statusInfo = Color(0xFF0969DA),
    isDark = false
)

// 3. Midnight Studio (Deep obsidian dark with purple & indigo accent)
val MidnightDarkPalette = IdeColorPalette(
    background = Color(0xFF0F1117),
    sidebar = Color(0xFF161922),
    surface = Color(0xFF161922),
    surfaceVariant = Color(0xFF1E2230),
    border = Color(0xFF2C3144),
    borderSubtle = Color(0xFF202433),
    primary = Color(0xFF7C4DFF),
    accent = Color(0xFFB388FF),
    secondary = Color(0xFF8C9EFF),
    textPrimary = Color(0xFFE2E8F0),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    statusSuccess = Color(0xFF10B981),
    statusWarning = Color(0xFFF59E0B),
    statusError = Color(0xFFEF4444),
    isDark = true
)

// 4. Monokai Ember (Warm charcoal dark with gold & amber accent)
val MonokaiDarkPalette = IdeColorPalette(
    background = Color(0xFF222129),
    sidebar = Color(0xFF2A2833),
    surface = Color(0xFF2A2833),
    surfaceVariant = Color(0xFF33303E),
    border = Color(0xFF423E50),
    borderSubtle = Color(0xFF312E3C),
    primary = Color(0xFFFF9800),
    accent = Color(0xFFFFD54F),
    secondary = Color(0xFFFFB74D),
    textPrimary = Color(0xFFF8F8F2),
    textSecondary = Color(0xFFA6A2B3),
    textMuted = Color(0xFF797585),
    statusSuccess = Color(0xFFA6E22E),
    statusWarning = Color(0xFFFD971F),
    statusError = Color(0xFFF92672),
    isDark = true
)

// Ambient CompositionLocal
val LocalIdeColors = staticCompositionLocalOf { VsCodeDarkModernPalette }

// Seamless dynamic color accessors for Compose
val IdeBackground: Color @Composable get() = LocalIdeColors.current.background
val IdeSidebar: Color @Composable get() = LocalIdeColors.current.sidebar
val IdeSurface: Color @Composable get() = LocalIdeColors.current.surface
val IdeSurfaceVariant: Color @Composable get() = LocalIdeColors.current.surfaceVariant
val IdeBorder: Color @Composable get() = LocalIdeColors.current.border
val IdeBorderSubtle: Color @Composable get() = LocalIdeColors.current.borderSubtle
val IdePrimary: Color @Composable get() = LocalIdeColors.current.primary
val IdeAccent: Color @Composable get() = LocalIdeColors.current.accent
val IdeSecondary: Color @Composable get() = LocalIdeColors.current.secondary
val TextPrimary: Color @Composable get() = LocalIdeColors.current.textPrimary
val TextSecondary: Color @Composable get() = LocalIdeColors.current.textSecondary
val TextMuted: Color @Composable get() = LocalIdeColors.current.textMuted
val StatusSuccess: Color @Composable get() = LocalIdeColors.current.statusSuccess
val StatusWarning: Color @Composable get() = LocalIdeColors.current.statusWarning
val StatusError: Color @Composable get() = LocalIdeColors.current.statusError
val StatusInfo: Color @Composable get() = LocalIdeColors.current.statusInfo

val DefaultTextPrimary = Color(0xFFD4D4D4)

// Tokens for Syntax Highlighting
val SyntaxKeyword = Color(0xFF569CD6)
val SyntaxType = Color(0xFF4EC9B0)
val SyntaxString = Color(0xFFCE9178)
val SyntaxComment = Color(0xFF6A9955)
val SyntaxNumber = Color(0xFFB5CEA8)
val SyntaxAnnotation = Color(0xFFDCDCAA)
val SyntaxPunctuation = Color(0xFFD4D4D4)
val SyntaxIdentifier = Color(0xFF9CDCFE)
val SyntaxFunction = Color(0xFFDCDCAA)
