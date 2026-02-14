package com.meshtalk.app.data.db

import androidx.room.*
import com.meshtalk.app.data.model.MeshMessage
import com.meshtalk.app.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MeshMessage>>

    @Query("SELECT * FROM messages WHERE packetId = :packetId LIMIT 1")
    suspend fun getByPacketId(packetId: String): MeshMessage?

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE packetId = :packetId)")
    suspend fun exists(packetId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MeshMessage): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<MeshMessage>)

    @Update
    suspend fun update(message: MeshMessage)

    @Query("UPDATE messages SET status = :status WHERE packetId = :packetId")
    suspend fun updateStatus(packetId: String, status: MessageStatus)

    @Query("UPDATE messages SET isRead = 1 WHERE conversationId = :conversationId AND isRead = 0")
    suspend fun markAllRead(conversationId: String)

    @Query("SELECT * FROM messages WHERE status = :status ORDER BY timestamp ASC")
    suspend fun getMessagesByStatus(status: MessageStatus): List<MeshMessage>

    @Query("SELECT * FROM messages WHERE destinationId = :peerId AND status != 'DELIVERED' AND status != 'READ' ORDER BY timestamp ASC")
    suspend fun getUndeliveredForPeer(peerId: String): List<MeshMessage>

    @Query("""
        SELECT * FROM messages 
        WHERE hopCount < maxHops 
        AND status IN ('SENT', 'RELAYED') 
        AND timestamp > :since
        ORDER BY timestamp ASC
    """)
    suspend fun getRelayableMessages(since: Long): List<MeshMessage>

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND isRead = 0 AND isOutgoing = 0")
    fun getUnreadCount(conversationId: String): Flow<Int>

    @Query("DELETE FROM messages WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Delete
    suspend fun delete(message: MeshMessage)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteConversationMessages(conversationId: String)
}

