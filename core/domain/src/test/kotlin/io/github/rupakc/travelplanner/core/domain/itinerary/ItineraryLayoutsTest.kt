package io.github.rupakc.travelplanner.core.domain.itinerary

import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.TIMES
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.activeLayout
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.arrange
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.citiesCompatible
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.fingerprint
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.layoutChangeCount
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.moveSlot
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.moveTargets
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.removeSlot
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.restoreSlot
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.slotKey
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts.syncPlannedSlots
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.ItineraryLayout
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of the web's `utils/itineraryLayout.test.js` (same cases, same expectations). */
class ItineraryLayoutsTest {
    private fun mkDay(n: Int, city: String?, costs: List<Double?> = listOf(10.0, 20.0, 30.0)) =
        ItineraryDay(
            dayNumber = n,
            date = "2027-04-0$n",
            city = city,
            theme = "Theme $n",
            slots = TIMES.mapIndexed { i, t -> ItinerarySlot(timeOfDay = t, activity = "D$n $t", estimatedCostUsd = costs[i]) },
            dailyEstimatedCostUsd = costs.filterNotNull().sum(),
        )

    private fun itinerary() = ItineraryData(days = listOf(mkDay(1, "Tokyo"), mkDay(2, "Tokyo, Japan"), mkDay(3, "Kyoto")), totalEstimatedCostUsd = 180.0)

    private fun layoutOf(it: ItineraryData, positions: Map<String, String?>) = ItineraryLayout(fingerprint(it), positions)

    private data class Found(val day: Int?, val slot: ItinerarySlot)

    private fun find(arranged: ArrangedItinerary, key: String): Found? {
        for (d in arranged.days) for (s in d.slots) if (s.key == key) return Found(d.dayNumber, s)
        return null
    }

    private fun ItineraryData.withDay(i: Int, transform: (ItineraryDay) -> ItineraryDay) =
        copy(
            days =
                days.mapIndexed { j, d ->
                    if (j ==
                        i
                    ) {
                        transform(d)
                    } else {
                        d
                    }
                },
        )

    @Test
    fun `builds keys and exposes times`() {
        assertEquals(listOf("morning", "afternoon", "evening"), TIMES)
        assertEquals("2-evening", slotKey(2, "evening"))
    }

    @Test
    fun `fingerprint is stable and sensitive to activity changes`() {
        val a = itinerary()
        assertEquals(fingerprint(a), fingerprint(itinerary()))
        val b = itinerary().withDay(0) { d -> d.copy(slots = listOf(d.slots[0].copy(activity = "Other")) + d.slots.drop(1)) }
        assertTrue(fingerprint(a) != fingerprint(b))
    }

    @Test
    fun `fingerprint equals the web value`() {
        // Computed with the web's itineraryFingerprint (frontend/src/utils/itineraryLayout.js) on the same input.
        assertEquals(WEB_FINGERPRINT, fingerprint(itinerary()))
        // From raw JSON: absent fields read "undefined", JSON null reads "null", like JS template strings.
        val raw =
            TravelJson.decodeFromString(
                ItineraryData.serializer(),
                """{"days":[{"day_number":1,"slots":[{"time_of_day":"morning","activity":null},{"activity":"Café ☕"}]}]}""",
            )
        assertEquals(WEB_FINGERPRINT_RAW, fingerprint(raw))
    }

    @Test
    fun `no layout returns the original days with keys and changed false`() {
        val a = arrange(itinerary(), null)
        assertFalse(a.changed)
        assertTrue(a.removed.isEmpty())
        assertEquals(
            listOf(
                listOf("1-morning", "1-afternoon", "1-evening"),
                listOf("2-morning", "2-afternoon", "2-evening"),
                listOf("3-morning", "3-afternoon", "3-evening"),
            ),
            a.days.map { d -> d.slots.map { it.key } },
        )
        assertEquals("D1 morning", a.days[0].slots[0].activity)
        assertEquals(180.0, a.totalEstimatedCostUsd)
    }

    @Test
    fun `moves into an empty (removed) spot`() {
        val it0 = itinerary()
        var l = removeSlot(it0, null, "2-evening")
        l = moveSlot(it0, l, "1-morning", 2, "evening")
        val a = arrange(it0, l)
        val found = find(a, "1-morning")!!
        assertEquals(2, found.day)
        assertEquals("evening", found.slot.timeOfDay)
        assertEquals("D1 morning", found.slot.activity)
        assertEquals(listOf("1-afternoon", "1-evening"), a.days[0].slots.map { it.key })
        assertEquals(listOf("2-evening"), a.removed.map { it.key })
        assertTrue(a.changed)
    }

