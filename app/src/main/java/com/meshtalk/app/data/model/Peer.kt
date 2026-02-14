package com.meshtalk.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a peer device in the mesh network.
 *
 * Peers are discovered via various transports (BLE, WiFi Direct, Nearby).
 * Each peer maintains connection state, signal quality, and routing info.
 */
@Entity(
    tableName = "peers",
    indices = [
        Index(value = ["meshId"], unique = true),
        Index(value = ["lastSeen"])
    ]
)
@Serializable
data class Peer(
    @PrimaryKey
    val meshId: String,                 // Unique mesh network identifier
    val displayName: String,            // User-chosen display name
    val deviceName: String = "",        // Device model name
    val endpointId: String? = null,     // Nearby Connections endpoint ID
    val bleAddress: String? = null,     // BLE MAC address
    val connectionState: ConnectionState = ConnectionState.DISCOVERED,
    val transport: TransportType = TransportType.UNKNOWN,
    val signalStrength: Int = 0,        // Signal quality (0-100)
    val hopDistance: Int = 0,           // How many hops away (0 = direct)
    val latitude: Double = 0.0,         // Last known GPS latitude
    val longitude: Double = 0.0,        // Last known GPS longitude
    val lastSeen: Long = 0,            // Last communication timestamp
    val firstSeen: Long = 0,           // First discovery timestamp
    val messagesRelayed: Int = 0,       // Number of messages relayed through this peer
    val isBlocked: Boolean = false,     // User has blocked this peer
    val isFavorite: Boolean = false,    // User has favorited this peer
    val avatarColor: Int = 0            // Auto-assigned color for avatar
)

@Serializable
enum class ConnectionState {
    DISCOVERED,     // Found via scan but not connected
    CONNECTING,     // Connection in progress
    CONNECTED,      // Actively connected
    AUTHENTICATED,  // Connected and verified
    DISCONNECTED,   // Previously connected, now offline
    LOST            // Not seen for extended period
}

@Serializable
enum class TransportType {
    NEARBY,         // Google Nearby Connections
    BLE,            // Bluetooth Low Energy
    WIFI_DIRECT,    // WiFi Direct P2P
    WIFI_AWARE,     // WiFi Aware (NAN)
    NFC,            // Near Field Communication
    ULTRASONIC,     // Audio-based ultrasonic
    MULTI,          // Multiple transports active
    UNKNOWN         // Transport not determined
}

