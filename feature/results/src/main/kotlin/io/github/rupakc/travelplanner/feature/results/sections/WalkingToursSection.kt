package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.feature.results.sections.info.WalkingToursBody

/**
 * Free walking tours (crawled, real URLs): provider and "Top pick" chips, image, rating,
 * duration, price note, reason, "View on {provider} ↗" and Add (web WalkingToursSection).
 */
@Composable
internal fun WalkingToursSectionContent(data: WalkingToursData, actions: SectionActions, modifier: Modifier = Modifier) {
    WalkingToursBody(data, actions::isSelected, actions::onToggle, actions::onOpenLink, modifier)
}
