package io.github.rupakc.travelplanner.feature.share

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.json.TravelJson
import io.github.rupakc.travelplanner.core.model.plan.SharedPlan
import io.github.rupakc.travelplanner.core.model.settings.Session
import io.github.rupakc.travelplanner.core.testing.MainDispatcherRule
import io.github.rupakc.travelplanner.core.testing.TestAppDispatchers
import io.github.rupakc.travelplanner.core.testing.fake.FakeAuthRepository
import io.github.rupakc.travelplanner.core.testing.fake.FakePlansRepository
import io.github.rupakc.travelplanner.core.testing.fake.TestUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal val sharedFixture: SharedPlan =
    TravelJson.decodeFromString(
        SharedPlan.serializer(),
        """{"name":"Japan","search_data":{"origin":"NYC","destination":"Tokyo","departure_date":"2027-04-01","return_date":"2027-04-05",
        "num_travelers":2,"interests":["food"]},
        "selections":{"hotel":{"name":"Park Hyatt","price_per_night_usd":100},
        "flights":[{"leg_index":0,"price_usd":300,"leg_from":"New York, USA","leg_to":"Seoul, Korea","outbound":{"airline":"KE","flight_number":"KE82"}},
                   {"leg_index":1,"price_usd":120,"leg_from":"Seoul","leg_to":"Tokyo","outbound":{"airline":"JL"}}],
        "itinerary":{"days":[{"day_number":1,"date":"2027-04-01","slots":[{"time_of_day":"morning","activity":"Market","duration_hours":2}]}]},
        "itinerary_notes":{"1-morning":"Go early"}}}""",
    )

@OptIn(ExperimentalCoroutinesApi::class)
class ShareLogicTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `parses app and web share links, rejects others`() {
        assertEquals("abc_DEF-123", ShareLinks.tokenFrom("travelplanner://share/abc_DEF-123"))
        assertEquals("tok", ShareLinks.tokenFrom("https://voyager.example/share/tok"))
        assertEquals("tok", ShareLinks.tokenFrom("http://10.0.2.2:5174/app/share/tok?x=1"))
        assertNull(ShareLinks.tokenFrom("https://voyager.example/plans/tok"))
        assertNull(ShareLinks.tokenFrom("travelplanner://plan/12"))
        assertNull(ShareLinks.tokenFrom("javascript:alert(1)"))
        assertNull(ShareLinks.tokenFrom("travelplanner://share/bad%20token"))
        assertNull(ShareLinks.tokenFrom(null))
    }

    @Test
    fun `shared trip mirrors the web share page`() {
        val trip = SharedTrip.from(sharedFixture)
        assertEquals("Tokyo", trip.destination)
        assertEquals(2, trip.travellers)
        // 300 + 120 flights + 100 x 4 nights
        assertEquals(820.0, trip.plannedUsd, 0.0)
        assertEquals(listOf(FlightTag.Leg(1), FlightTag.Leg(2)), trip.flights.map { it.tag })
        assertEquals("New York → Seoul", trip.flights[0].route)
        assertEquals(420.0, trip.legsTotalUsd!!, 0.0)
        assertTrue(trip.fullItinerary)
        val slot = trip.itinerary.single().slots.single()
        assertEquals("09:00–11:00", slot.timeRange)
        assertEquals("Go early", slot.note)
    }

    private fun vm(token: String?, repo: FakePlansRepository, signedIn: Boolean) =
        ShareViewModel(
            SavedStateHandle(mapOf(ShareViewModel.TOKEN_ARG to token)),
            repo,
            FakeAuthRepository(if (signedIn) Session("t", TestUser) else null),
            TestAppDispatchers(main.testDispatcher),
        )

    @Test
    fun `loads signed out without save a copy`() =
        runTest {
            val repo = FakePlansRepository().apply { sharedPlans["tok"] = sharedFixture }
            vm("tok", repo, signedIn = false).state.test {
                val s = expectMostRecentItem()
                assertEquals("Japan", s.trip!!.name)
                assertFalse(s.canSaveCopy)
            }
        }

    @Test
    fun `unknown and malformed tokens show unavailable`() =
        runTest {
            vm("missing", FakePlansRepository(), signedIn = false).state.test {
                assertTrue(expectMostRecentItem().error is AppError.NotFound)
            }
            vm("../etc", FakePlansRepository(), signedIn = false).state.test {
                assertTrue(expectMostRecentItem().error is AppError.NotFound)
            }
        }

    @Test
    fun `save a copy creates a plan with the shared data`() =
        runTest {
            val repo = FakePlansRepository().apply { sharedPlans["tok"] = sharedFixture }
            val vm = vm("tok", repo, signedIn = true)
            vm.state.test {
                assertTrue(expectMostRecentItem().canSaveCopy)
                vm.onEvent(ShareEvent.SaveCopy)
                assertEquals(ShareMessage.Saved(1), expectMostRecentItem().message)
            }
            val saved = repo.plans.first().single()
            assertEquals("Japan", saved.name)
            assertEquals("Park Hyatt", saved.selections.hotel?.name)
            assertEquals("Tokyo", saved.searchRequest?.destination ?: (saved.searchData["destination"] as kotlinx.serialization.json.JsonPrimitive).content)
        }
}
