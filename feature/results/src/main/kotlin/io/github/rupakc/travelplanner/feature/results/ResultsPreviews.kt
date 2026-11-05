package io.github.rupakc.travelplanner.feature.results

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ConfidenceData
import io.github.rupakc.travelplanner.core.model.section.ConfidenceSubScore
import io.github.rupakc.travelplanner.core.model.section.Flight
import io.github.rupakc.travelplanner.core.model.section.FlightLayover
import io.github.rupakc.travelplanner.core.model.section.FlightLeg
import io.github.rupakc.travelplanner.core.model.section.FlightsData
import io.github.rupakc.travelplanner.core.model.section.Hotel
import io.github.rupakc.travelplanner.core.model.section.HotelsData
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.sections.NoOpSectionActions
import io.github.rupakc.travelplanner.feature.results.sections.ResultsSection

/** Sample data for previews only (never shown in the app). */
@Suppress("MagicNumber")
private object PreviewData {
    val request =
        TravelSearchRequest(
            origin = "JFK",
            destination = "Lisbon",
            departureDate = "2026-12-10",
            returnDate = "2026-12-17",
            nationality = "American",
            budgetUsd = 3000.0,
            numTravelers = 2,
        )

    val flights =
        FlightsData(
            results =
                listOf(
                    Flight(
                        priceUsd = 612.0,
                        tripType = "round_trip",
                        source = "google_flights",
                        bookingUrl = "https://www.google.com/travel/flights",
                        outbound =
                            FlightLeg("TAP", "TP202", "JFK", "LIS", null, "21:40", "09:55+1", 435, 1, listOf(FlightLayover("Ponta Delgada", "PDL", 6.0))),
                        returnLeg = FlightLeg("TAP", "TP201", "LIS", "JFK", null, "11:10", "14:05", 475, 0),
                    ),
                ),
        )

    val hotels =
        HotelsData(
            results =
                listOf(
                    Hotel(
                        name = "Memmo Alfama",
                        starRating = 4.0,
                        pricePerNightUsd = 210.0,
                        totalPriceUsd = 1470.0,
                        location = "Alfama",
                        amenities = listOf("Pool", "Wi-Fi", "Bar", "Terrace", "Gym"),
                        reviewScore = 9.1,
                        source = "booking.com",
                        linkVerified = true,
                    ),
                ),
        )

    fun state(streaming: Boolean) =
        ResultsUiState(
            request = request,
            phase = if (streaming) StreamPhase.STREAMING else StreamPhase.DONE,
            readyCount = if (streaming) 4 else 16,
            confidence =
                ConfidenceData(
                    total = 84,
                    grade = "green",
                    destination = "Lisbon, Portugal",
                    subScores = mapOf("safety" to ConfidenceSubScore(18, 20, "Safety", "Low crime in tourist areas")),
                ),
            sections =
                ResultsSection.entries.map { s ->
                    val data =
                        when (s) {
                            ResultsSection.Flights -> flights
                            ResultsSection.Hotels -> if (streaming) null else hotels
                            else -> null
                        }
                    SectionUi(s, if (data == null && streaming) SectionStatus.LOADING else SectionStatus.DONE, data, relevant = true, expanded = s.ordinal < 3)
                },
            plan = PlanSummary(count = 2, total = 2694.0),
        )
}

@Composable
private fun PreviewScreen(dark: Boolean, streaming: Boolean) {
    TravelTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
        ResultsScreen(PreviewData.state(streaming), onEvent = {}, actions = NoOpSectionActions, onBack = {}, onOpenMyPlan = {})
    }
}

@Preview(name = "Streaming · light", showBackground = true, heightDp = 1400)
@Composable
private fun StreamingLight() = PreviewScreen(dark = false, streaming = true)

@Preview(name = "Streaming · dark", showBackground = true, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StreamingDark() = PreviewScreen(dark = true, streaming = true)

@Preview(name = "Done · light", showBackground = true, heightDp = 1800)
@Composable
private fun DoneLight() = PreviewScreen(dark = false, streaming = false)

@Preview(name = "Done · dark", showBackground = true, heightDp = 1800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DoneDark() = PreviewScreen(dark = true, streaming = false)

@Preview(name = "Expanded · light", showBackground = true, widthDp = 1000, heightDp = 900)
@Composable
private fun ExpandedLight() = PreviewScreen(dark = false, streaming = false)

@Preview(name = "Empty · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyDark() =
    TravelTheme(themeMode = ThemeMode.DARK) {
        ResultsScreen(ResultsUiState(), onEvent = {}, actions = NoOpSectionActions, onBack = {}, onOpenMyPlan = {})
    }
