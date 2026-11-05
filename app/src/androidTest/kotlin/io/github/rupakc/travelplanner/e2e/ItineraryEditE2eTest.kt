package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.MainActivity
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Flow 5: move an itinerary slot and add a note; the saved selections use the web's itinerary_layout / itinerary_notes. */
@E2eTest
@RunWith(AndroidJUnit4::class)
class ItineraryEditE2eTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val api = BackendApi()
    private lateinit var robot: AppRobot

    @Before fun setUp() {
        api.resetPreferences(api.adminToken())
        robot = AppRobot(rule)
    }

    @Test fun moveSlotAndNote_savedInWebShape() {
        robot.signInToHome()
        val note = "Bring the museum pass"
        val (id, _) = PlanFlows(robot, api).createItineraryPlan(PlanFlows.PLAN_PREFIX + " " + System.currentTimeMillis() % 100_000, note)

        val selections = api.plan(api.adminToken(), id).getValue("selections").jsonObject
        // itinerary_layout = { fingerprint, positions: { originalKey: "<day>-<tod>" | null } } (utils/itineraryLayout.js)
        val layout = selections.getValue("itinerary_layout").jsonObject
        assertTrue("fingerprint: $layout", layout["fingerprint"]?.jsonPrimitive?.content?.isNotBlank() == true)
        val positions = layout.getValue("positions").jsonObject
        assertTrue("a moved slot: $positions", positions.isNotEmpty())
        positions.values.filter { it != JsonNull }.forEach { assertTrue(it.jsonPrimitive.content.matches(Regex("""\d+-\w+"""))) }
        // itinerary_notes = { slotKey: text }
        val notes = selections.getValue("itinerary_notes").jsonObject
        assertEquals(listOf(note), notes.values.map { it.jsonPrimitive.content })
        assertTrue("itinerary snapshot saved", selections["itinerary"] is JsonObject)
    }
}
