package io.github.rupakc.travelplanner.feature.trips.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.trips.R

/** The dialog open over the trip screen (rename, dates, delete and "replace the unsaved plan" confirmations). */
@Composable
internal fun TripDialogHost(dialog: TripDialog?, tripTitle: String, onEvent: (TripEvent) -> Unit) {
    when (dialog) {
        null -> {
            Unit
        }

        is TripDialog.Rename -> {
            RenameDialog(dialog, onEvent)
        }

        is TripDialog.Dates -> {
            DatesDialog(dialog, onEvent)
        }

        TripDialog.ConfirmDelete -> {
            ConfirmDialog(
                text = stringResource(R.string.trip_delete_confirm, tripTitle),
                confirm = stringResource(R.string.trip_delete),
                onConfirm = { onEvent(TripEvent.ConfirmDelete) },
                onDismiss = { onEvent(TripEvent.DismissDialog) },
                tid = "trip.delete.confirm",
            )
        }

        TripDialog.ConfirmReplaceDraft -> {
            ConfirmDialog(
                text = stringResource(R.string.trip_discard_draft),
                confirm = stringResource(R.string.trip_open_planner),
                onConfirm = { onEvent(TripEvent.ConfirmOpenPlanner) },
                onDismiss = { onEvent(TripEvent.DismissDialog) },
                tid = "trip.planner.confirm",
            )
        }
    }
}

@Composable
private fun ConfirmDialog(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, tid: String) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = { TpButton(confirm, onConfirm, tid = tid) },
        dismissButton = { TpButton(stringResource(R.string.trip_cancel), onDismiss, variant = TpButtonVariant.Text, tid = "$tid.cancel") },
    )
}

@Composable
private fun RenameDialog(dialog: TripDialog.Rename, onEvent: (TripEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(TripEvent.DismissDialog) },
        title = { Text(stringResource(R.string.trip_rename)) },
        text = {
            OutlinedTextField(
                value = dialog.title,
                onValueChange = { onEvent(TripEvent.EditTitle(it)) },
                label = { Text(stringResource(R.string.trip_title_label)) },
                isError = dialog.invalid,
                supportingText = if (dialog.invalid) ({ Text(stringResource(R.string.trips_title_invalid)) }) else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TpButton(stringResource(R.string.trip_save), { onEvent(TripEvent.SubmitDialog) }, loading = dialog.saving, tid = "trip.rename.save")
        },
        dismissButton = {
            TpButton(stringResource(R.string.trip_cancel), { onEvent(TripEvent.DismissDialog) }, variant = TpButtonVariant.Text, tid = "trip.rename.cancel")
        },
    )
}

@Composable
private fun DatesDialog(dialog: TripDialog.Dates, onEvent: (TripEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(TripEvent.DismissDialog) },
        title = { Text(stringResource(R.string.trip_dates_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    DateField(dialog.start, R.string.trip_dates_start, dialog.invalid, { onEvent(TripEvent.EditStart(it)) }, Modifier.weight(1f))
                    DateField(dialog.end, R.string.trip_dates_end, dialog.invalid, { onEvent(TripEvent.EditEnd(it)) }, Modifier.weight(1f))
                }
                Text(stringResource(R.string.trip_dates_end_hint), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                if (dialog.invalid) {
                    Text(
                        stringResource(R.string.trips_dates_invalid),
                        color = TravelTheme.colors.error,
                        style = TravelTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                TpButton(stringResource(R.string.trip_dates_clear), { onEvent(TripEvent.ClearDates) }, variant = TpButtonVariant.Text, tid = "trip.dates.clear")
            }
        },
        confirmButton = {
            TpButton(stringResource(R.string.trip_dates_save), { onEvent(TripEvent.SubmitDialog) }, loading = dialog.saving, tid = "trip.dates.save")
        },
        dismissButton = {
            TpButton(stringResource(R.string.trip_cancel), { onEvent(TripEvent.DismissDialog) }, variant = TpButtonVariant.Text, tid = "trip.dates.cancel")
        },
    )
}

@Composable
private fun DateField(value: String, label: Int, error: Boolean, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(R.string.trips_date_hint)) },
        isError = error,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
