package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.feature.results.sections.info.SimBody

/** SIM and eSIM plans; single selection in My Plan (selecting one replaces the other) (web SimSection). */
@Composable
internal fun SimSectionContent(data: SimData, actions: SectionActions, modifier: Modifier = Modifier) {
    SimBody(data, actions::isSelected, actions::onToggle, actions::onOpenLink, modifier)
}
