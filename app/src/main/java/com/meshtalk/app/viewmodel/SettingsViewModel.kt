package com.meshtalk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.meshtalk.app.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val displayName: StateFlow<String> = userPreferences.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "MeshTalk User")

    val meshId: StateFlow<String> = userPreferences.meshId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val maxHops: StateFlow<Int> = userPreferences.maxHopCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 7)

    val autoRelay: StateFlow<Boolean> = userPreferences.autoRelay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val storeAndForward: StateFlow<Boolean> = userPreferences.storeAndForward
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val bleEnabled: StateFlow<Boolean> = userPreferences.bleEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val wifiDirectEnabled: StateFlow<Boolean> = userPreferences.wifiDirectEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val nearbyEnabled: StateFlow<Boolean> = userPreferences.nearbyEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val darkMode: StateFlow<String> = userPreferences.darkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    fun updateDisplayName(name: String) {
        viewModelScope.launch { userPreferences.updateDisplayName(name) }
    }

    fun updateMaxHops(hops: Int) {
        viewModelScope.launch { userPreferences.updateMaxHops(hops) }
    }

    fun updateAutoRelay(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateAutoRelay(enabled) }
    }

    fun updateStoreAndForward(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateStoreAndForward(enabled) }
    }

    fun updateBleEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateBleEnabled(enabled) }
    }

    fun updateWifiDirectEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateWifiDirectEnabled(enabled) }
    }

    fun updateNearbyEnabled(enabled: Boolean) {
        viewModelScope.launch { userPreferences.updateNearbyEnabled(enabled) }
    }

    fun updateDarkMode(mode: String) {
        viewModelScope.launch { userPreferences.updateDarkMode(mode) }
    }
}

