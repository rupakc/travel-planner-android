package io.github.rupakc.travelplanner.feature.results.sections.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.itinerary.ArrangedItinerary
import io.github.rupakc.travelplanner.core.domain.itinerary.ItineraryLayouts
import io.github.rupakc.travelplanner.core.domain.itinerary.ItinerarySchedule
import io.github.rupakc.travelplanner.core.domain.itinerary.MoveTarget
import io.github.rupakc.travelplanner.core.domain.itinerary.ScheduledSlot
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEdit
import io.github.rupakc.travelplanner.core.domain.itinerary.SlotEditFields
import io.github.rupakc.travelplanner.core.domain.itinerary.dayRows
import io.github.rupakc.travelplanner.core.model.plan.ItineraryLayout
import io.github.rupakc.travelplanner.core.model.plan.PlanItem
import io.github.rupakc.travelplanner.core.model.plan.PlanSelections
import io.github.rupakc.travelplanner.core.model.section.ItineraryData
import io.github.rupakc.travelplanner.core.model.section.ItineraryDay
import io.github.rupakc.travelplanner.core.model.section.ItinerarySlot
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.core.ui.itinerary.ItineraryDayCard
import io.github.rupakc.travelplanner.core.ui.itinerary.ItinerarySlotRow
import io.github.rupakc.travelplanner.core.ui.itinerary.timeOfDayLabel
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.results.R
import kotlinx.serialization.json.JsonPrimitive

/** Everything the itinerary editor reads and does; [onUpdateSelections] is a read-modify-write of the plan draft. */
internal class ItineraryHost(
    val selections: PlanSelections,
    val locked: Boolean,
    val isInPlan: (PlanItem) -> Boolean,
    val onTogglePlan: (PlanItem) -> Unit,
    val onUpdateSelections: ((PlanSelections) -> PlanSelections) -> Unit,
)

/**
 * Itinerary with web-parity editing: Edit (activity/location/cost + note), Note, Move (menu of
 * targets, swapping when occupied; the accessible alternative to drag and drop), Remove/Restore,
 * Undo (20 steps) / Reset, and Plan/In Plan. Changes are announced with the web's copy.
 */
