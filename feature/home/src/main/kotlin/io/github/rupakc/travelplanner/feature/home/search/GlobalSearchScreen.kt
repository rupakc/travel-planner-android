package io.github.rupakc.travelplanner.feature.home.search

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.tpClickable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearch
import io.github.rupakc.travelplanner.core.domain.globalsearch.GlobalSearchKind
import io.github.rupakc.travelplanner.core.domain.globalsearch.SearchableItem
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.feature.home.R

private val SearchMaxWidth = 840.dp

@Composable
internal fun GlobalSearchRoute(onBack: () -> Unit, onNavigate: (AppRoute) -> Unit, viewModel: GlobalSearchViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.navigation.collect(onNavigate) }
    GlobalSearchScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}

/** Unified search screen (stateless): query field, filter chips with counts, grouped results. */
@Composable
fun GlobalSearchScreen(
    state: GlobalSearchUiState,
    onEvent: (GlobalSearchEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (autoFocus) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            // Full-screen destination outside the shell's top bar: keep clear of the status bar / cutout (edge-to-edge).
            Column(
                Modifier
                    .widthIn(max = SearchMaxWidth)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(end = TravelTheme.spacing.gutter, top = TravelTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TpIconButton(onClick = onBack, tid = "home.home_search_back") {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.home_search_back))
                    }
                    TpOutlinedTextField(
                        value = state.query,
                        onValueChange = { onEvent(GlobalSearchEvent.QueryChanged(it)) },
                        modifier = Modifier.weight(1f).focusRequester(focus),
                        placeholder = { Text(stringResource(R.string.home_search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (state.query.isNotEmpty()) {
                                TpIconButton(onClick = { onEvent(GlobalSearchEvent.QueryChanged("")) }, tid = "home.home_search_clear") {
                                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.home_search_clear))
                                }
                            }
                        },
                        singleLine = true,
                        shape = TravelShape.Pill,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        tid = "home.home_search_hint",
                    )
                }
                if (state.query.isNotBlank()) FilterChips(state, onEvent)
                Results(state, onEvent)
            }
        }
    }
}

@Composable
private fun FilterChips(state: GlobalSearchUiState, onEvent: (GlobalSearchEvent) -> Unit) {
    val total = state.results.counts.values.sum()
    LazyRow(
        contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        item(key = "all") {
            TpChip(
                label = stringResource(R.string.home_search_filter_all, total),
                selected = state.filter.isEmpty(),
                onClick = { onEvent(GlobalSearchEvent.ClearFilter) },
                tid = "home.global_search_chip_1",
            )
        }
        items(GlobalSearchKind.entries, key = { it.name }) { kind ->
            TpChip(
                label = stringResource(kind.filterLabelRes(), state.results.counts[kind] ?: 0),
                selected = kind in state.filter,
                onClick = { onEvent(GlobalSearchEvent.ToggleFilter(kind)) },
                tid = "home.global_search_chip_2",
            )
        }
    }
}

