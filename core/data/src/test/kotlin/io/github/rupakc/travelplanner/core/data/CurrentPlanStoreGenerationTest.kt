package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStoreImpl
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.data.search.SearchLauncherImpl
import io.github.rupakc.travelplanner.core.database.PlanDraftDao
import io.github.rupakc.travelplanner.core.database.PlanDraftEntity
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.Plan
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.search.searchKey
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Architecture review I2 (draft generations) and performance review I3 (no snapshot walk on a toggle). */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrentPlanStoreGenerationTest {
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

    /** A plans repository whose create waits for [gate]. */
    private class SlowPlans(val delegate: FakePlansRepository = FakePlansRepository()) : PlansRepository by delegate {
        val gate = CompletableDeferred<Unit>()

        override suspend fun create(name: String, searchData: JsonObject, selections: PlanSelections): AppResult<Plan> {
            gate.await()
            return delegate.create(name, searchData, selections)
        }
    }

    /** Map that counts structural comparisons (what `JsonObject.equals` delegates to). */
    private class CountingMap(private val inner: Map<String, JsonElement>) : Map<String, JsonElement> by inner {
        var equalsCalls = 0

        override fun equals(other: Any?): Boolean {
            equalsCalls++
            return inner == other
        }

        override fun hashCode(): Int = inner.hashCode()
    }

    private val tokyo = TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-04-01", nationality = "American")
    private val lisbon = tokyo.copy(destination = "Lisbon")

    private fun TestScope.store(plans: PlansRepository = FakePlansRepository()) =
        CurrentPlanStoreImpl(plans, Dao(), CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `a slow save does not stamp its id on a trip opened meanwhile`() =
        runTest {
            val plans = SlowPlans()
            val store = store(plans)
            store.attachSearch(tokyo, null)
            store.toggle(PlanItem.HotelItem(Hotel(name = "Tokyo hotel")))
            val saving = async { store.save("Tokyo") }
            runCurrent() // the save is now waiting for the server
            // The user opens another trip while the save is in flight.
            store.load(Plan(id = 42, name = "Lisbon", selections = PlanSelections(hotel = Hotel(name = "Lisbon hotel"))))
            plans.gate.complete(Unit)
            assertTrue(saving.await() is AppResult.Success)
            val draft = store.draft.value
            assertEquals(42L, draft.planId)
            assertEquals("Lisbon", draft.name)
            assertEquals("Lisbon hotel", draft.selections.hotel?.name)
        }

    @Test
    fun `a save with no replacement in between still applies its id`() =
        runTest {
            val store = store()
            store.toggle(PlanItem.HotelItem(Hotel(name = "A")))
            val saved = store.save("Trip") as AppResult.Success
            assertEquals(saved.value.id, store.draft.value.planId)
            assertFalse(store.draft.value.dirty)
        }

    @Test
    fun `load marks the draft as opened from a saved plan, save and new searches do not`() =
        runTest {
            val store = store()
            store.toggle(PlanItem.HotelItem(Hotel(name = "A")))
            store.save("Trip")
            assertFalse(store.draft.value.loadedPlan)
            store.load(Plan(id = 9, name = "Saved"))
            assertTrue(store.draft.value.loadedPlan)
            SearchLauncherImpl(store, FakeSearchRepository()).startNewSearch(tokyo)
            assertFalse(store.draft.value.loadedPlan)
        }

    @Test
    fun `startNewSearch replaces the draft with a new plan for the search and starts it`() =
        runTest {
            val store = store()
            store.load(Plan(id = 1, name = "Old"))
            store.attachSearch(lisbon, JsonObject(mapOf("hotels" to JsonObject(emptyMap()))))
            val search = FakeSearchRepository()
            val preload = PlanSelections(hotel = Hotel(name = "Kept"))
            SearchLauncherImpl(store, search).startNewSearch(tokyo, preload)
            val draft = store.draft.value
            assertNull(draft.planId)
            assertNull(draft.searchData)
            assertEquals(tokyo.searchKey(), draft.searchKey)
            assertEquals("Kept", draft.selections.hotel?.name)
            assertEquals(listOf(tokyo), search.started)
            assertEquals(StreamPhase.DONE, search.current.value.phase)
        }

    @Test
    fun `a toggle does not compare the search snapshot structurally`() =
        runTest {
            val store = store()
            val content = CountingMap(mapOf("itinerary" to JsonObject(mapOf("days" to JsonPrimitive(1)))))
            store.attachSearch(tokyo, JsonObject(content))
            content.equalsCalls = 0
            repeat(5) { store.toggle(PlanItem.HotelItem(Hotel(name = "H$it"))) }
            store.rename("Renamed")
            assertEquals(0, content.equalsCalls)
        }

    @Test
    fun `draft equality treats an identical-content snapshot copy as a change`() {
        val a = TravelJson.parseToJsonElement("""{"hotels":{"results":[]}}""") as JsonObject
        val b = TravelJson.parseToJsonElement("""{"hotels":{"results":[]}}""") as JsonObject
        assertEquals(PlanDraft(searchData = a), PlanDraft(searchData = a))
        assertFalse(PlanDraft(searchData = a) == PlanDraft(searchData = b))
    }
}