@Suppress("CyclomaticComplexMethod") // one state owner wiring every editing callback; the pieces are separate composables
@Composable
internal fun ItineraryBody(data: ItineraryData, host: ItineraryHost, modifier: Modifier = Modifier) {
    if (data.days.isEmpty()) {
        EmptyState(stringResource(R.string.itin_empty), modifier)
        return
    }
    val layout = host.selections.itineraryLayout
    val rawEdits = host.selections.itineraryEdits
    val edits = remember(rawEdits) { SlotEdit.fromSelections(rawEdits) }
    val arranged = remember(data, layout, edits) { ItineraryLayouts.arrange(data, layout, edits) }
    val scheduled = remember(arranged, edits) { ItinerarySchedule.scheduleDays(arranged.days, edits) }
    val editable = !host.locked
    val targets = remember(editable, arranged, data, layout) { if (editable) moveTargetsByKey(data, layout, arranged) else emptyMap() }
    val changeCount = if (editable) remember(data, layout) { ItineraryLayouts.layoutChangeCount(data, layout) } else 0

    // Undo history belongs to this itinerary (a new search starts empty) and to layouts this editor wrote:
    // a layout changed from outside (plan loaded, selections cleared) drops it, like the web.
    val itineraryKey = remember(data) { ItineraryLayouts.fingerprint(data) }
    var undo by rememberSaveable(itineraryKey) { mutableStateOf(listOf<String>()) }
    var pending by rememberSaveable(itineraryKey) { mutableStateOf(listOf<String>()) }
    var lastSeen by rememberSaveable(itineraryKey) { mutableStateOf(ItineraryEdits.encodeLayout(layout)) }
    var status by rememberSaveable(itineraryKey) { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var noteOpen by rememberSaveable { mutableStateOf<String?>(null) }
    val layoutText = ItineraryEdits.encodeLayout(layout)
    LaunchedEffect(layoutText) {
        if (layoutText == lastSeen) return@LaunchedEffect
        val mine = pending.indexOf(layoutText)
        if (mine >= 0) {
            pending = pending.drop(mine + 1) // our own write arrived (possibly conflated)
        } else {
            undo = emptyList()
            pending = emptyList()
        }
        lastSeen = layoutText
    }

    // Computed from the latest draft inside the update (web layoutRef), so quick successive taps never
    // work from a stale layout. Returns whether the layout changed.
    fun commit(undoable: Boolean = true, next: (ItineraryLayout?) -> ItineraryLayout?): Boolean {
        if (!editable) return false
        var changed = false
        host.onUpdateSelections { current ->
            val prev = current.itineraryLayout
            val target = next(prev)
            if (target == prev) {
                current
            } else {
                changed = true
                if (undoable) undo = (undo + ItineraryEdits.encodeLayout(prev)).takeLast(ItineraryEdits.UNDO_LIMIT)
                pending = (pending + ItineraryEdits.encodeLayout(target)).takeLast(ItineraryEdits.UNDO_LIMIT + 1)
                ItineraryEdits.commitLayout(data, current, target)
            }
        }
        return changed
    }

    val nameOf = { key: String?, fallback: String? -> edits[key]?.activityOr(fallback) ?: fallback.orEmpty() }
    val texts = editorTexts()

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        ItineraryHeader(host.locked, arranged.totalEstimatedCostUsd, data.source == "template", scheduled.any { d -> d.slots.any { it.timed } })

        if (editable && (changeCount > 0 || undo.isNotEmpty())) {
            ChangeBar(
                changeCount = changeCount,
                canUndo = undo.isNotEmpty(),
                onUndo = {
                    undo.lastOrNull()?.let { last ->
                        undo = undo.dropLast(1)
                        commit(undoable = false) { ItineraryEdits.decodeLayout(last) }
                        status = texts.undone
                    }
                },
                onReset = {
                    if (commit { null }) status = texts.reset
                },
            )
        }

        // Visible status line with real bounds (an empty Box has none and TalkBack would skip it); polite live
        // region like the web's aria-live text. It sits under the change bar and stays in place (one line high) so changes are announced.
        if (editable) {
            Text(
                status,
                style = TravelTheme.typography.labelMedium,
                color = TravelTheme.colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        scheduled.forEachIndexed { index, day ->
            val original: ItineraryDay? = data.days.getOrNull(index)
            val rows = remember(day, original, targets, editable) { dayRows(day, original, { k -> targets[k].orEmpty().isNotEmpty() }, editable) }
            ItineraryDayCard(
                day = day,
                rows = rows,
                edits = edits,
                notes = host.selections.itineraryNotes,
                showTransit = ItinerarySchedule.listedInScheduleOrder(day.day),
                slotRow = { slot ->
                    val key = slot.key.orEmpty()
                    EditableSlot(
                        slot = slot,
                        dayNumber = day.day.dayNumber,
                        edit = edits[key] ?: SlotEdit.NONE,
                        note = host.selections.itineraryNotes[key].orEmpty(),
                        targets = targets[key].orEmpty(),
                        editable = editable,
                        editing = editing == key,
                        noteOpen = noteOpen == key,
                        host = host,
                        cityOfDay = { n -> arranged.days.firstOrNull { it.dayNumber == n }?.city },
                        nameOf = nameOf,
                        onEditingChange = { open ->
                            editing = if (open) key else null
                            noteOpen = null
                        },
                        onNoteToggle = {
                            noteOpen = if (noteOpen == key) null else key
                            editing = null
                        },
                        onMove = { target ->
                            val name = nameOf(key, slot.slot.activity)
                            if (commit { ItineraryLayouts.moveSlot(data, it, key, target.dayNumber, target.timeOfDay) }) {
                                status = texts.moved(name, target, target.swapWith?.let { nameOf(it.key, it.activity) })
                            }
                        },
                        onRemove = {
                            val name = nameOf(key, slot.slot.activity)
                            if (commit { ItineraryLayouts.removeSlot(data, it, key) }) status = texts.removed(name, day.day.dayNumber)
                        },
                    )
                },
            )
        }

        if (arranged.removed.isNotEmpty()) {
            RemovedList(
                removed = arranged.removed.map { it.key to nameOf(it.key, it.slot.activity) },
                canRestore = { key -> editable && ItineraryLayouts.restoreSlot(data, layout, key) != layout },
                originalDay = { key ->
                    data.days.firstOrNull { d -> ItineraryLayouts.TIMES.any { ItineraryLayouts.slotKey(d.dayNumber, it) == key } }?.dayNumber
                },
                onRestore = { key, name -> if (commit { ItineraryLayouts.restoreSlot(data, it, key) }) status = texts.restored(name) },
                showRestore = editable,
            )
        }
    }
}

/** Move targets of every slot of the arranged itinerary, by slot key (one source for the menu and placeholders). */
private fun moveTargetsByKey(data: ItineraryData, layout: ItineraryLayout?, arranged: ArrangedItinerary): Map<String, List<MoveTarget>> =
    arranged.days.flatMap { it.slots }.mapNotNull { s -> s.key?.let { it to ItineraryLayouts.moveTargets(data, layout, it) } }.toMap()

@Composable
private fun ItineraryHeader(locked: Boolean, total: Double?, template: Boolean, timed: Boolean) {
    if (locked) MetaText(stringResource(R.string.itin_editing_locked))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        total?.takeIf { it > 0 }?.let { InfoPill(stringResource(R.string.itin_total_cost, localPrice(it)), tone = Tone.Accent, section = SectionId.ITINERARY) }
        if (template) InfoPill(stringResource(R.string.itin_template), tone = Tone.Warning)
    }
    if (timed) MetaText(stringResource(R.string.itin_times_note))
}

/** Resolved announcement templates (web status copy). */
private class EditorTexts(
    val undone: String,
    val reset: String,
    val moved: (name: String, target: MoveTarget, swap: String?) -> String,
    val removed: (name: String, day: Int?) -> String,
    val restored: (name: String) -> String,
)

@Composable
private fun editorTexts(): EditorTexts {
    val undone = stringResource(R.string.itin_status_undo)
    val reset = stringResource(R.string.itin_status_reset)
    val moved = stringResource(R.string.itin_status_moved)
    val movedSwap = stringResource(R.string.itin_status_moved_swap)
    val removed = stringResource(R.string.itin_status_removed)
    val restored = stringResource(R.string.itin_status_restored)
    val labels =
        mapOf(
            "morning" to timeOfDayLabel("morning"),
            "afternoon" to timeOfDayLabel("afternoon"),
            "evening" to timeOfDayLabel("evening"),
        )
    return EditorTexts(
        undone = undone,
        reset = reset,
        moved = { name, t, swap ->
            val tod = labels[t.timeOfDay] ?: t.timeOfDay
            if (swap != null) String.format(movedSwap, name, t.dayNumber ?: 0, tod, swap) else String.format(moved, name, t.dayNumber ?: 0, tod)
        },
        removed = { name, day -> String.format(removed, name, day ?: 0) },
        restored = { name -> String.format(restored, name) },
    )
}

@Composable
private fun ChangeBar(changeCount: Int, canUndo: Boolean, onUndo: () -> Unit, onReset: () -> Unit) {
    val group = stringResource(R.string.itin_changes_group)
    val colors = toneColors(Tone.Accent, SectionId.ITINERARY)
    Surface(shape = TravelShape.Input, color = colors.container, modifier = Modifier.fillMaxWidth().semantics { paneTitle = group }) {
        FlowRow(
            Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            if (changeCount >
                0
            ) {
                Text(
                    pluralStringResource(R.plurals.itin_change_count, changeCount, changeCount),
                    style = TravelTheme.typography.labelLarge,
                    color = colors.content,
                )
            }
            TpButton(
                stringResource(R.string.itin_undo),
                onClick = onUndo,
                enabled = canUndo,
                variant = TpButtonVariant.Text,
                leadingIcon = Icons.Filled.Refresh,
            )
            if (changeCount > 0) TpButton(stringResource(R.string.itin_reset), onClick = onReset, variant = TpButtonVariant.Text)
        }
    }
}

@Suppress("LongParameterList") // one row's full editing surface; grouped callbacks would only rename them
@Composable
private fun EditableSlot(
    slot: ScheduledSlot,
    dayNumber: Int?,
    edit: SlotEdit,
    note: String,
    targets: List<MoveTarget>,
    editable: Boolean,
    editing: Boolean,
    noteOpen: Boolean,
    host: ItineraryHost,
    cityOfDay: (Int?) -> String?,
    nameOf: (String?, String?) -> String,
    onEditingChange: (Boolean) -> Unit,
    onNoteToggle: () -> Unit,
    onMove: (MoveTarget) -> Unit,
    onRemove: () -> Unit,
) {
    val key = slot.key.orEmpty()
    val activity = edit.activityOr(slot.slot.activity).orEmpty()
    if (editing) {
        SlotEditor(slot.slot, key, edit, note, host) { onEditingChange(false) }
        return
    }
    Column {
        ItinerarySlotRow(slot, edit = edit, note = note) {
            val planItem = PlanItem.SlotItem(ItineraryEdits.plannedEntry(dayNumber, slot, edit))
            val inPlan = host.isInPlan(planItem)
            SmallAction(
                text = stringResource(if (edit.isEdited) R.string.itin_edited else R.string.itin_edit),
                a11y = stringResource(R.string.itin_edit_a11y, activity),
                icon = Icons.Filled.Edit,
                onClick = { onEditingChange(true) },
            )
            SmallAction(
                text = stringResource(if (note.isNotEmpty()) R.string.itin_note_set else R.string.itin_note),
                a11y = stringResource(if (note.isNotEmpty()) R.string.itin_note_edit_a11y else R.string.itin_note_add_a11y, activity),
                icon = Icons.Filled.Create,
                onClick = onNoteToggle,
            )
            if (editable && targets.isNotEmpty()) MoveMenu(activity, targets, cityOfDay, nameOf, onMove, onRemove)
            SmallAction(
                text = stringResource(if (inPlan) R.string.itin_in_plan else R.string.itin_plan),
                a11y = stringResource(if (inPlan) R.string.itin_plan_remove_a11y else R.string.itin_plan_add_a11y, activity),
                icon = if (inPlan) Icons.Filled.Check else Icons.Filled.Add,
                onClick = { host.onTogglePlan(planItem) },
                emphasized = inPlan,
            )
        }
        if (noteOpen) {
            // Local text (cursor-stable, no dropped characters); every change is pushed to the plan draft.
            var noteText by rememberSaveable(key) { mutableStateOf(note) }
            OutlinedTextField(
                value = noteText,
                onValueChange = { text ->
                    noteText = text
                    host.onUpdateSelections { ItineraryEdits.setNote(it, key, text) }
                },
                placeholder = { Text(stringResource(R.string.itin_note_placeholder_personal)) },
                label = { Text(stringResource(R.string.itin_field_note)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.lg, vertical = TravelTheme.spacing.sm),
            )
        }
    }
}

@Composable
private fun SmallAction(text: String, a11y: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, emphasized: Boolean = false) {
    TpButton(
        text,
        onClick = onClick,
        variant = if (emphasized) TpButtonVariant.Tonal else TpButtonVariant.Text,
        leadingIcon = icon,
        modifier = Modifier.semantics { contentDescription = a11y },
    )
}

@Composable
private fun MoveMenu(
    activity: String,
    targets: List<MoveTarget>,
    cityOfDay: (Int?) -> String?,
    nameOf: (String?, String?) -> String,
    onMove: (MoveTarget) -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.itin_move_a11y, activity)
    val menuState = stringResource(if (open) R.string.itin_menu_expanded else R.string.itin_menu_collapsed)
    Box {
        TpButton(
            stringResource(R.string.itin_move),
            onClick = { open = !open },
            variant = TpButtonVariant.Text,
            leadingIcon = Icons.Filled.MoreVert,
            modifier =
                Modifier.semantics {
                    contentDescription = label
                    stateDescription = menuState
                },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.semantics { paneTitle = label }) {
            targets.groupBy { it.dayNumber }.forEach { (day, items) ->
                val city = cityOfDay(day)
                Text(
                    if (city != null) stringResource(R.string.itin_menu_day_city, day ?: 0, city) else stringResource(R.string.itin_menu_day, day ?: 0),
                    style = TravelTheme.typography.labelMedium,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs).semantics { heading() },
                )
                items.forEach { t ->
                    val tod = timeOfDayLabel(t.timeOfDay)
                    val swap = t.swapWith?.let { nameOf(it.key, it.activity) }
                    val text = if (swap != null) stringResource(R.string.itin_menu_target_swap, tod, swap) else tod
                    val a11y =
                        if (swap != null) {
                            stringResource(R.string.itin_move_to_swap_a11y, t.dayNumber ?: 0, tod, swap)
                        } else {
                            stringResource(R.string.itin_move_to_a11y, t.dayNumber ?: 0, tod)
                        }
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            open = false
                            onMove(t)
                        },
                        modifier = Modifier.semantics { contentDescription = a11y },
                    )
                }
            }
            // destructive action last, as on the web
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.itin_remove_from_day), color = TravelTheme.colors.error) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

