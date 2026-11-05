package io.github.rupakc.travelplanner.feature.search

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.core.layout.WindowSizeClass
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.ImportLimits
import io.github.rupakc.travelplanner.core.model.reference.Airport
import io.github.rupakc.travelplanner.core.model.reference.Nationality
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.search.compare.CompareEvent
import io.github.rupakc.travelplanner.feature.search.compare.CompareForm
import io.github.rupakc.travelplanner.feature.search.compare.CompareTable
import io.github.rupakc.travelplanner.feature.search.compare.CompareUiState
import io.github.rupakc.travelplanner.feature.search.compare.CompareViewModel
import io.github.rupakc.travelplanner.feature.search.compare.rememberBest
import io.github.rupakc.travelplanner.feature.search.components.SearchDimens
import io.github.rupakc.travelplanner.feature.search.components.SearchLookups
import io.github.rupakc.travelplanner.feature.search.discover.DestinationCard
import io.github.rupakc.travelplanner.feature.search.discover.DiscoverEvent
import io.github.rupakc.travelplanner.feature.search.discover.DiscoverForm
import io.github.rupakc.travelplanner.feature.search.discover.DiscoverResults
import io.github.rupakc.travelplanner.feature.search.discover.DiscoverUiState
import io.github.rupakc.travelplanner.feature.search.discover.DiscoverViewModel
import io.github.rupakc.travelplanner.feature.search.importlink.ImportEffect
import io.github.rupakc.travelplanner.feature.search.importlink.ImportEvent
import io.github.rupakc.travelplanner.feature.search.importlink.ImportPanel
import io.github.rupakc.travelplanner.feature.search.importlink.ImportReview
import io.github.rupakc.travelplanner.feature.search.importlink.ImportUiState
import io.github.rupakc.travelplanner.feature.search.importlink.ImportViewModel
import io.github.rupakc.travelplanner.feature.search.known.KnownSearchForm
import io.github.rupakc.travelplanner.feature.search.known.RecentNoticeBanner
import io.github.rupakc.travelplanner.feature.search.known.RecentSearchesSection
import kotlinx.coroutines.launch

/**
 * Search tab route: wires the four mode ViewModels, the Photo Picker, the undo snackbar and
 * navigation to Results once a search has started.
 */
