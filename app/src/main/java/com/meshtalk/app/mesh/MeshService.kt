package com.meshtalk.app.mesh

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.meshtalk.app.MainActivity
import com.meshtalk.app.MeshTalkApp
import com.meshtalk.app.R
import com.meshtalk.app.data.model.MeshMessage
import com.meshtalk.app.mesh.transport.MeshConnectionStatus
import com.meshtalk.app.mesh.transport.TransportManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

/**
 * ══════════════════════════════════════════════════════════════════════
 * MeshService — Persistent foreground service for mesh networking
 * ══════════════════════════════════════════════════════════════════════
 *
 * This service runs continuously in the foreground to maintain
 * mesh network connectivity. It:
 * - Keeps all transport layers active
 * - Handles incoming/outgoing mesh packets
 * - Shows a persistent notification with mesh status
 * - Survives activity lifecycle changes
 * - Auto-restarts if killed by the system
 */
@AndroidEntryPoint
class MeshService : Service() {

    companion object {
        private const val TAG = "MeshService"
        private const val NOTIFICATION_ID = 1001
        private const val MESSAGE_NOTIFICATION_BASE_ID = 2000

        const val ACTION_START = "com.meshtalk.START_MESH"
        const val ACTION_STOP = "com.meshtalk.STOP_MESH"
    }

    @Inject lateinit var transportManager: TransportManager
    @Inject lateinit var meshRouter: MeshRouter

    private val binder = MeshBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    // ══════════════════════════════════════════════════════════════
    // Service Lifecycle
    // ══════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MeshService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMesh()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundService()
                if (!isRunning) {
                    startMesh()
                }
            }
        }
        return START_STICKY // Restart if killed
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopMesh()
        serviceScope.cancel()
        Log.i(TAG, "MeshService destroyed")
    }

    // ══════════════════════════════════════════════════════════════
    // Mesh Networking Control
    // ══════════════════════════════════════════════════════════════

    private fun startMesh() {
        isRunning = true
        serviceScope.launch {
            try {
                Log.i(TAG, "Starting mesh networking...")
                transportManager.startAll()

                // Listen for status updates to update notification
                launch {
                    transportManager.connectionStatus.collectLatest { status ->
                        updateNotification(status)
                    }
                }

                // Listen for incoming messages to show notifications
                launch {
                    meshRouter.incomingMessages.collectLatest { message ->
                        showMessageNotification(message)
                    }
                }

                // Listen for SOS alerts
                launch {
                    meshRouter.statusUpdates.collectLatest { status ->
                        when (status) {
                            is RouterStatus.SOSReceived -> {
                                showSOSNotification(status.senderName, status.message)
                            }
                            else -> {}
                        }
                    }
                }

                Log.i(TAG, "Mesh networking started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start mesh: ${e.message}", e)
            }
        }
    }

    private fun stopMesh() {
        isRunning = false
        serviceScope.launch {
            try {
                transportManager.stopAll()
                Log.i(TAG, "Mesh networking stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping mesh: ${e.message}")
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Foreground Notification
    // ══════════════════════════════════════════════════════════════

    private fun startForegroundService() {
        val notification = buildStatusNotification(MeshConnectionStatus())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildStatusNotification(status: MeshConnectionStatus): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MeshService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (status.isActive) {
            val transports = status.activeTransports.joinToString(", ")
            "Active • ${status.connectedPeerCount} peers • $transports"
        } else {
            "Starting mesh network..."
        }

        return NotificationCompat.Builder(this, MeshTalkApp.CHANNEL_MESH_SERVICE)
            .setContentTitle("MeshTalk Active")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_mesh_network)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(status: MeshConnectionStatus) {
        val notification = buildStatusNotification(status)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun showMessageNotification(message: MeshMessage) {
        if (message.isOutgoing) return // Don't notify for our own messages

        val pendingIntent = PendingIntent.getActivity(
            this,
            message.packetId.hashCode(),
            Intent(this, MainActivity::class.java).apply {
                putExtra("conversationId", message.conversationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, MeshTalkApp.CHANNEL_MESSAGES)
            .setContentTitle(message.senderName)
            .setContentText(message.content.take(200))
            .setSmallIcon(R.drawable.ic_message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            MESSAGE_NOTIFICATION_BASE_ID + message.packetId.hashCode() % 1000,
            notification
        )
    }

    private fun showSOSNotification(senderName: String, message: String) {
        val notification = NotificationCompat.Builder(this, MeshTalkApp.CHANNEL_MESSAGES)
            .setContentTitle("🆘 SOS from $senderName")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_sos)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(9999, notification)
    }

    // ══════════════════════════════════════════════════════════════
    // Binder for Activity communication
    // ══════════════════════════════════════════════════════════════

    inner class MeshBinder : Binder() {
        fun getService(): MeshService = this@MeshService

        fun getConnectionStatus(): StateFlow<MeshConnectionStatus> =
            transportManager.connectionStatus

        fun getRouter(): MeshRouter = meshRouter
    }
}