    @Test
    fun `swaps when the target is occupied`() {
        val it0 = itinerary()
        val l = moveSlot(it0, null, "1-morning", 2, "afternoon")
        val a = arrange(it0, l)
        assertEquals(Found(2, find(a, "1-morning")!!.slot), find(a, "1-morning"))
        assertEquals("afternoon", find(a, "1-morning")!!.slot.timeOfDay)
        assertEquals(1, find(a, "2-afternoon")!!.day)
        assertEquals("morning", find(a, "2-afternoon")!!.slot.timeOfDay)
        assertEquals(2, layoutChangeCount(it0, l))
    }

    @Test
    fun `keeps slots sorted morning to evening after a move`() {
        val it0 = itinerary()
        val a = arrange(it0, moveSlot(it0, null, "1-evening", 1, "morning"))
        assertEquals(TIMES, a.days[0].slots.map { it.timeOfDay })
        assertEquals("D1 evening", a.days[0].slots[0].activity)
    }

    @Test
    fun `moving to its own position is a no-op`() {
        val it0 = itinerary()
        val l = layoutOf(it0, emptyMap())
        assertSame(l, moveSlot(it0, l, "1-morning", 1, "morning"))
        assertNull(moveSlot(it0, null, "1-morning", 1, "morning"))
    }

    @Test
    fun `rejects cross-city moves and swaps, and excludes them from targets`() {
        val it0 = itinerary()
        val l = layoutOf(it0, emptyMap())
        assertSame(l, moveSlot(it0, l, "1-morning", 3, "morning"))
        val targets = moveTargets(it0, l, "1-morning")
        assertFalse(targets.any { it.dayNumber == 3 })
        assertEquals(5, targets.size)
        assertEquals(SwapWith("2-morning", "D2 morning"), targets.first { it.dayNumber == 2 && it.timeOfDay == "morning" }.swapWith)
    }

    @Test
    fun `allows moves when a city is missing`() {
        val it0 = itinerary().withDay(2) { it.copy(city = null) }
        assertTrue(moveTargets(it0, null, "1-morning").any { it.dayNumber == 3 })
    }

    @Test
    fun `lists free spots without swapWith`() {
        val it0 = itinerary()
        val l = removeSlot(it0, null, "2-evening")
        assertNull(moveTargets(it0, l, "1-morning").first { it.dayNumber == 2 && it.timeOfDay == "evening" }.swapWith)
    }

    @Test
    fun `removes a slot and lists it`() {
        val it0 = itinerary()
        val a = arrange(it0, removeSlot(it0, null, "1-afternoon"))
        assertNull(find(a, "1-afternoon"))
        assertEquals(1, a.removed.size)
        assertEquals("D1 afternoon", a.removed[0].slot.activity)
        assertEquals(2, a.days[0].slots.size)
    }

    @Test
    fun `restores to the original position when free`() {
        val it0 = itinerary()
        val a = arrange(it0, restoreSlot(it0, removeSlot(it0, null, "1-afternoon"), "1-afternoon"))
        assertEquals("afternoon", find(a, "1-afternoon")!!.slot.timeOfDay)
        assertFalse(a.changed)
    }

    @Test
    fun `restores to the first free spot of the original day when the original is taken`() {
        val it0 = itinerary()
        var l = removeSlot(it0, null, "1-afternoon")
        l = removeSlot(it0, l, "1-morning")
        l = moveSlot(it0, l, "2-evening", 1, "afternoon")
        val a = arrange(it0, restoreSlot(it0, l, "1-afternoon"))
        assertEquals(1, find(a, "1-afternoon")!!.day)
        assertEquals("morning", find(a, "1-afternoon")!!.slot.timeOfDay)
    }

    @Test
    fun `stays removed when the original day is full`() {
        val it0 = itinerary()
        var l = removeSlot(it0, null, "1-afternoon")
        l = moveSlot(it0, l, "2-evening", 1, "afternoon")
        assertSame(l, restoreSlot(it0, l, "1-afternoon"))
    }

