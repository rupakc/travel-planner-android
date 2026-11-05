package io.github.rupakc.travelplanner.feature.search

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.CompareErrorReason
import io.github.rupakc.travelplanner.core.domain.search.ExtraStop
import io.github.rupakc.travelplanner.core.domain.search.ImportAction
import io.github.rupakc.travelplanner.core.domain.search.ImportErrorView
import io.github.rupakc.travelplanner.core.domain.search.SearchForm
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.TravellerCounts
import io.github.rupakc.travelplanner.core.domain.search.describeRecentSearch
import io.github.rupakc.travelplanner.core.domain.search.previewToDraft
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreview
import io.github.rupakc.travelplanner.core.model.importlink.ImportPreviewSource
import io.github.rupakc.travelplanner.core.model.importlink.PreviewDestination
import io.github.rupakc.travelplanner.core.model.importlink.PreviewPlace
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.search.CompareEase
import io.github.rupakc.travelplanner.core.model.search.CompareEstimate
import io.github.rupakc.travelplanner.core.model.search.CompareFlight
import io.github.rupakc.travelplanner.core.model.search.CompareHotel
import io.github.rupakc.travelplanner.core.model.search.CompareSummary
import io.github.rupakc.travelplanner.core.model.search.CompareVisa
import io.github.rupakc.travelplanner.core.model.search.CompareWeather
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.feature.search.compare.CompareColumn
import io.github.rupakc.travelplanner.feature.search.compare.CompareUiState
import io.github.rupakc.travelplanner.feature.search.components.SearchLookups
import io.github.rupakc.travelplanner.feature.search.discover.DiscoverUiState
import io.github.rupakc.travelplanner.feature.search.importlink.ImportBusy
import io.github.rupakc.travelplanner.feature.search.importlink.ImportSourceKind
import io.github.rupakc.travelplanner.feature.search.importlink.ImportUiState
import java.time.LocalDate

// Sample data for previews only (never shown in the app).
private val PreviewToday: LocalDate = LocalDate.of(2026, 11, 5)

private val PreviewForm =
    SearchForm.initial(PreviewToday).copy(
        origin = "JFK",
        destination = "Tokyo, Japan",
        nationality = "American",
        budgetUsd = "3000",
        interests = listOf("food", "history"),
        travellers = TravellerCounts(adults = 2, children = 1),
    )

private val PreviewRecent =
    RecentSearch("r1", RecentSearchIn("New York", "Lisbon, Portugal", "2026-12-01", "2026-12-08", interests = listOf("food", "art", "nature", "history")))

private val PreviewSummary =
    CompareSummary(
        destination = "Lisbon",
        label = "Lisbon, Portugal",
        flight = CompareFlight(priceUsd = 612.0, stops = 1, durationMinutes = 545, airline = "TAP"),
        hotel = CompareHotel(typicalNightlyUsd = 140.0, cheapestGoodNightlyUsd = 95.0),
        weather = CompareWeather(avgHighC = 17.0, avgLowC = 10.0, poorDays = 2, totalDays = 7, source = "open-meteo"),
        visa = CompareVisa(visaType = "visa-free", maxStayDays = 90, feeUsd = 0.0, verified = true),
        ease = CompareEase(total = 82, grade = "green", safety = 17, english = 14),
        dailyCostUsd = 180.0,
        estimate = CompareEstimate(totalUsd = 1840.0, budgetStatus = "under"),
    )

private val PreviewImport =
    ImportPreview(
        source = ImportPreviewSource(kind = "url", url = "https://example.com/lisbon", title = "48 hours in Lisbon", siteName = "Example"),
        title = "48 hours in Lisbon",
        summary = "Viewpoints and pastries.",
        destinations = listOf(PreviewDestination("Lisbon, Portugal", "Lisbon", "Portugal", nights = 3, placeCount = 2)),
        places =
            listOf(
                PreviewPlace(
                    "p1",
                    "Time Out Market",
                    "food",
                    "Lisbon",
                    destination = "Lisbon, Portugal",
                    approximate = false,
                    verified = true,
                    quote = "start here",
                ),
                PreviewPlace("p2", "Pena Palace", "sight", "Sintra", destination = "Lisbon, Portugal"),
            ),
        unresolved = listOf(PreviewPlace("p3", "Mystery Bar", "drink")),
        suggestedDays = 3,
    )

private fun knownState(issues: Boolean) =
    SearchUiState(
        today = PreviewToday,
        form = if (issues) SearchForm.initial(PreviewToday) else PreviewForm,
        stops = if (issues) emptyList() else listOf(ExtraStop("Seoul", "2")),
        issues =
            if (issues) {
                listOf(
                    SearchFormIssue.ORIGIN_REQUIRED,
                    SearchFormIssue.DESTINATION_REQUIRED,
                    SearchFormIssue.NATIONALITY_REQUIRED,
                )
            } else {
                emptyList()
            },
        recent = listOf(RecentSearchCard(PreviewRecent, describeRecentSearch(PreviewRecent.search), datesPassed = false)),
    )

