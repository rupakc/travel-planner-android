package io.github.rupakc.travelplanner.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.AuthGate
import io.github.rupakc.travelplanner.R
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.navigation.AccountChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.GlobalSearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.MyPlanRoute
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute
import io.github.rupakc.travelplanner.core.model.navigation.NotificationsRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResultsRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.feature.alerts.NotificationBellAction
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

/** Root of the UI: auth gate + NavHost + adaptive navigation suite. */
@Composable
fun TravelApp(gate: AuthGate, navController: NavHostController = rememberNavController()) {
    // Nothing is composed until the gate has decided (the splash covers LOADING): a NavHost composed now would start
    // Home, whose screens fetch plans / recent searches / taste profile without a session (E2E O3).
    val start = startDestinationFor(gate) ?: return
    TravelAppContent(gate, remember { start }, navController)
}

@Composable
private fun TravelAppContent(gate: AuthGate, startDestination: AppRoute, navController: NavHostController) {
    val shellViewModel: ShellViewModel = hiltViewModel()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // singleTask activity: deep links that arrive while running come through onNewIntent.
    val activity = LocalActivity.current as? ComponentActivity
    // Deep links held behind the auth gate (kept on Login, replayed once READY; see DeepLinkHold).
    val deepLinks = remember(shellViewModel) { DeepLinkHold(shellViewModel, DeepLinkIntents.appLink(activity?.intent)) }
    val currentGate by rememberUpdatedState(gate)
    DisposableEffect(activity, navController) {
        val listener =
            Consumer<Intent> { intent ->
                deepLinks.onIntent(DeepLinkIntents.appLink(intent))
                navController.handleDeepLink(intent)
                deepLinks.afterHandled(currentGate)
            }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val expiredMessage = stringResource(R.string.session_expired)
    LaunchedEffect(shellViewModel) {
        shellViewModel.sessionExpired.collect { snackbarHostState.showSnackbar(expiredMessage) }
    }

    // Auth gate: re-checked whenever the gate or the destination changes (also after Back from a deep link).
    LaunchedEffect(gate, destination?.id) {
        // Read the live destination: a launch deep link is applied when the NavHost sets its graph.
        val current = navController.currentDestination ?: return@LaunchedEffect
        val entry = navController.currentBackStackEntry
        val retakingQuiz = current.hasRoute(OnboardingRoute::class) && entry?.toRoute<OnboardingRoute>()?.fromSettings == true
        val target = gateTarget(gate, current, retakingQuiz)
        val replay = deepLinks.onGateCheck(gate, target, current.isGateScreen())
        target ?: return@LaunchedEffect
        navController.navigate(target) { popUpTo(0) { inclusive = true } }
        replay?.let { link -> navController.handleDeepLink(Intent(Intent.ACTION_VIEW, link.toUri())) }
    }

    val topLevel = TopLevelDestination.entries.firstOrNull { destination.isOn(it) }
    TravelAppScaffold(
        currentTopLevel = topLevel,
        showNavigation = gate == AuthGate.READY && topLevel != null,
        onNavigate = { navController.navigateToTopLevel(it) },
        onOpenSettings = { navController.navigate(SettingsRoute) },
        onOpenSearch = { navController.navigate(GlobalSearchRoute) },
        bell = { NotificationBellAction(onOpen = { navController.navigate(NotificationsRoute) }) },
        snackbarHostState = snackbarHostState,
    ) {
        NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.fillMaxSize()) {
            authGraph(onLoggedIn = {}, onPasswordChanged = {}, onBack = { navController.popBackStack() })
            // First run: onboarding marks itself complete and the gate opens Home; a Settings re-run pops back.
            onboardingGraph(onFinished = { fromSettings -> if (fromSettings) navController.popBackStack() })
            homeGraph(
                onPlanTrip = { navController.navigateToTopLevel(TopLevelDestination.SEARCH) },
                onOpenNearby = { navController.navigate(NearbyRoute) },
                onOpenPlan = { navController.navigate(PlanDetailRoute(it)) },
                onNavigate = { route -> navController.navigateTo(route) },
                onBack = { navController.popBackStack() },
            )
            searchGraph(onSearchStarted = {
                navController.navigate(ResultsRoute)
            })
            resultsGraph(
                onBack = { navController.popBackStack() },
                onOpenMyPlan = { navController.navigate(MyPlanRoute) },
                onOpenAlerts = { navController.navigateToTopLevel(TopLevelDestination.ALERTS) },
            )
            planGraph(
                onOpenPlan = { navController.navigate(PlanDetailRoute(it)) },
                onBack = { navController.popBackStack() },
                onOpenResults = { navController.navigate(ResultsRoute) },
                onPlanTrip = { navController.navigateToTopLevel(TopLevelDestination.SEARCH) },
                onOpenTrips = { navController.navigateToTopLevel(TopLevelDestination.PLANS) },
            )
            chatGraph(
                onOpenImport = { url ->
                    navController.navigate(
                        SearchRoute(mode = io.github.rupakc.travelplanner.core.model.navigation.SearchModes.IMPORT, importUrl = url),
                    )
                },
                onOpenMyPlan = { navController.navigate(MyPlanRoute) },
                onOpenPlan = { navController.navigate(PlanDetailRoute(it)) },
            )
            alertsGraph(
                onReplan = { navController.navigate(ResultsRoute) },
                onBack = { navController.popBackStack() },
                onOpenAlert = { kind, watchId ->
                    // Replace any Alerts entry above the inbox instead of stacking another.
                    navController.navigate(AlertsRoute(kind = kind, watchId = watchId)) {
                        popUpTo<AlertsRoute> { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSearch = { navController.navigateToTopLevel(TopLevelDestination.SEARCH) },
            )
            settingsGraph(
                onBack = { navController.popBackStack() },
                onRetakeQuiz = { navController.navigate(OnboardingRoute(fromSettings = true)) },
                // Settings signs out itself (AuthRepository.logout unregisters the push token); the gate then shows Login.
                onSignOut = {},
                // feature:auth (team A1) registers AccountChangePasswordRoute.
                onChangePassword = { navController.navigate(AccountChangePasswordRoute) },
                onNavigate = { route -> navController.navigateTo(route) },
            )
            nearbyGraph(onBack = { navController.popBackStack() })
            shareGraph(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(if (gate == AuthGate.READY) HomeRoute else LoginRoute) { popUpTo(0) { inclusive = true } }
                    }
                },
                onOpenPlan = { navController.navigate(PlanDetailRoute(it)) },
                onPlanTrip = {
                    if (gate == AuthGate.READY) {
                        navController.navigateToTopLevel(TopLevelDestination.SEARCH)
                    } else {
                        navController.navigate(LoginRoute) { popUpTo(0) { inclusive = true } }
                    }
                },
            )
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
    onOpenSearch: () -> Unit = {},
    /** Notifications bell (top bar action); empty in previews and tests. */
    bell: @Composable () -> Unit = {},
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
                            TopBarAction(Icons.Filled.Search, stringResource(R.string.nav_global_search), onOpenSearch)
                            bell()
                            TopBarAction(Icons.Filled.Settings, stringResource(R.string.nav_settings), onOpenSettings)
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

/**
 * One top-bar action. Its layout bounds are the full 48 x 48 dp target (Material's IconButton is a 40 dp container
 * with the rest as touch padding, which the Accessibility Test Framework measured as too small; E2E O2).
 */
@Composable
private fun TopBarAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(TravelTheme.spacing.minTouchTarget)) {
        Icon(icon, contentDescription = label)
    }
}

private fun NavDestination?.isOn(dest: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true

/**
 * The NavHost's start: the screen the decided gate shows first, so a signed-out start never composes Home (whose
 * screens make authed requests). Null while the gate is LOADING: compose nothing yet. Later gate changes navigate.
 */
internal fun startDestinationFor(gate: AuthGate): AppRoute? =
    when (gate) {
        AuthGate.LOADING -> null
        AuthGate.SIGNED_OUT -> LoginRoute
        AuthGate.MUST_CHANGE_PASSWORD -> ChangePasswordRoute
        AuthGate.NEEDS_ONBOARDING -> OnboardingRoute()
        AuthGate.READY -> HomeRoute
    }

/** Where the gate must send the user from [current], or null to stay. The public share viewer is always allowed. */
internal fun gateTarget(gate: AuthGate, current: NavDestination, retakingQuiz: Boolean): AppRoute? {
    if (current.hasRoute(ShareRoute::class)) return null
    return when (gate) {
        AuthGate.LOADING -> null
        AuthGate.SIGNED_OUT -> LoginRoute.takeUnless { current.hasRoute(LoginRoute::class) }
        AuthGate.MUST_CHANGE_PASSWORD -> ChangePasswordRoute.takeUnless { current.hasRoute(ChangePasswordRoute::class) }
        AuthGate.NEEDS_ONBOARDING -> OnboardingRoute().takeUnless { current.hasRoute(OnboardingRoute::class) }
        AuthGate.READY -> HomeRoute.takeIf { current.isGateScreen() && !retakingQuiz }
    }
}

private fun NavDestination.isGateScreen(): Boolean =
    hasRoute(LoginRoute::class) || hasRoute(ChangePasswordRoute::class) || hasRoute(OnboardingRoute::class)

private fun NavHostController.navigateToTopLevel(dest: TopLevelDestination) = navigateToTopLevel(dest.route)

/**
 * Switches to a top-level tab (popUpTo start with saveState, launchSingleTop, restoreState). A route
 * with arguments (e.g. `SearchRoute(mode = "compare")`) is not restored from the tab's saved state,
 * so its arguments arrive.
 */
private fun NavHostController.navigateToTopLevel(route: AppRoute) {
    val restore = restoresTabState(route)
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = restore
        restoreState = restore
    }
}

/** Top-level routes switch tabs; everything else is pushed. */
private fun NavHostController.navigateTo(route: AppRoute) {
    if (isTopLevelRoute(route)) navigateToTopLevel(route) else navigate(route)
}

/** True when [route] is one of the navigation-suite destinations (with or without arguments). */
internal fun isTopLevelRoute(route: AppRoute): Boolean = TopLevelDestination.entries.any { it.routeClass.isInstance(route) }

/** True when [route] is a tab's plain route, so its saved state may be restored. */
internal fun restoresTabState(route: AppRoute): Boolean = TopLevelDestination.entries.any { it.route == route }