    @Test
    fun `restore of a slot that is not removed is a no-op`() {
        val it0 = itinerary()
        val l = layoutOf(it0, emptyMap())
        assertSame(l, restoreSlot(it0, l, "1-morning"))
    }

    @Test
    fun `ignores a layout whose fingerprint does not match`() {
        val it0 = itinerary()
        val l = ItineraryLayout("nope", mapOf("1-morning" to null))
        assertNull(activeLayout(it0, l))
        val a = arrange(it0, l)
        assertFalse(a.changed)
        assertNotNull(find(a, "1-morning"))
        assertEquals(0, layoutChangeCount(it0, l))
    }

    @Test
    fun `ignores positions on a nonexistent day or a non-standard time`() {
        val it0 = itinerary()
        val l = layoutOf(it0, mapOf("1-morning" to "9-morning", "1-afternoon" to "2-late night", "1-evening" to "42"))
        val a = arrange(it0, l)
        assertFalse(a.changed)
        assertEquals(1, find(a, "1-morning")!!.day)
        assertEquals(0, layoutChangeCount(it0, l))
    }

    @Test
    fun `resolves colliding claims deterministically - first in original order wins`() {
        val it0 = itinerary()
        val l = layoutOf(it0, mapOf("1-morning" to "2-morning", "1-afternoon" to "2-morning"))
        val a = arrange(it0, l)
        assertEquals(2, find(a, "1-morning")!!.day)
        assertEquals("morning", find(a, "1-morning")!!.slot.timeOfDay)
        assertEquals(1, find(a, "1-afternoon")!!.day)
        assertEquals(listOf("2-morning"), a.removed.map { it.key })
        assertEquals(a, arrange(it0, l))
    }

    @Test
    fun `renders odd times where they are, not movable and not a target`() {
        val it0 = itinerary().withDay(0) { it.copy(slots = it.slots + ItinerarySlot(timeOfDay = "late night", activity = "Karaoke")) }
        assertTrue(moveTargets(it0, null, "1-late night").isEmpty())
        assertFalse(moveTargets(it0, null, "1-morning").any { it.timeOfDay == "late night" })
        assertNull(moveSlot(it0, null, "1-late night", 2, "morning"))
        assertNull(removeSlot(it0, null, "1-late night"))
        val a = arrange(it0, moveSlot(it0, null, "1-morning", 1, "evening"))
        assertEquals("Karaoke", find(a, "1-late night")!!.slot.activity)
    }

    @Test
    fun `recomputes only changed days, with edit overrides, skipping junk`() {
        var it0 = itinerary()
        // web: estimated_cost_usd = 'abc' → lenient decoding turns it into null, which also counts as 0
        it0 = it0.withDay(0) { d -> d.copy(slots = d.slots.mapIndexed { i, s -> if (i == 1) s.copy(estimatedCostUsd = null) else s }) }
        it0 = it0.withDay(1) { it.copy(dailyEstimatedCostUsd = 999.0) }
        val l = moveSlot(it0, null, "1-evening", 1, "morning")
        val edits = mapOf("1-evening" to SlotEdit(cost = 100.0), "1-morning" to SlotEdit(cost = null))
        val a = arrange(it0, l, edits)
        assertEquals(110.0, a.days[0].dailyEstimatedCostUsd)
        assertEquals(999.0, a.days[1].dailyEstimatedCostUsd)
        assertEquals(60.0, a.days[2].dailyEstimatedCostUsd)
        assertEquals(180.0 - 60 + 110, a.totalEstimatedCostUsd)
    }

    @Test
    fun `leaves numbers identical when nothing changed`() {
        val it0 = itinerary()
        val a = arrange(it0, layoutOf(it0, emptyMap()), mapOf("1-morning" to SlotEdit(cost = 500.0)))
        assertFalse(a.changed)
        assertEquals(60.0, a.days[0].dailyEstimatedCostUsd)
        assertEquals(180.0, a.totalEstimatedCostUsd)
    }

