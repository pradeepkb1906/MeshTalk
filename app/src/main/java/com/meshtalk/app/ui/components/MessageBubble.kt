package com.meshtalk.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meshtalk.app.data.model.MeshMessage
import com.meshtalk.app.data.model.MessageStatus
import com.meshtalk.app.data.model.MessageType
import com.meshtalk.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: MeshMessage,
    modifier: Modifier = Modifier
) {
    val isOutgoing = message.isOutgoing
    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isOutgoing) 16.dp else 4.dp,
        bottomEnd = if (isOutgoing) 4.dp else 16.dp
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isOutgoing) Alignment.End else Alignment.Start
    ) {
        // Sender name for incoming messages
        if (!isOutgoing) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                when (message.type) {
                    MessageType.SOS -> SOSContent(message, textColor)
                    MessageType.AUDIO -> AudioContent(message, textColor)
                    MessageType.IMAGE -> ImageContent(message, textColor)
                    MessageType.FILE -> FileContent(message, textColor)
                    MessageType.LOCATION -> LocationContent(message, textColor)
                    else -> TextContent(message, textColor)
                }

                // Timestamp and status row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )

                    if (message.hopCount > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${message.hopCount} hop${if (message.hopCount > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.5f)
                        )
                    }

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        StatusIcon(message.status, textColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextContent(message: MeshMessage, textColor: androidx.compose.ui.graphics.Color) {
    Text(
        text = message.content,
        style = MaterialTheme.typography.bodyLarge,
        color = textColor
    )
}

@Composable
private fun SOSContent(message: MeshMessage, textColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Warning,
            contentDescription = "SOS",
            tint = SOSRed,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "SOS EMERGENCY",
            style = MaterialTheme.typography.titleSmall,
            color = SOSRed
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = message.content,
        style = MaterialTheme.typography.bodyLarge,
        color = textColor
    )
}

@Composable
private fun AudioContent(message: MeshMessage, textColor: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = { /* Play audio */ },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = textColor
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = "Voice Message",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            if (message.mediaSize > 0) {
                Text(
                    text = formatFileSize(message.mediaSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ImageContent(message: MeshMessage, textColor: androidx.compose.ui.graphics.Color) {
    Column {
        // Placeholder for image (would use Coil in production)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Image,
                contentDescription = "Image",
                tint = textColor.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
        }
        if (message.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
        }
    }
}

@Composable
private fun FileContent(message: MeshMessage, textColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.AttachFile,
            contentDescription = "File",
            tint = textColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = message.mediaFileName ?: "File",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            if (message.mediaSize > 0) {
                Text(
                    text = formatFileSize(message.mediaSize),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun LocationContent(message: MeshMessage, textColor: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = "Location",
            tint = textColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message.content.ifBlank { "Shared location" },
            style = MaterialTheme.typography.bodyMedium,
            color = textColor
        )
    }
}

@Composable
private fun StatusIcon(status: MessageStatus, tint: androidx.compose.ui.graphics.Color) {
    val icon = when (status) {
        MessageStatus.SENDING -> Icons.Default.Schedule
        MessageStatus.SENT -> Icons.Default.Done
        MessageStatus.RELAYED -> Icons.Default.SwapHoriz
        MessageStatus.DELIVERED -> Icons.Default.DoneAll
        MessageStatus.READ -> Icons.Default.DoneAll
        MessageStatus.FAILED -> Icons.Default.ErrorOutline
    }
    val iconTint = when (status) {
        MessageStatus.READ -> MeshConnected
        MessageStatus.FAILED -> SOSRed
        else -> tint.copy(alpha = 0.7f)
    }
    Icon(
        icon,
        contentDescription = status.name,
        tint = iconTint,
        modifier = Modifier.size(14.dp)
    )
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> "${size / (1024 * 1024)} MB"
    }
}

