package io.github.rupakc.travelplanner.core.model.auth

import io.github.rupakc.travelplanner.core.model.grounding.TolerantListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response of `GET /api/auth/providers` (Phase 1; feature-detected, older servers answer 404).
 * Unknown fields (e.g. `id_token`) are ignored; a malformed entry is skipped, a non-list is empty.
 */
@Serializable
data class AuthProvidersDto(
    @Serializable(with = AuthProviderListSerializer::class) val providers: List<AuthProviderDto> = emptyList(),
)

/** One social sign-in provider: `apple` | `google` | `facebook` | `github`. [startUrl] is untrusted until checked. */
@Serializable
data class AuthProviderDto(
    val id: String? = null,
    val configured: Boolean = false,
    @SerialName("start_url") val startUrl: String? = null,
)

/** Tolerant `providers[]`. */
object AuthProviderListSerializer : TolerantListSerializer<AuthProviderDto>(AuthProviderDto.serializer())
