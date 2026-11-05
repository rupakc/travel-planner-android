package io.github.rupakc.travelplanner.feature.search.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.search.SearchFormIssue
import io.github.rupakc.travelplanner.core.domain.search.VisaBadge
import io.github.rupakc.travelplanner.core.domain.search.backendDetail
import io.github.rupakc.travelplanner.core.domain.search.fmtUsd
import io.github.rupakc.travelplanner.core.domain.search.visaBadge
import io.github.rupakc.travelplanner.core.model.search.DiscoveredDestination
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.search.R
import io.github.rupakc.travelplanner.feature.search.components.AirportField
import io.github.rupakc.travelplanner.feature.search.components.BudgetField
import io.github.rupakc.travelplanner.feature.search.components.InterestChips
import io.github.rupakc.travelplanner.feature.search.components.NationalityField
import io.github.rupakc.travelplanner.feature.search.components.SearchLookups
import io.github.rupakc.travelplanner.feature.search.components.TravellersField
import io.github.rupakc.travelplanner.feature.search.components.TripDatesField
import io.github.rupakc.travelplanner.feature.search.components.issueMessage

/** Web Surprise me form (Flying from, dates, nationality, budget, travellers, interests). */
@Composable
internal fun DiscoverForm(state: DiscoverUiState, lookups: SearchLookups, onEvent: (DiscoverEvent) -> Unit, modifier: Modifier = Modifier) {
    val form = state.form
    val update: (io.github.rupakc.travelplanner.core.domain.search.SearchForm) -> Unit = { onEvent(DiscoverEvent.UpdateForm(it)) }
    TpCard(modifier = modifier.fillMaxWidth(), size = TpCardSize.Large) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
            AirportField(
                value = form.origin,
                onValueChange = { update(form.copy(origin = it)) },
                label = stringResource(R.string.search_flying_from),
                lookups = lookups,
                leadingPainter = { Icon(painterResource(R.drawable.search_ic_flight), contentDescription = null) },
            )
            TripDatesField(
                departure = form.departureDate,
                returnDate = form.returnDate,
                today = state.today,
                onChange = { dep, ret -> update(form.copy(departureDate = dep, returnDate = ret)) },
                errorText =
                    issueMessage(
                        state.dateIssues,
                        SearchFormIssue.DEPARTURE_REQUIRED,
                        SearchFormIssue.DEPARTURE_IN_PAST,
                        SearchFormIssue.RETURN_BEFORE_DEPARTURE,
                    ),
            )
            NationalityField(value = form.nationality, onValueChange = { update(form.copy(nationality = it)) }, lookups = lookups)
            BudgetField(
                value = form.budgetUsd,
                onValueChange = { update(form.copy(budgetUsd = it)) },
                label = stringResource(R.string.search_budget_optional),
                placeholder = stringResource(R.string.search_budget_total_placeholder),
            )
            TravellersField(
                counts = form.travellers,
                accessibilityNeeds = form.accessibilityNeeds,
                onCountsChange = { update(form.copy(travellers = it)) },
                onAccessibilityChange = { update(form.copy(accessibilityNeeds = it)) },
            )
            InterestChips(stringResource(R.string.search_interests_discover), form.interests, { update(form.copy(interests = it)) })
            if (!state.canSubmit && !state.loading) {
                Text(
                    stringResource(R.string.search_discover_need_fields),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
            TpButton(
                text = stringResource(if (state.loading) R.string.search_discover_busy else R.string.search_discover_submit),
                onClick = { onEvent(DiscoverEvent.Submit) },
                enabled = state.canSubmit,
                loading = state.loading,
                leadingIcon = Icons.Filled.Search,
                modifier = Modifier.fillMaxWidth().testTag("discover_submit"),
            )
        }
    }
}

