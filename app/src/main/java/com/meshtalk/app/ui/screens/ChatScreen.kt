package com.meshtalk.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtalk.app.data.model.MessageType
import com.meshtalk.app.ui.components.MessageBubble
import com.meshtalk.app.ui.components.PeerAvatar
import com.meshtalk.app.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(
    conversationId: String,
    peerId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val peerInfo by viewModel.peerInfo.collectAsStateWithLifecycle()

    var messageText by remember { mutableStateOf("") }
    var showAttachMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Initialize ViewModel with conversation data
    LaunchedEffect(conversationId, peerId) {
        viewModel.initialize(conversationId, peerId)
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (peerInfo != null) {
                            PeerAvatar(
                                name = peerInfo!!.displayName,
                                colorIndex = peerInfo!!.avatarColor,
                                isConnected = true,
                                size = 36
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column {
                            Text(
                                text = conversation?.peerName ?: peerId.take(12),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (peerInfo != null) {
                                Text(
                                    text = when {
                                        peerInfo!!.hopDistance == 0 -> "Direct connection"
                                        else -> "${peerInfo!!.hopDistance} hops away"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { /* Send location */ }) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Share Location")
                    }
                    IconButton(onClick = { /* More options */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            )
        },
        bottomBar = {
            ChatInputBar(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText.trim())
                        messageText = ""
                        coroutineScope.launch {
                            if (messages.isNotEmpty()) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                    }
                },
                onAttach = { showAttachMenu = true },
                onVoiceRecord = { viewModel.sendMessage("[Voice Message]", MessageType.AUDIO) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (messages.isEmpty()) {
                // Empty chat state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Start the conversation!",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Messages travel through the mesh network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.packetId }
                    ) { message ->
                        MessageBubble(
                            message = message,
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
            }
        }
    }

    // Attachment bottom sheet
    if (showAttachMenu) {
        AttachmentBottomSheet(
            onDismiss = { showAttachMenu = false },
            onImageSelect = { /* Handle image */ showAttachMenu = false },
            onFileSelect = { /* Handle file */ showAttachMenu = false },
            onLocationShare = {
                viewModel.sendMessage("Location shared", MessageType.LOCATION)
                showAttachMenu = false
            }
        )
    }
}

@Composable
private fun ChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onVoiceRecord: () -> Unit
) {
    Surface(
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attachment button
            IconButton(onClick = onAttach) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Text input
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message via mesh...") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Send or Voice button
            if (messageText.isNotBlank()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // SMS Fallback Button
                    IconButton(
                        onClick = {
                            val smsIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("sms:")
                                putExtra("sms_body", messageText)
                            }
                            context.startActivity(smsIntent)
                        }
                    ) {
                        Icon(
                            Icons.Default.Sms,
                            contentDescription = "SMS",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }

                    FilledIconButton(
                        onClick = onSend,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            } else {
                FilledIconButton(
                    onClick = onVoiceRecord,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = "Voice",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentBottomSheet(
    onDismiss: () -> Unit,
    onImageSelect: () -> Unit,
    onFileSelect: () -> Unit,
    onLocationShare: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Share via Mesh",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentOption(
                    icon = Icons.Default.Image,
                    label = "Image",
                    color = MaterialTheme.colorScheme.primary,
                    onClick = onImageSelect
                )
                AttachmentOption(
                    icon = Icons.Default.AttachFile,
                    label = "File",
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = onFileSelect
                )
                AttachmentOption(
                    icon = Icons.Default.LocationOn,
                    label = "Location",
                    color = MaterialTheme.colorScheme.tertiary,
                    onClick = onLocationShare
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "💡 Tip: Images are compressed for mesh transfer. Keep files under 1MB for best delivery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.15f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

