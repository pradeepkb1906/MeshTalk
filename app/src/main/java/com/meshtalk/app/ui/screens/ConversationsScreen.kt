package com.meshtalk.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtalk.app.data.model.Conversation
import com.meshtalk.app.data.model.MessageType
import com.meshtalk.app.ui.components.MeshStatusBar
import com.meshtalk.app.ui.components.SOSButton
import com.meshtalk.app.ui.theme.*
import com.meshtalk.app.viewmodel.ConversationsViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ConversationsScreen(
    onConversationClick: (String, String) -> Unit, // conversationId, peerId
    onPeersClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    var showSOSDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Hub,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "MeshTalk",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onPeersClick) {
                        BadgedBox(
                            badge = {
                                if (connectionStatus.connectedPeerCount > 0) {
                                    Badge {
                                        Text("${connectionStatus.connectedPeerCount}")
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.People, contentDescription = "Peers")
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // SOS Button
                SOSButton(onClick = { showSOSDialog = true })

                Spacer(modifier = Modifier.height(12.dp))

                // New broadcast message
                FloatingActionButton(
                    onClick = { onConversationClick("broadcast", "BROADCAST") },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.CellTower, contentDescription = "Broadcast")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Mesh Status Bar
            MeshStatusBar(
                status = connectionStatus,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (conversations.isEmpty()) {
                // Empty state
                EmptyConversationsState(
                    onPeersClick = onPeersClick,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(
                        items = conversations,
                        key = { it.id }
                    ) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = {
                                onConversationClick(conversation.id, conversation.peerId)
                            }
                        )
                    }
                }
            }
        }
    }

    // SOS Dialog
    if (showSOSDialog) {
        SOSDialog(
            onDismiss = { showSOSDialog = false },
            onSend = { message ->
                viewModel.sendSOS(message)
                showSOSDialog = false
            }
        )
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    onClick: () -> Unit
) {
    val avatarColor = AvatarColors.getOrElse(conversation.peerAvatarColor % AvatarColors.size) { AvatarColors[0] }
    val initials = if (conversation.isBroadcast) {
        "BC"
    } else {
        conversation.peerName.split(" ")
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .take(2)
            .joinToString("")
            .ifEmpty { "?" }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (conversation.isBroadcast) MeshSecondary else avatarColor
                ),
            contentAlignment = Alignment.Center
        ) {
            if (conversation.isBroadcast) {
                Icon(
                    Icons.Default.CellTower,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp)
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (conversation.isBroadcast) "Broadcast Channel" else conversation.peerName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (conversation.lastMessageTime > 0) {
                    Text(
                        text = formatConversationTime(conversation.lastMessageTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (conversation.unreadCount > 0)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Last message preview with type icon
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val typeIcon = when (conversation.lastMessageType) {
                        MessageType.AUDIO -> Icons.Default.Mic
                        MessageType.IMAGE -> Icons.Default.Image
                        MessageType.FILE -> Icons.Default.AttachFile
                        MessageType.LOCATION -> Icons.Default.LocationOn
                        MessageType.SOS -> Icons.Default.Warning
                        else -> null
                    }
                    if (typeIcon != null) {
                        Icon(
                            typeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = conversation.lastMessage.ifBlank { "No messages yet" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Unread badge
                if (conversation.unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = if (conversation.unreadCount > 99) "99+" else "${conversation.unreadCount}",
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(start = 80.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}

@Composable
private fun EmptyConversationsState(
    onPeersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Forum,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Conversations Yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Find nearby peers to start chatting\nwithout internet!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onPeersClick,
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Find Peers")
        }
    }
}

@Composable
private fun SOSDialog(
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var message by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = SOSRed,
                modifier = Modifier.size(40.dp)
            )
        },
        title = {
            Text(
                "Send SOS Alert",
                fontWeight = FontWeight.Bold,
                color = SOSRed
            )
        },
        text = {
            Column {
                Text(
                    "This will broadcast an emergency alert to ALL nearby MeshTalk users across the entire mesh network.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Emergency message (optional)") },
                    placeholder = { Text("I need help!") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(message.ifBlank { "SOS - I need help!" }) },
                colors = ButtonDefaults.buttonColors(containerColor = SOSRed)
            ) {
                Text("SEND SOS")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatConversationTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val sdf = when {
        diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault())
        diff < 604_800_000 -> SimpleDateFormat("EEE", Locale.getDefault())
        else -> SimpleDateFormat("MMM dd", Locale.getDefault())
    }
    return sdf.format(Date(timestamp))
}

