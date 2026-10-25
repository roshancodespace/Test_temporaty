package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val _hidState = MutableStateFlow<HidState>(HidState.Initializing)
    val hidState: StateFlow<HidState> = _hidState.asStateFlow()

    private val _isSimulationEnabled = MutableStateFlow(false)
    val isSimulationEnabled: StateFlow<Boolean> = _isSimulationEnabled.asStateFlow()

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHostDevice: BluetoothDevice? = null

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
                hidDevice = null
                connectedHostDevice = null
                _hidState.value = HidState.Disconnected
            }
        }
    }

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            if (registered) {
                if (pluggedDevice != null) {
                    connectedHostDevice = pluggedDevice
                    _hidState.value = HidState.Connected(
                        deviceName = pluggedDevice.name ?: "PC / Host",
                        deviceAddress = pluggedDevice.address
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
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHostDevice = device
                    _hidState.value = HidState.Connected(
                        deviceName = device.name ?: "Connected Host",
                        deviceAddress = device.address
                    )
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHostDevice?.address == device.address) {
                        connectedHostDevice = null
                    }
                    _hidState.value = HidState.WaitingForHost
                }
            }
        }
    }

    init {
        initBluetooth()
    }

    fun initBluetooth() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            _hidState.value = HidState.Unsupported("Bluetooth hardware is not available on this device.")
            _isSimulationEnabled.value = true
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            _hidState.value = HidState.BluetoothDisabled
            return
        }

        try {
            val success = bluetoothAdapter!!.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
            if (!success) {
                _hidState.value = HidState.Unsupported("Bluetooth HID Device profile not supported by Android OS/hardware.")
                _isSimulationEnabled.value = true
            } else {
                _hidState.value = HidState.RegisteringApp
            }
        } catch (e: SecurityException) {
            _hidState.value = HidState.NeedsPermission
        } catch (e: Exception) {
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
            hid.registerApp(sdpSettings, null, qosSettings, { it.run() }, hidCallback)
        } catch (e: Exception) {
            _hidState.value = HidState.Unsupported("Registration failed: ${e.message}")
            _isSimulationEnabled.value = true
        }
    }

    fun enableSimulationMode() {
        _isSimulationEnabled.value = true
        _hidState.value = HidState.SimulationMode("Simulated PC Keyboard Output active")
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
        hidDevice?.let { bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, it) }
        hidDevice = null
        connectedHostDevice = null
        _hidState.value = HidState.Disconnected
    }
}
