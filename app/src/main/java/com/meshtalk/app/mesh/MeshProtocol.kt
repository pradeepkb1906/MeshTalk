package com.meshtalk.app.mesh

import com.meshtalk.app.data.model.MessageType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * ══════════════════════════════════════════════════════════════════════
 * MeshTalk Mesh Protocol v1.0
 * ══════════════════════════════════════════════════════════════════════
 *
 * This defines the wire protocol for all mesh network communication.
 *
 * ARCHITECTURE:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                     MeshPacket Structure                        │
 * ├─────────────┬──────────────────────────────────────────────────┤
 * │ Header      │ packetId, version, type, senderId, destinationId│
 * │             │ sourceId, hopCount, maxHops, timestamp           │
 * ├─────────────┼──────────────────────────────────────────────────┤
 * │ Routing     │ previousHop, routePath[]                         │
 * ├─────────────┼──────────────────────────────────────────────────┤
 * │ Payload     │ content (text/metadata), mediaChunk, mediaInfo  │
 * ├─────────────┼──────────────────────────────────────────────────┤
 * │ Auth        │ senderName, signature                           │
 * └─────────────┴──────────────────────────────────────────────────┘
 *
 * ROUTING ALGORITHM:
 * 1. Epidemic/Flood routing with duplicate detection
 * 2. TTL-based hop limiting (default 7 hops)
 * 3. Store-and-forward for offline peers
 * 4. Route path recording for loop prevention
 * 5. ACK propagation for delivery confirmation
 */

private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}

/**
 * The core packet that travels through the mesh network.
 * Every piece of data in MeshTalk is wrapped in a MeshPacket.
 */
@Serializable
data class MeshPacket(
    // === Header ===
    val packetId: String,               // Globally unique packet identifier
    val version: Int = PROTOCOL_VERSION, // Protocol version for compatibility
    val type: PacketType,               // What kind of packet this is
    val senderId: String,               // Mesh ID of the original sender
    val senderName: String,             // Human-readable sender name
    val destinationId: String,          // Target mesh ID or BROADCAST_ADDRESS

    // === Routing ===
    val hopCount: Int = 0,              // Current number of hops taken
    val maxHops: Int = DEFAULT_MAX_HOPS, // Maximum hops before packet is dropped
    val timestamp: Long,                // Creation time (epoch millis)
    val previousHop: String = "",       // Mesh ID of the node that forwarded this
    val routePath: List<String> = emptyList(), // Ordered list of mesh IDs this packet traversed

    // === Payload ===
    val contentType: MessageType = MessageType.TEXT,
    val content: String = "",           // Text content or JSON metadata
    val mediaInfo: MediaInfo? = null,   // Info about attached media (if any)

    // === Acknowledgment ===
    val ackForPacketId: String? = null  // If this is an ACK, which packet it acknowledges
) {
    companion object {
        const val PROTOCOL_VERSION = 1
        const val DEFAULT_MAX_HOPS = 7
        const val BROADCAST_ADDRESS = "BROADCAST"
        const val SOS_ADDRESS = "SOS_BROADCAST"
        const val MAX_CONTENT_SIZE = 64 * 1024 // 64KB text content limit
        const val MAX_MEDIA_CHUNK_SIZE = 256 * 1024 // 256KB per media chunk
    }

    /**
     * Check if this packet has expired (exceeded max hops).
     */
    fun isExpired(): Boolean = hopCount >= maxHops

    /**
     * Check if this is a broadcast packet.
     */
    fun isBroadcast(): Boolean = destinationId == BROADCAST_ADDRESS || destinationId == SOS_ADDRESS

    /**
     * Create a forwarded copy of this packet with incremented hop count.
     */
    fun forwarded(relayerMeshId: String): MeshPacket = copy(
        hopCount = hopCount + 1,
        previousHop = relayerMeshId,
        routePath = routePath + relayerMeshId
    )

    /**
     * Check if a given mesh ID has already seen/relayed this packet.
     */
    fun hasVisited(meshId: String): Boolean = meshId in routePath || senderId == meshId

    /**
     * Serialize to bytes for transport.
     */
    fun toBytes(): ByteArray = json.encodeToString(this).toByteArray(Charsets.UTF_8)
}

/**
 * Metadata about media attached to a message.
 */
@Serializable
data class MediaInfo(
    val fileName: String,
    val mimeType: String,
    val totalSize: Long,
    val chunkIndex: Int = 0,
    val totalChunks: Int = 1,
    val checksum: String = ""  // MD5 for integrity verification
)

/**
 * Types of packets in the mesh protocol.
 */
@Serializable
enum class PacketType {
    MESSAGE,        // Regular user message (text, audio, image, file)
    ACK,            // Delivery acknowledgment
    PEER_ANNOUNCE,  // "I exist" broadcast for discovery
    PEER_LEAVE,     // "I'm going offline" notification
    PING,           // Health check / keepalive
    PONG,           // Response to ping
    ROUTE_REQUEST,  // Request route to a specific peer
    ROUTE_REPLY,    // Response with route information
    MEDIA_CHUNK,    // Chunk of a larger media file
    SOS,            // Emergency SOS broadcast (highest priority)
    RELAY_TABLE     // Share known peers/routes with neighbors
}

/**
 * Peer announcement payload - broadcast periodically to announce presence.
 */
@Serializable
data class PeerAnnouncement(
    val meshId: String,
    val displayName: String,
    val deviceName: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val capabilities: List<String> = emptyList(), // "ble", "wifi_direct", "nearby", etc.
    val connectedPeerCount: Int = 0,
    val batteryLevel: Int = -1,
    val protocolVersion: Int = MeshPacket.PROTOCOL_VERSION
)

/**
 * Parse a MeshPacket from raw bytes.
 */
fun parseMeshPacket(bytes: ByteArray): MeshPacket? {
    return try {
        json.decodeFromString<MeshPacket>(String(bytes, Charsets.UTF_8))
    } catch (e: Exception) {
        null
    }
}

/**
 * Parse a PeerAnnouncement from JSON content.
 */
fun parsePeerAnnouncement(jsonContent: String): PeerAnnouncement? {
    return try {
        json.decodeFromString<PeerAnnouncement>(jsonContent)
    } catch (e: Exception) {
        null
    }
}

/**
 * Serialize a PeerAnnouncement to JSON.
 */
fun PeerAnnouncement.toJson(): String = json.encodeToString(this)

