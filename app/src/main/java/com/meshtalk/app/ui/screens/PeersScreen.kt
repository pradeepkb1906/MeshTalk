package com.meshtalk.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtalk.app.data.model.ConnectionState
import com.meshtalk.app.ui.components.MeshStatusBar
import com.meshtalk.app.ui.components.PeerCard
import com.meshtalk.app.ui.theme.*
import com.meshtalk.app.viewmodel.PeersViewModel

@Composable
fun PeersScreen(
    onBack: () -> Unit,
    onPeerChat: (String, String) -> Unit, // meshId, displayName
    viewModel: PeersViewModel = hiltViewModel()
) {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()

    val connectedPeers = peers.filter {
        it.connectionState == ConnectionState.CONNECTED ||
                it.connectionState == ConnectionState.AUTHENTICATED
    }
    val discoveredPeers = peers.filter {
        it.connectionState == ConnectionState.DISCOVERED ||
                it.connectionState == ConnectionState.CONNECTING
    }
    val offlinePeers = peers.filter {
        it.connectionState == ConnectionState.DISCONNECTED ||
                it.connectionState == ConnectionState.LOST
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("Nearby Peers", fontWeight = FontWeight.Bold)
                },
                actions = {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = { viewModel.refreshPeers() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mesh Status
            MeshStatusBar(
                status = connectionStatus,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (peers.isEmpty()) {
                // Scanning state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Scanning for Peers...",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Looking for nearby MeshTalk users\nvia Bluetooth, WiFi Direct & Nearby",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "💡 Tips for better discovery:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("• Enable Bluetooth & WiFi", style = MaterialTheme.typography.bodySmall)
                                Text("• Enable Location services", style = MaterialTheme.typography.bodySmall)
                                Text("• Move closer to other devices", style = MaterialTheme.typography.bodySmall)
                                Text("• Ask friends to open MeshTalk", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Connected peers
                    if (connectedPeers.isNotEmpty()) {
                        item {
                            PeerSectionHeader(
                                title = "Connected",
                                count = connectedPeers.size,
                                color = MeshConnected
                            )
                        }
                        items(
                            items = connectedPeers,
                            key = { "c_${it.meshId}" }
                        ) { peer ->
                            PeerCard(
                                peer = peer,
                                onClick = { onPeerChat(peer.meshId, peer.displayName) }
                            )
                        }
                    }

                    // Discovered peers
                    if (discoveredPeers.isNotEmpty()) {
                        item {
                            PeerSectionHeader(
                                title = "Discovered",
                                count = discoveredPeers.size,
                                color = MeshDiscovered
                            )
                        }
                        items(
                            items = discoveredPeers,
                            key = { "d_${it.meshId}" }
                        ) { peer ->
                            PeerCard(
                                peer = peer,
                                onClick = { onPeerChat(peer.meshId, peer.displayName) }
                            )
                        }
                    }

                    // Offline peers
                    if (offlinePeers.isNotEmpty()) {
                        item {
                            PeerSectionHeader(
                                title = "Previously Seen",
                                count = offlinePeers.size,
                                color = MeshDisconnected
                            )
                        }
                        items(
                            items = offlinePeers,
                            key = { "o_${it.meshId}" }
                        ) { peer ->
                            PeerCard(
                                peer = peer,
                                onClick = { onPeerChat(peer.meshId, peer.displayName) }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PeerSectionHeader(
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

