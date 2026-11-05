package io.github.rupakc.travelplanner.feature.settings.preferences

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.globalsearch.SettingsEntry
import io.github.rupakc.travelplanner.core.model.preferences.Preferences
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.settings.R
import io.github.rupakc.travelplanner.feature.settings.common.KeyedSettingsList
import io.github.rupakc.travelplanner.feature.settings.common.SettingsHeading
import io.github.rupakc.travelplanner.feature.settings.common.SettingsNote
import io.github.rupakc.travelplanner.feature.settings.common.SettingsRow
import io.github.rupakc.travelplanner.feature.settings.common.SettingsScaffold
import io.github.rupakc.travelplanner.feature.settings.common.highlighted

/** Item keys in order; settings entry keys double as global-search scroll targets. */
private val PreferenceKeys =
    listOf(
        "taste",
        "h_budget",
        SettingsEntry.BUDGET.key,
        "h_identity",
        SettingsEntry.NATIONALITY.key,
        SettingsEntry.RESIDENCE.key,
        SettingsEntry.EMAIL.key,
        "h_documents",
        SettingsEntry.DOCUMENTS.key,
        "h_style",
        SettingsEntry.TRAVEL_GROUP.key,
        SettingsEntry.INTERESTS.key,
        "save",
    )

@Composable
internal fun PreferencesRoute(highlightKey: String?, onBack: () -> Unit, onOpenTaste: () -> Unit, viewModel: PreferencesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PreferencesScreen(state, viewModel::onEvent, onBack = onBack, onOpenTaste = onOpenTaste, highlightKey = highlightKey)
}

/** Travel preferences form (stateless), web PreferencesPage order and copy. */
@Composable
fun PreferencesScreen(
    state: PreferencesUiState,
    onEvent: (PreferencesEvent) -> Unit,
    onBack: () -> Unit,
    onOpenTaste: () -> Unit,
    modifier: Modifier = Modifier,
    highlightKey: String? = null,
) {
    SettingsScaffold(stringResource(R.string.settings_prefs_title), onBack = onBack, modifier = modifier) { padding ->
        when {
            state.loading -> {
                SkeletonList(Modifier.padding(TravelTheme.spacing.gutter), message = stringResource(R.string.settings_prefs_loading), lines = 6)
            }

            state.loadError != null -> {
                ErrorState(
                    message = state.loadError.message ?: stringResource(R.string.settings_prefs_load_failed),
                    onRetry = { onEvent(PreferencesEvent.Retry) },
                )
            }

            else -> {
                KeyedSettingsList(PreferenceKeys, highlightKey, contentPadding = padding) {
                    items(PreferenceKeys.size, key = { PreferenceKeys[it] }, contentType = { PreferenceKeys[it] }) { index ->
                        val key = PreferenceKeys[index]
                        PreferenceItem(key, state, onEvent, onOpenTaste, Modifier.highlighted(key == highlightKey))
                    }
                }
            }
        }
    }
}

