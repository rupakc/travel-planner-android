package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStoreImpl
import io.github.rupakc.travelplanner.core.database.PlanDraftDao
import io.github.rupakc.travelplanner.core.database.PlanDraftEntity
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.PlanDraft
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightLeg
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CurrentPlanStoreImplTest {
    private class InMemoryDraftDao : PlanDraftDao {
        val rows = mutableMapOf<Int, PlanDraftEntity>()
        val writes = mutableMapOf<Int, Int>()
        var failWrites = false
        var failReads = false
        val row: PlanDraftEntity? get() = rows[PlanDraftEntity.SINGLE_ROW]

        override suspend fun get() = getRow(PlanDraftEntity.SINGLE_ROW)

        override suspend fun save(draft: PlanDraftEntity) {
            if (failWrites) throw android.database.sqlite.SQLiteFullException("disk full")
            rows[draft.id] = draft
            writes[draft.id] = (writes[draft.id] ?: 0) + 1
        }

        override suspend fun clear() = rows.clear()

        override suspend fun getRow(id: Int): PlanDraftEntity? {
            if (failReads) throw android.database.sqlite.SQLiteBlobTooBigException("row too big")
            return rows[id]
        }

        override suspend fun deleteRow(id: Int) {
            rows.remove(id)
        }
    }

    private val hotelA = Hotel(name = "A")
    private val hotelB = Hotel(name = "B")
    private val activity = Activity(name = "Food tour")
    private val request = TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-04-01", nationality = "American")

    private fun TestScope.store(dao: InMemoryDraftDao = InMemoryDraftDao(), plans: FakePlansRepository = FakePlansRepository()) =
        CurrentPlanStoreImpl(plans, dao, TestScope(UnconfinedTestDispatcher(testScheduler)))

    @Test
    fun `single-choice categories replace and lists add or remove`() =
        runTest {
            val store = store()
            store.toggle(PlanItem.HotelItem(hotelA))
            store.toggle(PlanItem.HotelItem(hotelB))
            assertEquals("B", store.draft.value.selections.hotel?.name)
            store.toggle(PlanItem.HotelItem(hotelB))
            assertNull(store.draft.value.selections.hotel)

            store.toggle(PlanItem.ActivityItem(activity))
            assertTrue(store.isSelected(PlanItem.ActivityItem(activity)))
            store.toggle(PlanItem.ActivityItem(activity))
            assertFalse(store.isSelected(PlanItem.ActivityItem(activity)))
            assertTrue(store.draft.value.dirty)
        }

    @Test
    fun `multi-city legs keep one flight per leg`() =
        runTest {
            val store = store()
            val leg0a = Flight(priceUsd = 100.0, legIndex = 0, outbound = FlightLeg(airline = "X", flightNumber = "1"))
            val leg0b = Flight(priceUsd = 120.0, legIndex = 0, outbound = FlightLeg(airline = "Y", flightNumber = "2"))
            val leg1 = Flight(priceUsd = 90.0, legIndex = 1, outbound = FlightLeg(airline = "Z", flightNumber = "3"))
            store.toggle(PlanItem.FlightItem(leg1))
            store.toggle(PlanItem.FlightItem(leg0a))
            store.toggle(PlanItem.FlightItem(leg0b))
            assertEquals(listOf(0, 1), store.draft.value.selections.flights.map { it.legIndex })
            assertEquals("Y", store.draft.value.selections.flights.first().outbound?.airline)
        }

    @Test
    fun `save creates then updates and snapshots the live itinerary`() =
        runTest {
            val plans = FakePlansRepository()
            val store = store(plans = plans)
            val itinerary =
                buildJsonObject {
                    put("days", kotlinx.serialization.json.JsonArray(emptyList()))
                    put("total_estimated_cost_usd", JsonPrimitive(560))
                }
            store.attachSearch(request, buildJsonObject { put("itinerary", itinerary) })
            store.toggle(PlanItem.HotelItem(hotelA))
            val created = store.save("Tokyo trip") as AppResult.Success
            assertEquals(1L, created.value.id)
            assertEquals("Tokyo", (created.value.searchData["destination"] as JsonPrimitive).content)
            assertNotNull(created.value.selections.itinerarySnapshot)
            assertFalse(store.draft.value.dirty)

            store.toggle(PlanItem.ActivityItem(activity))
            val updated = store.save() as AppResult.Success
            assertEquals(1L, updated.value.id)
            assertEquals("Tokyo trip", updated.value.name)
            assertEquals(1, updated.value.selections.activities.size)
        }

    @Test
    fun `load, snapshot and restore`() =
        runTest {
            val plans = FakePlansRepository()
            val store = store(plans = plans)
            store.toggle(PlanItem.HotelItem(hotelA))
            val saved = (store.save("Saved") as AppResult.Success).value
            store.newPlan()
            assertNull(store.draft.value.planId)
            store.load(saved.id)
            assertEquals("A", store.draft.value.selections.hotel?.name)
            val snap = store.snapshot()
            store.toggle(PlanItem.HotelItem(hotelA))
            store.restore(snap)
            assertEquals("A", store.draft.value.selections.hotel?.name)
        }

    @Test
    fun `draft is persisted and restored after process death`() =
        runTest {
            val dao = InMemoryDraftDao()
            val first = store(dao)
            first.toggle(PlanItem.ActivityItem(activity))
            advanceUntilIdle()
            assertNotNull(dao.row)
            val second = store(dao)
            advanceUntilIdle()
            assertEquals("Food tour", second.draft.value.selections.activities.single().name)
            val decoded = TravelJson.decodeFromString(PlanDraft.serializer(), dao.row!!.json)
            assertEquals("Food tour", decoded.selections.activities.single().name)
        }
}
