package io.github.rupakc.travelplanner.feature.nearby

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.nearby.NearbyPlace
import io.github.rupakc.travelplanner.core.model.section.Activity
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.AddToPlanButton
import io.github.rupakc.travelplanner.core.ui.BookingLinkButton
import io.github.rupakc.travelplanner.core.ui.PriceText

@Composable
fun NearMeRoute(onBack: () -> Unit, viewModel: NearbyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            viewModel.onEvent(NearbyEvent.PermissionResult(result.values.any { it }))
        }
    val context = LocalContext.current
    NearbyScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onRequestPermission = { permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
        onOpenAppSettings = { context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
        onOpenLocationSettings = { context.startSafely(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        onOpenMaps = { query -> context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))) },
    )
}

private fun Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        // No app for it (rare): nothing to open.
    }
}

/** Stateless Near me screen. */
@Composable
fun NearbyScreen(
    state: NearbyUiState,
    onEvent: (NearbyEvent) -> Unit,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenMaps: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TpTopBar(
                title = stringResource(R.string.nearby_title),
                onBack = onBack,
                actions = {
                    if (state.phase == NearbyPhase.Loaded) {
                        IconButton(onClick = { onEvent(NearbyEvent.Refresh) }) {
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
                        title = stringResource(R.string.nearby_denied_title),
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
                    })
                }

                is NearbyPhase.NoPlace -> {
                    EmptyState(
                        title = stringResource(R.string.nearby_no_place_title),
                        message = phase.message ?: stringResource(R.string.nearby_no_place),
                        icon = Icons.Filled.Place,
                        actionLabel = stringResource(R.string.nearby_try_again),
                        onAction = { onEvent(NearbyEvent.Refresh) },
                    )
                }

                is NearbyPhase.Failed -> {
                    ErrorState(phase.message ?: stringResource(R.string.nearby_failed), onRetry = { onEvent(NearbyEvent.Refresh) })
                }

                NearbyPhase.Loaded -> {
                    Results(state, onEvent, onOpenMaps)
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
        TpButton(stringResource(R.string.nearby_allow), onRequestPermission, leadingIcon = Icons.Filled.LocationOn)
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
        TpButton(primary.first, primary.second)
        TpButton(secondary.first, secondary.second, variant = TpButtonVariant.Text)
    }
}

@Composable
private fun Waiting(message: String) {
    SkeletonList(Modifier.padding(TravelTheme.spacing.gutter), message = message, lines = SKELETON_LINES)
}

@Composable
private fun Results(state: NearbyUiState, onEvent: (NearbyEvent) -> Unit, onOpenMaps: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(CARD_MIN_WIDTH),
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "place", span = { GridItemSpan(maxLineSpan) }, contentType = "place") { PlaceHeader(state.place, state.interests, state.items.size) }
        if (state.items.isEmpty()) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }, contentType = "empty") {
                EmptyState(title = stringResource(R.string.nearby_empty_title), message = stringResource(R.string.nearby_empty))
            }
        }
        items(state.items, key = { it.key }, contentType = { "item" }) { item ->
            NearbyCard(
                item = item,
                onToggle = { onEvent(NearbyEvent.TogglePlan(item.key)) },
                onOpenMaps = { onOpenMaps(mapsQuery(item.activity, state.place)) },
            )
        }
    }
}

