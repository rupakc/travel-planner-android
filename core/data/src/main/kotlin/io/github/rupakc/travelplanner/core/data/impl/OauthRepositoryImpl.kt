package io.github.rupakc.travelplanner.core.data.impl

import android.os.Build
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppLogger
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.repository.GameEvents
import io.github.rupakc.travelplanner.core.data.repository.OauthRepository
import io.github.rupakc.travelplanner.core.data.repository.SessionCleaner
import io.github.rupakc.travelplanner.core.datastore.SessionStore
import io.github.rupakc.travelplanner.core.domain.auth.OauthCallbackLink
import io.github.rupakc.travelplanner.core.domain.auth.Pkce
import io.github.rupakc.travelplanner.core.domain.auth.SafeStartPath
import io.github.rupakc.travelplanner.core.model.auth.LinkTicketRequest
import io.github.rupakc.travelplanner.core.model.auth.LinkTicketResponse
import io.github.rupakc.travelplanner.core.model.auth.OauthCompleteRequest
import io.github.rupakc.travelplanner.core.model.auth.OauthExchangeRequest
import io.github.rupakc.travelplanner.core.model.auth.OauthResult
import io.github.rupakc.travelplanner.core.model.auth.OauthResultKind
import io.github.rupakc.travelplanner.core.model.auth.SessionDto
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.OauthApi
import io.github.rupakc.travelplanner.core.network.apiCall
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * System-browser OAuth with PKCE. The verifier of the one pending attempt lives only in this singleton's memory
 * (never persisted, never logged) and is consumed by the first exchange; a callback without an attempt is refused.
 */
@Singleton
class OauthRepositoryImpl
    @Inject
    constructor(
        private val api: OauthApi,
        private val sessionStore: SessionStore,
        private val baseUrlProvider: BaseUrlProvider,
        private val sessionCleaner: SessionCleaner,
        private val gameEvents: GameEvents,
    ) : OauthRepository {
        private class Attempt(val verifier: String)

        @Volatile private var attempt: Attempt? = null

        override suspend fun begin(provider: String): AppResult<String> {
            if (provider !in PROVIDERS) return AppResult.Failure(AppError.Unknown(null))
            val base = baseUrlProvider.current()?.toString()?.trimEnd('/') ?: return AppResult.Failure(AppError.NotConfigured())
            val verifier = Pkce.newVerifier()
            val url = "$base/api/auth/oauth/$provider/start?" + startQuery(Pkce.challenge(verifier))
            attempt = Attempt(verifier)
            return AppResult.Success(url)
        }

        override suspend fun linkTicket(provider: String): AppResult<LinkTicketResponse> {
            if (provider !in PROVIDERS) return AppResult.Failure(AppError.Unknown(null))
            val base = baseUrlProvider.current()?.toString() ?: return AppResult.Failure(AppError.NotConfigured())
            val response =
                when (val result = apiCall { api.linkTicket(LinkTicketRequest(provider, CLIENT)) }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            // The server's start URL is untrusted until it passes the same-origin check; we add the PKCE parts.
            val safe = SafeStartPath.resolve(response.startUrl, base) ?: return AppResult.Failure(AppError.Unknown(null))
            val verifier = Pkce.newVerifier()
            val path = safe.substringBefore('?')
            val kept =
                safe.substringAfter('?', "").split('&')
                    .filter { it.isNotEmpty() && it.substringBefore('=') !in OWN_PARAMS }
            attempt = Attempt(verifier)
            val query = (kept + startQuery(Pkce.challenge(verifier))).joinToString("&")
            return AppResult.Success(LinkTicketResponse("$path?$query"))
        }

        override suspend fun exchange(code: String): AppResult<OauthResult> {
            // Single use: the verifier is dropped whatever the outcome, so a failed code restarts the flow.
            val current = attempt ?: return AppResult.Failure(AppError.Unknown(null))
            attempt = null
            val response =
                when (val result = apiCall { api.exchange(OauthExchangeRequest(code, current.verifier, deviceName())) }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            return finish(response)
        }

        override suspend fun complete(request: OauthCompleteRequest): AppResult<OauthResult> {
            val body = request.copy(client = request.client ?: CLIENT, deviceName = request.deviceName ?: deviceName())
            val response =
                when (val result = apiCall { api.complete(body) }) {
                    is AppResult.Failure -> return result
                    is AppResult.Success -> result.value
                }
            return finish(response)
        }

        override fun hasPendingAttempt(): Boolean = attempt != null

        override fun cancelPendingAttempt() {
            attempt = null
        }

        private suspend fun finish(response: OauthResult): AppResult<OauthResult> {
            val session = response.session
            if (response.kind != OauthResultKind.SIGNED_IN || session == null) return AppResult.Success(response)
            return when (val stored = persisting(session)) {
                is AppResult.Failure -> {
                    stored
                }

                is AppResult.Success -> {
                    gameEvents.publish(response.game)
                    AppResult.Success(response)
                }
            }
        }

        private fun startQuery(challenge: String): String =
            "client=$CLIENT&challenge=${enc(challenge)}&redirect_uri=${enc(OauthCallbackLink.REDIRECT_URI)}"

        private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

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
                AppLogger.e("OauthRepository", "Could not store the session", e)
                AppResult.Failure(AppError.Unknown(null))
            }

        private fun deviceName(): String? = Build.MODEL.orEmpty().trim().take(MAX_DEVICE_NAME).ifEmpty { null }

        private companion object {
            const val CLIENT = "android"
            const val MAX_DEVICE_NAME = 64
            const val MILLIS = 1000L

            /** Apple is omitted on Android (ruling R2). */
            val OWN_PARAMS = setOf("client", "challenge", "redirect_uri")
            val PROVIDERS = setOf("google", "github", "facebook")
        }
    }
