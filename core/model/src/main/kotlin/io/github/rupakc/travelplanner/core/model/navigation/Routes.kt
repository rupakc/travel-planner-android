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

@Serializable data object ChangePasswordRoute : AppRoute

// --- onboarding ---------------------------------------------------------

/** Welcome → questions → result → feature picker. [fromSettings] = re-run from Settings (ends with back, not Home). */
@Serializable data class OnboardingRoute(val fromSettings: Boolean = false) : AppRoute

// --- top level ----------------------------------------------------------
@Serializable data object HomeRoute : AppRoute

/**
 * Search form. [mode]: one of [SearchModes]; [importUrl] pre-fills "From a link"
 * (chat `import_link`, share intents).
 */
@Serializable data class SearchRoute(val mode: String? = null, val importUrl: String? = null) : AppRoute

/** Streamed results for the search currently held by `SearchRepository` / `CurrentPlanStore`. */
@Serializable data object ResultsRoute : AppRoute

/** Compare table for 2–3 destinations of the current compare request. */
@Serializable data object CompareRoute : AppRoute

/** "My trips": saved plans list. */
@Serializable data object PlansRoute : AppRoute

@Serializable data class PlanDetailRoute(val planId: Long) : AppRoute

/** Chat; null [threadId] = the most recent thread (or a new one). */
@Serializable data class ChatRoute(val threadId: Long? = null) : AppRoute

/** Watched prices. Deep link parity with web `/alerts?kind=<kind>&watch=<id>`. */
@Serializable data class AlertsRoute(val kind: String? = null, val watchId: Long? = null) : AppRoute

@Serializable data object NotificationsRoute : AppRoute

// --- settings -----------------------------------------------------------
@Serializable data object SettingsRoute : AppRoute

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
