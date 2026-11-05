package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.common.map
import io.github.rupakc.travelplanner.core.common.onSuccess
import io.github.rupakc.travelplanner.core.data.repository.AuthRepository
import io.github.rupakc.travelplanner.core.data.repository.PushTokenSource
import io.github.rupakc.travelplanner.core.database.ChatDao
import io.github.rupakc.travelplanner.core.database.PlanDao
import io.github.rupakc.travelplanner.core.database.PlanDraftDao
import io.github.rupakc.travelplanner.core.database.RecentSearchDao
import io.github.rupakc.travelplanner.core.database.SeenNotificationDao
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.auth.ChangePasswordRequest
import io.github.rupakc.travelplanner.core.model.auth.EmailUpdateRequest
import io.github.rupakc.travelplanner.core.model.auth.LoginRequest
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.devices.UnregisterDeviceRequest
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.api.AuthApi
import io.github.rupakc.travelplanner.core.network.api.DevicesApi
import io.github.rupakc.travelplanner.core.network.apiCall
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class AuthRepositoryImpl
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val devicesApi: DevicesApi,
        private val sessionStore: SessionStore,
        private val pushTokenSource: Optional<PushTokenSource>,
        private val planDao: PlanDao,
        private val chatDao: ChatDao,
        private val recentSearchDao: RecentSearchDao,
        private val seenNotificationDao: SeenNotificationDao,
        private val planDraftDao: PlanDraftDao,
    ) : AuthRepository {
        override val session: Flow<Session?> = sessionStore.session

        override suspend fun login(username: String, password: String): AppResult<User> =
            apiCall { authApi.login(LoginRequest(username.trim(), password)) }
                .onSuccess { sessionStore.save(Session(it.accessToken, it.user)) }
                .map { it.user }

        override suspend fun changePassword(currentPassword: String, newPassword: String): AppResult<Unit> =
            apiCall { authApi.changePassword(ChangePasswordRequest(currentPassword, newPassword)) }
                .onSuccess { token ->
                    sessionStore.updateToken(token.accessToken)
                    sessionStore.current()?.user?.let { sessionStore.updateUser(it.copy(requiresPasswordChange = false)) }
                }
                .map { }

        override suspend fun refreshUser(): AppResult<User> = apiCall { authApi.me() }.onSuccess { sessionStore.updateUser(it) }

        override suspend fun updateEmail(email: String): AppResult<String?> =
            apiCall { authApi.updateEmail(EmailUpdateRequest(email.trim())) }
                .onSuccess { r -> sessionStore.current()?.user?.let { sessionStore.updateUser(it.copy(email = r.email)) } }
                .map { it.email }

        override suspend fun logout() {
            val token = if (pushTokenSource.isPresent) pushTokenSource.get().token() else null
            if (token != null) apiCall { devicesApi.unregister(UnregisterDeviceRequest(token)) }
            sessionStore.clear()
            // Per-user caches; chat history is per user too (web keeps it per username).
            planDao.clear()
            recentSearchDao.clear()
            seenNotificationDao.clear()
            planDraftDao.clear()
            chatDao.clear()
        }
    }
