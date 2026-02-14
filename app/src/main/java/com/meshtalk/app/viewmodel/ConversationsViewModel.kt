package com.meshtalk.app.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshtalk.app.data.db.ConversationDao
import com.meshtalk.app.data.model.Conversation
import com.meshtalk.app.data.model.MessageType
import com.meshtalk.app.data.preferences.UserPreferences
import com.meshtalk.app.mesh.MeshRouter
import com.meshtalk.app.mesh.MeshService
import com.meshtalk.app.mesh.transport.MeshConnectionStatus
import com.meshtalk.app.mesh.transport.TransportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ConversationDao,
    private val meshRouter: MeshRouter,
    private val transportManager: TransportManager,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val conversations: StateFlow<List<Conversation>> =
        conversationDao.getAllConversations()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionStatus: StateFlow<MeshConnectionStatus> = transportManager.connectionStatus

    init {
        // Start the mesh service
        startMeshService()
    }

    private fun startMeshService() {
        val intent = Intent(context, MeshService::class.java).apply {
            action = MeshService.ACTION_START
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            // May fail if app doesn't have the right permissions yet
        }
    }

    fun sendSOS(message: String) {
        viewModelScope.launch {
            meshRouter.sendSOS(message)
        }
    }
}

