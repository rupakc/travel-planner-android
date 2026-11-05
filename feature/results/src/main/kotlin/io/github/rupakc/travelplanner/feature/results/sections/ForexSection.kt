package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.model.section.ForexData
import io.github.rupakc.travelplanner.feature.results.sections.info.ForexBody

/** Money and forex: currency and rates, where to exchange, cards, cash, ATMs, tipping, money tips and sources (web ForexSection). Read-only. */
@Composable
internal fun ForexSectionContent(data: ForexData, actions: SectionActions, modifier: Modifier = Modifier) {
    ForexBody(data, actions::onOpenLink, modifier)
}
