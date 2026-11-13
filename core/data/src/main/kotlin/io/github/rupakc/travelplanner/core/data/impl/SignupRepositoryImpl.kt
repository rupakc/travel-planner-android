package io.github.rupakc.travelplanner.core.data.impl

import android.os.Build
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.data.repository.SignupRepository
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.auth.EmailRequest
import io.github.rupakc.travelplanner.core.model.auth.PasswordResetConfirmRequest
import io.github.rupakc.travelplanner.core.model.auth.PasswordResetResponse
import io.github.rupakc.travelplanner.core.model.auth.SessionDto
import io.github.rupakc.travelplanner.core.model.auth.SignupRequest
import io.github.rupakc.travelplanner.core.model.auth.VerificationSentDto
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailRequest
import io.github.rupakc.travelplanner.core.model.auth.VerifyEmailResponse
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.SignupApi
import io.github.rupakc.travelplanner.core.network.apiCall
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Native sign-up, email verification and password reset. A verify or reset success that carries a session
 * stores it through the existing [SessionStore] (token, refresh token, expiry, user id), wiping the previous
 * user's local data first exactly like login does.
 */
@Singleton
class SignupRepositoryImpl
    @Inject
    constructor(
        private val api: SignupApi,
        private val sessionStore: SessionStore,
        private val baseUrlProvider: BaseUrlProvider,
        private val sessionCleaner: SessionCleaner,
        private val gameEvents: GameEvents,
    ) : SignupRepository {
        override suspend fun signup(request: SignupRequest): AppResult<VerificationSentDto> =
            apiCall { api.signup(request.copy(client = request.client ?: CLIENT)) }

        override suspend fun resendVerification(email: String, locale: String?): AppResult<VerificationSentDto> =
            apiCall { api.resendVerification(EmailRequest(email.trim(), locale)) }

        override suspend fun verifyEmail(token: String): AppResult<VerifyEmailResponse> =
            when (val redeemed = redeemEmailToken(token)) {
                is AppResult.Failure -> redeemed
                is AppResult.Success -> storeSession(redeemed.value).map { redeemed.value }
            }

        override suspend fun redeemEmailToken(token: String): AppResult<VerifyEmailResponse> =
            apiCall { api.verifyEmail(VerifyEmailRequest(token.trim(), CLIENT, deviceName())) }

        override suspend fun storeSession(response: VerifyEmailResponse): AppResult<Unit> {
            val session = response.session ?: return AppResult.Success(Unit)
            return persisting(session).map { gameEvents.publish(response.game) }
        }

        override suspend fun requestPasswordReset(email: String, locale: String?): AppResult<Unit> =
            apiCall { api.requestPasswordReset(EmailRequest(email.trim(), locale)) }.map { }

        override suspend fun confirmPasswordReset(token: String, newPassword: String): AppResult<PasswordResetResponse> {
            val body = PasswordResetConfirmRequest(token.trim(), newPassword, CLIENT, deviceName())
            val response =
                when (val result = apiCall { api.confirmPasswordReset(body) }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            val session = response.session ?: return AppResult.Success(response)
            return persisting(session).map { response }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun persisting(dto: SessionDto): AppResult<Unit> =
            try {
                val server = baseUrlProvider.current()?.toString()?.trimEnd('/')
                val otherUser = sessionStore.lastUsername()?.let { it != dto.user.username } == true
                val otherServer = sessionStore.lastServer()?.let { previous -> server != null && previous != server } == true
                if (otherUser || otherServer) sessionCleaner.clearUserData()
                val expiresAt = dto.expiresIn?.let { System.currentTimeMillis() + it * MILLIS }
                sessionStore.save(Session(dto.accessToken, dto.user, server, dto.refreshToken, expiresAt, dto.user.id))
                AppResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("SignupRepository", "Could not store the session", e)
                AppResult.Failure(AppError.Unknown(null))
            }

        private fun deviceName(): String? = Build.MODEL.orEmpty().trim().take(MAX_DEVICE_NAME).ifEmpty { null }

        private companion object {
            const val CLIENT = "android"
            const val MAX_DEVICE_NAME = 64
            const val MILLIS = 1000L
        }
    }
