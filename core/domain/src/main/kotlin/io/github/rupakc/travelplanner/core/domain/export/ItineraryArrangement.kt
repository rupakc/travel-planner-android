package io.github.rupakc.travelplanner.core.domain.export

import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The parts of the web's `itineraryLayout.js` (`arrangeItinerary`, `slotRanks`, fingerprint) and
 * `schedule.js` (`scheduleDays`) that the plan export needs, on raw itinerary JSON.
 *
 * TODO(integration): replace with core/domain itinerary port (Team E owns `core/domain/.../itinerary/`:
 * itineraryLayout.js + schedule.js + geo.js). Kept export-internal so the two ports never meet in one API.
 * Not ported here: move/remove/restore, move targets and the arranged cost totals (export does not use them).
 */
internal object ItineraryArrangement {
    val TIMES = listOf("morning", "afternoon", "evening")

    fun slotKey(dayNumber: JsonElement?, timeOfDay: JsonElement?): String = "${dayNumber.js()}-${timeOfDay.js()}"

    /** FNV-1a, base 36 (web `hash` / `fingerprint`): short and stable, not for security. */
    fun fnv(text: String): String {
        var h = FNV_OFFSET
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            h = h xor cp
            h *= FNV_PRIME
            i += Character.charCount(cp)
        }
        return (h.toLong() and UINT_MASK).toString(RADIX)
    }

    private fun JsonObject.days(): List<JsonObject> = this["days"].objects()

    private fun JsonObject.slots(): List<JsonObject> = this["slots"].objects()

    fun fingerprint(itinerary: JsonObject): String =
        fnv(
            itinerary.days().flatMap { day ->
                day.slots().map { "${day["day_number"].js()}|${it["time_of_day"].js()}|${it["activity"].js()}" }
            }.joinToString("\n"),
        )

    private fun activeLayout(itinerary: JsonObject, layout: JsonObject?): JsonObject? {
        if (layout == null || layout["positions"] !is JsonObject) return null
        return layout.takeIf { it["fingerprint"].str() == fingerprint(itinerary) }
    }

    /** Keys of a day's slots in order; later slots with the same time get "#2", "#3"… */
    private fun dayKeys(day: JsonObject): List<String> {
        val counts = HashMap<String, Int>()
        return day.slots().map { slot ->
            val base = slotKey(day["day_number"], slot["time_of_day"])
            val n = (counts[base] ?: 0) + 1
            counts[base] = n
            if (n == 1) base else "$base#$n"
        }
    }

    private fun isMovable(day: JsonObject, slot: JsonObject, key: String): Boolean =
        slot["time_of_day"].str() in TIMES && key == slotKey(day["day_number"], slot["time_of_day"])

    /** Sort rank of every slot of a day, keyed in slot order (web `slotRanks`). */
    fun slotRanks(day: JsonObject): LinkedHashMap<String, Double> {
        val ranks = LinkedHashMap<String, Double>()
        var rank = -1.0
        val keys = dayKeys(day)
        day.slots().forEachIndexed { i, slot ->
            if (isMovable(day, slot, keys[i])) {
                rank = TIMES.indexOf(slot["time_of_day"].str()).toDouble()
                ranks[keys[i]] = rank
            } else {
                ranks[keys[i]] = rank + HALF
            }
        }
        return ranks
    }

    private val placeSeparators = Regex("&|/|\\+|\\band\\b", RegexOption.IGNORE_CASE)

    private fun placeNames(city: JsonElement?): Set<String> =
        (city.text() ?: "").split(",")[0].split(placeSeparators).map { it.trim().lowercase() }.filter(String::isNotEmpty).toSet()

    fun citiesCompatible(a: JsonElement?, b: JsonElement?): Boolean {
        val left = placeNames(a)
        val right = placeNames(b)
        return left.isEmpty() || right.isEmpty() || left.any { it in right }
    }

    private class Model(val posDay: Map<String, JsonObject>, val slotByKey: Map<String, JsonObject>, val movable: List<String>)

    private fun buildModel(itinerary: JsonObject): Model {
        val posDay = HashMap<String, JsonObject>()
        val slotByKey = HashMap<String, JsonObject>()
        val movable = mutableListOf<String>()
        for (day in itinerary.days()) {
            if (day.slots().none { it["time_of_day"].str() in TIMES }) continue
            TIMES.forEach { tod -> posDay[slotKey(day["day_number"], JsonPrimitive(tod))] = day }
        }
        for (day in itinerary.days()) {
            val keys = dayKeys(day)
            day.slots().forEachIndexed { i, slot ->
                if (isMovable(day, slot, keys[i])) {
                    slotByKey[keys[i]] = slot
                    movable += keys[i]
                }
            }
        }
        return Model(posDay, slotByKey, movable)
    }

    /** Effective position of every movable key; null = removed. */
    private fun resolve(model: Model, layout: JsonObject?): LinkedHashMap<String, String?> {
        val map = LinkedHashMap<String, String?>()
        val taken = HashSet<String>()
        val claims = layout?.get("positions").obj() ?: JsonObject(emptyMap())
        for (key in model.movable) {
            var desired = key
            if (claims.containsKey(key)) {
                val want = claims[key]
                if (want == JsonNull) {
                    map[key] = null
                    continue
                }
                val target = want.str()
                if (target != null && target in model.posDay &&
                    citiesCompatible(model.posDay.getValue(key)["city"], model.posDay.getValue(target)["city"])
                ) {
                    desired = target
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

    private fun withKeys(day: JsonObject): JsonObject {
        val keys = dayKeys(day)
        return day.with("slots", JsonArray(day.slots().mapIndexed { i, slot -> slot.with("key", JsonPrimitive(keys[i])) }))
    }

    /** Days of [itinerary] with the user's [layout] applied and every slot keyed (web `arrangeItinerary(...).days`). */
    fun arrange(itinerary: JsonObject, layout: JsonObject?): List<JsonObject> {
        val plain = { itinerary.days().map(::withKeys) }
        val active = activeLayout(itinerary, layout) ?: return plain()
        val model = buildModel(itinerary)
        val resolved = resolve(model, active)
        val changedDays = HashSet<String>()
        for ((key, at) in resolved) {
            if (at == key) continue
            changedDays += model.posDay.getValue(key)["day_number"].js()
            if (at != null) changedDays += model.posDay.getValue(at)["day_number"].js()
        }
        if (changedDays.isEmpty()) return plain()
        val placed = resolved.entries.filter { it.value != null }.associate { it.value!! to it.key }
        return itinerary.days().map { day ->
            if (day["day_number"].js() !in changedDays) return@map withKeys(day)
            val entries = mutableListOf<Pair<Double, JsonObject>>()
            TIMES.forEachIndexed { rank, tod ->
                placed[slotKey(day["day_number"], JsonPrimitive(tod))]?.let { key ->
                    entries += rank.toDouble() to model.slotByKey.getValue(key).with("key", JsonPrimitive(key)).with("time_of_day", JsonPrimitive(tod))
                }
            }
            val keys = dayKeys(day)
            val ranks = slotRanks(day)
            day.slots().forEachIndexed { i, slot ->
                if (!isMovable(day, slot, keys[i])) entries += ranks.getValue(keys[i]) to slot.with("key", JsonPrimitive(keys[i]))
            }
            day.with("slots", JsonArray(entries.sortedBy { it.first }.map { it.second }))
        }
    }

    // --- schedule.js ------------------------------------------------------------------------

    private val ANCHORS = mapOf("morning" to 9 * 60, "afternoon" to 13 * 60 + 30, "evening" to 18 * 60 + 30)
    private val DEFAULT_MINUTES = mapOf("morning" to 180, "afternoon" to 180, "evening" to 150)
    private const val OTHER_DEFAULT_MINUTES = 120
    private const val MIN_HOURS = 0.25
    private const val MAX_HOURS = 12.0
    private const val DETOUR = 1.3
    private const val WALK_MAX_KM = 1.5
    private const val WALK_KMH = 4.5
    private const val TRANSIT_KMH = 20.0
    private const val TRANSIT_OVERHEAD_MINUTES = 10.0
    private const val UNKNOWN_TRAVEL_MINUTES = 20
    private const val LATE_MINUTES = 45
    private const val DAY_END = 23 * 60
    private const val MINUTES_PER_DAY = 1440
    private const val MAX_LAT = 90.0
    private const val MAX_LNG = 180.0
    private const val EARTH_KM = 6371.0
    private const val ROUND_TO = 5.0
    private const val TENTHS = 10.0

    private fun clock(minutes: Int): String = String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60 % 24, minutes % 60)

    private fun coords(slot: JsonObject): Pair<Double, Double>? {
        val lat = slot["lat"].strictNumber() ?: return null
        val lng = slot["lng"].strictNumber() ?: return null
        return if (kotlin.math.abs(lat) > MAX_LAT || kotlin.math.abs(lng) > MAX_LNG) null else lat to lng
    }

    private fun durationMinutes(slot: JsonObject): Int {
        val hours = slot["duration_hours"].strictNumber()
        if (hours != null && hours in MIN_HOURS..MAX_HOURS) return (hours * 60).roundToLong().toInt()
        return DEFAULT_MINUTES[slot["time_of_day"].str()] ?: OTHER_DEFAULT_MINUTES
    }

    /** Web `haversineKm` (geo.js). */
    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dL = Math.toRadians(lat2 - lat1)
        val dG = Math.toRadians(lng2 - lng1)
        val a = sin(dL / 2).let { it * it } + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dG / 2).let { it * it }
        return EARTH_KM * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun transitBetween(prev: JsonObject, slot: JsonObject): JsonObject? {
        val a = coords(prev) ?: return null
        val b = coords(slot) ?: return null
        val km = haversineKm(a.first, a.second, b.first, b.second) * DETOUR
        val walk = km <= WALK_MAX_KM
        val minutes = if (walk) km / WALK_KMH * 60 else km / TRANSIT_KMH * 60 + TRANSIT_OVERHEAD_MINUTES
        return buildJsonObject {
            put("minutes", maxOf(ROUND_TO, ceil(minutes / ROUND_TO) * ROUND_TO).toInt())
            put("mode", if (walk) "walk" else "transit")
            put("km", (km * TENTHS).roundToLong() / TENTHS)
        }
    }

    private fun scheduleDay(day: JsonObject, edits: JsonObject?): JsonObject {
        val slots = day.slots()
        if (slots.none { it["time_of_day"].str() in TIMES }) return day
        val ranks = slotRanks(day).values.toList()
        val order = ranks.indices.sortedBy { ranks[it] }
        val out = slots.toMutableList()
        val warnings = mutableListOf<String>()
        var prev: Pair<JsonObject, Int>? = null
        for (i in order) {
            val slot = slots[i]
            val anchor = if (ranks[i] % 1.0 == 0.0 && ranks[i] >= 0) ANCHORS[TIMES[ranks[i].toInt()]] else null
            if (anchor == null && prev == null) continue
            val transit = prev?.let { transitBetween(it.first, slot) }
            val earliest = prev?.let { it.second + (transit?.get("minutes").strictInt() ?: UNKNOWN_TRAVEL_MINUTES) } ?: 0
            val start = maxOf(anchor ?: 0, earliest)
            val end = start + durationMinutes(slot)
            out[i] =
                JsonObject(
                    slot +
                        mapOf(
                            "start_time" to JsonPrimitive(clock(start)),
                            "end_time" to JsonPrimitive(clock(end)),
                            "start_day_offset" to JsonPrimitive(start / MINUTES_PER_DAY),
                            "end_day_offset" to JsonPrimitive(end / MINUTES_PER_DAY),
                            "transit_from_prev" to (transit ?: JsonNull),
                        ),
                )
            if (anchor != null && start - anchor > LATE_MINUTES) {
                val name = edits?.get(slot["key"].js()).obj()?.get("activity").text() ?: slot["activity"].text() ?: "An activity"
                warnings += "$name starts at ${clock(start)} — later than planned"
            }
            prev = slot to end
        }
        if (prev != null && prev.second > DAY_END) warnings += "Day ${day["day_number"].js()} runs past 23:00"
        return JsonObject(day + mapOf("slots" to JsonArray(out), "schedule_warnings" to JsonArray(warnings.map(::JsonPrimitive))))
    }

    /** Web `scheduleDays`: estimated `start_time`/`end_time` (+ day offsets, transit) on every slot. */
    fun scheduleDays(days: List<JsonObject>, edits: JsonObject?): List<JsonObject> = days.map { scheduleDay(it, edits) }

    private fun JsonObject.with(key: String, value: JsonElement) = JsonObject(this + (key to value))

    private const val FNV_OFFSET = 0x811c9dc5.toInt()
    private const val FNV_PRIME = 0x01000193
    private const val UINT_MASK = 0xFFFFFFFFL
    private const val RADIX = 36
    private const val HALF = 0.5
}
