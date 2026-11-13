package io.github.rupakc.travelplanner.feature.alerts

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonBlock
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.rememberReduceMotion
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AlertTab
import io.github.rupakc.travelplanner.core.domain.alerts.AnyWatch
import io.github.rupakc.travelplanner.core.domain.alerts.Tone
import io.github.rupakc.travelplanner.core.model.alerts.AlertKind
import io.github.rupakc.travelplanner.core.model.alerts.FareWatch
import io.github.rupakc.travelplanner.core.model.alerts.PriceWatch
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.core.ui.watch.ToneBox
import io.github.rupakc.travelplanner.feature.alerts.savings.SavingsHeaderRoute
import io.github.rupakc.travelplanner.feature.alerts.story.PriceStorySheet
import io.github.rupakc.travelplanner.feature.alerts.story.StoryGateViewModel
import io.github.rupakc.travelplanner.feature.alerts.v2.AlertTools
import java.time.Instant

/** Watched prices (top-level: the shell draws the app bar). */
@Composable
fun WatchedPricesRoute(
    onReplanned: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenPrefs: () -> Unit = {},
    viewModel: AlertsViewModel = hiltViewModel(),
    gate: StoryGateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effect.collect { if (it == AlertsEffect.Replanned) onReplanned() }
    }
    val storyAvailable by gate.available.collectAsStateWithLifecycle()
    Column(modifier) {
        // Alerts 2.0: suggestions (one-tap) and alert settings; renders nothing against a backend without the v2 API.
        AlertTools(onOpenPrefs = onOpenPrefs)
        AlertsScreen(state, viewModel::onEvent, onSearch, Modifier.weight(1f), header = { SavingsHeaderRoute() }, storyAvailable = storyAvailable)
    }
}

