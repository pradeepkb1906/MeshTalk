package com.meshtalk.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

/**
 * MeshTalk Application class.
 *
 * Initializes Hilt dependency injection and sets up notification channels
 * for the mesh networking foreground service.
 */
@HiltAndroidApp
class MeshTalkApp : Application() {

    companion object {
        const val CHANNEL_MESH_SERVICE = "mesh_service"
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_PEER_DISCOVERY = "peer_discovery"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val meshServiceChannel = NotificationChannel(
            CHANNEL_MESH_SERVICE,
            "Mesh Network Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps mesh networking active for offline communication"
            setShowBadge(false)
        }

        val messagesChannel = NotificationChannel(
            CHANNEL_MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "New message notifications"
            enableVibration(true)
            enableLights(true)
        }

        val peerChannel = NotificationChannel(
            CHANNEL_PEER_DISCOVERY,
            "Peer Discovery",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when new mesh peers are discovered"
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannels(
            listOf(meshServiceChannel, messagesChannel, peerChannel)
        )
    }
}

