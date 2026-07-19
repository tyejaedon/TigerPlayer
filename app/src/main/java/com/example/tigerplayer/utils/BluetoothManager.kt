package com.example.tigerplayer.utils

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothA2dp
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresPermission
import com.example.tigerplayer.data.local.PlaybackPrefs
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class BluetoothDeviceInfo(
    val name: String = "Internal Speaker",
    val address: String = "",
    val batteryLevel: Int = -1,
    val isConnected: Boolean = false,
    val listeningTimeMs: Long = 0L,
    val type: String = "Internal",
    val codec: String = "N/A",
    val profile: String = "None",
    val transport: String = "Device",
    val deviceClass: String = "Built-in",
    val maskedAddress: String = "",
    val lastConnectedAtMs: Long = 0L
)

@Singleton
class BluetoothDeviceManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playbackPrefs: PlaybackPrefs
) {
    private val attributedContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.createAttributionContext(AttributionTags.BLUETOOTH_DEVICE_MANAGEMENT)
    } else {
        context
    }

    private val bluetoothManager = attributedContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _connectedDevice = MutableStateFlow(BluetoothDeviceInfo())
    val connectedDevice: StateFlow<BluetoothDeviceInfo> = _connectedDevice.asStateFlow()

    private val managerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var listeningJob: Job? = null
    private var lastUpdateTime: Long = 0L
    private var activeGatt: BluetoothGatt? = null

    // Standard BLE Battery Service UUIDs
    private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    private val BATTERY_LEVEL_UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return

            @Suppress("DEPRECATION")
            val broadcastDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    updateConnectedDevice()
                }

                // Catch API 31+ public intent and older hidden intent
                "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED" -> {
                    val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                    updateBatterySafely(broadcastDevice, level)
                }

                // Catch vendor-specific HFP broadcasts (Apple AT+XAPL, Plantronics, etc.)
                "android.bluetooth.headset.profile.action.VENDOR_SPECIFIC_HEADSET_EVENT" -> {
                    val cmd = intent.getStringExtra("android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_CMD")
                    val args = intent.getSerializableExtra("android.bluetooth.headset.extra.VENDOR_SPECIFIC_HEADSET_EVENT_ARGS") as? Array<*>

                    // Example: Apple/Beats exact battery reporting
                    if (cmd == "+IPHONEACCEV" && args != null && args.isNotEmpty()) {
                        // Parses exact 1% increments depending on the payload
                        val level = parseVendorBattery(args)
                        if (level in 0..100) updateBatterySafely(broadcastDevice, level)
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            addAction("android.bluetooth.headset.profile.action.VENDOR_SPECIFIC_HEADSET_EVENT")
        }
        ContextCompat.registerReceiver(
            attributedContext,
            bluetoothReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        updateConnectedDevice()
    }

    @SuppressLint("MissingPermission")
    private fun updateConnectedDevice() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || !hasBluetoothConnectPermission()) {
            disconnectAndClear()
            return
        }

        bluetoothAdapter.getProfileProxy(attributedContext, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                if (profile == BluetoothProfile.A2DP) {
                    val a2dp = proxy as? BluetoothA2dp
                    val device = a2dp?.connectedDevices?.firstOrNull()

                    if (device != null) {
                        managerScope.launch {
                            val savedTime = playbackPrefs.getBtListeningTime(device.address).first()

                            // 1. Instantly fetch using reflection rather than waiting for broadcast
                            val instantBattery = getBatteryLevelViaReflection(device)

                            _connectedDevice.update {
                                BluetoothDeviceInfo(
                                    name = device.name ?: "Unknown Device",
                                    address = device.address,
                                    batteryLevel = instantBattery,
                                    isConnected = true,
                                    listeningTimeMs = savedTime,
                                    type = "Bluetooth",
                                    codec = "A2DP Standard",
                                    profile = "A2DP",
                                    transport = resolveTransportLabel(device),
                                    deviceClass = resolveDeviceClassLabel(device),
                                    maskedAddress = maskAddress(device.address),
                                    lastConnectedAtMs = System.currentTimeMillis()
                                )
                            }

                            // 2. Connect GATT for precise 1% updates if supported
                            connectGattForPreciseBattery(device)
                        }
                    } else {
                        disconnectAndClear()
                    }
                    bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
    }

    @SuppressLint("MissingPermission")
    private fun connectGattForPreciseBattery(device: BluetoothDevice) {
        activeGatt?.close()

        activeGatt = device.connectGatt(attributedContext, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val batteryService = gatt.getService(BATTERY_SERVICE_UUID)
                val batteryChar = batteryService?.getCharacteristic(BATTERY_LEVEL_UUID)

                if (batteryChar != null) {
                    // Read the exact 1% level
                    gatt.readCharacteristic(batteryChar)

                    // Subscribe to future exact changes
                    gatt.setCharacteristicNotification(batteryChar, true)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: android.bluetooth.BluetoothGattCharacteristic, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_UUID) {
                    val level = characteristic.getIntValue(android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    updateBatterySafely(gatt.device, level)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: android.bluetooth.BluetoothGattCharacteristic) {
                if (characteristic.uuid == BATTERY_LEVEL_UUID) {
                    val level = characteristic.getIntValue(android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                    updateBatterySafely(gatt.device, level)
                }
            }
        })
    }

    /**
     * Actively invokes the hidden Android API to fetch the battery instantly
     * instead of waiting for the device to randomly broadcast it.
     */
    private fun getBatteryLevelViaReflection(device: BluetoothDevice): Int {
        return runCatching {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int ?: -1
            level
        }.getOrDefault(-1)
    }

    private fun updateBatterySafely(device: BluetoothDevice?, level: Int) {
        if (level < 0) return
        _connectedDevice.update { current ->
            if (!current.isConnected) return@update current
            if (device != null && device.address != current.address) return@update current
            current.copy(batteryLevel = level)
        }
    }

    private fun parseVendorBattery(args: Array<*>): Int {
        return runCatching {
            if (args.size >= 2) {
                val levelArg = args[1].toString().toIntOrNull() ?: return -1
                // Some vendors report 0-9, others 0-100.
                if (levelArg in 0..9) (levelArg + 1) * 10 else levelArg
            } else {
                -1
            }
        }.getOrDefault(-1)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun disconnectAndClear() {
        activeGatt?.close()
        activeGatt = null
        _connectedDevice.update { BluetoothDeviceInfo() }
    }

    fun refreshConnectedDevice() {
        updateConnectedDevice()
    }

    fun startTrackingListeningTime() {
        if (listeningJob?.isActive == true) return
        lastUpdateTime = System.currentTimeMillis()
        listeningJob = managerScope.launch {
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val delta = now - lastUpdateTime
                lastUpdateTime = now

                val current = _connectedDevice.value
                if (current.isConnected && current.address.isNotEmpty()) {
                    val newTime = current.listeningTimeMs + delta
                    _connectedDevice.update { it.copy(listeningTimeMs = newTime) }
                    playbackPrefs.saveBtListeningTime(current.address, newTime)
                }
            }
        }
    }

    fun stopTrackingListeningTime() {
        listeningJob?.cancel()
        listeningJob = null
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            attributedContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun resolveTransportLabel(device: BluetoothDevice): String {
        return runCatching {
            when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                else -> "Unknown"
            }
        }.getOrDefault("Unknown")
    }

    @SuppressLint("MissingPermission")
    private fun resolveDeviceClassLabel(device: BluetoothDevice): String {
        return runCatching {
            val majorClass = device.bluetoothClass?.majorDeviceClass ?: return@runCatching "Audio Device"
            when (majorClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video"
                BluetoothClass.Device.Major.PHONE -> "Phone"
                BluetoothClass.Device.Major.COMPUTER -> "Computer"
                BluetoothClass.Device.Major.WEARABLE -> "Wearable"
                else -> "Audio Device"
            }
        }.getOrDefault("Audio Device")
    }

    private fun maskAddress(address: String): String {
        val parts = address.split(':')
        if (parts.size != 6) return "Hidden"
        return "${parts[0]}:${parts[1]}:**:**:${parts[4]}:${parts[5]}"
    }
}