package io.github.rupakc.travelplanner.core.model.section

/**
 * The 16 results sections in the web's `AGENT_ORDER` (api-contract §3.2), with
 * their wire `type` names, plus the [optional] `facts` section (Place facts, web
 * `docs/Place-Facts.md`) placed after Tips as on the web. `confidence` and
 * `pricing_advisor` are not sections.
 *
 * An [optional] section has no status until its first event arrives, is left out
 * of the progress count ([CORE]) and is shown only when it holds something: an
 * older backend never sends it.
 */
enum class SectionId(val wire: String, val staticBacked: Boolean = false, val phaseOne: Boolean = true, val optional: Boolean = false) {
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
    PLACE_FACTS("facts", optional = true),
    EMERGENCY_CARD("emergency_card", staticBacked = true),
    GETTING_AROUND("getting_around", staticBacked = true),
    FOREX("forex", staticBacked = true),
    ITINERARY("itinerary", phaseOne = false),
    STRESS_TEST("stress_test", phaseOne = false),
    PACKING_LIST("packing_list", phaseOne = false),
    ;

    companion object {
        private val byWire = entries.associateBy { it.wire }

        /** The 16 always-expected sections (every non-[optional] one): the progress count's total. */
        val CORE: List<SectionId> = entries.filterNot { it.optional }

        fun fromWire(type: String?): SectionId? = type?.let(byWire::get)
    }
}

/**
 * `source` on section events: Phase-0 static table, crawler facts (Phase 0 of `facts`) or AI. Unknown/absent → null.
 * [STATIC] and [CRAWLER] are both Phase-0 data an AI pass may still replace ([isPhaseZero]).
 */
enum class DataSource(val wire: String) {
    STATIC("static"),
    CRAWLER("crawler"),
    AI("ai"),
    ;

    val isPhaseZero: Boolean get() = this == STATIC || this == CRAWLER

    companion object {
        fun fromWire(value: String?): DataSource? = entries.firstOrNull { it.wire == value }
    }
}

/** Web section status machine (api-contract §3.2). */
enum class SectionStatus { WAITING, LOADING, ENHANCING, DONE }
