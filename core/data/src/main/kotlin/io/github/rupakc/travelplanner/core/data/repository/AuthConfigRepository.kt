package io.github.rupakc.travelplanner.core.data.repository

/**
 * The account switches of `GET /api/config` the auth screens need. Everything is off / default against an
 * older backend or when the config could not be read.
 *
 * - [signupAvailable] / [resetAvailable]: show "Create account" / "Forgot password?".
 * - [inviteRequired]: `auth.signup_mode == "invite"` (the sign-up form asks for an invite code).
 * - [minPasswordLength]: `auth.min_password_length`, else [DEFAULT_MIN_PASSWORD_LENGTH].
 */
data class AuthFlags(
    val signupAvailable: Boolean = false,
    val resetAvailable: Boolean = false,
    val inviteRequired: Boolean = false,
    val minPasswordLength: Int = DEFAULT_MIN_PASSWORD_LENGTH,
) {
    companion object {
        const val DEFAULT_MIN_PASSWORD_LENGTH = 10
    }
}

/** Reads the auth block of `GET /api/config`; never throws (failures are [AuthFlags] defaults). */
interface AuthConfigRepository {
    suspend fun flags(): AuthFlags
}
