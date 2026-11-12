package io.github.rupakc.travelplanner.feature.account

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.userFacingDetail

/** A message for the user: our own string, or the backend's `detail` as-is when we have no code-specific copy. */
internal data class AccountMessage(
    @param:StringRes val res: Int,
    val detail: String? = null,
) {
    @Composable
    fun text(): String = detail ?: stringResource(res)
}

/** Maps a failure to copy: structured `error_code` first, then 429/network, then the backend detail, then [fallback]. */
internal fun AppError.toMessage(
    @StringRes fallback: Int,
): AccountMessage {
    val own =
        when (AccountRules.codeOf(this)) {
            "already_verified" -> R.string.account_err_already_verified
            "no_email" -> R.string.account_err_no_email
            "email_unavailable" -> R.string.account_err_email_unavailable
            "last_sign_in_method" -> R.string.account_err_last_method
            "password_incorrect" -> R.string.account_err_password_incorrect
            "confirmation_mismatch" -> R.string.account_err_confirmation
            "last_admin" -> R.string.account_err_last_admin
            "avatar_too_large" -> R.string.account_err_avatar_large
            "avatar_type", "avatar_unreadable" -> R.string.account_err_avatar_type
            "display_name_invalid" -> R.string.account_err_name_invalid
            "identity_not_found" -> R.string.account_err_identity_gone
            else -> null
        }
    if (own != null) return AccountMessage(own)
    return when (this) {
        is AppError.Network -> AccountMessage(R.string.account_err_network)
        is AppError.RateLimited -> AccountMessage(R.string.account_err_rate_limited, userFacingDetail())
        else -> AccountMessage(fallback, userFacingDetail())
    }
}
