package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    // Pure icon-driven status without verbose fluff
    val (statusIcon, iconColor, statusLabel) = when (hidState) {
        is BluetoothHidKeyboardManager.HidState.Connected -> {
            Triple(Icons.Default.BluetoothConnected, StatusSuccess, hidState.deviceName.ifBlank { "PC Connected" })
        }
        is BluetoothHidKeyboardManager.HidState.WaitingForHost -> {
            Triple(Icons.Default.BluetoothSearching, IdeAccent, "Ready to Pair")
        }
        is BluetoothHidKeyboardManager.HidState.BluetoothDisabled -> {
            Triple(Icons.Default.BluetoothDisabled, StatusWarning, "Bluetooth Off")
        }
        is BluetoothHidKeyboardManager.HidState.SimulationMode -> {
            Triple(Icons.Default.Bluetooth, IdeAccent, "Preview Mode")
        }
        is BluetoothHidKeyboardManager.HidState.Unsupported -> {
            Triple(Icons.Default.BluetoothDisabled, StatusWarning, "Notice")
        }
        else -> {
            Triple(Icons.Default.Bluetooth, TextMuted, "Initializing")
        }
    }

    // Clean, minimalist status strip
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(IdeSurface.copy(alpha = 0.5f))
            .clickable { onOpenPairingGuide() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusLabel,
                    tint = iconColor,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                ),
                color = if (hidState is BluetoothHidKeyboardManager.HidState.Connected) TextPrimary else iconColor,
                maxLines = 1
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Quick Test Key Icon Button
            IconButton(
                onClick = onTestKeystroke,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VpnKey,
                    contentDescription = "Test Keystroke",
                    tint = IdeAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Emergency Key Release
            IconButton(
                onClick = onEmergencyRelease,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Emergency Key Release",
                    tint = StatusError,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
