package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.BluetoothHidKeyboardManager
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.IdeSurfaceVariant
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BluetoothHostBar(
    hidState: BluetoothHidKeyboardManager.HidState,
    onOpenPairingGuide: () -> Unit,
    onTestKeystroke: () -> Unit,
    onEmergencyRelease: () -> Unit,
    onEnableSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (dotColor, statusTitle, statusSubtitle) = when (hidState) {
        is BluetoothHidKeyboardManager.HidState.Connected -> {
            Triple(StatusSuccess, "CONNECTED TO PC", "${hidState.deviceName} (${hidState.deviceAddress})")
        }
        is BluetoothHidKeyboardManager.HidState.WaitingForHost -> {
            Triple(IdeAccent, "READY TO PAIR", "Pair PC with 'CodeFlow Studio Keyboard'")
        }
        is BluetoothHidKeyboardManager.HidState.BluetoothDisabled -> {
            Triple(StatusWarning, "BLUETOOTH OFF", "Enable Bluetooth in device settings")
        }
        is BluetoothHidKeyboardManager.HidState.SimulationMode -> {
            Triple(IdeAccent, "PREVIEW MODE", hidState.reason)
        }
        is BluetoothHidKeyboardManager.HidState.Unsupported -> {
            Triple(StatusWarning, "HARDWARE NOTICE", hidState.reason)
        }
        else -> {
            Triple(TextSecondary, "INITIALIZING...", "Starting HID service")
        }
    }

    // Clean, borderless status header strip
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(IdeSurface.copy(alpha = 0.5f))
            .clickable { onOpenPairingGuide() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = dotColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "•  $statusSubtitle",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSecondary,
                        maxLines = 1
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onTestKeystroke,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Test Key",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = IdeAccent
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onEmergencyRelease,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Emergency Key Release",
                    tint = StatusError,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
