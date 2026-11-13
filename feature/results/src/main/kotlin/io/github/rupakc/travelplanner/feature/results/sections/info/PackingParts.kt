package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.tpToggleable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.PackingCategory
import io.github.rupakc.travelplanner.core.model.section.PackingItem
import io.github.rupakc.travelplanner.core.model.section.PackingListData
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.results.R
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private val PackingStateSaver = Saver<PackingState, String>(save = { it.encode() }, restore = { PackingState.decode(it) })

/**
 * Packing list. While it is saved to the plan, the plan's `packing_list` is the source of truth and every
 * change is written back ([onSave]); otherwise ticks live on this screen until "Save to Plan".
 * [onSave] receives the web-shaped object, or null to remove it from the plan.
 */
@Composable
internal fun PackingBody(
    data: PackingListData,
    saved: JsonObject?,
    onSave: (JsonObject?) -> Unit,
    modifier: Modifier = Modifier,
    rawCategories: JsonElement? = null,
) {
    if (data.categories.isEmpty()) {
        EmptyState(stringResource(R.string.packing_unavailable), modifier)
        return
    }
    var local by rememberSaveable(stateSaver = PackingStateSaver) { mutableStateOf(PackingState.fromPlan(saved)) }
    val isSaved = saved != null
    val state = if (isSaved) PackingState.fromPlan(saved) else local
    val update = { next: PackingState ->
        local = next
        if (isSaved) onSave(next.toPlanJson(data, rawCategories))
    }
    val inputs = remember { mutableStateMapOf<String, String>() }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        data.luggageNote?.takeIf { it.isNotBlank() }?.let { Callout(stringResource(R.string.packing_luggage, it), tone = Tone.Neutral) }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(R.plurals.packing_counter, state.total(data), state.checkedCount, state.total(data)),
                style = TravelTheme.typography.labelLarge,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            if (isSaved) {
                val a11y = stringResource(R.string.packing_saved_a11y)
                TpButton(
                    stringResource(R.string.packing_saved),
                    onClick = {
                        local = state
                        onSave(null)
                    },
                    variant = TpButtonVariant.Tonal,
                    leadingIcon = Icons.Filled.Check,
                    modifier = Modifier.semantics { contentDescription = a11y },
                    tid = "results.packing_saved",
                )
            } else {
                TpButton(stringResource(R.string.packing_save), onClick = {
                    onSave(state.toPlanJson(data, rawCategories))
                }, variant = TpButtonVariant.Secondary, tid = "results.packing_save")
            }
        }
        AdaptiveGrid(data.categories) { category, cell ->
            CategoryCard(
                category = category,
                state = state,
                input = inputs[category.name.orEmpty()].orEmpty(),
                onInput = { inputs[category.name.orEmpty()] = it },
                onToggle = { item -> update(state.toggle(category.name.orEmpty(), item)) },
                onAdd = {
                    val name = category.name.orEmpty()
                    val next = state.addCustom(name, inputs[name].orEmpty())
                    if (next != state) {
                        update(next)
                        inputs[name] = ""
                    }
                },
                modifier = cell,
            )
        }
        Text(stringResource(R.string.packing_essential_footnote), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun CategoryCard(
    category: PackingCategory,
    state: PackingState,
    input: String,
    onInput: (String) -> Unit,
    onToggle: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier,
) {
    val name = category.name.orEmpty()
    val items = category.items + state.custom[name].orEmpty().map { PackingItem(item = it, essential = false) }
    TpCard(modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Text(
                listOfNotNull(category.icon, name).joinToString(" "),
                style = TravelTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            category.weatherNote?.takeIf { it.isNotBlank() }?.let { Callout(it, tone = Tone.Info) }
            items.forEach { item ->
                val label = item.item.orEmpty()
                PackingRow(item, state.isChecked(name, label)) { onToggle(label) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TpOutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    placeholder = { Text(stringResource(R.string.packing_add_item)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    textStyle = TravelTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    tid = "results.packing_add_item",
                )
                TpIconButton(onClick = onAdd, tid = "results.packing_add_item_a11y") {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.packing_add_item_a11y, name))
                }
            }
        }
    }
}

@Composable
private fun PackingRow(item: PackingItem, checked: Boolean, onToggle: () -> Unit) {
    val label = item.item.orEmpty()
    val essential = item.essential == true
    val a11y = if (essential) stringResource(R.string.packing_essential_a11y, label) else label
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TravelTheme.spacing.minTouchTarget)
            .tpToggleable(tid = "results.packing_parts_toggleable", value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics(mergeDescendants = true) { contentDescription = a11y },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Column(Modifier.weight(1f)) {
            val decoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
            val color = if (checked) TravelTheme.colors.onSurfaceVariant else TravelTheme.colors.onSurface
            Row {
                Text(
                    label,
                    style = TravelTheme.typography.bodyMedium,
                    fontWeight = if (essential) FontWeight.SemiBold else FontWeight.Normal,
                    textDecoration = decoration,
                    color = color,
                )
                if (essential) Text(" *", style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.error)
            }
            item.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
        }
    }
}

private val previewPacking =
    PackingListData(
        categories =
            listOf(
                PackingCategory("Documents", "📄", items = listOf(PackingItem("Passport", true), PackingItem("Travel insurance", true, "Print a copy"))),
                PackingCategory(
                    "Clothing",
                    "👕",
                    "Evenings are cool — bring a light jacket",
                    listOf(PackingItem("Light jacket"), PackingItem("Walking shoes", true)),
                ),
            ),
        luggageNote = "A carry-on is enough for a week.",
        totalItems = 4,
    )

@Preview(name = "Packing list – light")
@Composable
private fun PackingLightPreview() = PreviewSurface(ThemeMode.LIGHT) { PackingBody(previewPacking, null, {}) }

@Preview(name = "Packing list – dark")
@Composable
private fun PackingDarkPreview() =
    PreviewSurface(ThemeMode.DARK) { PackingBody(previewPacking, PackingState(mapOf("Documents::Passport" to true)).toPlanJson(previewPacking), {}) }
