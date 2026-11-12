package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.model.trips.PrepItem
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrepRulesTest {
    private fun item(id: String, status: String = "todo", auto: Boolean = false, due: String? = null, overdue: Boolean = false) =
        PrepItem(id, status, auto, due, overdue)

    @Test
    fun `percent is floored and na is excluded`() {
        val s = PrepRules.summarize(listOf(item("a", "done"), item("b"), item("c"), item("d", "na")))
        assertEquals(1, s.done)
        assertEquals(3, s.total)
        assertEquals(33, s.percent)
        assertFalse(s.complete)
    }

    @Test
    fun `two of three is 66 not 67`() {
        assertEquals(66, PrepRules.summarize(listOf(item("a", "done"), item("b", "done"), item("c"))).percent)
    }

    @Test
    fun `no live items is 0 and never complete`() {
        val s = PrepRules.summarize(listOf(item("a", "na")))
        assertEquals(0, s.total)
        assertEquals(0, s.percent)
        assertFalse(s.complete)
        assertFalse(PrepRules.summarize(emptyList()).complete)
    }

    @Test
    fun `all live items done is complete`() {
        assertTrue(PrepRules.summarize(listOf(item("a", "done"), item("b", "na"), item("c", "done"))).complete)
    }

    @Test
    fun `applyToggle flips one item and leaves others`() {
        val items = listOf(item("visa"), item("passport", "done"))
        val next = PrepRules.applyToggle(items, "visa", true)
        assertEquals(listOf("done", "done"), next.map { it.status })
        assertEquals("todo", PrepRules.applyToggle(next, "visa", false)[0].status)
    }

    @Test
    fun `auto and na items cannot be toggled`() {
        assertFalse(PrepRules.canToggle(item("offline_pack", auto = true)))
        assertFalse(PrepRules.canToggle(item("visa", "na")))
        assertTrue(PrepRules.canToggle(item("passport")))
        val items = listOf(item("offline_pack", auto = true), item("visa", "na"))
        assertEquals(items, PrepRules.applyToggle(items, "offline_pack", true))
        assertEquals(items, PrepRules.applyToggle(items, "visa", true))
    }

    @Test
    fun `partition puts na items in their own group`() {
        val (active, na) = PrepRules.partition(listOf(item("a"), item("b", "na"), item("c", "done")))
        assertEquals(listOf("a", "c"), active.map { it.id })
        assertEquals(listOf("b"), na.map { it.id })
    }

    @Test
    fun `due state needs a due date and an open item`() {
        assertEquals(PrepRules.Due.None, PrepRules.dueState(item("a")))
        assertEquals(PrepRules.Due.Upcoming, PrepRules.dueState(item("a", due = "2027-04-01")))
        assertEquals(PrepRules.Due.Overdue, PrepRules.dueState(item("a", due = "2027-04-01", overdue = true)))
        assertEquals(PrepRules.Due.None, PrepRules.dueState(item("a", "done", due = "2027-04-01", overdue = true)))
        assertEquals(PrepRules.Due.None, PrepRules.dueState(item("a", "na", due = "2027-04-01")))
    }

    @Test
    fun `visa counts as checked when done or not needed`() {
        assertTrue(PrepRules.visaChecked(listOf(item("visa", "done"))))
        assertTrue(PrepRules.visaChecked(listOf(item("visa", "na"))))
        assertFalse(PrepRules.visaChecked(listOf(item("visa"))))
        assertFalse(PrepRules.visaChecked(emptyList()))
    }

    @Test
    fun `official url must be http or https`() {
        fun facts(url: String) = JsonObject(mapOf("official_url" to JsonPrimitive(url)))
        assertEquals("https://example.gov/visa", PrepRules.officialUrl(facts("https://example.gov/visa")))
        assertEquals("http://example.gov", PrepRules.officialUrl(facts(" http://example.gov ")))
        assertNull(PrepRules.officialUrl(facts("javascript:alert(1)")))
        assertNull(PrepRules.officialUrl(facts("intent://x")))
        assertNull(PrepRules.officialUrl(JsonObject(emptyMap())))
    }

    @Test
    fun `tick-off ids line up with the telemetry enum`() {
        assertEquals(12, io.github.rupakc.travelplanner.core.model.trips.PrepItemIds.ALL.size)
    }
}
