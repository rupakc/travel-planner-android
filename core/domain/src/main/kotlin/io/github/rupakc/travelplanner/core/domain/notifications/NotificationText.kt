package io.github.rupakc.travelplanner.core.domain.notifications

/**
 * Bundled English notification templates: the fallback when the catalog cannot be fetched (mirrors the server's `en`
 * table; the server catalog wins whenever it answers). `{name}` placeholders.
 */
object NotificationText {
    const val TRIP_T30 = "trip_t30"
    const val TRIP_T7 = "trip_t7"
    const val TRIP_T1 = "trip_t1"
    const val TRIP_MORNING = "trip_morning"
    const val NEARBY_ARRIVAL = "nearby_arrival"

    /** Locales the server catalog serves. */
    val LOCALES = setOf("en", "es", "de", "fr", "hi", "zh", "bn")

    const val DEFAULT_LOCALE = "en"

    val BUNDLED_EN: Map<String, String> =
        mapOf(
            TRIP_T30 to "{destination} is {days} days away. Check your visa and entry rules.",
            TRIP_T7 to "{days} days to {destination}. Time to pack.",
            TRIP_T1 to "Tomorrow you fly to {destination}. Check in and review the weather.",
            TRIP_MORNING to "Good morning from {destination}. Open Today for your plan.",
            "trip_morning_slot" to "First up: {slot}",
            "trip_morning_tip" to "Tip: {tip}",
            "trip_morning_weather" to "Weather: {weather}",
            NEARBY_ARRIVAL to "You're near {place}: {item} is {minutes} min away",
        )

    /** The catalog locale for a BCP-47 tag (`pt-BR` becomes `pt`, which the server answers in English): a supported base language, else `en`. */
    fun catalogLocale(tag: String?): String {
        val base = tag.orEmpty().replace('_', '-').substringBefore('-').lowercase()
        return if (base in LOCALES) base else DEFAULT_LOCALE
    }

    /** Fills `{name}` placeholders of [template] from [params]; a missing parameter renders empty and unknown ones are ignored. */
    fun render(template: String, params: Map<String, Any?>): String =
        PLACEHOLDER.replace(template) { params[it.groupValues[1]]?.toString().orEmpty() }

    private val PLACEHOLDER = Regex("\\{([a-z_]+)}")
}
