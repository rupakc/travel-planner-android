package io.github.rupakc.travelplanner.core.data.repository

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.model.auth.AccountDto
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.auth.DeleteAccountRequest
import io.github.rupakc.travelplanner.core.model.auth.LinkTicketResponse
import io.github.rupakc.travelplanner.core.model.auth.OauthCompleteRequest
import io.github.rupakc.travelplanner.core.model.auth.OauthResult
import io.github.rupakc.travelplanner.core.model.auth.OnboardingRequest
import io.github.rupakc.travelplanner.core.model.auth.OnboardingResponse
import io.github.rupakc.travelplanner.core.model.auth.PasswordResetResponse
import io.github.rupakc.travelplanner.core.model.auth.QuotaDto
import io.github.rupakc.travelplanner.core.model.auth.SessionInfo
import io.github.rupakc.travelplanner.core.model.auth.SignupRequest
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.auth.VerificationSentDto
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailResponse
import io.github.rupakc.travelplanner.core.model.game.GameCatalog
import io.github.rupakc.travelplanner.core.model.game.GameDelta
import io.github.rupakc.travelplanner.core.model.game.GameMe
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/** The signed-in account (Accounts-API "Account"). Every call needs `features.accounts`. */
interface AccountRepository {
    suspend fun account(): AppResult<AccountDto>

    suspend fun updateDisplayName(displayName: String): AppResult<User>

    suspend fun settings(): AppResult<AccountSettings>

    /** [patch] holds only the changed keys (a JSON null clears `language`, `timezone`, `bucket_list_goal`). */
    suspend fun patchSettings(patch: JsonObject): AppResult<AccountSettings>

    /** Fire-and-forget callers ignore the result; the game delta of the final step is forwarded to `GameEvents`. */
    suspend fun putOnboarding(request: OnboardingRequest): AppResult<OnboardingResponse>

    /** Uploads [bytes] (already downscaled below 2 MB) as `file`; returns the new avatar URL. */
    suspend fun uploadAvatar(bytes: ByteArray, mimeType: String): AppResult<String?>

    suspend fun removeAvatar(): AppResult<Unit>

    suspend fun requestEmailVerification(locale: String?): AppResult<VerificationSentDto>

    suspend fun unlinkIdentity(provider: String): AppResult<Unit>

    suspend fun quota(): AppResult<QuotaDto>

    /** Streams `GET /account/export` into [target] (never into memory); returns [target]. */
    suspend fun exportTo(target: File): AppResult<File>

    /** On success the caller wipes local data (`SessionCleaner`). */
    suspend fun deleteAccount(request: DeleteAccountRequest): AppResult<Unit>

    /** Task B1: [AppResult.Failure] with `NotFound` when the backend has no sessions endpoint. */
    suspend fun sessions(): AppResult<List<SessionInfo>>

    suspend fun revokeSession(id: String): AppResult<Unit>

    /** `POST /auth/logout-all`, then the local session is cleared. */
    suspend fun logoutAll(): AppResult<Unit>
}

/** Native sign-up, email verification and password reset. */
interface SignupRepository {
    suspend fun signup(request: SignupRequest): AppResult<VerificationSentDto>

    suspend fun resendVerification(email: String, locale: String?): AppResult<VerificationSentDto>

    /** Stores the session when the response carries one. */
    suspend fun verifyEmail(token: String): AppResult<VerifyEmailResponse>

    suspend fun requestPasswordReset(email: String, locale: String?): AppResult<Unit>

    /** Stores the returned session. */
    suspend fun confirmPasswordReset(token: String, newPassword: String): AppResult<PasswordResetResponse>
}

/** OAuth hand-off. The verifier of the pending attempt is held by the implementation, never by callers. */
interface OauthRepository {
    /** Begins an attempt: returns the browser URL (`/api/auth/oauth/<p>/start?...`) for [provider], or a failure. */
    suspend fun begin(provider: String): AppResult<String>

    /** Exchanges the callback [code] with the pending verifier. Stores the session on `signed_in`. */
    suspend fun exchange(code: String): AppResult<OauthResult>

    /** Finishes a `needs_username` result. Stores the session. */
    suspend fun complete(request: OauthCompleteRequest): AppResult<OauthResult>

    /** `POST /auth/oauth/link-ticket` for linking from the account area. */
    suspend fun linkTicket(provider: String): AppResult<LinkTicketResponse>

    /** True while an attempt waits for its callback. */
    fun hasPendingAttempt(): Boolean

    fun cancelPendingAttempt()
}

/** Game reads. A `404` is [AppResult.Failure] with `NotFound` (the game is off and every game element hides). */
interface GameRepository {
    suspend fun me(): AppResult<GameMe>

    /** Cached for 5 minutes. */
    suspend fun catalog(): AppResult<GameCatalog>
}

/** Where every repository forwards a response's `game` delta; the UI host in `core:ui` collects it. */
interface GameEvents {
    val deltas: Flow<GameDelta>

    /** True once the game is known to be on for the signed-in user; false (the default) hides every celebration. */
    val visible: StateFlow<Boolean>

    /** Records what `hide_game` / `GET /game/me` say; nothing is published or counted in telemetry while false. */
    fun setVisible(value: Boolean)

    /** Ignores null and empty deltas. */
    fun publish(delta: GameDelta?)
}
