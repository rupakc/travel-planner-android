package io.github.rupakc.travelplanner.core.model.auth

import io.github.rupakc.travelplanner.core.model.game.GameDelta
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `POST /auth/oauth/exchange`: the handoff [code] plus the PKCE [verifier] (mobile). */
@Serializable
data class OauthExchangeRequest(
    val code: String,
    val verifier: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
)

/** `POST /auth/oauth/complete`. */
@Serializable
data class OauthCompleteRequest(
    @SerialName("signup_token") val signupToken: String,
    val username: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("invite_code") val inviteCode: String? = null,
    val client: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    val locale: String? = null,
)

/**
 * Result of exchange / id-token / complete, flattened over `status`:
 * `signed_in` (session, game), `needs_username` (signupToken, suggestedUsername, displayName, email, inviteRequired)
 * or `linked` (provider). Use [kind] rather than comparing [status] strings.
 */
@Serializable
data class OauthResult(
    val status: String? = null,
    val session: SessionDto? = null,
    val game: GameDelta? = null,
    @SerialName("return_to") val returnTo: String? = null,
    @SerialName("signup_token") val signupToken: String? = null,
    @SerialName("suggested_username") val suggestedUsername: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
    @SerialName("invite_required") val inviteRequired: Boolean = false,
    val provider: String? = null,
) {
    val kind: OauthResultKind
        get() =
            when (status) {
                "signed_in" -> OauthResultKind.SIGNED_IN
                "needs_username" -> OauthResultKind.NEEDS_USERNAME
                "linked" -> OauthResultKind.LINKED
                else -> OauthResultKind.UNKNOWN
            }
}

enum class OauthResultKind { SIGNED_IN, NEEDS_USERNAME, LINKED, UNKNOWN }

/** Callback `?error=` codes of the OAuth redirect. */
object OauthCallbackErrors {
    val ALL: Set<String> =
        setOf(
            "cancelled",
            "state_invalid",
            "provider_error",
            "provider_unavailable",
            "email_in_use_unverified",
            "identity_in_use",
            "account_inactive",
            "bad_request",
        )
}
