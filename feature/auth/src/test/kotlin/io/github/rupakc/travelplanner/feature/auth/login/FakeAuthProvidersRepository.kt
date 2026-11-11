package io.github.rupakc.travelplanner.feature.auth.login

import io.github.rupakc.travelplanner.core.data.repository.AuthProviderInfo
import io.github.rupakc.travelplanner.core.data.repository.AuthProvidersRepository

/** Returns [providers] (empty = the server does not serve `/api/auth/providers`, e.g. a 404). */
internal class FakeAuthProvidersRepository(var providers: Map<String, AuthProviderInfo> = emptyMap()) : AuthProvidersRepository {
    var calls = 0
        private set

    override suspend fun providers(): Map<String, AuthProviderInfo> {
        calls++
        return providers
    }
}