@Composable
private fun PlaceHeader(place: NearbyPlace?, interests: List<String>, count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            place?.let(::placeLabel) ?: stringResource(R.string.nearby_title),
            style = TravelTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        val distance = place?.distanceKm?.let { stringResource(R.string.nearby_distance, decimal(it)) }
        Text(
            listOfNotNull(distance, pluralStringResource(R.plurals.nearby_count, count, count)).joinToString(" · "),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        if (interests.isNotEmpty()) {
            Text(
                stringResource(R.string.nearby_for_interests, interests.map { interestName(it) }.joinToString(", ")),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NearbyCard(item: NearbyItem, onToggle: () -> Unit, onOpenMaps: () -> Unit) {
    val a = item.activity
    val accent = TravelTheme.sectionAccents[SectionId.ACTIVITIES]
    TpCard(selected = item.inPlan, accent = accent.bar, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            a.category?.let { cat ->
                Surface(color = accent.tint, shape = TravelShape.Pill) {
                    Text(
                        cat,
                        style = TravelTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
                    )
                }
            }
            Text(a.name.orEmpty(), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            a.description?.let { Text(it, style = TravelTheme.typography.bodyMedium, maxLines = DESCRIPTION_LINES, overflow = TextOverflow.Ellipsis) }
            val meta =
                listOfNotNull(
                    a.rating?.let { r ->
                        val reviews = a.reviewCount
                        if (reviews !=
                            null
                        ) {
                            stringResource(R.string.nearby_rating_reviews, decimal(r), reviews)
                        } else {
                            stringResource(R.string.nearby_rating, decimal(r))
                        }
                    },
                    a.durationHours?.let { stringResource(R.string.nearby_duration, decimal(it)) },
                    a.location,
                )
            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            PriceText(a.priceUsd, isEstimate = true, hideWhenUnknown = true, style = TravelTheme.typography.titleSmall)
            if (item.why.isNotEmpty()) {
                Text(
                    stringResource(R.string.nearby_why, item.why.map { interestName(it) }.joinToString(", ")),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.primary,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                AddToPlanButton(selected = item.inPlan, itemName = a.name.orEmpty(), onToggle = onToggle)
                BookingLinkButton(a.bookingUrl, providerName = providerOf(a.source), variant = TpButtonVariant.Text)
                TpButton(stringResource(R.string.nearby_open_maps), onOpenMaps, variant = TpButtonVariant.Text, leadingIcon = Icons.Filled.Place)
            }
        }
    }
}

/** Numbers in the user's locale ("1,4 km" in Spanish). */
@Composable
private fun decimal(value: Double): String =
    java.text.NumberFormat.getNumberInstance(io.github.rupakc.travelplanner.core.ui.watch.currentLocale()).apply { maximumFractionDigits = 1 }.format(value)

/** A web interest id in the UI language; unknown ids as given. */
@Composable
private fun interestName(id: String): String =
    when (id) {
        "food" -> stringResource(R.string.nearby_interest_food)
        "history" -> stringResource(R.string.nearby_interest_history)
        "adventure" -> stringResource(R.string.nearby_interest_adventure)
        "culture" -> stringResource(R.string.nearby_interest_culture)
        "nature" -> stringResource(R.string.nearby_interest_nature)
        "shopping" -> stringResource(R.string.nearby_interest_shopping)
        "nightlife" -> stringResource(R.string.nearby_interest_nightlife)
        "wellness" -> stringResource(R.string.nearby_interest_wellness)
        "art" -> stringResource(R.string.nearby_interest_art)
        "family" -> stringResource(R.string.nearby_interest_family)
        else -> id
    }

/** Provider names as text only (web `providerName`), for "Open GetYourGuide". */
private fun providerOf(source: String?): String? =
    when (source) {
        "getyourguide" -> "GetYourGuide"
        "viator" -> "Viator"
        "klook" -> "Klook"
        "tripadvisor" -> "Tripadvisor"
        "tiqets" -> "Tiqets"
        "musement" -> "Musement"
        else -> null
    }

private val CARD_MIN_WIDTH = 340.dp
private const val SKELETON_LINES = 6
private const val DESCRIPTION_LINES = 3

private val previewState =
    NearbyUiState(
        phase = NearbyPhase.Loaded,
        place = NearbyPlace(name = "Kyoto", region = "Kyoto", country = "Japan", distanceKm = 1.4),
        interests = listOf("food", "culture"),
        items =
            listOf(
                NearbyItem(
                    "0",
                    Activity(
                        name = "Fushimi Inari Taisha Early Morning Hike",
                        description = "Walk the torii gates before the crowds.",
                        category = "culture",
                        rating = 4.8,
                        reviewCount = 12000,
                        priceUsd = 45.0,
                        bookingUrl = "https://www.getyourguide.com/s/?q=Fushimi",
                        source = "getyourguide",
                    ),
                    listOf("culture"),
                    inPlan = false,
                ),
            ),
    )

@Preview(name = "Near me · light", widthDp = 400)
@Composable
private fun NearbyPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { NearbyScreen(previewState, {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Near me · dark, permission")
@Composable
private fun NearbyPermissionPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { NearbyScreen(NearbyUiState(NearbyPhase.NeedsPermission), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Near me · denied")
@Composable
private fun NearbyDeniedPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { NearbyScreen(NearbyUiState(NearbyPhase.Denied), {}, {}, {}, {}, {}, {}) }
}

@Preview(name = "Near me · loading, dark")
@Composable
private fun NearbyLoadingPreview() {
    TravelTheme(themeMode = ThemeMode.DARK) { NearbyScreen(NearbyUiState(NearbyPhase.Loading), {}, {}, {}, {}, {}, {}) }
}
