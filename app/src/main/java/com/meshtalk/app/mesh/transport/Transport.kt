package com.meshtalk.app.mesh.transport

import com.meshtalk.app.mesh.MeshPacket

/**
 * Base interface for all mesh transport mechanisms.
 *
 * MeshTalk uses multiple transport layers simultaneously to maximize
 * connectivity. Each transport handles discovery and data transfer
 * using a specific technology (BLE, WiFi Direct, Nearby Connections, etc.)
 *
 * TRANSPORT HIERARCHY (priority order):
 * 1. Google Nearby Connections — Best overall (auto BLE + WiFi upgrade)
 * 2. WiFi Direct — High bandwidth for media
 * 3. BLE Direct — Low power, always-on, short range
 * 4. Ultrasonic — Last resort when radios are restricted
 */
interface Transport {

    /** Human-readable name of this transport */
    val name: String

    /** Whether this transport is currently active and scanning */
    val isActive: Boolean

    /** Start the transport — begin advertising and discovering */
    suspend fun start()

    /** Stop the transport — cease all communication */
    suspend fun stop()

    /** Send a packet to a specific endpoint (null = broadcast to all) */
    suspend fun sendPacket(packet: MeshPacket, endpointId: String?)

    /** Send raw bytes to a specific endpoint */
    suspend fun sendBytes(data: ByteArray, endpointId: String?)

    /** Set the callback for received packets */
    fun setOnPacketReceived(callback: suspend (MeshPacket, String) -> Unit)

    /** Set the callback for peer connection events */
    fun setOnPeerConnected(callback: suspend (endpointId: String, meshId: String, displayName: String) -> Unit)

    /** Set the callback for peer disconnection events */
    fun setOnPeerDisconnected(callback: suspend (endpointId: String) -> Unit)
}

/**
 * Information about a connected transport endpoint.
 */
data class TransportEndpoint(
    val endpointId: String,
    val meshId: String,
    val displayName: String,
    val transportName: String,
    val isConnected: Boolean = true
)

