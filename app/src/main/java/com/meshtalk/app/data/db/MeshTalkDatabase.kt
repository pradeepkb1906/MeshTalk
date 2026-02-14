package com.meshtalk.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.meshtalk.app.data.model.Conversation
import com.meshtalk.app.data.model.MeshMessage
import com.meshtalk.app.data.model.Peer

/**
 * Room database for MeshTalk.
 *
 * Stores all messages, peer information, and conversation data locally.
 * This is the single source of truth for all mesh communication data.
 */
@Database(
    entities = [
        MeshMessage::class,
        Peer::class,
        Conversation::class
    ],
    version = 1,
    exportSchema = true
)
abstract class MeshTalkDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun peerDao(): PeerDao
    abstract fun conversationDao(): ConversationDao
}

