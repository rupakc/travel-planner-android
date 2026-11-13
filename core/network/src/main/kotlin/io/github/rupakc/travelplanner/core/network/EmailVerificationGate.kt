package io.github.rupakc.travelplanner.core.network

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Set when the backend refused an AI route with `403 email_unverified` (the account is not confirmed yet).
 * The app shell shows the "Confirm your email" banner for it; it clears when the account turns out confirmed.
 */
@Singleton
class EmailVerificationGate
    @Inject
    constructor() {
        private val mutableBlocked = MutableStateFlow(false)
        val blocked: StateFlow<Boolean> = mutableBlocked.asStateFlow()

        fun markBlocked() {
            mutableBlocked.value = true
        }

        fun clear() {
            mutableBlocked.value = false
        }
    }

/**
 * Watches responses to our own backend for `403` + `error_code: email_unverified` and raises [EmailVerificationGate].
 * Social routes answer the same code for their own flows and keep their local copy, so they are skipped. The body is only
 * peeked (never consumed) and anything unexpected passes through untouched.
 */
class EmailUnverifiedInterceptor
    @Inject
    constructor(private val gate: EmailVerificationGate) : Interceptor {
        private val placeholderHost = PLACEHOLDER_BASE_URL.toHttpUrl().host

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)
            if (response.code != FORBIDDEN || request.url.host != placeholderHost) return response
            if (request.url.encodedPath.contains("/social")) return response
            val code = runCatching { HttpErrorMapper.parseBody(response.peekBody(PEEK_BYTES).string()).code }.getOrNull()
            if (code == CODE) gate.markBlocked()
            return response
        }

        private companion object {
            const val FORBIDDEN = 403
            const val PEEK_BYTES = 4096L
            const val CODE = "email_unverified"
        }
    }
