package io.github.rupakc.travelplanner.feature.nearby

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.live.LiveFailure
import io.github.rupakc.travelplanner.core.model.grounding.GroundingSource
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.nearby.NearbySectionIds
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener

@Composable
fun NearMeRoute(onBack: () -> Unit, viewModel: NearbyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            viewModel.onEvent(NearbyEvent.PermissionResult(result.values.any { it }))
        }
    val context = LocalContext.current
    val openLink = rememberLinkOpener()
    val noMaps = stringResource(R.string.nearby_no_maps_app)
    NearbyScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onRequestPermission = { permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
        onOpenAppSettings = { context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
        onOpenLocationSettings = { context.startSafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        onOpenMaps = { card ->
            if (!context.openMaps(card.mapsUri, mapsQuery(card.name, state.place))) Toast.makeText(context, noMaps, Toast.LENGTH_SHORT).show()
        },
        onOpenSource = openLink,
    )
}

private fun Context.startSafely(intent: Intent): Boolean =
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        // No app for it (rare): the caller decides what to say.
        false
    }

/**
 * "Open in Google Maps": the item's Maps link with ACTION_VIEW (the Maps app or a browser takes it);
 * without a handler, a `geo:` search for the name; false when nothing can open either.
 */
private fun Context.openMaps(mapsUri: String?, query: String): Boolean =
    (mapsUri != null && startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUri)))) ||
        startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))

