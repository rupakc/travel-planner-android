package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.model.plan.ItineraryLayout
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** The itinerary as the user arranged it (web `arrangeItinerary` result). Slots carry their original [ItinerarySlot.key]. */
data class ArrangedItinerary(
    val days: List<ItineraryDay>,
    val totalEstimatedCostUsd: Double?,
    val removed: List<RemovedSlot>,
    val changed: Boolean,
)

/** An activity the user removed, keyed by its original slot key. */
data class RemovedSlot(val key: String, val slot: ItinerarySlot)

/** Where a slot may move; [swapWith] is the activity there (it takes the moved slot's place). */
data class MoveTarget(val dayNumber: Int?, val timeOfDay: String, val swapWith: SwapWith?)

data class SwapWith(val key: String, val activity: String?)

/**
 * Port of the web's `utils/itineraryLayout.js`: a layout overlay keyed by each activity's
 * ORIGINAL slot key (`"<day_number>-<time_of_day>"`). Edits, notes and plan picks stay on
 * that key, so moving an activity never re-keys anything. Layout shape (stored in
 * `selections.itinerary_layout`, identical to the web): `{fingerprint, positions: {originalKey: "<day>-<tod>" | null}}`;
 * null = removed, an absent key stays where it was. Pure and deterministic.
 */
object ItineraryLayouts {
    val TIMES: List<String> = listOf("morning", "afternoon", "evening")

    /** `"<day>-<tod>"`; a missing day number reads "undefined" like the web's template string. */
    fun slotKey(dayNumber: Int?, timeOfDay: String?): String = "${dayNumber ?: UNDEFINED}-${timeOfDay ?: UNDEFINED}"

    /** FNV-1a over `"<day>|<tod>|<activity>"` lines, base 36 — the exact web value, so layouts saved on either side apply on both. */
    fun fingerprint(itinerary: ItineraryData): String {
        val lines = mutableListOf<String>()
        for (day in itinerary.days) {
            for (slot in day.slots) {
                lines +=
                    "${day.dayNumber ?: UNDEFINED}|${jsText(slot, "time_of_day", slot.timeOfDay)}|${jsText(slot, "activity", slot.activity)}"
            }
        }
        return fnv1a(lines.joinToString("\n"))
    }

    /** The layout when it belongs to this itinerary (same fingerprint), else null. */
    fun activeLayout(itinerary: ItineraryData, layout: ItineraryLayout?): ItineraryLayout? =
        layout?.takeIf { it.fingerprint == fingerprint(itinerary) }

    /**
     * Sort rank of every slot of an ORIGINAL day, keyed like [dayKeys]: a movable slot ranks at its
     * time's index; any other slot just after the movable slot preceding it (-0.5 when none).
     */
    fun slotRanks(day: ItineraryDay): Map<String, Double> = dayKeys(day).zip(rankList(day)).toMap()

    internal fun rankList(day: ItineraryDay): List<Double> {
        var rank = -1.0
        val keys = dayKeys(day)
        return day.slots.mapIndexed { i, slot ->
            if (isMovableSlot(day, slot, keys[i])) {
                rank = TIMES.indexOf(slot.timeOfDay).toDouble()
                rank
            } else {
                rank + HALF
            }
        }
    }

    /**
     * City rule: two days are compatible when their place-name sets share a name, or either day has no city
     * ("Montserrat & Barcelona, Spain" → {montserrat, barcelona}).
     */
    fun citiesCompatible(a: String?, b: String?): Boolean {
        val left = placeNames(a)
        val right = placeNames(b)
        if (left.isEmpty() || right.isEmpty()) return true
        return left.any { it in right }
    }

