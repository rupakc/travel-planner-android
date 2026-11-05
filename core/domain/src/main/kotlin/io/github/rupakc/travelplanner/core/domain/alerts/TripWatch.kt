package io.github.rupakc.travelplanner.core.domain.alerts

import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

// Port of frontend/src/utils/tripWatch.js. A trip watch's parts come from the server:
// subject.components (frozen at creation) and, per check, last_observation.breakdown.

/** Stacked-bar groups, in order. */
enum class TripGroup(val key: String) {
    FLIGHTS("flights"),
    STAY("stay"),
    ACTIVITIES("activities"),
    OTHER("other"),
    ;

    companion object {
        fun of(key: String?): TripGroup = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

/** One part of a watched trip (`{id, type, group, label, detail?, live, price, currency, in_total, status, delta?}`). */
data class TripRow(
    val id: String,
    val type: String?,
    val group: String?,
    val label: String,
    val detail: String?,
    val live: Boolean,
    val price: Double?,
    val currency: String,
    val inTotal: Boolean,
    val status: String?,
    val delta: Double?,
) {
    val countsInTotal: Boolean get() = inTotal && price != null

    companion object {
        fun from(obj: JsonObject, index: Int): TripRow =
            TripRow(
                id = obj.string("id") ?: index.toString(),
                type = obj.string("type"),
                group = obj.string("group"),
                label = obj.string("label").orEmpty(),
                detail = obj.string("detail"),
                live = obj.bool("live") == true,
                price = obj.double("price"),
                currency = obj.string("currency") ?: "USD",
                inTotal = obj.bool("in_total") != false,
                status = obj.string("status"),
                delta = obj.double("delta"),
            )
    }
}

private fun JsonObject?.rows(key: String): List<TripRow> =
    (this?.get(key) as? JsonArray).orEmpty().mapIndexedNotNull { i, e -> (e as? JsonObject)?.let { TripRow.from(it, i) } }

/** The watch's parts as of its latest look (creation if none). */
fun tripRows(watch: PriceWatch): List<TripRow> = watch.lastObservation.rows("breakdown").ifEmpty { watch.subject.rows("components") }

/** One stacked-bar group with its share of the total. */
data class GroupTotal(val group: TripGroup, val amount: Double, val share: Double)

fun groupTotals(rows: List<TripRow>): List<GroupTotal> {
    val sums = TripGroup.entries.associateWith { 0.0 }.toMutableMap()
    rows.filter { it.countsInTotal }.forEach { r -> TripGroup.of(r.group).let { g -> sums[g] = sums.getValue(g) + r.price!! } }
    val total = sums.values.sum()
    return TripGroup.entries.filter { sums.getValue(it) > 0 }.map { GroupTotal(it, sums.getValue(it), if (total > 0) sums.getValue(it) / total else 0.0) }
}

/** "<1%" for a sliver, else a rounded percent ("42%"). */
fun percent(share: Double): String = if (share > 0 && share < SLIVER) "<1%" else "${jsRound(share * HUNDRED)}%"

/** What kind of price a part is and how fresh (web `rowBadge`). */
enum class RowBadge(val tone: Tone) {
    ESTIMATE(Tone.NEUTRAL),
    UNAVAILABLE(Tone.WARN),
    NOT_CHECKED(Tone.WARN),
    LIVE_EARLIER(Tone.INFO),
    LIVE(Tone.GOOD),
}

fun rowBadge(row: TripRow): RowBadge =
    when {
        !row.live -> RowBadge.ESTIMATE
        row.status == "unavailable" -> RowBadge.UNAVAILABLE
        row.status == "error" -> RowBadge.NOT_CHECKED
        row.status == "stale" -> RowBadge.LIVE_EARLIER
        else -> RowBadge.LIVE
    }

/** One group's move in the last check ("flights −$70"). */
data class GroupMove(val group: TripGroup, val delta: Double)

/** The latest check in parts: which live groups moved, and which live parts couldn't be priced. */
data class TripChange(val moves: List<GroupMove>, val missing: List<String>)

/** Null before the first re-check (web `whatChanged`). */
fun whatChanged(watch: PriceWatch): TripChange? {
    val rows = watch.lastObservation.rows("breakdown")
    if ((watch.lastObservation?.get("breakdown") as? JsonArray) == null || (watch.checkCount ?: 0) < 2) return null
    val byGroup = linkedMapOf<TripGroup, Double>()
    rows.filter { it.live && it.status == "ok" && it.delta != null && it.countsInTotal }.forEach { r ->
        val g = TripGroup.of(r.group)
        byGroup[g] = (byGroup[g] ?: 0.0) + r.delta!!
    }
    val moves = byGroup.filter { jsRound(it.value) != 0L }.map { GroupMove(it.key, it.value) }.sortedBy { it.delta }
    val missing = rows.filter { it.live && (it.status == "unavailable" || it.status == "error") }.map { it.label }
    return TripChange(moves, missing)
}

fun liveCount(rows: List<TripRow>): Int = rows.count { it.live }

fun estimateCount(rows: List<TripRow>): Int = rows.count { !it.live }

/** A saved plan can be watched when it has an id and hasn't departed. */
fun isWatchablePlan(plan: Plan?, today: LocalDate): Boolean {
    val dep = parseIsoDate(plan?.searchRequest?.departureDate) ?: return false
    return dep.isAfter(today)
}

/** A fresh search for the watched trip from its live flight's route; null without one (web `tripReplanFare`). */
fun tripReplanRequest(watch: PriceWatch, preferences: Preferences?): TravelSearchRequest? {
    val components = (watch.subject["components"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val subject = components.firstOrNull { it.string("type") == "flight" && it.obj("subject").string("origin_label") != null }?.obj("subject") ?: return null
    val origin = subject.string("origin_label")
    val destination = subject.string("destination_label") ?: subject.string("destination_code")
    val departure = subject.string("departure_date")
    if (origin == null || destination == null || departure == null) return null
    return replanFrom(origin, destination, departure, subject.string("return_date"), subject.int("num_travelers") ?: 1, preferences)
}

/** One line of "Your plan, as we'll count it". [note] says how it is counted. */
data class TripPart(val key: Key, val amount: Double, val note: Note?, val travellers: Int = 1) {
    enum class Key { FLIGHTS, HOTEL, ACTIVITIES, SIM }

    enum class Note { CHEAPEST_FARE, PER_PERSON }
}

/** The plan's parts as a trip watch counts them before watching (web `tripPreviewParts`). */
fun tripPreviewParts(selections: PlanSelections, request: TravelSearchRequest?): List<TripPart> {
    val n = travellersFrom(request)
    val dep = parseIsoDate(request?.departureDate)
    val ret = parseIsoDate(request?.returnDate)
    val nights = if (dep != null && ret != null) maxOf(1, ChronoUnit.DAYS.between(dep, ret).toInt()) else DEFAULT_NIGHTS

    fun pos(v: Double?) = v?.takeIf { it.isFinite() && it > 0 } ?: 0.0
    val legs = selections.flights
    val hotel = selections.hotel
    val parts =
        listOf(
            TripPart(
                TripPart.Key.FLIGHTS,
                pos(selections.flight?.priceUsd) + legs.sumOf { pos(it.priceUsd) },
                TripPart.Note.CHEAPEST_FARE.takeIf { selections.flight != null && legs.isEmpty() },
            ),
            TripPart(
                TripPart.Key.HOTEL,
                if (hotel == null) 0.0 else pos(hotel.totalPriceUsd).takeIf { it > 0 } ?: (pos(hotel.pricePerNightUsd) * nights),
                null,
            ),
            TripPart(TripPart.Key.ACTIVITIES, selections.activities.sumOf { pos(it.priceUsd) * n }, TripPart.Note.PER_PERSON, n),
            TripPart(TripPart.Key.SIM, pos(selections.sim?.priceUsd), null),
        )
    return parts.filter { it.amount > 0 }
}

/** Why the watched total isn't the My Plan total. */
enum class DiffReason { CHEAPEST_FARE, LIVE_HOTEL, ACTIVITIES_PER_PERSON, LIVE_ACTIVITIES }

data class TotalDifference(val watched: Double, val plan: Double, val delta: Double, val reasons: List<DiffReason>, val travellers: Int)

/** Null when they agree (within $1) or there's nothing to compare (web `totalDifference`). */
fun totalDifference(watch: PriceWatch, planTotal: Double?): TotalDifference? {
    val watched = watch.baselinePrice ?: return null
    if (planTotal == null || planTotal <= 0) return null
    val delta = (watched - planTotal).roundCents()
    if (abs(delta) < 1) return null
    val rows = watch.subject.rows("components")
    val n = watch.subject.int("travellers") ?: 1
    val reasons = mutableListOf<DiffReason>()
    if (rows.any { it.type == "flight" && it.live }) reasons += DiffReason.CHEAPEST_FARE
    if (rows.any { it.type == "hotel" && it.live }) reasons += DiffReason.LIVE_HOTEL
    if (n > 1 && rows.any { it.type == "activity" }) {
        reasons += DiffReason.ACTIVITIES_PER_PERSON
    } else if (rows.any { it.type == "activity" && it.live }) {
        reasons += DiffReason.LIVE_ACTIVITIES
    }
    return TotalDifference(watched, planTotal, delta, reasons, n)
}

private const val SLIVER = 0.005
private const val HUNDRED = 100.0
private const val DEFAULT_NIGHTS = 7
