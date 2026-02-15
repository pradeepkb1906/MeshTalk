package com.meshtalk.app.mesh.transport

import android.util.Log
import com.meshtalk.app.data.model.TransportType
import com.meshtalk.app.mesh.MeshPacket
import com.meshtalk.app.mesh.MeshRouter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════
 * TransportManager — Orchestrates all mesh transport layers
 * ══════════════════════════════════════════════════════════════════════
 *
 * Manages multiple simultaneous transports and routes data between them
 * and the MeshRouter. This is the bridge between the physical communication
 * layer and the logical routing layer.
 *
 * TRANSPORT PRIORITY:
 * ┌───────────────────────────────────────────────────────────────┐
 * │  1. Nearby Connections (PRIMARY)                              │
 * │     - Auto BLE discovery + WiFi Direct upgrade                │
 * │     - Best overall for mesh networking                        │
 * │                                                               │
 * │  2. BLE Direct (SECONDARY)                                    │
 * │     - Low power background discovery                          │
 * │     - Good for text messages                                  │
 * │                                                               │
 * │  3. WiFi Direct (MEDIA)                                       │
 * │     - High bandwidth for media files                          │
 * │     - Used when large payloads need transfer                  │
 * └───────────────────────────────────────────────────────────────┘
 *
 * The TransportManager:
 * - Starts/stops transports based on availability
 * - Deduplicates data from multiple transports
 * - Routes outgoing data through the best available transport
 * - Reports combined connectivity status
 */
@Singleton
class TransportManager @Inject constructor(
    private val nearbyTransport: NearbyTransport,
    private val bleTransport: BleTransport,
    private val wifiDirectTransport: WifiDirectTransport,
    private val wifiAwareTransport: WifiAwareTransport,
    private val ultrasonicTransport: UltrasonicTransport,
    private val meshRouter: MeshRouter
) {
    companion object {
        private const val TAG = "TransportManager"
        private const val PEER_ANNOUNCE_INTERVAL_MS = 60_000L // 1 minute
    }

    private val _connectionStatus = MutableStateFlow(MeshConnectionStatus())
    val connectionStatus: StateFlow<MeshConnectionStatus> = _connectionStatus.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val transports = mutableListOf<Transport>()

    /**
     * Initialize and start all available transports.
     */
    suspend fun startAll() {
        Log.i(TAG, "Starting all mesh transports...")

        // Initialize router
        meshRouter.initialize()

        // Set the router's send callback to use our broadcast method
        meshRouter.setSendCallback { packet, endpointId, transportType ->
            sendThroughTransports(packet, endpointId, transportType)
        }

        // Configure all transports with common callbacks
        val allTransports = listOf(
            nearbyTransport, 
            bleTransport, 
            wifiDirectTransport,
            wifiAwareTransport,
            ultrasonicTransport
        )

        for (transport in allTransports) {
            configureTransport(transport)
        }

        // Start Nearby first (primary transport)
        try {
            nearbyTransport.start()
            transports.add(nearbyTransport)
            Log.i(TAG, "Nearby Connections transport started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Nearby transport: ${e.message}")
        }

        // Start BLE (secondary/fallback)
        try {
            bleTransport.start()
            transports.add(bleTransport)
            Log.i(TAG, "BLE transport started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start BLE transport: ${e.message}")
        }

        // Start WiFi Direct (for media)
        try {
            wifiDirectTransport.start()
            transports.add(wifiDirectTransport)
            Log.i(TAG, "WiFi Direct transport started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start WiFi Direct transport: ${e.message}")
        }
        
        // Start WiFi Aware (NAN)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                wifiAwareTransport.start()
                transports.add(wifiAwareTransport)
                Log.i(TAG, "WiFi Aware transport started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start WiFi Aware transport: ${e.message}")
            }
        }
        
        // Start Ultrasonic
        try {
            ultrasonicTransport.start()
            transports.add(ultrasonicTransport)
            Log.i(TAG, "Ultrasonic transport started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Ultrasonic transport: ${e.message}")
        }

        // Start periodic peer announcements
        scope.launch {
            while (isActive) {
                delay(PEER_ANNOUNCE_INTERVAL_MS)
                try {
                    meshRouter.broadcastPeerAnnouncement()
                } catch (e: Exception) {
                    Log.e(TAG, "Error broadcasting peer announcement: ${e.message}")
                }
            }
        }

        updateStatus()
        Log.i(TAG, "TransportManager started with ${transports.size} active transports")
    }

    /**
     * Stop all transports.
     */
    suspend fun stopAll() {
        Log.i(TAG, "Stopping all mesh transports...")

        for (transport in transports) {
            try {
                transport.stop()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping ${transport.name}: ${e.message}")
            }
        }
        transports.clear()
        updateStatus()
    }

    /**
     * Send a packet through all active transports.
     */
    /**
     * Send a packet through transports.
     * If transportType is specified, sends ONLY via that transport.
     * If transportType is null, broadcasts via ALL active transports.
     */
    private suspend fun sendThroughTransports(
        packet: MeshPacket, 
        endpointId: String?, 
        transportType: TransportType? = null
    ) {
        if (transportType != null) {
            // Targeted send
            val transport = transports.find { it.type == transportType }
            if (transport?.isActive == true) {
                try {
                    transport.sendPacket(packet, endpointId)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending via ${transport.name}: ${e.message}")
                }
            }
        } else {
            // Broadcast/Flood
            for (transport in transports) {
                if (transport.isActive) {
                    try {
                        transport.sendPacket(packet, endpointId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending via ${transport.name}: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Configure a transport with common callbacks.
     */
    private fun configureTransport(transport: Transport) {
        transport.setOnPacketReceived { packet, fromEndpointId ->
            meshRouter.handleIncomingPacket(packet, fromEndpointId)
            updateStatus()
        }

        transport.setOnPeerConnected { endpointId, meshId, displayName ->
            meshRouter.onPeerConnected(meshId, endpointId, displayName)
            updateStatus()
        }

        transport.setOnPeerDisconnected { endpointId ->
            meshRouter.onPeerDisconnected(endpointId)
            updateStatus()
        }
    }

    private fun updateStatus() {
        val activeTransports = transports.filter { it.isActive }.map { it.name }
        val connectedPeers = nearbyTransport.getConnectedCount()

        _connectionStatus.value = MeshConnectionStatus(
            isActive = transports.any { it.isActive },
            activeTransports = activeTransports,
            connectedPeerCount = connectedPeers,
            nearbyActive = nearbyTransport.isActive,
            bleActive = bleTransport.isActive,
            wifiDirectActive = wifiDirectTransport.isActive,
            wifiAwareActive = wifiAwareTransport.isActive,
            ultrasonicActive = ultrasonicTransport.isActive
        )
    }

    fun destroy() {
        scope.cancel()
        nearbyTransport.destroy()
        bleTransport.destroy()
        wifiDirectTransport.destroy()
        wifiAwareTransport.destroy()
        ultrasonicTransport.destroy()
        meshRouter.destroy()
    }
}

/**
 * Combined status of all mesh transports.
 */
data class MeshConnectionStatus(
    val isActive: Boolean = false,
    val activeTransports: List<String> = emptyList(),
    val connectedPeerCount: Int = 0,
    val nearbyActive: Boolean = false,
    val bleActive: Boolean = false,
    val wifiDirectActive: Boolean = false,
    val wifiAwareActive: Boolean = false,
    val ultrasonicActive: Boolean = false
)

