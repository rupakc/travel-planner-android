package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.TipsData
import io.github.rupakc.travelplanner.feature.results.sections.info.TipsBody

/** Travel tips tinted by severity, with a visible severity label (never colour alone), source link and Add (web TipsSection). */
@Composable
internal fun TipsSectionContent(data: TipsData, actions: SectionActions, modifier: Modifier = Modifier) {
    TipsBody(data, actions::isSelected, actions::onToggle, actions::onOpenLink, modifier)
}