@Composable
private fun SlotEditor(slot: ItinerarySlot, key: String, edit: SlotEdit, note: String, host: ItineraryHost, onDone: () -> Unit) {
    // Local field state: the draft round-trips through a StateFlow, so fields never read their value back from it.
    var activityText by rememberSaveable(key) { mutableStateOf(edit.activity ?: slot.activity.orEmpty()) }
    var locationText by rememberSaveable(key) { mutableStateOf(edit.location ?: slot.location.orEmpty()) }
    var noteText by rememberSaveable(key) { mutableStateOf(note) }
    var costText by rememberSaveable(key) { mutableStateOf((edit.cost ?: slot.estimatedCostUsd)?.plain().orEmpty()) }
    Column(Modifier.fillMaxWidth().padding(TravelTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        OutlinedTextField(
            value = activityText,
            onValueChange = { v ->
                activityText = v
                host.onUpdateSelections { ItineraryEdits.setEdit(it, key, SlotEditFields.ACTIVITY, JsonPrimitive(v)) }
            },
            label = { Text(stringResource(R.string.itin_field_activity)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            OutlinedTextField(
                value = locationText,
                onValueChange = { v ->
                    locationText = v
                    host.onUpdateSelections { ItineraryEdits.setEdit(it, key, SlotEditFields.LOCATION, JsonPrimitive(v)) }
                },
                label = { Text(stringResource(R.string.itin_field_location)) },
                modifier = Modifier.weight(1f),
            )
            val locale = currentLocale()
            OutlinedTextField(
                value = costText,
                onValueChange = { v ->
                    costText = v
                    host.onUpdateSelections { ItineraryEdits.setEdit(it, key, SlotEditFields.COST, ItineraryEdits.costValue(v, locale)) }
                },
                label = { Text(stringResource(R.string.itin_field_cost)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = noteText,
            onValueChange = { v ->
                noteText = v
                host.onUpdateSelections { ItineraryEdits.setNote(it, key, v) }
            },
            label = { Text(stringResource(R.string.itin_field_note)) },
            placeholder = { Text(stringResource(R.string.itin_note_placeholder)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )
        TpButton(stringResource(R.string.itin_done), onClick = onDone, leadingIcon = Icons.Filled.Check)
    }
}

@Composable
private fun RemovedList(
    removed: List<Pair<String, String>>,
    canRestore: (String) -> Boolean,
    originalDay: (String) -> Int?,
    onRestore: (key: String, name: String) -> Unit,
    showRestore: Boolean,
) {
    Surface(shape = TravelShape.ItemCard, border = BorderStroke(InfoDimens.Border, TravelTheme.colors.outline), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            SubHeading(stringResource(R.string.itin_removed), color = TravelTheme.colors.onSurfaceVariant)
            removed.forEach { (key, name) ->
                val allowed = canRestore(key)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = TravelTheme.typography.bodyMedium)
                        if (showRestore && !allowed) {
                            Text(
                                stringResource(R.string.itin_restore_blocked, originalDay(key) ?: 0),
                                style = TravelTheme.typography.labelSmall,
                                color = TravelTheme.colors.onSurfaceVariant,
                            )
                        }
                    }
                    if (showRestore) {
                        val a11y = stringResource(R.string.itin_restore_a11y, name)
                        TpButton(
                            stringResource(R.string.itin_restore),
                            onClick = { onRestore(key, name) },
                            enabled = allowed,
                            variant = TpButtonVariant.Text,
                            leadingIcon = Icons.Filled.Refresh,
                            modifier = Modifier.semantics { contentDescription = a11y },
                        )
                    }
                }
            }
        }
    }
}

private val previewItinerary =
    ItineraryData(
        days =
            listOf(
                ItineraryDay(
                    dayNumber = 1,
                    date = "2027-05-01",
                    city = "Lisbon",
                    theme = "Alfama",
                    slots =
                        listOf(
                            ItinerarySlot(timeOfDay = "morning", activity = "São Jorge Castle", location = "Alfama", estimatedCostUsd = 15.0),
                            ItinerarySlot(timeOfDay = "afternoon", activity = "Tram 28", estimatedCostUsd = 3.0),
                            ItinerarySlot(timeOfDay = "evening", activity = "Fado dinner", estimatedCostUsd = 40.0),
                        ),
                ),
            ),
        totalEstimatedCostUsd = 58.0,
    )

@Composable
private fun PreviewItinerary(mode: ThemeMode, locked: Boolean) =
    PreviewSurface(mode) { ItineraryBody(previewItinerary, ItineraryHost(PlanSelections.EMPTY, locked, { false }, {}, {})) }

@Preview(name = "Itinerary – light")
@Composable
private fun ItineraryLightPreview() = PreviewItinerary(ThemeMode.LIGHT, locked = false)

@Preview(name = "Itinerary – dark, locked")
@Composable
private fun ItineraryDarkPreview() = PreviewItinerary(ThemeMode.DARK, locked = true)
