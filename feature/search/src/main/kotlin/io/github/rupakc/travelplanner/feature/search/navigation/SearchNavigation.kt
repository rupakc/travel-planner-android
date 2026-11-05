package io.github.rupakc.travelplanner.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.feature.search.SearchFormRoute

/**
 * Search form (known destination, Surprise me, Compare, From a link) + recent searches.
 * [onSearchStarted]: a search was started on `SearchRepository` (go to Results).
 * Compare renders inside this screen (web parity: CompareTable lives on SearchPage).
 */
fun NavGraphBuilder.searchGraph(onSearchStarted: () -> Unit) {
    composable<SearchRoute> { entry ->
        val route = entry.toRoute<SearchRoute>()
        SearchFormRoute(importUrl = route.importUrl, onSearchStarted = onSearchStarted)
    }
}
