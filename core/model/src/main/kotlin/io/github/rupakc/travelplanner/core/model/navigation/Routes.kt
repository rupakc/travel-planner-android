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

/** Public share-link viewer (`/share/{token}`). */
@Serializable data class ShareRoute(val token: String) : AppRoute

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
}
