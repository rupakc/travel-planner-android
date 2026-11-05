package io.github.rupakc.travelplanner.feature.chat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.chat.R
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute

/** [onOpenImport] handles the `import_link` event (route to Search "From a link"). */
@Suppress("UnusedParameter", "UNUSED_PARAMETER") // callbacks reserved for the feature team
fun NavGraphBuilder.chatGraph(onOpenImport: (url: String) -> Unit) {
    composable<ChatRoute> { ChatScreen() }
}

@Composable
internal fun ChatScreen(modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.chat_title),
        message = stringResource(R.string.chat_message),
        modifier = modifier,
        showTopBar = false,
    )
}
