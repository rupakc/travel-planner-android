package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.preferences.RecentStop
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of frontend/src/utils/recentSearches.test.js. */
class RecentSearchesTest {
    private fun searchData(
        destinations: List<String>? = null,
        nights: List<Int?>? = null,
        budget: Double? = 3000.0,
        returnDate: String? = "2027-04-08",
    ) = TravelSearchRequest(
        origin = "New York, USA (JFK)",
        destination = "Tokyo, Japan (HND)",
        departureDate = "2027-04-01",
        returnDate = returnDate,
        nationality = "American",
        residencePermits = listOf("Schengen"),
        interests = listOf("food", "history"),
        budgetUsd = budget,
        numTravelers = 3,
        adults = 2,
        children = 1,
        serendipity = 0.7,
        destinations = destinations,
        destinationNights = nights,
    )

    @Test
    fun `captures every field needed to refill the form`() {
        assertEquals(
            RecentSearchIn(
                origin = "New York, USA (JFK)",
                destination = "Tokyo, Japan (HND)",
                departureDate = "2027-04-01",
                returnDate = "2027-04-08",
                stops = emptyList(),
                nationality = "American",
                residencePermits = listOf("Schengen"),
                existingVisas = emptyList(),
                interests = listOf("food", "history"),
                budgetUsd = 3000.0,
                adults = 2,
                children = 1,
                seniors = 0,
                infants = 0,
                accessibilityNeeds = emptyList(),
                serendipity = 0.7,
            ),
            toRecentSearch(searchData()),
        )
    }

    @Test
    fun `turns the multi-city journey into stops, without the final destination`() {
        val sd = searchData(listOf("Seoul, South Korea", "Osaka, Japan", "Tokyo, Japan (HND)"), listOf(2, null, null))
        assertEquals(listOf(RecentStop("Seoul, South Korea", 2), RecentStop("Osaka, Japan", null)), toRecentSearch(sd).stops)
    }

    @Test
    fun `tolerates missing nights for the stops`() {
        val sd = searchData(listOf("Seoul, South Korea", "Tokyo, Japan (HND)"), null)
        assertEquals(listOf(RecentStop("Seoul, South Korea", null)), toRecentSearch(sd).stops)
    }

    @Test
    fun `sends an empty budget or return date as null`() {
        val saved = toRecentSearch(searchData(budget = null, returnDate = ""))
        assertNull(saved.budgetUsd)
        assertNull(saved.returnDate)
    }

    private val saved = toRecentSearch(searchData(listOf("Seoul, South Korea", "Tokyo, Japan (HND)"), listOf(2, null)))

    @Test
    fun `restores the form and stops exactly when the dates are still ahead`() {
        val restored = recentSearchToForm(saved, LocalDate.parse("2027-01-15"))
        assertFalse(restored.datesShifted)
        assertEquals(
            SearchForm(
                origin = "New York, USA (JFK)",
                destination = "Tokyo, Japan (HND)",
                departureDate = "2027-04-01",
                returnDate = "2027-04-08",
                nationality = "American",
                residencePermits = listOf("Schengen"),
                interests = listOf("food", "history"),
                budgetUsd = "3000",
                travellers = TravellerCounts(adults = 2, children = 1),
                serendipity = 0.7,
            ),
            restored.form,
        )
        assertEquals(3, restored.form.travellers.total)
        assertEquals(listOf(ExtraStop("Seoul, South Korea", "2")), restored.stops)
    }

    @Test
    fun `a departure of today still counts as ahead`() {
        assertFalse(recentSearchToForm(saved, LocalDate.parse("2027-04-01")).datesShifted)
    }

    @Test
    fun `moves past dates a week out and keeps the trip length`() {
        val restored = recentSearchToForm(saved, LocalDate.parse("2027-06-28"))
        assertTrue(restored.datesShifted)
        assertEquals("2027-07-05", restored.form.departureDate)
        assertEquals("2027-07-12", restored.form.returnDate)
    }

    @Test
    fun `shifts a one-way search without inventing a return date`() {
        val restored = recentSearchToForm(saved.copy(returnDate = null), LocalDate.parse("2027-06-28"))
        assertEquals("2027-07-05", restored.form.departureDate)
        assertEquals("", restored.form.returnDate)
    }

    @Test
    fun `fills defaults for fields missing from older entries`() {
        val minimal = RecentSearchIn(origin = "A", destination = "B", departureDate = "2027-04-01")
        val restored = recentSearchToForm(minimal, LocalDate.parse("2027-01-01"))
        assertEquals(emptyList<String>(), restored.form.interests)
        assertEquals(1, restored.form.travellers.adults)
        assertEquals(1, restored.form.travellers.total)
        assertEquals("", restored.form.budgetUsd)
        assertEquals(0.5, restored.form.serendipity, 0.0)
        assertEquals(emptyList<ExtraStop>(), restored.stops)
    }

    @Test
    fun `summarises route, dates, interests and travellers`() {
        assertEquals(
            RecentSearchSummary(route = "New York → Seoul → Tokyo", dates = "Apr 1 – Apr 8, 2027", interests = listOf("food", "history"), travellers = 3),
            describeRecentSearch(saved),
        )
    }

    @Test
    fun `handles one-way trips and dates across years`() {
        assertEquals("Dec 30, 2027", describeRecentSearch(RecentSearchIn("A", "B", "2027-12-30")).dates)
        assertEquals("Dec 30, 2027 – Jan 4, 2028", describeRecentSearch(RecentSearchIn("A", "B", "2027-12-30", "2028-01-04")).dates)
    }

    @Test
    fun `other locales use their own date order`() {
        val es = java.util.Locale.forLanguageTag("es")
        assertEquals(
            "1 abr 2027 – 8 abr 2027",
            describeRecentSearch(RecentSearchIn("A", "B", "2027-04-01", "2027-04-08"), es).dates.replace(".", ""),
        )
    }

    @Test
    fun `round-trips a submitted request back into the same form`() {
        val form =
            SearchForm(
                origin = "JFK",
                destination = "HND",
                departureDate = "2027-04-01",
                returnDate = "2027-04-08",
                nationality = "American",
                budgetUsd = "2500",
                travellers = TravellerCounts(adults = 1, seniors = 1),
            )
        val stops = listOf(ExtraStop("Seoul", "2"))
        val restored = recentSearchToForm(toRecentSearch(buildSearchRequest(form, stops)), LocalDate.parse("2027-01-01"))
        assertEquals(form, restored.form)
        assertEquals(stops, restored.stops)
    }

    @Test
    fun `dates passed only before today`() {
        assertTrue(recentDatesPassed(RecentSearchIn("A", "B", "2027-03-31"), LocalDate.parse("2027-04-01")))
        assertFalse(recentDatesPassed(RecentSearchIn("A", "B", "2027-04-01"), LocalDate.parse("2027-04-01")))
    }
}
