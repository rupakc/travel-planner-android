package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Travel from the stop before (web `transit_from_prev`). [km] is rounded to 0.1 (street estimate). */
data class Transit(val minutes: Int, val mode: Mode, val km: Double) {
    enum class Mode(val wire: String) { WALK("walk"), TRANSIT("transit") }
}

/**
 * One slot of a scheduled day. [timed] slots carry derived [startTime]/[endTime] ("HH:MM", local, no zone) and
 * day offsets (whole days past the trip day). Untimed slots keep whatever times the data had.
 */
data class ScheduledSlot(
    val slot: ItinerarySlot,
    val timed: Boolean = false,
    val startTime: String? = slot.startTime,
    val endTime: String? = slot.endTime,
    val startDayOffset: Int? = null,
    val endDayOffset: Int? = null,
    val transitFromPrev: Transit? = null,
) {
    val key: String? get() = slot.key
}

/** A scheduled day: the arranged [day] (unchanged fields) with timed [slots] in listed order. */
data class ScheduledDay(val day: ItineraryDay, val slots: List<ScheduledSlot>, val warnings: List<ScheduleWarning>, val scheduled: Boolean)

/** Web `schedule_warnings`, typed so the UI can translate them; [english] is the web's exact text (sent to /stress-test). */
sealed interface ScheduleWarning {
    val english: String

    /** [activity] null → the web's "An activity". */
    data class LateStart(val activity: String?, val time: String) : ScheduleWarning {
        override val english: String get() = "${activity ?: "An activity"} starts at $time — later than planned"
    }

    data class RunsLate(val dayNumber: Int?) : ScheduleWarning {
        override val english: String get() = "Day ${dayNumber ?: "undefined"} runs past 23:00"
    }
}

/**
 * Port of the web's `utils/schedule.js`: estimated clock times for an arranged itinerary. Times are
 * derived from each slot's position, its own duration and the distance from the stop before it,
 * so they are never stored (except on picked plan slots, see [pickedTimes]).
 */
object ItinerarySchedule {
    private val ANCHORS = mapOf("morning" to 9 * 60, "afternoon" to 13 * 60 + 30, "evening" to 18 * 60 + 30)
    private val DEFAULT_MINUTES = mapOf("morning" to 180, "afternoon" to 180, "evening" to 150)
    private const val OTHER_DEFAULT_MINUTES = 120
    private const val MIN_HOURS = 0.25
    private const val MAX_HOURS = 12.0
    private const val DETOUR = 1.3
    private const val WALK_MAX_KM = 1.5
    private const val WALK_KMH = 4.5
    private const val TRANSIT_KMH = 20.0
    private const val TRANSIT_OVERHEAD_MINUTES = 10
    private const val UNKNOWN_TRAVEL_MINUTES = 20
    private const val LATE_MINUTES = 45
    private const val DAY_END = 23 * 60
    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 1440
    private const val HOURS_PER_DAY = 24
    private const val MAX_LAT = 90.0
    private const val MAX_LNG = 180.0
    private const val ROUND_STEP = 5
    private const val KM_TENTHS = 10.0
    private const val MIN_KM_LABEL = 0.1
    private const val HALF = 0.5

    /** Times every slot of arranged [days]. [edits] only rename activities in warnings. */
    fun scheduleDays(days: List<ItineraryDay>, edits: Map<String, SlotEdit> = emptyMap()): List<ScheduledDay> = days.map { scheduleDay(it, edits) }

    /** Indexes of a day's slots in timing order (morning → afternoon → evening; ties keep listed order). */
    fun scheduleOrder(day: ItineraryDay): List<Int> {
        val ranks = ItineraryLayouts.rankList(day)
        return ranks.indices.sortedBy { ranks[it] }
    }

    /** True when a day's slots are listed in the order they are timed (each transit is from the row above). */
    fun listedInScheduleOrder(day: ItineraryDay): Boolean {
        val ranks = ItineraryLayouts.rankList(day)
        return ranks.indices.all { it == 0 || ranks[it - 1] <= ranks[it] }
    }

