package io.github.rupakc.travelplanner.core.data.impl

import io.github.rupakc.travelplanner.core.data.repository.AuthProviderInfo
import io.github.rupakc.travelplanner.core.data.repository.AuthProvidersRepository
import io.github.rupakc.travelplanner.core.domain.auth.SafeStartPath
import io.github.rupakc.travelplanner.core.network.BaseUrlProvider
import io.github.rupakc.travelplanner.core.network.api.AuthProvidersApi
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/** Asks the server each call (no cache: the login screen asks once per ViewModel); failures are silent. */
class AuthProvidersRepositoryImpl
    @Inject
    constructor(
        private val api: AuthProvidersApi,
        private val baseUrl: BaseUrlProvider,
    ) : AuthProvidersRepository {
        @Suppress("TooGenericExceptionCaught")
        override suspend fun providers(): Map<String, AuthProviderInfo> =
            try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Feature detection: an unreachable, older or broken server simply has no providers.
                emptyMap()
            }

        private suspend fun load(): Map<String, AuthProviderInfo> {
            val response = api.providers()
            val body = response.body()
            val base = baseUrl.current()?.toString()
            if (!response.isSuccessful || body == null || base == null) return emptyMap()
            return body.providers
                .mapNotNull { p -> p.id?.let { id -> id to AuthProviderInfo(p.configured, SafeStartPath.resolve(p.startUrl, base)) } }
                .toMap()
        }
    }