    /** The itinerary arranged by [layout] (ignored unless active) with [edits] applied to recomputed costs. */
    fun arrange(itinerary: ItineraryData, layout: ItineraryLayout?, edits: Map<String, SlotEdit> = emptyMap()): ArrangedItinerary {
        val active = activeLayout(itinerary, layout) ?: return plainArrangement(itinerary)
        val model = buildModel(itinerary)
        val resolved = resolve(model, active)

        val changedDays = linkedSetOf<Int?>()
        val removed = mutableListOf<RemovedSlot>()
        for ((key, at) in resolved) {
            if (at == key) continue
            changedDays += model.posDay.getValue(key).dayNumber
            if (at == null) {
                removed += RemovedSlot(key, model.slotByKey.getValue(key).copy(key = key))
            } else {
                changedDays += model.posDay.getValue(at).dayNumber
            }
        }
        if (changedDays.isEmpty()) return plainArrangement(itinerary)

        val placed = resolved.entries.filter { it.value != null }.associate { it.value!! to it.key }
        val days =
            itinerary.days.map { day ->
                if (day.dayNumber !in changedDays) return@map day.copy(slots = withKeys(day))
                val entries = mutableListOf<Pair<Double, ItinerarySlot>>()
                TIMES.forEachIndexed { rank, tod ->
                    placed[slotKey(day.dayNumber, tod)]?.let { key ->
                        entries += rank.toDouble() to model.slotByKey.getValue(key).copy(key = key, timeOfDay = tod)
                    }
                }
                val keys = dayKeys(day)
                val ranks = slotRanks(day)
                day.slots.forEachIndexed { i, slot ->
                    if (!isMovableSlot(day, slot, keys[i])) entries += ranks.getValue(keys[i]) to slot.copy(key = keys[i])
                }
                val slots = entries.sortedBy { it.first }.map { it.second }
                day.copy(slots = slots, dailyEstimatedCostUsd = slots.sumOf { slotCost(it, edits) })
            }

        val total =
            itinerary.totalEstimatedCostUsd?.takeIf { it.isFinite() }?.let { original ->
                var sum = original
                itinerary.days.forEachIndexed { i, before ->
                    if (before.dayNumber !in changedDays) return@forEachIndexed
                    val was = before.dailyEstimatedCostUsd?.takeIf { it.isFinite() } ?: daySlotCost(before, emptyMap())
                    sum += (days[i].dailyEstimatedCostUsd ?: 0.0) - was
                }
                sum
            } ?: days.sumOf { day -> day.dailyEstimatedCostUsd?.takeIf { it.isFinite() } ?: daySlotCost(day, edits) }

        return ArrangedItinerary(days, total, removed, changed = true)
    }

    /** Positions [key] can move to (city rule applied); occupied ones carry [MoveTarget.swapWith]. */
    fun moveTargets(itinerary: ItineraryData, layout: ItineraryLayout?, key: String): List<MoveTarget> {
        val model = buildModel(itinerary)
        val resolved = resolve(model, activeLayout(itinerary, layout))
        val from = resolved[key] ?: return emptyList()
        val fromDay = model.posDay.getValue(from)
        return model.positions.mapNotNull { pos ->
            if (pos == from) return@mapNotNull null
            val day = model.posDay.getValue(pos)
            if (!citiesCompatible(fromDay.city, day.city)) return@mapNotNull null
            val occupant = occupantOf(resolved, pos)
            MoveTarget(
                dayNumber = day.dayNumber,
                timeOfDay = pos.substring("${day.dayNumber ?: UNDEFINED}".length + 1),
                swapWith = occupant?.let { SwapWith(it, model.slotByKey.getValue(it).activity) },
            )
        }
    }

    /** Moves [key] to day/time, swapping with the occupant; returns [layout] itself when the move is not allowed. */
    fun moveSlot(itinerary: ItineraryData, layout: ItineraryLayout?, key: String, dayNumber: Int?, timeOfDay: String): ItineraryLayout? {
        val target = moveTargets(itinerary, layout, key).find { it.dayNumber == dayNumber && it.timeOfDay == timeOfDay } ?: return layout
        val model = buildModel(itinerary)
        val resolved = resolve(model, activeLayout(itinerary, layout))
        val from = resolved[key]
        target.swapWith?.let { resolved[it.key] = from }
        resolved[key] = slotKey(dayNumber, timeOfDay)
        return layoutFrom(itinerary, resolved)
    }

