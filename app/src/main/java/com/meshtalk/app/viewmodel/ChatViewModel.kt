package com.meshtalk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshtalk.app.data.db.ConversationDao
import com.meshtalk.app.data.db.MessageDao
import com.meshtalk.app.data.db.PeerDao
import com.meshtalk.app.data.model.*
import com.meshtalk.app.mesh.MeshRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val peerDao: PeerDao,
    private val meshRouter: MeshRouter
) : ViewModel() {

    private val _conversationId = MutableStateFlow("")
    private val _peerId = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MeshMessage>> = _conversationId
        .filter { it.isNotBlank() }
        .flatMapLatest { id ->
            messageDao.getMessagesForConversation(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val conversation: StateFlow<Conversation?> = _conversationId
        .filter { it.isNotBlank() }
        .flatMapLatest { id ->
            conversationDao.observeById(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val peerInfo: StateFlow<Peer?> = _peerId
        .filter { it.isNotBlank() }
        .flatMapLatest { id ->
            peerDao.observePeer(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun initialize(conversationId: String, peerId: String) {
        _conversationId.value = conversationId
        _peerId.value = peerId

        // Mark messages as read
        viewModelScope.launch {
            messageDao.markAllRead(conversationId)
            conversationDao.clearUnread(conversationId)
        }
    }

    fun sendMessage(content: String, type: MessageType = MessageType.TEXT) {
        viewModelScope.launch {
            val destinationId = _peerId.value
            if (destinationId.isNotBlank()) {
                meshRouter.sendMessage(
                    destinationId = destinationId,
                    content = content,
                    type = type
                )
            }
        }
    }

    fun sendLocationMessage(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val content = "📍 $latitude, $longitude"
            sendMessage(content, MessageType.LOCATION)
        }
    }
}

