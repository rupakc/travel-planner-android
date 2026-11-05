package io.github.rupakc.travelplanner.e2e

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextInput
import io.github.rupakc.travelplanner.core.ui.R as UiR
import io.github.rupakc.travelplanner.feature.plan.R as PlanR
import io.github.rupakc.travelplanner.feature.results.R as ResultsR
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Shared steps for the plan flows (3, 4, 5): results, itinerary edits, My Plan save. */
class PlanFlows(private val robot: AppRobot, private val api: BackendApi = BackendApi()) {
    private val rule = robot.rule

    fun sectionHeader(section: Int) =
        robot.str(section).let { title ->
            hasStateDescription(robot.str(UiR.string.section_collapse, title)) or hasStateDescription(robot.str(UiR.string.section_expand, title))
        }

    /** Waits until [section] shows Ready (done or an honest error). */
    fun awaitTerminal(section: Int, timeoutMs: Long = STREAM_TIMEOUT) {
        val header = sectionHeader(section) and hasContentDescription(robot.str(UiR.string.section_status_done))
        rule.waitUntil(timeoutMs) { robot.scrollListTo(RESULTS_LIST, header) && robot.existsMerged(header) }
    }

    fun scrollToSection(key: String) {
        rule.onNode(hasTestTag(RESULTS_LIST)).performScrollToKey(key)
        rule.waitForIdle()
    }

    /** Moves the first movable itinerary slot to its first target and adds [note] to a slot; returns the moved activity. */
    fun editItinerary(note: String): String {
        scrollToSection("ITINERARY")
        val movePrefix = robot.prefixOf(ResultsR.string.itin_move_a11y)
        rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { robot.descriptionsStartingWith(movePrefix).isNotEmpty() }
        val moveLabel = robot.descriptionsStartingWith(movePrefix).first()
        val moved = moveLabel.removePrefix(movePrefix)
        robot.tapDescription(moveLabel)
        val targetPrefix = robot.str(ResultsR.string.itin_move_to_a11y, 0, AppRobot.MARK).substringBefore("0")
        rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { robot.descriptionsStartingWith(targetPrefix).isNotEmpty() }
        robot.tapDescription(robot.descriptionsStartingWith(targetPrefix).first())

        val notePrefix = robot.prefixOf(ResultsR.string.itin_note_add_a11y)
        rule.waitUntil(AppRobot.DEFAULT_TIMEOUT) { robot.descriptionsStartingWith(notePrefix).isNotEmpty() }
        robot.tapDescription(robot.descriptionsStartingWith(notePrefix).first())
        val field = rule.onAllNodes(hasSetTextAction() and hasText(robot.str(ResultsR.string.itin_field_note), substring = true)).onFirst()
        runCatching { field.performScrollTo() }
        field.performTextInput(note)
        rule.waitForIdle()
        return moved
    }

    /** Opens My Plan from the Results bar, names the plan and saves it; returns the new plan's id on the backend. */
    fun saveFromResults(name: String): String {
        val token = api.adminToken()
        val before = api.plans(token).map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
        robot.tap(ResultsR.string.results_open_my_plan)
        robot.waitForText(robot.str(PlanR.string.plan_save))
        robot.type(robot.str(PlanR.string.plan_name_label), name)
        Screenshots.capture("my-plan")
        robot.tap(PlanR.string.plan_save)
        robot.waitForText(robot.str(PlanR.string.plan_saved))
        return api.plans(token).map { it.jsonObject.getValue("id").jsonPrimitive.content }.first { it !in before }
    }

    /** Search -> wait for the itinerary -> edit it -> save as [name]. Returns the saved plan. */
    fun createItineraryPlan(name: String, note: String?): Pair<String, String?> {
        robot.searchTrip(AppRobot.Trip())
        robot.waitForText(robot.str(ResultsR.string.results_ease_title), timeoutMs = STREAM_TIMEOUT)
        awaitTerminal(UiR.string.section_itinerary)
        val moved = note?.let(::editItinerary)
        return saveFromResults(name) to moved
    }

    /** An existing plan of this account with an itinerary snapshot whose name starts with [prefix]. */
    fun existingItineraryPlan(prefix: String): JsonObject? {
        val token = api.adminToken()
        return api.plans(token).map { it.jsonObject }
            .filter { it["name"]?.jsonPrimitive?.content?.startsWith(prefix) == true }
            .map { api.plan(token, it.getValue("id").jsonPrimitive.content) }
            .firstOrNull { (it["selections"] as? JsonObject)?.get("itinerary") is JsonObject }
    }

    companion object {
        const val RESULTS_LIST = "results_list"
        const val STREAM_TIMEOUT = 300_000L
        const val PLAN_PREFIX = "E2E Lisbon itinerary"
    }
}
