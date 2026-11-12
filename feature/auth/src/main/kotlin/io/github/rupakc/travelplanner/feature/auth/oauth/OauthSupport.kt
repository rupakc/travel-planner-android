package io.github.rupakc.travelplanner.feature.auth.oauth

import androidx.annotation.StringRes
import io.github.rupakc.travelplanner.feature.auth.R

/** Our copy for a callback `?error=` code (web `errors` namespace); an unknown code reads as `bad_request`. */
@StringRes
internal fun oauthErrorCopy(code: String): Int =
    when (code) {
        "cancelled" -> R.string.auth_oauth_err_cancelled
        "state_invalid" -> R.string.auth_oauth_err_state_invalid
        "provider_error" -> R.string.auth_oauth_err_provider_error
        "provider_unavailable" -> R.string.auth_oauth_err_provider_unavailable
        "email_in_use_unverified" -> R.string.auth_oauth_err_email_in_use_unverified
        "identity_in_use" -> R.string.auth_oauth_err_identity_in_use
        "account_inactive" -> R.string.auth_oauth_err_account_inactive
        else -> R.string.auth_oauth_err_bad_request
    }
