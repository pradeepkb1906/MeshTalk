package com.meshtalk.app.mesh.transport

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.meshtalk.app.data.preferences.UserPreferences
import com.meshtalk.app.data.model.TransportType
import com.meshtalk.app.mesh.MeshPacket
import com.meshtalk.app.mesh.parseMeshPacket
import kotlinx.coroutines.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════
 * BleTransport — Bluetooth Low Energy mesh transport
 * ══════════════════════════════════════════════════════════════════════
 *
 * BLE is used as a fallback/supplementary transport:
 * - Lower power consumption than WiFi Direct
 * - Always-on background scanning
 * - Short range (~30-100 meters)
 * - Lower bandwidth (suitable for text messages)
 *
 * Uses GATT server/client for bidirectional communication:
 * - Advertises a custom BLE service UUID
 * - Scans for other MeshTalk devices
 * - Exchanges data via GATT characteristics
 *
 * Limitations:
 * - ~512 bytes per GATT write (MTU negotiation can increase)
 * - Larger messages are chunked
 * - ~20-50 kbps effective throughput
 */
@Singleton
class BleTransport @Inject constructor(
    private val context: Context,
    private val userPreferences: UserPreferences
) : Transport {

    companion object {
        private const val TAG = "BleTransport"

        // Custom UUIDs for MeshTalk BLE service
        // Custom UUIDs for MeshTalk BLE service
        // Note: UUIDs must be valid hexadecimal. We use a base UUID and modify the 16-bit prefix.
        // Base: 0000XXXX-0000-1000-8000-00805F9B34FB
        val MESH_SERVICE_UUID: UUID = UUID.fromString("0000FE60-0000-1000-8000-00805F9B34FB")
        val MESH_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE61-0000-1000-8000-00805F9B34FB")
        val MESH_NAME_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000FE62-0000-1000-8000-00805F9B34FB")

        private const val SCAN_PERIOD_MS = 10_000L  // 10 second scan windows
        private const val SCAN_INTERVAL_MS = 5_000L // 5 second pause between scans
        private const val MAX_CHUNK_SIZE = 500       // BLE write size limit
    }

    override val name: String = "Bluetooth LE"
    override val type: TransportType = TransportType.BLE
    override var isActive: Boolean = false
        private set

    private val bluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter by lazy { bluetoothManager?.adapter }
    private val bleScanner by lazy { bluetoothAdapter?.bluetoothLeScanner }
    private val bleAdvertiser by lazy { bluetoothAdapter?.bluetoothLeAdvertiser }

    private var gattServer: BluetoothGattServer? = null
    // Clients who connected to us (we act as Server)
    private val connectedDevices = ConcurrentHashMap<String, BluetoothDevice>()
    // Servers we connected to (we act as Client)
    private val connectedGattClients = ConcurrentHashMap<String, BluetoothGatt>()
    
    // Buffer for incoming partial messages (MAC -> (Timestamp, Bytes))
    private val messageBuffer = ConcurrentHashMap<String, Pair<Long, ByteArray>>()

    private var onPacketReceived: (suspend (MeshPacket, String) -> Unit)? = null
    private var onPeerConnected: (suspend (String, String, String) -> Unit)? = null
    private var onPeerDisconnected: (suspend (String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var localMeshId: String = ""
    private var scanJob: Job? = null

    override suspend fun start() {
        if (bluetoothAdapter == null || bluetoothAdapter?.isEnabled != true) {
            Log.w(TAG, "Bluetooth not available or not enabled")
            return
        }

        localMeshId = userPreferences.getMeshIdSync()
        Log.i(TAG, "Starting BLE Transport")

        try {
            startGattServer()
            startAdvertising()
            startScanning()
            startBufferCleanup()
            isActive = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE: ${e.message}")
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "Stopping BLE Transport")
        isActive = false
        scanJob?.cancel()

        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
            bleScanner?.stopScan(scanCallback)
            gattServer?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping BLE: ${e.message}")
        }

        connectedDevices.clear()
    }

    override suspend fun sendPacket(packet: MeshPacket, endpointId: String?) {
        val bytes = packet.toBytes()
        sendBytes(bytes, endpointId)
    }

    override suspend fun sendBytes(data: ByteArray, endpointId: String?) {
        if (gattServer == null) return

        try {
            // Chunk data if necessary
            val chunks = data.toList().chunked(MAX_CHUNK_SIZE).map { it.toByteArray() }

            // 1. Send to devices connected to us (we are Server) - use Notification
            val serverDevices = if (endpointId != null) {
                listOfNotNull(connectedDevices[endpointId])
            } else {
                connectedDevices.values.toList()
            }

            if (serverDevices.isNotEmpty()) {
                val service = gattServer?.getService(MESH_SERVICE_UUID)
                val characteristic = service?.getCharacteristic(MESH_CHARACTERISTIC_UUID)
                
                if (characteristic != null) {
                    for (device in serverDevices) {
                        for (chunk in chunks) {
                            characteristic.value = chunk
                            try {
                                gattServer?.notifyCharacteristicChanged(device, characteristic, false)
                            } catch (e: SecurityException) {
                                Log.e(TAG, "Cannot notify device: ${e.message}")
                            }
                            delay(50) // Small delay between chunks
                        }
                    }
                }
            }

            // 2. Send to devices we connected to (we are Client) - use Write
            val clientGatts = if (endpointId != null) {
                listOfNotNull(connectedGattClients[endpointId])
            } else {
                connectedGattClients.values.toList()
            }

            for (gatt in clientGatts) {
                val service = gatt.getService(MESH_SERVICE_UUID) ?: continue
                val characteristic = service.getCharacteristic(MESH_CHARACTERISTIC_UUID) ?: continue
                
                for (chunk in chunks) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(characteristic, chunk, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    } else {
                        characteristic.value = chunk
                        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                        gatt.writeCharacteristic(characteristic)
                    }
                    delay(50) 
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending BLE data: ${e.message}")
        }
    }

    override fun setOnPacketReceived(callback: suspend (MeshPacket, String) -> Unit) {
        onPacketReceived = callback
    }

    override fun setOnPeerConnected(callback: suspend (String, String, String) -> Unit) {
        onPeerConnected = callback
    }

    override fun setOnPeerDisconnected(callback: suspend (String) -> Unit) {
        onPeerDisconnected = callback
    }

    // ══════════════════════════════════════════════════════════════
    // GATT Server (for receiving data from other devices)
    // ══════════════════════════════════════════════════════════════

    @Suppress("MissingPermission")
    private fun startGattServer() {
        val service = BluetoothGattService(
            MESH_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val messageCharacteristic = BluetoothGattCharacteristic(
            MESH_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_WRITE or
                    BluetoothGattCharacteristic.PERMISSION_READ
        )

        val nameCharacteristic = BluetoothGattCharacteristic(
            MESH_NAME_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        service.addCharacteristic(messageCharacteristic)
        service.addCharacteristic(nameCharacteristic)

        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
        gattServer?.addService(service)

        Log.i(TAG, "GATT Server started")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            val address = device.address
            when (newState) {
                BluetoothGattServer.STATE_CONNECTED -> {
                    Log.i(TAG, "BLE device connected: $address")
                    connectedDevices[address] = device
                    scope.launch {
                        onPeerConnected?.invoke(address, address, "BLE-$address")
                    }
                }
                BluetoothGattServer.STATE_DISCONNECTED -> {
                    Log.i(TAG, "BLE device disconnected: $address")
                    connectedDevices.remove(address)
                    scope.launch {
                        onPeerDisconnected?.invoke(address)
                    }
                }
            }
        }

        @Suppress("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (characteristic.uuid == MESH_CHARACTERISTIC_UUID && value != null) {
                // Try to parse as a complete packet
                val packet = parseMeshPacket(value)
                if (packet != null) {
                    scope.launch {
                        onPacketReceived?.invoke(packet, device.address)
                    }
                } else {
                    // Buffer for chunked messages
                    val now = System.currentTimeMillis()
                    val existing = messageBuffer[device.address]?.second ?: byteArrayOf()
                    val combined = existing + value
                    val parsed = parseMeshPacket(combined)
                    if (parsed != null) {
                        messageBuffer.remove(device.address)
                        scope.launch {
                            onPacketReceived?.invoke(parsed, device.address)
                        }
                    } else {
                        messageBuffer[device.address] = Pair(now, combined)
                    }
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        @Suppress("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == MESH_NAME_CHARACTERISTIC_UUID) {
                val nameBytes = localMeshId.toByteArray(Charsets.UTF_8)
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, nameBytes)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // BLE Advertising
    // ══════════════════════════════════════════════════════════════

    @Suppress("MissingPermission")
    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
            .setIncludeDeviceName(false) // Save space
            .build()

        bleAdvertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "BLE advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "BLE advertising failed: $errorCode")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // BLE Scanning
    // ══════════════════════════════════════════════════════════════

    private fun startScanning() {
        scanJob = scope.launch {
            while (isActive) {
                try {
                    performScan()
                } catch (e: SecurityException) {
                    Log.e(TAG, "BLE scan permission denied: ${e.message}")
                    break
                }
                delay(SCAN_INTERVAL_MS)
            }
        }
    }

    @Suppress("MissingPermission")
    private suspend fun performScan() {
        if (bleScanner == null) return

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(MESH_SERVICE_UUID))
                .build()
        )

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED) // Better for battery and background
            .setReportDelay(0)
            .build()

        try {
            bleScanner?.startScan(filters, settings, scanCallback)
            delay(SCAN_PERIOD_MS) // Scan for this duration
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
             Log.e(TAG, "Error during BLE scan: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @Suppress("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val address = device.address

            // 1. Immediately show as "Discovered" in UI
            scope.launch {
                 val displayName = if (device.name.isNullOrBlank()) "BLE Peer" else device.name
                 onPeerConnected?.invoke(address, address, displayName)
            }

            // 2. Attempt to connect if not already connected
            if (!connectedGattClients.containsKey(address)) {
                Log.i(TAG, "Found MeshTalk BLE device: $address (RSSI: ${result.rssi})")
                
                // Connect
                device.connectGatt(context, false, object : BluetoothGattCallback() {
                    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                        if (newState == BluetoothGatt.STATE_CONNECTED) {
                            connectedGattClients[address] = gatt
                            gatt.discoverServices()
                             // Update UI to "Connected" (handled by existing logic, but ensuring it fires)
                            scope.launch {
                                onPeerConnected?.invoke(address, address, "BLE-$address")
                            }
                        } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                            connectedGattClients.remove(address)
                            gatt.close()
                            scope.launch {
                                onPeerDisconnected?.invoke(address)
                            }
                        }
                    }

                    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            Log.i(TAG, "Services discovered on $address")
                        }
                    }
                })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed: $errorCode")
        }
    }

    private fun startBufferCleanup() {
        scope.launch {
            while (isActive) {
                delay(60_000) // Every minute
                val now = System.currentTimeMillis()
                val cleanupThreshold = now - 30_000 // Remove incomplete packets older than 30s
                
                val iterator = messageBuffer.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (entry.value.first < cleanupThreshold) {
                        iterator.remove()
                    }
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
        connectedDevices.clear()
        connectedGattClients.values.forEach { it.close() }
        connectedGattClients.clear()
        messageBuffer.clear()
    }
}

