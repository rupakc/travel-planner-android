package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.GettingAroundData
import io.github.rupakc.travelplanner.feature.results.sections.info.GettingAroundBody

/** Getting around: In the City / Between Cities / Other groups with type tags, details, link and Add (web GettingAroundSection). */
@Composable
internal fun GettingAroundSectionContent(data: GettingAroundData, actions: SectionActions, modifier: Modifier = Modifier) {
    GettingAroundBody(data, actions::isSelected, actions::onToggle, actions::onOpenLink, modifier)
}
