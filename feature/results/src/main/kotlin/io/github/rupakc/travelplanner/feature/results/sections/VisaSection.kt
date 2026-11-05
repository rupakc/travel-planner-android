package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.feature.results.sections.info.VisaBody

/** Visa: conflict banner, type pill, tiles, requirements checklist, health, customs and official links (web VisaSection). Read-only. */
@Composable
internal fun VisaSectionContent(data: VisaData, actions: SectionActions, modifier: Modifier = Modifier) {
    VisaBody(data, actions::onOpenLink, modifier)
}
