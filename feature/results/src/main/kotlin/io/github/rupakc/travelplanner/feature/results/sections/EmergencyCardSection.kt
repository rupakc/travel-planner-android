package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.EmergencyCardData
import io.github.rupakc.travelplanner.feature.results.sections.info.EmergencyBody

/**
 * Emergency card per city: tap-to-dial number tiles (`ACTION_DIAL`), embassy, hospitals,
 * phrases table, laws, and "Share card" (plain text via the share sheet) (web EmergencyCardSection).
 */
@Composable
internal fun EmergencyCardSectionContent(data: EmergencyCardData, actions: SectionActions, modifier: Modifier = Modifier) {
    EmergencyBody(data, actions::onOpenLink, modifier)
}
