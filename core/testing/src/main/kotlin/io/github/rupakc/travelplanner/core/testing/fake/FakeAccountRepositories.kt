package io.github.rupakc.travelplanner.core.testing.fake

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.GameRepository
import io.github.rupakc.travelplanner.core.data.repository.OauthRepository
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonObject

/** Settable results, call log of method names (with the argument where useful). */
class FakeAccountRepository : AccountRepository {
    val calls = mutableListOf<String>()
    var accountResult: AppResult<AccountDto> = AppResult.Success(AccountDto(user = TestUser))
    var updateDisplayNameResult: AppResult<User> = AppResult.Success(TestUser)
    var settingsResult: AppResult<AccountSettings> = AppResult.Success(AccountSettings())
    var patchSettingsResult: AppResult<AccountSettings> = AppResult.Success(AccountSettings())
    var putOnboardingResult: AppResult<OnboardingResponse> = AppResult.Success(OnboardingResponse())
    var uploadAvatarResult: AppResult<String?> = AppResult.Success(null)
    var removeAvatarResult: AppResult<Unit> = AppResult.Success(Unit)
    var requestEmailVerificationResult: AppResult<VerificationSentDto> = AppResult.Success(VerificationSentDto(status = "verification_sent"))
    var unlinkIdentityResult: AppResult<Unit> = AppResult.Success(Unit)
    var quotaResult: AppResult<QuotaDto> = AppResult.Success(QuotaDto())
    var exportResult: AppResult<File>? = null
    var deleteAccountResult: AppResult<Unit> = AppResult.Success(Unit)
    var sessionsResult: AppResult<List<SessionInfo>> = AppResult.Success(emptyList())
    var revokeSessionResult: AppResult<Unit> = AppResult.Success(Unit)
    var logoutAllResult: AppResult<Unit> = AppResult.Success(Unit)

    /** The patches received, in order. */
    val patches = mutableListOf<JsonObject>()
    val onboardingRequests = mutableListOf<OnboardingRequest>()
    val deleteRequests = mutableListOf<DeleteAccountRequest>()

    override suspend fun account() = accountResult.also { calls += "account" }

    override suspend fun updateDisplayName(displayName: String) = updateDisplayNameResult.also { calls += "updateDisplayName:$displayName" }

    override suspend fun settings() = settingsResult.also { calls += "settings" }

    override suspend fun patchSettings(patch: JsonObject) =
        patchSettingsResult.also {
            patches += patch
            calls += "patchSettings"
        }

    override suspend fun putOnboarding(request: OnboardingRequest) =
        putOnboardingResult.also {
            onboardingRequests += request
            calls += "putOnboarding"
        }

    override suspend fun uploadAvatar(bytes: ByteArray, mimeType: String) = uploadAvatarResult.also { calls += "uploadAvatar:${bytes.size}:$mimeType" }

    override suspend fun removeAvatar() = removeAvatarResult.also { calls += "removeAvatar" }

    override suspend fun requestEmailVerification(locale: String?) = requestEmailVerificationResult.also { calls += "requestEmailVerification" }

    override suspend fun unlinkIdentity(provider: String) = unlinkIdentityResult.also { calls += "unlinkIdentity:$provider" }

    override suspend fun quota() = quotaResult.also { calls += "quota" }

    override suspend fun exportTo(target: File): AppResult<File> = (exportResult ?: AppResult.Success(target)).also { calls += "exportTo" }

    override suspend fun deleteAccount(request: DeleteAccountRequest) =
        deleteAccountResult.also {
            deleteRequests += request
            calls += "deleteAccount"
        }

    override suspend fun sessions() = sessionsResult.also { calls += "sessions" }

    override suspend fun revokeSession(id: String) = revokeSessionResult.also { calls += "revokeSession:$id" }

    override suspend fun logoutAll() = logoutAllResult.also { calls += "logoutAll" }
}

