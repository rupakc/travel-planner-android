package io.github.rupakc.travelplanner.core.domain.export

import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionsSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What a plan export is built from (web `buildPlanIcs({planName, searchData, selections, itinerary, planId, now})`).
 *
 * @property selections the `selections` JSON exactly as saved (use [PlanSelectionsSerializer.toJson])
 * @property itinerary the live itinerary when the plan comes straight from a search; saved plans carry a snapshot
 */
data class PlanExportInput(
    val planName: String?,
    val searchData: JsonObject?,
    val selections: JsonObject?,
    val itinerary: JsonObject? = null,
    val planId: Long? = null,
    val now: Instant,
) {
    companion object {
        /** Input for a saved [plan] (its snapshot itinerary, its id for stable UIDs). */
        fun of(plan: Plan, now: Instant): PlanExportInput =
            PlanExportInput(plan.name, plan.searchData, PlanSelectionsSerializer.toJson(plan.selections), null, plan.id, now)
    }
}

/**
 * One calendar entry of a plan. Times are "floating" local wall-clock times: a 09:00 museum
 * visit in Tokyo reads 09:00 wherever the calendar is opened. For [allDay] events [start] and
 * [end] are midnights and [end] is exclusive (RFC 5545).
 */
data class PlanCalendarEvent(
    val id: String,
    val summary: String?,
    val location: String?,
    val description: String?,
    val allDay: Boolean,
    val start: LocalDateTime,
    val end: LocalDateTime?,
)

/**
 * Port of the web's `frontend/src/utils/planExport.js`: an iCalendar (.ics, RFC 5545) file of
 * the trip built from the same selections My Plan shows (flights, hotel stay, local events and
 * the arranged, scheduled itinerary), with escaping, 75-octet folding and stable UIDs.
 */
