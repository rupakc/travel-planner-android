package io.github.rupakc.travelplanner.feature.search.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.feature.search.SearchFormRoute

/**
 * Search form (known destination, Surprise me, Compare, From a link) + recent searches.
 * [onSearchStarted]: a search was started on `SearchRepository` (go to Results).
 * [onCompare] is kept for the shell's `CompareRoute`; Compare renders inside this screen (web parity), so it is not called.
 */
@Suppress("UnusedParameter", "UNUSED_PARAMETER")
fun NavGraphBuilder.searchGraph(
    onSearchStarted: () -> Unit,
    onCompare: () -> Unit,
) {
    composable<SearchRoute> { entry ->
        val route = entry.toRoute<SearchRoute>()
        SearchFormRoute(importUrl = route.importUrl, onSearchStarted = onSearchStarted)
    }
}
