package io.github.rupakc.travelplanner.core.model.section

/**
 * The 16 results sections in the web's `AGENT_ORDER` (api-contract §3.2), with
 * their wire `type` names. `confidence` and `pricing_advisor` are not sections.
 */
enum class SectionId(val wire: String, val staticBacked: Boolean = false, val phaseOne: Boolean = true) {
    FLIGHTS("flights"),
    WEATHER("weather"),
    HOTELS("hotels"),
    ACTIVITIES("activities"),
    PLACES_TO_SEE("places_to_see"),
    WALKING_TOURS("walking_tours"),
    EVENTS("events"),
    VISA("visa", staticBacked = true),
    SIM("sim", staticBacked = true),
    TIPS("tips", staticBacked = true),
    EMERGENCY_CARD("emergency_card", staticBacked = true),
    GETTING_AROUND("getting_around", staticBacked = true),
    FOREX("forex", staticBacked = true),
    ITINERARY("itinerary", phaseOne = false),
    STRESS_TEST("stress_test", phaseOne = false),
    PACKING_LIST("packing_list", phaseOne = false),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        fun fromWire(type: String?): SectionId? = type?.let(byWire::get)
    }
}

/** `source` on section events: Phase-0 static table or AI. Unknown/absent → null. */
enum class DataSource(val wire: String) {
    STATIC("static"),
    AI("ai"),
    ;

    companion object {
        fun fromWire(value: String?): DataSource? = entries.firstOrNull { it.wire == value }
    }
}

/** Web section status machine (api-contract §3.2). */
enum class SectionStatus { WAITING, LOADING, ENHANCING, DONE }
