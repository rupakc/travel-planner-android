package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.json.EmptyJsonObject
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.section.PlaceToSee
import io.github.rupakc.travelplanner.core.model.section.PlacesData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.results.R
import java.text.NumberFormat
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Weather chip for an outdoor place (web: "⚠️ Check weather" / "☀️ Good weather"). */
internal enum class PlaceWeather { CHECK, GOOD }

private val OUTDOOR = listOf("viewpoint", "park/garden", "natural wonder", "market")

/** Web rule: outdoor categories get a weather chip once weather is known. */
internal fun placeWeather(category: String?, weather: WeatherData?): PlaceWeather? {
    val cat = category.orEmpty().lowercase()
    if (OUTDOOR.none { cat.contains(it) }) return null
    val poor = weather?.poorWeatherDayCount ?: 0
    return when {
        poor > 0 -> PlaceWeather.CHECK
        weather?.days?.isNotEmpty() == true -> PlaceWeather.GOOD
        else -> null
    }
}

/** `link_verified: true` on the item's original JSON (web FoundOnlineBadge). */
internal fun JsonObject.linkVerified(): Boolean = (this["link_verified"] as? JsonPrimitive)?.booleanOrNull == true

@Composable
internal fun PlacesBody(
    data: PlacesData,
    weather: WeatherData?,
    isSelected: (PlanItem) -> Boolean,
    onToggle: (PlanItem) -> Unit,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (data.results.isEmpty()) {
        EmptyState(stringResource(R.string.places_empty), modifier)
        return
    }
    AdaptiveGrid(data.results, modifier) { place, cell ->
        val item = PlanItem.PlaceItem(place)
        PlaceCard(place, isSelected(item), { onToggle(item) }, onOpenLink, placeWeather(place.category, weather), cell)
    }
}

@Composable
private fun PlaceCard(
    place: PlaceToSee,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpenLink: (String) -> Unit,
    weatherChip: PlaceWeather?,
    modifier: Modifier,
) {
    val accent = TravelTheme.sectionAccents[SectionId.PLACES_TO_SEE]
    TpCard(modifier.fillMaxWidth(), selected = selected, accent = accent.bar) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                InfoPill(place.category ?: stringResource(R.string.places_default_category), tone = Tone.Accent, section = SectionId.PLACES_TO_SEE)
                place.rating?.let { RatingText(it, place.reviewCount) }
            }
            Text(place.name.orEmpty(), style = TravelTheme.typography.titleMedium)
            (place.neighbourhood ?: place.address)?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            place.description?.let { Text(it, style = TravelTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
                place.visitDurationHours?.takeIf { it > 0 }?.let { MetaText(stringResource(R.string.places_duration, it.plain())) }
                place.bestTimeToVisit?.takeIf { it.isNotBlank() }?.let { MetaText(it) }
                when {
                    place.admissionFeeUsd == 0.0 -> MetaText(stringResource(R.string.places_free_entry), TravelTheme.extendedColors.success)
                    (place.admissionFeeUsd ?: 0.0) > 0 -> MetaText(stringResource(R.string.places_fee, localPrice(place.admissionFeeUsd)))
                }
            }
            if (place.highlights.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                ) {
                    place.highlights.forEach { InfoPill(it) }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                CityChip(cityOf(place.raw))
                if (place.raw.linkVerified()) FoundOnlineChip()
                if (place.hiddenGem == true) InfoPill(stringResource(R.string.info_hidden_gem), tone = Tone.Accent, section = SectionId.EVENTS)
                if (place.familyFriendly == true) InfoPill(stringResource(R.string.info_family), tone = Tone.Info)
                if (place.accessibilityFeatures.isNotEmpty()) InfoPill(stringResource(R.string.info_accessible), tone = Tone.Violet)
                when (weatherChip) {
                    PlaceWeather.CHECK -> InfoPill(stringResource(R.string.info_check_weather), tone = Tone.Warning)
                    PlaceWeather.GOOD -> InfoPill(stringResource(R.string.info_good_weather), tone = Tone.Success)
                    null -> Unit
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LinkButton(stringResource(R.string.info_view_details), place.infoUrl, onOpenLink)
                AddToPlanButton(selected, place.name.orEmpty(), onToggle)
            }
        }
    }
}

@Composable
internal fun MetaText(text: String, color: androidx.compose.ui.graphics.Color = TravelTheme.colors.onSurfaceVariant) {
    Text(text, style = TravelTheme.typography.labelMedium, color = color)
}

/** "★ 4.6 (1,234)" read by TalkBack as "Rated 4.6 (1,234)". */
@Composable
internal fun RatingText(rating: Double, reviews: Int?, reviewsTemplate: Int = R.string.info_rating_reviews) {
    val value = String.format(currentLocale(), "%.1f", rating)
    val count = reviews?.takeIf { it > 0 }?.let { NumberFormat.getIntegerInstance().format(it) }
    val text = if (count != null) stringResource(reviewsTemplate, value, count) else stringResource(R.string.info_rating, value)
    val a11y = stringResource(R.string.info_rating_a11y, value) + text.substringAfter(value)
    Text(
        text,
        style = TravelTheme.typography.labelMedium,
        color = TravelTheme.extendedColors.warning,
        modifier = Modifier.semantics { contentDescription = a11y },
    )
}

@Composable
internal fun FoundOnlineChip() {
    val a11y = stringResource(R.string.info_found_online_a11y)
    InfoPill(stringResource(R.string.info_found_online), tone = Tone.Success, modifier = Modifier.semantics { contentDescription = a11y })
}

private val previewPlaces =
    PlacesData(
        results =
            listOf(
                PlaceToSee(
                    name = "Miradouro da Senhora do Monte",
                    category = "Viewpoint",
                    description = "The highest viewpoint in Lisbon, with sweeping views over the castle and river.",
                    neighbourhood = "Graça",
                    rating = 4.7,
                    reviewCount = 12840,
                    visitDurationHours = 1.0,
                    bestTimeToVisit = "Sunset",
                    admissionFeeUsd = 0.0,
                    highlights = listOf("Sunset", "Photography"),
                    infoUrl = "https://example.org/miradouro",
                    hiddenGem = true,
                    raw = EmptyJsonObject,
                ),
            ),
    )

@Preview(name = "Places – light")
@Composable
private fun PlacesLightPreview() = PreviewSurface(ThemeMode.LIGHT) { PlacesBody(previewPlaces, null, { false }, {}, {}) }

@Preview(name = "Places – dark")
@Composable
private fun PlacesDarkPreview() = PreviewSurface(ThemeMode.DARK) { PlacesBody(previewPlaces, WeatherData(poorWeatherDayCount = 2), { true }, {}, {}) }
