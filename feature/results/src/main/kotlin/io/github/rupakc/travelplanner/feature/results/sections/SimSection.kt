package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import io.github.rupakc.travelplanner.core.model.section.SimData
import io.github.rupakc.travelplanner.feature.results.R

/**
 * Sim section body (inside SectionScaffold). Placeholder: replace this whole
 * file; keep the signature so SectionRegistry keeps compiling.
 */
@Suppress("UnusedParameter") // placeholder: the section team uses `actions`
@Composable
internal fun SimSectionContent(data: SimData, actions: SectionActions, modifier: Modifier = Modifier) {
    val count = data.plans.size
    Text(pluralStringResource(R.plurals.results_items, count, count), modifier = modifier)
}
