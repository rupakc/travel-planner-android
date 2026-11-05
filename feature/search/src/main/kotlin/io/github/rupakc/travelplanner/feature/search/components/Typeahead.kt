package io.github.rupakc.travelplanner.feature.search.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.feature.search.R
import kotlinx.coroutines.delay

/** Typeahead sources (`GET /api/airports/search`, `GET /api/nationalities/search`); failures return no suggestions. */
@Stable
interface SearchLookups {
    suspend fun airports(query: String): List<Airport>

    suspend fun nationalities(query: String): List<Nationality>

    companion object {
        /** No suggestions (previews, tests). */
        val None: SearchLookups =
            object : SearchLookups {
                override suspend fun airports(query: String): List<Airport> = emptyList()

                override suspend fun nationalities(query: String): List<Nationality> = emptyList()
            }
    }
}

/** Debounce before querying (brief: 250 ms). */
private const val TYPEAHEAD_DEBOUNCE_MS = 250L

/**
 * Editable field with a suggestion menu (web AirportSearch / NationalitySearch): typing emits the raw
 * text; picking a suggestion emits [toValue] (e.g. the IATA code) while the field shows [toDisplay].
 * Values set from outside (preferences, a recent search) replace the shown text.
 * Keyboard and TalkBack friendly: an exposed dropdown menu anchored to the text field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> TypeaheadField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    search: suspend (String) -> List<T>,
    toValue: (T) -> String,
    toDisplay: (T) -> String,
    itemKey: (T) -> String,
    modifier: Modifier = Modifier,
    minChars: Int = 2,
    leadingIcon: ImageVector? = null,
    leadingPainter: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorText: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    itemContent: @Composable (T) -> Unit,
) {
    var display by rememberSaveable { mutableStateOf(value) }
    var emitted by rememberSaveable { mutableStateOf(value) }
    var query by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf(emptyList<T>()) }
    var loading by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value != emitted) {
            emitted = value
            display = value
        }
    }
    LaunchedEffect(query) {
        // A new query (or a cleared field) cancels the previous search; the spinner must never outlive it.
        loading = false
        val q = query?.trim() ?: return@LaunchedEffect
        if (q.length < minChars) {
            results = emptyList()
            expanded = false
            return@LaunchedEffect
        }
        delay(TYPEAHEAD_DEBOUNCE_MS)
        loading = true
        try {
            results = search(q)
            expanded = results.isNotEmpty()
        } finally {
            loading = false
        }
    }

    val suggestionsLabel = pluralStringResource(R.plurals.search_suggestions, results.size, results.size)
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && results.isNotEmpty() }, modifier = modifier) {
        OutlinedTextField(
            value = display,
            onValueChange = {
                display = it
                emitted = it
                onValueChange(it)
                query = it
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .semantics { if (expanded) stateDescription = suggestionsLabel },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            leadingIcon = leadingPainter ?: leadingIcon?.let { { Icon(it, contentDescription = null) } },
            trailingIcon = {
                when {
                    loading -> {
                        CircularProgressIndicator(Modifier.size(TravelTheme.spacing.lg), strokeWidth = TravelTheme.spacing.xxs)
                    }

                    display.isNotEmpty() -> {
                        IconButton(onClick = {
                            display = ""
                            emitted = ""
                            onValueChange("")
                            query = null
                            results = emptyList()
                            expanded = false
                        }) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear_field, label)) }
                    }
                }
            },
            isError = isError,
            supportingText = errorText?.let { { Text(it, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
            singleLine = true,
            shape = TravelShape.Input,
            keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Next),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            results.forEach { item ->
                DropdownMenuItem(
                    text = { itemContent(item) },
                    onClick = {
                        display = toDisplay(item)
                        emitted = toValue(item)
                        onValueChange(emitted)
                        query = null
                        results = emptyList()
                        expanded = false
                    },
                    modifier = Modifier.testTag("suggestion:${itemKey(item)}"),
                )
            }
        }
    }
}

/** From / To / stop field over `/api/airports/search`; a pick stores the IATA code and shows "JFK — New York, United States". */
@Composable
internal fun AirportField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    lookups: SearchLookups,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.search_city_or_airport),
    leadingIcon: ImageVector? = null,
    leadingPainter: (@Composable () -> Unit)? = null,
    errorText: String? = null,
) {
    TypeaheadField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        search = lookups::airports,
        toValue = { it.iataCode.orEmpty().ifBlank { it.city.orEmpty() } },
        toDisplay = { a -> listOfNotNull(a.iataCode, listOfNotNull(a.city, a.country).joinToString(", ").ifBlank { null }).joinToString(" — ") },
        itemKey = { it.iataCode ?: it.name.orEmpty() },
        modifier = modifier,
        leadingIcon = leadingIcon,
        leadingPainter = leadingPainter,
        isError = errorText != null,
        errorText = errorText,
    ) { airport -> AirportSuggestion(airport) }
}

@Composable
private fun AirportSuggestion(airport: Airport) {
    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = TravelShape.Input, color = TravelTheme.colors.primaryContainer, contentColor = TravelTheme.colors.onPrimaryContainer) {
            Text(
                airport.iataCode.orEmpty(),
                style = TravelTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
            )
        }
        Column {
            Text(airport.name.orEmpty(), style = TravelTheme.typography.bodyMedium, maxLines = 1)
            Text(
                listOfNotNull(airport.city, airport.country).joinToString(" · "),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** Nationality field over `/api/nationalities/search` (from 1 character, web parity); a pick stores the nationality. */
@Composable
internal fun NationalityField(
    value: String,
    onValueChange: (String) -> Unit,
    lookups: SearchLookups,
    modifier: Modifier = Modifier,
    errorText: String? = null,
) {
    TypeaheadField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(R.string.search_nationality),
        placeholder = stringResource(R.string.search_nationality_placeholder),
        search = lookups::nationalities,
        toValue = { it.nationality.orEmpty() },
        toDisplay = { it.nationality.orEmpty() },
        itemKey = { it.nationality.orEmpty() },
        modifier = modifier,
        minChars = 1,
        isError = errorText != null,
        errorText = errorText,
    ) { nat ->
        Column {
            Text(nat.nationality.orEmpty(), style = TravelTheme.typography.bodyMedium)
            nat.country?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
        }
    }
}
