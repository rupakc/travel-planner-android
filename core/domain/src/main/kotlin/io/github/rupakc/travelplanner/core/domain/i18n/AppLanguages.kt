package io.github.rupakc.travelplanner.core.domain.i18n

/** The supported UI languages and how the effective one is chosen (mirrors the web's `frontend/src/i18n`). */
object AppLanguages {
    /** Picker order. */
    val CODES: List<String> = listOf("en", "es", "de", "fr", "hi", "zh", "bn")

    private const val FALLBACK = "en"

    /**
     * First app-locale tag whose base language is supported, else the first such system tag, else "en".
     * Base = the subtag before '-' (or '_'), lowercased ("zh-Hant-TW" gives zh).
     */
    fun resolve(appLocaleTags: List<String>, systemLocaleTags: List<String>): String =
        firstSupported(appLocaleTags) ?: firstSupported(systemLocaleTags) ?: FALLBACK

    /** The BCP-47 tag for `Intl`-style formatting and telemetry: zh-CN, hi-IN, bn-IN, else the code itself. */
    fun intlTag(code: String): String =
        when (code) {
            "zh" -> "zh-CN"
            "hi" -> "hi-IN"
            "bn" -> "bn-IN"
            else -> code
        }

    private fun firstSupported(tags: List<String>): String? =
        tags.firstNotNullOfOrNull { tag ->
            tag.trim().split('-', '_').first().lowercase().takeIf { it in CODES }
        }
}