@Composable
private fun PreferenceItem(key: String, state: PreferencesUiState, onEvent: (PreferencesEvent) -> Unit, onOpenTaste: () -> Unit, modifier: Modifier) {
    val prefs = state.prefs
    val pad = Modifier.padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm)
    when (key) {
        "taste" -> {
            Column {
                SettingsNote(stringResource(R.string.settings_prefs_subtitle))
                SettingsRow(
                    stringResource(R.string.settings_taste_title),
                    subtitle = stringResource(R.string.settings_taste_heading),
                    icon = Icons.Filled.Favorite,
                    onClick = onOpenTaste,
                )
            }
        }

        "h_budget" -> {
            SettingsHeading(stringResource(R.string.settings_prefs_section_budget))
        }

        SettingsEntry.BUDGET.key -> {
            BudgetOptions(prefs.budgetCategory, onEvent, modifier.then(pad))
        }

        "h_identity" -> {
            SettingsHeading(stringResource(R.string.settings_prefs_section_identity))
        }

        SettingsEntry.NATIONALITY.key -> {
            NationalityField(prefs.nationality, state.nationalitySuggestions, onEvent, modifier.then(pad))
        }

        SettingsEntry.RESIDENCE.key -> {
            OutlinedTextField(
                value = prefs.currentResidence,
                onValueChange = { onEvent(PreferencesEvent.ResidenceChanged(it)) },
                label = { Text(stringResource(R.string.settings_prefs_residence)) },
                placeholder = { Text(stringResource(R.string.settings_prefs_residence_placeholder)) },
                supportingText = { Text(stringResource(R.string.settings_prefs_residence_help)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = modifier.then(pad).fillMaxWidth(),
            )
        }

        SettingsEntry.EMAIL.key -> {
            EmailField(state, onEvent, modifier.then(pad))
        }

        "h_documents" -> {
            SettingsHeading(stringResource(R.string.settings_prefs_section_documents))
        }

        SettingsEntry.DOCUMENTS.key -> {
            Column(modifier.then(pad), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
                TagInput(
                    label = stringResource(R.string.settings_prefs_permits),
                    placeholder = stringResource(R.string.settings_prefs_permits_placeholder),
                    tags = prefs.residencePermits,
                    input = state.permitInput,
                    onInputChange = { onEvent(PreferencesEvent.PermitInputChanged(it)) },
                    onAdd = { onEvent(PreferencesEvent.AddPermits(it)) },
                    onRemove = { onEvent(PreferencesEvent.RemovePermit(it)) },
                )
                TagInput(
                    label = stringResource(R.string.settings_prefs_visas),
                    placeholder = stringResource(R.string.settings_prefs_visas_placeholder),
                    tags = prefs.existingVisas,
                    input = state.visaInput,
                    onInputChange = { onEvent(PreferencesEvent.VisaInputChanged(it)) },
                    onAdd = { onEvent(PreferencesEvent.AddVisas(it)) },
                    onRemove = { onEvent(PreferencesEvent.RemoveVisa(it)) },
                )
            }
        }

        "h_style" -> {
            SettingsHeading(stringResource(R.string.settings_prefs_section_style))
        }

        SettingsEntry.TRAVEL_GROUP.key -> {
            TravelGroup(prefs, onEvent, modifier.then(pad))
        }

        SettingsEntry.INTERESTS.key -> {
            Interests(prefs.interests, onEvent, modifier.then(pad))
        }

        "save" -> {
            SaveBar(state, onEvent, pad)
        }
    }
}

@Composable
private fun BudgetOptions(selected: String, onEvent: (PreferencesEvent) -> Unit, modifier: Modifier) {
    Column(modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(stringResource(R.string.settings_prefs_budget_label), style = TravelTheme.typography.labelLarge)
        BudgetCategories.forEach { category ->
            val isSelected = category == selected
            TpCard(
                selected = isSelected,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onEvent(PreferencesEvent.SetBudget(category)) }),
            ) {
                Column(Modifier.padding(TravelTheme.spacing.md)) {
                    Text(stringResource(budgetLabelRes(category)), style = TravelTheme.typography.titleSmall)
                    Text(stringResource(budgetDescriptionRes(category)), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NationalityField(value: String, suggestions: List<Nationality>, onEvent: (PreferencesEvent) -> Unit, modifier: Modifier) {
    Column(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { onEvent(PreferencesEvent.NationalityChanged(it)) },
            label = { Text(stringResource(R.string.settings_prefs_nationality)) },
            placeholder = { Text(stringResource(R.string.settings_prefs_nationality_placeholder)) },
            singleLine = true,
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onEvent(PreferencesEvent.NationalityChanged("")) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.settings_prefs_nationality_clear))
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            TpCard(Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.xs)) {
                suggestions.forEach { nat ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = TravelTheme.spacing.minTouchTarget)
                            .clickable(role = Role.Button) { onEvent(PreferencesEvent.NationalityPicked(nat)) }
                            .padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.sm),
                    ) {
                        Text(nat.nationality.orEmpty(), style = TravelTheme.typography.bodyLarge)
                        nat.country?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailField(state: PreferencesUiState, onEvent: (PreferencesEvent) -> Unit, modifier: Modifier) {
    val status = state.emailStatus
    val help =
        when {
            status == EmailStatus.Invalid -> stringResource(R.string.settings_prefs_email_invalid)
            status is EmailStatus.Failed -> status.error.message ?: stringResource(R.string.settings_prefs_email_failed)
            status == EmailStatus.Saved -> stringResource(R.string.settings_prefs_email_saved)
            state.emailAlertsAvailable -> stringResource(R.string.settings_prefs_email_help)
            else -> stringResource(R.string.settings_prefs_email_unavailable)
        }
    val isError = status == EmailStatus.Invalid || status is EmailStatus.Failed
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        OutlinedTextField(
            value = state.email,
            onValueChange = { onEvent(PreferencesEvent.EmailChanged(it)) },
            label = { Text(stringResource(R.string.settings_prefs_email)) },
            placeholder = { Text(stringResource(R.string.settings_prefs_email_placeholder)) },
            isError = isError,
            supportingText = { Text(help, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(PreferencesEvent.SaveEmail) }),
            modifier = Modifier.fillMaxWidth(),
        )
        TpButton(
            stringResource(R.string.settings_prefs_email_save),
            onClick = { onEvent(PreferencesEvent.SaveEmail) },
            variant = TpButtonVariant.Secondary,
            loading = state.emailSaving,
        )
    }
}

/** Web TagInput: chips + a field; Enter, comma or leaving the field adds, the chip's ✕ removes. Pending text lives in the ViewModel. */
@Composable
private fun TagInput(
    label: String,
    placeholder: String,
    tags: List<String>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(label, style = TravelTheme.typography.labelLarge)
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                tags.forEach { tag ->
                    val remove = stringResource(R.string.settings_prefs_tag_remove, tag)
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null) },
                        modifier = Modifier.semantics { contentDescription = remove },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            trailingIcon = {
                if (input.isNotBlank()) {
                    IconButton(onClick = {
                        onAdd(input)
                    }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_prefs_tag_add, input.trim())) }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions =
                KeyboardActions(onDone = { onAdd(input) }),
            supportingText = { Text(stringResource(R.string.settings_prefs_tag_help)) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && input.isNotBlank()) onAdd(input) },
        )
    }
}

