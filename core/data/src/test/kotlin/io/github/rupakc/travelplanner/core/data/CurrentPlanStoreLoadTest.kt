package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStoreImpl
import io.github.rupakc.travelplanner.core.database.PlanDraftDao
import io.github.rupakc.travelplanner.core.database.PlanDraftEntity
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.search.searchKey
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Results must render a saved plan without a stream, and tell the draft's search from a stale one. */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrentPlanStoreLoadTest {
    private class Dao : PlanDraftDao {
        val rows = mutableMapOf<Int, PlanDraftEntity>()

        override suspend fun get() = rows[0]

        override suspend fun save(draft: PlanDraftEntity) {
            rows[draft.id] = draft
        }

        override suspend fun clear() = rows.clear()

        override suspend fun getRow(id: Int) = rows[id]

        override suspend fun deleteRow(id: Int) {
            rows.remove(id)
        }
    }

    private val request = TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-04-01", nationality = "American")
    private val requestJson = TravelJson.encodeToJsonElement(TravelSearchRequest.serializer(), request) as JsonObject
    private val itinerary =
        TravelJson.parseToJsonElement(
            """{"days":[{"day_number":1,"date":"2026-04-01","slots":[{"time_of_day":"morning","activity":"Senso-ji"}]}],"total_estimated_cost_usd":120}""",
        ) as JsonObject

    private fun TestScope.store(plans: FakePlansRepository) = CurrentPlanStoreImpl(plans, Dao(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `load restores the request, its key and the saved itinerary as search data`() =
        runTest {
            val plan = Plan(id = 7, name = "Tokyo", searchData = requestJson, selections = PlanSelections(itinerarySnapshot = itinerary))
            val store = store(FakePlansRepository(listOf(plan)))
            store.load(7) as AppResult.Success
            val draft = store.draft.value
            assertEquals(request, draft.searchRequest)
            assertEquals(request.searchKey(), draft.searchKey)
            val restored = TravelJson.decodeFromJsonElement(ItineraryData.serializer(), draft.searchData!!["itinerary"]!!)
            assertEquals("Senso-ji", restored.days.single().slots.single().activity)
        }

    @Test
    fun `section objects stored in search_data are restored too`() =
        runTest {
            val withSections =
                buildJsonObject {
                    requestJson.forEach { (k, v) -> put(k, v) }
                    put("hotels", buildJsonObject { put("results", kotlinx.serialization.json.JsonArray(emptyList())) })
                    put("not_a_section", JsonPrimitive(1))
                }
            val store = store(FakePlansRepository(listOf(Plan(id = 3, searchData = withSections))))
            store.load(3)
            assertEquals(setOf("hotels"), store.draft.value.searchData?.keys)
        }

    @Test
    fun `plans with nothing recoverable load with null search data`() =
        runTest {
            val store = store(FakePlansRepository(listOf(Plan(id = 1, searchData = requestJson))))
            store.load(1)
            assertNull(store.draft.value.searchData)
        }

    @Test
    fun `attachSearch for a different request changes the key and newPlan clears it`() =
        runTest {
            val store = store(FakePlansRepository())
            store.attachSearch(request, null)
            val first = store.draft.value.searchKey
            assertEquals(request.searchKey(), first)
            store.attachSearch(request.copy(destination = "Osaka"), null)
            assertNotEquals(first, store.draft.value.searchKey)
            store.newPlan()
            assertNull(store.draft.value.searchKey)
        }
}
