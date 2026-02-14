package com.meshtalk.app.mesh.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.util.Log
import com.meshtalk.app.data.preferences.UserPreferences
import com.meshtalk.app.mesh.MeshPacket
import com.meshtalk.app.mesh.parseMeshPacket
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════
 * WifiDirectTransport — High-bandwidth WiFi Direct mesh transport
 * ══════════════════════════════════════════════════════════════════════
 *
 * WiFi Direct (P2P) provides:
 * - High bandwidth (~250 Mbps) for media transfer
 * - Range up to ~200 meters
 * - Works without WiFi infrastructure
 * - Supports file/stream transfer
 *
 * Used primarily for:
 * - Sending images and audio messages
 * - Large file transfers
 * - High-throughput scenarios
 *
 * Complements BLE (for discovery) and Nearby (for general messaging).
 */
@Singleton
class WifiDirectTransport @Inject constructor(
    private val context: Context,
    private val userPreferences: UserPreferences
) : Transport {

    companion object {
        private const val TAG = "WifiDirectTransport"
        private const val MESH_PORT = 8765
        private const val SOCKET_TIMEOUT = 10_000
        private const val DISCOVERY_INTERVAL_MS = 30_000L
    }

    override val name: String = "WiFi Direct"
    override var isActive: Boolean = false
        private set

    @Suppress("MissingPermission")
    private val wifiP2pManager by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val connectedPeers = ConcurrentHashMap<String, WifiP2pDevice>()
    private var serverSocket: ServerSocket? = null
    private var isGroupOwner = false
    private var groupOwnerAddress: String? = null

    private var onPacketReceived: (suspend (MeshPacket, String) -> Unit)? = null
    private var onPeerConnected: (suspend (String, String, String) -> Unit)? = null
    private var onPeerDisconnected: (suspend (String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var localMeshId: String = ""

    override suspend fun start() {
        localMeshId = userPreferences.getMeshIdSync()
        Log.i(TAG, "Starting WiFi Direct Transport")

        try {
            channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
            registerReceiver()
            startDiscovery()
            startServer()
            isActive = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WiFi Direct: ${e.message}")
        }
    }

    override suspend fun stop() {
        Log.i(TAG, "Stopping WiFi Direct Transport")
        isActive = false

        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }

        try {
            wifiP2pManager?.removeGroup(channel, null)
            wifiP2pManager?.stopPeerDiscovery(channel, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied stopping WiFi Direct: ${e.message}")
        }

        serverSocket?.close()
        connectedPeers.clear()
    }

    override suspend fun sendPacket(packet: MeshPacket, endpointId: String?) {
        val bytes = packet.toBytes()
        sendBytes(bytes, endpointId)
    }

    override suspend fun sendBytes(data: ByteArray, endpointId: String?) {
        val targetAddress = if (endpointId != null) {
            endpointId
        } else {
            groupOwnerAddress
        }

        if (targetAddress == null) {
            Log.w(TAG, "No target address for WiFi Direct send")
            return
        }

        scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(targetAddress, MESH_PORT), SOCKET_TIMEOUT)
                val outputStream = socket.getOutputStream()
                // Write length prefix
                val sizeBytes = data.size.toLittleEndianBytes()
                outputStream.write(sizeBytes)
                outputStream.write(data)
                outputStream.flush()
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send via WiFi Direct: ${e.message}")
            }
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
    // WiFi P2P Discovery
    // ══════════════════════════════════════════════════════════════

    private fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            @Suppress("MissingPermission")
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            Log.i(TAG, "WiFi P2P is enabled")
                        } else {
                            Log.w(TAG, "WiFi P2P is disabled")
                        }
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        wifiP2pManager?.requestPeers(channel) { peerList ->
                            handlePeersChanged(peerList)
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(
                            WifiP2pManager.EXTRA_NETWORK_INFO
                        )
                        if (networkInfo?.isConnected == true) {
                            wifiP2pManager?.requestConnectionInfo(channel) { info ->
                                handleConnectionInfo(info)
                            }
                        }
                    }
                }
            }
        }

        context.registerReceiver(receiver, intentFilter)
    }

    @Suppress("MissingPermission")
    private fun startDiscovery() {
        scope.launch {
            while (isActive) {
                try {
                    wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d(TAG, "WiFi Direct discovery started")
                        }

                        override fun onFailure(reason: Int) {
                            Log.e(TAG, "WiFi Direct discovery failed: $reason")
                        }
                    })
                } catch (e: SecurityException) {
                    Log.e(TAG, "WiFi Direct discovery permission denied")
                    break
                }
                delay(DISCOVERY_INTERVAL_MS)
            }
        }
    }

    @Suppress("MissingPermission")
    private fun handlePeersChanged(peerList: WifiP2pDeviceList) {
        val devices = peerList.deviceList
        Log.i(TAG, "WiFi Direct peers found: ${devices.size}")

        for (device in devices) {
            if (!connectedPeers.containsKey(device.deviceAddress)) {
                Log.i(TAG, "New WiFi Direct peer: ${device.deviceName} (${device.deviceAddress})")

                // Auto-connect to MeshTalk peers
                val config = WifiP2pConfig().apply {
                    deviceAddress = device.deviceAddress
                }

                try {
                    wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            connectedPeers[device.deviceAddress] = device
                            scope.launch {
                                onPeerConnected?.invoke(
                                    device.deviceAddress,
                                    device.deviceAddress,
                                    device.deviceName
                                )
                            }
                        }

                        override fun onFailure(reason: Int) {
                            Log.e(TAG, "WiFi Direct connect failed: $reason")
                        }
                    })
                } catch (e: SecurityException) {
                    Log.e(TAG, "WiFi Direct connect permission denied")
                }
            }
        }
    }

    private fun handleConnectionInfo(info: WifiP2pInfo) {
        isGroupOwner = info.isGroupOwner
        groupOwnerAddress = info.groupOwnerAddress?.hostAddress

        Log.i(TAG, "WiFi Direct connected - isGroupOwner=$isGroupOwner, address=$groupOwnerAddress")

        if (isGroupOwner) {
            // Already running server
            Log.i(TAG, "We are the group owner, server is ready")
        } else if (groupOwnerAddress != null) {
            // Connect to the group owner's server
            Log.i(TAG, "Connecting to group owner at $groupOwnerAddress")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // TCP Server for data exchange over WiFi Direct
    // ══════════════════════════════════════════════════════════════

    private fun startServer() {
        scope.launch {
            try {
                serverSocket = ServerSocket(MESH_PORT)
                Log.i(TAG, "WiFi Direct server started on port $MESH_PORT")

                while (isActive) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (isActive) Log.e(TAG, "Server accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WiFi Direct server: ${e.message}")
            }
        }
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            val inputStream = socket.getInputStream()
            val address = socket.inetAddress.hostAddress ?: "unknown"

            // Read length prefix (4 bytes, little-endian)
            val sizeBytes = ByteArray(4)
            inputStream.read(sizeBytes)
            val size = sizeBytes.fromLittleEndianInt()

            if (size in 1..10_000_000) { // Max 10MB
                val data = ByteArray(size)
                var read = 0
                while (read < size) {
                    val n = inputStream.read(data, read, size - read)
                    if (n == -1) break
                    read += n
                }

                val packet = parseMeshPacket(data)
                if (packet != null) {
                    onPacketReceived?.invoke(packet, address)
                }
            }

            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling WiFi Direct client: ${e.message}")
        }
    }

    fun destroy() {
        scope.cancel()
        serverSocket?.close()
        connectedPeers.clear()
    }
}

// Extension functions for little-endian byte conversion
private fun Int.toLittleEndianBytes(): ByteArray {
    return byteArrayOf(
        (this and 0xFF).toByte(),
        (this shr 8 and 0xFF).toByte(),
        (this shr 16 and 0xFF).toByte(),
        (this shr 24 and 0xFF).toByte()
    )
}

private fun ByteArray.fromLittleEndianInt(): Int {
    return (this[0].toInt() and 0xFF) or
            ((this[1].toInt() and 0xFF) shl 8) or
            ((this[2].toInt() and 0xFF) shl 16) or
            ((this[3].toInt() and 0xFF) shl 24)
}

