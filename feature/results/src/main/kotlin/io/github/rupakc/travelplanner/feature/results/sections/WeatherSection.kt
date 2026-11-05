package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.feature.results.sections.info.WeatherBody

/**
 * Weather: horizontal day cards (poor days tinted and marked), multi-city groups,
 * the climate-estimate note and the poor-weather alert (web WeatherSection).
 */
@Suppress("UnusedParameter") // read-only section: no plan actions
@Composable
internal fun WeatherSectionContent(data: WeatherData, actions: SectionActions, modifier: Modifier = Modifier) {
    WeatherBody(data, modifier)
}
