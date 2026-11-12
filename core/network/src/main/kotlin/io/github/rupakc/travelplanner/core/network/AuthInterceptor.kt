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
import okhttp3.Request
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
 * Adds `Authorization: Bearer <token>` when signed in, but only to requests for the app's own
 * backend (built against [PLACEHOLDER_BASE_URL]; this interceptor must run *before*
 * [BaseUrlInterceptor]) and only when the effective backend is the one that issued the token
 * (review I3).
 *
 * Sessions with a refresh token (mobile, Accounts-API) refresh through [SessionManager] (single flight):
 * pre-emptively when the access token expires within 60 s, and once on a `401`. A retry that still answers
 * `401` (a revoked session) signs out. Network errors and `429` on the refresh never sign out. Token-only
 * sessions (older backends) keep the old rule: a `401` for the current token clears the session. A `401`
 * for a token that was already replaced is ignored.
 */
class AuthInterceptor
    @Inject
    constructor(
        private val sessionStore: SessionStore,
        private val authEvents: AuthEvents,
        private val baseUrlProvider: BaseUrlProvider,
        private val sessionManager: SessionManager,
    ) : Interceptor {
        private val placeholderHost = PLACEHOLDER_BASE_URL.toHttpUrl().host

        override fun intercept(chain: Interceptor.Chain): Response {
            val original = chain.request()
            if (original.url.host != placeholderHost || original.header(HEADER) != null) return chain.proceed(original)
            var session = runBlocking { sessionStore.current()?.takeIf { issuedBy(it, baseUrlProvider.current()) } }
            if (session != null && sessionManager.needsPreemptiveRefresh(session)) {
                val stale = session.token
                session = runBlocking { refreshedOrCurrent(stale, session) }
            }
            val token = session?.token ?: return chain.proceed(original)
            val response = chain.proceed(original.withBearer(token))
            return if (response.code == UNAUTHORIZED) onUnauthorized(chain, original, response, session) else response
        }

        private fun onUnauthorized(chain: Interceptor.Chain, original: Request, response: Response, session: Session): Response {
            if (session.refreshToken != null) return recover(chain, original, response, session.token)
            runBlocking { clearIfCurrent(session.token) }
            return response
        }

        private fun recover(chain: Interceptor.Chain, original: Request, rejected: Response, token: String): Response {
            val outcome = runBlocking { sessionManager.refresh(token) }
            if (outcome !is RefreshOutcome.Refreshed) return rejected
            rejected.close()
            val retried = chain.proceed(original.withBearer(outcome.session.token))
            if (retried.code == UNAUTHORIZED) runBlocking { clearIfCurrent(outcome.session.token) }
            return retried
        }

        private suspend fun refreshedOrCurrent(stale: String, fallback: Session?): Session? =
            when (val outcome = sessionManager.refresh(stale)) {
                is RefreshOutcome.Refreshed -> outcome.session
                RefreshOutcome.Invalid -> null
                RefreshOutcome.Transient -> fallback
            }

        private suspend fun clearIfCurrent(token: String) {
            if (sessionStore.current()?.token == token) {
                sessionStore.clear()
                authEvents.emit(AuthEvent.SessionExpired)
            }
        }

        private fun Request.withBearer(token: String): Request = newBuilder().header(HEADER, "Bearer $token").build()

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
