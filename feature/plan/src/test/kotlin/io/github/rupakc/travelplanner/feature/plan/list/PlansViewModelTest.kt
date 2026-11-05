package io.github.rupakc.travelplanner.feature.plan.list

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.feature.plan.FixedClock
import io.github.rupakc.travelplanner.feature.plan.model.TripFilter
import io.github.rupakc.travelplanner.feature.plan.model.TripSort
import io.github.rupakc.travelplanner.feature.plan.model.plan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlansViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val initial =
        listOf(
            plan(1, "Spring in Japan", "Tokyo", "2027-04-01", "2027-04-08", "2026-10-01 10:00:00"),
            plan(2, "Andalucía", "Málaga", "2026-12-01", "2026-12-05", "2026-11-04 10:00:00", shareToken = "t"),
            plan(3, "Old trip", "Lisbon", "2026-03-01", "2026-03-05", "2026-03-06 10:00:00"),
        )

    private fun TestScope.viewModel(repo: FakePlansRepository) =
        PlansViewModel(SavedStateHandle(), repo, FixedClock(), TestAppDispatchers(main.testDispatcher), backgroundScope)

    @Test
    fun `shows cached trips, then search, filter and sort narrow them`() =
        runTest {
            val vm = viewModel(FakePlansRepository(initial))
            vm.state.test {
                var s = expectMostRecentItem()
                assertFalse(s.isLoading)
                assertEquals(3, s.totalCount)
                assertEquals(listOf(1L, 2L, 3L), s.trips.map { it.id })

                vm.onEvent(PlansEvent.QueryChanged("malaga"))
                s = awaitItem()
                assertEquals(listOf(2L), s.trips.map { it.id })

                vm.onEvent(PlansEvent.QueryChanged(""))
                vm.onEvent(PlansEvent.FilterChanged(TripFilter.PAST))
                s = expectMostRecentItem()
                assertEquals(listOf(3L), s.trips.map { it.id })

                vm.onEvent(PlansEvent.FilterChanged(TripFilter.ALL))
                vm.onEvent(PlansEvent.SharedOnlyChanged(true))
                assertEquals(listOf(2L), expectMostRecentItem().trips.map { it.id })

                vm.onEvent(PlansEvent.SharedOnlyChanged(false))
                vm.onEvent(PlansEvent.SortChanged(TripSort.UPDATED))
                assertEquals(listOf(2L, 1L, 3L), expectMostRecentItem().trips.map { it.id })
            }
        }

    @Test
    fun `offline refresh keeps the cached copies and reports the error`() =
        runTest {
            val repo = FakePlansRepository(initial).apply { failNext = AppError.Network() }
            val vm = viewModel(repo)
            vm.state.test {
                val s = expectMostRecentItem()
                assertEquals(3, s.trips.size)
                assertTrue(s.error is AppError.Network)
                vm.onEvent(PlansEvent.Refresh)
                assertNull(expectMostRecentItem().error)
            }
        }

    @Test
    fun `empty cache with a failed first refresh shows the error, not a skeleton`() =
        runTest {
            val vm = viewModel(FakePlansRepository().apply { failNext = AppError.Server(500, null) })
            vm.state.test {
                val s = expectMostRecentItem()
                assertFalse(s.isLoading)
                assertEquals(0, s.totalCount)
                assertTrue(s.error is AppError.Server)
            }
        }

    @Test
    fun `swipe delete hides the trip and undo brings it back without a server call`() =
        runTest {
            val repo = FakePlansRepository(initial)
            val vm = viewModel(repo)
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(PlansEvent.Delete(1))
                val s = expectMostRecentItem()
                assertEquals(listOf(2L, 3L), s.trips.map { it.id })
                assertEquals(PlansMessage.Deleted(1, "Spring in Japan"), s.message)
                vm.onEvent(PlansEvent.UndoDelete)
                assertEquals(listOf(1L, 2L, 3L), expectMostRecentItem().trips.map { it.id })
            }
            assertEquals(3, repo.plans.first().size)
        }

    @Test
    fun `dismissing the undo snackbar deletes on the server`() =
        runTest {
            val repo = FakePlansRepository(initial)
            val vm = viewModel(repo)
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(PlansEvent.Delete(3))
                vm.onEvent(PlansEvent.CommitDelete)
                assertEquals(listOf(1L, 2L), expectMostRecentItem().trips.map { it.id })
            }
            assertEquals(listOf(1L, 2L), repo.plans.first().map { it.id })
        }

    @Test
    fun `a failed delete brings the trip back with a message`() =
        runTest {
            val repo = FakePlansRepository(initial)
            val vm = viewModel(repo)
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(PlansEvent.Delete(3))
                repo.failNext = AppError.Network()
                vm.onEvent(PlansEvent.CommitDelete)
                val s = expectMostRecentItem()
                assertEquals(3, s.trips.size)
                assertTrue(s.message is PlansMessage.DeleteFailed)
            }
        }

    @Test
    fun `same-named trips get distinct undo messages`() =
        runTest {
            val repo = FakePlansRepository(listOf(plan(1, "Paris", "Paris", "2027-01-01"), plan(2, "Paris", "Paris", "2027-02-01")))
            val vm = viewModel(repo)
            vm.state.test {
                expectMostRecentItem()
                vm.onEvent(PlansEvent.Delete(1))
                val first = expectMostRecentItem().message
                vm.onEvent(PlansEvent.Delete(2))
                val second = expectMostRecentItem().message
                assertTrue(first != second)
            }
        }

    @Test
    fun `a swipe-delete pending at process death is completed on restore`() =
        runTest {
            val repo = FakePlansRepository(initial)
            PlansViewModel(
                SavedStateHandle(mapOf(PlansViewModel.PENDING_DELETE_KEY to 3L)),
                repo,
                FixedClock(),
                TestAppDispatchers(main.testDispatcher),
                backgroundScope,
            )
            assertEquals(listOf(1L, 2L), repo.plans.first().map { it.id })
        }
}
