package io.github.rupakc.travelplanner.core.model.carbon

import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientDouble
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientString
import io.github.rupakc.travelplanner.core.model.json.LenientStringList
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.section.Flight
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject

/**
 * Answer of `POST /api/carbon/trip` (web `docs/Carbon-Footprint.md`): pure computation on the server, nothing stored.
 * Every list drops items that are not objects or do not decode, so one odd entry never hides the card.
 * [crawlerSources] and [CarbonLine.crawlerSource] come only from a backend whose crawler supplied a number.
 */
@Serializable
data class CarbonFootprint(
    @SerialName("total_kg") val totalKg: LenientDouble = null,
    @SerialName("per_person_kg") val perPersonKg: LenientDouble = null,
    val travellers: LenientInt = null,
    val nights: LenientInt = null,
    val diet: LenientString = null,
    val cabin: LenientString = null,
    @Serializable(with = CarbonCategoryList::class) val categories: List<CarbonCategory> = emptyList(),
    @Serializable(with = CarbonLineList::class) val lines: List<CarbonLine> = emptyList(),
    val comparisons: CarbonComparisons? = null,
    @SerialName("lower_carbon") @Serializable(with = CarbonHintList::class) val lowerCarbon: List<CarbonHint> = emptyList(),
    @Serializable(with = CarbonSourceList::class) val sources: List<CarbonSource> = emptyList(),
    @Serializable(with = CarbonAssumptionList::class) val assumptions: List<CarbonAssumption> = emptyList(),
    val attribution: LenientStringList = emptyList(),
    @SerialName("factors_version") val factorsVersion: LenientString = null,
    @SerialName("crawler_sources") @Serializable(with = CarbonSourceList::class) val crawlerSources: List<CarbonSource> = emptyList(),
)

/** One of `flights`, `stays`, `ground`, `food`, `activities` (always in that order); [share] is 0–1. */
@Serializable
data class CarbonCategory(
    val key: LenientString = null,
    val kg: LenientDouble = null,
    val share: LenientDouble = null,
    val included: LenientBoolean = null,
)

/**
 * One counted line. [label] is the backend's English text; [kind] + [params] let clients translate it.
 * [method] is `measured_tim`, `factor` or `estimate`.
 */
@Serializable
data class CarbonLine(
    val category: LenientString = null,
    val kind: LenientString = null,
    val label: LenientString = null,
    val kg: LenientDouble = null,
    val method: LenientString = null,
    val source: LenientString = null,
    val params: JsonObject? = null,
    @SerialName("crawler_source") @Serializable(with = CarbonSourceOrNull::class) val crawlerSource: CarbonSource? = null,
)

@Serializable
data class CarbonComparisons(
    @SerialName("car_km") val carKm: LenientDouble = null,
    @SerialName("annual_budget_kg") val annualBudgetKg: LenientDouble = null,
    @SerialName("budget_share") val budgetShare: LenientDouble = null,
)

/** "A train could work" hint for a short flight (no timetable check). */
@Serializable
data class CarbonHint(
    val route: LenientString = null,
    val from: LenientString = null,
    val to: LenientString = null,
    @SerialName("rail_km") val railKm: LenientDouble = null,
    val legs: LenientInt = null,
    @SerialName("flight_kg") val flightKg: LenientDouble = null,
    @SerialName("rail_kg") val railKg: LenientDouble = null,
    @SerialName("saving_kg") val savingKg: LenientDouble = null,
    val factor: LenientString = null,
)

/** A source of the factors (also the crawler's per-number source: [asOf], [licenceUrl], [sourceId], [year]). */
@Serializable
data class CarbonSource(
    val id: LenientString = null,
    val name: LenientString = null,
    val url: LenientString = null,
    val publisher: LenientString = null,
    val licence: LenientString = null,
    val attribution: LenientString = null,
    @SerialName("as_of") val asOf: LenientString = null,
    @SerialName("licence_url") val licenceUrl: LenientString = null,
    @SerialName("source_id") val sourceId: LenientString = null,
    val year: LenientInt = null,
)

/** One assumption: English [text], stable [code] + [params] for translation. */
@Serializable
data class CarbonAssumption(
    val code: LenientString = null,
    val text: LenientString = null,
    val params: JsonObject? = null,
)

/**
 * A flight result's `emissions` (Google Travel Impact Model, web `docs/Travel-Impact-Model.md`). Absent on an older
 * backend, with `TIM_ENABLED` off, or for a flight TIM cannot estimate.
 */
@Serializable
data class FlightEmissions(
    @SerialName("kg_co2e_per_pax") val kgPerPassenger: LenientDouble = null,
    val cabin: LenientString = null,
    /** Keys `economy`, `premiumEconomy`, `business`, `first`. */
    @SerialName("by_class") val byClass: JsonObject? = null,
    @SerialName("attribution_url") val attributionUrl: LenientString = null,
    val source: LenientString = null,
    val licence: LenientString = null,
)

/** The flight's TIM figure from its original JSON, or null when absent or unreadable (never a made-up number). */
fun Flight.timEmissions(): FlightEmissions? {
    val obj = raw["emissions"] as? JsonObject ?: return null
    val e = runCatching { TravelJson.decodeFromJsonElement(FlightEmissions.serializer(), obj) }.getOrNull() ?: return null
    return e.takeIf { it.kgPerPassenger != null }
}

/** A list that keeps only the elements that are objects and decode; anything else becomes an empty list. */
open class TolerantObjectListSerializer<T>(private val item: KSerializer<T>) : KSerializer<List<T>> {
    private val delegate = ListSerializer(item)
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<T> {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        val array = decoder.decodeJsonElement() as? JsonArray ?: return emptyList()
        return array.mapNotNull { el ->
            (el as? JsonObject)?.let { runCatching { decoder.json.decodeFromJsonElement(item, it) }.getOrNull() }
        }
    }

    override fun serialize(encoder: Encoder, value: List<T>) = delegate.serialize(encoder, value)
}

/** A [CarbonSource] when the element is an object that decodes, else null. */
object CarbonSourceOrNull : KSerializer<CarbonSource?> {
    private val delegate = CarbonSource.serializer()
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): CarbonSource? {
        if (decoder !is JsonDecoder) return delegate.deserialize(decoder)
        val obj = decoder.decodeJsonElement() as? JsonObject ?: return null
        return runCatching { decoder.json.decodeFromJsonElement(delegate, obj) }.getOrNull()
    }

    override fun serialize(encoder: Encoder, value: CarbonSource?) {
        if (value == null) encoder.encodeNull() else encoder.encodeSerializableValue(delegate, value)
    }
}

object CarbonCategoryList : TolerantObjectListSerializer<CarbonCategory>(CarbonCategory.serializer())

object CarbonLineList : TolerantObjectListSerializer<CarbonLine>(CarbonLine.serializer())

object CarbonHintList : TolerantObjectListSerializer<CarbonHint>(CarbonHint.serializer())

object CarbonSourceList : TolerantObjectListSerializer<CarbonSource>(CarbonSource.serializer())

object CarbonAssumptionList : TolerantObjectListSerializer<CarbonAssumption>(CarbonAssumption.serializer())