@Composable
private fun PreviewHost(
    state: SearchUiState,
    discover: DiscoverUiState = DiscoverUiState(PreviewToday),
    compare: CompareUiState = CompareUiState(PreviewToday),
    import: ImportUiState = ImportUiState(PreviewToday),
    wide: Boolean = false,
) {
    TravelTheme {
        Box(Modifier.background(TravelTheme.colors.background)) {
            SearchScreen(
                state = state,
                discover = discover,
                compare = compare,
                import = import,
                lookups = SearchLookups.None,
                onEvent = {},
                onDiscoverEvent = {},
                onCompareEvent = {},
                onImportEvent = {},
                onPlanDiscovered = {},
                onPlanCompared = {},
                onPickImages = {},
                wide = wide,
            )
        }
    }
}

@Preview(name = "Known — light", heightDp = 1600)
@Preview(name = "Known — dark", heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KnownPreview() = PreviewHost(knownState(issues = false))

@Preview(name = "Known — validation, light", heightDp = 1400)
@Preview(name = "Known — validation, dark", heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun KnownIssuesPreview() = PreviewHost(knownState(issues = true))

@Preview(name = "Surprise me — results, light", heightDp = 1800)
@Preview(name = "Surprise me — results, dark", heightDp = 1800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverPreview() =
    PreviewHost(
        SearchUiState(today = PreviewToday, mode = SearchMode.SURPRISE),
        discover =
            DiscoverUiState(
                today = PreviewToday,
                form = PreviewForm,
                destinations =
                    listOf(
                        DiscoveredDestination(
                            city = "Lisbon",
                            country = "Portugal",
                            estimatedCostUsdLow = 1200.0,
                            estimatedCostUsdHigh = 1800.0,
                            visaType = "visa-free",
                            visaVerified = true,
                            weatherEmoji = "☀️",
                            weatherDescription = "Mild and sunny, 18°C",
                            flightDurationLabel = "~7h direct",
                            matchReasons = listOf("Great food scene", "Walkable old town"),
                        ),
                    ),
            ),
    )

@Preview(name = "Surprise me — loading, light", heightDp = 1400)
@Preview(name = "Surprise me — loading, dark", heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DiscoverLoadingPreview() =
    PreviewHost(SearchUiState(today = PreviewToday, mode = SearchMode.SURPRISE), discover = DiscoverUiState(PreviewToday, form = PreviewForm, loading = true))

private val CompareColumns =
    listOf(
        CompareColumn("Lisbon", CompareColumn.Status.DONE, PreviewSummary),
        CompareColumn("Porto", CompareColumn.Status.ERROR, error = CompareErrorReason.Unavailable),
        CompareColumn("Madrid", CompareColumn.Status.LOADING),
    )

@Preview(name = "Compare — cards, light", heightDp = 2400)
@Preview(name = "Compare — cards, dark", heightDp = 2400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ComparePreview() =
    PreviewHost(
        SearchUiState(today = PreviewToday, mode = SearchMode.COMPARE),
        compare = CompareUiState(PreviewToday, form = PreviewForm, destinations = listOf("Lisbon", "Porto", "Madrid"), columns = CompareColumns),
    )

@Preview(name = "Compare — table, expanded", widthDp = 1100, heightDp = 1800)
@Composable
private fun CompareWidePreview() =
    PreviewHost(
        SearchUiState(today = PreviewToday, mode = SearchMode.COMPARE),
        compare = CompareUiState(PreviewToday, form = PreviewForm, destinations = listOf("Lisbon", "Porto", "Madrid"), columns = CompareColumns),
        wide = true,
    )

@Preview(name = "From a link — error, light", heightDp = 1200)
@Preview(name = "From a link — error, dark", heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImportErrorPreview() =
    PreviewHost(
        SearchUiState(today = PreviewToday, mode = SearchMode.IMPORT),
        import =
            ImportUiState(
                PreviewToday,
                text = "https://instagram.com/p/abc",
                error = ImportErrorView("blocked_source", "Instagram doesn't let apps read posts.", listOf(ImportAction.TEXT, ImportAction.IMAGES)),
            ),
    )

@Preview(name = "From a link — reading, light", heightDp = 1000)
@Preview(name = "From a link — reading, dark", heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImportBusyPreview() =
    PreviewHost(
        SearchUiState(today = PreviewToday, mode = SearchMode.IMPORT),
        import = ImportUiState(PreviewToday, busy = ImportBusy(ImportSourceKind.URL, step = 1, elapsedSeconds = 4)),
    )

@Preview(name = "From a link — review, light", heightDp = 2200)
@Preview(name = "From a link — review, dark", heightDp = 2200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ImportReviewPreview() =
    PreviewHost(
        SearchUiState(today = PreviewToday, mode = SearchMode.IMPORT),
        import = ImportUiState(PreviewToday, preview = PreviewImport, draft = previewToDraft(PreviewImport), trip = PreviewForm),
    )
