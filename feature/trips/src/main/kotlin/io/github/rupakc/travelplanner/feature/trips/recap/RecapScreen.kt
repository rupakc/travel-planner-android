package io.github.rupakc.travelplanner.feature.trips.recap

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.rememberReduceMotion
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.journey.RecapRules
import io.github.rupakc.travelplanner.core.domain.search.searchText
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.social.IdeaKind
import io.github.rupakc.travelplanner.core.model.trips.Phase
import io.github.rupakc.travelplanner.core.model.trips.Recap
import io.github.rupakc.travelplanner.core.model.trips.RecapCountry
import io.github.rupakc.travelplanner.core.model.trips.Trip
import io.github.rupakc.travelplanner.core.ui.share.LocalShareLauncher
import io.github.rupakc.travelplanner.core.ui.share.ShareLauncher
import io.github.rupakc.travelplanner.core.ui.social.IdeaEntryButton
import io.github.rupakc.travelplanner.feature.trips.R
import io.github.rupakc.travelplanner.feature.trips.trip.TripTabContent
import java.text.NumberFormat
import java.util.Currency

/**
 * The Recap tab for `TripTabContent` (`tripsGraph(tabContent = mapOf(TripTab.Recap to recapTabContent(...)))`).
 * [onWhereNext] opens Explore; a button whose lambda is null is not shown. With [onPlanThis] (a host that can prefill Search), the
 * "Where next?" button shows up to five suggestions here instead, each with "Plan this" (the destination text goes to the host);
 * Explore stays the fallback when suggestions cannot be had. Without it the button opens Explore as before.
 */
fun recapTabContent(onWhereNext: (() -> Unit)? = null, onPlanThis: ((destination: String) -> Unit)? = null): TripTabContent =
    { trip, modifier ->
        RecapRoute(onWhereNext, modifier, trip.phase, onPlanThis = onPlanThis)
    }

@Composable
fun RecapRoute(
    onWhereNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    phase: Phase? = null,
    onPlanThis: ((destination: String) -> Unit)? = null,
    viewModel: RecapViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // A phase change (the user sets a stage, or the dates move it) refetches the recap instead of keeping the old one.
    LaunchedEffect(phase) { viewModel.onEvent(RecapEvent.PhaseShown(phase)) }
    val context = LocalContext.current
    val chooser = stringResource(R.string.recap_share_chooser)
    val shareText = state.recap?.let { shareText(it) }
    // Share links 2.0 (card + link with privacy options) when the backend has it; otherwise the plain-text share.
    val shareLauncher = LocalShareLauncher.current
    RecapScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onShare = {
            if (shareRecap(shareLauncher, state.recap) { shareText != null && share(context, shareText, chooser) }) {
                viewModel.onEvent(RecapEvent.Shared)
            }
        },
        onWhereNext =
            onWhereNext?.let { f ->
                {
                    viewModel.onEvent(RecapEvent.WhereNext)
                    // A host that cannot prefill Search keeps the plain Explore link.
                    if (onPlanThis == null) f()
                }
            },
        onPlanThis = onPlanThis,
        onExplore = onWhereNext.takeIf { onPlanThis != null },
        modifier = modifier,
    )
}

/**
 * Share the recap: the Share links 2.0 dialog when [launcher] exists (the trip's id is its plan id), else [legacy] (the plain-text
 * sheet). True when a share was started.
 */
internal fun shareRecap(launcher: ShareLauncher?, recap: Recap?, legacy: () -> Boolean): Boolean =
    if (launcher != null && recap != null) {
        launcher.open(ShareKind.RECAP, recap.tripId)
        true
    } else {
        legacy()
    }

/** The share text of a recap: counts and countries only, no amounts. */
@Composable
internal fun shareText(recap: Recap): String {
    val days = recap.days ?: 0
    val places = recap.visitedCount
    val head = stringResource(R.string.recap_share_head, recap.title)
    val line = stringResource(R.string.recap_share_stats, days, places)
    val countries = recap.countries.mapNotNull { it.name ?: it.code.takeIf { c -> c.isNotBlank() } }
    return listOfNotNull(head, line, countries.takeIf { it.isNotEmpty() }?.joinToString(", ")).joinToString("\n")
}

