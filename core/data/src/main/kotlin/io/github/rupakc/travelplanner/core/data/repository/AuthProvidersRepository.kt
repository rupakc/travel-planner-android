package io.github.rupakc.travelplanner.core.data.repository

/** A social sign-in provider as the server reports it; [startUrl] is already checked by `SafeStartPath` (null = unsafe or none). */
data class AuthProviderInfo(val configured: Boolean, val startUrl: String?)

/** Social sign-in providers of the current server (`GET /api/auth/providers`, feature-detected). */
interface AuthProvidersRepository {
    /** Providers by id (`apple`, `google`, …). Never throws: a 404, any error or a malformed body is an empty map. */
    suspend fun providers(): Map<String, AuthProviderInfo>
}