@Composable
private fun TravelGroup(prefs: Preferences, onEvent: (PreferencesEvent) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(stringResource(R.string.settings_prefs_group), style = TravelTheme.typography.labelLarge)
        TravellerField.entries.forEach { field -> Stepper(prefs, field, onEvent) }
        HorizontalDivider(Modifier.padding(vertical = TravelTheme.spacing.sm))
        Text(stringResource(R.string.settings_prefs_access), style = TravelTheme.typography.labelLarge)
        AccessibilityIds.forEach { id ->
            val checked = id in prefs.accessibilityNeeds
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TravelTheme.spacing.minTouchTarget)
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onEvent(PreferencesEvent.ToggleAccessibility(id)) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null)
                Spacer(Modifier.width(TravelTheme.spacing.sm))
                Text(stringResource(accessibilityLabelRes(id)), style = TravelTheme.typography.bodyMedium)
            }
        }
        SettingsNote(stringResource(R.string.settings_prefs_group_help), Modifier.padding(top = TravelTheme.spacing.xs))
    }
}

@Composable
private fun Stepper(prefs: Preferences, field: TravellerField, onEvent: (PreferencesEvent) -> Unit) {
    val count = PreferencesRules.count(prefs, field)
    val label = stringResource(field.labelRes())
    Row(Modifier.fillMaxWidth().heightIn(min = TravelTheme.spacing.minTouchTarget), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = TravelTheme.typography.bodyLarge)
            Text(stringResource(field.ageRes()), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        IconButton(onClick = { onEvent(PreferencesEvent.ChangeCount(field, -1)) }, enabled = PreferencesRules.canDecrease(prefs, field)) {
            Icon(painterResource(R.drawable.settings_ic_remove), contentDescription = stringResource(R.string.settings_prefs_decrease, label))
        }
        val countDescription = stringResource(R.string.settings_prefs_count_a11y, label, count)
        Text(
            count.toString(),
            style = TravelTheme.typography.titleMedium,
            modifier =
                Modifier.semantics {
                    contentDescription = countDescription
                    liveRegion = LiveRegionMode.Polite
                },
        )
        IconButton(onClick = { onEvent(PreferencesEvent.ChangeCount(field, 1)) }, enabled = PreferencesRules.canIncrease(prefs)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.settings_prefs_increase, label))
        }
    }
}

@Composable
private fun Interests(selected: List<String>, onEvent: (PreferencesEvent) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(stringResource(R.string.settings_prefs_interests), style = TravelTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            InterestIds.forEach { id ->
                TpChip(label = stringResource(interestLabelRes(id)), selected = id in selected, onClick = { onEvent(PreferencesEvent.ToggleInterest(id)) })
            }
        }
    }
}

