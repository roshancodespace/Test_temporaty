package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BluetoothHidKeyboardManager(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

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

    private val preferences = com.example.data.storage.ProjectPreferences(context)

    private val _hidState = MutableStateFlow<HidState>(HidState.Initializing)
    val hidState: StateFlow<HidState> = _hidState.asStateFlow()

    private val _isSimulationEnabled = MutableStateFlow(false)
    val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHostDevice: BluetoothDevice? = null

    private var isReceiverRegistered = false
    private var registrationTimeoutJob: Job? = null

    private fun isDeviceAclConnected(device: BluetoothDevice): Boolean {
        return try {
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as? Boolean ?: false
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
                        connectedHostDevice = device
                        // Immediately bind HID channel when Windows initiates ACL connection so Windows doesn't drop it
                        try {
                            hidDevice?.connect(device)
                        } catch (_: Exception) {}
                        _hidState.value = HidState.Connected(
                            deviceName = runCatching { device.name }.getOrNull() ?: "Connected PC",
                            deviceAddress = device.address
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
                    if (device != null && (connectedHostDevice == null || connectedHostDevice?.address == device.address)) {
                        connectedHostDevice = null
                        if (hidDevice != null) {
                            _hidState.value = HidState.WaitingForHost
                        }
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
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        @SuppressLint("MissingPermission")
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            registrationTimeoutJob?.cancel()
            if (registered) {
                val hid = hidDevice
                val activeDevice = pluggedDevice
                    ?: runCatching { hid?.connectedDevices?.firstOrNull() }.getOrNull()
                    ?: runCatching {
                        bluetoothAdapter?.bondedDevices?.firstOrNull { isDeviceAclConnected(it) }
                    }.getOrNull()

                if (activeDevice != null) {
                    connectedHostDevice = activeDevice
                    try {
                        hid?.connect(activeDevice)
                    } catch (_: Exception) {}
                    _hidState.value = HidState.Connected(
                        deviceName = runCatching { activeDevice.name }.getOrNull() ?: "PC / Host",
                        deviceAddress = activeDevice.address
                    )
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
                    connectedHostDevice = device
                    _hidState.value = HidState.Connected(
                        deviceName = runCatching { device.name }.getOrNull() ?: "Connected Host",
                        deviceAddress = device.address
                    )
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (!isDeviceAclConnected(device)) {
                        if (connectedHostDevice?.address == device.address) {
                            connectedHostDevice = null
                        }
                        _hidState.value = HidState.WaitingForHost
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
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    context.registerReceiver(bluetoothReceiver, filter)
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

        cleanupProfile()

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            _hidState.value = HidState.Unsupported("Bluetooth hardware is not available on this device.")
            _isSimulationEnabled.value = true
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

        // 5-second watchdog timer: If Android OS / OEM bluetooth daemon never returns proxy or app status,
        // prevent freezing in RegisteringApp state and provide clear fallback
        registrationTimeoutJob?.cancel()
        registrationTimeoutJob = scope.launch {
            delay(5000L)
            if (_hidState.value is HidState.RegisteringApp || _hidState.value is HidState.Initializing) {
                _hidState.value = HidState.Unsupported(
                    "Bluetooth HID Device registration timed out. Your Android build or OEM kernel may restrict Bluetooth peripheral mode."
                )
                _isSimulationEnabled.value = true
            }
        }

        try {
            val success = bluetoothAdapter!!.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
            if (!success) {
                registrationTimeoutJob?.cancel()
                _hidState.value = HidState.Unsupported("Bluetooth HID Device profile not supported by Android OS/hardware.")
                _isSimulationEnabled.value = true
            }
        } catch (e: SecurityException) {
            registrationTimeoutJob?.cancel()
            _hidState.value = HidState.NeedsPermission
        } catch (e: Exception) {
            registrationTimeoutJob?.cancel()
            _hidState.value = HidState.Unsupported("Failed to initialize HID profile: ${e.message}")
            _isSimulationEnabled.value = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerHidApp() {
        val hid = hidDevice ?: return
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "CodeFlow Studio Keyboard",
            "CodeFlow Deterministic Keyboard",
            "Google AI Studio",
            BluetoothHidDevice.SUBCLASS1_KEYBOARD,
            HidKeycodes.KEYBOARD_REPORT_DESCRIPTOR
        )

        val qosSettings = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 11250,
            BluetoothHidDeviceAppQosSettings.MAX
        )

        try {
            val callAccepted = hid.registerApp(sdpSettings, null, qosSettings, { it.run() }, hidCallback)
            if (!callAccepted) {
                registrationTimeoutJob?.cancel()
                _hidState.value = HidState.Unsupported("Bluetooth HID Device registration was rejected by Bluetooth stack.")
                _isSimulationEnabled.value = true
            }
        } catch (e: Exception) {
            registrationTimeoutJob?.cancel()
            _hidState.value = HidState.Unsupported("Registration failed: ${e.message}")
            _isSimulationEnabled.value = true
        }
    }

    fun retry() {
        _isSimulationEnabled.value = false
        initBluetooth()
    }

    fun enableSimulationMode() {
        registrationTimeoutJob?.cancel()
        _isSimulationEnabled.value = true
        _hidState.value = HidState.SimulationMode("Simulated PC Keyboard Output active")
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
            bonded.map { device ->
                val isHidConn = connectedHostDevice?.address == device.address ||
                        runCatching { hidDevice?.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED }.getOrDefault(false)
                val isAclConn = isDeviceAclConnected(device)
                if ((isHidConn || isAclConn) && connectedHostDevice == null) {
                    connectedHostDevice = device
                    if (hidDevice != null) {
                        runCatching { hidDevice?.connect(device) }
                        _hidState.value = HidState.Connected(
                            deviceName = device.name ?: device.address,
                            deviceAddress = device.address
                        )
                    }
                }
                PairedHost(
                    name = device.name ?: device.address,
                    address = device.address,
                    isConnected = isHidConn || isAclConn || connectedHostDevice?.address == device.address
                )
            }.sortedWith(compareByDescending<PairedHost> { it.isConnected }.thenBy { it.name.lowercase() })
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToHost(deviceAddress: String): Boolean {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: return false
        return try {
            val initiated = hidDevice?.connect(device) ?: false
            connectedHostDevice = device
            _hidState.value = HidState.Connected(
                deviceName = device.name ?: device.address,
                deviceAddress = device.address
            )
            initiated || isDeviceAclConnected(device)
        } catch (e: Exception) {
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
     * Sends a character to the connected PC host using HID keyboard report
     */
    @SuppressLint("MissingPermission")
    suspend fun sendChar(char: Char): Boolean {
        val keystroke = HidKeycodes.charToKeystroke(char) ?: return false

        if (_isSimulationEnabled.value) {
            // Virtual transmission
            delay(15L)
            return true
        }

        val device = connectedHostDevice ?: return false
        val hid = hidDevice ?: return false

        // Wake HID channel if host is ACL connected but HID channel went idle
        runCatching {
            if (hid.getConnectionState(device) != BluetoothProfile.STATE_CONNECTED) {
                hid.connect(device)
                delay(40L)
            }
        }

        val keyDownReport = HidKeycodes.makeReport(keystroke.modifier, keystroke.keycode)
        val keyUpReport = HidKeycodes.emptyReport()

        return try {
            hid.sendReport(device, 0, keyDownReport)
            delay(16L) // Hold key down briefly
            hid.sendReport(device, 0, keyUpReport)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sends a shortcut combination, e.g. Ctrl+N or Ctrl+S
     */
    @SuppressLint("MissingPermission")
    suspend fun sendShortcut(modifier: Byte, keycode: Byte): Boolean {
        if (_isSimulationEnabled.value) {
            delay(30L)
            return true
        }

        val device = connectedHostDevice ?: return false
        val hid = hidDevice ?: return false

        return try {
            val down = HidKeycodes.makeReport(modifier, keycode)
            val up = HidKeycodes.emptyReport()
            hid.sendReport(device, 0, down)
            delay(30L)
            hid.sendReport(device, 0, up)
            true
        } catch (e: Exception) {
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
                context.unregisterReceiver(bluetoothReceiver)
                isReceiverRegistered = false
            } catch (_: Exception) {}
        }
        _hidState.value = HidState.Disconnected
    }
}