/** Stateless Alerts screen (web AlertsPage). */
@Composable
fun AlertsScreen(
    state: AlertsUiState,
    onEvent: (AlertsEvent) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    /** Slot under the title: the savings tally (stateful, so the stateless screen stays previewable). */
    header: @Composable () -> Unit = {},
    storyAvailable: Boolean = false,
) {
    var storyKey by rememberSaveable { mutableStateOf<String?>(null) }
    val grid = rememberLazyGridState()
    val reduceMotion = rememberReduceMotion()
    val snackbar = remember { SnackbarHostState() }
    val open = rememberLinkOpener()

    // Deep link: scroll the focused card into view once.
    LaunchedEffect(state.focusKey, state.shown.size) {
        val key = state.focusKey ?: return@LaunchedEffect
        val index = gridIndexOf(state, key) ?: return@LaunchedEffect
        if (reduceMotion) grid.scrollToItem(index) else grid.animateScrollToItem(index)
    }

    val removed = stringResource(R.string.alerts_removed)
    val undo = stringResource(R.string.alerts_undo)
    LaunchedEffect(state.undo?.key) {
        if (state.undo == null) return@LaunchedEffect
        val result = snackbar.showSnackbar(removed, actionLabel = undo, duration = SnackbarDuration.Short)
        onEvent(if (result == SnackbarResult.ActionPerformed) AlertsEvent.Undo else AlertsEvent.UndoExpired)
    }

    Box(modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(CARD_MIN_WIDTH),
            state = grid,
            contentPadding = PaddingValues(TravelTheme.spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
            modifier = Modifier.fillMaxSize(),
        ) {
            full("header") { Header() }
            full("savings") { header() }
            if (state.livePrices == false) full("unavailable") { ToneBox(Tone.WARN) { Text(stringResource(R.string.alerts_live_unavailable)) } }
            when {
                state.loading -> loading()
                else -> content(state, onEvent, onSearch, open, onStory = if (storyAvailable) ({ key: String -> storyKey = key }) else null)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    storyKey?.let { key ->
        val watch = state.watches.firstOrNull { it.key == key }
        val kind = watch?.let { AlertKind.from(it.kind) }
        if (watch == null || kind == null) {
            storyKey = null
        } else {
            PriceStorySheet(kind, watch.id, watchTitle(watch, state.placeNames), onDismiss = { storyKey = null })
        }
    }

    state.liveCheck?.let { live ->
        val watch = state.watches.firstOrNull { it.key == live.key }
        LiveCheckSheet(
            live = live,
            title = watch?.let { watchTitle(it, state.placeNames) },
            onRetry = { onEvent(AlertsEvent.RetryLiveCheck) },
            onDismiss = { onEvent(AlertsEvent.DismissLiveCheck) },
            onOpen = open,
        )
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) =
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }

/** Grid index of [key]: header, optional banner, then [content]'s fixed rows before the cards. */
private fun gridIndexOf(state: AlertsUiState, key: String): Int? {
    val before = 1 + (if (state.livePrices == false) 1 else 0) + listOf(state.faresFailed, state.pricesFailed && !state.faresFailed).count { it }
    val fixed = before + FIXED_ROWS
    val active = state.active.indexOfFirst { it.key == key }
    if (active >= 0) return fixed + 1 + active
    val past = state.past.indexOfFirst { it.key == key }
    if (past >= 0) return fixed + (if (state.active.isNotEmpty()) 1 + state.active.size else 0) + 1 + past
    return null
}

/** Search box, tab row and live status. */
private const val FIXED_ROWS = 3

@Composable
private fun Header() {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(stringResource(R.string.alerts_title), style = TravelTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.alerts_message), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
    }
}

private fun LazyGridScope.loading() {
    full("loading-label") {
        val label = stringResource(R.string.alerts_loading)
        Text(
            label,
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
            modifier =
                Modifier.semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                },
        )
    }
    items(SKELETON_CARDS, key = { "skeleton-$it" }, contentType = { "skeleton" }) {
        Surface(shape = TravelShape.LargeCard, color = TravelTheme.colors.surface, tonalElevation = TravelTheme.spacing.xxs) {
            Column(Modifier.padding(TravelTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                SkeletonBlock(Modifier.fillMaxWidth(SKELETON_TITLE))
                SkeletonBlock(Modifier.fillMaxWidth(SKELETON_SUB))
                SkeletonBlock(Modifier.fillMaxWidth(SKELETON_PRICE), height = TravelTheme.spacing.xxl)
                SkeletonBlock(Modifier.fillMaxWidth(), height = TravelTheme.spacing.xxl)
            }
        }
    }
}

private fun LazyGridScope.content(
    state: AlertsUiState,
    onEvent: (AlertsEvent) -> Unit,
    onSearch: () -> Unit,
    open: (String) -> Unit,
    onStory: ((String) -> Unit)?,
) {
    if (state.faresFailed) {
        full("fares-error") {
            ToneBox(Tone.WARN, live = true) {
                Text(state.faresError ?: stringResource(R.string.alerts_fares_error))
                TpButton(stringResource(R.string.alerts_try_again), {
                    onEvent(AlertsEvent.Retry)
                }, variant = TpButtonVariant.Secondary, tid = "alerts.alerts_try_again")
            }
        }
    } else if (state.pricesFailed) {
        full("prices-error") {
            ToneBox(Tone.NEUTRAL, live = true) {
                Text(stringResource(R.string.alerts_prices_error))
                TpButton(stringResource(R.string.alerts_try_again), {
                    onEvent(AlertsEvent.RetryPrices)
                }, variant = TpButtonVariant.Secondary, tid = "alerts.alerts_try_again_2")
            }
        }
    }
    if (state.nothingWatched) {
        full("empty") {
            EmptyState(
                title = stringResource(R.string.alerts_empty_title),
                message = stringResource(if (state.livePrices == false) R.string.alerts_empty_unavailable else R.string.alerts_empty_message),
                icon = Icons.Filled.Notifications,
                actionLabel = stringResource(R.string.alerts_search_flights),
                onAction = onSearch,
            )
        }
        return
    }
    if (state.watches.isEmpty()) return
    full("search") { SearchBox(state.query) { onEvent(AlertsEvent.Query(it)) } }
    full("tabs") { Tabs(state.tab, state.counts) { onEvent(AlertsEvent.SelectTab(it)) } }
    full("status") {
        Text(
            filterStatus(state.tab, state.shown.size),
            style = TravelTheme.typography.labelMedium,
            color = TravelTheme.colors.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    if (state.shown.isEmpty()) {
        full("tab-empty") {
            val text = if (state.query.isNotBlank()) stringResource(R.string.alerts_no_match, state.query.trim()) else tabEmpty(state.tab)
            Text(
                text,
                style = TravelTheme.typography.bodyMedium,
                color = TravelTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(TravelTheme.spacing.xl),
            )
        }
        return
    }
    section("active", R.string.alerts_active, state.active, state, onEvent, open, onStory)
    section("past", R.string.alerts_past, state.past, state, onEvent, open, onStory)
}

private fun LazyGridScope.section(
    key: String,
    titleRes: Int,
    list: List<AnyWatch>,
    state: AlertsUiState,
    onEvent: (AlertsEvent) -> Unit,
    open: (String) -> Unit,
    onStory: ((String) -> Unit)?,
) {
    if (list.isEmpty()) return
    full("$key-header") {
        Text(
            stringResource(titleRes, list.size),
            style = TravelTheme.typography.titleMedium,
            modifier = Modifier.padding(top = TravelTheme.spacing.sm).semantics { heading() },
        )
    }
    items(list, key = { it.key }, contentType = { it.kind }) { item ->
        WatchCard(
            item = item,
            busy = state.busy[item.key],
            message = state.messages[item.key],
            highlighted = state.focusKey == item.key,
            emailAvailable = state.emailAvailable,
            userEmail = state.userEmail,
            now = state.now,
            placeNames = state.placeNames,
            callbacks =
                CardCallbacks(
                    onCheck = { onEvent(AlertsEvent.Check(item.key)) },
                    onStatus = { onEvent(AlertsEvent.SetStatus(item.key, it)) },
                    onSave = { onEvent(AlertsEvent.Save(item.key, it)) },
                    onDelete = { onEvent(AlertsEvent.Delete(item.key)) },
                    onReplan = { onEvent(AlertsEvent.Replan(item.key, it)) },
                    onOpenLink = open,
                    onLiveCheck = { onEvent(AlertsEvent.LiveCheck(item.key)) },
                    onStory = onStory?.let { show -> { show(item.key) } },
                ),
        )
    }
}

@Composable
private fun SearchBox(query: String, onQuery: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    TpOutlinedTextField(
        value = query,
        onValueChange = onQuery,
        singleLine = true,
        // Filters as you type; Search on the keyboard just puts the keyboard away so the cards show.
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        label = { Text(stringResource(R.string.alerts_search_label)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TpIconButton(onClick = {
                    onQuery("")
                }, tid = "alerts.alerts_search_clear") { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.alerts_search_clear)) }
            }
        },
        shape = TravelShape.Input,
        modifier = Modifier.fillMaxWidth(),
        tid = "alerts.alerts_search_label",
    )
}

@Composable
private fun Tabs(selected: AlertTab, counts: Map<AlertTab, Int>, onSelect: (AlertTab) -> Unit) {
    val group = stringResource(R.string.alerts_filter_group)
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .semantics { contentDescription = group },
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        AlertTab.entries.forEach { tab ->
            val n = counts[tab] ?: 0
            val label = tabLabel(tab)
            val desc = stringResource(R.string.alerts_tab_a11y, label, n)
            val state = stringResource(if (tab == selected) R.string.alerts_selected else R.string.alerts_not_selected)
            TpChip(
                label = stringResource(R.string.alerts_tab_label, label, n),
                selected = tab == selected,
                onClick = { onSelect(tab) },
                modifier =
                    Modifier.semantics {
                        contentDescription = desc
                        stateDescription = state
                    },
            )
        }
    }
}

private const val SKELETON_CARDS = 3
private const val SKELETON_TITLE = 0.6f
private const val SKELETON_SUB = 0.4f
private const val SKELETON_PRICE = 0.3f

private val previewState =
    AlertsUiState(
        fares =
            listOf(
                FareWatch(
                    id = 7,
                    status = "active",
                    originLabel = "Berlin, Germany",
                    destinationLabel = "Lisbon, Portugal",
                    departureDate = "2026-12-01",
                    returnDate = "2026-12-08",
                    numTravelers = 2,
                    baselinePrice = 500.0,
                    lastPrice = 454.0,
                    lowestPrice = 454.0,
                ),
            ),
        prices =
            listOf(
                PriceWatch(
                    id = 3,
                    kind = "hotel",
                    status = "paused",
                    title = "Lumen Hotel",
                    subtitle = "10–14 Dec · 4 nights",
                    currency = "EUR",
                    lastPrice = 398.0,
                ),
            ),
        livePrices = true,
        now = Instant.parse("2026-11-05T12:00:00Z"),
    )

@Preview(name = "Alerts · light", widthDp = 400, heightDp = 900)
@Composable
private fun AlertsPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { Surface { AlertsScreen(previewState, {}, {}) } }
}

@Preview(name = "Alerts · dark, unavailable", widthDp = 400, heightDp = 900)
@Composable
private fun AlertsDarkPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { Surface { AlertsScreen(previewState.copy(livePrices = false), {}, {}) } }
}

@Preview(name = "Alerts · empty", widthDp = 400)
@Composable
private fun AlertsEmptyPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { Surface { AlertsScreen(AlertsUiState(fares = emptyList(), prices = emptyList()), {}, {}) } }
}

@Preview(name = "Alerts · loading", widthDp = 400)
@Composable
private fun AlertsLoadingPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { Surface { AlertsScreen(AlertsUiState(), {}, {}) } }
}
