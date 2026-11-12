package io.github.rupakc.travelplanner

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rupakc.travelplanner.core.domain.journey.StartRoute
import io.github.rupakc.travelplanner.core.domain.journey.resolveStartRoute
import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import io.github.rupakc.travelplanner.core.model.navigation.HomeRoute
import io.github.rupakc.travelplanner.core.model.navigation.LoginRoute
import io.github.rupakc.travelplanner.core.model.navigation.SearchRoute
import io.github.rupakc.travelplanner.core.model.navigation.TripsRoute
import io.github.rupakc.travelplanner.navigation.TopLevelDestination
import io.github.rupakc.travelplanner.navigation.startDestinationFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = android.app.Application::class)
class ShellTabsTest {
    private val on = JourneyFlags(trips = true, journey = true)

    @Test
    fun `journey on gives Trips Explore Alerts Play You, off gives the classic five`() {
        assertEquals(
            listOf(
                TopLevelDestination.TRIPS,
                TopLevelDestination.EXPLORE,
                TopLevelDestination.ALERTS,
                TopLevelDestination.PLAY,
                TopLevelDestination.YOU,
            ),
            TopLevelDestination.forJourney(true),
        )
        assertEquals(
            listOf(
                TopLevelDestination.HOME,
                TopLevelDestination.SEARCH,
                TopLevelDestination.CHAT,
                TopLevelDestination.PLANS,
                TopLevelDestination.ALERTS,
            ),
            TopLevelDestination.forJourney(false),
        )
    }

    @Test
    fun `every tab has a distinct telemetry id`() {
        val ids = TopLevelDestination.entries.map { it.telemetryId }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("nav.you", TopLevelDestination.YOU.telemetryId)
    }

    @Test
    fun `signed out sees Explore (never a trip count), signed in with trips starts on Trips`() {
        assertEquals(StartRoute.Explore, resolveStartRoute(on, signedIn = false, tripCount = null))
        assertEquals(StartRoute.Explore, resolveStartRoute(on, signedIn = true, tripCount = 0))
        assertEquals(StartRoute.Explore, resolveStartRoute(on, signedIn = true, tripCount = null))
        assertEquals(StartRoute.Trips, resolveStartRoute(on, signedIn = true, tripCount = 2))
        assertEquals(StartRoute.Classic, resolveStartRoute(JourneyFlags.OFF, signedIn = true, tripCount = 2))
    }

    @Test
    fun `the shell mode maps the start route to a destination`() {
        fun home(route: StartRoute) = MainUiState(flags = on, startRoute = route).shellMode().home
        assertEquals(TripsRoute, home(StartRoute.Trips))
        assertEquals(SearchRoute(), home(StartRoute.Explore))
        assertEquals(HomeRoute, MainUiState().shellMode().home)
        assertTrue(MainUiState(flags = on).shellMode().journey)
    }

    @Test
    fun `the gate starts where the home rule says once ready, and signed out still starts on Login`() {
        assertEquals(TripsRoute, startDestinationFor(AuthGate.READY, TripsRoute))
        assertEquals(HomeRoute, startDestinationFor(AuthGate.READY))
        assertEquals(LoginRoute, startDestinationFor(AuthGate.SIGNED_OUT, TripsRoute))
    }

    @Test
    fun `flags survive the cache encoding`() {
        val all = JourneyFlags(true, true, true, true, true, true, true)
        assertEquals(all, decode(encode(all)))
        assertEquals(JourneyFlags.OFF, decode(encode(JourneyFlags.OFF)))
        assertEquals(on, decode(encode(on)))
    }
}
