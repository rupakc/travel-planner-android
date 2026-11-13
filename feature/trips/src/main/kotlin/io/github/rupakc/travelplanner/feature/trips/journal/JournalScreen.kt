package io.github.rupakc.travelplanner.feature.trips.journal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.TpCheckbox
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.trips.JournalMoods
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.feature.trips.R
import io.github.rupakc.travelplanner.feature.trips.trip.TripTabContent

/** The Journal tab for `TripTabContent` (`tripsGraph(tabContent = mapOf(TripTab.Journal to journalTabContent()))`). */
fun journalTabContent(): TripTabContent = { _: Trip, modifier -> JournalRoute(modifier) }

@Composable
fun JournalRoute(modifier: Modifier = Modifier, viewModel: JournalViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    JournalScreen(state, viewModel::onEvent, modifier)
}

@Composable
fun JournalScreen(state: JournalUiState, onEvent: (JournalEvent) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.loading -> {
            SkeletonList(modifier, message = stringResource(R.string.trip_loading))
        }

        state.failed -> {
            ErrorState(null, modifier, title = stringResource(R.string.journal_load_failed), onRetry = {
                onEvent(JournalEvent.Retry)
            }, tid = "trips.journal_retry")
        }

        else -> {
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(TravelTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
            ) {
                item(key = "title", contentType = "title") {
                    Text(stringResource(R.string.journal_title), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                state.failure?.let { f -> item(key = "failure", contentType = "failure") { FailureBanner(f, onEvent) } }
                item(key = "composer", contentType = "composer") { Composer(state, onEvent) }
                if (state.rows.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Text(stringResource(R.string.journal_empty), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
                    }
                }
                items(state.rows, key = { "e:" + (it.entry.clientId ?: it.entry.id) }, contentType = { "entry" }) { row -> EntryCard(row, onEvent) }
                item(key = "places-title", contentType = "title") {
                    Text(stringResource(R.string.journal_places), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                }
                if (state.places.isEmpty()) {
                    item(key = "places-empty", contentType = "empty") {
                        Text(
                            stringResource(R.string.journal_places_empty),
                            style = TravelTheme.typography.bodyMedium,
                            color = TravelTheme.colors.onSurfaceVariant,
                        )
                    }
                }
                items(state.places, key = { "p:" + it.key }, contentType = { "place" }) { place -> PlaceCard(place, onEvent) }
            }
        }
    }
}

@Composable
private fun FailureBanner(failure: JournalFailure, onEvent: (JournalEvent) -> Unit) {
    val text =
        when (failure.problem) {
            JournalRules.Problem.JournalLimit -> stringResource(R.string.journal_limit)
            JournalRules.Problem.PlacesLimit -> stringResource(R.string.places_limit)
            JournalRules.Problem.Other -> failure.message ?: stringResource(if (failure.places) R.string.journal_place_failed else R.string.journal_save_failed)
        }
    Surface(color = TravelTheme.colors.errorContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                color = TravelTheme.colors.onErrorContainer,
                style = TravelTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TpButton(stringResource(R.string.journal_dismiss), {
                onEvent(JournalEvent.DismissFailure)
            }, variant = TpButtonVariant.Text, tid = "journal.dismiss")
        }
    }
}

@Composable
private fun Composer(state: JournalUiState, onEvent: (JournalEvent) -> Unit) {
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            val tooLong = state.draft.length > JournalRules.MAX_BODY
            TpOutlinedTextField(
                value = state.draft,
                onValueChange = { onEvent(JournalEvent.DraftChanged(it)) },
                label = { Text(stringResource(R.string.journal_hint)) },
                isError = tooLong,
                supportingText = if (tooLong) ({ Text(stringResource(R.string.journal_too_long)) }) else null,
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                tid = "trips.journal_hint",
            )
            Text(stringResource(R.string.journal_mood), style = TravelTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                JournalMoods.ALL.forEach { mood ->
                    TpChip(
                        label = moodLabel(mood),
                        selected = state.mood == mood,
                        onClick = { onEvent(JournalEvent.MoodChanged(if (state.mood == mood) null else mood)) },
                        tid = "journal.mood.$mood",
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                TpButton(
                    stringResource(if (state.editingId != null) R.string.journal_update else R.string.journal_save),
                    { onEvent(JournalEvent.Save) },
                    enabled = state.canSave,
                    loading = state.saving,
                    tid = "journal.save",
                )
                if (state.editingId != null) {
                    TpButton(stringResource(R.string.journal_cancel), {
                        onEvent(JournalEvent.CancelEdit)
                    }, variant = TpButtonVariant.Text, tid = "journal.cancel")
                }
            }
        }
    }
}

@Composable
private fun moodLabel(mood: String): String =
    stringResource(
        when (mood) {
            "great" -> R.string.journal_mood_great
            "good" -> R.string.journal_mood_good
            "ok" -> R.string.journal_mood_ok
            else -> R.string.journal_mood_tough
        },
    )

@Composable
private fun EntryCard(row: JournalRules.Row, onEvent: (JournalEvent) -> Unit) {
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                row.entry.day?.let { Text(it, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant) }
                row.entry.mood?.takeIf { it in JournalMoods.ALL }?.let { Text(moodLabel(it), style = TravelTheme.typography.labelMedium) }
            }
            Text(row.entry.body, style = TravelTheme.typography.bodyMedium)
            if (row.pending) {
                Text(stringResource(R.string.journal_waiting), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    TpButton(stringResource(R.string.journal_edit), {
                        onEvent(JournalEvent.StartEdit(row.entry.id))
                    }, variant = TpButtonVariant.Text, tid = "journal.edit")
                    TpButton(stringResource(R.string.journal_delete), {
                        onEvent(JournalEvent.Delete(row.entry.id))
                    }, variant = TpButtonVariant.Text, tid = "journal.delete")
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(place: JournalRules.PlaceRow, onEvent: (JournalEvent) -> Unit) {
    var noteOpen by rememberSaveable(place.key) { mutableStateOf(false) }
    var note by remember(place.key, place.note) { mutableStateOf(place.note.orEmpty()) }
    val visitedLabel = stringResource(R.string.journal_visited, place.name)
    val starsValue = stringResource(R.string.journal_stars_value, place.stars ?: 0)
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TpCheckbox(
                    checked = place.visited,
                    onCheckedChange = { onEvent(JournalEvent.SetVisited(place.key, it)) },
                    modifier = Modifier.semantics { contentDescription = visitedLabel },
                    tid = "trips.journal_screen_checkbox",
                )
                Text(place.name, style = TravelTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            if (place.pending) {
                Text(stringResource(R.string.journal_waiting), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            Row(
                Modifier.semantics { stateDescription = starsValue },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (n in JournalRules.MIN_STARS..JournalRules.MAX_STARS) {
                    val label = stringResource(R.string.journal_stars, place.name, n)
                    TpIconButton(onClick = {
                        onEvent(JournalEvent.Rate(place.key, n))
                    }, modifier = Modifier.semantics { contentDescription = label }, tid = "trips.journal_screen_icon_button") {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = if ((place.stars ?: 0) >= n) TravelTheme.extendedColors.warning else TravelTheme.colors.outlineVariant,
                        )
                    }
                }
            }
            if (!noteOpen) {
                TpButton(stringResource(R.string.journal_note), { noteOpen = true }, variant = TpButtonVariant.Text, tid = "journal.place.note")
                place.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.bodySmall) }
            } else {
                TpOutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(JournalRules.MAX_NOTE) },
                    label = { Text(stringResource(R.string.journal_note_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    tid = "trips.journal_note_label",
                )
                TpButton(
                    stringResource(R.string.journal_note_save),
                    {
                        onEvent(JournalEvent.SaveNote(place.key, note))
                        noteOpen = false
                    },
                    tid = "journal.place.note.save",
                )
            }
        }
    }
}
