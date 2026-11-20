package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.PlaceFactsData
import io.github.rupakc.travelplanner.core.ui.facts.PlaceFactsBody

/**
 * Place facts (web `PlaceFactsSection`, `docs/Place-Facts.md`): place chips, group chips, crawler facts with their
 * source line and AI facts labelled "AI-suggested, not verified", each selectable into My Plan (`place_facts`).
 * The section is only listed when the stream sent facts (an older backend never does).
 */
@Composable
internal fun PlaceFactsSectionContent(data: PlaceFactsData, actions: SectionActions, modifier: Modifier = Modifier) {
    PlaceFactsBody(data, actions::isSelected, actions::onToggle, actions::onOpenLink, modifier)
}
