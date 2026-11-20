package io.github.rupakc.travelplanner.feature.results

import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** A restored plan gives an optional section (Place facts) a status only when it was stored. */
class ResultsSnapshotStatusTest {
    private val request =
        TravelSearchRequest(origin = "JFK", destination = "BER", departureDate = "2026-12-01", returnDate = "2026-12-04", nationality = "American")

    private fun draft(data: String) = PlanDraft(searchRequest = request, searchData = TravelJson.parseToJsonElement(data).jsonObject)

    @Test
    fun `an optional section that was not stored has no status`() {
        val state = ResultsStateBuilder.snapshot(draft("""{"tips":{"tips":[]}}"""))!!
        assertFalse(SectionId.PLACE_FACTS in state.statuses)
        assertEquals(SectionStatus.DONE, state.statuses[SectionId.TIPS])
        assertEquals(SectionStatus.WAITING, state.statuses[SectionId.FLIGHTS])
    }

    @Test
    fun `a stored optional section is done`() {
        val state = ResultsStateBuilder.snapshot(draft("""{"facts":{"places":[]}}"""))!!
        assertEquals(SectionStatus.DONE, state.statuses[SectionId.PLACE_FACTS])
    }
}
