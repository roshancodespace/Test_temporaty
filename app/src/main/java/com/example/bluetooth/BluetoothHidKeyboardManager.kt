package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BluetoothHidKeyboardManager(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val appContext: Context = context.applicationContext

    sealed class HidState {
        data object Initializing : HidState()
        data class Unsupported(val reason: String) : HidState()
        data object BluetoothDisabled : HidState()
        data object NeedsPermission : HidState()
        data object RegisteringApp : HidState()
        data object WaitingForHost : HidState()
        data class Connected(val deviceName: String, val deviceAddress: String) : HidState()
        data object Disconnected : HidState()
        data class SimulationMode(val reason: String) : HidState()
    }

    data class PairedHost(
        val name: String,
        val address: String,
        val isConnected: Boolean = false
    )

    private val preferences = com.example.data.storage.ProjectPreferences(appContext)

    private val _hidState = MutableStateFlow<HidState>(HidState.Initializing)
    val hidState: StateFlow<HidState> = _hidState.asStateFlow()

    private val _isSimulationEnabled = MutableStateFlow(false)
    val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    @Volatile
    private var connectedHostDevice: BluetoothDevice? = null

    private var isReceiverRegistered = false
    private var registrationTimeoutJob: Job? = null
    private val connectionMutex = Mutex()

    private fun isDeviceAclConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun isDeviceHidConnected(device: BluetoothDevice): Boolean {
        val hid = hidDevice ?: return false
        return try {
            hid.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
        } catch (_: Exception) {
            false
        }
    }

    // BroadcastReceiver for real-time Bluetooth adapter state and device ACL changes
    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    when (state) {
                        BluetoothAdapter.STATE_ON -> {
                            initBluetooth()
                        }
                        BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                            cleanupProfile()
                            _hidState.value = HidState.BluetoothDisabled
                        }
                        BluetoothAdapter.STATE_TURNING_ON -> {
                            _hidState.value = HidState.Initializing
                        }
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null) {
                        _isSimulationEnabled.value = false
                        connectedHostDevice = device
                        val hid = hidDevice
                        if (hid != null) {
                            runCatching {
                                if (hid.getConnectionState(device) == BluetoothProfile.STATE_DISCONNECTED) {
                                    hid.connect(device)
                                }
                            }
                        }
                        KeystrokeForegroundService.start(
                            appContext,
                            "Connected to ${runCatching { device.name }.getOrNull() ?: "Host"}"
                        )
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && connectedHostDevice?.address == device.address) {
                        if (!isDeviceHidConnected(device)) {
                            connectedHostDevice = null
                            if (hidDevice != null) {
                                _hidState.value = HidState.WaitingForHost
                            }
                        }
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (state == BluetoothDevice.BOND_BONDED && device != null) {
                        _isSimulationEnabled.value = false
                        connectedHostDevice = device
                        runCatching { hidDevice?.connect(device) }
                        _hidState.value = HidState.Connected(
                            deviceName = runCatching { device.name }.getOrNull() ?: device.address,
                            deviceAddress = device.address
                        )
                    }
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        @SuppressLint("MissingPermission")
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                registrationTimeoutJob?.cancel()
                hidDevice = null
                connectedHostDevice = null
                _hidState.value = HidState.Disconnected
                // Automatically re-acquire HID profile if Bluetooth is still enabled
                scope.launch {
                    delay(1000L)
                    if (runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)) {
                        initBluetooth(force = true)
                    }
                }
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            registrationTimeoutJob?.cancel()
            if (registered) {
                _isSimulationEnabled.value = false
                // Crucial: Keep Foreground Service active whenever BluetoothHidDevice is registered
                // so Android's system HidDevService.mUidImportanceListener never unregisters us when minimized to Recents!
                KeystrokeForegroundService.start(appContext, "Bluetooth HID Keyboard Ready")

                val hid = hidDevice
                val alreadyHidConnected = runCatching { hid?.connectedDevices?.firstOrNull() }.getOrNull()
                val targetDevice = alreadyHidConnected
                    ?: pluggedDevice
                    ?: runCatching {
                        bluetoothAdapter?.bondedDevices?.firstOrNull { isDeviceAclConnected(it) }
                    }.getOrNull()

                if (targetDevice != null) {
                    connectedHostDevice = targetDevice
                    if (alreadyHidConnected != null || isDeviceHidConnected(targetDevice)) {
                        val name = runCatching { targetDevice.name }.getOrNull() ?: "Connected Device"
                        _hidState.value = HidState.Connected(
                            deviceName = name,
                            deviceAddress = targetDevice.address
                        )
                        KeystrokeForegroundService.updateStatus(appContext, "Connected to $name")
                    } else {
                        _hidState.value = HidState.WaitingForHost
                        // Initiate HID L2CAP channel connection once
                        runCatching { hid?.connect(targetDevice) }
                    }
                } else {
                    _hidState.value = HidState.WaitingForHost
                }
            } else {
                _hidState.value = HidState.Disconnected
            }
        }

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            registrationTimeoutJob?.cancel()
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _isSimulationEnabled.value = false
                    connectedHostDevice = device
                    val name = runCatching { device.name }.getOrNull() ?: "Connected Host"
                    _hidState.value = HidState.Connected(
                        deviceName = name,
                        deviceAddress = device.address
                    )
                    KeystrokeForegroundService.updateStatus(appContext, "Connected to $name")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHostDevice?.address == device.address) {
                        connectedHostDevice = null
                    }
                    val remaining = runCatching { hidDevice?.connectedDevices?.firstOrNull() }.getOrNull()
                    if (remaining != null) {
                        connectedHostDevice = remaining
                        _hidState.value = HidState.Connected(
                            deviceName = runCatching { remaining.name }.getOrNull() ?: "Connected Host",
                            deviceAddress = remaining.address
                        )
                    } else {
                        _hidState.value = HidState.WaitingForHost
                        KeystrokeForegroundService.updateStatus(appContext, "Bluetooth HID Keyboard Ready")
                    }
                }
            }
        }
    }

    init {
        registerBroadcastReceiver()
        initBluetooth()
    }

    private fun registerBroadcastReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    appContext.registerReceiver(bluetoothReceiver, filter)
                }
                isReceiverRegistered = true
            } catch (_: Exception) {}
        }
    }

    private fun cleanupProfile() {
        registrationTimeoutJob?.cancel()
        registrationTimeoutJob = null
        try {
            hidDevice?.unregisterApp()
        } catch (_: Exception) {}
        try {
            if (hidDevice != null && bluetoothAdapter != null) {
                bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)
            }
        } catch (_: Exception) {}
        hidDevice = null
        connectedHostDevice = null
    }

    fun initBluetooth(force: Boolean = false) {
        if (!force) {
            if (hidDevice != null && (_hidState.value is HidState.WaitingForHost || _hidState.value is HidState.Connected)) {
                return
            }
            if (_hidState.value is HidState.RegisteringApp) {
                return
            }
        }

        _isSimulationEnabled.value = false
        cleanupProfile()

        val bluetoothManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            _hidState.value = HidState.Unsupported("Bluetooth hardware is not available on this device.")
            return
        }

        val isBtEnabled = try {
            bluetoothAdapter!!.isEnabled
        } catch (_: SecurityException) {
            _hidState.value = HidState.NeedsPermission
            return
        }

        if (!isBtEnabled) {
            _hidState.value = HidState.BluetoothDisabled
            return
        }

        // Start foreground service so Android OS never kills our HID registration when minimized to Recents
        KeystrokeForegroundService.start(appContext, "Initializing Bluetooth HID Keyboard...")

        // Apply saved spoofed Bluetooth name if user configured one
        runCatching {
            preferences.getSpoofedBtName()?.let { spoofed ->
                if (preferences.getOriginalBtName() == null && bluetoothAdapter!!.name != null) {
                    preferences.saveOriginalBtName(bluetoothAdapter!!.name)
                }
                bluetoothAdapter!!.setName(spoofed)
            }
        }

        _hidState.value = HidState.RegisteringApp

        registrationTimeoutJob?.cancel()
        registrationTimeoutJob = scope.launch {
            delay(6000L)
            if (_hidState.value is HidState.RegisteringApp || _hidState.value is HidState.Initializing) {
                _hidState.value = HidState.Unsupported(
                    "Bluetooth HID registration timed out. Tap Refresh or toggle Bluetooth."
                )
            }
        }

        try {
            val success = bluetoothAdapter!!.getProfileProxy(appContext, profileListener, BluetoothProfile.HID_DEVICE)
            if (!success) {
                registrationTimeoutJob?.cancel()
                _hidState.value = HidState.Unsupported("Bluetooth HID profile not supported on this device.")
            }
        } catch (e: SecurityException) {
            registrationTimeoutJob?.cancel()
            _hidState.value = HidState.NeedsPermission
        } catch (e: Exception) {
            registrationTimeoutJob?.cancel()
            _hidState.value = HidState.Unsupported("Failed to initialize HID profile: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerHidApp() {
        val hid = hidDevice ?: return
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "CodeFlow Keyboard",
            "Wireless Bluetooth HID Keyboard",
            "Android",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HidKeycodes.KEYBOARD_REPORT_DESCRIPTOR
        )

        try {
            // Pass null for inQos and outQos so L2CAP negotiates universal compatibility across Android, Windows, and macOS
            val callAccepted = hid.registerApp(sdpSettings, null, null, { it.run() }, hidCallback)
            if (!callAccepted) {
                registrationTimeoutJob?.cancel()
                _hidState.value = HidState.Unsupported("Bluetooth HID registration rejected. Tap Refresh to retry.")
            }
        } catch (e: Exception) {
            registrationTimeoutJob?.cancel()
            _hidState.value = HidState.Unsupported("Registration failed: ${e.message}")
        }
    }

    fun retry() {
        _isSimulationEnabled.value = false
        initBluetooth(force = true)
    }

    fun enableSimulationMode() {
        registrationTimeoutJob?.cancel()
        _isSimulationEnabled.value = true
        _hidState.value = HidState.SimulationMode("Simulated Keyboard Output active")
    }

    @SuppressLint("MissingPermission")
    fun getDeviceName(): String {
        return try {
            bluetoothAdapter?.name ?: "Android Device"
        } catch (_: SecurityException) {
            "Android Device"
        }
    }

    @SuppressLint("MissingPermission")
    fun spoofDeviceName(customName: String): Boolean {
        val adapter = bluetoothAdapter ?: return false
        val trimmed = customName.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val current = adapter.name
            if (current != null && preferences.getOriginalBtName() == null) {
                preferences.saveOriginalBtName(current)
            }
            val ok = adapter.setName(trimmed)
            if (ok) {
                preferences.saveSpoofedBtName(trimmed)
            }
            ok
        } catch (_: SecurityException) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun restoreOriginalName(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        val original = preferences.getOriginalBtName() ?: return false
        return try {
            val ok = adapter.setName(original)
            if (ok) {
                preferences.saveSpoofedBtName(null)
            }
            ok
        } catch (_: SecurityException) {
            false
        }
    }

    fun isNameSpoofed(): Boolean = preferences.getSpoofedBtName() != null

    fun getOriginalDeviceName(): String = preferences.getOriginalBtName() ?: getDeviceName()

    @SuppressLint("MissingPermission")
    fun getBondedHosts(): List<PairedHost> {
        return try {
            val bonded = bluetoothAdapter?.bondedDevices ?: return emptyList()
            val hid = hidDevice
            val hidConnectedList = runCatching { hid?.connectedDevices ?: emptyList() }.getOrDefault(emptyList())

            bonded.map { device ->
                val isHidConn = hidConnectedList.any { it.address == device.address } || isDeviceHidConnected(device)
                if (isHidConn) {
                    connectedHostDevice = device
                    _isSimulationEnabled.value = false
                    if (_hidState.value !is HidState.Connected) {
                        _hidState.value = HidState.Connected(
                            deviceName = device.name ?: device.address,
                            deviceAddress = device.address
                        )
                    }
                }
                PairedHost(
                    name = device.name ?: device.address,
                    address = device.address,
                    isConnected = isHidConn
                )
            }.sortedWith(compareByDescending<PairedHost> { it.isConnected }.thenBy { it.name.lowercase() })
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToHost(deviceAddress: String): Boolean {
        _isSimulationEnabled.value = false
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return false
        val hid = hidDevice ?: return false
        connectedHostDevice = device
        return try {
            if (isDeviceHidConnected(device)) {
                _hidState.value = HidState.Connected(
                    deviceName = device.name ?: device.address,
                    deviceAddress = device.address
                )
                return true
            }
            val initiated = hid.connect(device)
            if (initiated) {
                _hidState.value = HidState.Connected(
                    deviceName = device.name ?: device.address,
                    deviceAddress = device.address
                )
            }
            initiated
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(deviceAddress: String): Boolean {
        _isSimulationEnabled.value = false
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return false
        return try {
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                connectToHost(deviceAddress)
            } else {
                device.createBond()
            }
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectHost(): Boolean {
        val device = connectedHostDevice ?: return false
        return try {
            runCatching { hidDevice?.disconnect(device) }
            connectedHostDevice = null
            _hidState.value = HidState.WaitingForHost
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Ensures the Bluetooth HID interrupt channel is genuinely in STATE_CONNECTED before sending keystrokes.
     * Waits for an in-flight connection instead of spamming connect() repeatedly.
     */
    @SuppressLint("MissingPermission")
    suspend fun ensureHidConnected(): BluetoothDevice? = connectionMutex.withLock {
        val hid = hidDevice ?: return null

        // 1. Check if theBluetoothHidDevice stack already has an active connected host
        val alreadyConnected = runCatching { hid.connectedDevices.firstOrNull() }.getOrNull()
        if (alreadyConnected != null) {
            connectedHostDevice = alreadyConnected
            _hidState.value = HidState.Connected(
                deviceName = runCatching { alreadyConnected.name }.getOrNull() ?: alreadyConnected.address,
                deviceAddress = alreadyConnected.address
            )
            return alreadyConnected
        }

        // 2. Pick target device: either user-selected connectedHostDevice, or an ACL-connected bonded device, or single bonded device
        val candidate = connectedHostDevice
            ?: runCatching { bluetoothAdapter?.bondedDevices?.firstOrNull { isDeviceAclConnected(it) } }.getOrNull()
            ?: runCatching { bluetoothAdapter?.bondedDevices?.singleOrNull() }.getOrNull()
            ?: return null

        connectedHostDevice = candidate

        val currentState = runCatching { hid.getConnectionState(candidate) }.getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
        if (currentState == BluetoothProfile.STATE_CONNECTED) {
            return candidate
        }

        // Initiate connection ONCE if currently disconnected
        if (currentState == BluetoothProfile.STATE_DISCONNECTED) {
            runCatching { hid.connect(candidate) }
        }

        // Wait up to 2500ms for the L2CAP HID channel to reach STATE_CONNECTED
        repeat(25) {
            delay(100L)
            val state = runCatching { hid.getConnectionState(candidate) }.getOrDefault(BluetoothProfile.STATE_DISCONNECTED)
            if (state == BluetoothProfile.STATE_CONNECTED) {
                _hidState.value = HidState.Connected(
                    deviceName = runCatching { candidate.name }.getOrNull() ?: candidate.address,
                    deviceAddress = candidate.address
                )
                return candidate
            }
        }

        return if (runCatching { hid.getConnectionState(candidate) == BluetoothProfile.STATE_CONNECTED }.getOrDefault(false)) {
            candidate
        } else {
            null
        }
    }

    /**
     * Sends a character to the connected host using HID keyboard report
     */
    @SuppressLint("MissingPermission")
    suspend fun sendChar(char: Char): Boolean {
        val keystroke = HidKeycodes.charToKeystroke(char) ?: return true // Skip unmapped non-ASCII chars gracefully

        if (_isSimulationEnabled.value && _hidState.value is HidState.SimulationMode) {
            delay(15L)
            return true
        }

        val hid = hidDevice ?: return false
        val device = connectedHostDevice?.takeIf { isDeviceHidConnected(it) } ?: ensureHidConnected() ?: return false

        val keyDownReport = HidKeycodes.makeReport(keystroke.modifier, keystroke.keycode)
        val keyUpReport = HidKeycodes.emptyReport()

        return try {
            val downOk = hid.sendReport(device, 0, keyDownReport)
            delay(20L) // Hold key down so Android uhid / Windows hidbth registers the key press
            val upOk = hid.sendReport(device, 0, keyUpReport)
            delay(14L) // Clean inter-key release gap so fast consecutive keys never drop or merge
            downOk && upOk
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Sends a shortcut combination, e.g. Ctrl+N or Ctrl+S
     */
    @SuppressLint("MissingPermission")
    suspend fun sendShortcut(modifier: Byte, keycode: Byte): Boolean {
        if (_isSimulationEnabled.value && _hidState.value is HidState.SimulationMode) {
            delay(30L)
            return true
        }

        val hid = hidDevice ?: return false
        val device = connectedHostDevice?.takeIf { isDeviceHidConnected(it) } ?: ensureHidConnected() ?: return false

        return try {
            val down = HidKeycodes.makeReport(modifier, keycode)
            val up = HidKeycodes.emptyReport()
            val downOk = hid.sendReport(device, 0, down)
            delay(30L)
            val upOk = hid.sendReport(device, 0, up)
            delay(15L)
            downOk && upOk
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Emergency Halt: Sends an all-zeros empty report to guarantee no keys remain stuck down
     */
    @SuppressLint("MissingPermission")
    fun emergencyReleaseAllKeys() {
        val device = connectedHostDevice ?: return
        val hid = hidDevice ?: return
        try {
            hid.sendReport(device, 0, HidKeycodes.emptyReport())
        } catch (_: Exception) {}
    }

    fun disconnect() {
        emergencyReleaseAllKeys()
        cleanupProfile()
        if (isReceiverRegistered) {
            try {
                appContext.unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
            } catch (_: Exception) {}
        }
        KeystrokeForegroundService.stop(appContext)
        _hidState.value = HidState.Disconnected
    }
}
