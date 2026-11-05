package io.github.rupakc.travelplanner.core.model

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelectionRules
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.plan.SelectionKeys
import io.github.rupakc.travelplanner.core.model.section.Tip
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanSelectionsTest {
    private val webPlan =
        """
        {"id":7,"username":"alice","name":"Tokyo","search_data":{"origin":"NYC","destination":"Tokyo","departure_date":"2026-04-01","nationality":"American","web_only":1},
         "selections":{"flight":null,"flights":[],"hotel":{"name":"Park Hyatt","price_per_night_usd":"650","x_web":"keep"},
           "activities":[{"name":"Food tour","price_usd":120}],"places_to_see":[],"walking_tours":[],"sim":null,
           "tips":[{"title":"Cash","body":"Bring yen"}],"getting_around":[],"events":[],
           "itinerary_notes":{"1-morning":"Early!"},"itinerary_edits":{"1-evening":{"activity":"Ramen"}},
           "itinerary_slots":[{"key":"1-morning","day_number":1,"date":"2026-04-01","activity":"Senso-ji"}],
           "itinerary_layout":{"fingerprint":"abc","positions":{"1-morning":"2-morning","1-evening":null}},
           "packing_list":{"checked_items":{"Clothing-Jacket":true}},
           "itinerary":{"days":[],"total_estimated_cost_usd":500},"future_key":[1,2]},
         "created_at":"2026-01-01 10:00:00","updated_at":"2026-01-02 10:00:00","share_token":null}
        """.trimIndent()

    @Test
    fun `web plan decodes and re-encodes losslessly with web keys in order`() {
        val plan = TravelJson.decodeFromString(Plan.serializer(), webPlan)
        assertEquals(650.0, plan.selections.hotel?.pricePerNightUsd!!, 0.0)
        assertEquals("Early!", plan.selections.itineraryNotes["1-morning"])
        assertEquals(null, plan.selections.itineraryLayout?.positions?.get("1-evening"))
        assertEquals("Tokyo", plan.searchRequest?.destination)

        val encoded = TravelJson.encodeToJsonElement(Plan.serializer(), plan).jsonObject["selections"]!!.jsonObject
        assertEquals(JsonPrimitive("keep"), encoded["hotel"]!!.jsonObject["x_web"])
        assertEquals(JsonPrimitive("650"), encoded["hotel"]!!.jsonObject["price_per_night_usd"])
        assertEquals(2, encoded["future_key"]!!.jsonArray.size)
        assertEquals(JsonNull, encoded["flight"])
        assertEquals(SelectionKeys.ALL.filter { it in encoded }, encoded.keys.filter { it in SelectionKeys.ALL })
        assertTrue(encoded.containsKey(SelectionKeys.ITINERARY))
    }

    @Test
    fun `empty selections encode to the web EMPTY_SELECTIONS`() {
        val encoded = TravelJson.encodeToJsonElement(PlanSelections.serializer(), PlanSelections()) as JsonObject
        val expected =
            TravelJson.parseToJsonElement(
                """{"flight":null,"flights":[],"hotel":null,"activities":[],"places_to_see":[],"walking_tours":[],"sim":null,"tips":[],""" +
                    """"getting_around":[],"events":[],"itinerary_notes":{},"itinerary_edits":{},"itinerary_slots":[],""" +
                    """"itinerary_layout":null,"packing_list":null}""",
            )
        assertEquals(expected, encoded)
    }

    @Test
    fun `tips dedupe by title and count follows web countSelections`() {
        val tip = PlanItem.TipItem(Tip(title = "Cash"))
        var s = PlanSelectionRules.toggle(PlanSelections(), tip)
        s = PlanSelectionRules.toggle(s, PlanItem.TipItem(Tip(title = "Other")))
        assertEquals(2, s.count)
        s = PlanSelectionRules.toggle(s, tip)
        assertEquals(listOf("Other"), s.tips.map { it.title })
    }
}
