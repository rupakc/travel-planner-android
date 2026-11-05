package io.github.rupakc.travelplanner.feature.results.sections

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.model.section.PackingListData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.feature.results.sections.info.PackingBody

/**
 * Packing list: categories with checkboxes, essentials marker, custom items per category,
 * "{checked}/{total} items packed", and Save to Plan (`selections.packing_list`, web shape).
 */
@Composable
internal fun PackingListSectionContent(data: PackingListData, actions: SectionActions, modifier: Modifier = Modifier) {
    val selections by actions.selections.collectAsStateWithLifecycle()
    val results by actions.results.collectAsStateWithLifecycle()
    PackingBody(
        data = data,
        saved = selections.packingList,
        onSave = { json -> actions.onUpdateSelections { it.copy(packingList = json) } },
        modifier = modifier,
        rawCategories = results.sections[SectionId.PACKING_LIST]?.raw?.get("categories"),
    )
}
