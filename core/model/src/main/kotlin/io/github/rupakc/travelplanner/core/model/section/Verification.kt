package io.github.rupakc.travelplanner.core.model.section

import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `data.verification` on /api/search sections (api-contract §3.3). Not used by chat. */
@Serializable
data class Verification(
    /** verified | partial | unverified | conflict */
    val level: String? = null,
    val label: String? = null,
    val summary: String? = null,
    val checks: List<VerificationCheck> = emptyList(),
    @SerialName("items_verified") val itemsVerified: LenientInt = null,
    @SerialName("items_total") val itemsTotal: LenientInt = null,
    val pending: LenientBoolean = null,
) {
    val levelKind: VerificationLevel? get() = VerificationLevel.fromWire(level)
}

@Serializable
data class VerificationCheck(
    val label: String? = null,
    /** "pass" seen; full enum UNVERIFIED. */
    val status: String? = null,
    val detail: String? = null,
    val url: String? = null,
)

enum class VerificationLevel(val wire: String) {
    VERIFIED("verified"),
    PARTIAL("partial"),
    UNVERIFIED("unverified"),
    CONFLICT("conflict"),
    ;

    companion object {
        fun fromWire(value: String?): VerificationLevel? = entries.firstOrNull { it.wire == value }
    }
}
