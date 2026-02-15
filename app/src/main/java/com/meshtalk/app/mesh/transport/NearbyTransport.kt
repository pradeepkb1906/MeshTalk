package com.meshtalk.app.mesh.transport

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.meshtalk.app.data.model.TransportType
import com.meshtalk.app.data.preferences.UserPreferences
import com.meshtalk.app.mesh.MeshPacket
import com.meshtalk.app.mesh.parseMeshPacket
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════
 * NearbyTransport — Primary mesh transport using Google Nearby Connections
 * ══════════════════════════════════════════════════════════════════════
 *
 * Google Nearby Connections API is the IDEAL transport for mesh networking:
 * - Automatically uses BLE for discovery
 * - Upgrades to WiFi Direct or WiFi Hotspot for data transfer
 * - Works completely WITHOUT internet
 * - Handles connection management automatically
 * - Supports multiple simultaneous connections
 *
 * Strategy: P2P_CLUSTER
 * - All devices both advertise and discover simultaneously
 * - Supports many-to-many connections
 * - Perfect for mesh topology
 */
@Singleton
class NearbyTransport @Inject constructor(
    private val context: Context,
    private val userPreferences: UserPreferences
) : Transport {

    companion object {
        private const val TAG = "NearbyTransport"
        private const val SERVICE_ID = "com.meshtalk.mesh"
        private val STRATEGY = Strategy.P2P_CLUSTER
        private const val MAX_CONNECTIONS = 6
    }

    override val name: String = "Nearby Connections"
    override val type: TransportType = TransportType.NEARBY
    override var isActive: Boolean = false
        private set

    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }
    private val connectedEndpoints = ConcurrentHashMap<String, TransportEndpoint>()

    private var onPacketReceived: (suspend (MeshPacket, String) -> Unit)? = null
    private var onPeerConnected: (suspend (String, String, String) -> Unit)? = null
    private var onPeerDisconnected: (suspend (String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var localMeshId: String = ""
    private var localDisplayName: String = ""

    // ══════════════════════════════════════════════════════════════
    // Transport Interface Implementation
    // ══════════════════════════════════════════════════════════════

    override suspend fun start() {
        localMeshId = userPreferences.getMeshIdSync()
        localDisplayName = userPreferences.getDisplayNameSync()

        Log.i(TAG, "Starting Nearby Transport: meshId=${localMeshId.take(8)}, name=$localDisplayName")

        startAdvertising()
        startDiscovery()
        isActive = true
    }

    override suspend fun stop() {
        Log.i(TAG, "Stopping Nearby Transport")
        isActive = false
        connectionsClient.stopAdvertising()
        connectionsClient.stopDiscovery()
        connectionsClient.stopAllEndpoints()
        connectedEndpoints.clear()
    }

    override suspend fun sendPacket(packet: MeshPacket, endpointId: String?) {
        val bytes = packet.toBytes()
        sendBytes(bytes, endpointId)
    }

    override suspend fun sendBytes(data: ByteArray, endpointId: String?) {
        val payload = Payload.fromBytes(data)

        if (endpointId != null) {
            // Send to specific endpoint
            try {
                connectionsClient.sendPayload(endpointId, payload)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to $endpointId: ${e.message}")
            }
        } else {
            // Broadcast to all connected endpoints
            val endpoints = connectedEndpoints.keys.toList()
            for (ep in endpoints) {
                try {
                    connectionsClient.sendPayload(ep, Payload.fromBytes(data))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send to $ep: ${e.message}")
                    connectedEndpoints.remove(ep)
                }
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
    // Nearby Connections Advertising
    // ══════════════════════════════════════════════════════════════

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        // Encode mesh ID into the advertised name for discovery
        val advertiseName = "$localDisplayName|$localMeshId"

        connectionsClient.startAdvertising(
            advertiseName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.i(TAG, "Advertising started successfully")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start advertising: ${e.message}")
            // Retry after delay
            scope.launch {
                delay(5000)
                if (isActive) startAdvertising()
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Nearby Connections Discovery
    // ══════════════════════════════════════════════════════════════

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(STRATEGY)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.i(TAG, "Discovery started successfully")
        }.addOnFailureListener { e ->
            Log.e(TAG, "Failed to start discovery: ${e.message}")
            scope.launch {
                delay(5000)
                if (isActive) startDiscovery()
            }
        }
    }

    /**
     * Callback for discovering nearby endpoints.
     */
    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.i(TAG, "Endpoint found: $endpointId (${info.endpointName})")

            // Parse mesh ID from endpoint name
            val parts = info.endpointName.split("|")
            val peerName = parts.getOrElse(0) { "Unknown" }
            val peerMeshId = parts.getOrElse(1) { endpointId }

            // Don't connect to ourselves
            if (peerMeshId == localMeshId) return

            // Don't reconnect if already connected
            if (connectedEndpoints.containsKey(endpointId)) return

            // Limit maximum simultaneous connections to prevent stability issues
            if (connectedEndpoints.size >= MAX_CONNECTIONS) {
                Log.w(TAG, "Ignoring endpoint $endpointId: Max connections reached (${connectedEndpoints.size})")
                return
            }

            // Request connection
            connectionsClient.requestConnection(
                "$localDisplayName|$localMeshId",
                endpointId,
                connectionLifecycleCallback
            ).addOnSuccessListener {
                Log.i(TAG, "Connection requested to $peerName")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to request connection: ${e.message}")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.i(TAG, "Endpoint lost: $endpointId")
            val endpoint = connectedEndpoints.remove(endpointId)
            if (endpoint != null) {
                scope.launch { onPeerDisconnected?.invoke(endpointId) }
            }
        }
    }

    /**
     * Callback for connection lifecycle events.
     */
    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Log.i(TAG, "Connection initiated with: ${info.endpointName}")

            // Auto-accept all connections (mesh network is open)
            // In production, could add authentication here
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.i(TAG, "Connected to: $endpointId")

                    // Parse info from what we know
                    val endpoint = TransportEndpoint(
                        endpointId = endpointId,
                        meshId = endpointId, // Will be updated when we receive peer announcement
                        displayName = "Peer",
                        transportName = name
                    )
                    connectedEndpoints[endpointId] = endpoint

                    scope.launch {
                        onPeerConnected?.invoke(endpointId, endpointId, "Peer")
                    }
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.w(TAG, "Connection rejected by: $endpointId")
                }
                ConnectionsStatusCodes.STATUS_ERROR -> {
                    Log.e(TAG, "Connection error with: $endpointId")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.i(TAG, "Disconnected from: $endpointId")
            connectedEndpoints.remove(endpointId)
            scope.launch { onPeerDisconnected?.invoke(endpointId) }
        }
    }

    /**
     * Callback for received payloads (data).
     */
    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val bytes = payload.asBytes() ?: return
                    val packet = parseMeshPacket(bytes)
                    if (packet != null) {
                        // Update endpoint info with actual mesh ID
                        val existingEndpoint = connectedEndpoints[endpointId]
                        if (existingEndpoint != null && existingEndpoint.meshId == endpointId) {
                            connectedEndpoints[endpointId] = existingEndpoint.copy(
                                meshId = packet.senderId,
                                displayName = packet.senderName
                            )
                        }

                        scope.launch {
                            onPacketReceived?.invoke(packet, endpointId)
                        }
                    }
                }
                Payload.Type.STREAM -> {
                    Log.d(TAG, "Received stream payload from $endpointId")
                    // Handle streaming data (for larger files)
                }
                Payload.Type.FILE -> {
                    Log.d(TAG, "Received file payload from $endpointId")
                    // Handle file transfer
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Track transfer progress for large payloads
            when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Log.d(TAG, "Payload transfer complete for $endpointId")
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    Log.e(TAG, "Payload transfer failed for $endpointId")
                }
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    // Could emit progress updates
                }
                PayloadTransferUpdate.Status.CANCELED -> {
                    Log.d(TAG, "Payload transfer canceled for $endpointId")
                }
            }
        }
    }

    /**
     * Get the number of currently connected endpoints.
     */
    fun getConnectedCount(): Int = connectedEndpoints.size

    /**
     * Get all connected endpoint IDs.
     */
    fun getConnectedEndpoints(): List<TransportEndpoint> = connectedEndpoints.values.toList()

    fun destroy() {
        scope.cancel()
        connectedEndpoints.clear()
    }
}

