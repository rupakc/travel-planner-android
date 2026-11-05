package io.github.rupakc.travelplanner.core.data

import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.plan.CurrentPlanStoreImpl
import io.github.rupakc.travelplanner.core.data.repository.PlansRepository
import io.github.rupakc.travelplanner.core.database.PlanDraftDao
import io.github.rupakc.travelplanner.core.database.PlanDraftEntity
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Review I5 (save races) and I7 (storage failures must not crash; search snapshot written sparingly). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class CurrentPlanStoreRobustnessTest {
    private class Dao : PlanDraftDao {
        val rows = mutableMapOf<Int, PlanDraftEntity>()
        val writes = mutableMapOf<Int, Int>()
        var failWrites = false
        var failReads = false

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

    /** Plans repo whose create/update wait for [gate]. */
    private class GatedPlans(private val inner: FakePlansRepository = FakePlansRepository()) : PlansRepository by inner {
        var gate = CompletableDeferred<Unit>()
        var creates = 0

        override suspend fun create(name: String, searchData: JsonObject, selections: PlanSelections) =
            gate.await().let {
                creates++
                inner.create(name, searchData, selections)
            }

        override suspend fun update(id: Long, name: String?, selections: PlanSelections?) = gate.await().let { inner.update(id, name, selections) }
    }

    private val crashes = mutableListOf<Throwable>()
    private val request = TravelSearchRequest(origin = "NYC", destination = "Tokyo", departureDate = "2026-04-01", nationality = "American")

    private fun TestScope.appScope() =
        CoroutineScope(UnconfinedTestDispatcher(testScheduler) + SupervisorJob() + CoroutineExceptionHandler { _, t -> crashes += t })

    @Test
    fun `the draft request is read from the small row while the snapshot is still being restored`() =
        runTest {
            val dao = Dao()
            val draftJson =
                io.github.rupakc.travelplanner.core.model.json.TravelJson
                    .encodeToString(
                        io.github.rupakc.travelplanner.core.model.plan.PlanDraft.serializer(),
                        io.github.rupakc.travelplanner.core.model.plan.PlanDraft(searchRequest = request),
                    )
            dao.rows[PlanDraftEntity.SINGLE_ROW] = PlanDraftEntity(PlanDraftEntity.SINGLE_ROW, draftJson)
            dao.rows[PlanDraftEntity.SEARCH_ROW] = PlanDraftEntity(PlanDraftEntity.SEARCH_ROW, """{"flights":{"flights":[]}}""")
            // A scope that has not run the restore yet (a worker process right after start).
            val idle = CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler) + SupervisorJob())
            val store = CurrentPlanStoreImpl(FakePlansRepository(), dao, idle)
            assertNull(store.draft.value.searchRequest)
            assertEquals("Tokyo", store.draftRequest()?.destination)
            advanceUntilIdle()
            assertEquals("Tokyo", store.draftRequest()?.destination)
            idle.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        }

    @Test
    fun `toggle during save keeps the toggle and stays dirty`() =
        runTest {
            val plans = GatedPlans()
            val store = CurrentPlanStoreImpl(plans, Dao(), appScope())
            store.toggle(PlanItem.HotelItem(Hotel(name = "A")))
            val saving = async { store.save("Trip") }
            runCurrent() // the save has snapshotted the draft and is waiting on the network
            store.toggle(PlanItem.ActivityItem(Activity(name = "Food tour")))
            plans.gate.complete(Unit)
            assertTrue(saving.await() is AppResult.Success)
            assertEquals(1, store.draft.value.selections.activities.size)
            assertEquals("A", store.draft.value.selections.hotel?.name)
            assertEquals(1L, store.draft.value.planId)
            assertTrue(store.draft.value.dirty)
        }

    @Test
    fun `double tap on save creates one plan`() =
        runTest {
            val plans = GatedPlans()
            val store = CurrentPlanStoreImpl(plans, Dao(), appScope())
            store.toggle(PlanItem.HotelItem(Hotel(name = "A")))
            val first = async { store.save() }
            val second = async { store.save() }
            runCurrent()
            plans.gate.complete(Unit)
            first.await()
            second.await()
            assertEquals(1, plans.creates)
            assertTrue(!store.draft.value.dirty)
        }

    @Test
    fun `storage failures are logged, not fatal, and later writes still happen`() =
        runTest {
            val dao = Dao().apply { failReads = true }
            val store = CurrentPlanStoreImpl(FakePlansRepository(), dao, appScope())
            dao.failReads = false
            dao.failWrites = true
            store.toggle(PlanItem.HotelItem(Hotel(name = "A")))
            advanceUntilIdle()
            dao.failWrites = false
            store.toggle(PlanItem.ActivityItem(Activity(name = "Food tour")))
            advanceUntilIdle()
            assertTrue("crashes: $crashes", crashes.isEmpty())
            assertTrue(dao.rows[PlanDraftEntity.SINGLE_ROW]!!.json.contains("Food tour"))
        }

    @Test
    fun `search snapshot has its own row, is not rewritten by toggles, and is dropped above the cap`() =
        runTest {
            val dao = Dao()
            val store = CurrentPlanStoreImpl(FakePlansRepository(), dao, appScope())
            store.attachSearch(request, buildJsonObject { put("hotels", JsonPrimitive("x")) })
            advanceUntilIdle()
            assertEquals(1, dao.writes[PlanDraftEntity.SEARCH_ROW])
            repeat(5) { store.toggle(PlanItem.HotelItem(Hotel(name = "H$it"))) }
            advanceUntilIdle()
            assertEquals(1, dao.writes[PlanDraftEntity.SEARCH_ROW])
            assertTrue(!dao.rows[PlanDraftEntity.SINGLE_ROW]!!.json.contains("\"hotels\":\"x\""))

            val huge = buildJsonObject { put("hotels", JsonPrimitive("x".repeat(CurrentPlanStoreImpl.MAX_SEARCH_DATA_BYTES + 1))) }
            store.attachSearch(request, huge)
            advanceUntilIdle()
            assertNull(dao.rows[PlanDraftEntity.SEARCH_ROW])
            assertTrue(crashes.isEmpty())
            // In memory the snapshot is still available to this process.
            assertEquals(huge, store.draft.value.searchData)
        }

    @Test
    fun `restore brings back the draft and its search snapshot, and reset wipes both`() =
        runTest {
            val dao = Dao()
            val first = CurrentPlanStoreImpl(FakePlansRepository(), dao, appScope())
            first.attachSearch(request, buildJsonObject { put("weather", JsonPrimitive("sunny")) })
            first.toggle(PlanItem.HotelItem(Hotel(name = "A")))
            advanceUntilIdle()
            val second = CurrentPlanStoreImpl(FakePlansRepository(), dao, appScope())
            advanceUntilIdle()
            assertEquals("A", second.draft.value.selections.hotel?.name)
            assertEquals(JsonPrimitive("sunny"), second.draft.value.searchData?.get("weather"))
            second.reset()
            advanceUntilIdle()
            assertNull(second.draft.value.selections.hotel)
            assertNull(dao.rows[PlanDraftEntity.SEARCH_ROW])
            assertTrue(dao.rows[PlanDraftEntity.SINGLE_ROW]?.json?.contains("\"A\"") != true)
        }
}
