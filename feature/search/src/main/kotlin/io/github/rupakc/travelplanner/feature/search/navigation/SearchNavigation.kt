package io.github.rupakc.travelplanner.feature.search.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.ui.FeaturePlaceholder
import io.github.rupakc.travelplanner.core.ui.PlaceholderAction
import io.github.rupakc.travelplanner.feature.search.R

/** Search form (known destination, Surprise me, Compare, From a link) + recent searches. */
@Suppress("UnusedParameter", "UNUSED_PARAMETER") // callbacks reserved for the feature team
fun NavGraphBuilder.searchGraph(
    onSearchStarted: () -> Unit,
    onCompare: () -> Unit,
) {
    composable<SearchRoute> { SearchScreen(onSearchStarted = onSearchStarted) }
}

@Composable
internal fun SearchScreen(onSearchStarted: () -> Unit, modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.search_title),
        message = stringResource(R.string.search_message),
        modifier = modifier,
        showTopBar = false,
        actions = listOf(PlaceholderAction(stringResource(R.string.search_show_results), onSearchStarted)),
    )
}