    /** Removes [key] from the plan; returns [layout] itself when there is nothing to remove. */
    fun removeSlot(itinerary: ItineraryData, layout: ItineraryLayout?, key: String): ItineraryLayout? {
        val model = buildModel(itinerary)
        val resolved = resolve(model, activeLayout(itinerary, layout))
        if (resolved[key] == null) return layout
        resolved[key] = null
        return layoutFrom(itinerary, resolved)
    }

    /** Restores a removed [key] to its original spot, else the first free time of its original day; [layout] itself when impossible. */
    fun restoreSlot(itinerary: ItineraryData, layout: ItineraryLayout?, key: String): ItineraryLayout? {
        if (layout == null || activeLayout(itinerary, layout) == null) return layout
        val model = buildModel(itinerary)
        val resolved = resolve(model, layout)
        if (!resolved.containsKey(key) || resolved[key] != null) return layout
        val originalDay = model.posDay.getValue(key)
        val candidates = listOf(key) + TIMES.map { slotKey(originalDay.dayNumber, it) }
        val free = candidates.firstOrNull { occupantOf(resolved, it) == null } ?: return layout
        resolved[key] = free
        return layoutFrom(itinerary, resolved)
    }

    /** Number of activities away from their original spot (moved or removed). */
    fun layoutChangeCount(itinerary: ItineraryData, layout: ItineraryLayout?): Int {
        val active = activeLayout(itinerary, layout) ?: return 0
        return resolve(buildModel(itinerary), active).count { (key, at) -> at != key }
    }

    /**
     * Moves picked `itinerary_slots` entries to where their activity now is. Unchanged entries keep their
     * identity; the same list comes back when nothing moved.
     */
    fun syncPlannedSlots(itinerarySlots: List<ItinerarySlot>, arranged: ArrangedItinerary): List<ItinerarySlot> {
        val where = mutableMapOf<String?, Pair<Int?, String?>>()
        for (day in arranged.days) for (slot in day.slots) where[slot.key] = day.dayNumber to slot.timeOfDay
        var changed = false
        val out =
            itinerarySlots.map { entry ->
                val at = where[entry.key] ?: return@map entry
                if (at.first == entry.dayNumber && at.second == entry.timeOfDay) return@map entry
                changed = true
                entry.patched(mapOf("day_number" to JsonPrimitive(at.first), "time_of_day" to JsonPrimitive(at.second)))
            }
        return if (changed) out else itinerarySlots
    }

    // ---- internals ---------------------------------------------------------------------------

    private const val UNDEFINED = "undefined"
    private const val HALF = 0.5
    private const val FNV_OFFSET = 0x811c9dc5.toInt()
    private const val FNV_PRIME = 0x01000193
    private const val BASE_36 = 36
    private const val UINT_MASK = 0xffffffffL

    private class Model(
        val positions: List<String>,
        val posDay: Map<String, ItineraryDay>,
        val slotByKey: Map<String, ItinerarySlot>,
        val movable: List<String>,
    )

