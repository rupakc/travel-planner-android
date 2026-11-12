package io.github.rupakc.travelplanner.feature.onboarding.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingEvent
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingUiState
import io.github.rupakc.travelplanner.feature.onboarding.R

/** Step 3: home airport (kept on the device) and passport (saved to Preferences). Both optional. */
@Composable
internal fun HomePassportStep(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    askNotifications: Boolean,
    modifier: Modifier = Modifier,
) {
    OnboardingFrame(
        modifier = modifier,
        onBack = { onEvent(OnboardingEvent.Back) }.takeUnless { state.saving },
        onSkipAll = skipAllAction(state, onEvent),
        actions = {
            TpButton(
                stringResource(if (state.saving) R.string.onboarding_saving else R.string.onboarding_continue),
                { onEvent(OnboardingEvent.HomeNext(askNotifications)) },
                Modifier.fillMaxWidth(),
                loading = state.saving,
            )
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            if (state.resumed) ResumeNote()
            StepIntro(R.string.onboarding_home_title, stringResource(R.string.onboarding_home_message))
            OutlinedTextField(
                value = state.homeAirport,
                onValueChange = { onEvent(OnboardingEvent.SetHomeAirport(it)) },
                label = { Text(stringResource(R.string.onboarding_home_airport)) },
                placeholder = { Text(stringResource(R.string.onboarding_home_airport_placeholder)) },
                supportingText = { Text(stringResource(R.string.onboarding_home_airport_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            state.airportSuggestions.forEach { airport ->
                val code = airport.iataCode.orEmpty()
                val place = listOfNotNull(airport.city ?: airport.name, airport.country).joinToString(", ")
                SuggestionRow(listOf(code, place).filter { it.isNotBlank() }.joinToString(" - ")) { onEvent(OnboardingEvent.PickHomeAirport(airport)) }
            }
            OutlinedTextField(
                value = state.passport,
                onValueChange = { onEvent(OnboardingEvent.SetPassport(it)) },
                label = { Text(stringResource(R.string.onboarding_passport)) },
                placeholder = { Text(stringResource(R.string.onboarding_passport_placeholder)) },
                supportingText = { Text(stringResource(R.string.onboarding_passport_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            state.passportSuggestions.forEach { nationality ->
                val label = nationality.nationality.orEmpty()
                if (label.isNotBlank()) SuggestionRow(label) { onEvent(OnboardingEvent.PickPassport(nationality)) }
            }
        }
    }
}

/** Step 5: first trip or explore. Finishing saves the preferences (Retry / Continue without syncing on failure). */
@Composable
internal fun FirstTripStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, modifier: Modifier = Modifier) {
    OnboardingFrame(
        modifier = modifier,
        onSkipAll = skipAllAction(state, onEvent),
        actions = {
            TpButton(
                stringResource(R.string.onboarding_just_explore),
                { onEvent(OnboardingEvent.Complete(planTrip = false)) },
                variant = TpButtonVariant.Text,
                enabled = !state.saving,
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TpButton(
                    stringResource(if (state.saving) R.string.onboarding_saving else R.string.onboarding_plan_first_trip),
                    { onEvent(OnboardingEvent.Complete(planTrip = true)) },
                    loading = state.saving,
                )
            }
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
        ) {
            if (state.resumed) ResumeNote()
            StepIntro(
                R.string.onboarding_first_trip_title,
                stringResource(R.string.onboarding_first_trip_message, stringResource(io.github.rupakc.travelplanner.core.ui.R.string.brand_name)),
            )
            state.syncError?.let { SyncErrorCard(it.message, onEvent) }
        }
    }
}

@Composable
private fun StepIntro(title: Int, message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(stringResource(title), style = TravelTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(message, style = TravelTheme.typography.bodyLarge, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun ResumeNote() {
    Text(
        stringResource(R.string.onboarding_resume),
        style = TravelTheme.typography.bodyMedium,
        color = TravelTheme.colors.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun SuggestionRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TravelTheme.spacing.minTouchTarget)
            .clip(TravelShape.ItemCard)
            .clickable(onClick = onClick)
            .padding(horizontal = TravelTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant, modifier = Modifier.size(SUGGESTION_ICON))
        Text(text, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurface)
    }
}

private val SUGGESTION_ICON = 20.dp
