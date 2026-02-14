package com.meshtalk.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Represents a message in the MeshTalk network.
 *
 * Messages are the core unit of communication. They can be text, audio,
 * images, or files. Each message carries routing information for the
 * mesh network including hop count and TTL.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["packetId"], unique = true),
        Index(value = ["timestamp"])
    ]
)
@Serializable
data class MeshMessage(
    @PrimaryKey
    val packetId: String,           // Unique message ID (UUID)
    val conversationId: String,      // Conversation this belongs to
    val senderId: String,            // Original sender's mesh ID
    val senderName: String,          // Human-readable sender name
    val destinationId: String,       // Target mesh ID or "BROADCAST"
    val type: MessageType,           // Type of message content
    val content: String,             // Text content or file metadata JSON
    val mediaFileName: String? = null,  // Original filename for media
    val mediaSize: Long = 0,         // Size of media attachment in bytes
    val mediaMimeType: String? = null,  // MIME type for media
    val timestamp: Long,             // Creation timestamp (epoch millis)
    val receivedAt: Long = 0,        // When we received this message
    val hopCount: Int = 0,           // How many hops this message has taken
    val maxHops: Int = 7,            // Maximum allowed hops (TTL)
    val status: MessageStatus = MessageStatus.SENDING,
    val isOutgoing: Boolean = true,  // Whether we sent this
    val isRead: Boolean = false      // Whether user has read this
)

@Serializable
enum class MessageType {
    TEXT,           // Plain text message
    AUDIO,          // Voice message / audio clip
    IMAGE,          // Compressed image
    FILE,           // Small file attachment
    LOCATION,       // GPS coordinates
    ACK,            // Delivery acknowledgment
    PEER_ANNOUNCE,  // Peer discovery announcement
    PING,           // Keepalive / mesh health check
    SOS             // Emergency broadcast
}

@Serializable
enum class MessageStatus {
    SENDING,        // Being sent to mesh
    SENT,           // Sent to at least one peer
    RELAYED,        // Forwarded by intermediate nodes
    DELIVERED,      // Received ACK from destination
    READ,           // Destination has read the message
    FAILED          // Failed to send after retries
}