/** Stateless Near me screen. */
@Composable
fun NearbyScreen(
    state: NearbyUiState,
    onEvent: (NearbyEvent) -> Unit,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenMaps: (NearbyCard) -> Unit,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val hereFailedText = stringResource(R.string.nearby_here_failed)
    LaunchedEffect(state.hereFailed) {
        if (state.hereFailed) {
            snackbar.showSnackbar(hereFailedText)
            onEvent(NearbyEvent.HereMessageShown)
        }
    }
    if (state.pickingFor !=
        null
    ) {
        TripPicker(state.hereTrips, onPick = { onEvent(NearbyEvent.PickTrip(it)) }, onDismiss = { onEvent(NearbyEvent.DismissPicker) })
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TpTopBar(
                title = stringResource(R.string.nearby_title),
                onBack = onBack,
                actions = {
                    if (state.phase == NearbyPhase.Loaded || state.phase is NearbyPhase.Failed || state.phase is NearbyPhase.NoPlace) {
                        TpIconButton(onClick = { onEvent(NearbyEvent.Refresh) }, tid = "nearby.nearby_refresh") {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.nearby_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val phase = state.phase) {
                NearbyPhase.NeedsPermission -> {
                    Rationale(onRequestPermission)
                }

                NearbyPhase.Denied -> {
                    Message(
                        title = stringResource(R.string.nearby_denied_title, stringResource(io.github.rupakc.travelplanner.core.ui.R.string.brand_name)),
                        body = stringResource(R.string.nearby_denied_body),
                        primary = stringResource(R.string.nearby_open_settings) to onOpenAppSettings,
                        secondary = stringResource(R.string.nearby_try_again) to onRequestPermission,
                    )
                }

                NearbyPhase.Locating -> {
                    Waiting(stringResource(R.string.nearby_locating))
                }

                NearbyPhase.Loading -> {
                    Waiting(stringResource(R.string.nearby_loading))
                }

                NearbyPhase.LocationOff -> {
                    Message(
                        title = stringResource(R.string.nearby_off_title),
                        body = stringResource(R.string.nearby_off_body),
                        primary = stringResource(R.string.nearby_open_location_settings) to onOpenLocationSettings,
                        secondary = stringResource(R.string.nearby_try_again) to { onEvent(NearbyEvent.Refresh) },
                    )
                }

                NearbyPhase.NoFix -> {
                    ErrorState(stringResource(R.string.nearby_no_fix), title = stringResource(R.string.nearby_no_fix_title), onRetry = {
                        onEvent(NearbyEvent.Refresh)
                    }, tid = "nearby.nearby_retry_1")
                }

                is NearbyPhase.NoPlace -> {
                    EmptyState(
                        title = stringResource(R.string.nearby_no_place_title),
                        // Our translated copy; the backend's English detail is not shown (review M1).
                        message = stringResource(R.string.nearby_no_place),
                        icon = Icons.Filled.Place,
                        actionLabel = stringResource(R.string.nearby_try_again),
                        onAction = { onEvent(NearbyEvent.Refresh) },
                        tid = "nearby.nearby_empty_action",
                    )
                }

                is NearbyPhase.Failed -> {
                    ErrorState(failureText(phase), onRetry = { onEvent(NearbyEvent.Refresh) }, tid = "nearby.nearby_retry_2")
                }

                NearbyPhase.Loaded -> {
                    Results(state, onEvent, onOpenMaps, onOpenSource)
                }
            }
        }
    }
}

@Composable
private fun Rationale(onRequestPermission: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(TravelTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TravelTheme.colors.primary)
        Text(stringResource(R.string.nearby_rationale_title), style = TravelTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.nearby_message), style = TravelTheme.typography.bodyLarge)
        Text(stringResource(R.string.nearby_rationale_precise), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        Text(stringResource(R.string.nearby_background_note), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        TpButton(stringResource(R.string.nearby_allow), onRequestPermission, leadingIcon = Icons.Filled.LocationOn, tid = "nearby.nearby_allow")
    }
}

@Composable
private fun Message(title: String, body: String, primary: Pair<String, () -> Unit>, secondary: Pair<String, () -> Unit>) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(TravelTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = TravelTheme.extendedColors.warning)
        Text(title, style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(body, style = TravelTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        TpButton(primary.first, primary.second, tid = "nearby.nearby_screen_tp_button")
        TpButton(secondary.first, secondary.second, variant = TpButtonVariant.Text, tid = "nearby.nearby_screen_tp_button_2")
    }
}

@Composable
private fun Waiting(message: String) {
    SkeletonList(Modifier.padding(TravelTheme.spacing.gutter), message = message, lines = SKELETON_LINES)
}

/** Our translated copy for the failure; a 429 keeps the backend's words (they carry the wait time). */
@Composable
private fun failureText(phase: NearbyPhase.Failed): String {
    // Only a 429's text is the backend's: it says how long to wait. Everything else uses our translated copy.
    if (phase.failure == LiveFailure.RATE_LIMITED) phase.message?.let { return it }
    return stringResource(
        when (phase.failure) {
            LiveFailure.OFFLINE -> R.string.nearby_offline
            LiveFailure.RATE_LIMITED -> R.string.nearby_rate_limited
            LiveFailure.AI_LIMIT -> R.string.nearby_ai_limit
            LiveFailure.AI_BILLING -> R.string.nearby_ai_billing
            LiveFailure.GROUNDING_UNAVAILABLE -> R.string.nearby_grounding_unavailable
            else -> R.string.nearby_failed
        },
    )
}

@Composable
private fun Results(state: NearbyUiState, onEvent: (NearbyEvent) -> Unit, onOpenMaps: (NearbyCard) -> Unit, onOpenSource: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "place", contentType = "place") { PlaceHeader(state) }
        if (state.isEmpty) {
            // One page-level empty state, not four section ones as well.
            item(key = "empty", contentType = "empty") {
                EmptyState(title = stringResource(R.string.nearby_empty_title), message = stringResource(R.string.nearby_empty))
            }
        }
        state.sections.takeUnless { state.isEmpty }.orEmpty().forEach { section ->
            item(key = "h-${section.id}", contentType = "section") {
                Text(
                    stringResource(sectionTitle(section.id)),
                    style = TravelTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = TravelTheme.spacing.sm).semantics { heading() },
                )
            }
            if (section.cards.isEmpty()) {
                item(key = "e-${section.id}", contentType = "section-empty") {
                    Text(
                        stringResource(R.string.nearby_section_empty),
                        style = TravelTheme.typography.bodyMedium,
                        color = TravelTheme.colors.onSurfaceVariant,
                    )
                }
            }
            items(section.cards, key = { it.key }, contentType = { "card" }) { card ->
                PlaceCard(
                    card,
                    onOpenMaps,
                    canMark = ImHere.available(card, state.hereTrips),
                    marked = card.key in state.markedKeys,
                    onHere = { onEvent(NearbyEvent.ImHere(card.key)) },
                )
            }
        }
        if (state.sources.isNotEmpty()) {
            item(key = "sources", contentType = "sources") { Sources(state.sources, onOpenSource) }
        }
    }
}

