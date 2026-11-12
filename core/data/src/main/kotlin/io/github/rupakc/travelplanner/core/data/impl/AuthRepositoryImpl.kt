package io.github.rupakc.travelplanner.core.data.impl

import android.os.Build
import io.github.rupakc.travelplanner.core.common.AppClock
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.PushTokenSource
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.auth.ChangePasswordRequest
import io.github.rupakc.travelplanner.core.model.auth.EmailUpdateRequest
import io.github.rupakc.travelplanner.core.model.auth.LoginRequest
import io.github.rupakc.travelplanner.core.model.auth.LogoutRequest
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.devices.UnregisterDeviceRequest
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.AccountApi
import io.github.rupakc.travelplanner.core.network.api.AuthApi
import io.github.rupakc.travelplanner.core.network.api.DevicesApi
import io.github.rupakc.travelplanner.core.network.apiCall
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AuthRepositoryImpl
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val devicesApi: DevicesApi,
        private val sessionStore: SessionStore,
        private val pushTokenSource: Optional<PushTokenSource>,
        private val baseUrlProvider: BaseUrlProvider,
        private val sessionCleaner: SessionCleaner,
        private val accountApi: AccountApi,
        private val clock: AppClock,
    ) : AuthRepository {
        // A 401 or a locally expired JWT only signs out (the session reads null → Login); the user's draft,
        // cached plans, recent searches and chat survive so their work outlives a 24 h token. Local data is
        // wiped only on explicit logout, on login as a different user, and on a server change.

        override val session: Flow<Session?> = sessionStore.session

        override suspend fun login(username: String, password: String): AppResult<User> {
            val response =
                when (val result = apiCall { authApi.login(LoginRequest(username.trim(), password, CLIENT, deviceName())) }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            return persisting {
                val server = baseUrlProvider.current()?.toString()?.trimEnd('/')
                val previousUser = sessionStore.lastUsername()
                val previousServer = sessionStore.lastServer()
                // Local data belongs to (user, server): wipe it when either differs (review I6, R2-I2).
                val otherUser = previousUser != null && previousUser != response.user.username
                val otherServer = previousServer != null && server != null && previousServer != server
                if (otherUser || otherServer) sessionCleaner.clearUserData()
                sessionStore.save(
                    Session(
                        token = response.accessToken,
                        user = response.user,
                        baseUrl = server,
                        refreshToken = response.refreshToken,
                        expiresAtEpochMs = expiryOf(response.expiresIn),
                        userId = response.user.id,
                    ),
                )
                response.user
            }
        }

        override suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit> {
            val token =
                when (val result = apiCall { authApi.changePassword(ChangePasswordRequest(currentPassword, newPassword, CLIENT)) }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            return persisting {
                // The server revokes every refresh token on a password change: keep the returned pair (mobile).
                sessionStore.current()?.let {
                    sessionStore.save(
                        it.copy(
                            token = token.accessToken,
                            refreshToken = token.refreshToken ?: it.refreshToken,
                            expiresAtEpochMs = expiryOf(token.expiresIn) ?: it.expiresAtEpochMs,
                            user = it.user.copy(requiresPasswordChange = false),
                        ),
                    )
                }
                Unit
            }
        }

        /** Storage failures after a successful call become a Failure, never an exception (review R2-I1). */
        @Suppress("TooGenericExceptionCaught")
        private suspend fun <T> persisting(block: suspend () -> T): AppResult<T> =
            try {
                AppResult.Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e("AuthRepository", "Could not store the session", e)
                AppResult.Failure(AppError.Unknown(null))
            }

        override suspend fun refreshUser(): AppResult<User> = apiCall { authApi.me() }.onSuccess { sessionStore.updateUser(it) }

        override suspend fun updateEmail(email: String): AppResult<String?> =
            apiCall { authApi.updateEmail(EmailUpdateRequest(email.trim())) }
                .onSuccess { r -> sessionStore.current()?.user?.let { sessionStore.updateUser(it.copy(email = r.email)) } }
                .map { it.email }

        private fun expiryOf(expiresIn: Long?): Long? = expiresIn?.let { clock.now().toEpochMilli() + it * MILLIS }

        // Build.MODEL is null under plain JVM unit tests; the server accepts at most 64 characters.
        private fun deviceName(): String? = Build.MODEL?.trim()?.take(MAX_DEVICE_NAME)?.ifEmpty { null }

        override suspend fun logoutAll(): AppResult<Unit> {
            // Unregister this device first: once logout-all succeeds the token is dead and devices/unregister would 401.
            val push = pushTokenSource.orElse(null)
            unregisterDevice(push)
            val result = apiCall { accountApi.logoutAll() }
            // The token is dead after a successful call; on failure the user stays signed in so they can retry.
            if (result is AppResult.Success) {
                if (push != null) withTimeoutOrNull(DELETE_TOKEN_TIMEOUT_MS) { push.delete() }
                endSession()
            }
            return result
        }

        private suspend fun unregisterDevice(push: PushTokenSource?) {
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
                val token = push?.token()
                if (token != null) apiCall { devicesApi.unregister(UnregisterDeviceRequest(token)) }
            }
        }

        private suspend fun endSession() {
            sessionStore.clear()
            sessionCleaner.clearUserData()
        }

        override suspend fun logout() {
            val refreshToken = sessionStore.current()?.refreshToken
            // Best effort and bounded: a dead network must not keep the user signed in.
            val push = pushTokenSource.orElse(null)
            unregisterDevice(push)
            // Revoke this device's refresh family (best effort, bounded) before the token is forgotten.
            if (refreshToken != null) withTimeoutOrNull(LOGOUT_TIMEOUT_MS) { apiCall { accountApi.logout(LogoutRequest(refreshToken)) } }
            // Even if the unregister never arrived, a deleted token is pruned by the server on its next send,
            // so this phone stops receiving the user's alerts (API review I1).
            if (push != null) withTimeoutOrNull(DELETE_TOKEN_TIMEOUT_MS) { push.delete() }
            endSession()
        }

        companion object {
            private const val CLIENT = "android"
            private const val MAX_DEVICE_NAME = 64
            private const val MILLIS = 1000L
            const val LOGOUT_TIMEOUT_MS = 3_000L
            const val UNREGISTER_TIMEOUT_MS = 3_000L
            const val DELETE_TOKEN_TIMEOUT_MS = 2_000L
        }
    }
