package com.example

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.BluetoothHidKeyboardManager
import com.example.bluetooth.HumanTypingSimulator
import com.example.ui.CodeFlowViewModel
import com.example.ui.components.AutomationCenterModal
import com.example.ui.components.BluetoothKeyboardModal
import com.example.ui.components.ConsoleLogCard
import com.example.ui.components.DirectScreenMirrorModal
import com.example.ui.components.GitHubImportModal
import com.example.ui.components.ImportProgressModal
import com.example.ui.components.ProjectSourceCard
import com.example.ui.components.SettingsModal
import com.example.ui.components.TypingControlCard
import com.example.ui.theme.CodeFlowTheme
import com.example.ui.theme.IdeAccent
import com.example.ui.theme.IdeBackground
import com.example.ui.theme.IdeBorderSubtle
import com.example.ui.theme.IdeSurface
import com.example.ui.theme.StatusSuccess
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
    val context = LocalContext.current
    val activity = context as? Activity

    val hidState by viewModel.hidState.collectAsState()
    val typingState by viewModel.typingState.collectAsState()
    val currentProject by viewModel.currentProject.collectAsState()
    val fileTree by viewModel.fileTree.collectAsState()
    val eventLogs by viewModel.eventLogs.collectAsState()
    val importProgress by viewModel.importProgress.collectAsState()

    val typingCps by viewModel.typingCps.collectAsState()
    val linePauseMs by viewModel.linePauseMs.collectAsState()
    val humanJitter by viewModel.humanJitter.collectAsState()
    val startCountdownSec by viewModel.startCountdownSec.collectAsState()

    val themeMode by viewModel.themeMode.collectAsState()
    val palettePreset by viewModel.palettePreset.collectAsState()
    val checkpoint by viewModel.checkpoint.collectAsState()

    val currentBtName by viewModel.currentBtName.collectAsState()
    val isBtNameSpoofed by viewModel.isBtNameSpoofed.collectAsState()
    val keepScreenAwake by viewModel.keepScreenAwake.collectAsState()
    val originalBtName = remember(isBtNameSpoofed, currentBtName) {
        viewModel.bluetoothHidManager.getOriginalDeviceName()
    }

    var showSettingsModal by remember { mutableStateOf(false) }
    var showGitHubImportModal by remember { mutableStateOf(false) }
    var showBluetoothModal by remember { mutableStateOf(false) }
    var showAutomationModal by remember { mutableStateOf(false) }
    var showScreenMirrorModal by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Keep screen awake during active typing if enabled
    DisposableEffect(keepScreenAwake, typingState) {
        val isActivelyTyping = typingState is HumanTypingSimulator.TypingState.Typing
        if (keepScreenAwake && isActivelyTyping) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Bluetooth Permissions
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.bluetoothHidManager.initBluetooth(force = false)
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isNotEmpty()) {
            bluetoothPermissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // SAF ZIP Picker
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importProjectZip(uri)
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
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Flat Material 3 Expressive Header Bar
                Surface(
                    color = IdeSurface.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, IdeBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(IdeAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Keyboard,
                                    contentDescription = null,
                                    tint = IdeAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "CodeFlow",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                                color = TextPrimary
                            )
                        }

                        // Active Bluetooth Host Pill
                        val currentHidState = hidState
                        val (btLabel, btColor) = when (currentHidState) {
                            is BluetoothHidKeyboardManager.HidState.Connected -> Pair(
                                currentHidState.deviceName.ifBlank { "PC Connected" },
                                StatusSuccess
                            )
                            is BluetoothHidKeyboardManager.HidState.WaitingForHost -> Pair("Ready to Pair", IdeAccent)
                            else -> Pair("Offline", TextSecondary)
                        }

                        Surface(
                            color = btColor.copy(alpha = 0.12f),
                            shape = CircleShape,
                            modifier = Modifier.clickable { showBluetoothModal = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(btColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = btLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = btColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        IconButton(
                            onClick = { showSettingsModal = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Settings",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Primary Expressive Action Bar (3 Flat Pills)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { viewModel.startBluetoothTypingIntoPc() },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = IdeAccent, contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("primary_typing_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "Type",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { showAutomationModal = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("automate_vs_code_button")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "Automate",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { showScreenMirrorModal = true },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, IdeBorderSubtle),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("screen_mirror_button")
                    ) {
                        Icon(Icons.Default.DesktopWindows, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "Mirror",
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.5.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Section 2: Typing Progress / Checkpoint Bar
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

                // Section 3: Redesigned Minimal File Tree Card
                ProjectSourceCard(
                    project = currentProject,
                    fileTree = fileTree,
                    onToggleFileInclusion = { viewModel.toggleFileInclusion(it) },
                    onToggleAllFiles = { viewModel.toggleAllFiles(it) },
                    onSelectCodeOnly = { viewModel.selectCodeFilesOnly() },
                    onImportGitHubClick = { showGitHubImportModal = true },
                    onImportZipClick = {
                        zipPickerLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                        )
                    },
                    onResetSample = { viewModel.resetToSampleProject() }
                )

                // Section 4: Collapsible Console Telemetry Strip
                ConsoleLogCard(
                    logs = eventLogs,
                    onClearLogs = { viewModel.clearLogs() }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    ImportProgressModal(
        progress = importProgress,
        onDismiss = { viewModel.dismissImportProgress() }
    )

    if (showGitHubImportModal) {
        GitHubImportModal(
            onImportRepo = { url, branch -> viewModel.importGitHubRepo(url, branch) },
            onDismiss = { showGitHubImportModal = false }
        )
    }

    if (showBluetoothModal) {
        val pairedHosts = remember(showBluetoothModal, hidState) { viewModel.bluetoothHidManager.getBondedHosts() }
        BluetoothKeyboardModal(
            hidState = hidState,
            deviceName = currentBtName,
            originalDeviceName = originalBtName,
            isNameSpoofed = isBtNameSpoofed,
            bondedHosts = pairedHosts,
            onConnectToHost = { viewModel.bluetoothHidManager.connectToHost(it) },
            onDisconnectHost = { viewModel.bluetoothHidManager.disconnectHost() },
            onSpoofName = { viewModel.spoofBluetoothName(it) },
            onRestoreOriginalName = { viewModel.restoreOriginalBluetoothName() },
            onTestKeystroke = { viewModel.testSampleKeystroke() },
            onStartTypingIntoPc = { viewModel.startBluetoothTypingIntoPc() },
            onEmergencyHalt = { viewModel.bluetoothHidManager.emergencyReleaseAllKeys() },
            onEnableSimulation = { viewModel.bluetoothHidManager.enableSimulationMode() },
            onRetryBluetooth = { viewModel.bluetoothHidManager.initBluetooth(force = true) },
            onDismiss = { showBluetoothModal = false }
        )
    }

    if (showSettingsModal) {
        SettingsModal(
            cps = typingCps,
            lineEndPauseMs = linePauseMs,
            randomJitter = humanJitter,
            countdownSec = startCountdownSec,
            themeMode = themeMode,
            palettePreset = palettePreset,
            checkpoint = checkpoint,
            keepScreenAwake = keepScreenAwake,
            currentBtName = currentBtName,
            originalBtName = originalBtName,
            isBtNameSpoofed = isBtNameSpoofed,
            onCpsChange = { viewModel.updateTypingCadence(it, linePauseMs, humanJitter, startCountdownSec) },
            onLineEndPauseChange = { viewModel.updateTypingCadence(typingCps, it, humanJitter, startCountdownSec) },
            onToggleJitter = { viewModel.updateTypingCadence(typingCps, linePauseMs, !humanJitter, startCountdownSec) },
            onCountdownSecChange = { viewModel.updateTypingCadence(typingCps, linePauseMs, humanJitter, it) },
            onThemeModeChange = { viewModel.updateTheme(it, palettePreset) },
            onPalettePresetChange = { viewModel.updateTheme(themeMode, it) },
            onToggleKeepScreenAwake = { viewModel.toggleKeepScreenAwake(it) },
            onSpoofBtName = { viewModel.spoofBluetoothName(it) },
            onRestoreBtName = { viewModel.restoreOriginalBluetoothName() },
            onClearCheckpoint = { viewModel.clearCheckpoint() },
            onResetDefaults = { viewModel.updateTypingCadence(28, 120L, true, 3) },
            onDismiss = { showSettingsModal = false }
        )
    }

    if (showAutomationModal) {
        AutomationCenterModal(
            viewModel = viewModel,
            onDismiss = { showAutomationModal = false },
            onOpenScreenScout = {
                showAutomationModal = false
                showScreenMirrorModal = true
            }
        )
    }

    if (showScreenMirrorModal) {
        DirectScreenMirrorModal(
            viewModel = viewModel,
            onDismiss = { showScreenMirrorModal = false }
        )
    }
}
