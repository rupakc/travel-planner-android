package io.github.rupakc.travelplanner.core.model.settings

import io.github.rupakc.travelplanner.core.model.auth.User

/**
 * A signed-in session (token from login / change-password; JWT lasts 24 h, no refresh).
 * [baseUrl] is the backend that issued the token (normalised, no trailing slash); the token is only
 * ever sent there. Null for sessions saved before it was recorded (sent to the current backend).
 */
data class Session(val token: String, val user: User, val baseUrl: String? = null)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Device-only app settings. [baseUrlOverride] null = the flavour default
 * (`BuildConfig.DEFAULT_BASE_URL`). [localeTag] null = follow the system.
 */
data class AppSettings(
    val baseUrlOverride: String? = null,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val dynamicColor: Boolean = false,
    val localeTag: String? = null,
    val tripNotifications: Boolean = true,
    val priceAlertNotifications: Boolean = true,
    val nearbyNotifications: Boolean = false,
    /** Nearby suggestions outside trip dates too (default: only during an active trip). */
    val nearbyNotificationsAlways: Boolean = false,
)

/**
 * Result of the onboarding quiz, stored on the device (spec §6). Type names are
 * `TravellerType.name` values from core:domain; [answers] maps question id → chosen option ids.
 */
data class TravellerProfile(
    val onboardingCompleted: Boolean = false,
    val primaryType: String? = null,
    val secondaryTypes: List<String> = emptyList(),
    val answers: Map<String, List<String>> = emptyMap(),
    val completedAtEpochMillis: Long? = null,
)

/** User feature toggles on top of type defaults: visible = defaults ∪ enabled − disabled (`FeatureId.id` strings). */
data class FeatureOverrides(val enabled: Set<String> = emptySet(), val disabled: Set<String> = emptySet())
