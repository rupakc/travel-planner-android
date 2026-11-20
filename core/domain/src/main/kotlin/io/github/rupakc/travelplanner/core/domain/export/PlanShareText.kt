package io.github.rupakc.travelplanner.core.domain.export

import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.model.plan.PlaceFactPick
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Flight
import kotlinx.serialization.json.JsonObject

/**
 * Localised headings for [PlanShareText]; the feature fills them from its string resources so
 * the text follows the app language (domain code holds no UI copy).
 */
data class PlanTextLabels(
    val flights: String,
    val hotel: String,
    val activities: String,
    val places: String,
    val walkingTours: String,
    val events: String,
    val sim: String,
    val gettingAround: String,
    val tips: String,
    val itinerary: String,
    /** "Day %d" */
    val day: (Int) -> String,
    /** "Estimated cost: %s" */
    val estimatedCost: (String) -> String,
    /** "%s / night" */
    val perNight: (String) -> String,
    /** "Estimate" (web label for estimated prices) */
    val estimate: String,
    /** "Place facts" (web print `placeFacts`); the section is left out while it is blank. */
    val placeFacts: String = "",
    /** "AI-suggested, not verified", under every AI fact. */
    val aiFact: String = "",
    /** "Source: %s", a crawler fact's source name. */
    val source: (String) -> String = { it },
)

/**
 * Plain-text plan for "Share as text" (the phone's take on the web's Print / PDF page): plan
 * name, trip summary, each selected item with its own price and only http(s) links ([safeUrl]),
 * and the day-by-day itinerary from [PlanIcs.planItineraryDays]. Never adds a number the plan
 * does not contain.
 */
object PlanShareText {
    fun build(
        planName: String,
        summaryLines: List<String>,
        selections: PlanSelections,
        itineraryDays: List<JsonObject>,
        totalCostUsd: Double?,
        labels: PlanTextLabels,
    ): String =
        buildString {
            appendLine(planName)
            summaryLines.filter(String::isNotBlank).forEach(::appendLine)
            totalCostUsd?.takeIf { it > 0 }?.let { appendLine(labels.estimatedCost(formatPrice(it))) }

            val flights = listOfNotNull(selections.flight) + selections.flights
            section(labels.flights, flights) { flightLine(it, labels.estimate) + link(it.bookingUrl) }
            section(labels.hotel, listOfNotNull(selections.hotel)) { h ->
                listOfNotNull(
                    h.name,
                    h.location,
                    h.pricePerNightUsd?.takeIf { it > 0 }?.let { labels.perNight(formatPrice(it)) },
                ).joinToString(", ") + link(h.bookingUrl)
            }
            section(labels.activities, selections.activities) { a -> named(a.name, a.priceUsd) + link(a.bookingUrl) }
            section(labels.places, selections.placesToSee) { p -> named(p.name, p.admissionFeeUsd) + link(p.infoUrl) }
            section(labels.walkingTours, selections.walkingTours) { t -> (t.name ?: "") + link(t.url) }
            section(labels.events, selections.events) { e ->
                listOfNotNull(e.name, listOfNotNull(e.startDate, e.endDate).distinct().joinToString(" – ").ifEmpty { null }).joinToString(", ")
            }
            section(labels.sim, listOfNotNull(selections.sim)) { s ->
                named(listOfNotNull(s.provider, s.planName).joinToString(" "), s.priceUsd) + link(s.url)
            }
            section(labels.gettingAround, selections.gettingAround) { o -> listOfNotNull(o.name, o.priceInfo).joinToString(", ") + link(o.bookingUrl) }
            section(labels.tips, selections.tips) { it.title.orEmpty() }
            if (labels.placeFacts.isNotBlank()) section(labels.placeFacts, selections.placeFacts) { factLine(it, labels) }

            if (itineraryDays.isNotEmpty()) {
                appendLine()
                appendLine(labels.itinerary)
                for (day in itineraryDays) {
                    val number = day["day_number"].looseNumber()?.toInt()
                    val heading = listOfNotNull(number?.let(labels.day), day["date"].text(), day["city"].text(), day["theme"].text())
                    appendLine(heading.joinToString(", "))
                    for (slot in day["slots"].objects()) {
                        val key = slot["key"].text()
                        val activity = key?.let { selections.itineraryEdits[it]?.get("activity").text() } ?: slot["activity"].text() ?: continue
                        val range = slot["start_time"].text()?.let { s -> slot["end_time"].text()?.let { "$s–$it" } }
                        val note = key?.let { selections.itineraryNotes[it] }?.takeIf(String::isNotBlank)
                        appendLine("  • " + listOfNotNull(range ?: slot["time_of_day"].text(), activity, slot["location"].text()).joinToString(" · "))
                        note?.let { appendLine("    $it") }
                    }
                }
            }
        }.trimEnd()

    private fun <T> StringBuilder.section(title: String, items: List<T>, line: (T) -> String) {
        val lines = items.map(line).filter(String::isNotBlank)
        if (lines.isEmpty()) return
        appendLine()
        appendLine(title)
        lines.forEach { appendLine("  • $it") }
    }

    /** A place fact as the printed plan shows it: "Place: text", then its source, link and attribution, or the AI label. */
    private fun factLine(f: PlaceFactPick, labels: PlanTextLabels): String {
        val text = f.text?.takeIf(String::isNotBlank) ?: return ""
        val head = listOfNotNull(f.place?.takeIf(String::isNotBlank), text).joinToString(": ")
        val c = f.citation
        return if (f.isCrawler && c != null) {
            head + (c.name?.takeIf(String::isNotBlank)?.let { "\n    " + labels.source(it) } ?: "") + link(c.url) +
                (c.attribution?.takeIf(String::isNotBlank)?.let { "\n    $it" } ?: "")
        } else if (!f.isCrawler && labels.aiFact.isNotBlank()) {
            head + "\n    " + labels.aiFact
        } else {
            head
        }
    }

    private fun named(name: String?, price: Double?): String =
        listOfNotNull(name?.takeIf(String::isNotBlank), price?.takeIf { it > 0 }?.let(::formatPrice)).joinToString(", ")

    private fun link(url: String?): String = safeUrl(url)?.let { "\n    $it" } ?: ""

    private fun flightLine(f: Flight, estimate: String): String {
        val leg = f.outbound
        val route =
            if (leg?.origin != null && leg.destination != null) {
                "${leg.origin} → ${leg.destination}"
            } else {
                listOfNotNull(f.legFrom, f.legTo).joinToString(" → ") { it.split(",")[0] }
            }
        return listOfNotNull(
            listOfNotNull(leg?.airline, leg?.flightNumber).joinToString(" ").ifEmpty { null },
            route.ifEmpty { null },
            listOfNotNull(leg?.departureDate ?: f.legDate, leg?.departureTime).joinToString(" ").ifEmpty { null },
            f.priceUsd?.takeIf { it > 0 }?.let { formatPrice(it) + if (f.isEstimate) " ($estimate)" else "" },
        ).joinToString(" · ")
    }
}