@Composable
private fun SaveBar(state: PreferencesUiState, onEvent: (PreferencesEvent) -> Unit, modifier: Modifier) {
    val status = state.saveStatus
    Column(modifier.padding(top = TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        TpButton(
            if (state.saving) stringResource(R.string.settings_prefs_saving) else stringResource(R.string.settings_prefs_save),
            onClick = { onEvent(PreferencesEvent.Save) },
            loading = state.saving,
            modifier = Modifier.fillMaxWidth(),
        )
        val message =
            when (status) {
                SaveStatus.Saved -> {
                    stringResource(R.string.settings_prefs_saved)
                }

                is SaveStatus.Failed -> {
                    status.error.message?.let { stringResource(R.string.settings_prefs_save_error, it) }
                        ?: stringResource(R.string.settings_prefs_save_failed)
                }

                null -> {
                    null
                }
            }
        Text(
            message.orEmpty(),
            style = TravelTheme.typography.bodyMedium,
            color = if (status is SaveStatus.Failed) TravelTheme.colors.error else TravelTheme.extendedColors.success,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

internal fun budgetLabelRes(category: String): Int =
    when (category) {
        "low" -> R.string.settings_budget_low
        "high" -> R.string.settings_budget_high
        else -> R.string.settings_budget_medium
    }

private fun budgetDescriptionRes(category: String): Int =
    when (category) {
        "low" -> R.string.settings_budget_low_desc
        "high" -> R.string.settings_budget_high_desc
        else -> R.string.settings_budget_medium_desc
    }

private fun TravellerField.labelRes(): Int =
    when (this) {
        TravellerField.ADULTS -> R.string.settings_count_adults
        TravellerField.SENIORS -> R.string.settings_count_seniors
        TravellerField.CHILDREN -> R.string.settings_count_children
        TravellerField.INFANTS -> R.string.settings_count_infants
    }

private fun TravellerField.ageRes(): Int =
    when (this) {
        TravellerField.ADULTS -> R.string.settings_count_adults_age
        TravellerField.SENIORS -> R.string.settings_count_seniors_age
        TravellerField.CHILDREN -> R.string.settings_count_children_age
        TravellerField.INFANTS -> R.string.settings_count_infants_age
    }

private fun accessibilityLabelRes(id: String): Int =
    when (id) {
        "wheelchair" -> R.string.settings_access_wheelchair
        "visual_impairment" -> R.string.settings_access_visual
        "hearing_impairment" -> R.string.settings_access_hearing
        else -> R.string.settings_access_cognitive
    }

internal fun interestLabelRes(id: String): Int =
    when (id) {
        "food" -> R.string.settings_interest_food
        "history" -> R.string.settings_interest_history
        "adventure" -> R.string.settings_interest_adventure
        "culture" -> R.string.settings_interest_culture
        "nature" -> R.string.settings_interest_nature
        "shopping" -> R.string.settings_interest_shopping
        "nightlife" -> R.string.settings_interest_nightlife
        "wellness" -> R.string.settings_interest_wellness
        "art" -> R.string.settings_interest_art
        else -> R.string.settings_interest_family
    }

private val previewPrefs =
    PreferencesUiState(
        loading = false,
        prefs =
            Preferences(
                budgetCategory = "medium",
                nationality = "American",
                residencePermits = listOf("Schengen"),
                interests = listOf("food", "art"),
                adults = 2,
                children = 1,
                accessibilityNeeds = listOf("wheelchair"),
            ),
        email = "alice@example.com",
        saveStatus = SaveStatus.Saved,
    )

@Preview(name = "Preferences light", widthDp = 380, heightDp = 2200)
@Composable
private fun PreferencesPreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { PreferencesScreen(previewPrefs, onEvent = {}, onBack = {}, onOpenTaste = {}) }
}

@Preview(name = "Preferences dark error", widthDp = 380, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreferencesPreviewDark() {
    TravelTheme(themeMode = ThemeMode.DARK) {
        PreferencesScreen(
            PreferencesUiState(loading = false, loadError = io.github.rupakc.travelplanner.core.common.AppError.Network()),
            onEvent = {},
            onBack = {},
            onOpenTaste = {},
        )
    }
}