@Composable
internal fun SearchFormRoute(
    importUrl: String?,
    onSearchStarted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    discoverViewModel: DiscoverViewModel = hiltViewModel(),
    compareViewModel: CompareViewModel = hiltViewModel(),
    importViewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val discover by discoverViewModel.state.collectAsStateWithLifecycle()
    val compare by compareViewModel.state.collectAsStateWithLifecycle()
    val import by importViewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(maxItems = ImportLimits.MAX_IMAGES)) {
            importViewModel.onEvent(ImportEvent.AddShots(it))
        }
    val pickImages = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val lookups =
        remember(viewModel) {
            object : SearchLookups {
                override suspend fun airports(query: String): List<Airport> = viewModel.airports(query)

                override suspend fun nationalities(query: String): List<Nationality> = viewModel.nationalities(query)
            }
        }

    val locale = currentLocale()
    LaunchedEffect(locale) {
        viewModel.setLocale(locale)
        discoverViewModel.onEvent(DiscoverEvent.SetLocale(locale))
        compareViewModel.onEvent(CompareEvent.SetLocale(locale))
    }

    LaunchedEffect(viewModel) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SearchEffect.SearchStarted -> {
                    onSearchStarted()
                }

                is SearchEffect.RecentRemoved -> {
                    scope.launch {
                        // Undo only on the action; Dismissed, a timeout or leaving the screen (cancellation) commits.
                        var undone = false
                        try {
                            undone =
                                snackbar.showSnackbar(
                                    resources.getString(R.string.search_recent_removed, effect.route),
                                    resources.getString(R.string.search_recent_undo),
                                    duration = SnackbarDuration.Long,
                                ) == SnackbarResult.ActionPerformed
                        } finally {
                            viewModel.onEvent(
                                if (undone) SearchFormEvent.UndoRemoveRecent(effect.id) else SearchFormEvent.CommitRemoveRecent(effect.id),
                            )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(importViewModel) {
        importViewModel.effect.collect { effect ->
            when (effect) {
                ImportEffect.PickImages -> pickImages()
                is ImportEffect.PlanReady -> viewModel.planImport(effect.search.form, effect.search.stops)
            }
        }
    }
    // Compare and From a link start from the known form (web: seeded once on entering the mode).
    // Preferences arriving late (deep link straight into a mode) still pre-fill untouched Compare / import forms.
    LaunchedEffect(state.preferences) {
        state.preferences?.let { prefs ->
            compareViewModel.applyPreferences(prefs)
            importViewModel.applyPreferences(prefs)
        }
    }
    LaunchedEffect(state.mode) {
        when (state.mode) {
            SearchMode.COMPARE -> compareViewModel.seed(viewModel.state.value.form)
            SearchMode.IMPORT -> importViewModel.seed(viewModel.state.value.form, importUrl)
            else -> Unit
        }
    }

    SearchScreen(
        state = state,
        discover = discover,
        compare = compare,
        import = import,
        lookups = lookups,
        onEvent = viewModel::onEvent,
        onDiscoverEvent = discoverViewModel::onEvent,
        onCompareEvent = compareViewModel::onEvent,
        onImportEvent = importViewModel::onEvent,
        onPlanDiscovered = { viewModel.applyDiscovered(discover.form, it) },
        onPlanCompared = { index -> compareViewModel.requestAt(index)?.let(viewModel::planCompared) },
        onPickImages = pickImages,
        snackbarHostState = snackbar,
        modifier = modifier,
    )
}

/** True on expanded windows (tablets, unfolded foldables): the compare table shows as a real table. */
@Composable
private fun isWide(): Boolean = currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

/** The stateless Search tab: hero, mode chips and the current mode's content in one scrolling column. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    discover: DiscoverUiState,
    compare: CompareUiState,
    import: ImportUiState,
    lookups: SearchLookups,
    onEvent: (SearchFormEvent) -> Unit,
    onDiscoverEvent: (DiscoverEvent) -> Unit,
    onCompareEvent: (CompareEvent) -> Unit,
    onImportEvent: (ImportEvent) -> Unit,
    onPlanDiscovered: (DiscoveredDestination) -> Unit,
    onPlanCompared: (Int) -> Unit,
    onPickImages: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    wide: Boolean = isWide(),
) {
    val formWidth = Modifier.widthIn(max = SearchDimens.FormMaxWidth).fillMaxWidth()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("search_list"),
            contentPadding = PaddingValues(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "hero", contentType = "hero") { Hero(state, onEvent, formWidth) }
            item(key = "modes", contentType = "modes") { ModeChips(state, onEvent, formWidth) }
            when (state.mode) {
                SearchMode.KNOWN -> {
                    item(key = "recent", contentType = "recent") {
                        RecentSearchesSection(
                            cards = state.recent,
                            expanded = state.recentExpanded,
                            onToggle = { onEvent(SearchFormEvent.ToggleRecent) },
                            onLoad = { onEvent(SearchFormEvent.LoadRecent(it)) },
                            onRerun = { onEvent(SearchFormEvent.RerunRecent(it)) },
                            onRemove = { onEvent(SearchFormEvent.RemoveRecent(it)) },
                            modifier = formWidth,
                        )
                    }
                    state.notice?.let { notice ->
                        item(key = "notice", contentType = "notice") { RecentNoticeBanner(notice, { onEvent(SearchFormEvent.DismissNotice) }, formWidth) }
                    }
                    item(key = "known", contentType = "form") { KnownSearchForm(state, lookups, onEvent, formWidth) }
                }

                SearchMode.SURPRISE -> {
                    item(key = "discover", contentType = "form") { DiscoverForm(discover, lookups, onDiscoverEvent, formWidth) }
                    item(key = "discover_status", contentType = "status") { DiscoverResults(discover, { onDiscoverEvent(DiscoverEvent.Submit) }, formWidth) }
                    items(discover.destinations.orEmpty(), key = { "dest:${it.city}:${it.country}" }, contentType = { "destination" }) { dest ->
                        DestinationCard(dest, { onPlanDiscovered(dest) }, formWidth)
                    }
                }

                SearchMode.COMPARE -> {
                    item(key = "compare", contentType = "form") { CompareForm(compare, lookups, onCompareEvent, formWidth) }
                    compare.columns?.let { columns ->
                        item(key = "compare_table", contentType = "table") {
                            CompareTable(
                                columns = columns,
                                best = rememberBest(compare),
                                wide = wide,
                                onPlan = onPlanCompared,
                                onRetry = { onCompareEvent(CompareEvent.Retry(it)) },
                                modifier = Modifier.widthIn(max = if (wide) SearchDimens.TableMaxWidth else SearchDimens.FormMaxWidth).fillMaxWidth(),
                            )
                        }
                    }
                }

                SearchMode.IMPORT -> {
                    if (import.preview != null) {
                        item(key = "import_review", contentType = "review") { ImportReview(import, lookups, onImportEvent, formWidth) }
                    } else {
                        item(key = "import_panel", contentType = "panel") { ImportPanel(import, onImportEvent, onPickImages, formWidth) }
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(TravelTheme.spacing.gutter))
    }
    if (state.confirmNewPlan) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { onEvent(SearchFormEvent.CancelNewPlan) },
            title = { androidx.compose.material3.Text(stringResource(R.string.search_new_plan_title)) },
            text = { androidx.compose.material3.Text(stringResource(R.string.search_new_plan_message)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onEvent(SearchFormEvent.ConfirmNewPlan) }) {
                    androidx.compose.material3.Text(stringResource(R.string.search_new_plan_confirm))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { onEvent(SearchFormEvent.CancelNewPlan) }) {
                    androidx.compose.material3.Text(stringResource(R.string.search_new_plan_keep))
                }
            },
        )
    }
}

@Composable
private fun Hero(state: SearchUiState, onEvent: (SearchFormEvent) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(
            stringResource(R.string.search_title),
            style = TravelTheme.typography.displaySmall,
            color = TravelTheme.colors.primary,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.search_message), style = TravelTheme.typography.bodyLarge, color = TravelTheme.colors.onSurfaceVariant)
        // Web amber pill "Saw a great trip online? Paste the link" (only while From a link is available).
        if (state.mode != SearchMode.IMPORT && SearchMode.IMPORT in state.visibleModes) {
            TpButton(
                text = stringResource(R.string.search_paste_link_prompt),
                onClick = { onEvent(SearchFormEvent.SelectMode(SearchMode.IMPORT)) },
                variant = TpButtonVariant.Tonal,
                leadingIcon = null,
                modifier = Modifier.testTag("hero_paste_link"),
            )
        }
    }
}

/** Mode chips (web segmented toggle); hidden features drop out, except the current mode. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeChips(state: SearchUiState, onEvent: (SearchFormEvent) -> Unit, modifier: Modifier) {
    val groupLabel = stringResource(R.string.search_modes)
    FlowRow(
        modifier.selectableGroup().semantics { contentDescription = groupLabel },
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        state.visibleModes.forEach { mode ->
            TpChip(
                label = stringResource(mode.labelRes()),
                selected = state.mode == mode,
                onClick = { onEvent(SearchFormEvent.SelectMode(mode)) },
                modifier = Modifier.testTag("mode_${mode.routeValue}"),
            )
        }
    }
}

private fun SearchMode.labelRes(): Int =
    when (this) {
        SearchMode.KNOWN -> R.string.search_mode_known
        SearchMode.SURPRISE -> R.string.search_mode_surprise
        SearchMode.COMPARE -> R.string.search_mode_compare
        SearchMode.IMPORT -> R.string.search_mode_import
    }
