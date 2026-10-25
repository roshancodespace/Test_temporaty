package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.HumanTypingSimulator
import com.example.data.model.PlaybackSpeed
import com.example.data.model.PlaybackState
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdeBorder
import com.example.ui.theme.IdePrimary
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun PlaybackControlHud(
    playbackState: PlaybackState,
    speed: PlaybackSpeed,
    isDistractionFree: Boolean,
    bluetoothTypingState: HumanTypingSimulator.TypingState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRestart: () -> Unit,
    onSeek: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onToggleDistractionFree: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBluetoothModal: () -> Unit,
    onPauseBluetoothTyping: () -> Unit,
    onResumeBluetoothTyping: () -> Unit,
    onStopBluetoothTyping: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = when (playbackState) {
        is PlaybackState.Playing -> playbackState.progress
        is PlaybackState.Paused -> playbackState.progress
        is PlaybackState.Completed -> 1.0f
        else -> 0.0f
    }

    val elapsedMs = when (playbackState) {
        is PlaybackState.Playing -> playbackState.elapsedMs
        is PlaybackState.Paused -> playbackState.elapsedMs
        is PlaybackState.Completed -> playbackState.durationMs
        else -> 0L
    }

    val remainingMs = when (playbackState) {
        is PlaybackState.Playing -> playbackState.estimatedRemainingMs
        is PlaybackState.Paused -> playbackState.estimatedRemainingMs
        else -> 0L
    }

    val isPlaying = playbackState is PlaybackState.Playing
    val isBtActive = bluetoothTypingState is HumanTypingSimulator.TypingState.Typing ||
            bluetoothTypingState is HumanTypingSimulator.TypingState.Paused

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(IdeSurface)
            .border(width = 1.dp, color = IdeBorder)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("playback_control_hud")
    ) {
        // Active Bluetooth Typing Status Banner
        AnimatedVisibility(visible = isBtActive) {
            val (statusText, fileProgress) = when (bluetoothTypingState) {
                is HumanTypingSimulator.TypingState.Typing -> {
                    Pair(
                        "Typing into PC: ${bluetoothTypingState.currentFile} (${(bluetoothTypingState.progress * 100).toInt()}%)",
                        bluetoothTypingState.progress
                    )
                }
                is HumanTypingSimulator.TypingState.Paused -> {
                    Pair(
                        "PAUSED PC Typing: ${bluetoothTypingState.currentFile}",
                        bluetoothTypingState.progress
                    )
                }
                else -> Pair("", 0f)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IdeAccent.copy(alpha = 0.15f))
                    .border(1.dp, IdeAccent.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        tint = IdeAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        ),
                        color = IdeAccent
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bluetoothTypingState is HumanTypingSimulator.TypingState.Paused) {
                        IconButton(onClick = onResumeBluetoothTyping, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = IdeAccent)
                        }
                    } else {
                        IconButton(onClick = onPauseBluetoothTyping, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = StatusSuccess)
                        }
                    }
                    IconButton(onClick = onStopBluetoothTyping, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = StatusError)
                    }
                }
            }
        }

        // Scrubber Slider for local playback
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatDuration(elapsedMs),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = TextSecondary
            )

            Slider(
                value = progress,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = IdeAccent,
                    activeTrackColor = IdePrimary,
                    inactiveTrackColor = IdeBorder
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .padding(horizontal = 8.dp)
                    .testTag("playback_seek_slider")
            )

            Text(
                text = if (remainingMs > 0) "-${formatDuration(remainingMs)}" else "00:00",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = TextMuted
            )
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Speed Chip Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.testTag("speed_cycle_button")
            ) {
                IconButton(
                    onClick = onCycleSpeed,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Playback Speed",
                        tint = IdeAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = speed.label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = IdeAccent
                )
            }

            // Primary Controls (Restart, Play/Pause)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("restart_playback_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = "Restart",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                FilledIconButton(
                    onClick = {
                        if (isPlaying) onPause() else onPlay()
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = IdePrimary,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Direct Bluetooth PC Output Trigger
                Button(
                    onClick = onOpenBluetoothModal,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBtActive) StatusSuccess else IdeAccent,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("bluetooth_pc_trigger_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBtActive) "TYPING..." else "TYPE TO PC",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Mode & Settings Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleDistractionFree,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("fullscreen_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDistractionFree) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = "Distraction-Free Mode",
                        tint = if (isDistractionFree) IdeAccent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Session Settings",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun formatDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