    private fun fnv1a(text: String): String {
        var h = FNV_OFFSET
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            h = (h xor cp) * FNV_PRIME
            i += Character.charCount(cp)
        }
        return (h.toLong() and UINT_MASK).toString(BASE_36)
    }

    /** JS `${value}` of a slot field: "undefined" when absent, "null" for JSON null. */
    private fun jsText(slot: ItinerarySlot, field: String, typed: String?): String {
        if (slot.raw.isNotEmpty()) {
            val v = slot.raw[field] ?: return UNDEFINED
            return if (v is JsonNull) "null" else (v as? JsonPrimitive)?.content ?: typed ?: UNDEFINED
        }
        return typed ?: UNDEFINED
    }

    /** Keys of a day's slots in order; duplicates of a time get "#2", "#3"… suffixes. */
    internal fun dayKeys(day: ItineraryDay): List<String> {
        val counts = mutableMapOf<String, Int>()
        return day.slots.map { slot ->
            val base = slotKey(day.dayNumber, slot.timeOfDay)
            val n = (counts[base] ?: 0) + 1
            counts[base] = n
            if (n == 1) base else "$base#$n"
        }
    }

    private fun isMovableSlot(day: ItineraryDay, slot: ItinerarySlot, key: String): Boolean =
        slot.timeOfDay in TIMES && key == slotKey(day.dayNumber, slot.timeOfDay)

    private val PLACE_SPLIT = Regex("""&|/|\+|\band\b""", RegexOption.IGNORE_CASE)

    private fun placeNames(city: String?): Set<String> =
        city.orEmpty().split(",")[0].split(PLACE_SPLIT).map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    private fun buildModel(itinerary: ItineraryData): Model {
        val positions = mutableListOf<String>()
        val posDay = mutableMapOf<String, ItineraryDay>()
        val slotByKey = mutableMapOf<String, ItinerarySlot>()
        val movable = mutableListOf<String>()
        for (day in itinerary.days) {
            if (day.slots.none { it.timeOfDay in TIMES }) continue
            for (tod in TIMES) {
                val pos = slotKey(day.dayNumber, tod)
                positions += pos
                posDay[pos] = day
            }
        }
        for (day in itinerary.days) {
            val keys = dayKeys(day)
            day.slots.forEachIndexed { i, slot ->
                if (isMovableSlot(day, slot, keys[i])) {
                    slotByKey[keys[i]] = slot
                    movable += keys[i]
                }
            }
        }
        return Model(positions, posDay, slotByKey, movable)
    }

    /** Effective position of every movable key (null = removed); first claimant in original order wins. */
    private fun resolve(model: Model, layout: ItineraryLayout?): MutableMap<String, String?> {
        val map = linkedMapOf<String, String?>()
        val taken = mutableSetOf<String>()
        val claims = layout?.positions.orEmpty()
        for (key in model.movable) {
            var desired = key
            if (claims.containsKey(key)) {
                val want = claims[key]
                if (want == null) {
                    map[key] = null
                    continue
                }
                if (model.posDay.containsKey(want) && citiesCompatible(model.posDay.getValue(key).city, model.posDay.getValue(want).city)) {
                    desired = want
                }
            }
            when {
                taken.add(desired) -> map[key] = desired
                taken.add(key) -> map[key] = key
                else -> map[key] = null
            }
        }
        return map
    }

    private fun occupantOf(resolved: Map<String, String?>, pos: String): String? = resolved.entries.firstOrNull { it.value == pos }?.key

    private fun layoutFrom(itinerary: ItineraryData, resolved: Map<String, String?>): ItineraryLayout =
        ItineraryLayout(fingerprint(itinerary), resolved.filter { (key, at) -> at != key })

    private fun slotCost(slot: ItinerarySlot, edits: Map<String, SlotEdit>): Double =
        (edits[slot.key]?.cost ?: slot.estimatedCostUsd)?.takeIf { it.isFinite() } ?: 0.0

    private fun daySlotCost(day: ItineraryDay, edits: Map<String, SlotEdit>): Double = day.slots.sumOf { slotCost(it, edits) }

    private fun withKeys(day: ItineraryDay): List<ItinerarySlot> = dayKeys(day).zip(day.slots) { key, slot -> slot.copy(key = key) }

    private fun plainArrangement(itinerary: ItineraryData) =
        ArrangedItinerary(
            days = itinerary.days.map { it.copy(slots = withKeys(it)) },
            totalEstimatedCostUsd = itinerary.totalEstimatedCostUsd,
            removed = emptyList(),
            changed = false,
        )
}
