package io.github.rupakc.travelplanner.feature.home

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonBlock
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.place.placeName
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearch
import io.github.rupakc.travelplanner.core.model.preferences.RecentSearchIn
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.profile.descriptionRes
import io.github.rupakc.travelplanner.core.ui.profile.featureIcon
import io.github.rupakc.travelplanner.core.ui.profile.titleRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Layout constants (defined once). */
private object HomeDimens {
    val TileMinWidth = 168.dp
    val ContentMaxWidth = 1040.dp
    val IconBadge = 40.dp
    val RecentCardWidth = 240.dp
    val SuggestionCardWidth = 220.dp
    val SkeletonTitle = 32.dp
    val SkeletonBar = 48.dp
    val SkeletonCard = 140.dp
    val SkeletonTile = 120.dp
}

private const val ISO_DATE_LENGTH = 10
private const val SKELETON_TITLE_FRACTION = 0.6f

@Composable
internal fun HomeRoute(onNavigate: (AppRoute) -> Unit, viewModel: HomeViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onEvent = { event -> event.destination(state.upcomingTrip?.planId)?.let(onNavigate) ?: viewModel.onEvent(event) },
        modifier = modifier,
    )
}

/** Personalised Home (stateless). Top-level screen: the shell draws the top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeUiState, onEvent: (HomeEvent) -> Unit, modifier: Modifier = Modifier) {
    val spacing = TravelTheme.spacing
    PullToRefreshBox(
        isRefreshing = state.refreshing && !state.loading,
        onRefresh = { onEvent(HomeEvent.Refresh) },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(HomeDimens.TileMinWidth),
            modifier = Modifier.widthIn(max = HomeDimens.ContentMaxWidth).fillMaxSize().align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = spacing.gutter, vertical = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (state.loading) {
                full("skeleton") { HomeSkeleton() }
                return@LazyVerticalGrid
            }
            full("greeting") { Greeting(state.userName, state.travellerTypes, onEvent) }
            full("search") { SearchBarButton(onClick = { onEvent(HomeEvent.OpenSearch) }) }
            state.refreshError?.let { error -> full("error") { RefreshErrorBanner(error.message, onEvent) } }
            full("trip") {
                val trip = state.upcomingTrip
                if (trip != null) UpcomingTripCard(trip, onClick = { onEvent(HomeEvent.OpenTrip(trip.planId)) }) else PlanNextTripCard(onEvent)
            }
            state.becauseYouSearched?.let { because -> full("because") { BecauseYouSearchedRow(because, state.placeNames, onEvent) } }
            full("tools_heading") { SectionHeading(stringResource(R.string.home_tools_heading)) }
            if (state.tiles.isEmpty()) {
                full("tools_empty") {
                    EmptyState(
                        title = stringResource(R.string.home_tools_empty_title),
                        message = stringResource(R.string.home_tools_empty_message),
                        actionLabel = stringResource(R.string.home_tools_empty_action),
                        onAction = { onEvent(HomeEvent.OpenTravellerType) },
                    )
                }
            } else {
                items(state.tiles, key = { "tile-${it.feature.id}" }, contentType = { "tile" }) { tile ->
                    FeatureTileCard(tile, onClick = { onEvent(HomeEvent.OpenFeature(tile.feature)) })
                }
            }
            if (state.tasteHighlights.isNotEmpty()) full("taste") { TasteRow(state.tasteHighlights, onEvent) }
            if (state.recentSearches.isNotEmpty()) full("recent") { RecentSearchesRow(state.recentSearches, state.placeNames, onEvent) }
        }
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }, contentType = key) { content() }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TravelTheme.typography.titleMedium,
        color = TravelTheme.colors.onSurface,
        modifier = modifier.padding(top = TravelTheme.spacing.sm).semantics { heading() },
    )
}

@Composable
private fun Greeting(userName: String?, types: List<TravellerType>, onEvent: (HomeEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(
            if (userName.isNullOrBlank()) stringResource(R.string.home_greeting_anonymous) else stringResource(R.string.home_greeting, userName),
            style = TravelTheme.typography.headlineMedium,
            color = TravelTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        val typeLabel =
            if (types.isEmpty()) {
                stringResource(R.string.home_type_unset)
            } else {
                types.map { stringResource(it.titleRes()) }.joinToString(stringResource(R.string.home_type_joiner))
            }
        val typeDescription = stringResource(R.string.home_type_a11y, typeLabel)
        AssistChip(
            onClick = { onEvent(HomeEvent.OpenTravellerType) },
            label = { Text(typeLabel) },
            leadingIcon = { Icon(Icons.Filled.Face, contentDescription = null) },
            modifier = Modifier.heightIn(min = TravelTheme.spacing.minTouchTarget).semantics { contentDescription = typeDescription },
        )
    }
}

@Composable
private fun SearchBarButton(onClick: () -> Unit) {
    val label = stringResource(R.string.home_search_hint)
    Surface(
        shape = TravelShape.Pill,
        color = TravelTheme.colors.surfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = TravelTheme.spacing.minTouchTarget)
                .clip(TravelShape.Pill)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.home_search_open), onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = TravelTheme.spacing.lg, vertical = TravelTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant)
            Spacer(Modifier.width(TravelTheme.spacing.md))
            Text(label, style = TravelTheme.typography.bodyLarge, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun RefreshErrorBanner(message: String?, onEvent: (HomeEvent) -> Unit) {
    Surface(color = TravelTheme.extendedColors.warningContainer, shape = TravelShape.ItemCard) {
        Column(Modifier.fillMaxWidth().padding(TravelTheme.spacing.md)) {
            Text(
                message ?: stringResource(R.string.home_refresh_failed),
                style = TravelTheme.typography.bodyMedium,
                color = TravelTheme.extendedColors.onWarningContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                TpButton(stringResource(R.string.home_retry), onClick = { onEvent(HomeEvent.Refresh) }, variant = TpButtonVariant.Text)
                TpButton(stringResource(R.string.home_dismiss), onClick = { onEvent(HomeEvent.DismissError) }, variant = TpButtonVariant.Text)
            }
        }
    }
}

@Composable
private fun rememberDateFormatter(): DateTimeFormatter {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
}

@Composable
private fun UpcomingTripCard(trip: UpcomingTrip, onClick: () -> Unit) {
    val formatter = rememberDateFormatter()
    val dates =
        trip.returnDate?.let { stringResource(R.string.home_date_range, formatter.format(trip.departure), formatter.format(it)) }
            ?: formatter.format(trip.departure)
    val countdown =
        when {
            trip.ongoing -> stringResource(R.string.home_trip_ongoing)
            trip.daysUntil == 0L -> stringResource(R.string.home_trip_today)
            else -> pluralStringResource(R.plurals.home_trip_in_days, trip.daysUntil.toInt(), trip.daysUntil.toInt())
        }
    TpCard(size = TpCardSize.Large, accent = TravelTheme.colors.primary, onClick = onClick) {
        Column(Modifier.padding(TravelTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Text(stringResource(R.string.home_next_trip), style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.primary)
            Text(
                trip.destination,
                style = TravelTheme.typography.displaySmall,
                color = TravelTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (trip.name != trip.destination) Text(trip.name, style = TravelTheme.typography.titleMedium, color = TravelTheme.colors.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = TravelTheme.spacing.sm)) {
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant)
                Spacer(Modifier.width(TravelTheme.spacing.sm))
                Text(dates, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                Pill(countdown)
            }
        }
    }
}

@Composable
private fun Pill(text: String, container: Color = TravelTheme.colors.primaryContainer, content: Color = TravelTheme.colors.onPrimaryContainer) {
    Text(
        text,
        style = TravelTheme.typography.labelLarge,
        color = content,
        modifier = Modifier.clip(TravelShape.Pill).background(container).padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs),
    )
}

@Composable
private fun PlanNextTripCard(onEvent: (HomeEvent) -> Unit) {
    TpCard(size = TpCardSize.Large) {
        Column(Modifier.padding(TravelTheme.spacing.xl), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(
                stringResource(R.string.home_plan_next_title),
                style = TravelTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.home_plan_next_message), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
            TpButton(stringResource(R.string.home_plan_trip), onClick = { onEvent(HomeEvent.PlanTrip) }, leadingIcon = Icons.Filled.Search)
        }
    }
}

@Composable
private fun FeatureIconBadge(feature: FeatureId, iconName: String?) {
    val accent = feature.sectionId()?.let { TravelTheme.sectionAccents[it] }
    Box(
        Modifier
            .size(HomeDimens.IconBadge)
            .clip(TravelShape.ItemCard)
            .background(accent?.tint ?: TravelTheme.colors.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(featureIcon(iconName.orEmpty()), contentDescription = null, tint = accent?.icon ?: TravelTheme.colors.onPrimaryContainer)
    }
}

@Composable
private fun FeatureTileCard(tile: FeatureTile, onClick: () -> Unit) {
    TpCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            FeatureIconBadge(tile.feature, tile.iconName)
            Text(stringResource(tile.feature.titleRes()), style = TravelTheme.typography.titleSmall, color = TravelTheme.colors.onSurface)
            Text(
                stringResource(tile.feature.descriptionRes()),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.onSurfaceVariant,
            )
            when {
                tile.reason != null -> {
                    ReasonLabel(tile.reason)
                }

                !tile.feature.isTool -> {
                    Text(
                        stringResource(R.string.home_in_results),
                        style = TravelTheme.typography.labelSmall,
                        color = TravelTheme.colors.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReasonLabel(reason: TileReason) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, contentDescription = null, tint = TravelTheme.colors.primary, modifier = Modifier.size(TravelTheme.spacing.lg))
        Spacer(Modifier.width(TravelTheme.spacing.xs))
        Text(stringResource(reason.labelRes()), style = TravelTheme.typography.labelSmall, color = TravelTheme.colors.primary)
    }
}

internal fun TileReason.labelRes(): Int =
    when (this) {
        TileReason.SEARCHED_WITH_BUDGET -> R.string.home_reason_budget
        TileReason.SEARCHED_WITH_ACCESSIBILITY -> R.string.home_reason_accessibility
        TileReason.SEARCHED_WITH_KIDS -> R.string.home_reason_kids
        TileReason.SEARCHED_MULTI_CITY -> R.string.home_reason_multi_city
        TileReason.SEARCHED_SEVERAL_DESTINATIONS -> R.string.home_reason_several
        TileReason.SEARCHED_DESTINATION -> R.string.home_reason_destination
        TileReason.TASTE_EVENTS, TileReason.TASTE_WALKING -> R.string.home_reason_taste
    }

@Composable
private fun BecauseYouSearchedRow(because: BecauseYouSearched, names: Map<String, String>, onEvent: (HomeEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        SectionHeading(stringResource(R.string.home_because_heading, placeName(because.destination, names)))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            items(because.tiles, key = { "because-${it.feature.id}" }, contentType = { "suggestion" }) { tile ->
                TpCard(onClick = { onEvent(HomeEvent.OpenFeature(tile.feature)) }, modifier = Modifier.width(HomeDimens.SuggestionCardWidth)) {
                    Row(Modifier.padding(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
                        FeatureIconBadge(tile.feature, tile.iconName)
                        Spacer(Modifier.width(TravelTheme.spacing.md))
                        Column {
                            Text(stringResource(tile.feature.titleRes()), style = TravelTheme.typography.titleSmall)
                            tile.reason?.let {
                                Text(stringResource(it.labelRes()), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            item(key = "because-again", contentType = "again") {
                TpButton(
                    stringResource(R.string.home_because_again),
                    onClick = { onEvent(HomeEvent.OpenRecentSearch(because.recentSearchId)) },
                    variant = TpButtonVariant.Tonal,
                )
            }
        }
    }
}

@Composable
private fun TasteRow(values: List<String>, onEvent: (HomeEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        SectionHeading(stringResource(R.string.home_taste_heading))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            values.forEach { value ->
                AssistChip(
                    onClick = { onEvent(HomeEvent.OpenTasteGraph) },
                    label = { Text(value) },
                    modifier = Modifier.heightIn(min = TravelTheme.spacing.minTouchTarget),
                )
            }
        }
    }
}

/** "A → B → C" for a recent search (stops in journey order). */
internal fun RecentSearchIn.routeLabel(names: Map<String, String> = emptyMap()): String =
    (listOf(origin) + stops.map { it.city } + destination).joinToString(" → ") { placeName(it, names) }