private fun share(context: Context, text: String, chooserTitle: String): Boolean =
    try {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

@Composable
fun RecapScreen(
    state: RecapUiState,
    onEvent: (RecapEvent) -> Unit,
    onShare: () -> Unit,
    onWhereNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onPlanThis: ((destination: String) -> Unit)? = null,
    onExplore: (() -> Unit)? = null,
) {
    val recap = state.recap
    when {
        state.loading -> {
            SkeletonList(modifier, message = stringResource(R.string.trip_loading))
        }

        state.failed || recap == null -> {
            ErrorState(null, modifier, title = stringResource(R.string.recap_load_failed), onRetry = { onEvent(RecapEvent.Retry) })
        }

        else -> {
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(TravelTheme.spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
            ) {
                item(key = "stats", contentType = "stats") { StatsCard(recap) }
                item(key = "spend", contentType = "spend") { SpendCard(state, recap) }
                val points = RecapRules.routePoints(recap)
                if (points.size >= 2) item(key = "route", contentType = "route") { RouteCard(points) }
                if (recap.countries.isNotEmpty()) item(key = "countries", contentType = "countries") { CountriesCard(recap.countries, state.newCountries) }
                item(key = "actions", contentType = "actions") { Actions(state, recap, onEvent, onShare, onWhereNext) }
                if (state.whereNext != WhereNextState.Idle && onPlanThis != null) {
                    item(key = "where_next", contentType = "where_next") { WhereNextCard(state.whereNext, onEvent, onPlanThis, onExplore) }
                }
            }
        }
    }
}

@Composable
private fun StatsCard(recap: Recap) {
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Text(recap.title, style = TravelTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            recap.days?.let { Text(stringResource(R.string.recap_days, it), style = TravelTheme.typography.titleMedium) }
            Text(stringResource(R.string.recap_places, recap.visitedCount, recap.ratedCount), style = TravelTheme.typography.bodyMedium)
            RecapRules.averageStars(recap)?.let {
                Text(stringResource(R.string.recap_avg_stars, oneDecimal(it)), style = TravelTheme.typography.bodyMedium)
            }
            // A trip without coordinates has no distance: say so instead of showing 0.
            val distance = RecapRules.distanceKm(recap)
            Text(
                if (distance == null) {
                    stringResource(R.string.recap_distance_none)
                } else {
                    stringResource(R.string.recap_distance, NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(distance))
                },
                style = TravelTheme.typography.bodyMedium,
            )
        }
    }
}

private fun usd(amount: Number): String =
    NumberFormat.getCurrencyInstance().apply {
        currency = Currency.getInstance("USD")
        maximumFractionDigits = 0
    }.format(amount)

