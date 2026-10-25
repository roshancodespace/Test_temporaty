package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.HumanTypingSimulator
import com.example.data.storage.TypingCheckpoint
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdePrimary
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.IdeSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun TypingControlCard(
    typingState: HumanTypingSimulator.TypingState,
    speedCps: Int,
    linePauseMs: Long,
    jitterEnabled: Boolean,
    checkpoint: TypingCheckpoint?,
    onStartTyping: () -> Unit,
    onResumeFromCheckpoint: () -> Unit,
    onPauseTyping: () -> Unit,
    onResumeTyping: () -> Unit,
    onStopTyping: () -> Unit,
    onOpenCadenceSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTyping = typingState is HumanTypingSimulator.TypingState.Typing
    val isPaused = typingState is HumanTypingSimulator.TypingState.Paused
    val isActive = isTyping || isPaused

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("typing_control_card")
    ) {
        // Active Transmission Status Banner
        AnimatedVisibility(visible = isActive) {
            val currentFile = when (typingState) {
                is HumanTypingSimulator.TypingState.Typing -> typingState.currentFile
                is HumanTypingSimulator.TypingState.Paused -> typingState.currentFile
                else -> ""
            }
            val progress = when (typingState) {
                is HumanTypingSimulator.TypingState.Typing -> typingState.progress
                is HumanTypingSimulator.TypingState.Paused -> typingState.progress
                else -> 0f
            }
            val charsTyped = when (typingState) {
                is HumanTypingSimulator.TypingState.Typing -> typingState.charsTyped
                is HumanTypingSimulator.TypingState.Paused -> typingState.charsTyped
                else -> 0
            }
            val totalChars = when (typingState) {
                is HumanTypingSimulator.TypingState.Typing -> typingState.totalChars
                is HumanTypingSimulator.TypingState.Paused -> typingState.totalChars
                else -> 0
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(IdeSurface.copy(alpha = 0.6f))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPaused) "PAUSED" else "TRANSMITTING TO PC",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = if (isPaused) StatusWarning else IdeAccent,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Text(
                        text = "$charsTyped / $totalChars chars (${(progress * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = currentFile,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = IdeAccent,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    color = IdeAccent,
                    trackColor = IdeSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }

        if (isActive) {
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Primary Start / Controls
        if (!isActive) {
            if (checkpoint != null) {
                // Persistent Checkpoint Detected -> Give option to resume or restart
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Checkpoint Info Ribbon
                    Surface(
                        color = IdeSurfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = StatusWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Checkpoint Memory: ${checkpoint.fileRelativePath}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${checkpoint.charsTyped}/${checkpoint.totalChars} chars typed (${(checkpoint.charsTyped.toFloat() / checkpoint.totalChars.coerceAtLeast(1) * 100).toInt()}%)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onResumeFromCheckpoint,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = IdeAccent,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp)
                                .testTag("resume_checkpoint_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RESUME CHECKPOINT",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        OutlinedButton(
                            onClick = onStartTyping,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Start Fresh", fontSize = 11.5.sp)
                        }
                    }
                }
            } else {
                Button(
                    onClick = onStartTyping,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IdeAccent,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("start_pc_typing_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "START HUMAN TYPING INTO PC",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isPaused) {
                    Button(
                        onClick = onResumeTyping,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IdeAccent,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Resume Typing", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = onPauseTyping,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusWarning),
                        modifier = Modifier
                            .weight(1.5f)
                            .height(44.dp)
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pause Typing", fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onStopTyping,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusError,
                        contentColor = TextPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stop", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cadence & Rhythm Info Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable { onOpenCadenceSettings() }
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$speedCps cps (~${speedCps * 60 / 5} wpm)",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = TextSecondary
                )
                Text(text = "•", color = TextMuted)
                Text(
                    text = "${linePauseMs}ms pause",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Text(text = "•", color = TextMuted)
                Text(
                    text = if (jitterEnabled) "Human Jitter" else "Fixed Cadence",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (jitterEnabled) IdeAccent else TextMuted
                )
            }

            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Adjust Cadence & Theme",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
