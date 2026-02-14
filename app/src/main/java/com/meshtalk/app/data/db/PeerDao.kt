package com.meshtalk.app.data.db

import androidx.room.*
import com.meshtalk.app.data.model.ConnectionState
import com.meshtalk.app.data.model.Peer
import kotlinx.coroutines.flow.Flow

@Dao
interface PeerDao {

    @Query("SELECT * FROM peers ORDER BY lastSeen DESC")
    fun getAllPeers(): Flow<List<Peer>>

    @Query("SELECT * FROM peers WHERE connectionState = 'CONNECTED' OR connectionState = 'AUTHENTICATED' ORDER BY lastSeen DESC")
    fun getConnectedPeers(): Flow<List<Peer>>

    @Query("SELECT * FROM peers WHERE connectionState = 'CONNECTED' OR connectionState = 'AUTHENTICATED'")
    suspend fun getConnectedPeersList(): List<Peer>

    @Query("SELECT * FROM peers WHERE meshId = :meshId LIMIT 1")
    suspend fun getByMeshId(meshId: String): Peer?

    @Query("SELECT * FROM peers WHERE meshId = :meshId LIMIT 1")
    fun observePeer(meshId: String): Flow<Peer?>

    @Query("SELECT * FROM peers WHERE endpointId = :endpointId LIMIT 1")
    suspend fun getByEndpointId(endpointId: String): Peer?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(peer: Peer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(peers: List<Peer>)

    @Update
    suspend fun update(peer: Peer)

    @Query("UPDATE peers SET connectionState = :state WHERE meshId = :meshId")
    suspend fun updateConnectionState(meshId: String, state: ConnectionState)

    @Query("UPDATE peers SET lastSeen = :timestamp WHERE meshId = :meshId")
    suspend fun updateLastSeen(meshId: String, timestamp: Long)

    @Query("UPDATE peers SET connectionState = 'DISCONNECTED' WHERE connectionState = 'CONNECTED' OR connectionState = 'AUTHENTICATED'")
    suspend fun disconnectAll()

    @Query("UPDATE peers SET connectionState = 'LOST' WHERE lastSeen < :threshold AND connectionState != 'LOST'")
    suspend fun markLostPeers(threshold: Long)

    @Query("SELECT COUNT(*) FROM peers WHERE connectionState = 'CONNECTED' OR connectionState = 'AUTHENTICATED'")
    fun getConnectedPeerCount(): Flow<Int>

    @Delete
    suspend fun delete(peer: Peer)

    @Query("DELETE FROM peers WHERE lastSeen < :threshold AND isFavorite = 0")
    suspend fun deleteOldPeers(threshold: Long)
}

