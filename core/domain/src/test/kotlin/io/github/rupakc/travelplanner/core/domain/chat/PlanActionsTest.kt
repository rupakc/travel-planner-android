package io.github.rupakc.travelplanner.core.domain.chat

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.stream.ChatEvent
import io.github.rupakc.travelplanner.core.model.stream.PlanActionType
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanActionsTest {
    private fun obj(json: String) = TravelJson.parseToJsonElement(json) as JsonObject

    private fun action(type: PlanActionType?, field: String, data: String?) = ChatEvent.PlanAction(type, field, data?.let(::obj))

    private val base =
        PlanSelections(
            hotel = Hotel(name = "Old"),
            activities = listOf(Activity(name = "Tsukiji food tour")),
        )

    @Test
    fun `add appends to a list and keeps the server json`() {
        val result = applyPlanAction(PlanSelections.EMPTY, action(PlanActionType.ADD, "activities", """{"name":"Sumo","price_usd":80,"extra":"kept"}"""))
        assertEquals(listOf("Sumo"), result.activities.map { it.name })
        assertEquals("kept", result.activities.single().raw["extra"].toString().trim('"'))
    }

    @Test
    fun `add dedupes by name, title and key`() {
        val once = applyPlanAction(base, action(PlanActionType.ADD, "activities", """{"name":"Tsukiji food tour"}"""))
        assertSame(base.activities, once.activities)
        val tips = applyPlanAction(PlanSelections.EMPTY, action(PlanActionType.ADD, "tips", """{"title":"Cash"}"""))
        assertEquals(1, applyPlanAction(tips, action(PlanActionType.ADD, "tips", """{"title":"Cash","body":"x"}""")).tips.size)
        val slots = applyPlanAction(PlanSelections.EMPTY, action(PlanActionType.ADD, "itinerary_slots", """{"key":"1-morning","activity":"A"}"""))
        assertEquals(1, applyPlanAction(slots, action(PlanActionType.ADD, "itinerary_slots", """{"key":"1-morning","activity":"B"}""")).itinerarySlots.size)
    }

    @Test
    fun `set replaces single fields`() {
        val result = applyPlanAction(base, action(PlanActionType.SET, "hotel", """{"name":"Park Hyatt Tokyo","price_per_night_usd":650}"""))
        assertEquals("Park Hyatt Tokyo", result.hotel?.name)
        val flight = applyPlanAction(base, action(PlanActionType.SET, "flight", """{"price_usd":500,"outbound":{"airline":"JAL"}}"""))
        assertEquals("JAL", flight.flight?.outbound?.airline)
    }

    @Test
    fun `remove filters list fields and nulls single fields`() {
        val result = applyPlanAction(base, action(PlanActionType.REMOVE, "activities", """{"name":"Tsukiji food tour"}"""))
        assertTrue(result.activities.isEmpty())
        assertNull(applyPlanAction(base, action(PlanActionType.REMOVE, "hotel", """{"name":"Old"}""")).hotel)
    }

    @Test
    fun `clear all empties, clear field empties that field`() {
        assertEquals(PlanSelections.EMPTY, applyPlanAction(base, action(PlanActionType.CLEAR, "all", null)))
        val cleared = applyPlanAction(base, action(PlanActionType.CLEAR, "activities", null))
        assertTrue(cleared.activities.isEmpty())
        assertEquals("Old", cleared.hotel?.name)
        assertNull(applyPlanAction(base, action(PlanActionType.CLEAR, "hotel", null)).hotel)
    }

    @Test
    fun `unknown field or action leaves selections unchanged`() {
        assertSame(base, applyPlanAction(base, action(PlanActionType.ADD, "spaceships", """{"name":"x"}""")))
        assertSame(base, applyPlanAction(base, action(null, "activities", """{"name":"x"}""")))
    }
}
