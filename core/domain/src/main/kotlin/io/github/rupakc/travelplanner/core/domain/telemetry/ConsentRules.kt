package io.github.rupakc.travelplanner.core.domain.telemetry

/**
 * Where telemetry consent is required (web `frontend/src/telemetry/consent.js` `consentRequired`):
 * all `Europe` zones except the non-EU/EEA/UK/CH ones, plus the Atlantic zones of EU/EEA states. Everywhere
 * else telemetry is on and can be switched off.
 */
object ConsentRules {
    private val EUROPE_EXCLUDED =
        setOf(
            "Europe/Istanbul",
            "Europe/Moscow",
            "Europe/Minsk",
            "Europe/Kiev",
            "Europe/Kyiv",
        )
    private val ATLANTIC_INCLUDED =
        setOf(
            "Atlantic/Reykjavik",
            "Atlantic/Canary",
            "Atlantic/Madeira",
            "Atlantic/Azores",
            "Atlantic/Faroe",
        )

    fun required(zoneId: String?): Boolean {
        if (zoneId.isNullOrEmpty()) return false
        if (zoneId.startsWith("Europe/")) return zoneId !in EUROPE_EXCLUDED
        return zoneId in ATLANTIC_INCLUDED
    }
}