class FakeSignupRepository : SignupRepository {
    val calls = mutableListOf<String>()
    val signupRequests = mutableListOf<SignupRequest>()
    var signupResult: AppResult<VerificationSentDto> = AppResult.Success(VerificationSentDto(status = "verification_sent"))
    var resendResult: AppResult<VerificationSentDto> = AppResult.Success(VerificationSentDto(status = "verification_sent"))
    var verifyResult: AppResult<VerifyEmailResponse> = AppResult.Success(VerifyEmailResponse(status = "verified"))
    var requestResetResult: AppResult<Unit> = AppResult.Success(Unit)
    var confirmResetResult: AppResult<PasswordResetResponse> = AppResult.Success(PasswordResetResponse(status = "password_reset"))

    override suspend fun signup(request: SignupRequest) =
        signupResult.also {
            signupRequests += request
            calls += "signup"
        }

    override suspend fun resendVerification(email: String, locale: String?) = resendResult.also { calls += "resend:$email" }

    override suspend fun verifyEmail(token: String) = verifyResult.also { calls += "verifyEmail:$token" }

    override suspend fun requestPasswordReset(email: String, locale: String?) = requestResetResult.also { calls += "requestReset:$email" }

    override suspend fun confirmPasswordReset(token: String, newPassword: String) = confirmResetResult.also { calls += "confirmReset:$token" }
}

class FakeOauthRepository : OauthRepository {
    val calls = mutableListOf<String>()
    var beginResult: AppResult<String> = AppResult.Success("https://example.test/api/auth/oauth/github/start")
    var exchangeResult: AppResult<OauthResult> = NotStubbed
    var completeResult: AppResult<OauthResult> = NotStubbed
    var linkTicketResult: AppResult<LinkTicketResponse> = AppResult.Success(LinkTicketResponse())
    var pending = false

    override suspend fun begin(provider: String) =
        beginResult.also {
            pending = it is AppResult.Success
            calls += "begin:$provider"
        }

    override suspend fun exchange(code: String) =
        exchangeResult.also {
            pending = false
            calls += "exchange:$code"
        }

    override suspend fun complete(request: OauthCompleteRequest) = completeResult.also { calls += "complete:${request.username}" }

    override suspend fun linkTicket(provider: String) = linkTicketResult.also { calls += "linkTicket:$provider" }

    override fun hasPendingAttempt() = pending

    override fun cancelPendingAttempt() {
        pending = false
    }
}

class FakeGameRepository : GameRepository {
    val calls = mutableListOf<String>()
    var meResult: AppResult<GameMe> = AppResult.Success(GameMe())
    var catalogResult: AppResult<GameCatalog> = AppResult.Success(GameCatalog())

    override suspend fun me() = meResult.also { calls += "me" }

    override suspend fun catalog() = catalogResult.also { calls += "catalog" }
}

/** Records every published non-empty delta; collect [deltas] or read [published]. */
class FakeGameEvents : GameEvents {
    private val flow = MutableSharedFlow<GameDelta>(replay = 16, extraBufferCapacity = 16)
    override val deltas: Flow<GameDelta> = flow
    val published = mutableListOf<GameDelta>()
    private val shown = kotlinx.coroutines.flow.MutableStateFlow(true)
    override val visible: kotlinx.coroutines.flow.StateFlow<Boolean> = shown

    override fun setVisible(value: Boolean) {
        shown.value = value
    }

    override fun publish(delta: GameDelta?) {
        if (delta == null || delta.isEmpty || !shown.value) return
        published += delta
        flow.tryEmit(delta)
    }
}

/** Settable [AuthFlags]. */
class FakeAuthConfigRepository(
    var flags: io.github.rupakc.travelplanner.core.data.repository.AuthFlags = io.github.rupakc.travelplanner.core.data.repository.AuthFlags(),
) :
    io.github.rupakc.travelplanner.core.data.repository.AuthConfigRepository {
    override suspend fun flags() = flags
}