    @Test
    fun `syncPlannedSlots updates moved entries and keeps removed ones`() {
        val it0 = itinerary()
        var l = moveSlot(it0, null, "1-morning", 2, "afternoon")
        l = removeSlot(it0, l, "3-morning")
        val arranged = arrange(it0, l)
        val slots =
            listOf(
                ItinerarySlot(key = "1-morning", dayNumber = 1, timeOfDay = "morning", activity = "D1 morning"),
                ItinerarySlot(key = "3-morning", dayNumber = 3, timeOfDay = "morning", activity = "D3 morning"),
                ItinerarySlot(key = "1-evening", dayNumber = 1, timeOfDay = "evening", activity = "D1 evening"),
            )
        val out = syncPlannedSlots(slots, arranged)
        assertNotSame(slots, out)
        assertEquals(2, out[0].dayNumber)
        assertEquals("afternoon", out[0].timeOfDay)
        assertEquals("1-morning", out[0].key)
        // raw JSON follows the typed fields, so a plan saved after the move carries the new position
        assertEquals("afternoon", out[0].raw["time_of_day"].toString().trim('"'))
        assertSame(slots[1], out[1])
        assertSame(slots[2], out[2])
        val same = syncPlannedSlots(slots, arrange(it0, null))
        assertSame(slots, same)
    }

    @Test
    fun `ignores a stored cross-city position`() {
        val it0 = itinerary()
        val l = layoutOf(it0, mapOf("1-morning" to "3-morning"))
        val a = arrange(it0, l)
        assertFalse(a.changed)
        assertEquals(1, find(a, "1-morning")!!.day)
        assertEquals(0, layoutChangeCount(it0, l))
    }

    @Test
    fun `treats a stale layout as empty for the mutators`() {
        val it0 = itinerary()
        val stale = ItineraryLayout("old", mapOf("1-morning" to null))
        val moved = moveSlot(it0, stale, "1-evening", 1, "morning")
        assertEquals(moveSlot(it0, null, "1-evening", 1, "morning"), moved)
        assertEquals(fingerprint(it0), moved!!.fingerprint)
        assertEquals(listOf("1-evening", "1-morning"), moved.positions.keys.sorted())
        assertEquals(ItineraryLayout(fingerprint(it0), mapOf("2-morning" to null)), removeSlot(it0, stale, "2-morning"))
        assertEquals(moveTargets(it0, null, "1-morning"), moveTargets(it0, stale, "1-morning"))
    }

    @Test
    fun `keeps an odd-time slot after the standard slot that preceded it`() {
        var it0 = itinerary()
        it0 =
            it0.withDay(0) {
                it.copy(
                    slots =
                        listOf(
                            ItinerarySlot(timeOfDay = "morning", activity = "A"),
                            ItinerarySlot(timeOfDay = "late night", activity = "Karaoke"),
                            ItinerarySlot(timeOfDay = "evening", activity = "B"),
                        ),
                )
            }
        it0 = it0.withDay(1) { it.copy(slots = it.slots.take(2)) }
        val a = arrange(it0, moveSlot(it0, null, "1-evening", 2, "evening"))
        assertEquals(listOf("A", "Karaoke"), a.days[0].slots.map { it.activity })

        val it1 = itinerary().withDay(0) { listOf(ItinerarySlot(timeOfDay = "dawn", activity = "Run")).let { run -> it.copy(slots = run + it.slots) } }
        val a1 = arrange(it1, moveSlot(it1, null, "1-evening", 1, "morning"))
        assertEquals(listOf("Run", "D1 evening", "D1 afternoon", "D1 morning"), a1.days[0].slots.map { it.activity })
    }

    @Test
    fun `gives duplicate time_of_day slots unique, unmovable keys`() {
        val it0 =
            itinerary().withDay(0) {
                it.copy(slots = it.slots + ItinerarySlot(timeOfDay = "morning", activity = "Dup1") + ItinerarySlot(timeOfDay = "morning", activity = "Dup2"))
            }
        assertEquals(
            listOf("1-morning", "1-afternoon", "1-evening", "1-morning#2", "1-morning#3"),
            arrange(it0, null).days[0].slots.map { it.key },
        )
        assertTrue(moveTargets(it0, null, "1-morning#2").isEmpty())
        assertFalse(moveTargets(it0, null, "1-afternoon").any { it.swapWith?.key?.contains("#") == true })
        val a = arrange(it0, moveSlot(it0, null, "1-evening", 1, "morning"))
        assertEquals(listOf("1-morning#2", "1-morning#3"), a.days[0].slots.mapNotNull { it.key }.filter { "#" in it })
        assertEquals(5, a.days[0].slots.map { it.key }.toSet().size)
    }

