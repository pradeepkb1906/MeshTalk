package com.meshtalk.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.meshtalk.app.mesh.transport.MeshConnectionStatus
import com.meshtalk.app.ui.theme.*

/**
 * Top status bar showing mesh network connectivity.
 */
@Composable
fun MeshStatusBar(
    status: MeshConnectionStatus,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = if (status.isActive) MeshConnected else MeshDisconnected,
        label = "statusColor"
    )

    // Pulsing animation for active state
    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Animated status dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .scale(if (status.isActive) pulseScale else 1f)
                    .clip(CircleShape)
                    .background(statusColor)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Status text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (status.isActive) "Mesh Active" else "Mesh Inactive",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (status.isActive) {
                    Text(
                        text = status.activeTransports.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Peer count
            if (status.connectedPeerCount > 0) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MeshConnected.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = "Peers",
                            modifier = Modifier.size(16.dp),
                            tint = MeshConnected
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${status.connectedPeerCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MeshConnected,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Transport icons
            Spacer(modifier = Modifier.width(8.dp))
            if (status.nearbyActive) {
                Icon(
                    Icons.Default.WifiTethering,
                    contentDescription = "Nearby",
                    modifier = Modifier.size(16.dp),
                    tint = MeshPrimary
                )
            }
            if (status.bleActive) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = "BLE",
                    modifier = Modifier.size(16.dp),
                    tint = MeshSecondary
                )
            }
            if (status.wifiDirectActive) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = "WiFi Direct",
                    modifier = Modifier.size(16.dp),
                    tint = MeshTertiary
                )
            }
        }
    }
}

/**
 * SOS emergency button component.
 */
@Composable
fun SOSButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseScale by rememberInfiniteTransition(label = "sosPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sosPulseScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier.scale(pulseScale),
        colors = ButtonDefaults.buttonColors(
            containerColor = SOSRed,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = "SOS",
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "SOS",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

