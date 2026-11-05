package io.github.rupakc.travelplanner.feature.home

import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.common.AppResult
import io.github.rupakc.travelplanner.core.data.profile.ProfileFeatureVisibility
import io.github.rupakc.travelplanner.core.domain.feature.FeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.DefaultFeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.navigation.AlertsRoute
import io.github.rupakc.travelplanner.core.model.navigation.GlobalSearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsEntryRoute
import io.github.rupakc.travelplanner.core.model.preferences.TasteProfile
import io.github.rupakc.travelplanner.core.model.preferences.TasteSignal
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.model.settings.TravellerProfile
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeFeatureVisibility
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePreferencesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeRecentSearchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val auth = FakeAuthRepository(Session("t", TestUser))
    private val profile =
        FakeProfileRepository(TravellerProfile(onboardingCompleted = true, primaryType = "CULTURE_FOODIE", secondaryTypes = listOf("ACCESSIBLE")))
    private val plans = FakePlansRepository(listOf(plan(7, "Tokyo spring", "Tokyo", "2027-04-01", "2027-04-08")))
    private val recent = FakeRecentSearchesRepository()
    private val prefs = FakePreferencesRepository()

    private fun vm(catalogue: FeatureCatalogue = FakeFeatureCatalogue(setOf(FeatureId.CHAT, FeatureId.COMPARE, FeatureId.BUDGET_PLANNER))) =
        HomeViewModel(auth, profile, plans, recent, prefs, catalogue, ProfileFeatureVisibility(profile, catalogue), FixedClock(LocalDate.of(2027, 3, 10)))

    @Test
    fun `state combines user, type, trip and visible tiles`() =
        runTest {
            vm().state.test {
                val s = expectMostRecentItem()
                assertEquals("alice", s.userName)
                assertEquals(listOf(TravellerType.CULTURE_FOODIE, TravellerType.ACCESSIBLE), s.travellerTypes)
                assertEquals(7L, s.upcomingTrip?.planId)
                assertEquals(listOf(FeatureId.BUDGET_PLANNER, FeatureId.COMPARE, FeatureId.CHAT).toSet(), s.tiles.map { it.feature }.toSet())
                assertEquals(false, s.refreshing)
            }
        }

    @Test
    fun `overrides hide and show tiles`() =
        runTest {
            val model = vm()
            model.state.test {
                expectMostRecentItem()
                profile.setFeature(FeatureId.CHAT.id, false)
                profile.setFeature(FeatureId.NEAR_ME.id, true)
                val tiles = expectMostRecentItem().tiles.map { it.feature }
                assertTrue(FeatureId.NEAR_ME in tiles)
                assertTrue(FeatureId.CHAT !in tiles)
            }
        }

    @Test
    fun `recent searches and taste personalise the order`() =
        runTest {
            prefs.taste = AppResult.Success(TasteProfile(signals = mapOf("interest" to listOf(TasteSignal("food", 4)))))
            recent.add(recent("x", "Lisbon", budget = 1200.0).search)
            val model = vm()
            model.state.test {
                val s = expectMostRecentItem()
                assertEquals(FeatureId.BUDGET_PLANNER, s.tiles.first().feature)
                assertEquals(TileReason.SEARCHED_WITH_BUDGET, s.tiles.first().reason)
                assertEquals("Lisbon", s.becauseYouSearched?.destination)
                assertEquals(listOf("food"), s.tasteHighlights)
                assertEquals(1, s.recentSearches.size)
            }
        }

    @Test
    fun `a user who skipped the quiz gets every feature tile except the ones they turned off`() =
        runTest {
            profile.profileState.value = TravellerProfile()
            profile.setFeature(FeatureId.EVENTS.id, false)
            vm(catalogue = DefaultFeatureCatalogue).state.test {
                val tiles = expectMostRecentItem().tiles.map { it.feature }.toSet()
                assertEquals(FeatureId.entries.toSet() - FeatureId.EVENTS, tiles)
            }
        }

    @Test
    fun `refresh failure keeps cached data and can be dismissed`() =
        runTest {
            plans.failNext = AppError.Network()
            val failingRecent =
                object : io.github.rupakc.travelplanner.core.data.repository.RecentSearchesRepository by recent {
                    override suspend fun refresh(): AppResult<Unit> = AppResult.Failure(AppError.Network())
                }
            val model =
                HomeViewModel(
                    auth,
                    profile,
                    plans,
                    failingRecent,
                    prefs,
                    DefaultFeatureCatalogue,
                    FakeFeatureVisibility(),
                    FixedClock(LocalDate.of(2027, 3, 10)),
                )
            model.state.test {
                val s = expectMostRecentItem()
                assertEquals(AppError.Network(), s.refreshError)
                assertEquals(7L, s.upcomingTrip?.planId)
                model.onEvent(HomeEvent.DismissError)
                assertNull(expectMostRecentItem().refreshError)
            }
        }

    @Test
    fun `navigation events map to routes`() {
        assertEquals(GlobalSearchRoute, HomeEvent.OpenSearch.destination(null))
        assertEquals(SearchRoute(), HomeEvent.PlanTrip.destination(null))
        assertEquals(SettingsEntryRoute("traveller_type"), HomeEvent.OpenTravellerType.destination(null))
        assertEquals(PlanDetailRoute(3), HomeEvent.OpenTrip(3).destination(null))
        assertEquals(SearchRoute(recentSearchId = "rs-1"), HomeEvent.OpenRecentSearch("rs-1").destination(null))
        assertEquals(AlertsRoute(), HomeEvent.OpenFeature(FeatureId.DEAL_ALERTS).destination(null))
        assertEquals(SearchRoute(mode = "surprise"), HomeEvent.OpenFeature(FeatureId.SURPRISE_ME).destination(null))
        assertEquals(PlanDetailRoute(5), HomeEvent.OpenFeature(FeatureId.PACKING_LIST).destination(5))
        assertEquals(SearchRoute(), HomeEvent.OpenFeature(FeatureId.PACKING_LIST).destination(null))
        assertNull(HomeEvent.Refresh.destination(null))
    }
}
