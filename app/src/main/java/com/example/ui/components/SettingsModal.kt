package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.storage.TypingCheckpoint
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdeBorder
import com.example.ui.theme.IdeBorderSubtle
import com.example.ui.theme.IdePrimary
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.IdeSurfaceVariant
import com.example.ui.theme.PalettePreset
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ThemeMode

@Composable
fun SettingsModal(
    cps: Int,
    lineEndPauseMs: Long,
    randomJitter: Boolean,
    countdownSec: Int,
    themeMode: ThemeMode,
    palettePreset: PalettePreset,
    checkpoint: TypingCheckpoint?,
    keepScreenAwake: Boolean = true,
    currentBtName: String = "Android Device",
    originalBtName: String = "Android Device",
    isBtNameSpoofed: Boolean = false,
    onCpsChange: (Int) -> Unit,
    onLineEndPauseChange: (Long) -> Unit,
    onToggleJitter: () -> Unit,
    onCountdownSecChange: (Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPalettePresetChange: (PalettePreset) -> Unit,
    onToggleKeepScreenAwake: (Boolean) -> Unit = {},
    onSpoofBtName: (String) -> Unit = {},
    onRestoreBtName: () -> Unit = {},
    onClearCheckpoint: () -> Unit,
    onResetDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var btNameInput by remember(currentBtName) { mutableStateOf(currentBtName) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 520.dp)
                .fillMaxWidth(0.92f)
                .testTag("settings_modal"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = IdeSurface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(IdeSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = IdeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Settings & Preferences",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Theme, Cadence & Memory Checkpoints",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = IdeBorderSubtle, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // ==================== SECTION 1: THEME SETTINGS ====================
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = IdeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "THEME & APPEARANCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = IdeAccent
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Theme Mode Selector (System / Dark / Light)
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        Triple(ThemeMode.SYSTEM, "System", Icons.Default.BrightnessMedium),
                        Triple(ThemeMode.DARK, "Dark", Icons.Default.DarkMode),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Default.LightMode)
                    )

                    modes.forEach { (mode, label, icon) ->
                        val isSelected = themeMode == mode
                        Surface(
                            onClick = { onThemeModeChange(mode) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) IdePrimary.copy(alpha = 0.2f) else IdeSurfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) IdeAccent else IdeBorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) IdeAccent else TextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isSelected) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Accent / Palette Preset Selector
                Text(
                    text = "IDE Style & Accent Palette",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val palettes = listOf(
                        Triple(PalettePreset.VS_CODE, "VS Code Classic", Color(0xFF00E5FF)),
                        Triple(PalettePreset.GITHUB, "GitHub Modern", Color(0xFF0969DA)),
                        Triple(PalettePreset.MIDNIGHT, "Midnight Studio", Color(0xFFB388FF)),
                        Triple(PalettePreset.MONOKAI, "Monokai Ember", Color(0xFFFFD54F))
                    )

                    palettes.forEach { (preset, label, accentColor) ->
                        val isSelected = palettePreset == preset
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) IdeSurfaceVariant else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) accentColor else IdeBorderSubtle,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onPalettePresetChange(preset) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = TextPrimary
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = accentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = IdeBorderSubtle, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // ==================== SECTION 2: CADENCE SETTINGS ====================
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = IdeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "KEYSTROKE CADENCE & RHYTHM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = IdeAccent
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Typing Speed (CPS)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Typing Speed",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Characters per second transmitted to PC",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        val wpm = (cps * 60 / 5)
                        Text(
                            text = "$cps cps (~$wpm wpm)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = IdeAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = cps.toFloat(),
                        onValueChange = { onCpsChange(it.toInt()) },
                        valueRange = 10f..80f,
                        colors = SliderDefaults.colors(
                            thumbColor = IdeAccent,
                            activeTrackColor = IdePrimary,
                            inactiveTrackColor = IdeSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Line-End Pause
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Line-End Pause",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Pause after newlines (\\n)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "${lineEndPauseMs} ms",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = IdeAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = lineEndPauseMs.toFloat(),
                        onValueChange = { onLineEndPauseChange(it.toLong()) },
                        valueRange = 0f..500f,
                        colors = SliderDefaults.colors(
                            thumbColor = IdeAccent,
                            activeTrackColor = IdePrimary,
                            inactiveTrackColor = IdeSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Focus Countdown
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Focus Countdown",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Delay to focus VS Code window on PC",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Text(
                            text = "${countdownSec}s countdown",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            ),
                            color = IdeAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = countdownSec.toFloat(),
                        onValueChange = { onCountdownSecChange(it.toInt()) },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = IdeAccent,
                            activeTrackColor = IdePrimary,
                            inactiveTrackColor = IdeSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Natural Jitter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Natural Human Jitter",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Gaussian micro-timing variance between keys",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = randomJitter,
                        onCheckedChange = { onToggleJitter() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = IdeAccent,
                            checkedTrackColor = IdePrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = IdeSurfaceVariant
                        )
                    )
                }

                // ==================== SECTION 3: SCREEN & BLUETOOTH IDENTITY ====================
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = IdeBorderSubtle, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.StayCurrentPortrait,
                        contentDescription = null,
                        tint = IdeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SCREEN AWAKE & BLUETOOTH SPOOFING",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = IdeAccent
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Keep Screen Awake Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Keep Screen Awake During Typing",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Prevents screen timeout while streaming. (Background WakeLock also keeps typing alive when screen is off).",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = keepScreenAwake,
                        onCheckedChange = { onToggleKeepScreenAwake(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = IdeAccent,
                            checkedTrackColor = IdePrimary,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = IdeSurfaceVariant
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bluetooth Name Spoofing Quick Control
                Surface(
                    color = IdeSurfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = null,
                                    tint = IdeAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Advertised Name: $currentBtName",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                            }
                            if (isBtNameSpoofed) {
                                Text(
                                    text = "Spoofed",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = StatusSuccess,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = btNameInput,
                                onValueChange = { btNameInput = it },
                                label = { Text("Spoof Keyboard Name", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = IdeSurface,
                                    unfocusedContainerColor = IdeSurface,
                                    focusedIndicatorColor = IdeAccent,
                                    unfocusedIndicatorColor = IdeBorderSubtle
                                )
                            )
                            Button(
                                onClick = { onSpoofBtName(btNameInput) },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = IdeAccent,
                                    contentColor = Color.Black
                                ),
                                enabled = btNameInput.isNotBlank() && btNameInput.trim() != currentBtName,
                                modifier = Modifier.height(46.dp)
                            ) {
                                Text("Spoof", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }

                        if (isBtNameSpoofed || originalBtName != currentBtName) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        onRestoreBtName()
                                        btNameInput = originalBtName
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Undo,
                                        contentDescription = null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Restore '$originalBtName'",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // ==================== SECTION 4: PERSISTENCE MEMORY ====================
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = IdeBorderSubtle, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = IdeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PERSISTENCE & CHECKPOINT MEMORY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = IdeAccent
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (checkpoint != null) {
                    Surface(
                        color = IdeSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Saved Checkpoint Active",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = StatusWarning
                                )
                                TextButton(
                                    onClick = onClearCheckpoint,
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = "Clear",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear Checkpoint", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                            }
                            Text(
                                text = "File: ${checkpoint.fileRelativePath} (char ${checkpoint.charOffset})",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = TextPrimary
                            )
                            Text(
                                text = "${checkpoint.charsTyped}/${checkpoint.totalChars} characters transmitted so far.",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                } else {
                    Text(
                        text = "No saved checkpoint. Typing starts fresh from file 1. When paused or stopped in the middle, your position is automatically remembered here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = IdeBorderSubtle, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onResetDefaults,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reset Cadence", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IdePrimary,
                            contentColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text("Done", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
