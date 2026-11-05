package io.github.rupakc.travelplanner.core.domain.search

import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.model.search.CompareEase
import io.github.rupakc.travelplanner.core.model.search.CompareEstimate
import io.github.rupakc.travelplanner.core.model.search.CompareFlight
import io.github.rupakc.travelplanner.core.model.search.CompareHotel
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.CompareWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Port of frontend/src/utils/compare.test.js. */
class CompareTest {
    private val base =
        SearchForm(
            origin = "NYC",
            departureDate = "2026-04-01",
            returnDate = "2026-04-08",
            nationality = "American",
            interests = listOf("food"),
            budgetUsd = "3000",
            travellers = TravellerCounts(adults = 2, children = 1),
        )

    @Test
    fun `shares fields and differs only in destination`() {
        val reqs = buildCompareRequests(base, listOf("Tokyo", "Paris"))
        assertEquals(listOf("Tokyo", "Paris"), reqs.map { it.destination })
        assertEquals(reqs[0].copy(destination = ""), reqs[1].copy(destination = ""))
        with(reqs[0]) {
            assertEquals(3, numTravelers)
            assertEquals(3000.0, budgetUsd)
            assertNull(destinations)
            assertNull(destinationNights)
            assertEquals("NYC", origin)
        }
    }

    @Test
    fun `drops blanks and case-insensitive duplicates, trims`() {
        val reqs = buildCompareRequests(base, listOf("Tokyo", "  ", "tokyo ", "Paris", ""))
        assertEquals(listOf("Tokyo", "Paris"), reqs.map { it.destination })
    }

    @Test
    fun `empty budget becomes null`() {
        assertNull(buildCompareRequests(base.copy(budgetUsd = ""), listOf("Rome"))[0].budgetUsd)
    }

    @Test
    fun `blank return date becomes null (one-way), set one is kept`() {
        assertNull(buildCompareRequests(base.copy(returnDate = ""), listOf("Rome"))[0].returnDate)
        assertEquals("2026-04-08", buildCompareRequests(base, listOf("Rome"))[0].returnDate)
    }

    private fun s(
        estimate: CompareEstimate? = CompareEstimate(totalUsd = 1000.0),
        flight: CompareFlight? = CompareFlight(priceUsd = 500.0),
        weather: CompareWeather? = CompareWeather(poorDays = 2),
        ease: CompareEase? = CompareEase(total = 80, safety = 15, english = 10),
        daily: Double? = 90.0,
    ) = CompareSummary(
        estimate = estimate,
        flight = flight,
        hotel = CompareHotel(typicalNightlyUsd = 100.0),
        weather = weather,
        ease = ease,
        dailyCostUsd = daily,
    )

    @Test
    fun `picks lowest for costs and highest for scores`() {
        val best =
            bestByRow(
                listOf(
                    s(),
                    s(
                        estimate = CompareEstimate(totalUsd = 800.0),
                        ease = CompareEase(total = 90, safety = 12, english = 10),
                        weather = CompareWeather(poorDays = 0),
                    ),
                ),
            )
        assertEquals(setOf(1), best[CompareRow.ESTIMATE])
        assertEquals(setOf(1), best[CompareRow.WEATHER])
        assertEquals(setOf(1), best[CompareRow.EASE])
        assertEquals(setOf(0), best[CompareRow.SAFETY])
    }

    @Test
    fun `marks ties`() {
        assertEquals(setOf(0, 1), bestByRow(listOf(s(), s()))[CompareRow.ENGLISH])
    }

    @Test
    fun `skips rows with fewer than two values and ignores junk or null columns`() {
        // Junk numbers decode to null (LenientDouble), the same as the web's non-number check.
        val best = bestByRow(listOf(s(), null, s(flight = null, daily = null)))
        assertFalse(CompareRow.FLIGHT in best)
        assertFalse(CompareRow.DAILY in best)
        assertTrue(CompareRow.ESTIMATE in best)
        assertFalse(CompareRow.VISA in best)
    }

    @Test
    fun `never awards Best cost to a partial estimate`() {
        val best =
            bestByRow(
                listOf(
                    s(estimate = CompareEstimate(totalUsd = 400.0, partial = true)),
                    s(estimate = CompareEstimate(totalUsd = 1000.0, partial = false)),
                    s(estimate = CompareEstimate(totalUsd = 1200.0, partial = false)),
                ),
            )
        assertEquals(setOf(1), best[CompareRow.ESTIMATE])
        val onlyOneComplete =
            bestByRow(
                listOf(
                    s(estimate = CompareEstimate(totalUsd = 400.0, partial = true)),
                    s(estimate = CompareEstimate(totalUsd = 1000.0, partial = false)),
                ),
            )
        assertNull(onlyOneComplete[CompareRow.ESTIMATE])
    }

    @Test
    fun `uses a string detail`() {
        assertEquals(
            CompareErrorReason.Detail("Compare supports single destinations only"),
            compareErrorReason(AppError.Client(422, "Compare supports single destinations only")),
        )
        assertEquals(CompareErrorReason.Detail("All sources failed"), compareErrorReason(AppError.Server(502, "All sources failed")))
    }

    @Test
    fun `generic text for non-string 4xx detail`() {
        // What HttpErrorMapper produces for a FastAPI 422 list: the first field message in `message` + fieldErrors.
        val validation =
            AppError.Client(
                422,
                "Value error, bad date",
                fieldErrors = listOf(AppError.FieldError(listOf("body", "return_date"), "Value error, bad date", "value_error")),
            )
        assertEquals(CompareErrorReason.CheckDetails, compareErrorReason(validation))
        assertEquals(CompareErrorReason.CheckDetails, compareErrorReason(AppError.Client(400, null)))
        // A structured {code, message} detail is not a string detail on the web either.
        assertEquals(CompareErrorReason.CheckDetails, compareErrorReason(AppError.Client(422, "m", code = "x")))
    }

    @Test
    fun `generic text for 5xx and network errors`() {
        assertEquals(CompareErrorReason.Unavailable, compareErrorReason(AppError.Server(500, null)))
        assertEquals(CompareErrorReason.Unavailable, compareErrorReason(AppError.Network()))
        assertEquals(CompareErrorReason.Unavailable, compareErrorReason(null))
        // Exception text from decoding / unknown failures is never shown.
        assertEquals(CompareErrorReason.Unavailable, compareErrorReason(AppError.Decoding("Unexpected JSON token at offset 3")))
        assertEquals(CompareErrorReason.Unavailable, compareErrorReason(AppError.Unknown("java.lang.IllegalStateException")))
    }

    @Test
    fun `backend detail is only a plain string detail`() {
        assertEquals("Discovery timed out — please try again", backendDetail(AppError.Server(504, " Discovery timed out — please try again ")))
        assertEquals(
            "Too many requests — please wait 5 seconds and try again.",
            backendDetail(AppError.RateLimited("Too many requests — please wait 5 seconds and try again.", 5)),
        )
        assertNull(backendDetail(AppError.Decoding("Unexpected JSON token")))
        assertNull(backendDetail(AppError.Network("timeout")))
        assertNull(backendDetail(AppError.Server(502, "m", code = "extract_failed")))
        assertNull(backendDetail(AppError.Client(422, "msg", fieldErrors = listOf(AppError.FieldError(emptyList(), "msg", null)))))
    }

    @Test
    fun `format values`() {
        assertEquals("$1,234", fmtUsd(1234.4))
        assertEquals("—", fmtUsd(null))
        assertEquals("2h 5m", fmtDuration(125))
        assertNull(fmtDuration(null))
        assertNull(fmtDuration(0))
    }
}
