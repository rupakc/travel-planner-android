package io.github.rupakc.travelplanner.feature.social.profile

import io.github.rupakc.travelplanner.core.model.social.OwnProfile
import io.github.rupakc.travelplanner.core.model.social.ProfileUpdate
import io.github.rupakc.travelplanner.core.model.social.ProfileVisibility
import java.util.Locale

/** Longest bio the server accepts. */
internal const val BIO_MAX = 160

internal const val HANDLE_MIN = 3
internal const val HANDLE_MAX = 20
internal const val HANDLE_CHECK_DEBOUNCE_MS = 400L

private const val COUNTRY_LEN = 2
private const val SECONDS_PER_DAY = 86_400L

private val HANDLE = Regex("^[a-z0-9_]{$HANDLE_MIN,$HANDLE_MAX}$")

/** Handles are case-insensitive: lower-case, no spaces, at most [HANDLE_MAX] characters (the field never holds more). */
internal fun normalizeHandle(raw: String): String = raw.trim().lowercase(Locale.ROOT).take(HANDLE_MAX)

/** The syntax the server enforces (`3-20` chars `[a-z0-9_]`); reserved words and taken handles are the server's call. */
internal fun isValidHandle(handle: String): Boolean = HANDLE.matches(handle)

/** An upper-case ISO alpha-2 candidate: letters only, at most two. */
internal fun normalizeCountry(raw: String): String = raw.filter { it.isLetter() && it.code < ASCII_LIMIT }.uppercase(Locale.ROOT).take(COUNTRY_LEN)

private const val ASCII_LIMIT = 128

/** True for an empty country (not set) or a full two-letter code. */
internal fun isCountryComplete(code: String): Boolean = code.isEmpty() || code.length == COUNTRY_LEN

/** The country's name in [locale], or the code itself when the platform does not know it. */
internal fun countryName(code: String, locale: Locale = Locale.getDefault()): String {
    if (code !in Locale.getISOCountries()) return code
    val name = runCatching { Locale.Builder().setRegion(code).build().getDisplayCountry(locale) }.getOrNull()
    return name?.takeIf { it.isNotBlank() && it != code } ?: code
}

/** Whole days (rounded up, at least 1) of a `retry_after` in seconds. */
internal fun retryDays(seconds: Long?): Int? = seconds?.takeIf { it > 0 }?.let { ((it + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY).toInt().coerceAtLeast(1) }

/** What the edit screen holds; every opt-in is a separate switch and defaults off. */
internal data class ProfileForm(
    val handle: String = "",
    val visibility: ProfileVisibility = ProfileVisibility.PRIVATE,
    val bio: String = "",
    val showAvatar: Boolean = false,
    val showHomeCountry: Boolean = false,
    val showCountries: Boolean = false,
    val showLevel: Boolean = false,
    val showBadges: Boolean = false,
    val leaderboardOptIn: Boolean = false,
    val homeCountry: String = "",
) {
    /** Only what differs from [original]; the handle is sent when it is new or changed. */
    fun diff(original: OwnProfile): ProfileUpdate =
        ProfileUpdate(
            handle = handle.takeIf { it != original.handle.orEmpty() },
            visibility = visibility.wire.takeIf { it != null && visibility != original.visibilityMode },
            bio = bio.takeIf { it != original.bio.orEmpty() },
            showAvatar = showAvatar.takeIf { it != original.showAvatar },
            showHomeCountry = showHomeCountry.takeIf { it != original.showHomeCountry },
            showCountries = showCountries.takeIf { it != original.showCountries },
            showLevel = showLevel.takeIf { it != original.showLevel },
            showBadges = showBadges.takeIf { it != original.showBadges },
            leaderboardOptIn = leaderboardOptIn.takeIf { it != original.leaderboardOptIn },
            homeCountry = homeCountry.takeIf { it != original.homeCountry.orEmpty() },
        )

    companion object {
        /** An unknown server visibility reads as private (the safe reading). */
        fun from(profile: OwnProfile): ProfileForm =
            ProfileForm(
                handle = profile.handle.orEmpty(),
                visibility = profile.visibilityMode.takeUnless { it == ProfileVisibility.UNKNOWN } ?: ProfileVisibility.PRIVATE,
                bio = profile.bio.orEmpty(),
                showAvatar = profile.showAvatar,
                showHomeCountry = profile.showHomeCountry,
                showCountries = profile.showCountries,
                showLevel = profile.showLevel,
                showBadges = profile.showBadges,
                leaderboardOptIn = profile.leaderboardOptIn,
                homeCountry = profile.homeCountry.orEmpty(),
            )
    }
}

/** True when sending [this] would change nothing. */
internal fun ProfileUpdate.isEmpty(): Boolean = this == ProfileUpdate()