@Composable
private fun PlaceHeader(state: NearbyUiState) {
    val place = state.place
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            place?.let(::placeLabel)?.takeIf(String::isNotBlank) ?: stringResource(R.string.nearby_title),
            style = TravelTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        place?.distanceKm?.let {
            Text(stringResource(R.string.nearby_distance, decimal(it)), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        }
        // Google Maps attribution (as sent; "Google Maps" when the server left it out).
        Text(
            stringResource(R.string.nearby_attribution, state.attribution ?: GOOGLE_MAPS),
            style = TravelTheme.typography.labelMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaceCard(card: NearbyCard, onOpenMaps: (NearbyCard) -> Unit, canMark: Boolean, marked: Boolean, onHere: () -> Unit) {
    TpCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(card.name, style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            card.summary?.let { Text(it, style = TravelTheme.typography.bodyMedium) }
            val a11y = stringResource(R.string.nearby_open_maps_a11y, card.name)
            TpButton(
                stringResource(R.string.nearby_open_maps),
                { onOpenMaps(card) },
                variant = TpButtonVariant.Text,
                leadingIcon = Icons.Filled.Place,
                modifier = Modifier.semantics { contentDescription = a11y },
                tid = "nearby.nearby_open_maps",
            )
            if (canMark) {
                val hereA11y = stringResource(R.string.nearby_here_a11y, card.name)
                TpButton(
                    stringResource(if (marked) R.string.nearby_here_done else R.string.nearby_here),
                    onHere,
                    variant = TpButtonVariant.Text,
                    enabled = !marked,
                    leadingIcon = Icons.Filled.Check,
                    modifier = Modifier.semantics { contentDescription = hereA11y },
                    tid = "nearby.nearby_here_done",
                )
            }
        }
    }
}

@Composable
private fun TripPicker(trips: List<HereTrip>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nearby_here_pick_title)) },
        text = {
            Column {
                trips.forEach { trip ->
                    TpTextButton(onClick = {
                        onPick(trip.id)
                    }, modifier = Modifier.fillMaxWidth(), tid = "nearby.nearby_screen_text_button") { Text(trip.title) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TpTextButton(onClick = onDismiss, tid = "nearby.nearby_here_cancel") { Text(stringResource(R.string.nearby_here_cancel)) } },
    )
}

@Composable
private fun Sources(sources: List<GroundingSource>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs), modifier = Modifier.padding(top = TravelTheme.spacing.md)) {
        Text(stringResource(R.string.nearby_sources), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        sources.forEach { source ->
            val uri = source.uri ?: return@forEach
            TpButton(
                source.title?.takeIf(String::isNotBlank) ?: uri,
                { onOpen(uri) },
                variant = TpButtonVariant.Text,
                leadingIcon = Icons.AutoMirrored.Filled.ExitToApp,
                tid = "nearby.nearby_screen_tp_button_3",
            )
        }
    }
}

private fun sectionTitle(id: String): Int =
    when (id) {
        NearbySectionIds.SEE -> R.string.nearby_section_see
        NearbySectionIds.EAT -> R.string.nearby_section_eat
        NearbySectionIds.SHOP -> R.string.nearby_section_shop
        else -> R.string.nearby_section_stay
    }

/** Numbers in the user's locale ("1,4 km" in Spanish). */
@Composable
private fun decimal(value: Double): String =
    java.text.NumberFormat.getNumberInstance(io.github.rupakc.travelplanner.core.ui.watch.currentLocale()).apply { maximumFractionDigits = 1 }.format(value)

private const val GOOGLE_MAPS = "Google Maps"
private const val SKELETON_LINES = 6

private val previewState =
    NearbyUiState(
        phase = NearbyPhase.Loaded,
        place = NearbyPlace(name = "Lisbon", country = "Portugal", distanceKm = 0.4),
        sections =
            listOf(
                NearbySectionUi(
                    NearbySectionIds.SEE,
                    listOf(NearbyCard("see-1", "Miradouro da Senhora do Monte", "Wide view over the city.", "https://maps.google.com/?cid=1")),
                ),
                NearbySectionUi(
                    NearbySectionIds.EAT,
                    listOf(NearbyCard("eat-1", "Time Out Market", "Food hall with local chefs.", "https://maps.google.com/?cid=2")),
                ),
                NearbySectionUi(NearbySectionIds.SHOP, emptyList()),
                NearbySectionUi(NearbySectionIds.STAY, emptyList()),
            ),
        attribution = "Google Maps",
        sources = listOf(GroundingSource("Time Out Market", "https://maps.google.com/?cid=2")),
    )

@Preview(name = "Near me · light", widthDp = 400)
@Composable
private fun NearbyPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { NearbyScreen(previewState, {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Near me · dark, permission")
@Composable
private fun NearbyPermissionPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { NearbyScreen(NearbyUiState(NearbyPhase.NeedsPermission), {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Near me · denied")
@Composable
private fun NearbyDeniedPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { NearbyScreen(NearbyUiState(NearbyPhase.Denied), {}, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Near me · loading, dark")
@Composable
private fun NearbyLoadingPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { NearbyScreen(NearbyUiState(NearbyPhase.Loading), {}, {}, {}, {}, {}, {}, {}) }
}