internal val RecentSearchIn.travellerCount: Int get() = maxOf(1, adults + children + seniors + infants)

@Composable
private fun RecentSearchesRow(recent: List<RecentSearch>, names: Map<String, String>, onEvent: (HomeEvent) -> Unit) {
    val formatter = rememberDateFormatter()
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        SectionHeading(stringResource(R.string.home_recent_heading))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            items(recent, key = { "recent-${it.id}" }, contentType = { "recent" }) { item ->
                val s = item.search
                val departure = s.departureDate.toDateOrNull()?.let(formatter::format) ?: s.departureDate
                val dates =
                    s.returnDate?.let { r -> stringResource(R.string.home_date_range, departure, r.toDateOrNull()?.let(formatter::format) ?: r) } ?: departure
                TpCard(onClick = { onEvent(HomeEvent.OpenRecentSearch(item.id)) }, modifier = Modifier.width(HomeDimens.RecentCardWidth)) {
                    Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                        Text(s.routeLabel(names), style = TravelTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(dates, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                        Text(
                            pluralStringResource(R.plurals.home_travellers, s.travellerCount, s.travellerCount),
                            style = TravelTheme.typography.bodySmall,
                            color = TravelTheme.colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun String.toDateOrNull(): LocalDate? = runCatching { LocalDate.parse(take(ISO_DATE_LENGTH)) }.getOrNull()

@Composable
private fun HomeSkeleton() {
    val loading = stringResource(R.string.home_loading)
    Column(Modifier.semantics { contentDescription = loading }, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        SkeletonBlock(Modifier.fillMaxWidth(SKELETON_TITLE_FRACTION), height = HomeDimens.SkeletonTitle)
        SkeletonBlock(Modifier.fillMaxWidth(), height = HomeDimens.SkeletonBar)
        SkeletonBlock(Modifier.fillMaxWidth(), height = HomeDimens.SkeletonCard)
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            repeat(2) { SkeletonBlock(Modifier.weight(1f), height = HomeDimens.SkeletonTile) }
        }
    }
}

private val previewState =
    HomeUiState(
        loading = false,
        userName = "alice",
        travellerTypes = listOf(TravellerType.CULTURE_FOODIE, TravellerType.ACCESSIBLE),
        upcomingTrip = UpcomingTrip(7, "Tokyo spring", "Tokyo", LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 8), daysUntil = 22),
        tiles =
            listOf(
                FeatureTile(FeatureId.BUDGET_PLANNER, TileReason.SEARCHED_WITH_BUDGET),
                FeatureTile(FeatureId.COMPARE),
                FeatureTile(FeatureId.CHAT),
                FeatureTile(FeatureId.WALKING_TOURS),
            ),
        becauseYouSearched = BecauseYouSearched("rs-1", "Lisbon", listOf(FeatureTile(FeatureId.BUDGET_PLANNER, TileReason.SEARCHED_WITH_BUDGET))),
        recentSearches = listOf(RecentSearch("rs-1", RecentSearchIn("New York", "Lisbon", "2027-05-01", "2027-05-06", budgetUsd = 1200.0))),
        tasteHighlights = listOf("food", "history"),
    )

@Preview(name = "Home light", widthDp = 380, heightDp = 1200)
@Composable
private fun HomePreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { Surface { HomeScreen(previewState, onEvent = {}) } }
}

@Preview(name = "Home dark", widthDp = 380, heightDp = 1200, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePreviewDark() {
    TravelTheme(themeMode = ThemeMode.DARK) { Surface { HomeScreen(previewState, onEvent = {}) } }
}

@Preview(name = "Home empty", widthDp = 380, heightDp = 800)
@Composable
private fun HomePreviewEmpty() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { Surface { HomeScreen(HomeUiState(loading = false, userName = "alice"), onEvent = {}) } }
}

@Preview(name = "Home loading", widthDp = 380, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomePreviewLoading() {
    TravelTheme(themeMode = ThemeMode.DARK) { Surface { HomeScreen(HomeUiState(), onEvent = {}) } }
}
