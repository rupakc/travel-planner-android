package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.AccountRepository
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.auth.AccountDto
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import io.github.rupakc.travelplanner.core.model.auth.DeleteAccountRequest
import io.github.rupakc.travelplanner.core.model.auth.DisplayNameRequest
import io.github.rupakc.travelplanner.core.model.auth.LocaleRequest
import io.github.rupakc.travelplanner.core.model.auth.OnboardingRequest
import io.github.rupakc.travelplanner.core.model.auth.OnboardingResponse
import io.github.rupakc.travelplanner.core.model.auth.QuotaDto
import io.github.rupakc.travelplanner.core.model.auth.SessionInfo
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.auth.VerificationSentDto
import io.github.rupakc.travelplanner.core.network.api.AccountApi
import io.github.rupakc.travelplanner.core.network.apiCall
import io.github.rupakc.travelplanner.core.network.toAppError
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Accounts-API "Account": profile, settings, avatar, export, delete, quota and sessions.
 * The stored session's user is kept in step with every response that returns a user, and a deleted account
 * wipes the session and the local data. Sessions answer `NotFound` on a backend without the list endpoint.
 */
@Singleton
class AccountRepositoryImpl
    @Inject
    constructor(
        private val api: AccountApi,
        private val sessionStore: SessionStore,
        private val sessionCleaner: SessionCleaner,
        private val authRepository: AuthRepository,
        private val gameEvents: GameEvents,
    ) : AccountRepository {
        override suspend fun account(): AppResult<AccountDto> = apiCall { api.account() }.onSuccess { sessionStore.updateUser(it.user) }

        override suspend fun updateDisplayName(displayName: String): AppResult<User> =
            apiCall { api.updateDisplayName(DisplayNameRequest(displayName.trim())) }
                .map { it.user }
                .onSuccess { sessionStore.updateUser(it) }

        override suspend fun settings(): AppResult<AccountSettings> = apiCall { api.settings() }.onSuccess { applySettings(it) }

        override suspend fun patchSettings(patch: JsonObject): AppResult<AccountSettings> =
            apiCall { api.patchSettings(patch) }.onSuccess { applySettings(it) }

        private fun applySettings(s: AccountSettings) {
            gameEvents.setVisible(!s.hideGame)
            gameEvents.setCommunityHidden(s.hideCommunity)
        }

        override suspend fun putOnboarding(request: OnboardingRequest): AppResult<OnboardingResponse> =
            apiCall { api.putOnboarding(request) }.onSuccess { gameEvents.publish(it.game) }

        override suspend fun uploadAvatar(bytes: ByteArray, mimeType: String): AppResult<String?> {
            val ext =
                if (mimeType == "image/png") {
                    "png"
                } else if (mimeType == "image/webp") {
                    "webp"
                } else {
                    "jpg"
                }
            val part = MultipartBody.Part.createFormData("file", "avatar.$ext", bytes.toRequestBody(mimeType.toMediaType()))
            return apiCall { api.putAvatar(part) }.map { it.avatarUrl }.onSuccess { setAvatar(it) }
        }

        override suspend fun removeAvatar(): AppResult<Unit> = apiCall { api.deleteAvatar() }.onSuccess { setAvatar(null) }.map { }

        private suspend fun setAvatar(url: String?) {
            sessionStore.current()?.user?.let { sessionStore.updateUser(it.copy(avatarUrl = url)) }
        }

        override suspend fun requestEmailVerification(locale: String?): AppResult<VerificationSentDto> =
            apiCall { api.requestEmailVerification(LocaleRequest(locale)) }

        override suspend fun unlinkIdentity(provider: String): AppResult<Unit> = apiCall { api.unlinkIdentity(provider) }

        override suspend fun quota(): AppResult<QuotaDto> = apiCall { api.quota() }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun exportTo(target: File): AppResult<File> =
            try {
                withContext(Dispatchers.IO) {
                    api.export().use { body ->
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
                    }
                }
                AppResult.Success(target)
            } catch (e: CancellationException) {
                target.delete()
                throw e
            } catch (e: Exception) {
                target.delete()
                AppResult.Failure(e.toAppError())
            }

        override suspend fun deleteAccount(request: DeleteAccountRequest): AppResult<Unit> =
            apiCall { api.deleteAccount(request) }.onSuccess {
                sessionStore.clear()
                try {
                    sessionCleaner.clearUserData()
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    AppLogger.e("AccountRepository", "Could not wipe local data after delete", e)
                }
            }

        override suspend fun sessions(): AppResult<List<SessionInfo>> {
            val refresh = sessionStore.current()?.refreshToken
            return apiCall { api.sessions(refresh) }.map { it.sessions }
        }

        override suspend fun revokeSession(id: String): AppResult<Unit> = apiCall { api.revokeSession(id) }

        // Unregisters the device first, then revokes everything and ends the session locally. A plain logout()
        // after logout-all would 401 on devices/unregister, emit a false SessionExpired and never unregister.
        override suspend fun logoutAll(): AppResult<Unit> = authRepository.logoutAll()
    }
