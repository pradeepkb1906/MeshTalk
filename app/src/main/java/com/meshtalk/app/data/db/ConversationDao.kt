package com.meshtalk.app.data.db

import androidx.room.*
import com.meshtalk.app.data.model.Conversation
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("""
        SELECT * FROM conversations 
        ORDER BY isPinned DESC, lastMessageTime DESC
    """)
    fun getAllConversations(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): Conversation?

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<Conversation?>

    @Query("SELECT * FROM conversations WHERE peerId = :peerId LIMIT 1")
    suspend fun getByPeerId(peerId: String): Conversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: Conversation)

    @Update
    suspend fun update(conversation: Conversation)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :conversationId")
    suspend fun clearUnread(conversationId: String)

    @Query("""
        UPDATE conversations 
        SET lastMessage = :message, lastMessageTime = :timestamp, 
            unreadCount = unreadCount + CASE WHEN :incrementUnread THEN 1 ELSE 0 END
        WHERE id = :conversationId
    """)
    suspend fun updateLastMessage(
        conversationId: String,
        message: String,
        timestamp: Long,
        incrementUnread: Boolean
    )

    @Delete
    suspend fun delete(conversation: Conversation)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteById(conversationId: String)

    @Query("SELECT SUM(unreadCount) FROM conversations WHERE isMuted = 0")
    fun getTotalUnreadCount(): Flow<Int?>
}

