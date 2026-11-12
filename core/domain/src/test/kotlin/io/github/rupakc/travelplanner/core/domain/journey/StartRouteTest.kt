package io.github.rupakc.travelplanner.core.domain.journey

import io.github.rupakc.travelplanner.core.domain.server.JourneyFlags
import org.junit.Assert.assertEquals
import org.junit.Test

class StartRouteTest {
    private val on = JourneyFlags(trips = true, journey = true)

    private data class Case(val name: String, val flags: JourneyFlags, val signedIn: Boolean, val trips: Int?, val expected: StartRoute)

    @Test
    fun startRouteTable() {
        val cases =
            listOf(
                Case("journey on, signed in, trips", on, true, 3, StartRoute.Trips),
                Case("journey on, signed in, one trip", on, true, 1, StartRoute.Trips),
                Case("journey on, signed in, zero trips", on, true, 0, StartRoute.Explore),
                Case("journey on, signed in, trips fail to load", on, true, null, StartRoute.Explore),
                Case("journey on, signed out, has cached count", on, false, 4, StartRoute.Explore),
                Case("journey on, signed out", on, false, null, StartRoute.Explore),
                Case("trips api without journey ui", JourneyFlags(trips = true), true, 5, StartRoute.Classic),
                Case("journey flag without trips api", JourneyFlags(journey = true), true, 5, StartRoute.Classic),
                Case("old backend", JourneyFlags.OFF, true, 5, StartRoute.Classic),
                Case("old backend signed out", JourneyFlags.OFF, false, null, StartRoute.Classic),
            )
        cases.forEach { assertEquals(it.name, it.expected, resolveStartRoute(it.flags, it.signedIn, it.trips)) }
    }

    @Test
    fun splashHoldsOnlyWhileSomethingIsUnknownAndWithinTwoSeconds() {
        assertEquals(true, StartGate.hold(configKnown = false, flags = on, signedIn = true, tripCountKnown = false, waitedMs = 100))
        assertEquals(false, StartGate.hold(configKnown = false, flags = on, signedIn = true, tripCountKnown = false, waitedMs = 2_000))
        assertEquals(true, StartGate.hold(configKnown = true, flags = on, signedIn = true, tripCountKnown = false, waitedMs = 500))
        assertEquals(false, StartGate.hold(configKnown = true, flags = on, signedIn = true, tripCountKnown = true, waitedMs = 0))
        assertEquals(false, StartGate.hold(configKnown = true, flags = on, signedIn = false, tripCountKnown = false, waitedMs = 0))
        assertEquals(false, StartGate.hold(configKnown = true, flags = JourneyFlags.OFF, signedIn = true, tripCountKnown = false, waitedMs = 0))
    }
}
