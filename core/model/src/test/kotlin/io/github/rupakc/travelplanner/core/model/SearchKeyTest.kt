package io.github.rupakc.travelplanner.core.model

import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.search.searchKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SearchKeyTest {
    private val base =
        TravelSearchRequest(
            origin = "NYC",
            destination = "Tokyo",
            departureDate = "2026-04-01",
            nationality = "American",
            interests = listOf("food", "history"),
        )

    @Test
    fun `equivalent requests share a key`() {
        val same = base.copy(origin = " nyc ", destination = "TOKYO", interests = listOf("history", "food", "food"))
        assertEquals(base.searchKey(), same.searchKey())
    }

    @Test
    fun `a different search gets a different key`() {
        assertNotEquals(base.searchKey(), base.copy(destination = "Osaka").searchKey())
        assertNotEquals(base.searchKey(), base.copy(returnDate = "2026-04-08").searchKey())
        assertNotEquals(base.searchKey(), base.copy(numTravelers = 2).searchKey())
    }

    @Test
    fun `empty must_include and a single-city destinations list equal their absence`() {
        assertEquals(base.searchKey(), base.copy(mustInclude = emptyList()).searchKey())
        assertEquals(base.searchKey(), base.copy(destinations = listOf("Tokyo"), destinationNights = listOf(7)).searchKey())
        assertNotEquals(base.searchKey(), base.copy(destinations = listOf("Kyoto", "Tokyo")).searchKey())
    }
}
