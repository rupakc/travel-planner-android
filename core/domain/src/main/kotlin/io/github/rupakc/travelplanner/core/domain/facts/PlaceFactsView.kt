package io.github.rupakc.travelplanner.core.domain.facts

import io.github.rupakc.travelplanner.core.domain.export.safeUrl
import io.github.rupakc.travelplanner.core.model.plan.PickCitation
import io.github.rupakc.travelplanner.core.model.plan.PlaceFactPick
import io.github.rupakc.travelplanner.core.model.section.PlaceFact
import io.github.rupakc.travelplanner.core.model.section.PlaceFactsData
import io.github.rupakc.travelplanner.core.model.section.PlaceFactsPlace
import io.github.rupakc.travelplanner.core.model.section.SectionData
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Port of the web's `PlaceFactsSection.jsx` helpers and `utils/placeFactText.js` (web `docs/Place-Facts.md`). Pure.
 */
object PlaceFactsView {
    /** The five groups in the web's order. */
    val FACT_TYPES: List<String> = listOf("historical", "fun", "popular", "unbelievable", "little_known")

    /**
     * Places that have at least one fact, each with exactly the [FACT_TYPES] groups (blank facts dropped), as the
     * web's `placesWithFacts`. Empty for an error, a missing section or anything that is not [PlaceFactsData].
     */
    fun places(data: SectionData?): List<PlaceFactsPlace> {
        val facts = data as? PlaceFactsData ?: return emptyList()
        if (facts.isError) return emptyList()
        return facts.places
            .map { p -> p.copy(groups = FACT_TYPES.associateWith { t -> p.groups[t].orEmpty().filter { !it.text.isNullOrBlank() } }) }
            .filter { p -> p.groups.values.any { it.isNotEmpty() } }
    }

    /** True when [data] holds at least one fact to show (web `hasPlaceFacts`). */
    fun hasFacts(data: SectionData?): Boolean = places(data).isNotEmpty()

    /** The groups of [place] that have facts, in [FACT_TYPES] order. */
    fun availableGroups(place: PlaceFactsPlace): List<String> = FACT_TYPES.filter { place.groups[it].orEmpty().isNotEmpty() }

    /** The identity My Plan dedupes on: the fact's `id`, else `<place key>:<text>` (web `factSelection`). */
    fun selectionId(fact: PlaceFact, place: PlaceFactsPlace?): String = fact.id?.takeIf { it.isNotEmpty() } ?: "${place?.key.orEmpty()}:${fact.text.orEmpty()}"

    /**
     * What My Plan keeps for one fact (web `factSelection`): `{id, text, fact_type, place, origin, lang, value_lang,
     * citation}`; `citation` is `{name, url, as_of, attribution, licence_url}` for a crawler fact and null for an AI one.
     * The JSON is written exactly so (nulls included) and kept as the pick's raw object.
     */
    fun selection(fact: PlaceFact, place: PlaceFactsPlace?): PlaceFactPick {
        val crawler = fact.isCrawler
        val citation =
            fact.citation?.takeIf { crawler }?.let {
                PickCitation(name = it.name, url = it.url, asOf = it.asOf, attribution = it.attribution, licenceUrl = it.licenceUrl)
            }
        val pick =
            PlaceFactPick(
                id = selectionId(fact, place),
                text = fact.text,
                factType = fact.factType,
                place = place?.name,
                origin = if (crawler) PlaceFact.ORIGIN_CRAWLER else PlaceFact.ORIGIN_AI,
                lang = langTag(fact.lang),
                valueLang = fact.valueLang,
                citation = citation,
            )
        val raw =
            buildJsonObject {
                put("id", str(pick.id))
                put("text", str(pick.text))
                put("fact_type", str(pick.factType))
                put("place", str(pick.place))
                put("origin", str(pick.origin))
                put("lang", str(pick.lang))
                put("value_lang", str(pick.valueLang))
                put(
                    "citation",
                    citation?.let { c ->
                        buildJsonObject {
                            put("name", str(c.name))
                            put("url", str(c.url))
                            put("as_of", str(c.asOf))
                            put("attribution", str(c.attribution))
                            put("licence_url", str(c.licenceUrl))
                        }
                    } ?: JsonNull,
                )
            }
        return pick.withRaw(raw)
    }

    private fun str(value: String?) = value?.let(::JsonPrimitive) ?: JsonNull

    /** A BCP 47 tag worth marking the text with, else null (`und`, bad or missing tags too). Web `langAttr`. */
    fun langTag(tag: String?): String? {
        if (tag == null || tag.length > MAX_TAG || !TAG.matches(tag)) return null
        return if (tag.equals("und", ignoreCase = true)) null else tag
    }

    /**
     * [text] split around its one quoted value (“…”) when [valueLang] names the value's language, so the value can be
     * marked in its own language; null when there is nothing to mark (no tag, no quote, two quotes, an empty value).
     * Web `quotedValue`: [QuotedValue.before] ends with the opening mark and [QuotedValue.after] starts with the closing one.
     */
    fun quotedValue(text: String?, valueLang: String?): QuotedValue? {
        val lang = langTag(valueLang) ?: return null
        if (text == null) return null
        val open = text.indexOf(OPEN)
        val close = if (open >= 0) text.indexOf(CLOSE, open + 1) else -1
        if (open < 0 || close < 0 || text.indexOf(OPEN, close) >= 0) return null
        val value = text.substring(open + 1, close)
        if (value.isBlank()) return null
        return QuotedValue(before = text.substring(0, open + 1), value = value, after = text.substring(close), lang = lang)
    }

    /** "2026-11-19T08:00:00Z" → "2026-11-19"; null when it does not start with a date (web `day`). */
    fun day(asOf: String?): String? = asOf?.take(DATE_LENGTH)?.takeIf { DATE.matches(it) }

    /** The http(s) link of a source or licence, else null (every link goes through `safeUrl`). */
    fun link(url: String?): String? = safeUrl(url)

    private const val MAX_TAG = 35
    private const val DATE_LENGTH = 10
    private const val OPEN = '“'
    private const val CLOSE = '”'
    private val TAG = Regex("^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{1,8})*$")
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
}

/** A fact's text around its quoted value: render [value] in [lang]. */
data class QuotedValue(val before: String, val value: String, val after: String, val lang: String)
