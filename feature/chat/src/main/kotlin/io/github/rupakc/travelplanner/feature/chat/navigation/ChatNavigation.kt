package io.github.rupakc.travelplanner.feature.chat.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute as ChatDestination
import io.github.rupakc.travelplanner.core.model.navigation.DeepLinks
import io.github.rupakc.travelplanner.feature.chat.ChatRoute

/**
 * Chat graph. [onOpenImport] handles `import_link` (Search "From a link" with the URL),
 * [onOpenMyPlan] opens the shared My Plan (owned by the plan feature) and [onOpenPlan]
 * opens a plan just saved from a chat itinerary.
 */
fun NavGraphBuilder.chatGraph(
    onOpenImport: (url: String) -> Unit,
    onOpenMyPlan: () -> Unit = {},
    onOpenPlan: (planId: Long) -> Unit = {},
) {
    composable<ChatDestination>(deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.CHAT_PATTERN })) { entry ->
        ChatRoute(
            threadId = entry.toRoute<ChatDestination>().threadId,
            onOpenImport = onOpenImport,
            onOpenMyPlan = onOpenMyPlan,
            onOpenPlan = onOpenPlan,
        )
    }
}
