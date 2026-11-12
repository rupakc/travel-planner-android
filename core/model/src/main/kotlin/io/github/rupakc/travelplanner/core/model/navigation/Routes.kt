package io.github.rupakc.travelplanner.core.model.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation Compose routes for every screen. Features never depend on
 * each other: to go elsewhere a feature calls a lambda it was given by `app`
 * (e.g. `onOpenPlan(planId)`), and `app` navigates to one of these objects.
 */
sealed interface AppRoute

// --- auth ---------------------------------------------------------------
@Serializable data object LoginRoute : AppRoute

/** Forced password change (temporary password, `requires_password_change`); a gate screen. */
@Serializable data object ChangePasswordRoute : AppRoute

/**
 * Voluntary password change from Settings → Account (not a gate screen: it stays put while
 * signed in and ends with back). Same form and rules as [ChangePasswordRoute].
 */
@Serializable data object AccountChangePasswordRoute : AppRoute

// --- onboarding ---------------------------------------------------------

/** Welcome → questions → result → feature picker. [fromSettings] = re-run from Settings (ends with back, not Home). */
@Serializable data class OnboardingRoute(val fromSettings: Boolean = false) : AppRoute

// --- top level ----------------------------------------------------------
@Serializable data object HomeRoute : AppRoute

/**
 * Search form. [mode]: one of [SearchModes] (null = known destination); [importUrl] pre-fills
 * "From a link" and starts reading it (chat `import_link`, share intents); [recentSearchId] loads that
 * recent search (`RecentSearch.id`, from the `RecentSearchesRepository` cache) into the known-destination
 * form (Home's recent searches row and global search "tap → Search pre-filled"), shifting past dates like the web.
 */
@Serializable data class SearchRoute(
    val mode: String? = null,
    val importUrl: String? = null,
    val recentSearchId: String? = null,
) : AppRoute

/** Streamed results for the search currently held by `SearchRepository` / `CurrentPlanStore`. */
@Serializable data object ResultsRoute : AppRoute

/** "My trips": saved plans list. */
@Serializable data object PlansRoute : AppRoute

@Serializable data class PlanDetailRoute(val planId: Long) : AppRoute

/**
 * The My Plan screen for the shared draft (`CurrentPlanStore`): grouped selections, cost, name,
 * Save / Save as new / New plan and export. Results and Chat open it (web "My Plan" drawer).
 */
@Serializable data object MyPlanRoute : AppRoute

/** Chat; null [threadId] = the most recent thread (or a new one). */
@Serializable data class ChatRoute(val threadId: Long? = null) : AppRoute

/** Watched prices. Deep link parity with web `/alerts?kind=<kind>&watch=<id>`. */
@Serializable data class AlertsRoute(val kind: String? = null, val watchId: Long? = null) : AppRoute

@Serializable data object NotificationsRoute : AppRoute

/** Price alert preferences (digest, instant mode, quiet hours, caps, grouping); needs `alerts_api >= 2`. */
@Serializable data object AlertPrefsRoute : AppRoute

/** Unified offline-first search over trips, chats, recent searches, features and settings (opened from Home / top bars). */
@Serializable data object GlobalSearchRoute : AppRoute

// --- settings -----------------------------------------------------------
@Serializable data object SettingsRoute : AppRoute

/**
 * One settings row, opened from global search: the settings graph shows the screen that owns
 * [entry] (a `SettingsEntry.key` from core:domain globalsearch) scrolled to and highlighting it.
 */
@Serializable data class SettingsEntryRoute(val entry: String) : AppRoute

@Serializable data object PreferencesRoute : AppRoute

@Serializable data object TasteProfileRoute : AppRoute

@Serializable data object FeaturesRoute : AppRoute

@Serializable data object AppearanceRoute : AppRoute

@Serializable data object ServerRoute : AppRoute

@Serializable data object NotificationSettingsRoute : AppRoute

// --- nearby / share -----------------------------------------------------
@Serializable data object NearbyRoute : AppRoute

/** The paste-a-share-link screen (R6: opening a share link the system did not route to the app). */
@Serializable data object ShareOpenLinkRoute : AppRoute

/** Public share-link viewer (`/share/{token}`). */
@Serializable data class ShareRoute(val token: String) : AppRoute

/**
 * The share dialog (Share links 2.0): [kind] is a `ShareKind.wire` (`plan`, `recap`, `savings`), [planId] the plan or trip's
 * plan (null for savings). Features open it through `LocalShareLauncher`; only `feature:share` implements it.
 */
@Serializable data class ShareCreateRoute(val kind: String, val planId: Long? = null) : AppRoute

/** The caller's own share links (Share links 2.0 "My shares"): list, copy, share again, revoke. Reached from the You tab. */
@Serializable data object MySharesRoute : AppRoute

/** Search form modes (web segmented toggle + "Surprise me"). */
object SearchModes {
    const val KNOWN = "known"
    const val SURPRISE = "surprise"
    const val COMPARE = "compare"
    const val IMPORT = "import"
}

/** Deep link scheme/host constants (app manifest + NavHost must agree). */
object DeepLinks {
    const val SCHEME = "travelplanner"
    const val SHARE_PATTERN = "$SCHEME://share/{token}"
    const val ALERTS_PATTERN = "$SCHEME://alerts?kind={kind}&watch={watchId}"
    const val PLAN_PATTERN = "$SCHEME://plan/{planId}"
    const val CHAT_PATTERN = "$SCHEME://chat"
    const val NEARBY_PATTERN = "$SCHEME://nearby"

    /** A trip, optionally on a tab (`travelplanner://trip/12?tab=today`); built by notifications. */
    const val TRIP_PATTERN = "$SCHEME://trip/{tripId}?tab={tab}"
    const val NOTIFICATIONS_PATTERN = "$SCHEME://notifications"
}

// --- Phase 1: accounts ----------------------------------------------------
@Serializable data object SignUpRoute : AppRoute

@Serializable data class CheckEmailRoute(val email: String) : AppRoute

/** Email verification; [token] comes from the deep link (null = paste a link or code). */
@Serializable data class VerifyEmailRoute(val token: String? = null) : AppRoute

@Serializable data object ForgotPasswordRoute : AppRoute

/** Password reset; [token] comes from the deep link (null = paste a link or code). */
@Serializable data class ResetPasswordRoute(val token: String? = null) : AppRoute

/** Pick a username after a social sign-in that answered `needs_username`. */
@Serializable data object OauthUsernameRoute : AppRoute

@Serializable data object AccountRoute : AppRoute

@Serializable data object SessionsRoute : AppRoute

@Serializable data object PlayRoute : AppRoute

// --- Phase 2: journey -------------------------------------------------------
@Serializable data object TripsRoute : AppRoute

/** A trip; [tab] is a tab id (`overview`, `prep`, `money`, `journal`, `recap`, `offline`) or null for the phase default. */
@Serializable data class TripRoute(val tripId: Long, val tab: String? = null) : AppRoute

@Serializable data class TodayRoute(val tripId: Long) : AppRoute

@Serializable data class OfflinePackRoute(val tripId: Long) : AppRoute

@Serializable data class ExpensesRoute(val tripId: Long) : AppRoute

@Serializable data class JournalRoute(val tripId: Long) : AppRoute

@Serializable data class RecapRoute(val tripId: Long) : AppRoute

@Serializable data object YouRoute : AppRoute
