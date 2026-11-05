package io.github.rupakc.travelplanner.feature.auth

import androidx.annotation.StringRes
import io.github.rupakc.travelplanner.core.common.AppError

/** A message shown on the auth screens: the backend's `detail` as-is, or one of our own strings. */
sealed interface AuthMessage {
    /** Backend `detail` (e.g. "Invalid username or password", 429 "Too many requests — …"). */
    data class Server(val text: String) : AuthMessage

    /** One of this module's strings. [suggestServer]: the server address is the likely fix (Login shows a "Server" action). */
    data class Local(
        @param:StringRes val res: Int,
        val suggestServer: Boolean = false,
    ) : AuthMessage
}

/**
 * The backend `detail` when [this] came from an HTTP response, else null. Client-side failures
 * (decoding, unknown, not configured) carry exception text that must never reach the user.
 */
internal fun AppError.serverDetail(): String? =
    when (this) {
        is AppError.Client, is AppError.RateLimited, is AppError.Unauthorized, is AppError.Forbidden,
        is AppError.NotFound, is AppError.Server,
        -> message?.takeIf { it.isNotBlank() }

        else -> null
    }

/** Errors that mean "no usable server": they get their own copy and a "Server" action on Login. */
private fun AppError.serverProblem(): AuthMessage? =
    when (this) {
        is AppError.NotConfigured -> AuthMessage.Local(R.string.auth_error_not_configured, suggestServer = true)
        is AppError.Network -> AuthMessage.Local(R.string.auth_error_network, suggestServer = true)
        else -> null
    }

/** Login failures: server problems → their copy; backend detail when present; else "Login failed. Please try again." */
internal fun AppError.toLoginMessage(): AuthMessage =
    serverProblem()
        ?: serverDetail()?.let(AuthMessage::Server)
        ?: AuthMessage.Local(R.string.auth_error_login_failed)

/** Change-password failures (web: detail, else "Password change failed"; unexpected → "Something went wrong…"). */
internal fun AppError.toChangePasswordMessage(): AuthMessage =
    serverProblem()
        ?: serverDetail()?.let(AuthMessage::Server)
        ?: when (this) {
            is AppError.Unknown, is AppError.Decoding, is AppError.Interrupted -> AuthMessage.Local(R.string.auth_error_generic)
            else -> AuthMessage.Local(R.string.auth_error_change_failed)
        }
