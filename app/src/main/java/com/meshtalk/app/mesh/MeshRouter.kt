package com.meshtalk.app.mesh

import android.util.Log
import com.meshtalk.app.data.db.ConversationDao
import com.meshtalk.app.data.db.MessageDao
import com.meshtalk.app.data.db.PeerDao
import com.meshtalk.app.data.model.*
import com.meshtalk.app.data.preferences.UserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════
 * MeshRouter — The brain of the mesh network
 * ══════════════════════════════════════════════════════════════════════
 *
 * Handles all routing logic:
 * - Duplicate detection (seen packet cache)
 * - Flood-based forwarding with TTL
 * - Store-and-forward for offline peers
 * - ACK generation and propagation
 * - Message persistence to local database
 * - Conversation management
 *
 * ROUTING ALGORITHM:
 * ┌──────────────────────────────────────────────────────────────┐
 * │  Packet Received                                             │
 * │  ├── Already seen? → DROP                                    │
 * │  ├── Expired (hop >= maxHops)? → DROP                        │
 * │  ├── Destination is ME? → DELIVER + send ACK                 │
 * │  ├── Broadcast? → DELIVER + FORWARD to all peers             │
 * │  └── Destination is OTHER? → FORWARD to all peers            │
 * │                                                              │
 * │  New Peer Connected                                          │
 * │  ├── Exchange peer announcements                             │
 * │  └── Forward cached undelivered messages                     │
 * └──────────────────────────────────────────────────────────────┘
 */
