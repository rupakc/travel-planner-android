package io.github.rupakc.travelplanner.feature.results.sections.info

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.section.PackingListData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.StressTestData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.sections.ItinerarySectionContent
import io.github.rupakc.travelplanner.feature.results.sections.PackingListSectionContent
import io.github.rupakc.travelplanner.feature.results.sections.StressTestSectionContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Itinerary editing, Trip Health Check re-check and packing list persistence through the plan draft. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class PlanEditingUiTest {
    @get:Rule val compose = createComposeRule()

    private fun show(dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Column(Modifier.verticalScroll(rememberScrollState())) { content() }
            }
        }
    }

    private val itinerary =
        ItineraryData(
            days =
                listOf(
                    ItineraryDay(
                        dayNumber = 1,
                        city = "Lisbon",
                        slots =
                            listOf(
                                ItinerarySlot(timeOfDay = "morning", activity = "Castle", durationHours = 2.0, estimatedCostUsd = 10.0),
                                ItinerarySlot(timeOfDay = "afternoon", activity = "Tram", durationHours = 1.0),
                                ItinerarySlot(timeOfDay = "evening", activity = "Fado", durationHours = 2.0),
                            ),
                    ),
                ),
            totalEstimatedCostUsd = 10.0,
        )

    /** The announcement is a visible line with real bounds and a polite live region (TalkBack-reachable). */
    private fun assertStatus(text: String) {
        val node = compose.onNodeWithText(text)
        node.performScrollTo().assertIsDisplayed()
        node.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        val bounds = node.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width > 0f && bounds.height > 0f)
    }

    // ---- itinerary --------------------------------------------------------------------------

    @Test
    fun `fast typing keeps every character even when the plan flow lags`() {
        val actions = FakeSectionActions(laggingFlow = true)
        show { ItinerarySectionContent(itinerary, actions) }
        compose.onNodeWithContentDescription("Edit Tram").performScrollTo().performClick()
        "Graça".forEach { compose.onNodeWithText("Location").performTextInput(it.toString()) }
        compose.onNodeWithText("Graça").assertExists()
        assertEquals("Graça", actions.value.itineraryEdits["1-afternoon"]!!["location"]!!.jsonPrimitive.content)
        "Café".forEach { compose.onNodeWithText("Personal note").performTextInput(it.toString()) }
        assertEquals("Café", actions.value.itineraryNotes["1-afternoon"])
    }

    @Test
    fun `successive edits build on the latest layout, not a stale one`() {
        val actions = FakeSectionActions(laggingFlow = true)
        show { ItinerarySectionContent(itinerary, actions) }
        compose.onNodeWithContentDescription("Move Castle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Move to Day 1 · Evening (swap with Fado)").performClick()
        // the screen still shows the old layout (flow lags) — the next action must not undo the first
        compose.onNodeWithContentDescription("Move Tram").performScrollTo().performClick()
        compose.onNodeWithText("Remove from day").performClick()
        val moved = ItineraryLayouts.moveSlot(itinerary, null, "1-morning", 1, "evening")
        assertEquals(ItineraryLayouts.removeSlot(itinerary, moved, "1-afternoon"), actions.value.itineraryLayout)
    }

    @Test
    fun `a layout changed outside the editor clears undo`() {
        val actions = FakeSectionActions()
        show { ItinerarySectionContent(itinerary, actions) }
        compose.onNodeWithContentDescription("Move Castle").performScrollTo().performClick()
        compose.onNodeWithText("Remove from day").performClick()
        compose.onNodeWithText("Undo").assertExists()
        compose.runOnIdle { actions.onUpdateSelections { PlanSelections.EMPTY } } // e.g. "clear selections" / plan load
        compose.waitForIdle()
        compose.onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun `move menu reports expanded state and lists remove last`() {
        show { ItinerarySectionContent(itinerary, FakeSectionActions()) }
        val move = compose.onNodeWithContentDescription("Move Castle")
        move.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))
        move.performScrollTo().performClick()
        move.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("Remove from day").assertExists()
    }

    @Test
    fun `itinerary renders derived times and the fixture`() {
        show { ItinerarySectionContent(fixture(SectionId.ITINERARY), FakeSectionActions()) }
        compose.onNodeWithText("Senso-ji").assertExists()
        compose.onNodeWithText("09:00–11:00").assertExists()
        compose.onNodeWithText("Est. itinerary cost: $560").assertExists()
        compose.onNodeWithText("Times are estimates based on typical durations and distances.").assertExists()
    }

    @Test
    fun `move with swap, undo and reset write the web layout`() {
        val actions = FakeSectionActions()
        show { ItinerarySectionContent(itinerary, actions) }
        compose.onNodeWithContentDescription("Move Castle").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Move to Day 1 · Evening (swap with Fado)").performClick()
        val layout = actions.value.itineraryLayout
        assertEquals(ItineraryLayouts.moveSlot(itinerary, null, "1-morning", 1, "evening"), layout)
        compose.onNodeWithText("2 changes").assertExists()
        assertStatus("Moved Castle to Day 1 · Evening (swapped with Fado)")
        compose.onNodeWithText("09:00–11:00").assertExists() // Fado now in the morning

        compose.onNodeWithText("Undo").performScrollTo().performClick()
        assertNull(actions.value.itineraryLayout)
        assertStatus("Undid the last change")

        compose.onNodeWithContentDescription("Move Tram").performScrollTo().performClick()
        compose.onNodeWithText("Remove from day").performClick()
        compose.onNodeWithText("Removed").assertExists()
        compose.onNodeWithContentDescription("Restore Tram").performScrollTo().performClick()
        assertEquals(0, ItineraryLayouts.layoutChangeCount(itinerary, actions.value.itineraryLayout))

        compose.onNodeWithContentDescription("Move Tram").performScrollTo().performClick()
        compose.onNodeWithText("Remove from day").performClick()
        compose.onNodeWithText("Reset").performScrollTo().performClick()
        assertNull(actions.value.itineraryLayout)
        assertStatus("Itinerary reset to the original order")
    }

    @Test
    fun `plan picks a slot with its times, edit and note write selections`() {
        val actions = FakeSectionActions()
        show(dark = true) { ItinerarySectionContent(itinerary, actions) }
        compose.onNodeWithContentDescription("Add Castle to plan").performScrollTo().performClick()
        val picked = actions.value.itinerarySlots.single()
        assertEquals("1-morning", picked.key)
        assertEquals("09:00", picked.raw["start_time"]!!.jsonPrimitive.content)
        compose.onNodeWithContentDescription("Remove Castle from plan").assertExists()

        compose.onNodeWithContentDescription("Add note to Fado").performScrollTo().performClick()
        compose.onNodeWithText("Personal note").performTextInput("Book ahead")
        assertEquals("Book ahead", actions.value.itineraryNotes["1-evening"])

        compose.onNodeWithContentDescription("Edit Tram").performScrollTo().performClick()
        compose.onNodeWithText("Location").performTextInput("Graça")
        assertEquals("Graça", actions.value.itineraryEdits["1-afternoon"]!!["location"]!!.jsonPrimitive.content)
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Edited").assertExists()
    }

    @Test
    fun `a saved plan locks moving but keeps the itinerary readable`() {
        show { ItinerarySectionContent(itinerary, FakeSectionActions(itineraryEditingLocked = true)) }
        compose.onNodeWithText("Editing is off while a saved plan is open", substring = true).assertExists()
        compose.onNodeWithContentDescription("Move Castle").assertDoesNotExist()
        compose.onNodeWithText("Castle").assertExists()
    }

    @Test
    fun `itinerary empty copy`() {
        show { ItinerarySectionContent(ItineraryData(), FakeSectionActions()) }
        compose.onNodeWithText("No itinerary available").assertExists()
    }

    // ---- trip health check ------------------------------------------------------------------

    @Test
    fun `health check shows score, verdict and sorted findings`() {
        show { StressTestSectionContent(fixture(SectionId.STRESS_TEST), FakeSectionActions()) }
        compose.onNodeWithContentDescription("Health score: 72/100, Needs attention").assertExists()
        compose.onNodeWithText("Jet lag").assertExists()
        compose.onNodeWithText("💡 Start later").assertExists()
        compose.onNodeWithText("Re-check my edited plan").assertDoesNotExist()
    }

    @Test
    fun `re-check sends the edited itinerary and shows the new result`() {
        val layout = ItineraryLayouts.moveSlot(itinerary, null, "1-morning", 1, "evening")
        val actions =
            FakeSectionActions(
                initial = PlanSelections(itineraryLayout = layout),
                sections = mapOf(SectionId.ITINERARY to sectionResult(SectionId.ITINERARY, itinerary)),
                recheckResult = AppResult.Success(StressTestData(overall = "green", score = 91)),
            )
        show { StressTestSectionContent(StressTestData(overall = "amber", score = 60), actions) }
        compose.onNodeWithText("Checked against the original itinerary — your moves aren't re-checked.").assertExists()
        compose.onNodeWithText("Re-check my edited plan").performScrollTo().performClick()
        compose.waitForIdle()
        val sent = actions.payloads.single()
        val slots = sent.itinerary["days"]!!.jsonArray[0].jsonObject["slots"]!!.jsonArray
        assertEquals(listOf("Fado", "Tram", "Castle"), slots.map { it.jsonObject["activity"]!!.jsonPrimitive.content })
        compose.onNodeWithText("Checked against your edited itinerary").assertExists()
        compose.onNodeWithContentDescription("Health score: 91/100, Looking solid").assertExists()
    }

    @Test
    fun `re-check failure shows the server message`() {
        val layout = ItineraryLayouts.removeSlot(itinerary, null, "1-afternoon")
        val actions =
            FakeSectionActions(
                initial = PlanSelections(itineraryLayout = layout),
                sections = mapOf(SectionId.ITINERARY to sectionResult(SectionId.ITINERARY, itinerary)),
                recheckResult = AppResult.Failure(AppError.RateLimited("Too many requests — please wait 30 seconds", 30)),
            )
        show(dark = true) { StressTestSectionContent(StressTestData(overall = "red", score = 20), actions) }
        compose.onNodeWithText("Re-check my edited plan").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Too many requests — please wait 30 seconds").assertExists()
    }

    @Test
    fun `a running re-check survives the card leaving the composition`() {
        val layout = ItineraryLayouts.moveSlot(itinerary, null, "1-morning", 1, "evening")
        val gate = CompletableDeferred<AppResult<StressTestData>>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val controller = StressRecheckController(scope) { gate.await() }
        val actions =
            FakeSectionActions(
                initial = PlanSelections(itineraryLayout = layout),
                sections = mapOf(SectionId.ITINERARY to sectionResult(SectionId.ITINERARY, itinerary)),
                stressRecheck = controller,
            )
        var visible by mutableStateOf(true)
        show { if (visible) StressTestSectionContent(StressTestData(overall = "amber", score = 60), actions) }
        compose.onNodeWithText("Re-check my edited plan").performScrollTo().performClick()
        compose.onNodeWithText("Re-checking…").assertExists()
        compose.runOnIdle { visible = false } // scrolled away: section disposed
        compose.runOnIdle { gate.complete(AppResult.Success(StressTestData(overall = "green", score = 95))) }
        compose.runOnIdle { visible = true }
        compose.onNodeWithText("Checked against your edited itinerary").assertExists()
        compose.onNodeWithContentDescription("Health score: 95/100, Looking solid").assertExists()
        scope.cancel()
    }

    @Test
    fun `health check fallback for an error result`() {
        show { StressTestSectionContent(StressTestData(error = "timed out"), FakeSectionActions()) }
        compose.onNodeWithText("Health check unavailable for this plan — try re-running the search.").assertExists()
    }

    // ---- packing list -----------------------------------------------------------------------

    @Test
    fun `packing counter, custom items and save to plan persist the web shape`() {
        val actions = FakeSectionActions()
        show { PackingListSectionContent(fixture(SectionId.PACKING_LIST), actions) }
        compose.onNodeWithText("0/1 item packed").assertExists()
        compose.onNodeWithContentDescription("Light jacket, essential").performClick()
        compose.onNodeWithText("1/1 item packed").assertExists()
        compose
            .onNodeWithContentDescription("Light jacket, essential")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))

        compose.onNodeWithText("Save to Plan").performScrollTo().performClick()
        val saved = actions.value.packingList!!
        assertEquals("true", saved["checked_items"]!!.jsonObject["Clothing::Light jacket"]!!.jsonPrimitive.content)

        // while saved, every change goes straight to the plan
        compose.onNodeWithText("Add item…").performTextInput("Umbrella")
        compose.onNodeWithContentDescription("Add item to Clothing").performClick()
        compose.onNodeWithText("1/2 items packed").assertExists()
        assertEquals("2", actions.value.packingList!!["total_items"]!!.jsonPrimitive.content)

        compose.onNodeWithContentDescription("Saved to Plan. Remove the packing list from My Plan").performScrollTo().performClick()
        assertNull(actions.value.packingList)
        compose.onNodeWithText("1/2 items packed").assertExists()
    }

    @Test
    fun `packing list opened from a saved plan restores ticks`() {
        val data: PackingListData = fixture(SectionId.PACKING_LIST)
        val saved = PackingState(mapOf("Clothing::Light jacket" to true)).toPlanJson(data)
        show(dark = true) { PackingListSectionContent(data, FakeSectionActions(PlanSelections(packingList = saved))) }
        compose.onNodeWithText("1/1 item packed").assertExists()
    }

    @Test
    fun `packing list fallback`() {
        show { PackingListSectionContent(PackingListData(), FakeSectionActions()) }
        compose.onNodeWithText("Packing list unavailable — try re-running the search.").assertExists()
    }

    @Test
    fun `undo is disabled before any change`() {
        val actions = FakeSectionActions()
        show { ItinerarySectionContent(itinerary, actions) }
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithContentDescription("Move Castle").performScrollTo().performClick()
        compose.onNodeWithText("Remove from day").performClick()
        compose.onNodeWithText("Undo").performScrollTo().performClick()
        // nothing left to undo and no change: the change bar goes away
        compose.onNodeWithText("Undo").assertDoesNotExist()
    }
}
