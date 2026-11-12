package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.model.trips.TripsResponse
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Local copies of the server's trip rules (Trips-API "Phases", `GET /trips` order and `pinned_id`), used to
 * show a cached list offline: the phase, `days_to_start` and `day_index` follow today's device date.
 */
object TripsOffline {
    /** The pinned "memories" trip must have ended within this many days. */
    const val RECAP_PIN_DAYS = 14L

    private val phaseRank = listOf(Phase.ON_THE_ROAD, Phase.READY, Phase.PLANNING, Phase.DREAMING, Phase.MEMORIES)

    private fun date(value: String?): LocalDate? =
        try {
            value?.let(LocalDate::parse)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun rank(phase: Phase?): Int = phaseRank.indexOf(phase).let { if (it < 0) phaseRank.size else it }

    /** [trip] with phase, `days_to_start` and `day_index` recomputed for [today] (a manual phase override is kept). */
    fun recompute(trip: Trip, today: LocalDate): Trip {
        val start = date(trip.startDate)
        val end = date(trip.endDate)
        val override = if (trip.phaseSource == "manual") trip.phase?.let { TripPhase.fromWire(it.wire) } else null
        val phase = TripPhaseRules.effective(start, end, override, today, trip.prep.complete)
        return trip.copy(
            phase = Phase.fromWire(phase.wire),
            daysToStart = TripPhaseRules.daysToStart(start, today),
            dayIndex = TripPhaseRules.dayIndex(start, end, today),
        )
    }

    /** Served order: phase (on the road, ready, planning, dreaming, memories), start date (memories newest first), `updated_at` newest first. */
    fun sort(trips: List<Trip>): List<Trip> {
        val byStart =
            Comparator<Trip> { a, b ->
                val sa = a.startDate
                val sb = b.startDate
                when {
                    sa == null && sb == null -> 0
                    sa == null -> 1
                    sb == null -> -1
                    a.phase == Phase.MEMORIES -> sb.compareTo(sa)
                    else -> sa.compareTo(sb)
                }
            }
        return trips.sortedWith(
            compareBy<Trip> { rank(it.phase) }.then(byStart).thenByDescending { it.updatedAt.orEmpty() },
        )
    }

    /** The "Next up" trip: on the road (earliest start), else soonest upcoming, else a recently ended trip without a recap. */
    fun pinnedId(trips: List<Trip>, today: LocalDate): Long? {
        trips.filter { it.phase == Phase.ON_THE_ROAD }.minByOrNull { it.startDate.orEmpty() }?.let { return it.id }
        trips.mapNotNull { t -> date(t.startDate)?.let { s -> if (today.isBefore(s)) t to ChronoUnit.DAYS.between(today, s) else null } }
            .minByOrNull { it.second }
            ?.let { return it.first.id }
        return trips.firstOrNull { t ->
            val end = date(t.endDate ?: t.startDate)
            t.phase == Phase.MEMORIES && t.recapCompletedAt == null && end != null && ChronoUnit.DAYS.between(end, today) in 0..RECAP_PIN_DAYS
        }?.id
    }

    /** The whole list recomputed for [today]: phases, order, pin and the `today` echo. */
    fun recompute(response: TripsResponse, today: LocalDate): TripsResponse {
        val trips = sort(response.trips.map { recompute(it, today) })
        return response.copy(trips = trips, pinnedId = pinnedId(trips, today), today = today.toString())
    }
}
