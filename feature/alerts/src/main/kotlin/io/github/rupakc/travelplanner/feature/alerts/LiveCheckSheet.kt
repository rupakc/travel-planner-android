package io.github.rupakc.travelplanner.feature.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.alerts.AnyWatch
import io.github.rupakc.travelplanner.core.domain.alerts.routeLabel
import io.github.rupakc.travelplanner.core.domain.format.formatPrice
import io.github.rupakc.travelplanner.core.domain.live.LiveFailure
import io.github.rupakc.travelplanner.core.domain.live.googleSearchUrl
import io.github.rupakc.travelplanner.core.domain.live.safeWebUri
import io.github.rupakc.travelplanner.core.model.alerts.LiveCheckResult
import io.github.rupakc.travelplanner.core.model.alerts.LiveQuote
import io.github.rupakc.travelplanner.core.model.grounding.GroundingSource
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale

/** The card's heading: the route for a fare, the title for a price watch. */
internal fun watchTitle(item: AnyWatch, placeNames: Map<String, String>): String? =
    when (item) {
        is AnyWatch.Fare -> routeLabel(item.watch, placeNames)
        is AnyWatch.Price -> item.watch.title
    }

/** A quote's source link, when it cites a valid http(s) source. */
internal fun quoteSource(quote: LiveQuote, sources: List<GroundingSource>): GroundingSource? =
    quote.sourceIndex?.let { sources.getOrNull(it) }?.takeIf { safeWebUri(it.uri) != null }

/** "Search the web now" result panel (bottom sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveCheckSheet(live: LiveCheckUi, title: String?, onRetry: () -> Unit, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LiveCheckContent(live, title, onRetry, onDismiss, onOpen)
    }
}

/**
 * The panel's content: the grounded summary exactly as returned, quotes with their source links,
 * "Searched for" queries linking to Google Search, the sources and the disclaimer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LiveCheckContent(live: LiveCheckUi, title: String?, onRetry: () -> Unit, onClose: () -> Unit, onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        Text(stringResource(R.string.alerts_live_title), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        title?.let { Text(it, style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant) }
        val result = live.result
        val error = live.error
        when {
            live.loading -> {
                SkeletonList(message = stringResource(R.string.alerts_live_searching), lines = SKELETON_LINES)
            }

            error != null -> {
                Text(
                    liveErrorText(error.failure, error.message),
                    style = TravelTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (error.failure != LiveFailure.NOT_FOUND) {
                    TpButton(stringResource(R.string.alerts_retry), onRetry, variant = TpButtonVariant.Secondary, leadingIcon = Icons.Filled.Refresh)
                }
            }

            result != null -> {
                LiveResult(result, onOpen)
            }
        }
        HorizontalDivider(color = TravelTheme.colors.outlineVariant)
        TpButton(stringResource(R.string.alerts_live_close), onClose, variant = TpButtonVariant.Text)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveResult(result: LiveCheckResult, onOpen: (String) -> Unit) {
    // The grounded answer is shown unmodified.
    Text(
        result.summary?.takeIf(String::isNotBlank) ?: stringResource(R.string.alerts_live_no_summary),
        style = TravelTheme.typography.bodyLarge,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    val locale = currentLocale()
    val quotes = result.quotes.filter { it.amount != null }
    if (quotes.isNotEmpty()) {
        Text(stringResource(R.string.alerts_live_quotes), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        quotes.forEach { q ->
            val amount = formatPrice(q.amount, q.currency, locale = locale)
            Text(listOfNotNull(q.label?.takeIf(String::isNotBlank), amount).joinToString(" · "), style = TravelTheme.typography.bodyMedium)
            quoteSource(q, result.sources)?.let { src ->
                val uri = src.uri ?: return@let
                TpButton(
                    stringResource(R.string.alerts_live_quote_source, src.title?.takeIf(String::isNotBlank) ?: uri),
                    { onOpen(uri) },
                    variant = TpButtonVariant.Text,
                    leadingIcon = Icons.AutoMirrored.Filled.ExitToApp,
                )
            }
        }
    }
    if (result.searchedFor.isNotEmpty()) {
        Text(stringResource(R.string.alerts_live_searched_for), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            result.searchedFor.forEach { q ->
                TpButton(q, { onOpen(googleSearchUrl(q)) }, variant = TpButtonVariant.Text, leadingIcon = Icons.Filled.Search)
            }
        }
    }
    val sources = result.sources.filter { safeWebUri(it.uri) != null }
    if (sources.isNotEmpty()) {
        Text(stringResource(R.string.alerts_live_sources), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        sources.forEach { src ->
            val uri = src.uri ?: return@forEach
            TpButton(src.title?.takeIf(String::isNotBlank) ?: uri, {
                onOpen(uri)
            }, variant = TpButtonVariant.Text, leadingIcon = Icons.AutoMirrored.Filled.ExitToApp)
        }
    }
    Text(
        result.disclaimer?.takeIf(String::isNotBlank) ?: stringResource(R.string.alerts_live_disclaimer),
        style = TravelTheme.typography.bodySmall,
        color = TravelTheme.colors.onSurfaceVariant,
    )
}

/** The backend's words when it sent any, else our copy for the failure (offline is always ours). */
@Composable
private fun liveErrorText(failure: LiveFailure, message: String?): String {
    if (failure == LiveFailure.OFFLINE) return stringResource(R.string.alerts_live_offline)
    message?.let { return it }
    return stringResource(
        when (failure) {
            LiveFailure.RATE_LIMITED -> R.string.alerts_live_rate_limited
            LiveFailure.AI_LIMIT -> R.string.alerts_live_ai_limit
            LiveFailure.AI_BILLING -> R.string.alerts_live_ai_billing
            LiveFailure.GROUNDING_UNAVAILABLE -> R.string.alerts_live_search_unavailable
            LiveFailure.NOT_FOUND -> R.string.alerts_live_not_found
            else -> R.string.alerts_live_failed
        },
    )
}

private const val SKELETON_LINES = 4

@Preview(name = "Live check · result")
@Composable
private fun LiveCheckPreview() {
    TravelTheme(themeMode = ThemeMode.LIGHT) {
        LiveCheckContent(
            LiveCheckUi(
                "price-watch-1",
                loading = false,
                result =
                    LiveCheckResult(
                        summary = "Rooms from €412 for 3 nights on Booking.com.",
                        quotes = listOf(LiveQuote("Booking.com, 3 nights", 412.0, "EUR", 0)),
                        sources = listOf(GroundingSource("Booking.com", "https://www.booking.com/")),
                        searchedFor = listOf("memmo alfama lisbon price"),
                    ),
            ),
            "Memmo Alfama",
            {},
            {},
            {},
        )
    }
}
