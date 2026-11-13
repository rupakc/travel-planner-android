package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.core.model.section.WeatherDay
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.results.R
import kotlin.math.roundToInt

/** Web WMO_EMOJI (decorative; the description text carries the meaning). WMO codes are data, not magic numbers. */
@Suppress("MagicNumber")
internal fun weatherEmoji(code: Int?): String =
    when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 80 -> "🌦️"
        53, 55, 61, 63, 65, 81 -> "🌧️"
        71, 85 -> "🌨️"
        73, 75, 77, 86 -> "❄️"
        82, 95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

/** Consecutive days grouped by city (journey order), web WeatherSection. */
internal data class WeatherGroup(val city: String, val days: List<WeatherDay>)

internal fun weatherGroups(days: List<WeatherDay>): List<WeatherGroup> {
    val groups = mutableListOf<WeatherGroup>()
    for (day in days) {
        val city = cityName(day.city)
        val last = groups.lastOrNull()
        if (last != null && last.city == city) groups[groups.lastIndex] = last.copy(days = last.days + day) else groups += WeatherGroup(city, listOf(day))
    }
    return groups
}

@Composable
internal fun WeatherBody(data: WeatherData, modifier: Modifier = Modifier) {
    if (data.days.isEmpty()) return
    val groups = remember(data.days) { weatherGroups(data.days) }
    val multiCity = groups.count { it.city.isNotEmpty() } > 1
    val poor = data.poorWeatherDayCount ?: 0
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        if (data.source.orEmpty().contains("llm-estimate")) {
            Text(
                stringResource(R.string.weather_estimate),
                style = TravelTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
        if (multiCity) {
            groups.forEachIndexed { gi, group ->
                Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        InfoPill(stringResource(R.string.weather_city, group.city.ifEmpty { stringResource(R.string.weather_en_route) }), tone = Tone.Info)
                        Text(
                            pluralStringResource(R.plurals.weather_group_days, group.days.size, group.days.size),
                            style = TravelTheme.typography.labelSmall,
                            color = TravelTheme.colors.onSurfaceVariant,
                        )
                    }
                    WeatherStrip(group.days, keyPrefix = "$gi-${group.city}")
                }
            }
        } else {
            WeatherStrip(data.days, keyPrefix = "all")
        }
        if (poor > 0) Callout(pluralStringResource(R.plurals.weather_poor_days, poor, poor), tone = Tone.Warning, icon = Icons.Filled.Warning)
        Text(stringResource(R.string.weather_attribution), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun WeatherStrip(days: List<WeatherDay>, keyPrefix: String) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        itemsIndexed(days, key = { i, d -> "$keyPrefix-${d.city.orEmpty()}-${d.date.orEmpty()}-$i" }, contentType = { _, _ -> "weather_day" }) { _, day ->
            WeatherDayCard(day)
        }
    }
}

@Composable
private fun WeatherDayCard(day: WeatherDay) {
    val poor = day.isPoor == true
    val locale = currentLocale()
    val date = shortDate(day.date, locale).orEmpty()
    val high = day.tempHighC?.roundToInt()
    val low = day.tempLowC?.roundToInt()
    val temps = if (high != null && low != null) stringResource(R.string.weather_high_low, high, low) else null
    val rain = day.precipitationMm?.takeIf { it > 0 }?.let { stringResource(R.string.weather_rain, String.format(locale, "%.1f", it)) }
    val poorLabel = stringResource(R.string.weather_poor_marker)
    val a11y = listOfNotNull(date, day.description, temps, rain, poorLabel.takeIf { poor }).filter { it.isNotBlank() }.joinToString(", ")
    Surface(
        modifier = Modifier.width(InfoDimens.WeatherCardWidth).clearAndSetSemantics { contentDescription = a11y },
        shape = TravelShape.ItemCard,
        color = if (poor) TravelTheme.extendedColors.warningContainer else TravelTheme.colors.surface,
        border = BorderStroke(InfoDimens.Border, if (poor) TravelTheme.extendedColors.warningBorder else TravelTheme.colors.outlineVariant),
    ) {
        Column(
            Modifier.padding(TravelTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs),
        ) {
            day.city?.takeIf { it.isNotBlank() }?.let {
                Text(cityName(it), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.secondary, maxLines = 1)
            }
            Text(date, style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
            Text(weatherEmoji(day.weatherCode), style = TravelTheme.typography.headlineMedium)
            day.description?.let { Text(it, style = TravelTheme.typography.labelMedium, textAlign = TextAlign.Center) }
            temps?.let { Text(it, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant) }
            rain?.let { Text(it, style = TravelTheme.typography.labelSmall, color = TravelTheme.extendedColors.info) }
            if (poor) Text(poorLabel, style = TravelTheme.typography.labelSmall, color = TravelTheme.extendedColors.onWarningContainer)
        }
    }
}

private val previewWeather =
    WeatherData(
        days =
            listOf(
                WeatherDay("2027-05-01", "Lisbon, Portugal", "Sunny", 0, 24.4, 15.2, 0.0),
                WeatherDay("2027-05-02", "Lisbon, Portugal", "Showers", 80, 19.0, 13.0, 6.2, isPoor = true),
                WeatherDay("2027-05-03", "Porto, Portugal", "Cloudy", 3, 18.0, 12.0, 0.0),
            ),
        poorWeatherDayCount = 1,
        source = "llm-estimate",
    )

@Preview(name = "Weather – light")
@Composable
private fun WeatherLightPreview() = PreviewSurface(ThemeMode.LIGHT) { WeatherBody(previewWeather) }

@Preview(name = "Weather – dark")
@Composable
private fun WeatherDarkPreview() = PreviewSurface(ThemeMode.DARK) { WeatherBody(previewWeather) }
