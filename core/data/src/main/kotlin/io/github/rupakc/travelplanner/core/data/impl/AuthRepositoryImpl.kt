package io.github.rupakc.travelplanner.core.data.impl

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
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.devices.UnregisterDeviceRequest
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
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
    ) : AuthRepository {
        // A 401 or a locally expired JWT only signs out (the session reads null → Login); the user's draft,
        // cached plans, recent searches and chat survive so their work outlives a 24 h token. Local data is
        // wiped only on explicit logout, on login as a different user, and on a server change.

        override val session: Flow<Session?> = sessionStore.session

        override suspend fun login(username: String, password: String): AppResult<User> {
            val response =
                when (val result = apiCall { authApi.login(LoginRequest(username.trim(), password)) }) {
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
                sessionStore.save(Session(response.accessToken, response.user, server))
                response.user
            }
        }

        override suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit> {
            val token =
                when (val result = apiCall { authApi.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            return persisting {
                sessionStore.updateToken(token.accessToken)
                sessionStore.current()?.user?.let { sessionStore.updateUser(it.copy(requiresPasswordChange = false)) }
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

        override suspend fun logout() {
            // Best effort and bounded: a dead network must not keep the user signed in.
            withTimeoutOrNull(UNREGISTER_TIMEOUT_MS) {
                val token = if (pushTokenSource.isPresent) pushTokenSource.get().token() else null
                if (token != null) apiCall { devicesApi.unregister(UnregisterDeviceRequest(token)) }
            }
            sessionStore.clear()
            sessionCleaner.clearUserData()
        }

        companion object {
            const val UNREGISTER_TIMEOUT_MS = 5_000L
        }
    }