/** Below the form: skeleton while loading, error with retry, empty copy, or the picks. */
@Composable
internal fun DiscoverResults(state: DiscoverUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    when {
        state.loading -> {
            SkeletonList(modifier, message = stringResource(R.string.search_discover_busy), lines = 4)
        }

        state.error != null -> {
            ErrorState(backendDetail(state.error) ?: stringResource(R.string.search_discover_error), modifier, onRetry = onRetry)
        }

        state.destinations?.isEmpty() == true -> {
            EmptyState(stringResource(R.string.search_discover_empty), modifier)
        }

        !state.destinations.isNullOrEmpty() -> {
            Text(
                stringResource(R.string.search_discover_picks),
                style = TravelTheme.typography.headlineSmall,
                modifier =
                    modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
        }
    }
}

/** Web DestinationCard: city, country, weather emoji, visa badge, weather, flight time, rough cost estimate, two match reasons. */
@Composable
internal fun DestinationCard(dest: DiscoveredDestination, onPlan: () -> Unit, modifier: Modifier = Modifier) {
    val city = dest.city.orEmpty()
    val planLabel = stringResource(R.string.search_plan_trip_to, listOfNotNull(dest.city, dest.country).joinToString(", "))
    TpCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(city, style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    dest.country?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
                }
                Text(dest.weatherEmoji ?: "🌍", style = TravelTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics {})
            }
            VisaBadgeChip(dest.visaType, dest.visaVerified == true)
            dest.weatherDescription?.let { Text(it, style = TravelTheme.typography.bodySmall) }
            dest.flightDurationLabel?.let { label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                    Icon(painterResource(R.drawable.search_ic_flight), contentDescription = null, tint = TravelTheme.colors.onSurfaceVariant)
                    Text(label, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
            dest.estimatedCostUsdLow?.let { low ->
                val high = dest.estimatedCostUsdHigh
                val range =
                    if (high !=
                        null
                    ) {
                        stringResource(R.string.search_cost_range, fmtUsd(low, currentLocale()), fmtUsd(high, currentLocale()))
                    } else {
                        "~${fmtUsd(low, currentLocale())}"
                    }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                    Text(range, style = TravelTheme.typography.titleSmall, color = TravelTheme.colors.primary, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.search_rough_estimate), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
            dest.matchReasons.take(2).forEach { reason ->
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = TravelTheme.colors.primary)
                    Text(reason, style = TravelTheme.typography.bodySmall)
                }
            }
            TpButton(
                text = stringResource(R.string.search_plan_this_trip),
                onClick = onPlan,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = planLabel },
            )
        }
    }
}

@Composable
private fun VisaBadgeChip(visaType: String?, verified: Boolean) {
    val ext = TravelTheme.extendedColors
    val badge = visaBadge(visaType)
    val (container, content) =
        when (badge) {
            VisaBadge.VISA_FREE -> ext.successContainer to ext.onSuccessContainer
            VisaBadge.ON_ARRIVAL, VisaBadge.E_VISA -> ext.infoContainer to ext.info
            VisaBadge.REQUIRED, VisaBadge.VERIFY -> ext.warningContainer to ext.onWarningContainer
        }
    val label =
        stringResource(
            when (badge) {
                VisaBadge.VISA_FREE -> R.string.search_visa_free
                VisaBadge.ON_ARRIVAL -> R.string.search_visa_on_arrival
                VisaBadge.E_VISA -> R.string.search_visa_e_visa
                VisaBadge.REQUIRED -> R.string.search_visa_required
                VisaBadge.VERIFY -> R.string.search_visa_verify
            },
        )
    val unverified = stringResource(R.string.search_visa_unverified)
    Surface(shape = TravelShape.Pill, color = container, contentColor = content) {
        Text(
            if (verified) label else "⚠️ $label",
            style = TravelTheme.typography.labelMedium,
            modifier =
                Modifier
                    .padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs)
                    .semantics { if (!verified) contentDescription = "$label. $unverified" },
        )
    }
}
