package com.meshtalk.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a conversation between the local user and a peer (or a group/broadcast).
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["peerId"]),
        Index(value = ["lastMessageTime"])
    ]
)
data class Conversation(
    @PrimaryKey
    val id: String,                     // Conversation ID
    val peerId: String,                 // Remote peer mesh ID (or "BROADCAST")
    val peerName: String,               // Display name of peer
    val lastMessage: String = "",       // Preview of last message
    val lastMessageTime: Long = 0,      // Timestamp of last message
    val lastMessageType: MessageType = MessageType.TEXT,
    val unreadCount: Int = 0,           // Number of unread messages
    val isPinned: Boolean = false,      // Whether conversation is pinned
    val isMuted: Boolean = false,       // Whether notifications are muted
    val isGroup: Boolean = false,       // Whether this is a group conversation
    val isBroadcast: Boolean = false,   // Whether this is a broadcast channel
    val peerAvatarColor: Int = 0,       // Color for peer avatar
    val createdAt: Long = System.currentTimeMillis()
)