@Composable
private fun Results(state: GlobalSearchUiState, onEvent: (GlobalSearchEvent) -> Unit) {
    val announcement =
        if (state.query.isBlank()) "" else pluralStringResource(R.plurals.home_search_result_count, state.results.total, state.results.total)
    Text(
        announcement,
        style = TravelTheme.typography.labelMedium,
        color = TravelTheme.colors.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = TravelTheme.spacing.gutter).semantics { liveRegion = LiveRegionMode.Polite },
    )
    when {
        state.query.isBlank() -> {
            EmptyState(
                title = stringResource(R.string.home_search_empty_title),
                message = stringResource(R.string.home_search_empty_message),
                icon = Icons.Filled.Search,
            )
        }

        state.results.total == 0 -> {
            EmptyState(
                title = stringResource(R.string.home_search_none_title, state.query.trim()),
                message = stringResource(R.string.home_search_none_message),
                icon = Icons.Filled.Search,
                actionLabel = stringResource(R.string.home_search_clear_filters).takeIf { state.filter.isNotEmpty() },
                onAction = { onEvent(GlobalSearchEvent.ClearFilter) }.takeIf { state.filter.isNotEmpty() },
                tid = "home.global_search_empty_action",
            )
        }

        else -> {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = TravelTheme.spacing.xl)) {
                state.results.groups.forEach { group ->
                    item(key = "h-${group.kind}", contentType = "heading") {
                        Text(
                            stringResource(group.kind.headingRes(), state.results.counts[group.kind] ?: group.hits.size),
                            style = TravelTheme.typography.titleSmall,
                            color = TravelTheme.colors.primary,
                            modifier =
                                Modifier
                                    .padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm)
                                    .semantics { heading() },
                        )
                    }
                    items(group.hits, key = { "${group.kind}-${it.item.id}" }, contentType = { "hit" }) { hit ->
                        ResultRow(hit.item, hidden = hit.item.kind == GlobalSearchKind.FEATURE && hit.item.id in state.hiddenFeatures, onEvent)
                    }
                    item(key = "d-${group.kind}", contentType = "divider") { HorizontalDivider(Modifier.padding(vertical = TravelTheme.spacing.xs)) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(item: SearchableItem, hidden: Boolean, onEvent: (GlobalSearchEvent) -> Unit) {
    val supporting = listOfNotNull(item.subtitle, stringResource(R.string.home_search_feature_off).takeIf { hidden })
    ListItem(
        headlineContent = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent =
            supporting.takeIf { it.isNotEmpty() }?.let { lines ->
                { Text(lines.joinToString("\n"), maxLines = 3, overflow = TextOverflow.Ellipsis) }
            },
        leadingContent = { Icon(item.icon(), contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant) },
        modifier =
            Modifier.tpClickable(tid = "home.home_search_open_result", onClickLabel = stringResource(R.string.home_search_open_result)) {
                onEvent(GlobalSearchEvent.Open(item))
            },
    )
}

private fun SearchableItem.icon(): ImageVector =
    when (kind) {
        GlobalSearchKind.TRIP -> Icons.AutoMirrored.Filled.List
        GlobalSearchKind.CHAT -> Icons.AutoMirrored.Filled.Send
        GlobalSearchKind.RECENT_SEARCH -> Icons.Filled.Refresh
        GlobalSearchKind.FEATURE -> Icons.Filled.Star
        GlobalSearchKind.SETTING -> Icons.Filled.Settings
    }

private fun GlobalSearchKind.filterLabelRes(): Int =
    when (this) {
        GlobalSearchKind.TRIP -> R.string.home_search_filter_trips
        GlobalSearchKind.CHAT -> R.string.home_search_filter_chats
        GlobalSearchKind.RECENT_SEARCH -> R.string.home_search_filter_recent
        GlobalSearchKind.FEATURE -> R.string.home_search_filter_features
        GlobalSearchKind.SETTING -> R.string.home_search_filter_settings
    }

private fun GlobalSearchKind.headingRes(): Int = filterLabelRes()

private val previewItems =
    listOf(
        SearchableItem(GlobalSearchKind.TRIP, "7", "Tokyo spring", "Tokyo, 2027-04-01"),
        SearchableItem(GlobalSearchKind.CHAT, "3", "Ramen in Tokyo", "Where is the best ramen near Shinjuku?"),
        SearchableItem(GlobalSearchKind.FEATURE, "near_me", "Near me", "Things to do around where you are now."),
    )

private val previewState =
    GlobalSearchUiState(query = "tokyo", results = GlobalSearch.search("to", previewItems), hiddenFeatures = setOf("near_me"))

@Preview(name = "Search light", widthDp = 380, heightDp = 700)
@Composable
private fun GlobalSearchPreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { GlobalSearchScreen(previewState, onEvent = {}, onBack = {}, autoFocus = false) }
}

@Preview(name = "Search dark empty", widthDp = 380, heightDp = 500, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GlobalSearchPreviewDark() {
    TravelTheme(themeMode = ThemeMode.DARK) { GlobalSearchScreen(GlobalSearchUiState(), onEvent = {}, onBack = {}, autoFocus = false) }
}