    /** "09:00–12:00" for a timed slot, else "". */
    fun timeRange(startTime: String?, endTime: String?): String = if (!startTime.isNullOrEmpty() && !endTime.isNullOrEmpty()) "$startTime–$endTime" else ""

    /** The web's English label ("🚶 12 min walk · 0.9 km" / "🚇 ~25 min by transit"); the app UI uses string resources. */
    fun transitLabel(transit: Transit?): String {
        if (transit == null) return ""
        if (transit.mode != Transit.Mode.WALK) return "🚇 ~${transit.minutes} min by transit"
        return if (transit.km >= MIN_KM_LABEL) "🚶 ${transit.minutes} min walk · ${formatKm(transit.km)} km" else "🚶 ${transit.minutes} min walk"
    }

    /** Km like JS number printing: 4.0 → "4", 0.8 → "0.8". */
    fun formatKm(km: Double): String = if (km == floor(km)) km.toLong().toString() else km.toString()

    /**
     * The timing to store with a picked itinerary entry (`start_time`, `end_time`, day offsets and
     * `duration_hours` when the slot has one); empty when untimed.
     */
    fun pickedTimes(slot: ScheduledSlot): JsonObject {
        if (slot.startTime.isNullOrEmpty() || slot.endTime.isNullOrEmpty()) return JsonObject(emptyMap())
        return buildJsonObject {
            put("start_time", JsonPrimitive(slot.startTime))
            put("end_time", JsonPrimitive(slot.endTime))
            slot.startDayOffset?.let { put("start_day_offset", JsonPrimitive(it)) }
            slot.endDayOffset?.let { put("end_day_offset", JsonPrimitive(it)) }
            val raw = slot.slot.raw
            when {
                raw.containsKey("duration_hours") -> put("duration_hours", raw.getValue("duration_hours"))
                slot.slot.durationHours != null -> put("duration_hours", JsonPrimitive(slot.slot.durationHours))
            }
        }
    }

    /**
     * Refreshes the stored times of picked entries (by key) from [scheduledDays]. Entries with no timed
     * counterpart are kept; the same list comes back when nothing changed.
     */
    fun syncPlannedSlotTimes(itinerarySlots: List<ItinerarySlot>, scheduledDays: List<ScheduledDay>): List<ItinerarySlot> {
        val timed = mutableMapOf<String?, JsonObject>()
        for (day in scheduledDays) {
            for (slot in day.slots) {
                val times = pickedTimes(slot)
                if (times.containsKey("start_time")) timed[slot.key] = times
            }
        }
        var changed = false
        val out =
            itinerarySlots.map { entry ->
                val times = timed[entry.key] ?: return@map entry
                val base = entry.baseJson()
                if (times.all { (field, value) -> sameValue(base[field], value) }) return@map entry
                changed = true
                entry.patched(times)
            }
        return if (changed) out else itinerarySlots
    }

