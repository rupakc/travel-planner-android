package io.github.rupakc.travelplanner.feature.home.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.data.place.PlaceNameLookup
import io.github.rupakc.travelplanner.core.data.profile.ProfileFeatureVisibility
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchKind
import io.github.rupakc.travelplanner.core.domain.globalsearch.SettingsEntry
import io.github.rupakc.travelplanner.core.model.navigation.ChatRoute
import io.github.rupakc.travelplanner.core.model.navigation.NearbyRoute
import io.github.rupakc.travelplanner.core.model.navigation.PlanDetailRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.SettingsEntryRoute
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeChatRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeProfileRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeRecentSearchesRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakeSearchRepository
import io.github.rupakc.travelplanner.feature.home.FakeFeatureCatalogue
import io.github.rupakc.travelplanner.feature.home.plan
import io.github.rupakc.travelplanner.feature.home.recent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Plain labels: English-like text built from ids. */
internal object FakeLabels : SearchLabels {
    override fun featureTitle(feature: FeatureId) = feature.id.replace('_', ' ')

    override fun featureDescription(feature: FeatureId) = "feature"

    override fun settingTitle(entry: SettingsEntry) = entry.key.replace('_', ' ')

    override fun settingSection(entry: SettingsEntry) = "settings"

    override fun settingKeywords(entry: SettingsEntry) = if (entry == SettingsEntry.THEME) listOf("dark mode") else emptyList()
}

class GlobalSearchViewModelTest {
    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    @get:Rule val main = MainDispatcherRule()

    private var plans = FakePlansRepository(listOf(plan(7, "Spring break", "Tokyo", "2027-04-01")))
    private val chat = FakeChatRepository()
    private val recentRepo = FakeRecentSearchesRepository()
    private val profile = FakeProfileRepository()
    private val handle = SavedStateHandle()
    private val search = FakeSearchRepository()

    private fun vm() =
        GlobalSearchViewModel(
            handle,
            plans,
            chat,
            recentRepo,
            profile,
            ProfileFeatureVisibility(profile, FakeFeatureCatalogue(setOf(FeatureId.CHAT))),
            FakeLabels,
            TestAppDispatchers(main.testDispatcher),
            PlaceNameLookup(search),
        )

    @Test
    fun `trips and recent searches show city names and match the city or the code`() =
        runTest {
            search.airports = listOf(Airport(iataCode = "LIS", city = "Lisbon"))
            plans = FakePlansRepository(listOf(plan(9, "", "LIS", "2027-04-01")))
            recentRepo.add(recent("y", "LIS").search)
            val model = vm()
            model.state.test {
                model.onEvent(GlobalSearchEvent.QueryChanged("lisbon"))
                settle()
                val hits = expectMostRecentItem().results.groups.flatMap { it.hits }.map { it.item }
                assertEquals("Lisbon", hits.first { it.kind == GlobalSearchKind.TRIP }.title)
                assertEquals("New York → Lisbon", hits.first { it.kind == GlobalSearchKind.RECENT_SEARCH }.title)
                model.onEvent(GlobalSearchEvent.QueryChanged("LIS"))
                settle()
                assertTrue(expectMostRecentItem().results.groups.flatMap { it.hits }.any { it.item.kind == GlobalSearchKind.TRIP })
            }
        }

    @Test
    fun `indexes trips, chat content, recent searches, features and settings`() =
        runTest {
            val thread = chat.createThread("Food questions")
            chat.appendMessage(thread, "user", "Best ramen in Tokyo?")
            recentRepo.add(recent("x", "Tokyo").search)
            val model = vm()
            model.state.test {
                model.onEvent(GlobalSearchEvent.QueryChanged("tokyo"))
                settle()
                val s = expectMostRecentItem()
                assertEquals("tokyo", s.query)
                assertEquals(mapOf(GlobalSearchKind.TRIP to 1, GlobalSearchKind.CHAT to 1, GlobalSearchKind.RECENT_SEARCH to 1), s.results.counts)
                model.onEvent(GlobalSearchEvent.QueryChanged("dark"))
                settle()
                assertEquals("theme", expectMostRecentItem().results.groups.single().hits.single().item.id)
            }
        }

    @Test
    fun `filter chips toggle and survive in saved state`() =
        runTest {
            val thread = chat.createThread("Tokyo plan")
            chat.appendMessage(thread, "user", "hi")
            val model = vm()
            model.state.test {
                model.onEvent(GlobalSearchEvent.QueryChanged("tokyo"))
                model.onEvent(GlobalSearchEvent.ToggleFilter(GlobalSearchKind.CHAT))
                settle()
                val s = expectMostRecentItem()
                assertEquals(setOf(GlobalSearchKind.CHAT), s.filter)
                assertEquals(listOf(GlobalSearchKind.CHAT), s.results.groups.map { it.kind })
                assertEquals("tokyo", handle.get<String>("global_search_query"))
                model.onEvent(GlobalSearchEvent.ClearFilter)
                settle()
                assertTrue(expectMostRecentItem().filter.isEmpty())
            }
        }

    @Test
    fun `opening results navigates, and a hidden feature is turned on first`() =
        runTest {
            val model = vm()
            model.state.test {
                model.onEvent(GlobalSearchEvent.QueryChanged("near"))
                settle()
                val s = expectMostRecentItem()
                assertTrue("near_me" in s.hiddenFeatures)
                val nearMe = s.results.groups.flatMap { it.hits }.first { it.item.id == "near_me" }.item
                model.navigation.test {
                    model.onEvent(GlobalSearchEvent.Open(nearMe))
                    assertEquals(NearbyRoute, awaitItem())
                    assertEquals(setOf("near_me"), profile.overridesState.value.enabled)
                    model.onEvent(
                        GlobalSearchEvent.Open(io.github.rupakc.travelplanner.core.domain.globalsearch.SearchableItem(GlobalSearchKind.TRIP, "7", "t")),
                    )
                    assertEquals(PlanDetailRoute(7), awaitItem())
                    model.onEvent(
                        GlobalSearchEvent.Open(io.github.rupakc.travelplanner.core.domain.globalsearch.SearchableItem(GlobalSearchKind.CHAT, "3", "c")),
                    )
                    assertEquals(ChatRoute(3), awaitItem())
                    model.onEvent(
                        GlobalSearchEvent.Open(
                            io.github.rupakc.travelplanner.core.domain.globalsearch.SearchableItem(GlobalSearchKind.RECENT_SEARCH, "rs-1", "r"),
                        ),
                    )
                    assertEquals(SearchRoute(recentSearchId = "rs-1"), awaitItem())
                    model.onEvent(
                        GlobalSearchEvent.Open(
                            io.github.rupakc.travelplanner.core.domain.globalsearch.SearchableItem(GlobalSearchKind.SETTING, "theme", "Theme"),
                        ),
                    )
                    assertEquals(SettingsEntryRoute("theme"), awaitItem())
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
}
