package io.github.rupakc.travelplanner.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.rupakc.travelplanner.AuthGate
import io.github.rupakc.travelplanner.R
import io.github.rupakc.travelplanner.core.data.telemetry.ConsentPromptState
import io.github.rupakc.travelplanner.core.designsystem.component.LocalUiEventSink
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventKind
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventSink
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.navigation.AccountChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.AccountRoute
import io.github.rupakc.travelplanner.core.model.navigation.AlertPrefsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChangePasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute
import io.github.rupakc.travelplanner.core.model.navigation.CheckEmailRoute
import io.github.rupakc.travelplanner.core.model.navigation.ForgotPasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.GlobalSearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.MyPlanRoute
import io.github.rupakc.travelplanner.core.model.navigation.MySharesRoute
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute
import io.github.rupakc.travelplanner.core.model.navigation.NotificationsRoute
import io.github.rupakc.travelplanner.core.model.navigation.OauthUsernameRoute
import io.github.rupakc.travelplanner.core.model.navigation.OnboardingRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlansRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResetPasswordRoute
import io.github.rupakc.travelplanner.core.model.navigation.ResultsRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareCreateRoute
import io.github.rupakc.travelplanner.core.model.navigation.ShareRoute
import io.github.rupakc.travelplanner.core.model.navigation.SignUpRoute
import io.github.rupakc.travelplanner.core.model.navigation.TripRoute
import io.github.rupakc.travelplanner.core.model.navigation.VerifyEmailRoute
import io.github.rupakc.travelplanner.core.model.navigation.YouRoute
import io.github.rupakc.travelplanner.core.model.settings.ConsentChoice
import io.github.rupakc.travelplanner.core.ui.game.GameDeltaHost
import io.github.rupakc.travelplanner.core.ui.privacy.ConsentPrompt
import io.github.rupakc.travelplanner.core.ui.share.LocalShareLauncher
import io.github.rupakc.travelplanner.core.ui.share.ShareLauncher
import io.github.rupakc.travelplanner.feature.account.navigation.accountGraph
import io.github.rupakc.travelplanner.feature.alerts.NotificationBellAction
import io.github.rupakc.travelplanner.feature.alerts.navigation.alertsGraph
import io.github.rupakc.travelplanner.feature.auth.navigation.AuthNavActions
import io.github.rupakc.travelplanner.feature.auth.navigation.authGraph
import io.github.rupakc.travelplanner.feature.chat.navigation.chatGraph
import io.github.rupakc.travelplanner.feature.home.navigation.homeGraph
import io.github.rupakc.travelplanner.feature.nearby.navigation.nearbyGraph
import io.github.rupakc.travelplanner.feature.onboarding.navigation.onboardingGraph
import io.github.rupakc.travelplanner.feature.plan.navigation.planGraph
import io.github.rupakc.travelplanner.feature.play.navigation.playGraph
import io.github.rupakc.travelplanner.feature.results.navigation.resultsGraph
import io.github.rupakc.travelplanner.feature.search.navigation.searchGraph
import io.github.rupakc.travelplanner.feature.settings.navigation.settingsGraph
import io.github.rupakc.travelplanner.feature.share.myshares.mySharesGraph
import io.github.rupakc.travelplanner.feature.share.navigation.shareCreateGraph
import io.github.rupakc.travelplanner.feature.share.navigation.shareGraph
import io.github.rupakc.travelplanner.feature.trips.journal.journalTabContent
import io.github.rupakc.travelplanner.feature.trips.money.moneyTabContent
import io.github.rupakc.travelplanner.feature.trips.navigation.tripsGraph
import io.github.rupakc.travelplanner.feature.trips.offline.OfflinePackTabContent
import io.github.rupakc.travelplanner.feature.trips.offline.offlinePackDestination
import io.github.rupakc.travelplanner.feature.trips.recap.recapTabContent
import io.github.rupakc.travelplanner.feature.trips.today.todayTabContent
import io.github.rupakc.travelplanner.feature.trips.trip.TripTab
import io.github.rupakc.travelplanner.feature.trips.trip.TripTabContent
import kotlinx.coroutines.delay