    @Test
    fun `applies a delta to the original total when changed`() {
        fun mk(total: Double?) =
            ItineraryData(
                days =
                    listOf(
                        ItineraryDay(
                            dayNumber = 1,
                            city = "X",
                            dailyEstimatedCostUsd = 100.0,
                            slots =
                                listOf(
                                    ItinerarySlot(timeOfDay = "morning", activity = "A", estimatedCostUsd = 30.0),
                                    ItinerarySlot(timeOfDay = "afternoon", activity = "B", estimatedCostUsd = 70.0),
                                ),
                        ),
                        ItineraryDay(
                            dayNumber = 2,
                            city = "X",
                            dailyEstimatedCostUsd = 150.0,
                            slots = listOf(ItinerarySlot(timeOfDay = "morning", activity = "C", estimatedCostUsd = 150.0)),
                        ),
                        ItineraryDay(dayNumber = 3, city = "X", dailyEstimatedCostUsd = 200.0),
                    ),
                totalEstimatedCostUsd = total,
            )
        val it0 = mk(500.0)
        val l = moveSlot(it0, null, "1-morning", 2, "afternoon")
        assertEquals(500.0, arrange(it0, l).totalEstimatedCostUsd)
        assertEquals(520.0, arrange(it0, l, mapOf("1-morning" to SlotEdit(cost = 50.0))).totalEstimatedCostUsd)
        val noTotal = mk(null)
        assertEquals(70.0 + 180 + 200, arrange(noTotal, moveSlot(noTotal, null, "1-morning", 2, "afternoon")).totalEstimatedCostUsd)
    }

    @Test
    fun `offers no positions on days without a standard-time slot`() {
        val it0 =
            itinerary().let {
                it.copy(
                    days =
                        it.days + ItineraryDay(dayNumber = 4, city = "Kyoto") +
                            ItineraryDay(dayNumber = 5, city = "Kyoto", slots = listOf(ItinerarySlot(timeOfDay = "late night", activity = "Club"))),
                )
            }
        assertEquals(setOf(3), moveTargets(it0, null, "3-morning").map { it.dayNumber }.toSet())
        assertFalse(arrange(it0, layoutOf(it0, mapOf("3-morning" to "4-morning"))).changed)
        assertNull(moveSlot(it0, null, "3-morning", 5, "evening"))
    }

    @Test
    fun `treats overlapping place-name sets as the same city`() {
        assertTrue(citiesCompatible("Montserrat & Barcelona", "Barcelona"))
        assertTrue(citiesCompatible("Montserrat & Barcelona", "Barcelona, Spain"))
        assertFalse(citiesCompatible("Lisbon", "Porto"))
        assertTrue(citiesCompatible("Lisbon & Sintra", "Lisbon"))
        assertFalse(citiesCompatible("Lisbon & Sintra", "Porto"))
        assertTrue(citiesCompatible("Sintra and Cascais", "cascais, Portugal"))
        assertFalse(citiesCompatible("Rome / Vatican", "Vatican City"))
        assertTrue(citiesCompatible("Florence + Pisa", "Pisa"))
        assertTrue(citiesCompatible("", "Porto"))
        assertFalse(citiesCompatible("Sarajevo, Bosnia and Herzegovina", "Mostar, Bosnia and Herzegovina"))
        assertFalse(citiesCompatible("Basseterre, St Kitts & Nevis", "Charlestown, St Kitts & Nevis"))
        assertTrue(citiesCompatible("Montserrat & Barcelona, Spain", "Barcelona"))
        assertTrue(citiesCompatible(null, null))
    }

    @Test
    fun `allows moves and applies stored layouts across Barcelona and a day trip day`() {
        val it0 = ItineraryData(days = listOf(mkDay(1, "Barcelona"), mkDay(2, "Montserrat & Barcelona"), mkDay(3, "Girona")))
        assertTrue(moveTargets(it0, null, "1-morning").any { it.dayNumber == 2 })
        assertTrue(moveTargets(it0, null, "2-morning").any { it.dayNumber == 1 })
        assertFalse(moveTargets(it0, null, "2-morning").any { it.dayNumber == 3 })
        val a = arrange(it0, layoutOf(it0, mapOf("1-morning" to "2-morning", "2-morning" to "1-morning")))
        assertTrue(a.changed)
        assertEquals(2, find(a, "1-morning")!!.day)
        assertEquals(1, find(a, "2-morning")!!.day)
    }

    private companion object {
        const val WEB_FINGERPRINT = "kkiorp"
        const val WEB_FINGERPRINT_RAW = "u99dqe"
    }
}
