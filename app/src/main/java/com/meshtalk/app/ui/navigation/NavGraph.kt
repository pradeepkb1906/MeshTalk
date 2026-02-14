package com.meshtalk.app.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meshtalk.app.data.preferences.UserPreferences
import com.meshtalk.app.ui.screens.*
import com.meshtalk.app.viewmodel.OnboardingViewModel

/**
 * Navigation routes for MeshTalk.
 */
object MeshRoutes {
    const val ONBOARDING = "onboarding"
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}/{peerId}"
    const val PEERS = "peers"
    const val SETTINGS = "settings"

    fun chatRoute(conversationId: String, peerId: String) =
        "chat/$conversationId/$peerId"
}

@Composable
fun MeshTalkNavHost(
    navController: NavHostController = rememberNavController(),
    onboardingViewModel: OnboardingViewModel = hiltViewModel()
) {
    val isOnboardingComplete by onboardingViewModel.isOnboardingComplete.collectAsStateWithLifecycle()

    val startDestination = if (isOnboardingComplete) {
        MeshRoutes.CONVERSATIONS
    } else {
        MeshRoutes.ONBOARDING
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ═══ Onboarding ═══
        composable(MeshRoutes.ONBOARDING) {
            OnboardingScreen(
                onComplete = { displayName ->
                    onboardingViewModel.completeOnboarding(displayName)
                    navController.navigate(MeshRoutes.CONVERSATIONS) {
                        popUpTo(MeshRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        // ═══ Conversations List ═══
        composable(MeshRoutes.CONVERSATIONS) {
            ConversationsScreen(
                onConversationClick = { conversationId, peerId ->
                    navController.navigate(MeshRoutes.chatRoute(conversationId, peerId))
                },
                onPeersClick = {
                    navController.navigate(MeshRoutes.PEERS)
                },
                onSettingsClick = {
                    navController.navigate(MeshRoutes.SETTINGS)
                }
            )
        }

        // ═══ Chat ═══
        composable(
            route = MeshRoutes.CHAT,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("peerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""

            ChatScreen(
                conversationId = conversationId,
                peerId = peerId,
                onBack = { navController.popBackStack() }
            )
        }

        // ═══ Peers Discovery ═══
        composable(MeshRoutes.PEERS) {
            PeersScreen(
                onBack = { navController.popBackStack() },
                onPeerChat = { meshId, displayName ->
                    // Navigate to chat with this peer — use meshId as temporary conversationId
                    navController.navigate(MeshRoutes.chatRoute(meshId, meshId))
                }
            )
        }

        // ═══ Settings ═══
        composable(MeshRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

