package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import io.github.rupakc.travelplanner.core.model.section.VisaData
import io.github.rupakc.travelplanner.feature.results.R

/**
 * Visa section body (inside SectionScaffold). Placeholder: replace this whole
 * file; keep the signature so SectionRegistry keeps compiling.
 */
@Suppress("UnusedParameter") // placeholder: the section team uses `actions`
@Composable
internal fun VisaSectionContent(data: VisaData, actions: SectionActions, modifier: Modifier = Modifier) {
    val count = data.requirement?.requirements?.size ?: 0
    Text(pluralStringResource(R.plurals.results_items, count, count), modifier = modifier)
}
