package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bluetooth.BluetoothHidKeyboardManager
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdeBorderSubtle
import com.example.ui.theme.IdePrimary
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.IdeSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BluetoothKeyboardModal(
    hidState: BluetoothHidKeyboardManager.HidState,
    onStartTypingIntoPc: () -> Unit,
    onEmergencyHalt: () -> Unit,
    onEnableSimulation: () -> Unit,
    onRetryBluetooth: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 320.dp, max = 500.dp)
                .fillMaxWidth(0.92f)
                .testTag("bluetooth_keyboard_modal"),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = IdeSurface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Unified Header
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
                                .clip(RoundedCornerShape(6.dp))
                                .background(IdeSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = IdeAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Bluetooth PC Keyboard",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Humanized keystroke transmission to PC VS Code",
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
                Spacer(modifier = Modifier.height(14.dp))

                // Connection Status Strip
                val statusInfo = when (hidState) {
                    is BluetoothHidKeyboardManager.HidState.Connected -> StatusDisplayData(
                        icon = Icons.Default.BluetoothConnected,
                        color = StatusSuccess,
                        title = "CONNECTED TO PC",
                        subtitle = "${hidState.deviceName} (${hidState.deviceAddress})"
                    )
                    is BluetoothHidKeyboardManager.HidState.WaitingForHost -> StatusDisplayData(
                        icon = Icons.Default.Bluetooth,
                        color = IdeAccent,
                        title = "READY TO PAIR WITH PC",
                        subtitle = "Pair in PC Bluetooth settings as 'CodeFlow Studio Keyboard'"
                    )
                    is BluetoothHidKeyboardManager.HidState.BluetoothDisabled -> StatusDisplayData(
                        icon = Icons.Default.BluetoothDisabled,
                        color = StatusWarning,
                        title = "BLUETOOTH OFF",
                        subtitle = "Enable Bluetooth on your device to broadcast as a keyboard."
                    )
                    is BluetoothHidKeyboardManager.HidState.SimulationMode -> StatusDisplayData(
                        icon = Icons.Default.Computer,
                        color = IdeAccent,
                        title = "PREVIEW / SIMULATION MODE",
                        subtitle = hidState.reason
                    )
                    is BluetoothHidKeyboardManager.HidState.Unsupported -> StatusDisplayData(
                        icon = Icons.Default.ErrorOutline,
                        color = StatusWarning,
                        title = "HID HARDWARE NOT DETECTED",
                        subtitle = hidState.reason
                    )
                    is BluetoothHidKeyboardManager.HidState.NeedsPermission -> StatusDisplayData(
                        icon = Icons.Default.ErrorOutline,
                        color = StatusWarning,
                        title = "PERMISSIONS REQUIRED",
                        subtitle = "Grant Bluetooth Nearby Devices permissions to connect to PC."
                    )
                    else -> StatusDisplayData(
                        icon = Icons.Default.Bluetooth,
                        color = TextSecondary,
                        title = "INITIALIZING SERVICE...",
                        subtitle = "Registering HID Keyboard App profile..."
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusInfo.color.copy(alpha = 0.12f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusInfo.color)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusInfo.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            ),
                            color = statusInfo.color
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = statusInfo.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }

                // Context-specific actions
                val context = LocalContext.current
                when (hidState) {
                    is BluetoothHidKeyboardManager.HidState.BluetoothDisabled -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                    context.startActivity(enableBtIntent)
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                                    } catch (_: Exception) {}
                                }
                            },
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StatusWarning.copy(alpha = 0.2f),
                                contentColor = StatusWarning
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("enable_bluetooth_button")
                        ) {
                            Icon(Icons.Default.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Turn On Bluetooth / Open Settings",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    is BluetoothHidKeyboardManager.HidState.Unsupported -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRetryBluetooth,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("retry_bluetooth_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Retry", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = {
                                    onEnableSimulation()
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = IdeAccent, contentColor = Color.Black),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(38.dp)
                                    .testTag("preview_mode_fallback_button")
                            ) {
                                Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Use Preview Mode",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                    is BluetoothHidKeyboardManager.HidState.RegisteringApp,
                    is BluetoothHidKeyboardManager.HidState.Initializing -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                onEnableSimulation()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Text(
                                "Taking long? Switch to Preview / Simulation Mode",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Clean Instructions Section
                Text(
                    text = "PAIRING & USAGE GUIDE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    ),
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InstructionStep(
                        step = "1",
                        title = "Pair Device",
                        description = "Open PC Bluetooth settings and connect to 'CodeFlow Studio Keyboard'."
                    )
                    InstructionStep(
                        step = "2",
                        title = "Open VS Code",
                        description = "Open VS Code on your PC and focus the editor or file where code should appear."
                    )
                    InstructionStep(
                        step = "3",
                        title = "Start Typing",
                        description = "Tap below. A countdown gives you time to click into your PC window."
                    )
                    InstructionStep(
                        step = "4",
                        title = "Humanized Rhythm",
                        description = "Types slowly like a human with natural timing variations and newline pauses."
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Primary Start Button
                Button(
                    onClick = {
                        onStartTypingIntoPc()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = IdeAccent,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("start_pc_typing_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "START HUMAN TYPING INTO PC",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Secondary Control Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onEmergencyHalt,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Release Keys",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    OutlinedButton(
                        onClick = onEnableSimulation,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = IdeAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Preview Mode",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionStep(
    step: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(IdePrimary.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = step,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = IdeAccent
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextPrimary
            )
            Text(
                text = description,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                lineHeight = 15.sp
            )
        }
    }
}

private data class StatusDisplayData(
    val icon: ImageVector,
    val color: Color,
    val title: String,
    val subtitle: String
)
