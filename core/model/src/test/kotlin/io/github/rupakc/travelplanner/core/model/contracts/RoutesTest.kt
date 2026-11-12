package io.github.rupakc.travelplanner.core.model.contracts

import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.CheckEmailRoute
import io.github.rupakc.travelplanner.core.model.navigation.ExpensesRoute
import io.github.rupakc.travelplanner.core.model.navigation.TripRoute
import io.github.rupakc.travelplanner.core.model.navigation.VerifyEmailRoute
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutesTest {
    private inline fun <reified T : AppRoute> roundTrip(route: T): T = Json.decodeFromString(serializer<T>(), Json.encodeToString(serializer<T>(), route))

    @Test
    fun `routes with arguments round-trip and optional arguments default to null`() {
        assertEquals(TripRoute(7, "prep"), roundTrip(TripRoute(7, "prep")))
        assertNull(roundTrip(TripRoute(7)).tab)
        assertEquals(CheckEmailRoute("a@b.c"), roundTrip(CheckEmailRoute("a@b.c")))
        assertNull(roundTrip(VerifyEmailRoute()).token)
        assertEquals(ExpensesRoute(3), roundTrip(ExpensesRoute(3)))
    }
}
