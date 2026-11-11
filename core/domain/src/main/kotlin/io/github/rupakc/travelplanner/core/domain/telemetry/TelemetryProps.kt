package io.github.rupakc.travelplanner.core.domain.telemetry

/**
 * Prop-value rules of the telemetry wire contract (web `docs/Telemetry.md`, `registry.py`): a value is a
 * token `^[A-Za-z0-9_.:/-]{1,64}$`, an integer within ±2^53, a finite number or a boolean. Anything else
 * (free text, emails, null, collections) is dropped before it is queued.
 */
object TelemetryProps {
    val TOKEN: Regex = Regex("^[A-Za-z0-9_.:/-]{1,64}$")

    private const val MAX_SAFE = 1L shl 53
    private const val MAX_TOKEN = 64
    private val NOT_TOKEN_CHAR = Regex("[^A-Za-z0-9_.:/-]")

    fun isToken(value: String): Boolean = TOKEN.matches(value)

    /** Keeps token strings, Int/Long within ±2^53 (as Long), finite Float/Double (as Double), Booleans; drops everything else. */
    fun clean(props: Map<String, Any?>): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        for ((key, value) in props) {
            val kept: Any? =
                when (value) {
                    is String -> value.takeIf(::isToken)
                    is Boolean -> value
                    is Int -> value.toLong()
                    is Long -> value.takeIf { it in -MAX_SAFE..MAX_SAFE }
                    is Float -> value.toDouble().takeIf { it.isFinite() }
                    is Double -> value.takeIf { it.isFinite() }
                    else -> null
                }
            if (kept != null) out[key] = kept
        }
        return out
    }

    /** Non-token chars → '_', at most 64 chars, blank → "0.0.0". */
    fun cleanVersion(version: String): String {
        if (version.isBlank()) return "0.0.0"
        return version.replace(NOT_TOKEN_CHAR, "_").take(MAX_TOKEN)
    }
}
