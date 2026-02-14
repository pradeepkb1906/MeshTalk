package com.meshtalk.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshtalk.app.ui.theme.*
import com.meshtalk.app.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val meshId by viewModel.meshId.collectAsStateWithLifecycle()
    val maxHops by viewModel.maxHops.collectAsStateWithLifecycle()
    val autoRelay by viewModel.autoRelay.collectAsStateWithLifecycle()
    val storeAndForward by viewModel.storeAndForward.collectAsStateWithLifecycle()
    val bleEnabled by viewModel.bleEnabled.collectAsStateWithLifecycle()
    val wifiDirectEnabled by viewModel.wifiDirectEnabled.collectAsStateWithLifecycle()
    val nearbyEnabled by viewModel.nearbyEnabled.collectAsStateWithLifecycle()
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()

    var showNameDialog by remember { mutableStateOf(false) }
    var showHopsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("Settings", fontWeight = FontWeight.Bold)
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // ═══ Profile Section ═══
            SettingsSectionHeader("Profile")

            SettingsClickItem(
                icon = Icons.Default.Person,
                title = "Display Name",
                subtitle = displayName,
                onClick = { showNameDialog = true }
            )

            SettingsInfoItem(
                icon = Icons.Default.Fingerprint,
                title = "Mesh ID",
                subtitle = meshId.take(12) + "..."
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ═══ Mesh Network Section ═══
            SettingsSectionHeader("Mesh Network")

            SettingsClickItem(
                icon = Icons.Default.AltRoute,
                title = "Maximum Hops",
                subtitle = "$maxHops hops (message reach)",
                onClick = { showHopsDialog = true }
            )

            SettingsSwitchItem(
                icon = Icons.Default.SwapHoriz,
                title = "Auto-Relay Messages",
                subtitle = "Automatically relay messages for other users",
                checked = autoRelay,
                onCheckedChange = { viewModel.updateAutoRelay(it) }
            )

            SettingsSwitchItem(
                icon = Icons.Default.Storage,
                title = "Store & Forward",
                subtitle = "Cache messages for offline peers and deliver when they connect",
                checked = storeAndForward,
                onCheckedChange = { viewModel.updateStoreAndForward(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ═══ Transport Section ═══
            SettingsSectionHeader("Transport Layers")

            SettingsSwitchItem(
                icon = Icons.Default.WifiTethering,
                title = "Nearby Connections",
                subtitle = "Primary transport — BLE discovery + WiFi Direct data",
                checked = nearbyEnabled,
                onCheckedChange = { viewModel.updateNearbyEnabled(it) }
            )

            SettingsSwitchItem(
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth LE",
                subtitle = "Low-power background discovery and messaging",
                checked = bleEnabled,
                onCheckedChange = { viewModel.updateBleEnabled(it) }
            )

            SettingsSwitchItem(
                icon = Icons.Default.Wifi,
                title = "WiFi Direct",
                subtitle = "High-bandwidth transport for media files",
                checked = wifiDirectEnabled,
                onCheckedChange = { viewModel.updateWifiDirectEnabled(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ═══ Appearance Section ═══
            SettingsSectionHeader("Appearance")

            SettingsClickItem(
                icon = Icons.Default.DarkMode,
                title = "Dark Mode",
                subtitle = when (darkMode) {
                    "dark" -> "Always Dark"
                    "light" -> "Always Light"
                    else -> "Follow System"
                },
                onClick = {
                    val next = when (darkMode) {
                        "system" -> "dark"
                        "dark" -> "light"
                        else -> "system"
                    }
                    viewModel.updateDarkMode(next)
                }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            // ═══ About Section ═══
            SettingsSectionHeader("About")

            SettingsInfoItem(
                icon = Icons.Default.Info,
                title = "MeshTalk",
                subtitle = "Version 1.0.0 • World's first internet-free mesh messenger"
            )

            SettingsInfoItem(
                icon = Icons.Default.Hub,
                title = "How It Works",
                subtitle = "Messages hop between nearby devices using Bluetooth, WiFi Direct, and other technologies — no internet or cell towers needed."
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Name edit dialog
    if (showNameDialog) {
        var newName by remember { mutableStateOf(displayName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("Change Display Name") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { if (it.length <= 30) newName = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        viewModel.updateDisplayName(newName.trim())
                        showNameDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Max hops dialog
    if (showHopsDialog) {
        var hops by remember { mutableFloatStateOf(maxHops.toFloat()) }
        AlertDialog(
            onDismissRequest = { showHopsDialog = false },
            title = { Text("Maximum Hops") },
            text = {
                Column {
                    Text(
                        "How many times a message can be relayed. More hops = wider reach but more network traffic.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "${hops.toInt()} hops",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Slider(
                        value = hops,
                        onValueChange = { hops = it },
                        valueRange = 1f..15f,
                        steps = 13
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("1 (local)", style = MaterialTheme.typography.labelSmall)
                        Text("15 (max reach)", style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateMaxHops(hops.toInt())
                    showHopsDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showHopsDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

