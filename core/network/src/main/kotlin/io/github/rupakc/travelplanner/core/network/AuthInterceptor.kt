package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.datastore.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/** Session events the app shell reacts to (snackbar "Signed out — please sign in again"). */
sealed interface AuthEvent {
    data object SessionExpired : AuthEvent
}

@Singleton
class AuthEvents
    @Inject
    constructor() {
        private val events = MutableSharedFlow<AuthEvent>(extraBufferCapacity = 1)
        val flow: SharedFlow<AuthEvent> = events.asSharedFlow()

        fun emit(event: AuthEvent) {
            events.tryEmit(event)
        }
    }

/**
 * Adds `Authorization: Bearer <token>` when signed in. Any 401 on a request that
 * carried the current token clears the session (the auth gate then shows Login)
 * — spec §4 Networking. A 401 for a token that was already replaced is ignored.
 */
class AuthInterceptor
    @Inject
    constructor(
        private val sessionStore: SessionStore,
        private val authEvents: AuthEvents,
    ) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val token = if (original.header(HEADER) != null) null else runBlocking { sessionStore.current()?.token }
            val request = if (token != null) original.newBuilder().header(HEADER, "Bearer $token").build() else original
            val response = chain.proceed(request)
            if (response.code == UNAUTHORIZED && token != null) {
                runBlocking {
                    if (sessionStore.current()?.token == token) {
                        sessionStore.clear()
                        authEvents.emit(AuthEvent.SessionExpired)
                    }
                }
            }
            return response
        }

        companion object {
            const val HEADER = "Authorization"
            private const val UNAUTHORIZED = 401
        }
    }