    /** "Day 2 · Afternoon · 13:30–16:00" (web English); times left out when absent. */
    fun slotWhen(slot: ItinerarySlot): String {
        val tod = slot.timeOfDay?.replaceFirstChar { it.uppercaseChar() }.orEmpty()
        return listOf("Day ${slot.dayNumber ?: "undefined"}", tod, timeRange(slot.startTime, slot.endTime)).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    // ---- internals ---------------------------------------------------------------------------

    private fun sameValue(a: JsonElement?, b: JsonElement): Boolean {
        if (a == b) return true
        val x = (a as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toDoubleOrNull()
        val y = (b as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toDoubleOrNull()
        return x != null && x == y
    }

    private fun pad(n: Int) = n.toString().padStart(2, '0')

    private fun clock(minutes: Int) = "${pad((minutes / MINUTES_PER_HOUR) % HOURS_PER_DAY)}:${pad(minutes % MINUTES_PER_HOUR)}"

    private fun roundUpTo5(minutes: Double) = max(ROUND_STEP, (ceil(minutes / ROUND_STEP) * ROUND_STEP).toInt())

    private fun coords(slot: ItinerarySlot): Pair<Double, Double>? {
        val lat = slot.lat?.takeIf { it.isFinite() } ?: return null
        val lng = slot.lng?.takeIf { it.isFinite() } ?: return null
        if (abs(lat) > MAX_LAT || abs(lng) > MAX_LNG) return null
        return lat to lng
    }

    private fun durationMinutes(slot: ItinerarySlot): Int {
        val hours = durationHours(slot)?.takeIf { it.isFinite() }
        if (hours != null && hours in MIN_HOURS..MAX_HOURS) return floor(hours * MINUTES_PER_HOUR + HALF).toInt()
        return DEFAULT_MINUTES[slot.timeOfDay] ?: OTHER_DEFAULT_MINUTES
    }

    /**
     * Web parity: only a JSON number counts (`typeof n === 'number'`); a string such as "2" falls back to the
     * default even though lenient decoding would read it. Typed-only slots (no raw JSON) use the typed value.
     */
    private fun durationHours(slot: ItinerarySlot): Double? {
        if (slot.raw.isEmpty()) return slot.durationHours
        val v = slot.raw["duration_hours"] as? JsonPrimitive ?: return null
        return if (v.isString) null else v.content.toDoubleOrNull()
    }

    private fun transitBetween(prev: ItinerarySlot, slot: ItinerarySlot): Transit? {
        val a = coords(prev) ?: return null
        val b = coords(slot) ?: return null
        val km = haversineKm(a.first, a.second, b.first, b.second) * DETOUR
        val walk = km <= WALK_MAX_KM
        val minutes = if (walk) km / WALK_KMH * MINUTES_PER_HOUR else km / TRANSIT_KMH * MINUTES_PER_HOUR + TRANSIT_OVERHEAD_MINUTES
        return Transit(roundUpTo5(minutes), if (walk) Transit.Mode.WALK else Transit.Mode.TRANSIT, floor(km * KM_TENTHS + HALF) / KM_TENTHS)
    }

    private fun scheduleDay(day: ItineraryDay, edits: Map<String, SlotEdit>): ScheduledDay {
        val slots = day.slots
        if (slots.none { it.timeOfDay in ItineraryLayouts.TIMES }) {
            return ScheduledDay(day, slots.map { ScheduledSlot(it) }, emptyList(), scheduled = false)
        }
        val ranks = ItineraryLayouts.rankList(day)
        val out = slots.map { ScheduledSlot(it) }.toMutableList()
        val warnings = mutableListOf<ScheduleWarning>()
        var prev: Pair<ItinerarySlot, Int>? = null
        for (i in scheduleOrder(day)) {
            val slot = slots[i]
            val rank = ranks[i]
            val anchor = if (rank == floor(rank) && rank >= 0) ANCHORS[ItineraryLayouts.TIMES[rank.toInt()]] else null
            if (anchor == null && prev == null) continue
            val transit = prev?.let { transitBetween(it.first, slot) }
            val earliest = prev?.let { it.second + (transit?.minutes ?: UNKNOWN_TRAVEL_MINUTES) } ?: 0
            val start = max(anchor ?: 0, earliest)
            val end = start + durationMinutes(slot)
            out[i] =
                ScheduledSlot(
                    slot = slot,
                    timed = true,
                    startTime = clock(start),
                    endTime = clock(end),
                    startDayOffset = start / MINUTES_PER_DAY,
                    endDayOffset = end / MINUTES_PER_DAY,
                    transitFromPrev = transit,
                )
            if (anchor != null && start - anchor > LATE_MINUTES) {
                warnings += ScheduleWarning.LateStart((edits[slot.key]?.activityOr(slot.activity) ?: slot.activity)?.takeIf { it.isNotEmpty() }, clock(start))
            }
            prev = slot to end
        }
        if (prev != null && prev.second > DAY_END) warnings += ScheduleWarning.RunsLate(day.dayNumber)
        return ScheduledDay(day, out, warnings, scheduled = true)
    }
}
