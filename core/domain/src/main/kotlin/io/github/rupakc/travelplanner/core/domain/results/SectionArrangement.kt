package io.github.rupakc.travelplanner.core.domain.results

import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.model.json.RawBacked
import io.github.rupakc.travelplanner.core.model.section.ActivitiesData
import io.github.rupakc.travelplanner.core.model.section.EventsData
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.GettingAroundData
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.PlacesData
import io.github.rupakc.travelplanner.core.model.section.SectionData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.core.model.section.TipsData
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import java.text.Normalizer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A results section in display order; [relevant] = shown expanded for this traveller (spec §6.3). */
data class ArrangedSection(val id: SectionId, val relevant: Boolean)

/**
 * Section order by traveller type (spec §6.3): sections whose optional feature
 * is visible for the traveller come first (web `AGENT_ORDER` kept within each
 * group); the others follow, collapsed — never removed.
 */
object SectionArrangement {
    /** Results sections backed by an optional feature; the rest are core and always relevant. */
    val FEATURE_OF: Map<SectionId, FeatureId> =
        mapOf(
            SectionId.WALKING_TOURS to FeatureId.WALKING_TOURS,
            SectionId.EVENTS to FeatureId.EVENTS,
            SectionId.VISA to FeatureId.VISA_CHECKER,
            SectionId.SIM to FeatureId.SIM,
            SectionId.EMERGENCY_CARD to FeatureId.EMERGENCY_CARD,
            SectionId.FOREX to FeatureId.FOREX,
            SectionId.STRESS_TEST to FeatureId.TRIP_HEALTH_CHECK,
            SectionId.PACKING_LIST to FeatureId.PACKING_LIST,
        )

    /** [visible] null = no traveller profile/catalogue yet → web order, all relevant. */
    fun arrange(visible: Set<FeatureId>?): List<ArrangedSection> {
        val all = SectionId.entries.map { id -> ArrangedSection(id, visible == null || FEATURE_OF[id]?.let(visible::contains) ?: true) }
        return all.filter { it.relevant } + all.filterNot { it.relevant }
    }
}

/**
 * In-results text filter ("easy to search and filter"): keeps the items of a
 * section whose own JSON text contains the query (case- and accent-insensitive).
 */
object ResultsTextFilter {
    /** Lower-cased, accent-folded text for matching. */
    fun normalize(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFKD).replace(Regex("[\\u0300-\\u036f]"), "").trim()

    private fun collect(element: JsonElement?, into: StringBuilder) {
        when (element) {
            is JsonPrimitive -> if (element.isString) into.append(element.content).append(' ')
            is JsonObject -> element.values.forEach { collect(it, into) }
            is JsonArray -> element.forEach { collect(it, into) }
            else -> Unit
        }
    }

    /** True when any string value of [json] contains the normalized [query]. */
    fun matches(json: JsonElement?, query: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return normalize(StringBuilder().also { collect(json, it) }.toString()).contains(q)
    }

    private fun <T : RawBacked<T>> List<T>.keep(q: String) = filter { matches(it.raw, q) }

    private fun keepFlights(list: List<Flight>, q: String) = list.keep(q)

    /**
     * [data] narrowed to matching items plus the match count. Sections without an
     * item list match as a whole ([raw] text) → count 1 or 0.
     */
    @Suppress("CyclomaticComplexMethod")
    fun filter(data: SectionData, raw: JsonObject, query: String): Pair<SectionData, Int> {
        if (normalize(query).isEmpty()) return data to Int.MAX_VALUE
        return when (data) {
            is FlightsData -> {
                val legs = data.legs.map { it.copy(results = keepFlights(it.results, query)) }
                val results = keepFlights(data.results, query)
                data.copy(results = results, legs = legs) to if (data.legs.isNotEmpty()) legs.sumOf { it.results.size } else results.size
            }

            is HotelsData -> {
                data.results.keep(query).let { data.copy(results = it) to it.size }
            }

            is ActivitiesData -> {
                data.results.keep(query).let { data.copy(results = it) to it.size }
            }

            is PlacesData -> {
                data.results.keep(query).let { data.copy(results = it) to it.size }
            }

            is WalkingToursData -> {
                data.results.keep(query).let { data.copy(results = it) to it.size }
            }

            is EventsData -> {
                data.results.keep(query).let { data.copy(results = it) to it.size }
            }

            is SimData -> {
                data.plans.keep(query).let { data.copy(plans = it) to it.size }
            }

            is TipsData -> {
                data.tips.keep(query).let { data.copy(tips = it) to it.size }
            }

            is GettingAroundData -> {
                data.options.keep(query).let { data.copy(options = it) to it.size }
            }

            else -> {
                data to if (matches(raw, query)) 1 else 0
            }
        }
    }
}
