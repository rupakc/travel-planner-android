package io.github.rupakc.travelplanner.core.network

import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.model.settings.Session
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
 * Adds `Authorization: Bearer <token>` when signed in — but only to requests for the app's own
 * backend (built against [PLACEHOLDER_BASE_URL]; this interceptor must run *before*
 * [BaseUrlInterceptor]) and only when the effective backend is the one that issued the token
 * (review I3). Any 401 on a request that carried the current token clears the session (the auth
 * gate then shows Login) — spec §4 Networking. A 401 for a token that was already replaced is ignored.
 */
class AuthInterceptor
    @Inject
    constructor(
        private val sessionStore: SessionStore,
        private val authEvents: AuthEvents,
        private val baseUrlProvider: BaseUrlProvider,
    ) : Interceptor {
        private val placeholderHost = PLACEHOLDER_BASE_URL.toHttpUrl().host

        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            val ownBackend = original.url.host == placeholderHost
            val token =
                if (!ownBackend || original.header(HEADER) != null) {
                    null
                } else {
                    runBlocking { sessionStore.current()?.takeIf { issuedBy(it, baseUrlProvider.current()) }?.token }
                }
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

            /** True when [session] may be sent to [base] (same origin it was issued by, or not recorded). */
            fun issuedBy(session: Session, base: HttpUrl?): Boolean {
                val issuer = session.baseUrl ?: return true
                return base != null && issuer.trimEnd('/') == base.toString().trimEnd('/')
            }

            private const val UNAUTHORIZED = 401
        }
    }
