package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.CodeFlowViewModel
import com.example.ui.components.BluetoothHostBar
import com.example.ui.components.BluetoothKeyboardModal
import com.example.ui.components.ConsoleLogCard
import com.example.ui.components.GitHubImportModal
import com.example.ui.components.ProjectSourceCard
import com.example.ui.components.SettingsModal
import com.example.ui.components.TypingControlCard
import com.example.ui.theme.CodeFlowTheme
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdeBackground
import com.example.ui.theme.IdeBorder
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {

    private val viewModel: CodeFlowViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val palettePreset by viewModel.palettePreset.collectAsState()

            CodeFlowTheme(
                themeMode = themeMode,
                palettePreset = palettePreset
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: CodeFlowViewModel) {
    val hidState by viewModel.hidState.collectAsState()
    val typingState by viewModel.typingState.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val fileTree by viewModel.fileTree.collectAsState()
    val eventLogs by viewModel.eventLogs.collectAsState()
    val importMessage by viewModel.importStatusMessage.collectAsState()
    val isImporting by viewModel.isImporting.collectAsState()

    val typingCps by viewModel.typingCps.collectAsState()
    val linePauseMs by viewModel.linePauseMs.collectAsState()
    val humanJitter by viewModel.humanJitter.collectAsState()
    val startCountdownSec by viewModel.startCountdownSec.collectAsState()

    val themeMode by viewModel.themeMode.collectAsState()
    val palettePreset by viewModel.palettePreset.collectAsState()
    val checkpoint by viewModel.checkpoint.collectAsState()

    var showSettingsModal by remember { mutableStateOf(false) }
    var showGitHubImportModal by remember { mutableStateOf(false) }
    var showBluetoothModal by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Bluetooth Runtime Permission Request for Android 12+
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.bluetoothHidManager.initBluetooth()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            bluetoothPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        }
    }

    // SAF File Picker for local ZIP projects
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importProjectZip(uri)
        }
    }

    LaunchedEffect(importMessage) {
        importMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissImportStatus()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(IdeBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(IdeAccent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = null,
                                tint = IdeAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CodeFlow Studio",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "Bluetooth PC Keystroke Remote",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showSettingsModal = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Settings",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Section 1: Bluetooth Host Status Bar
                BluetoothHostBar(
                    hidState = hidState,
                    onOpenPairingGuide = { showBluetoothModal = true },
                    onTestKeystroke = { viewModel.testSampleKeystroke() },
                    onEmergencyRelease = { viewModel.bluetoothHidManager.emergencyReleaseAllKeys() },
                    onEnableSimulation = { viewModel.bluetoothHidManager.enableSimulationMode() }
                )

                // Section 2: Typing Control Card (Start, Checkpoint Resume, Progress, Cadence)
                TypingControlCard(
                    typingState = typingState,
                    speedCps = typingCps,
                    linePauseMs = linePauseMs,
                    jitterEnabled = humanJitter,
                    checkpoint = checkpoint,
                    onStartTyping = { viewModel.startBluetoothTypingIntoPc() },
                    onResumeFromCheckpoint = { viewModel.resumeFromCheckpoint() },
                    onPauseTyping = { viewModel.pauseBluetoothTyping() },
                    onResumeTyping = { viewModel.resumeBluetoothTyping() },
                    onStopTyping = { viewModel.stopBluetoothTyping() },
                    onOpenCadenceSettings = { showSettingsModal = true }
                )

                // Section 3: Project Source & File Selection Card (Android-friendly M3 layout)
                ProjectSourceCard(
                    project = currentProject,
                    fileTree = fileTree,
                    onToggleFileInclusion = { viewModel.toggleFileInclusion(it) },
                    onToggleAllFiles = { viewModel.toggleAllFiles(it) },
                    onImportGitHubClick = { showGitHubImportModal = true },
                    onImportZipClick = {
                        zipPickerLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                        )
                    },
                    onResetSample = { viewModel.resetToSampleProject() }
                )

                // Section 4: Live Telemetry & Event Log Card
                ConsoleLogCard(
                    logs = eventLogs,
                    onClearLogs = { viewModel.clearLogs() }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Loading overlay during repository extractions
            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(IdeBackground.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = IdeSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, IdeBorder),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = IdeAccent,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Extracting repository files safely...",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    // GitHub Repo Import Modal
    if (showGitHubImportModal) {
        GitHubImportModal(
            onImportRepo = { url, branch -> viewModel.importGitHubRepo(url, branch) },
            onDismiss = { showGitHubImportModal = false }
        )
    }

    // Bluetooth PC Keyboard Pairing Guide Modal
    if (showBluetoothModal) {
        BluetoothKeyboardModal(
            hidState = hidState,
            onStartTypingIntoPc = { viewModel.startBluetoothTypingIntoPc() },
            onEmergencyHalt = { viewModel.bluetoothHidManager.emergencyReleaseAllKeys() },
            onEnableSimulation = { viewModel.bluetoothHidManager.enableSimulationMode() },
            onRetryBluetooth = { viewModel.bluetoothHidManager.initBluetooth() },
            onDismiss = { showBluetoothModal = false }
        )
    }

    // Comprehensive Settings Modal: Cadence, Themes & Appearance, Persistence Memory
    if (showSettingsModal) {
        SettingsModal(
            cps = typingCps,
            lineEndPauseMs = linePauseMs,
            randomJitter = humanJitter,
            countdownSec = startCountdownSec,
            themeMode = themeMode,
            palettePreset = palettePreset,
            checkpoint = checkpoint,
            onCpsChange = { viewModel.updateTypingCadence(it, linePauseMs, humanJitter, startCountdownSec) },
            onLineEndPauseChange = { viewModel.updateTypingCadence(typingCps, it, humanJitter, startCountdownSec) },
            onToggleJitter = { viewModel.updateTypingCadence(typingCps, linePauseMs, !humanJitter, startCountdownSec) },
            onCountdownSecChange = { viewModel.updateTypingCadence(typingCps, linePauseMs, humanJitter, it) },
            onThemeModeChange = { viewModel.updateTheme(it, palettePreset) },
            onPalettePresetChange = { viewModel.updateTheme(themeMode, it) },
            onClearCheckpoint = { viewModel.clearCheckpoint() },
            onResetDefaults = { viewModel.updateTypingCadence(28, 120L, true, 3) },
            onDismiss = { showSettingsModal = false }
        )
    }
}
