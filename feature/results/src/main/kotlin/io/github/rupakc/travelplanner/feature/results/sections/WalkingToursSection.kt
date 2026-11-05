package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import io.github.rupakc.travelplanner.core.model.section.WalkingToursData
import io.github.rupakc.travelplanner.feature.results.R

/**
 * WalkingTours section body (inside SectionScaffold). Placeholder: replace this whole
 * file; keep the signature so SectionRegistry keeps compiling.
 */
@Composable
internal fun WalkingToursSectionContent(data: WalkingToursData, actions: SectionActions, modifier: Modifier = Modifier) {
    val count = data.results.size
    Text(pluralStringResource(R.plurals.results_items, count, count), modifier = modifier)
}
