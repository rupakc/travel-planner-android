package io.github.rupakc.travelplanner.core.model.section

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.LenientInt
import io.github.rupakc.travelplanner.core.model.json.LenientString
import io.github.rupakc.travelplanner.core.model.json.RawBacked
import io.github.rupakc.travelplanner.core.model.json.RawBackedListSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/**
 * The optional `facts` section (web `docs/Place-Facts.md`): short facts about each destination, its region and
 * its country in five groups. Crawler facts carry a [FactCitation]; AI facts never do. An older backend never
 * sends this section, and the backend never streams a `facts` error.
 */
@Serializable
data class PlaceFactsData(
    val places: List<PlaceFactsPlace> = emptyList(),
    @SerialName("fact_types") val factTypes: List<String> = emptyList(),
    override val error: String? = null,
    override val verification: Verification? = null,
) : SectionData

/** One place of [PlaceFactsData]: [scope] is `city`, `place`, `region` or `country`; [groups] is keyed by fact type. */
@Serializable
data class PlaceFactsPlace(
    val key: String? = null,
    val scope: String? = null,
    val name: String? = null,
    val country: String? = null,
    @SerialName("country_name") val countryName: String? = null,
    val destination: String? = null,
    @SerialName("geonames_id") val geonamesId: LenientInt = null,
    val groups: Map<String, PlaceFactList> = emptyMap(),
)

/**
 * One fact. [origin] is `crawler` (sourced, with [citation]) or `ai` ("AI-suggested, not verified", no source).
 * [lang] is the language of [text] as shown, [valueLang] the language of a quoted value in it; both optional.
 */
@Serializable
data class PlaceFact(
    val id: String? = null,
    @SerialName("fact_type") val factType: String? = null,
    val text: String? = null,
    val origin: String? = null,
    @SerialName("date_ref") val dateRef: LenientString = null,
    val lang: String? = null,
    @SerialName("value_lang") val valueLang: String? = null,
    @SerialName("machine_translated") val machineTranslated: LenientBoolean = null,
    val confidence: String? = null,
    val citation: FactCitation? = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<PlaceFact> {
    override fun withRaw(raw: JsonObject): PlaceFact = copy(raw = raw)

    val isCrawler: Boolean get() = origin == ORIGIN_CRAWLER

    companion object {
        const val ORIGIN_CRAWLER = "crawler"
        const val ORIGIN_AI = "ai"
    }
}

object PlaceFactListSerializer : RawBackedListSerializer<PlaceFact>(PlaceFact.serializer())

/** A group of facts: malformed items are skipped and a group that is not an array is empty (never a decoding failure). */
typealias PlaceFactList =
    @Serializable(with = PlaceFactListSerializer::class)
    List<PlaceFact>

/** Where a crawler fact comes from: shown with the fact as its source line (name + link, as-of, licence attribution). */
@Serializable
data class FactCitation(
    @SerialName("source_id") val sourceId: String? = null,
    val name: String? = null,
    val publisher: String? = null,
    val url: String? = null,
    @SerialName("as_of") val asOf: String? = null,
    val licence: String? = null,
    val attribution: String? = null,
    @SerialName("licence_url") val licenceUrl: String? = null,
    val conflict: LenientBoolean = null,
)
