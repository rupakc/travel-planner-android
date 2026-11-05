package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.EventsData
import io.github.rupakc.travelplanner.feature.results.sections.info.EventsBody

/**
 * What's On: category chips with emoji, dates (typical-season "~" explained to TalkBack),
 * matched interests, Add, and the "Plan around these" disruptions (web EventsSection).
 */
@Composable
internal fun EventsSectionContent(data: EventsData, actions: SectionActions, modifier: Modifier = Modifier) {
    EventsBody(data, actions::isSelected, actions::onToggle, modifier)
}