object PlanIcs {
    private val SLOT_HOURS = mapOf("morning" to (9 to 12), "afternoon" to (13 to 17), "evening" to (18 to 21))
    private val SLOT_ORDER = listOf("morning", "afternoon", "evening")
    private const val MINUTES_PER_DAY = 1440
    private const val MAX_OCTETS = 75
    private const val HOUR = 60
    private const val NOON = 12
    private const val AM = "am"
    private const val MERIDIEM_GROUP = 3
    private const val PM = "pm"
    private const val LAST_HOUR = 23
    private const val LAST_MINUTE = 59
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val CLOCK = Regex("^(\\d{2}):(\\d{2})$")
    private val TIME = Regex("^\\s*(\\d{1,2}):(\\d{2})\\s*(am|pm)?", RegexOption.IGNORE_CASE)
    private val CONTROL = Regex("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]")
    private val NEWLINE = Regex("\r\n|\r|\n")
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd", java.util.Locale.ROOT)
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", java.util.Locale.ROOT)
    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.ROOT).withZone(ZoneOffset.UTC)

    /** Escape a TEXT value (RFC 5545 §3.3.11). */
    fun escape(text: String): String =
        text.replace(CONTROL, "")
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(NEWLINE, "\\\\n")

    /** Fold a content line to at most 75 octets per line (RFC 5545 §3.1); never splits a character. */
    fun fold(line: String): String {
        if (line.toByteArray(Charsets.UTF_8).size <= MAX_OCTETS) return line
        val out = mutableListOf<String>()
        val current = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val ch = String(Character.toChars(cp))
            val size = ch.toByteArray(Charsets.UTF_8).size
            // Continuation lines start with a space, which counts toward the 75
            val limit = if (out.isEmpty()) MAX_OCTETS else MAX_OCTETS - 1
            if (bytes + size > limit) {
                out += current.toString()
                current.clear()
                bytes = 0
            }
            current.append(ch)
            bytes += size
            i += Character.charCount(cp)
        }
        out += current.toString()
        return out.joinToString("\r\n ")
    }

    /**
     * The plan's day-by-day itinerary: the live [itinerary] if given, else the snapshot saved with
     * the plan — arranged by the user's layout and given estimated times — else the individually
     * selected slots grouped by day (which keep their stored times, if any).
     */
    fun planItineraryDays(selections: JsonObject?, itinerary: JsonObject?): List<JsonObject> {
        val edits = selections?.get("itinerary_edits").obj()
        val layout = selections?.get("itinerary_layout").obj()

        fun arranged(source: JsonObject) = ItineraryArrangement.scheduleDays(ItineraryArrangement.arrange(source, layout), edits)
        if (itinerary?.get("days").array().isNotEmpty()) return arranged(itinerary!!)
        val snapshot = selections?.get("itinerary").obj()
        if (snapshot?.get("days").array().isNotEmpty()) return arranged(snapshot!!)
        val byDay = LinkedHashMap<String, Pair<JsonObject, MutableList<JsonObject>>>()
        for (slot in selections?.get("itinerary_slots").objects()) {
            val day = slot["day_number"]
            byDay.getOrPut(day.js()) {
                JsonObject(mapOf("day_number" to (day ?: JsonPrimitive(null as String?)), "date" to (slot["date"] ?: JsonPrimitive(null as String?)))) to
                    mutableListOf()
            }.second += slot
        }
        return byDay.values
            .sortedBy { it.first["day_number"].looseNumber() ?: Double.NaN }
            .map { (day, slots) ->
                JsonObject(day + ("slots" to JsonArray(slots.sortedBy { SLOT_ORDER.indexOf(it["time_of_day"].str()) })))
            }
    }

    /** Every calendar entry of the plan, in file order (also used for per-event "Add to calendar"). */
    fun events(input: PlanExportInput): List<PlanCalendarEvent> {
        val sel = input.selections
        val sd = input.searchData
        return flightEvents(sel, sd) +
            listOfNotNull(hotelEvent(sel?.get("hotel").obj(), sd)) +
            localEvents(sel?.get("events")) +
            itineraryEvents(planItineraryDays(sel, input.itinerary), sel, sd)
    }

    /** The .ics text for a plan (CRLF line endings, folded, escaped). */
    fun build(input: PlanExportInput): String {
        val stamp = STAMP.format(input.now)
        val base = uidBase(input.planId, input.planName, input.searchData)
        val lines =
            mutableListOf(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Voyager Travel Planner//Plan Export//EN",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "X-WR-CALNAME:${escape(input.planName?.takeIf(String::isNotEmpty) ?: "Trip plan")}",
            )
        val source = input.searchData?.get("import_source").obj()
        if (source != null && (source["title"].truthy() || source["url"].truthy())) {
            val url = source["url"].text()?.takeIf { Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            val credit = listOfNotNull(source["title"].text(), url).joinToString(" — ")
            lines += "X-WR-CALDESC:${escape("Inspired by $credit")}"
        }
        for (ev in events(input)) {
            val dateParam = if (ev.allDay) ";VALUE=DATE" else ""
            lines += "BEGIN:VEVENT"
            lines += "UID:$base-${ev.id}@voyager-travel-planner"
            lines += "DTSTAMP:$stamp"
            lines += "DTSTART$dateParam:${ev.start.ics(ev.allDay)}"
            ev.end?.let { lines += "DTEND$dateParam:${it.ics(ev.allDay)}" }
            lines += "SUMMARY:${escape(ev.summary?.takeIf(String::isNotEmpty) ?: "Trip")}"
            ev.location?.takeIf(String::isNotEmpty)?.let { lines += "LOCATION:${escape(it)}" }
            ev.description?.takeIf(String::isNotEmpty)?.let { lines += "DESCRIPTION:${escape(it)}" }
            lines += "END:VEVENT"
        }
        lines += "END:VCALENDAR"
        return lines.joinToString("\r\n") { fold(it) } + "\r\n"
    }

    /** A safe download file name for the plan, e.g. "tokyo-trip.ics". */
    fun fileName(planName: String?, ext: String): String = "${slug(planName)}.$ext"

    // --- internals (same structure as planExport.js) -----------------------------------------

    private fun LocalDateTime.ics(allDay: Boolean): String = if (allDay) DATE.format(this) else DATE_TIME.format(this)

    private fun isIsoDate(value: String?): Boolean = value != null && ISO_DATE.matches(value) && runCatching { LocalDate.parse(value) }.isSuccess

    private fun date(iso: String): LocalDate = LocalDate.parse(iso)

    /** Floating local date-time: minutes past midnight of [iso], may roll over days. */
    private fun at(iso: String, minutes: Int): LocalDateTime = date(iso).atStartOfDay().plusMinutes(minutes.toLong())

    private fun allDay(iso: String): LocalDateTime = date(iso).atStartOfDay()

    private fun clockMinutes(value: String?): Int? =
        value?.let(CLOCK::matchEntire)?.let { it.groupValues[1].toInt() * HOUR + it.groupValues[2].toInt() }

    /** "11:55", "9:05", "11:55 PM" → minutes past midnight, or null. */
    private fun parseTime(value: String?): Int? {
        val m = TIME.find(value ?: return null) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        val meridiem = m.groupValues[MERIDIEM_GROUP].lowercase()
        if (meridiem == PM && h < NOON) h += NOON
        if (meridiem == AM && h == NOON) h = 0
        if (h > LAST_HOUR || min > LAST_MINUTE) return null
        return h * HOUR + min
    }

    private fun shortPlace(place: String?): String = (place ?: "").split(",")[0].trim()

    private fun dayDate(day: JsonObject, searchData: JsonObject?): String? {
        day["date"].str()?.takeIf(::isIsoDate)?.let { return it }
        val departure = searchData?.get("departure_date").str()
        val dayNumber = day["day_number"].looseNumber()
        if (isIsoDate(departure) && dayNumber != null && dayNumber >= 1) {
            return date(departure!!).plusDays(dayNumber.toLong() - 1).toString()
        }
        return null
    }

    private fun itineraryEvents(days: List<JsonObject>, selections: JsonObject?, searchData: JsonObject?): List<PlanCalendarEvent> {
        val events = mutableListOf<PlanCalendarEvent>()
        for (day in days) {
            val date = dayDate(day, searchData) ?: continue
            day["slots"].objects().mapTo(events) { slot -> slotEvent(day, slot, date, selections) }
            val extras = day["link_extras"].objects()
            if (extras.isNotEmpty()) {
                events +=
                    PlanCalendarEvent(
                        id = "extras-${day["day_number"].js()}",
                        summary = "Also from your link: ${extras.joinToString(", ") { it["name"].text().orEmpty() }}",
                        location = null,
                        description = extras.joinToString("\n") { x -> listOfNotNull(x["name"].text(), x["note"].text()).joinToString(" — ") },
                        allDay = true,
                        start = allDay(date),
                        end = allDay(date).plusDays(1),
                    )
            }
        }
        return events
    }

    private fun slotEvent(day: JsonObject, slot: JsonObject, date: String, selections: JsonObject?): PlanCalendarEvent {
        val key = slot["key"].orIfNull { null }?.js() ?: ItineraryArrangement.slotKey(day["day_number"], slot["time_of_day"])
        val edit = selections?.get("itinerary_edits").obj()?.get(key).obj()
        val note = selections?.get("itinerary_notes").obj()?.get(key).text()
        var start = clockMinutes(slot["start_time"].str())
        var end = clockMinutes(slot["end_time"].str())
        if (start != null && end != null) {
            // Scheduled clock times wrap at midnight; the day offsets say which day
            start += dayOffset(slot["start_day_offset"]) * MINUTES_PER_DAY
            end += dayOffset(slot["end_day_offset"]) * MINUTES_PER_DAY
        } else {
            val hours = SLOT_HOURS[slot["time_of_day"].str()] ?: SLOT_HOURS.getValue("morning")
            start = hours.first * HOUR
            val duration = slot["duration_hours"].looseNumber()
            end = if (duration != null && duration > 0) start + (duration * HOUR).roundToLong().toInt() else hours.second * HOUR
        }
        val cost = (edit?.get("cost").orIfNull { slot["estimated_cost_usd"] }).looseNumber()
        val description =
            listOfNotNull(
                day["theme"].text()?.let { "Day ${day["day_number"].js()}: $it" },
                if (slot["from_link"].truthy()) "From your link" + (slot["link_quote"].text()?.let { ": “$it”" } ?: "") else null,
                slot["notes"].text(),
                note,
                cost?.takeIf { it > 0 }?.let { "Est. cost: $${enUs(it)}" },
            ).joinToString("\n")
        return PlanCalendarEvent(
            id = "slot-$key",
            summary = edit?.get("activity").text() ?: slot["activity"].text(),
            location = edit?.get("location").orIfNull { slot["location"].orIfNull { day["city"] } }.text(),
            description = description,
            allDay = false,
            start = at(date, start),
            end = at(date, end),
        )
    }

    private fun dayOffset(n: JsonElement?): Int = n.strictInt()?.takeIf { it > 0 } ?: 0

    private fun flightEvent(
        id: String,
        leg: JsonObject?,
        date: String?,
        from: String? = null,
        to: String? = null,
        bookingUrl: String? = null,
    ): PlanCalendarEvent? {
        if (leg == null || !isIsoDate(date)) return null
        val origin = leg["origin"].text() ?: shortPlace(from)
        val destination = leg["destination"].text() ?: shortPlace(to)
        val route = if (origin.isNotEmpty() && destination.isNotEmpty()) "$origin → $destination" else null
        val summary = listOfNotNull("✈", leg["airline"].text(), leg["flight_number"].text(), route).joinToString(" ")
        // Floating times follow the departure clock, so the arrival — in the destination's clock — is spelled out instead
        val description =
            listOfNotNull(leg["arrival_time"].text()?.let { "Arrives $it local time" }, bookingUrl?.let { "Booking: $it" }).joinToString("\n")
        val departs =
            parseTime(leg["departure_time"].text()) ?: return PlanCalendarEvent(
                id,
                summary,
                null,
                description,
                allDay = true,
                start = allDay(date!!),
                end = allDay(date).plusDays(1),
            )
        val duration = leg["duration_minutes"].looseNumber()
        return PlanCalendarEvent(
            id,
            summary,
            null,
            description,
            allDay = false,
            start = at(date!!, departs),
            end = if (duration != null && duration > 0) at(date, departs + duration.toInt()) else null,
        )
    }

    private fun flightEvents(selections: JsonObject?, searchData: JsonObject?): List<PlanCalendarEvent> {
        val events = mutableListOf<PlanCalendarEvent?>()
        val f = selections?.get("flight").obj()
        if (f != null) {
            val out = f["outbound"].obj() ?: f
            val url = f["booking_url"].text()
            events += flightEvent("flight-out", out, out["departure_date"].text() ?: searchData?.get("departure_date").str(), bookingUrl = url)
            f["return"].obj()?.let { ret ->
                events += flightEvent("flight-return", ret, ret["departure_date"].text() ?: searchData?.get("return_date").str(), bookingUrl = url)
            }
        }
        selections?.get("flights").objects().forEachIndexed { i, leg ->
            val out = leg["outbound"].obj() ?: leg
            events +=
                flightEvent(
                    "flight-leg-${leg["leg_index"].orIfNull { JsonPrimitive(i) }.js()}",
                    out,
                    out["departure_date"].text() ?: leg["leg_date"].text(),
                    from = leg["leg_from"].text(),
                    to = leg["leg_to"].text(),
                    bookingUrl = leg["booking_url"].text(),
                )
        }
        return events.filterNotNull()
    }

    private fun hotelEvent(hotel: JsonObject?, searchData: JsonObject?): PlanCalendarEvent? {
        val checkIn = searchData?.get("departure_date").str()
        if (hotel == null || !isIsoDate(checkIn)) return null
        val name = hotel["name"].text() ?: return null
        val ret = searchData?.get("return_date").str()
        val checkOut = if (isIsoDate(ret) && ret!! > checkIn!!) date(ret) else date(checkIn!!).plusDays(1)
        return PlanCalendarEvent(
            id = "hotel",
            summary = "🏨 $name",
            location = hotel["location"].text(),
            description = hotel["booking_url"].text()?.let { "Booking: $it" },
            allDay = true,
            start = allDay(checkIn),
            end = checkOut.atStartOfDay(),
        )
    }

    private fun localEvents(events: JsonElement?): List<PlanCalendarEvent> =
        events.objects()
            .filter { it["name"].truthy() && isIsoDate(it["start_date"].str()) }
            .map { ev ->
                val first = ev["start_date"].str()!!
                val endDate = ev["end_date"].str()
                val last = if (isIsoDate(endDate) && endDate!! >= first) endDate else first
                PlanCalendarEvent(
                    id = "event-${ItineraryArrangement.fnv("${ev["name"].js()}|$first")}",
                    summary = "🎉 ${ev["name"].js()}",
                    location = ev["location"].text(),
                    description = listOfNotNull(ev["description"].text(), ev["price"].text()?.let { "Price: $it" }).joinToString("\n"),
                    allDay = true,
                    start = allDay(first),
                    // All-day DTEND is exclusive: the day after the last day
                    end = allDay(last).plusDays(1),
                )
            }

    private fun slug(value: String?): String =
        (value?.takeIf(String::isNotEmpty) ?: "trip").lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "trip" }

    // Event IDs must differ between plans yet survive a rename: a saved plan's id is ideal;
    // an unsaved plan falls back to its route, dates and name.
    private fun uidBase(planId: Long?, planName: String?, searchData: JsonObject?): String {
        if (planId != null) return "plan-$planId"
        val trip =
            listOf("origin", "destination", "departure_date", "return_date").map { key ->
                searchData?.get(key).let { if (it == null || it == kotlinx.serialization.json.JsonNull) "" else it.js() }
            } + (planName ?: "")
        return "trip-${slug(planName)}-${ItineraryArrangement.fnv(trip.joinToString("|"))}"
    }
}
