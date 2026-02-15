package com.meshtalk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.meshtalk.app.ui.navigation.MeshTalkNavHost
import com.meshtalk.app.ui.theme.MeshTalkTheme
import androidx.compose.runtime.*
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main entry point for MeshTalk.
 *
 * This activity hosts the Jetpack Compose navigation graph
 * and manages the app lifecycle with the mesh service.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MeshTalkTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var permissionsGranted by remember { mutableStateOf(false) }

                    if (permissionsGranted) {
                        MeshTalkNavHost()
                    } else {
                        com.meshtalk.app.ui.screens.PermissionScreen(
                            onPermissionsGranted = {
                                permissionsGranted = true
                            }
                        )
                    }
                }
            }
        }
    }
}