/**
 * Root of the UI: auth gate + NavHost + adaptive navigation suite, with the telemetry hooks: [uiEventSink] is
 * provided to every screen, [screenTracker] follows the destination, and the consent prompt sits above everything.
 */
@Composable
fun TravelApp(
    gate: AuthGate,
    navController: NavHostController = rememberNavController(),
    uiEventSink: UiEventSink = LocalUiEventSink.current,
    screenTracker: NavController.OnDestinationChangedListener? = null,
    consentPrompt: ConsentPromptState = ConsentPromptState.Hidden,
    onConsentChoice: (ConsentChoice) -> Unit = {},
    onCloseConsent: () -> Unit = {},
    shell: ShellMode = ShellMode(),
) {
    // Nothing is composed until the gate has decided (the splash covers LOADING): a NavHost composed now would start
    // Home, whose screens fetch plans / recent searches / taste profile without a session (E2E O3).
    val start = startDestinationFor(gate, shell.home) ?: return
    if (screenTracker != null) {
        DisposableEffect(navController, screenTracker) {
            navController.addOnDestinationChangedListener(screenTracker)
            onDispose { navController.removeOnDestinationChangedListener(screenTracker) }
        }
    }
    // Share links 2.0 entry points read this; null (no `features.sharing`) keeps the legacy plan share and hides the rest.
    val shareLauncher =
        remember(navController, shell.sharing) {
            if (shell.sharing) ShareLauncher { kind, planId -> navController.navigate(ShareCreateRoute(kind.wire.orEmpty(), planId)) } else null
        }
    CompositionLocalProvider(LocalUiEventSink provides uiEventSink, LocalShareLauncher provides shareLauncher) {
        Box(Modifier.fillMaxSize()) {
            TravelAppContent(gate, remember { start }, navController, shell)
            // Over every destination (Login and Share included), above the system navigation bar.
            ConsentHost(consentPrompt, onConsentChoice, onCloseConsent, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/**
 * What the feature flags and the home rule decide about the shell: [journey] swaps the tabs (Trips, Explore, Alerts, Play, You),
 * [home] is where a signed-in user starts (Home, Trips or Explore), [gameEnabled] shows celebrations, [offlinePack] the Offline tab.
 */
data class ShellMode(
    val journey: Boolean = false,
    val home: AppRoute = HomeRoute,
    val gameEnabled: Boolean = false,
    val offlinePack: Boolean = false,
    /** Share links 2.0 is on (`features.sharing >= 1`). */
    val sharing: Boolean = false,
)

/** The non-modal consent card: first run (no close button), reopened choices (current choice selected), or nothing. */
@Composable
internal fun ConsentHost(
    prompt: ConsentPromptState,
    onChoice: (ConsentChoice) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentAllowed =
        when (prompt) {
            ConsentPromptState.Hidden -> return
            ConsentPromptState.FirstRun -> null
            is ConsentPromptState.Reopened -> prompt.allowed
        }
    ConsentPrompt(
        currentAllowed = currentAllowed,
        onAllow = { onChoice(ConsentChoice.GRANTED) },
        onDeny = { onChoice(ConsentChoice.DENIED) },
        onClose = onClose,
        modifier =
            modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(TravelTheme.spacing.md)
                .widthIn(max = CONSENT_MAX_WIDTH)
                .fillMaxWidth(),
    )
}

private val CONSENT_MAX_WIDTH = 560.dp

@Composable
private fun TravelAppContent(gate: AuthGate, startDestination: AppRoute, navController: NavHostController, shell: ShellMode) {
    val shellViewModel: ShellViewModel = hiltViewModel()
    val services: ShellServicesViewModel = hiltViewModel()
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    // singleTask activity: deep links that arrive while running come through onNewIntent.
    val activity = LocalActivity.current as? ComponentActivity
    // Deep links held behind the auth gate (kept on Login, replayed once READY; see DeepLinkHold).
    val deepLinks = remember(shellViewModel) { DeepLinkHold(shellViewModel, DeepLinkIntents.heldLink(activity?.intent)) }
    val currentGate by rememberUpdatedState(gate)
    DisposableEffect(activity, navController) {
        val listener =
            Consumer<Intent> { intent ->
                deepLinks.onIntent(DeepLinkIntents.heldLink(intent))
                navController.handleDeepLink(intent)
                // An emailed verify / reset link opens its screen when no gate screen stands in the way (signed out or ready).
                DeepLinkIntents.accountLink(intent)?.takeIf { currentGate.opensAccountLinks() }?.let(navController::openLink)
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
    val droppedMessage = stringResource(R.string.outbox_dropped)
    LaunchedEffect(shellViewModel) {
        shellViewModel.droppedWrites.collect { snackbarHostState.showSnackbar(droppedMessage) }
    }

    // Auth gate: re-checked whenever the gate or the destination changes (also after Back from a deep link).
    LaunchedEffect(gate, destination?.id) {
        // Read the live destination: a launch deep link is applied when the NavHost sets its graph.
        val current = navController.currentDestination ?: return@LaunchedEffect
        val entry = navController.currentBackStackEntry
        val retakingQuiz = current.hasRoute(OnboardingRoute::class) && entry?.toRoute<OnboardingRoute>()?.fromSettings == true
        val target = gateTarget(gate, current, retakingQuiz)
        val replay = deepLinks.onGateCheck(gate, target, current.isGateScreen() || current.isSignedOutScreen(), shell.home)
        target ?: return@LaunchedEffect
        navController.navigate(target) { popUpTo(0) { inclusive = true } }
        replay?.let(navController::openLink)
    }

    ShellEffects(gate, services, navController, activity, snackbarHostState, shell)

    val tabs = remember(shell.journey) { TopLevelDestination.forJourney(shell.journey) }
    val coachSeen by services.coachSeen.collectAsStateWithLifecycle()

    val topLevel = tabs.firstOrNull { destination.isOn(it) } ?: TopLevelDestination.YOU.takeIf { shell.journey && destination.isOn(PlansRoute::class) }
    val tip = CoachMarks.tipFor(topLevel, coachSeen, shell.journey && gate == AuthGate.READY)
    TravelAppScaffold(
        currentTopLevel = topLevel,
        showNavigation = gate == AuthGate.READY && topLevel != null,
        tabs = tabs,
        showSettingsAction = !shell.journey,
        belowBar = {
            if (topLevel == TopLevelDestination.EXPLORE) {
                ExploreEntries(onOpenChat = { navController.navigate(ChatRoute()) }, onOpenNearby = { navController.navigate(NearbyRoute) })
            }
            tip?.let {
                CoachMarkCard(it, onGotIt = { services.markCoachSeen(setOf(it.id)) }, onSkipAll = { services.markCoachSeen(CoachMarks.ALL_IDS) })
            }
        },
        onNavigate = { navController.navigateToTopLevel(it) },
        onOpenSettings = { navController.navigate(SettingsRoute) },
        onOpenSearch = { navController.navigate(GlobalSearchRoute) },
        bell = { NotificationBellAction(onOpen = { navController.navigate(NotificationsRoute) }) },
        snackbarHostState = snackbarHostState,
    ) {
        NavHost(navController = navController, startDestination = startDestination, modifier = Modifier.fillMaxSize()) {
            authGraph(
                onLoggedIn = {},
                onPasswordChanged = {},
                onBack = { navController.popBackStack() },
                actions =
                    AuthNavActions(
                        toSignUp = { navController.navigate(SignUpRoute) },
                        toForgotPassword = { navController.navigate(ForgotPasswordRoute) },
                        toCheckEmail = { email -> navController.navigate(CheckEmailRoute(email)) },
                        toLogin = { navController.navigate(LoginRoute) { popUpTo(0) { inclusive = true } } },
                        toOauthUsername = { navController.navigate(OauthUsernameRoute) },
                    ),
            )
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
                onOpenPrefs = { navController.navigate(AlertPrefsRoute) },
                onOpenTrip = { navController.navigate(TripRoute(it, tab = TripTab.Today.id)) { launchSingleTop = true } },
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
            tripsGraph(
                onOpenTrip = { navController.navigate(TripRoute(it)) },
                onPlanTrip = { navController.navigateToTopLevel(TopLevelDestination.EXPLORE) },
                onBack = { navController.popBackStack() },
                onOpenPlanner = { navController.navigate(ResultsRoute) },
                onPlanIt = { navController.navigateToTopLevel(TopLevelDestination.EXPLORE) },
                tabContent =
                    tripTabContent(shell.offlinePack, onAddExpense = { id ->
                        navController.navigate(TripRoute(id, tab = TripTab.Money.id)) { launchSingleTop = true }
                    }, onWhereNext = { navController.navigateToTopLevel(TopLevelDestination.EXPLORE) }),
            )
            offlinePackDestination(onBack = { navController.popBackStack() })
            // The Play tab hides itself when the game is off (the flag or the user's hide_game setting): leave for Trips / Home.
            playGraph(onHidden = { navController.navigateToTopLevel(if (shell.journey) TopLevelDestination.TRIPS else TopLevelDestination.HOME) })
            accountGraph(
                onBack = { navController.popBackStack() },
                onChangePassword = { navController.navigate(AccountChangePasswordRoute) },
                onOpenSessions = { navController.navigate(io.github.rupakc.travelplanner.core.model.navigation.SessionsRoute) },
                // The session is already cleared (account delete, sign out everywhere): the gate shows Login.
                onSignedOut = {},
            )
            composable<YouRoute> {
                YouScreen(
                    onOpenAccount = { navController.navigate(AccountRoute) },
                    onOpenSavedPlans = { navController.navigate(PlansRoute) },
                    onOpenSettings = { navController.navigate(SettingsRoute) },
                    onOpenShares = if (shell.sharing) ({ navController.navigate(MySharesRoute) }) else null,
                )
            }
            shareCreateGraph(onClose = { navController.popBackStack() })
            mySharesGraph(onBack = { navController.popBackStack() })
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

/**
 * The shell's one-shot effects: the verify / reset link that launched the app, the server-driven onboarding check and the
 * game celebrations. Split from [TravelAppContent] to keep each readable.
 */
@Composable
private fun ShellEffects(
    gate: AuthGate,
    services: ShellServicesViewModel,
    navController: NavHostController,
    activity: ComponentActivity?,
    snackbarHostState: SnackbarHostState,
    shell: ShellMode,
) {
    // A verify / reset link that launched the app: the gate has decided (nothing is composed before), so open it now, once.
    var launchLinkHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(gate) {
        if (launchLinkHandled || !gate.opensAccountLinks()) return@LaunchedEffect
        launchLinkHandled = true
        DeepLinkIntents.accountLink(activity?.intent)?.let(navController::openLink)
    }

    // The server says onboarding is not done (e.g. a new sign-in on this device): once per process, then it sends `done`.
    LaunchedEffect(gate) {
        if (gate == AuthGate.READY && services.shouldShowOnboarding()) navController.navigate(OnboardingRoute(fromSettings = true))
    }

    // Game celebrations: one delta at a time, each given the length of a snackbar before the next replaces it.
    var gameDelta by remember { mutableStateOf<GameDelta?>(null) }
    LaunchedEffect(services) {
        services.gameDeltas.collect { delta ->
            gameDelta = delta
            delay(GAME_DELTA_WINDOW_MS)
            gameDelta = null
        }
    }
    GameDeltaHost(gameDelta, snackbarHostState, shell.gameEnabled, onConsumed = {})
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
    /** The tabs of the bar / rail (journey on or off). */
    tabs: List<TopLevelDestination> = TopLevelDestination.forJourney(false),
    /** Classic shell: Settings sits in the top bar. Journey: it is reached from You. */
    showSettingsAction: Boolean = true,
    /** Content between the top bar and the screen: Explore's entries and the coach mark. */
    belowBar: @Composable () -> Unit = {},
    /** Notifications bell (top bar action); empty in previews and tests. */
    bell: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable () -> Unit,
) {
    val sink = LocalUiEventSink.current
    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                if (showNavigation && currentTopLevel != null) {
                    TopAppBar(
                        title = { Text(stringResource(currentTopLevel.labelRes), modifier = Modifier.semantics { heading() }) },
                        actions = {
                            TopBarAction(Icons.Filled.Search, stringResource(R.string.nav_global_search), onOpenSearch)
                            bell()
                            if (showSettingsAction) TopBarAction(Icons.Filled.Settings, stringResource(R.string.nav_settings), onOpenSettings)
                        },
                        // Above the screen below it: a list item scrolled under the bar must not count as covering
                        // the bar's 48 dp actions (accessibility services read the z-order, not the clip).
                        modifier = Modifier.zIndex(1f),
                    )
                }
                if (showNavigation && currentTopLevel != null) belowBar()
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
            tabs.forEach { dest ->
                item(
                    selected = dest == currentTopLevel,
                    onClick = {
                        runCatching { sink.onUiEvent(UiEventKind.TAP, dest.telemetryId, null) }
                        onNavigate(dest)
                    },
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

private fun NavDestination?.isOn(dest: TopLevelDestination): Boolean = isOn(dest.routeClass)

private fun NavDestination?.isOn(route: kotlin.reflect.KClass<out AppRoute>): Boolean = this?.hierarchy?.any { it.hasRoute(route) } == true

/**
 * The NavHost's start: the screen the decided gate shows first, so a signed-out start never composes Home (whose
 * screens make authed requests). Null while the gate is LOADING: compose nothing yet. Later gate changes navigate.
 */
internal fun startDestinationFor(gate: AuthGate, home: AppRoute = HomeRoute): AppRoute? =
    when (gate) {
        AuthGate.LOADING -> null
        AuthGate.SIGNED_OUT -> LoginRoute
        AuthGate.MUST_CHANGE_PASSWORD -> ChangePasswordRoute
        AuthGate.NEEDS_ONBOARDING -> OnboardingRoute()
        AuthGate.READY -> home
    }

/** Where the gate must send the user from [current], or null to stay. The public share viewer is always allowed. */
internal fun gateTarget(gate: AuthGate, current: NavDestination, retakingQuiz: Boolean, home: AppRoute = HomeRoute): AppRoute? {
    if (current.hasRoute(ShareRoute::class)) return null
    return when (gate) {
        AuthGate.LOADING -> null
        AuthGate.SIGNED_OUT -> LoginRoute.takeUnless { current.isSignedOutScreen() }
        AuthGate.MUST_CHANGE_PASSWORD -> ChangePasswordRoute.takeUnless { current.hasRoute(ChangePasswordRoute::class) }
        AuthGate.NEEDS_ONBOARDING -> OnboardingRoute().takeUnless { current.hasRoute(OnboardingRoute::class) }
        AuthGate.READY -> home.takeIf { current.isGateScreen() && !retakingQuiz }
    }
}

/** Login and the account screens a signed-out user moves between (sign-up, check email, verify, forgot / reset, social username). */
private fun NavDestination.isSignedOutScreen(): Boolean =
    hasRoute(LoginRoute::class) ||
        hasRoute(SignUpRoute::class) ||
        hasRoute(CheckEmailRoute::class) ||
        hasRoute(VerifyEmailRoute::class) ||
        hasRoute(ForgotPasswordRoute::class) ||
        hasRoute(ResetPasswordRoute::class) ||
        hasRoute(OauthUsernameRoute::class)

/** An emailed link opens its screen when signed out or ready; behind another gate screen it is held and replayed. */
private fun AuthGate.opensAccountLinks(): Boolean = this == AuthGate.SIGNED_OUT || this == AuthGate.READY

private fun NavHostController.openLink(link: String) {
    val account = DeepLinkIntents.accountRoute(link)
    if (account != null) navigate(account) else handleDeepLink(Intent(Intent.ACTION_VIEW, link.toUri()))
}

/** How long a celebration owns the host before the next one: the length of a short snackbar. */
private const val GAME_DELTA_WINDOW_MS = 4_500L

private val offlinePackTab: TripTabContent = { trip, modifier -> OfflinePackTabContent(trip.id, modifier) }

/** The trip tabs this task wires (Overview and Prep are built in); Offline only when the server allows the pack. */
private fun tripTabContent(offlinePack: Boolean, onAddExpense: (Long) -> Unit, onWhereNext: () -> Unit): Map<TripTab, TripTabContent> =
    buildMap {
        put(TripTab.Today, todayTabContent(onAddExpense = onAddExpense))
        put(TripTab.Money, moneyTabContent())
        put(TripTab.Journal, journalTabContent())
        put(TripTab.Recap, recapTabContent(onWhereNext = onWhereNext))
        if (offlinePack) put(TripTab.Offline, offlinePackTab)
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
