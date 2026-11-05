package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFormTest {
    private val today = LocalDate.parse("2026-11-05")
    private val filled =
        SearchForm.initial(today).copy(origin = "JFK", destination = "Tokyo", nationality = "American")

    @Test
    fun `initial dates are a week and two weeks out`() {
        val form = SearchForm.initial(today)
        assertEquals("2026-11-12", form.departureDate)
        assertEquals("2026-11-19", form.returnDate)
        assertEquals(TravellerCounts(), form.travellers)
    }

    @Test
    fun `builds the request like startSearch`() {
        val req =
            buildSearchRequest(
                filled.copy(budgetUsd = "2500", travellers = TravellerCounts(adults = 2, infants = 1), returnDate = ""),
                listOf(ExtraStop(" Seoul ", "2"), ExtraStop("  ", "3"), ExtraStop("Osaka", "")),
                Locale.US,
            )
        assertEquals(2500.0, req.budgetUsd)
        assertEquals(3, req.numTravelers)
        assertNull(req.returnDate)
        assertEquals(listOf("Seoul", "Osaka", "Tokyo"), req.destinations)
        assertEquals(listOf(2, null, null), req.destinationNights)
        assertTrue(req.isMultiCity)
    }

    @Test
    fun `single city has no destinations and a blank budget is null`() {
        val req = buildSearchRequest(filled, emptyList(), Locale.US)
        assertNull(req.destinations)
        assertNull(req.destinationNights)
        assertNull(req.budgetUsd)
        assertEquals("balanced", req.pace)
    }

    @Test
    fun `budget categories map both ways like the web`() {
        assertEquals("medium", usdToBudgetCategory("", Locale.US))
        assertEquals("low", usdToBudgetCategory("1500", Locale.US))
        assertEquals("medium", usdToBudgetCategory("5000", Locale.US))
        assertEquals("high", usdToBudgetCategory("5001", Locale.US))
        assertEquals("1000", budgetCategoryToUsd("low"))
        assertEquals("3000", budgetCategoryToUsd("medium"))
        assertEquals("8000", budgetCategoryToUsd("high"))
        assertEquals("", budgetCategoryToUsd("weird"))
    }

    @Test
    fun `preferences pre-fill non-empty values and keep the rest`() {
        val prefs =
            Preferences(
                budgetCategory = "low",
                nationality = "Indian",
                interests = listOf("food"),
                adults = 2,
                children = 1,
                accessibilityNeeds = listOf("wheelchair"),
            )
        val form = applyPreferences(filled.copy(existingVisas = listOf("US")), prefs)
        assertEquals("Indian", form.nationality)
        assertEquals(listOf("US"), form.existingVisas)
        assertEquals("1000", form.budgetUsd)
        assertEquals(TravellerCounts(adults = 2, children = 1), form.travellers)
        assertEquals(listOf("wheelchair"), form.accessibilityNeeds)
        assertEquals("American", applyPreferences(filled, Preferences()).nationality)
    }

    @Test
    fun `writes back the preference fields and keeps the others`() {
        val current = Preferences(username = "alice", currentResidence = "Munich, Germany")
        val form = filled.copy(budgetUsd = "9000", interests = listOf("art"), travellers = TravellerCounts(adults = 1, seniors = 2))
        val next = preferencesFromForm(current, form, Locale.US)
        assertEquals("alice", next.username)
        assertEquals("Munich, Germany", next.currentResidence)
        assertEquals("high", next.budgetCategory)
        assertEquals(3, next.numTravelers)
        assertEquals(2, next.seniors)
        assertEquals(listOf("art"), next.interests)
        assertTrue(preferencesChanged(current, form, Locale.US))
        assertFalse(preferencesChanged(next, form, Locale.US))
    }

    @Test
    fun `validation mirrors the web's required and min rules`() {
        assertEquals(emptyList<SearchFormIssue>(), validateSearchForm(filled, emptyList(), today, Locale.US))
        val empty = SearchForm.initial(today)
        assertEquals(
            listOf(SearchFormIssue.ORIGIN_REQUIRED, SearchFormIssue.DESTINATION_REQUIRED, SearchFormIssue.NATIONALITY_REQUIRED),
            validateSearchForm(empty, emptyList(), today, Locale.US),
        )
        assertEquals(
            listOf(SearchFormIssue.STOP_CITY_REQUIRED, SearchFormIssue.STOP_NIGHTS_OUT_OF_RANGE),
            validateSearchForm(filled, listOf(ExtraStop("", ""), ExtraStop("Rome", "31")), today, Locale.US),
        )
        assertEquals(listOf(SearchFormIssue.BUDGET_INVALID), validateSearchForm(filled.copy(budgetUsd = "-5"), emptyList(), today, Locale.US))
    }

    @Test
    fun `date rules`() {
        assertEquals(listOf(SearchFormIssue.DEPARTURE_REQUIRED), validateTripDates("", "", today))
        assertEquals(listOf(SearchFormIssue.DEPARTURE_IN_PAST), validateTripDates("2026-11-04", "", today))
        assertEquals(emptyList<SearchFormIssue>(), validateTripDates("2026-11-05", "2026-11-05", today))
        assertEquals(listOf(SearchFormIssue.RETURN_BEFORE_DEPARTURE), validateTripDates("2026-11-10", "2026-11-09", today))
    }

    @Test
    fun `serendipity buckets`() {
        assertEquals(SerendipityStyle.CLASSICS, serendipityStyle(0.3))
        assertEquals(SerendipityStyle.BALANCED, serendipityStyle(0.5))
        assertEquals(SerendipityStyle.BALANCED, serendipityStyle(0.66))
        assertEquals(SerendipityStyle.HIDDEN_GEMS, serendipityStyle(0.7))
    }

    @Test
    fun `traveller rules`() {
        val one = TravellerCounts()
        assertFalse(one.canDecrement(TravellerKind.ADULTS))
        assertEquals(one, one.change(TravellerKind.ADULTS, -1))
        assertEquals(one, one.change(TravellerKind.INFANTS, -1))
        val withChild = one.change(TravellerKind.CHILDREN, 1)
        assertEquals(2, withChild.total)
        assertTrue(withChild.canDecrement(TravellerKind.ADULTS))
        assertEquals(TravellerCounts(adults = 0, children = 1), withChild.change(TravellerKind.ADULTS, -1))
        val full = TravellerCounts(adults = 20)
        assertFalse(full.canIncrement(TravellerKind.INFANTS))
        assertNull(TravellerCounts.of(0, 0, 0, 0))
        assertNull(TravellerCounts.of(-1, 2, 0, 0))
    }

    @Test
    fun `discover helpers`() {
        assertFalse(canDiscover(SearchForm(origin = "JFK")))
        assertTrue(canDiscover(SearchForm(origin = "JFK", nationality = "American")))
        val discover = filled.copy(budgetUsd = "1200", interests = listOf("nature"))
        val known = SearchForm.initial(today).copy(existingVisas = listOf("US"))
        val picked = applyDiscoveredDestination(known, discover, DiscoveredDestination(city = "Lisbon", country = "Portugal"))
        assertEquals("Lisbon, Portugal", picked.destination)
        assertEquals("JFK", picked.origin)
        assertEquals(listOf("US"), picked.existingVisas)
        assertEquals("1200", picked.budgetUsd)
        val req = toDiscoveryRequest(discover, Locale.US)
        assertEquals(1200.0, req.budgetUsd)
        assertEquals(listOf("nature"), req.interests)
        assertEquals(VisaBadge.VISA_FREE, visaBadge("visa-free"))
        assertEquals(VisaBadge.VERIFY, visaBadge(null))
    }

    @Test
    fun `the budget is read in the user's language`() {
        val es = Locale.forLanguageTag("es-ES")
        assertEquals(1500.0, buildSearchRequest(filled.copy(budgetUsd = "1.500"), emptyList(), es).budgetUsd)
        assertEquals(1500.5, buildSearchRequest(filled.copy(budgetUsd = "1.500,50"), emptyList(), es).budgetUsd)
        assertEquals(1500.5, buildSearchRequest(filled.copy(budgetUsd = "1,500.50"), emptyList(), Locale.US).budgetUsd)
        assertEquals(emptyList<SearchFormIssue>(), validateSearchForm(filled.copy(budgetUsd = "1500,50"), emptyList(), today, es))
        assertEquals("medium", usdToBudgetCategory("1.600", es))
        assertEquals(1500.0, toDiscoveryRequest(filled.copy(budgetUsd = "1.500"), es).budgetUsd)
        assertEquals("medium", preferencesFromForm(Preferences(), filled.copy(budgetUsd = "3.000"), es).budgetCategory)
    }
}