@Singleton
class MeshRouter @Inject constructor(
    private val messageDao: MessageDao,
    private val peerDao: PeerDao,
    private val conversationDao: ConversationDao,
    private val userPreferences: UserPreferences
) {
    companion object {
        private const val TAG = "MeshRouter"
        private const val SEEN_CACHE_MAX_SIZE = 10_000
        private const val SEEN_CACHE_TTL_MS = 3_600_000L // 1 hour
        private const val STORE_FORWARD_WINDOW_MS = 24 * 3_600_000L // 24 hours
    }

    // Cache of recently seen packet IDs to prevent re-processing
    private val seenPackets = ConcurrentHashMap<String, Long>()

    // Callback for sending packets out via transport layer
    private var sendCallback: (suspend (MeshPacket, String?, TransportType?) -> Unit)? = null

    // Flow of incoming messages for the UI
    private val _incomingMessages = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MeshMessage> = _incomingMessages.asSharedFlow()

    // Flow of status updates
    private val _statusUpdates = MutableSharedFlow<RouterStatus>(extraBufferCapacity = 16)
    val statusUpdates: SharedFlow<RouterStatus> = _statusUpdates.asSharedFlow()

    private var localMeshId: String = ""
    private var localDisplayName: String = ""
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Initialize the router with local identity.
     */
    suspend fun initialize() {
        localMeshId = userPreferences.getMeshIdSync()
        localDisplayName = userPreferences.getDisplayNameSync()
        Log.i(TAG, "MeshRouter initialized: meshId=$localMeshId, name=$localDisplayName")

        // Start periodic cache cleanup
        scope.launch {
            while (isActive) {
                delay(300_000) // Every 5 minutes
                cleanSeenCache()
            }
        }
    }

    /**
     * Register the callback used to send packets through the transport layer.
     */
    /**
     * Register the callback used to send packets through the transport layer.
     */
    fun setSendCallback(callback: suspend (MeshPacket, String?, TransportType?) -> Unit) {
        sendCallback = callback
    }

    /**
     * Handle an incoming packet from the transport layer.
     * This is the main entry point for all received mesh data.
     */
    suspend fun handleIncomingPacket(packet: MeshPacket, fromEndpointId: String?) {
        // Step 1: Duplicate detection
        if (isAlreadySeen(packet.packetId)) {
            Log.d(TAG, "Dropping duplicate packet: ${packet.packetId.take(8)}")
            return
        }
        markAsSeen(packet.packetId)

        // Step 2: TTL check
        if (packet.isExpired()) {
            Log.d(TAG, "Dropping expired packet: ${packet.packetId.take(8)} (hops=${packet.hopCount})")
            return
        }

        // Step 3: Loop detection
        if (packet.hasVisited(localMeshId)) {
            Log.d(TAG, "Dropping looped packet: ${packet.packetId.take(8)}")
            return
        }

        Log.i(TAG, "Processing packet: type=${packet.type}, from=${packet.senderId.take(8)}, " +
                "dest=${packet.destinationId.take(8)}, hops=${packet.hopCount}")

        // Step 4: Route based on packet type
        when (packet.type) {
            PacketType.MESSAGE -> handleMessage(packet)
            PacketType.ACK -> handleAck(packet)
            PacketType.PEER_ANNOUNCE -> handlePeerAnnounce(packet)
            PacketType.PEER_LEAVE -> handlePeerLeave(packet)
            PacketType.PING -> handlePing(packet)
            PacketType.PONG -> { /* Just note the peer is alive */ }
            PacketType.SOS -> handleSos(packet)
            PacketType.MEDIA_CHUNK -> handleMediaChunk(packet)
            PacketType.ROUTE_REQUEST -> handleRouteRequest(packet)
            PacketType.ROUTE_REPLY -> handleRouteReply(packet)
            PacketType.RELAY_TABLE -> handleRelayTable(packet)
        }
    }

    /**
     * Handle a regular message packet.
     */
    private suspend fun handleMessage(packet: MeshPacket) {
        val isForMe = packet.destinationId == localMeshId
        val isBroadcast = packet.isBroadcast()

        if (isForMe || isBroadcast) {
            // Deliver to local user
            deliverMessage(packet)

            // Send ACK if message was specifically for us (not broadcast)
            if (isForMe) {
                sendAck(packet)
            }
        }

        // Forward to other peers (flood routing)
        if (!isForMe || isBroadcast) {
            forwardPacket(packet)
        }
    }

    /**
     * Deliver a message to the local database and notify UI.
     */
    private suspend fun deliverMessage(packet: MeshPacket) {
        // Check if we already have this message
        if (messageDao.exists(packet.packetId)) return

        // Ensure conversation exists
        val conversationId = if (packet.isBroadcast()) {
            "broadcast"
        } else {
            getOrCreateConversation(packet.senderId, packet.senderName)
        }

        val message = MeshMessage(
            packetId = packet.packetId,
            conversationId = conversationId,
            senderId = packet.senderId,
            senderName = packet.senderName,
            destinationId = packet.destinationId,
            type = packet.contentType,
            content = packet.content,
            mediaFileName = packet.mediaInfo?.fileName,
            mediaSize = packet.mediaInfo?.totalSize ?: 0,
            mediaMimeType = packet.mediaInfo?.mimeType,
            timestamp = packet.timestamp,
            receivedAt = System.currentTimeMillis(),
            hopCount = packet.hopCount,
            maxHops = packet.maxHops,
            status = MessageStatus.DELIVERED,
            isOutgoing = false,
            isRead = false
        )

        messageDao.insert(message)

        // Update conversation
        val preview = when (packet.contentType) {
            MessageType.TEXT -> packet.content.take(100)
            MessageType.AUDIO -> "🎤 Voice message"
            MessageType.IMAGE -> "📷 Image"
            MessageType.FILE -> "📎 File: ${packet.mediaInfo?.fileName ?: "unknown"}"
            MessageType.LOCATION -> "📍 Location"
            MessageType.SOS -> "🆘 SOS: ${packet.content.take(100)}"
            else -> packet.content.take(100)
        }

        conversationDao.updateLastMessage(
            conversationId = conversationId,
            message = preview,
            timestamp = packet.timestamp,
            incrementUnread = true
        )

        _incomingMessages.emit(message)
        _statusUpdates.emit(RouterStatus.MessageReceived(message))

        Log.i(TAG, "Delivered message from ${packet.senderName}: ${preview.take(50)}")
    }

    /**
     * Send an ACK back to the sender.
     */
    private suspend fun sendAck(originalPacket: MeshPacket) {
        val ack = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            type = PacketType.ACK,
            senderId = localMeshId,
            senderName = localDisplayName,
            destinationId = originalPacket.senderId,
            timestamp = System.currentTimeMillis(),
            ackForPacketId = originalPacket.packetId
        )
        broadcastToAllPeers(ack)
    }

    /**
     * Handle delivery acknowledgment.
     */
    private suspend fun handleAck(packet: MeshPacket) {
        val ackFor = packet.ackForPacketId ?: return

        if (packet.destinationId == localMeshId) {
            // ACK is for us — update message status
            messageDao.updateStatus(ackFor, MessageStatus.DELIVERED)
            _statusUpdates.emit(RouterStatus.MessageDelivered(ackFor))
            Log.i(TAG, "Message delivered: ${ackFor.take(8)}")
        } else {
            // Forward ACK towards destination
            forwardPacket(packet)
        }
    }

    /**
     * Handle peer announcement.
     */
    private suspend fun handlePeerAnnounce(packet: MeshPacket) {
        val announcement = parsePeerAnnouncement(packet.content) ?: return

        val existingPeer = peerDao.getByMeshId(announcement.meshId)
        val peer = Peer(
            meshId = announcement.meshId,
            displayName = announcement.displayName,
            deviceName = announcement.deviceName,
            connectionState = if (packet.hopCount == 0) ConnectionState.CONNECTED else ConnectionState.DISCOVERED,
            hopDistance = packet.hopCount,
            latitude = announcement.latitude,
            longitude = announcement.longitude,
            lastSeen = System.currentTimeMillis(),
            firstSeen = existingPeer?.firstSeen ?: System.currentTimeMillis(),
            messagesRelayed = existingPeer?.messagesRelayed ?: 0,
            isBlocked = existingPeer?.isBlocked ?: false,
            isFavorite = existingPeer?.isFavorite ?: false,
            avatarColor = existingPeer?.avatarColor ?: (Math.random() * 12).toInt()
        )

        peerDao.upsert(peer)
        _statusUpdates.emit(RouterStatus.PeerDiscovered(peer))

        // Forward peer announcement
        if (!packet.isExpired()) {
            forwardPacket(packet)
        }

        // Check for stored messages to forward to this peer
        deliverStoredMessages(announcement.meshId)

        Log.i(TAG, "Peer discovered: ${announcement.displayName} (${announcement.meshId.take(8)}) at ${packet.hopCount} hops")
    }

    /**
     * Handle peer leaving the network.
     */
    private suspend fun handlePeerLeave(packet: MeshPacket) {
        peerDao.updateConnectionState(packet.senderId, ConnectionState.DISCONNECTED)
        forwardPacket(packet)
    }

    /**
     * Handle ping — respond with pong.
     */
    private suspend fun handlePing(packet: MeshPacket) {
        if (packet.destinationId == localMeshId) {
            val pong = MeshPacket(
                packetId = UUID.randomUUID().toString(),
                type = PacketType.PONG,
                senderId = localMeshId,
                senderName = localDisplayName,
                destinationId = packet.senderId,
                timestamp = System.currentTimeMillis()
            )
            broadcastToAllPeers(pong)
        } else {
            forwardPacket(packet)
        }
    }

    /**
     * Handle SOS emergency broadcast — always deliver and forward.
     */
    private suspend fun handleSos(packet: MeshPacket) {
        deliverMessage(packet)
        forwardPacket(packet)
        _statusUpdates.emit(RouterStatus.SOSReceived(packet.senderName, packet.content))
    }

    /**
     * Handle media chunk — reassemble if all chunks received.
     */
    private suspend fun handleMediaChunk(packet: MeshPacket) {
        // For now, treat small media as single-chunk messages
        handleMessage(packet)
    }

    /**
     * Handle route request.
     */
    private suspend fun handleRouteRequest(packet: MeshPacket) {
        // If we know the requested peer, send a route reply
        val targetId = packet.content // Target mesh ID is in content
        val knownPeer = peerDao.getByMeshId(targetId)
        if (knownPeer != null && knownPeer.connectionState == ConnectionState.CONNECTED) {
            val reply = MeshPacket(
                packetId = UUID.randomUUID().toString(),
                type = PacketType.ROUTE_REPLY,
                senderId = localMeshId,
                senderName = localDisplayName,
                destinationId = packet.senderId,
                timestamp = System.currentTimeMillis(),
                content = targetId
            )
            broadcastToAllPeers(reply)
        }
        forwardPacket(packet)
    }

    /**
     * Handle route reply.
     */
    private suspend fun handleRouteReply(packet: MeshPacket) {
        if (packet.destinationId == localMeshId) {
            Log.i(TAG, "Route found to ${packet.content.take(8)} via ${packet.senderId.take(8)}")
        } else {
            forwardPacket(packet)
        }
    }

    /**
     * Handle relay table sharing.
     */
    private suspend fun handleRelayTable(packet: MeshPacket) {
        // Parse peer list from content and update our known peers
        // This helps build a more complete view of the network
        Log.d(TAG, "Received relay table from ${packet.senderId.take(8)}")
    }

    // ══════════════════════════════════════════════════════════════
    // PUBLIC API — Used by MeshService and ViewModels
    // ══════════════════════════════════════════════════════════════

    /**
     * Send a text message to a specific peer or broadcast.
     */
    suspend fun sendMessage(
        destinationId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        mediaInfo: MediaInfo? = null
    ): MeshMessage {
        val packetId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val conversationId = if (destinationId == MeshPacket.BROADCAST_ADDRESS) {
            "broadcast"
        } else {
            val peer = peerDao.getByMeshId(destinationId)
            getOrCreateConversation(destinationId, peer?.displayName ?: "Unknown")
        }

        // Save to local database
        val message = MeshMessage(
            packetId = packetId,
            conversationId = conversationId,
            senderId = localMeshId,
            senderName = localDisplayName,
            destinationId = destinationId,
            type = type,
            content = content,
            mediaFileName = mediaInfo?.fileName,
            mediaSize = mediaInfo?.totalSize ?: 0,
            mediaMimeType = mediaInfo?.mimeType,
            timestamp = timestamp,
            receivedAt = timestamp,
            hopCount = 0,
            status = MessageStatus.SENDING,
            isOutgoing = true,
            isRead = true
        )
        messageDao.insert(message)

        // Update conversation
        val preview = when (type) {
            MessageType.TEXT -> content.take(100)
            MessageType.AUDIO -> "🎤 Voice message"
            MessageType.IMAGE -> "📷 Image"
            MessageType.FILE -> "📎 File"
            MessageType.LOCATION -> "📍 Location"
            MessageType.SOS -> "🆘 SOS: ${content.take(100)}"
            else -> content.take(100)
        }
        conversationDao.updateLastMessage(conversationId, preview, timestamp, false)

        // Create and send mesh packet
        val packet = MeshPacket(
            packetId = packetId,
            type = if (type == MessageType.SOS) PacketType.SOS else PacketType.MESSAGE,
            senderId = localMeshId,
            senderName = localDisplayName,
            destinationId = destinationId,
            timestamp = timestamp,
            contentType = type,
            content = content,
            mediaInfo = mediaInfo
        )

        markAsSeen(packetId)
        broadcastToAllPeers(packet)

        // Update status to SENT
        messageDao.updateStatus(packetId, MessageStatus.SENT)

        return message.copy(status = MessageStatus.SENT)
    }

    /**
     * Send SOS emergency broadcast.
     */
    suspend fun sendSOS(message: String): MeshMessage {
        return sendMessage(
            destinationId = MeshPacket.SOS_ADDRESS,
            content = message,
            type = MessageType.SOS
        )
    }

    /**
     * Broadcast peer announcement to all connected peers.
     */
    suspend fun broadcastPeerAnnouncement(latitude: Double = 0.0, longitude: Double = 0.0) {
        val connectedPeers = peerDao.getConnectedPeersList()
        val announcement = PeerAnnouncement(
            meshId = localMeshId,
            displayName = localDisplayName,
            deviceName = android.os.Build.MODEL,
            latitude = latitude,
            longitude = longitude,
            capabilities = listOf("nearby", "ble", "wifi_direct"),
            connectedPeerCount = connectedPeers.size
        )

        val packet = MeshPacket(
            packetId = UUID.randomUUID().toString(),
            type = PacketType.PEER_ANNOUNCE,
            senderId = localMeshId,
            senderName = localDisplayName,
            destinationId = MeshPacket.BROADCAST_ADDRESS,
            timestamp = System.currentTimeMillis(),
            content = announcement.toJson()
        )

        markAsSeen(packet.packetId)
        broadcastToAllPeers(packet)
    }

    /**
     * Called when a new peer connects — deliver any stored messages.
     */
    suspend fun onPeerConnected(meshId: String, endpointId: String, displayName: String) {
        val existingPeer = peerDao.getByMeshId(meshId)
        val peer = Peer(
            meshId = meshId,
            displayName = displayName,
            endpointId = endpointId,
            connectionState = ConnectionState.CONNECTED,
            transport = TransportType.NEARBY,
            hopDistance = 0,
            lastSeen = System.currentTimeMillis(),
            firstSeen = existingPeer?.firstSeen ?: System.currentTimeMillis(),
            avatarColor = existingPeer?.avatarColor ?: (Math.random() * 12).toInt()
        )
        peerDao.upsert(peer)
        _statusUpdates.emit(RouterStatus.PeerConnected(peer))

        // Deliver stored messages for this peer
        deliverStoredMessages(meshId)

        // Send our peer announcement
        broadcastPeerAnnouncement()
    }

    /**
     * Called when a peer disconnects.
     */
    suspend fun onPeerDisconnected(endpointId: String) {
        val peer = peerDao.getByEndpointId(endpointId)
        if (peer != null) {
            peerDao.updateConnectionState(peer.meshId, ConnectionState.DISCONNECTED)
            _statusUpdates.emit(RouterStatus.PeerDisconnected(peer))
        }
    }

    /**
     * Get or create a conversation for a peer.
     */
    private suspend fun getOrCreateConversation(peerId: String, peerName: String): String {
        val existing = conversationDao.getByPeerId(peerId)
        if (existing != null) return existing.id

        val conversationId = UUID.randomUUID().toString()
        val conversation = Conversation(
            id = conversationId,
            peerId = peerId,
            peerName = peerName,
            isBroadcast = peerId == MeshPacket.BROADCAST_ADDRESS
        )
        conversationDao.upsert(conversation)

        // Also ensure broadcast conversation exists
        if (peerId != MeshPacket.BROADCAST_ADDRESS && peerId != "broadcast") {
            ensureBroadcastConversation()
        }

        return conversationId
    }

    private suspend fun ensureBroadcastConversation() {
        if (conversationDao.getById("broadcast") == null) {
            conversationDao.upsert(
                Conversation(
                    id = "broadcast",
                    peerId = MeshPacket.BROADCAST_ADDRESS,
                    peerName = "Broadcast Channel",
                    isBroadcast = true
                )
            )
        }
    }

    /**
     * Deliver stored messages intended for a specific peer.
     */
    private suspend fun deliverStoredMessages(targetMeshId: String) {
        val undelivered = messageDao.getUndeliveredForPeer(targetMeshId)
        val peer = peerDao.getByMeshId(targetMeshId)
        
        for (msg in undelivered) {
            val packet = MeshPacket(
                packetId = msg.packetId,
                type = PacketType.MESSAGE,
                senderId = msg.senderId,
                senderName = msg.senderName,
                destinationId = msg.destinationId,
                timestamp = msg.timestamp,
                contentType = msg.type,
                content = msg.content
            )
            
            if (peer != null && peer.connectionState == ConnectionState.CONNECTED) {
                sendCallback?.invoke(packet, peer.endpointId, peer.transport)
            } else {
                sendCallback?.invoke(packet, null, null)
            }
        }
        if (undelivered.isNotEmpty()) {
            Log.i(TAG, "Delivered ${undelivered.size} stored messages to ${targetMeshId.take(8)}")
        }
    }

    // ══════════════════════════════════════════════════════════════
    // INTERNAL ROUTING HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Forward a packet to all connected peers.
     */
    private suspend fun forwardPacket(packet: MeshPacket) {
        if (packet.isExpired()) return

        val forwarded = packet.forwarded(localMeshId)
        broadcastToAllPeers(forwarded)

        Log.d(TAG, "Forwarded packet ${packet.packetId.take(8)} (hop ${forwarded.hopCount}/${forwarded.maxHops})")
    }

    /**
     * Send a packet to all connected peers via the transport layer.
     */
    /**
     * Send a packet. Tries unicast if destination is a direct neighbor, otherwise broadcasts.
     */
    private suspend fun broadcastToAllPeers(packet: MeshPacket) {
        try {
            if (packet.destinationId != MeshPacket.BROADCAST_ADDRESS && packet.destinationId != MeshPacket.SOS_ADDRESS) {
                // Try to find direct connection
                val peer = peerDao.getByMeshId(packet.destinationId)
                if (peer != null && peer.connectionState == ConnectionState.CONNECTED) {
                    // Unicast optimization
                    sendCallback?.invoke(packet, peer.endpointId, peer.transport)
                    return
                }
            }
            
            // Fallback to broadcast
            sendCallback?.invoke(packet, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting packet: ${e.message}")
        }
    }

    private fun isAlreadySeen(packetId: String): Boolean {
        return seenPackets.containsKey(packetId)
    }

    private fun markAsSeen(packetId: String) {
        seenPackets[packetId] = System.currentTimeMillis()
    }

    private fun cleanSeenCache() {
        val cutoff = System.currentTimeMillis() - SEEN_CACHE_TTL_MS
        val iterator = seenPackets.entries.iterator()
        var removed = 0
        while (iterator.hasNext()) {
            if (iterator.next().value < cutoff) {
                iterator.remove()
                removed++
            }
        }
        // Also trim if too large
        if (seenPackets.size > SEEN_CACHE_MAX_SIZE) {
            val entries = seenPackets.entries.sortedBy { it.value }
            val toRemove = entries.take(seenPackets.size - SEEN_CACHE_MAX_SIZE / 2)
            toRemove.forEach { seenPackets.remove(it.key) }
            removed += toRemove.size
        }
        if (removed > 0) {
            Log.d(TAG, "Cleaned $removed entries from seen cache (${seenPackets.size} remaining)")
        }
    }

    fun destroy() {
        scope.cancel()
        seenPackets.clear()
    }
}

/**
 * Status events emitted by the router.
 */
sealed class RouterStatus {
    data class MessageReceived(val message: MeshMessage) : RouterStatus()
    data class MessageDelivered(val packetId: String) : RouterStatus()
    data class PeerDiscovered(val peer: Peer) : RouterStatus()
    data class PeerConnected(val peer: Peer) : RouterStatus()
    data class PeerDisconnected(val peer: Peer) : RouterStatus()
    data class SOSReceived(val senderName: String, val message: String) : RouterStatus()
    data class Error(val message: String) : RouterStatus()
}

