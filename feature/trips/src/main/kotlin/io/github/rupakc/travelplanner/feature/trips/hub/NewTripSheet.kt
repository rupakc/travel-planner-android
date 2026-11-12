package io.github.rupakc.travelplanner.feature.trips.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.trips.R

/** "Save an idea": title, optional destination and optional dates. Field errors come from [validateNewTrip] or the server code. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewTripSheetContent(sheet: NewTripSheet, onEvent: (TripsEvent) -> Unit) {
    ModalBottomSheet(onDismissRequest = { onEvent(TripsEvent.DismissCreate) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            Text(stringResource(R.string.trips_create_heading), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            val titleError = sheet.error == NewTripError.Title
            OutlinedTextField(
                value = sheet.title,
                onValueChange = { onEvent(TripsEvent.SetTitle(it)) },
                label = { Text(stringResource(R.string.trips_create_title)) },
                isError = titleError,
                supportingText = if (titleError) ({ Text(stringResource(R.string.trips_title_invalid)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = sheet.destination,
                onValueChange = { onEvent(TripsEvent.SetDestination(it)) },
                label = { Text(stringResource(R.string.trips_create_destination)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            val datesError = sheet.error == NewTripError.Dates
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
                DateField(sheet.start, R.string.trips_start_date, datesError, { onEvent(TripsEvent.SetStart(it)) }, Modifier.weight(1f))
                DateField(sheet.end, R.string.trips_end_date, datesError, { onEvent(TripsEvent.SetEnd(it)) }, Modifier.weight(1f))
            }
            if (datesError) {
                Text(
                    stringResource(R.string.trips_dates_invalid),
                    color = TravelTheme.colors.error,
                    style = TravelTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (sheet.failed) {
                Text(
                    sheet.message ?: stringResource(R.string.trips_create_failed),
                    color = TravelTheme.colors.error,
                    style = TravelTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Row(Modifier.fillMaxWidth().padding(bottom = TravelTheme.spacing.lg), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
                TpButton(
                    stringResource(R.string.trips_create_cancel),
                    { onEvent(TripsEvent.DismissCreate) },
                    Modifier.weight(1f),
                    variant = TpButtonVariant.Secondary,
                    tid = "trips.create.cancel",
                )
                TpButton(
                    stringResource(R.string.trips_create_save),
                    { onEvent(TripsEvent.SubmitCreate) },
                    Modifier.weight(1f),
                    loading = sheet.saving,
                    tid = "trips.create.save",
                )
            }
        }
    }
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
