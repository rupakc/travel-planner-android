package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import coil3.compose.AsyncImage
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.WalkingTour
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.feature.results.R
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Top picks first within each city; cities keep their incoming (journey) order (web `sortTopPicksPerCity`). */
internal fun sortTopPicksPerCity(results: List<WalkingTour>): List<WalkingTour> =
    results.groupBy { cityName(it.city) }.values.flatMap { group -> group.sortedByDescending { it.topPick == true } }

/** One crawled provider (`data.sources[]`): label parts for "Searched: …". */
internal data class TourSource(val provider: String, val city: String, val status: String?, val count: Int?, val url: String?)

internal fun tourSources(sources: List<JsonObject>): List<TourSource> =
    sources.map { s ->
        fun str(k: String) = (s[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        TourSource(str("provider").orEmpty(), cityName(str("city")), str("status"), (s["count"] as? JsonPrimitive)?.intOrNull, str("url"))
    }

@Composable
internal fun WalkingToursBody(
    data: WalkingToursData,
    isSelected: (PlanItem) -> Boolean,
    onToggle: (PlanItem) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (data.isError) {
        EmptyState(stringResource(R.string.tours_error), modifier)
        return
    }
    if (data.results.isEmpty()) {
        EmptyState(stringResource(R.string.tours_empty), modifier)
        return
    }
    val sorted = remember(data.results) { sortTopPicksPerCity(data.results) }
    val showCity = data.results.map { cityName(it.city) }.filter { it.isNotEmpty() }.toSet().size > 1
    val sources = remember(data.sources) { tourSources(data.sources) }
    val multiCitySources = showCity || sources.map { it.city }.filter { it.isNotEmpty() }.toSet().size > 1
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        AdaptiveGrid(sorted) { tour, cell ->
            val item = PlanItem.WalkingTourItem(tour)
            TourCard(tour, isSelected(item), { onToggle(item) }, showCity, onOpenLink, cell)
        }
        if (sources.isNotEmpty()) SourcesLine(sources, multiCitySources, onOpenLink)
    }
}

@Composable
private fun TourCard(tour: WalkingTour, selected: Boolean, onToggle: () -> Unit, showCity: Boolean, onOpenLink: (String) -> Unit, modifier: Modifier) {
    val accent = TravelTheme.sectionAccents[SectionId.WALKING_TOURS]
    TpCard(modifier.fillMaxWidth(), selected = selected, accent = accent.bar) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            TourImage(tour.imageUrl)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                tour.provider?.takeIf { it.isNotBlank() }?.let { InfoPill(it, tone = Tone.Info) }
                if (tour.topPick == true) InfoPill(stringResource(R.string.tours_top_pick), tone = Tone.Warning)
            }
            Text(tour.name.orEmpty(), style = TravelTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), itemVerticalAlignment = Alignment.CenterVertically) {
                tour.rating?.let { RatingText(it, tour.reviewCount, R.string.tours_reviews) }
                tour.duration?.takeIf { it.isNotBlank() }?.let { MetaText(it) }
                tour.priceNote?.takeIf { it.isNotBlank() }?.let { MetaText(it, TravelTheme.extendedColors.success) }
            }
            tour.reason?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = TravelTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = TravelTheme.colors.onSurfaceVariant)
            }
            if (showCity) CityChip(cityName(tour.city).ifEmpty { null })
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LinkButton(stringResource(R.string.tours_view_on, tour.provider.orEmpty()), tour.url, onOpenLink)
                AddToPlanButton(selected, tour.name.orEmpty(), onToggle)
            }
        }
    }
}

@Composable
private fun TourImage(url: String?) {
    val safe = safeHttpUrl(url) ?: return
    var failed by remember(safe) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = safe,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = Modifier.fillMaxWidth().height(InfoDimens.TourImageHeight).clip(TravelShape.Input),
    )
}

@Composable
private fun SourcesLine(sources: List<TourSource>, multiCity: Boolean, onOpenLink: (String) -> Unit) {
    FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(stringResource(R.string.tours_searched), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
        sources.forEach { s ->
            val name = if (multiCity && s.city.isNotEmpty()) "${s.provider} · ${s.city}" else s.provider
            val label =
                when (s.status) {
                    "error" -> stringResource(R.string.tours_source_unavailable, name)
                    "none" -> stringResource(R.string.tours_source_count, name, 0)
                    else -> stringResource(R.string.tours_source_count, name, s.count ?: 0)
                }
            if (s.status == "ok" && safeHttpUrl(s.url) != null) {
                LinkButton(label, s.url, onOpenLink)
            } else {
                Text(label, style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
            }
        }
    }
}

private val previewTours =
    WalkingToursData(
        results =
            listOf(
                WalkingTour(
                    name = "Alfama & Mouraria Free Tour",
                    provider = "GuruWalk",
                    url = "https://www.guruwalk.com/lisbon",
                    rating = 4.8,
                    reviewCount = 2310,
                    duration = "2h 30m",
                    priceNote = "Free — tip what you like",
                    reason = "Covers the oldest quarters with a local guide.",
                    topPick = true,
                ),
            ),
    )

@Preview(name = "Walking tours – light")
@Composable
private fun ToursLightPreview() = PreviewSurface(ThemeMode.LIGHT) { WalkingToursBody(previewTours, { false }, {}, {}) }

@Preview(name = "Walking tours – dark")
@Composable
private fun ToursDarkPreview() = PreviewSurface(ThemeMode.DARK) { WalkingToursBody(WalkingToursData(), { false }, {}, {}) }
