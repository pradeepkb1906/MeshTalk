package com.meshtalk.app.mesh.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.aware.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.meshtalk.app.data.model.TransportType
import com.meshtalk.app.data.preferences.UserPreferences
import com.meshtalk.app.mesh.MeshPacket
import com.meshtalk.app.mesh.parseMeshPacket
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ══════════════════════════════════════════════════════════════════════
 * WifiAwareTransport — Low-power Neighbor Awareness Networking (NAN)
 * ══════════════════════════════════════════════════════════════════════
 *
 * Wi-Fi Aware (Android 8.0+) allows devices to discover and connect
 * directly to each other without any other type of connectivity
 * (like Internet or GPS).
 *
 * Advantages:
 * - Longer range than BLE
 * - Higher throughput than BLE
 * - Lower power than full Wi-Fi Direct scans
 * - Works in dense environments
 */
@RequiresApi(Build.VERSION_CODES.O)
@Singleton
class WifiAwareTransport @Inject constructor(
    private val context: Context,
    private val userPreferences: UserPreferences
) : Transport {

    companion object {
        private const val TAG = "WifiAwareTransport"
        private const val SERVICE_NAME = "com.meshtalk.aware"
        private const val MESSAGE_BUFFER_SIZE = 1024
    }

    override val name: String = "WiFi Aware"
    override val type: TransportType = TransportType.WIFI_AWARE
    override var isActive: Boolean = false
        private set

    private val wifiAwareManager by lazy {
        context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    }
    
    private var attachCallback: AttachCallback? = null
    private var wifiAwareSession: WifiAwareSession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var subscribeSession: SubscribeDiscoverySession? = null
    
    // Map of PeerHandle (remote device) to Peer Info
    private val knownPeers = ConcurrentHashMap<Int, PeerHandle>()
    private val peerMeshIds = ConcurrentHashMap<Int, String>()

    private var onPacketReceived: (suspend (MeshPacket, String) -> Unit)? = null
    private var onPeerConnected: (suspend (String, String, String) -> Unit)? = null
    private var onPeerDisconnected: (suspend (String) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var localMeshId: String = ""

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED) {
                if (wifiAwareManager?.isAvailable == true) {
                    if (isActive && wifiAwareSession == null) {
                        tryAttach()
                    }
                } else {
                    Log.w(TAG, "WiFi Aware became unavailable")
                    // Handle loss of transport
                }
            }
        }
    }

    override suspend fun start() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            Log.w(TAG, "WiFi Aware not supported on this device")
            return
        }

        localMeshId = userPreferences.getMeshIdSync()
        Log.i(TAG, "Starting WiFi Aware Transport")
        
        context.registerReceiver(
            broadcastReceiver, 
            IntentFilter(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED)
        )

        if (wifiAwareManager?.isAvailable == true) {
            tryAttach()
        }
        
        isActive = true
    }

    private fun tryAttach() {
        attachCallback = object : AttachCallback() {
            override fun onAttached(session: WifiAwareSession) {
                Log.i(TAG, "Attached to WiFi Aware session")
                wifiAwareSession = session
                startPublishing(session)
                startSubscribing(session)
            }

            override fun onAttachFailed() {
                Log.e(TAG, "Failed to attach to WiFi Aware")
            }
        }

        try {
            wifiAwareManager?.attach(attachCallback!!, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach WiFi Aware: ${e.message}")
        }
    }

    private fun startPublishing(session: WifiAwareSession) {
        val config = PublishConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .build()

        try {
            session.publish(config, object : DiscoverySessionCallback() {
                override fun onPublishStarted(session: PublishDiscoverySession) {
                    Log.i(TAG, "Publishing started")
                    publishSession = session
                }

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    handleIncomingMessage(peerHandle, message)
                }
            }, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start publishing: ${e.message}")
        }
    }

    private fun startSubscribing(session: WifiAwareSession) {
        val config = SubscribeConfig.Builder()
            .setServiceName(SERVICE_NAME)
            .build()

        try {
            session.subscribe(config, object : DiscoverySessionCallback() {
                override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                    Log.i(TAG, "Subscribing started")
                    subscribeSession = session
                }

                override fun onServiceDiscovered(
                    peerHandle: PeerHandle,
                    serviceSpecificInfo: ByteArray,
                    matchFilter: List<ByteArray>
                ) {
                    Log.i(TAG, "Service discovered from peer ${peerHandle.hashCode()}")
                    knownPeers[peerHandle.hashCode()] = peerHandle
                    
                    // Send our ID to the discovered peer so they know who we are
                    sendIdentity(peerHandle)
                    
                    // Notify connection (initially with hash as ID until we get a packet)
                    scope.launch {
                        onPeerConnected?.invoke(
                            peerHandle.hashCode().toString(), 
                            "aware-${peerHandle.hashCode()}", 
                            "Aware Peer"
                        )
                    }
                }

                override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                    handleIncomingMessage(peerHandle, message)
                }
            }, null)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to start subscribing: ${e.message}")
        }
    }

    private fun handleIncomingMessage(peerHandle: PeerHandle, message: ByteArray) {
        // Optimistic parsing
        val packet = parseMeshPacket(message)
        if (packet != null) {
            val meshId = packet.senderId
            peerMeshIds[peerHandle.hashCode()] = meshId
            knownPeers[peerHandle.hashCode()] = peerHandle
            
            scope.launch {
                onPacketReceived?.invoke(packet, meshId)
                // Update connection info with real MeshID
                onPeerConnected?.invoke(meshId, meshId, packet.senderName)
            }
        }
    }
    
    private fun sendIdentity(peerHandle: PeerHandle) {
        // In a real implementation, we'd send a small handshake packet
        // For now, we rely on the first data packet to establish identity based on MeshPacket
    }

    override suspend fun stop() {
        Log.i(TAG, "Stopping WiFi Aware Transport")
        isActive = false
        
        publishSession?.close()
        subscribeSession?.close()
        wifiAwareSession?.close()
        
        try {
            context.unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            // Ignore
        }
        
        knownPeers.clear()
        peerMeshIds.clear()
    }

    override suspend fun sendPacket(packet: MeshPacket, endpointId: String?) {
        val bytes = packet.toBytes()
        sendBytes(bytes, endpointId)
    }

    override suspend fun sendBytes(data: ByteArray, endpointId: String?) {
        // WiFi Aware messages have a size limit (usually ~255 bytes for instant messages)
        // For larger data, we would need to setup a Network Interface (L2) which is more complex
        // This basic implementation supports small packets (control messages, short texts)
        
        if (data.size > 255) {
            Log.w(TAG, "Packet too large for WiFi Aware Message (${data.size} bytes). Use L2 for data.")
            // Filter logic here would typically route large packets to full WiFi Direct
            return
        }

        if (endpointId != null) {
            // Send to specific peer (looking up handle from meshID)
            val handleHash = peerMeshIds.entries.find { it.value == endpointId }?.key
            val handle = knownPeers[handleHash]
            
            if (handle != null) {
                trySendMessage(handle, data)
            }
        } else {
            // Broadcast to all known handles
            for (handle in knownPeers.values) {
                trySendMessage(handle, data)
            }
        }
    }

    private fun trySendMessage(handle: PeerHandle, data: ByteArray) {
        try {
            // Try sending via both sessions to ensure reachability
            publishSession?.sendMessage(handle, 0, data)
            subscribeSession?.sendMessage(handle, 0, data)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Aware message: ${e.message}")
        }
    }

    override fun setOnPacketReceived(callback: suspend (MeshPacket, String) -> Unit) {
        onPacketReceived = callback
    }

    override fun setOnPeerConnected(callback: suspend (String, String, String) -> Unit) {
        onPeerConnected = callback
    }

    override fun setOnPeerDisconnected(callback: suspend (String) -> Unit) {
        onPeerDisconnected = callback
    }
    
    fun destroy() {
        scope.cancel()
        knownPeers.clear()
        peerMeshIds.clear()
    }
}
