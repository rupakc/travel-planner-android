package io.github.rupakc.travelplanner.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.rupakc.travelplanner.AuthGate
import io.github.rupakc.travelplanner.R
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResultsRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.feature.alerts.navigation.alertsGraph
import io.github.rupakc.travelplanner.feature.auth.navigation.authGraph
import io.github.rupakc.travelplanner.feature.chat.navigation.chatGraph
import io.github.rupakc.travelplanner.feature.home.navigation.homeGraph
import io.github.rupakc.travelplanner.feature.nearby.navigation.nearbyGraph
import io.github.rupakc.travelplanner.feature.onboarding.navigation.onboardingGraph
import io.github.rupakc.travelplanner.feature.plan.navigation.planGraph
import io.github.rupakc.travelplanner.feature.results.navigation.resultsGraph
import io.github.rupakc.travelplanner.feature.search.navigation.searchGraph
import io.github.rupakc.travelplanner.feature.settings.navigation.settingsGraph
import io.github.rupakc.travelplanner.feature.share.navigation.shareGraph
import kotlinx.coroutines.launch

/** Root of the UI: auth gate + NavHost + adaptive navigation suite. */
@Composable
fun TravelApp(gate: AuthGate, navController: NavHostController = rememberNavController()) {
    val shellViewModel: ShellViewModel = hiltViewModel()
    val scope = rememberCoroutineScope()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val snackbarHostState = remember { SnackbarHostState() }
    val expiredMessage = stringResource(R.string.session_expired)
    LaunchedEffect(shellViewModel) {
        shellViewModel.sessionExpired.collect { snackbarHostState.showSnackbar(expiredMessage) }
    }

    LaunchedEffect(gate) {
        val target: AppRoute =
            when (gate) {
                AuthGate.LOADING -> return@LaunchedEffect
                AuthGate.SIGNED_OUT -> LoginRoute
                AuthGate.MUST_CHANGE_PASSWORD -> ChangePasswordRoute
                AuthGate.NEEDS_ONBOARDING -> OnboardingRoute()
                AuthGate.READY -> HomeRoute
            }
        // The public share viewer stays open whatever the session state.
        if (destination?.hasRoute(ShareRoute::class) == true) return@LaunchedEffect
        if (gate == AuthGate.READY && destination != null && !destination.isGateScreen()) return@LaunchedEffect
        navController.navigate(target) { popUpTo(0) { inclusive = true } }
    }

    val topLevel = TopLevelDestination.entries.firstOrNull { destination.isOn(it) }
    TravelAppScaffold(
        currentTopLevel = topLevel,
        showNavigation = gate == AuthGate.READY && topLevel != null,
        onNavigate = { navController.navigateToTopLevel(it) },
        onOpenSettings = { navController.navigate(SettingsRoute) },
        snackbarHostState = snackbarHostState,
    ) {
        NavHost(navController = navController, startDestination = HomeRoute, modifier = Modifier.fillMaxSize()) {
            authGraph(onLoggedIn = {}, onPasswordChanged = {})
            onboardingGraph(onFinished = { fromSettings ->
                if (fromSettings) navController.popBackStack() else shellViewModel.completeOnboarding()
            })
            homeGraph(
                onPlanTrip = { navController.navigateToTopLevel(TopLevelDestination.SEARCH) },
                onOpenNearby = { navController.navigate(NearbyRoute) },
                onOpenPlan = { navController.navigate(PlanDetailRoute(it)) },
            )
            searchGraph(onSearchStarted = {
                navController.navigate(ResultsRoute)
            }, onCompare = { navController.navigate(io.github.rupakc.travelplanner.core.model.navigation.CompareRoute) })
            resultsGraph(onBack = { navController.popBackStack() })
            planGraph(onOpenPlan = { navController.navigate(PlanDetailRoute(it)) }, onBack = { navController.popBackStack() })
            chatGraph(onOpenImport = { url ->
                navController.navigate(
                    SearchRoute(mode = io.github.rupakc.travelplanner.core.model.navigation.SearchModes.IMPORT, importUrl = url),
                )
            })
            alertsGraph(onReplan = {
                navController.navigateToTopLevel(TopLevelDestination.SEARCH)
            }, onBack = { navController.popBackStack() })
            settingsGraph(
                onBack = { navController.popBackStack() },
                onRetakeQuiz = { navController.navigate(OnboardingRoute(fromSettings = true)) },
                onSignOut = { scope.launch { shellViewModel.signOut() } },
            )
            nearbyGraph(onBack = { navController.popBackStack() })
            shareGraph(onBack = { if (!navController.popBackStack()) navController.navigate(HomeRoute) })
        }
    }
}

/** Stateless shell: navigation suite (bar / rail by window size) + top bar with Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelAppScaffold(
    currentTopLevel: TopLevelDestination?,
    showNavigation: Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
    onOpenSettings: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable () -> Unit,
) {
    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (showNavigation && currentTopLevel != null) {
                    TopAppBar(
                        title = { Text(stringResource(currentTopLevel.labelRes), modifier = Modifier.semantics { heading() }) },
                        actions = {
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                            }
                        },
                    )
                }
                content()
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
        }
    }
    if (!showNavigation) {
        body()
        return
    }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { dest ->
                item(
                    selected = dest == currentTopLevel,
                    onClick = { onNavigate(dest) },
                    icon = { Icon(dest.icon, contentDescription = null) },
                    label = { Text(stringResource(dest.labelRes)) },
                )
            }
        },
    ) { body() }
}

private fun NavDestination?.isOn(dest: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true

private fun NavDestination.isGateScreen(): Boolean =
    hasRoute(LoginRoute::class) || hasRoute(ChangePasswordRoute::class) || hasRoute(OnboardingRoute::class)

private fun NavHostController.navigateToTopLevel(dest: TopLevelDestination) {
    navigate(dest.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
