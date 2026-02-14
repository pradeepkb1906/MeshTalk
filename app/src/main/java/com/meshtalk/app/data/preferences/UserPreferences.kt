package com.meshtalk.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meshtalk_prefs")

/**
 * Manages user preferences and mesh identity using DataStore.
 */
@Singleton
class UserPreferences @Inject constructor(
    private val context: Context
) {
    companion object {
        // Identity
        val MESH_ID = stringPreferencesKey("mesh_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val AVATAR_COLOR = intPreferencesKey("avatar_color")

        // Onboarding
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")

        // Mesh Settings
        val MAX_HOP_COUNT = intPreferencesKey("max_hop_count")
        val AUTO_RELAY = booleanPreferencesKey("auto_relay")
        val STORE_AND_FORWARD = booleanPreferencesKey("store_and_forward")
        val MESSAGE_RETENTION_DAYS = intPreferencesKey("message_retention_days")

        // Transport Settings
        val BLE_ENABLED = booleanPreferencesKey("ble_enabled")
        val WIFI_DIRECT_ENABLED = booleanPreferencesKey("wifi_direct_enabled")
        val NEARBY_ENABLED = booleanPreferencesKey("nearby_enabled")
        val ULTRASONIC_ENABLED = booleanPreferencesKey("ultrasonic_enabled")

        // Notification Settings
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val SOS_ALERTS_ENABLED = booleanPreferencesKey("sos_alerts_enabled")

        // UI Settings
        val DARK_MODE = stringPreferencesKey("dark_mode") // "system", "dark", "light"
        val MESSAGE_FONT_SIZE = intPreferencesKey("message_font_size")
    }

    private val dataStore = context.dataStore

    /**
     * Get or create the unique mesh ID for this device.
     */
    val meshId: Flow<String> = dataStore.data.map { prefs ->
        prefs[MESH_ID] ?: ""
    }

    val displayName: Flow<String> = dataStore.data.map { prefs ->
        prefs[DISPLAY_NAME] ?: "MeshTalk User"
    }

    val isOnboardingComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ONBOARDING_COMPLETE] ?: false
    }

    val maxHopCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[MAX_HOP_COUNT] ?: 7
    }

    val autoRelay: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[AUTO_RELAY] ?: true
    }

    val storeAndForward: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[STORE_AND_FORWARD] ?: true
    }

    val darkMode: Flow<String> = dataStore.data.map { prefs ->
        prefs[DARK_MODE] ?: "system"
    }

    val bleEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[BLE_ENABLED] ?: true
    }

    val wifiDirectEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[WIFI_DIRECT_ENABLED] ?: true
    }

    val nearbyEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[NEARBY_ENABLED] ?: true
    }

    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[NOTIFICATIONS_ENABLED] ?: true
    }

    val sosAlertsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SOS_ALERTS_ENABLED] ?: true
    }

    /**
     * Initialize user identity on first launch.
     */
    suspend fun initializeIdentity(displayName: String) {
        dataStore.edit { prefs ->
            if (prefs[MESH_ID] == null) {
                prefs[MESH_ID] = UUID.randomUUID().toString()
            }
            prefs[DISPLAY_NAME] = displayName
            prefs[AVATAR_COLOR] = (Math.random() * 12).toInt()
            prefs[ONBOARDING_COMPLETE] = true
            // Set defaults
            prefs[MAX_HOP_COUNT] = 7
            prefs[AUTO_RELAY] = true
            prefs[STORE_AND_FORWARD] = true
            prefs[BLE_ENABLED] = true
            prefs[WIFI_DIRECT_ENABLED] = true
            prefs[NEARBY_ENABLED] = true
            prefs[NOTIFICATIONS_ENABLED] = true
            prefs[VIBRATION_ENABLED] = true
            prefs[SOS_ALERTS_ENABLED] = true
            prefs[DARK_MODE] = "system"
            prefs[MESSAGE_FONT_SIZE] = 16
            prefs[MESSAGE_RETENTION_DAYS] = 30
        }
    }

    suspend fun updateDisplayName(name: String) {
        dataStore.edit { it[DISPLAY_NAME] = name }
    }

    suspend fun updateDarkMode(mode: String) {
        dataStore.edit { it[DARK_MODE] = mode }
    }

    suspend fun updateMaxHops(hops: Int) {
        dataStore.edit { it[MAX_HOP_COUNT] = hops }
    }

    suspend fun updateAutoRelay(enabled: Boolean) {
        dataStore.edit { it[AUTO_RELAY] = enabled }
    }

    suspend fun updateStoreAndForward(enabled: Boolean) {
        dataStore.edit { it[STORE_AND_FORWARD] = enabled }
    }

    suspend fun updateBleEnabled(enabled: Boolean) {
        dataStore.edit { it[BLE_ENABLED] = enabled }
    }

    suspend fun updateWifiDirectEnabled(enabled: Boolean) {
        dataStore.edit { it[WIFI_DIRECT_ENABLED] = enabled }
    }

    suspend fun updateNearbyEnabled(enabled: Boolean) {
        dataStore.edit { it[NEARBY_ENABLED] = enabled }
    }

    /**
     * Read current mesh ID synchronously (for service use).
     */
    suspend fun getMeshIdSync(): String {
        var id = ""
        dataStore.edit { prefs ->
            id = prefs[MESH_ID] ?: run {
                val newId = UUID.randomUUID().toString()
                prefs[MESH_ID] = newId
                newId
            }
        }
        return id
    }

    suspend fun getDisplayNameSync(): String {
        var name = "MeshTalk User"
        dataStore.edit { prefs ->
            name = prefs[DISPLAY_NAME] ?: "MeshTalk User"
        }
        return name
    }
}

