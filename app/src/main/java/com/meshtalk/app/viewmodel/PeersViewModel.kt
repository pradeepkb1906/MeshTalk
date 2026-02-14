package com.meshtalk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshtalk.app.data.db.PeerDao
import com.meshtalk.app.data.model.Peer
import com.meshtalk.app.mesh.MeshRouter
import com.meshtalk.app.mesh.transport.MeshConnectionStatus
import com.meshtalk.app.mesh.transport.TransportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PeersViewModel @Inject constructor(
    private val peerDao: PeerDao,
    private val meshRouter: MeshRouter,
    private val transportManager: TransportManager
) : ViewModel() {

    val peers: StateFlow<List<Peer>> = peerDao.getAllPeers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionStatus: StateFlow<MeshConnectionStatus> = transportManager.connectionStatus

    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        // Simulate scanning indicator
        viewModelScope.launch {
            delay(10_000)
            _isScanning.value = false
        }

        // Broadcast peer announcement on open
        viewModelScope.launch {
            meshRouter.broadcastPeerAnnouncement()
        }
    }

    fun refreshPeers() {
        viewModelScope.launch {
            _isScanning.value = true
            meshRouter.broadcastPeerAnnouncement()
            delay(10_000)
            _isScanning.value = false
        }
    }
}

