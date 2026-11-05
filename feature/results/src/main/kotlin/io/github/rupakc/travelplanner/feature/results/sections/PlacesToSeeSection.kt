package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.model.section.PlacesData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.WeatherData
import io.github.rupakc.travelplanner.feature.results.sections.info.PlacesBody

/**
 * Places to See: category, rating, "Free entry"/"~$N", highlights, badges, weather chips for
 * outdoor places, "View details" (Custom Tab) and Add (web PlacesToSeeSection).
 */
@Composable
internal fun PlacesToSeeSectionContent(data: PlacesData, actions: SectionActions, modifier: Modifier = Modifier) {
    val results by actions.results.collectAsStateWithLifecycle()
    val weather = results.sections[SectionId.WEATHER]?.data as? WeatherData
    PlacesBody(data, weather, actions::isSelected, actions::onToggle, actions::onOpenLink, modifier)
}
