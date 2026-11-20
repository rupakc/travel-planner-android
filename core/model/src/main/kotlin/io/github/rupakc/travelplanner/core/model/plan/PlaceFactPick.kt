package io.github.rupakc.travelplanner.core.model.plan

import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.json.LenientBoolean
import io.github.rupakc.travelplanner.core.model.json.RawBacked
import io.github.rupakc.travelplanner.core.model.section.PlaceFact
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/**
 * One place fact kept in My Plan (`selections.place_facts`, web `factSelection`):
 * `{id, text, fact_type, place, origin, lang, value_lang, citation}`, where [citation] is null for AI facts.
 * [quote] is the flag of plans saved before the crawler stopped serving source text; such a fact is shown as
 * plain text with its attribution. The original JSON is kept ([raw]) so a web plan round-trips unchanged.
 */
@Serializable
data class PlaceFactPick(
    val id: String? = null,
    val text: String? = null,
    @SerialName("fact_type") val factType: String? = null,
    val place: String? = null,
    val origin: String? = null,
    val lang: String? = null,
    @SerialName("value_lang") val valueLang: String? = null,
    val citation: PickCitation? = null,
    val quote: LenientBoolean = null,
    @Transient override val raw: JsonObject = EmptyJsonObject,
) : RawBacked<PlaceFactPick> {
    override fun withRaw(raw: JsonObject): PlaceFactPick = copy(raw = raw)

    val isCrawler: Boolean get() = origin == PlaceFact.ORIGIN_CRAWLER
}

/** The source line a crawler fact keeps in My Plan (web `factSelection`: name, url, as_of, attribution, licence_url). */
@Serializable
data class PickCitation(
    val name: String? = null,
    val url: String? = null,
    @SerialName("as_of") val asOf: String? = null,
    val attribution: String? = null,
    @SerialName("licence_url") val licenceUrl: String? = null,
)
