package io.github.rupakc.travelplanner.feature.plan.myplan

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.domain.plan.BudgetStatus
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeCurrentPlanStore
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.feature.plan.FixedClock
import io.github.rupakc.travelplanner.feature.plan.export.ExportRequest
import io.github.rupakc.travelplanner.feature.plan.model.PlanCategory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MyPlanViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val repo = FakePlansRepository()
    private val store = FakeCurrentPlanStore(repo)
    private val request =
        TravelSearchRequest(
            origin = "NYC",
            destination = "Tokyo",
            departureDate = "2027-04-01",
            returnDate = "2027-04-05",
            interests = listOf("food"),
            nationality = "American",
            budgetUsd = 3000.0,
        )

    private fun viewModel() = MyPlanViewModel(store, FixedClock(), TestAppDispatchers(main.testDispatcher))

    private fun seed() {
        store.attachSearch(request, null)
        store.toggle(PlanItem.HotelItem(Hotel(name = "Park Hyatt", pricePerNightUsd = 500.0)))
        store.toggle(PlanItem.ActivityItem(Activity(name = "Sushi", priceUsd = 80.0)))
    }

    @Test
    fun `empty draft is empty with the default name`() =
        runTest {
            viewModel().state.test {
                val c = expectMostRecentItem().content!!
                assertTrue(c.isEmpty)
                assertEquals("My Trip Plan", c.name)
            }
        }

    @Test
    fun `groups, cost and budget status from the draft with an auto name`() =
        runTest {
            seed()
            viewModel().state.test {
                val c = expectMostRecentItem().content!!
                assertEquals(listOf(PlanCategory.HOTEL, PlanCategory.ACTIVITIES), c.groups.map { it.category })
                assertEquals(2080.0, c.totalUsd, 0.0)
                assertEquals(BudgetStatus(over = false, amount = 920), c.budgetStatus)
                assertTrue(c.name, c.name.startsWith("The ") && c.name.contains(" Tokyo "))
                assertTrue(c.dirty)
            }
        }

    @Test
    fun `remove then undo restores the item`() =
        runTest {
            seed()
            val vm = viewModel()
            vm.state.test {
                val entry = expectMostRecentItem().content!!.groups.first { it.category == PlanCategory.ACTIVITIES }.entries.single()
                vm.onEvent(MyPlanEvent.Remove(entry.removal!!, entry.title))
                val s = expectMostRecentItem()
                assertEquals(MyPlanMessage.Removed("Sushi"), s.message)
                assertFalse(s.content!!.groups.any { it.category == PlanCategory.ACTIVITIES })
                vm.onEvent(MyPlanEvent.UndoRemove)
                assertTrue(expectMostRecentItem().content!!.groups.any { it.category == PlanCategory.ACTIVITIES })
            }
        }

    @Test
    fun `save creates then updates`() =
        runTest {
            seed()
            val vm = viewModel()
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(MyPlanEvent.NameChanged("Japan"))
                vm.onEvent(MyPlanEvent.Save)
                assertEquals(MyPlanMessage.Saved, expectMostRecentItem().message)
                vm.onEvent(MyPlanEvent.MessageShown)
                vm.onEvent(MyPlanEvent.Save)
                assertEquals(MyPlanMessage.Updated, expectMostRecentItem().message)
            }
            assertEquals(listOf("Japan"), repo.plans.first().map { it.name })
        }

    @Test
    fun `save as new keeps the old plan and creates another`() =
        runTest {
            seed()
            val vm = viewModel()
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(MyPlanEvent.Save)
                expectMostRecentItem()
                vm.onEvent(MyPlanEvent.SaveAsNew)
                assertEquals(MyPlanMessage.Saved, expectMostRecentItem().message)
            }
            assertEquals(2, repo.plans.first().size)
            assertEquals(2L, store.draft.value.planId)
        }

    @Test
    fun `save failures map to the web messages`() =
        runTest {
            seed()
            val vm = viewModel()
            vm.state.test {
                expectMostRecentItem()
                repo.failNext = AppError.Unauthorized()
                vm.onEvent(MyPlanEvent.Save)
                assertEquals(MyPlanMessage.LoginToSave, expectMostRecentItem().message)
                repo.failNext = AppError.Server(500, "Database busy")
                vm.onEvent(MyPlanEvent.Save)
                assertEquals(MyPlanMessage.SaveFailed(AppError.Server(500, "Database busy")), expectMostRecentItem().message)
            }
        }

    @Test
    fun `new plan asks first when there are unsaved changes`() =
        runTest {
            seed()
            val vm = viewModel()
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(MyPlanEvent.NewPlan)
                assertTrue(expectMostRecentItem().confirmNewPlan)
                vm.onEvent(MyPlanEvent.ConfirmNewPlan)
                val s = expectMostRecentItem()
                assertFalse(s.confirmNewPlan)
                assertTrue(s.content!!.isEmpty)
            }
        }

    @Test
    fun `export without anything datable says so, with a hotel it emits ics`() =
        runTest {
            val vm = viewModel()
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(MyPlanEvent.ExportCalendar)
                assertEquals(MyPlanMessage.NothingToExport, expectMostRecentItem().message)
            }
            seed()
            vm.exportRequests.test {
                vm.onEvent(MyPlanEvent.ExportCalendar)
                assertTrue((awaitItem() as ExportRequest.Ics).content.contains("DTSTART;VALUE=DATE:20270401"))
            }
        }

    @Test
    fun `a plan with only notes is not empty`() =
        runTest {
            store.setSelections(io.github.rupakc.travelplanner.core.model.plan.PlanSelections(itineraryNotes = mapOf("1-morning" to "Bring cash")))
            viewModel().state.test {
                assertFalse(expectMostRecentItem().content!!.isEmpty)
            }
        }
}
