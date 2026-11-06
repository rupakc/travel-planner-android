package io.github.rupakc.travelplanner.feature.search.known

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.formatTripDates
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.search.R
import io.github.rupakc.travelplanner.feature.search.RecentNotice
import io.github.rupakc.travelplanner.feature.search.RecentSearchCard
import io.github.rupakc.travelplanner.feature.search.components.SearchDimens

private const val MAX_INTEREST_CHIPS = 3

/** Web RecentSearches: collapsible "Recent searches (n)" with a horizontal strip of cards. Hidden when empty. */
@Composable
internal fun RecentSearchesSection(
    cards: List<RecentSearchCard>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLoad: (String) -> Unit,
    onRerun: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    val stateText = stringResource(if (expanded) R.string.search_expanded else R.string.search_collapsed)
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics {
                    heading()
                    stateDescription = stateText
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        ) {
            Icon(painterResource(R.drawable.search_ic_history), contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant)
            Text(stringResource(R.string.search_recent_title), style = TravelTheme.typography.titleSmall)
            Badge(containerColor = TravelTheme.colors.surfaceVariant, contentColor = TravelTheme.colors.onSurfaceVariant) { Text(cards.size.toString()) }
            Spacer(Modifier.weight(1f))
            Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
        if (expanded) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
                contentPadding = PaddingValues(vertical = TravelTheme.spacing.sm),
                modifier = Modifier.testTag("recent_list"),
            ) {
                items(cards, key = { it.id }, contentType = { "recent" }) { card ->
                    RecentCard(card, onLoad, onRerun, onRemove, Modifier.animateItem())
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecentCard(card: RecentSearchCard, onLoad: (String) -> Unit, onRerun: (String) -> Unit, onRemove: (String) -> Unit, modifier: Modifier) {
    val summary = card.summary
    val passedHint = stringResource(R.string.search_recent_again_passed)
    TpCard(modifier = modifier.width(SearchDimens.RecentCardWidth)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.search_recent_load, summary.route), role = Role.Button) { onLoad(card.id) },
            ) {
                Text(summary.route, style = TravelTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row {
                    Text(
                        formatTripDates(card.entry.search.departureDate, card.entry.search.returnDate, currentLocale()),
                        style = TravelTheme.typography.bodySmall,
                        color = TravelTheme.colors.onSurfaceVariant,
                    )
                    if (card.datesPassed) {
                        Text(
                            " " + stringResource(R.string.search_recent_dates_passed),
                            style = TravelTheme.typography.bodySmall,
                            color = TravelTheme.extendedColors.warning,
                        )
                    }
                }
                // Interests wrap on their own lines; the traveller count gets its own row so a long chip list
                // can never squeeze it into a one-letter-wide column.
                FlowRow(
                    Modifier.padding(top = TravelTheme.spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                ) {
                    summary.interests.take(MAX_INTEREST_CHIPS).forEach { interest ->
                        Surface(shape = TravelShape.Input, color = TravelTheme.colors.primaryContainer, contentColor = TravelTheme.colors.onPrimaryContainer) {
                            Text(
                                interest.replaceFirstChar(Char::titlecase),
                                style = TravelTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = TravelTheme.spacing.xs, vertical = TravelTheme.spacing.xxs),
                            )
                        }
                    }
                    val more = summary.interests.size - MAX_INTEREST_CHIPS
                    if (more > 0) {
                        Text(pluralStringResource(R.plurals.search_more_interests, more, more), style = TravelTheme.typography.labelSmall)
                    }
                }
                Row(
                    Modifier.padding(top = TravelTheme.spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Person, contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant)
                    Text(
                        pluralStringResource(R.plurals.search_traveler_count, summary.travellers, summary.travellers),
                        style = TravelTheme.typography.labelSmall,
                        color = TravelTheme.colors.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = { onRemove(card.id) }) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_recent_remove, summary.route))
            }
        }
        TpButton(
            text = stringResource(R.string.search_recent_again),
            onClick = { onRerun(card.id) },
            enabled = !card.datesPassed,
            modifier = Modifier.semantics { if (card.datesPassed) stateDescription = passedHint },
            variant = TpButtonVariant.Text,
            leadingIcon = Icons.Filled.Refresh,
        )
    }
}

/** Web teal notice after a recent search fills the form. */
@Composable
internal fun RecentNoticeBanner(notice: RecentNotice, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = TravelShape.Input,
        color = TravelTheme.colors.primaryContainer,
        contentColor = TravelTheme.colors.onPrimaryContainer,
    ) {
        Row(Modifier.padding(start = TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(if (notice.datesShifted) R.string.search_recent_loaded_shifted else R.string.search_recent_loaded, notice.route),
                style = TravelTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = TravelTheme.spacing.sm).semantics { liveRegion = LiveRegionMode.Polite },
            )
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_dismiss)) }
        }
    }
}
