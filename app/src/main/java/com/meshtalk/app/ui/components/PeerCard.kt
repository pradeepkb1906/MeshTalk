package com.meshtalk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshtalk.app.data.model.ConnectionState
import com.meshtalk.app.data.model.Peer
import com.meshtalk.app.ui.theme.*

@Composable
fun PeerCard(
    peer: Peer,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            PeerAvatar(
                name = peer.displayName,
                colorIndex = peer.avatarColor,
                isConnected = peer.connectionState == ConnectionState.CONNECTED ||
                        peer.connectionState == ConnectionState.AUTHENTICATED
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = peer.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConnectionStateChip(peer.connectionState)

                    if (peer.hopDistance > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${peer.hopDistance} hop${if (peer.hopDistance > 1) "s" else ""} away",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                if (peer.deviceName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = peer.deviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            // Signal strength indicator
            if (peer.signalStrength > 0) {
                SignalStrengthIndicator(peer.signalStrength)
            }

            // Action icons
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (peer.connectionState == ConnectionState.CONNECTED) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = "Chat",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PeerAvatar(
    name: String,
    colorIndex: Int,
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    size: Int = 48
) {
    val avatarColor = AvatarColors.getOrElse(colorIndex % AvatarColors.size) { AvatarColors[0] }
    val initials = name.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2)
        .joinToString("")
        .ifEmpty { "?" }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(size.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        // Online indicator
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(MeshConnected)
            )
        }
    }
}

@Composable
fun ConnectionStateChip(state: ConnectionState) {
    val (color, text) = when (state) {
        ConnectionState.CONNECTED -> MeshConnected to "Connected"
        ConnectionState.AUTHENTICATED -> MeshConnected to "Verified"
        ConnectionState.CONNECTING -> MeshDiscovered to "Connecting..."
        ConnectionState.DISCOVERED -> MeshDiscovered to "Discovered"
        ConnectionState.DISCONNECTED -> MeshDisconnected to "Offline"
        ConnectionState.LOST -> MeshDisconnected to "Lost"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun SignalStrengthIndicator(strength: Int) {
    val bars = (strength / 25).coerceIn(0, 4)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.padding(8.dp)
    ) {
        for (i in 0..3) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(((i + 1) * 5).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (i < bars) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
            )
        }
    }
}