@Composable
private fun SpendCard(state: RecapUiState, recap: Recap) {
    val spend = RecapRules.spend(recap)
    val plan = state.planCostUsd
    if (spend == null && plan == null) return
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Text(stringResource(R.string.recap_spend_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            if (spend != null) {
                Text(stringResource(R.string.recap_spent, usd(spend.spent)), style = TravelTheme.typography.titleMedium)
                spend.budget?.let { budget ->
                    Text(stringResource(R.string.recap_budget, usd(budget)), style = TravelTheme.typography.bodyMedium)
                    Text(
                        if (spend.over) {
                            stringResource(
                                R.string.recap_over_budget,
                                usd(spend.amount),
                            )
                        } else {
                            stringResource(R.string.recap_under_budget, usd(spend.amount))
                        },
                        style = TravelTheme.typography.bodyMedium,
                        color = if (spend.over) TravelTheme.colors.error else TravelTheme.colors.onSurfaceVariant,
                    )
                }
            }
            plan?.let {
                Text(stringResource(R.string.recap_plan_cost, usd(it)), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            if (recap.spend.unconverted.isNotEmpty()) {
                Text(
                    stringResource(R.string.recap_unconverted, recap.spend.unconverted.joinToString(", ")),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RouteCard(points: List<Pair<Double, Double>>) {
    val description = stringResource(R.string.recap_route_description)
    val line = TravelTheme.colors.primary
    val dot = TravelTheme.colors.onSurface
    val projected = remember(points) { RecapRules.project(points) }
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(stringResource(R.string.recap_route_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f).semantics { contentDescription = description }) {
                val pad = 16.dp.toPx()
                val box = Size(size.width - 2 * pad, size.height - 2 * pad)
                val side = minOf(box.width, box.height)
                val origin = Offset(pad + (box.width - side) / 2, pad + (box.height - side) / 2)
                val at = { p: RecapRules.MapPoint -> Offset(origin.x + p.x * side, origin.y + p.y * side) }
                val path = Path()
                projected.forEachIndexed { i, p -> at(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                drawPath(path, line, style = Stroke(width = 3.dp.toPx()))
                projected.forEach { drawCircle(dot, radius = 3.dp.toPx(), center = at(it)) }
                // Start and end are larger so direction reads without colour.
                drawCircle(line, radius = 6.dp.toPx(), center = at(projected.first()))
                drawCircle(line, radius = 6.dp.toPx(), center = at(projected.last()))
            }
        }
    }
}

@Composable
private fun CountriesCard(countries: List<RecapCountry>, newCountries: Set<String>) {
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(stringResource(R.string.recap_countries_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                countries.forEach { Stamp(it, isNew = it.new || it.code in newCountries) }
            }
        }
    }
}

/** A country stamp; a new one scales in once (static under reduced motion) and says "New" in words. */
@Composable
private fun Stamp(country: RecapCountry, isNew: Boolean) {
    val reduceMotion = rememberReduceMotion()
    var shown by remember { mutableStateOf(reduceMotion || !isNew) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 0.6f, label = "stamp")
    val name = country.name ?: country.code
    val label = if (isNew) stringResource(R.string.recap_country_new, name) else name
    Text(
        label,
        style = TravelTheme.typography.labelLarge,
        modifier = Modifier.scale(if (reduceMotion) 1f else scale).semantics { contentDescription = label }.padding(TravelTheme.spacing.xs),
        color = if (isNew) TravelTheme.colors.primary else TravelTheme.colors.onSurface,
    )
}

@Composable
private fun Actions(state: RecapUiState, recap: Recap, onEvent: (RecapEvent) -> Unit, onShare: () -> Unit, onWhereNext: (() -> Unit)?) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            if (recap.completedAt != null) {
                Text(stringResource(R.string.recap_completed), style = TravelTheme.typography.titleSmall, color = TravelTheme.colors.primary)
            }
            state.completeFailure?.let {
                Text(
                    if (it.notReady) stringResource(R.string.recap_err_not_ready) else it.message ?: stringResource(R.string.recap_err_generic),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.error,
                )
            }
        }
        if (RecapRules.showComplete(recap)) {
            TpButton(
                stringResource(R.string.recap_complete),
                { onEvent(RecapEvent.Complete) },
                Modifier.fillMaxWidth(),
                loading = state.completing,
                tid = "recap.complete",
            )
        }
        TpButton(stringResource(R.string.recap_share), onShare, Modifier.fillMaxWidth(), variant = TpButtonVariant.Secondary, tid = "recap.share")
        IdeaEntryButton(recap.tripId.takeIf { it > 0 }, IdeaKind.RECAP, Modifier.fillMaxWidth(), TpButtonVariant.Secondary, tid = "recap.share_idea")
        onWhereNext?.let {
            TpButton(stringResource(R.string.recap_where_next), it, Modifier.fillMaxWidth(), variant = TpButtonVariant.Secondary, tid = "recap.where_next")
        }
    }
}

/** Suggestions after a trip: loading, up to five picks (AI suggestions, estimated costs), or a reason and the way on. */
@Composable
private fun WhereNextCard(state: WhereNextState, onEvent: (RecapEvent) -> Unit, onPlanThis: (String) -> Unit, onExplore: (() -> Unit)?) {
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(stringResource(R.string.recap_wn_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                when (state) {
                    WhereNextState.Idle -> {}

                    WhereNextState.Loading -> {
                        Text(stringResource(R.string.recap_wn_loading), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
                    }

                    is WhereNextState.Ready -> {
                        if (state.picks.isEmpty()) {
                            Text(stringResource(R.string.recap_wn_empty), style = TravelTheme.typography.bodyMedium)
                        } else {
                            Text(stringResource(R.string.recap_wn_note), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                            state.picks.forEach { WhereNextPick(it, onPlanThis) }
                        }
                    }

                    WhereNextState.NeedsProfile -> {
                        Text(stringResource(R.string.recap_wn_needs_profile), style = TravelTheme.typography.bodyMedium)
                    }

                    WhereNextState.AiLimit -> {
                        Text(stringResource(R.string.recap_wn_ai_limit), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.error)
                    }

                    WhereNextState.Failed -> {
                        Text(stringResource(R.string.recap_wn_error), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.error)
                    }
                }
            }
            val failed = state == WhereNextState.Failed
            if (failed) TpButton(stringResource(R.string.recap_wn_retry), { onEvent(RecapEvent.WhereNext) }, variant = TpButtonVariant.Tonal, tid = "recap.where_next_retry")
            if (onExplore != null && state !is WhereNextState.Loading && state !is WhereNextState.Ready) {
                TpButton(stringResource(R.string.recap_wn_explore), onExplore, variant = TpButtonVariant.Text, tid = "recap.where_next_explore")
            }
        }
    }
}

@Composable
private fun WhereNextPick(pick: DiscoveredDestination, onPlanThis: (String) -> Unit) {
    val place = pick.searchText()
    val low = pick.estimatedCostUsdLow
    val high = pick.estimatedCostUsdHigh
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(place, style = TravelTheme.typography.titleMedium)
        pick.matchReasons.firstOrNull()?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
        if (low != null && high != null) {
            Text(
                stringResource(R.string.recap_wn_estimate, usd(low), usd(high)),
                style = TravelTheme.typography.bodySmall,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
        TpButton(stringResource(R.string.recap_wn_plan), { onPlanThis(place) }, variant = TpButtonVariant.Tonal, tid = "recap.where_next_plan")
    }
}

private fun oneDecimal(value: Double): String =
    NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)
