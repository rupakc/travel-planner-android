package io.github.rupakc.travelplanner.feature.auth

import androidx.annotation.StringRes
import io.github.rupakc.travelplanner.core.common.AppError

/** A message shown on the auth screens: the backend's `detail` as-is, or one of our own strings. */
sealed interface AuthMessage {
    /** Backend `detail` (e.g. "Invalid username or password", 429 "Too many requests — …"). */
    data class Server(val text: String) : AuthMessage

    /** One of this module's strings. */
    data class Local(
        @param:StringRes val res: Int,
    ) : AuthMessage
}

/** Login failures: network → connection copy; backend detail when present; else "Login failed. Please try again." */
internal fun AppError.toLoginMessage(): AuthMessage =
    when {
        this is AppError.Network -> AuthMessage.Local(R.string.auth_error_network)
        !message.isNullOrBlank() -> AuthMessage.Server(message.orEmpty())
        else -> AuthMessage.Local(R.string.auth_error_login_failed)
    }

/** Change-password failures (web: detail, else "Password change failed"; unexpected → "Something went wrong…"). */
internal fun AppError.toChangePasswordMessage(): AuthMessage =
    when {
        this is AppError.Network -> AuthMessage.Local(R.string.auth_error_network)
        !message.isNullOrBlank() -> AuthMessage.Server(message.orEmpty())
        this is AppError.Unknown || this is AppError.Decoding -> AuthMessage.Local(R.string.auth_error_generic)
        else -> AuthMessage.Local(R.string.auth_error_change_failed)
    }
