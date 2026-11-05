package io.github.rupakc.travelplanner.feature.results.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.search.StreamPhase
import io.github.rupakc.travelplanner.core.model.search.TravelSearchRequest
import io.github.rupakc.travelplanner.core.model.section.ConfidenceData
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus
import io.github.rupakc.travelplanner.core.ui.SectionStatusIndicator
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.core.ui.titleRes
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.results.R
import io.github.rupakc.travelplanner.feature.results.ResultsUiState
import io.github.rupakc.travelplanner.feature.results.SectionUi
import io.github.rupakc.travelplanner.feature.results.sections.commerce.ChipRow
import io.github.rupakc.travelplanner.feature.results.sections.commerce.Pill
import io.github.rupakc.travelplanner.feature.results.sections.commerce.PillTone
import io.github.rupakc.travelplanner.feature.results.sections.commerce.formatDate
import io.github.rupakc.travelplanner.feature.results.sections.commerce.shortCity

private val SCORE_RING = 52.dp
private val SCORE_BORDER = 2.dp
private const val GREEN_MIN = 71
private const val AMBER_MIN = 41
private const val BORDER_ALPHA = 0.4f

/** The final destination's display name: last multi-city stop, else the destination. */
fun TravelSearchRequest.destinationName(): String = shortCity(destinations?.lastOrNull() ?: destination)

/**
 * Trip summary — the one bold moment of the screen: the destination set large in
 * Sora, the route and dates underneath, and the live progress of the stream.
 */
@Composable
internal fun TripSummary(state: ResultsUiState, modifier: Modifier = Modifier) {
    val request = state.request ?: return
    val total = SectionId.entries.size
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(
            request.destinationName(),
            style = TravelTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = TravelTheme.colors.primary,
            modifier = Modifier.semantics { heading() },
        )
        val stops = request.destinations?.takeIf { it.size > 1 }?.joinToString(" → ") { shortCity(it) }
        Text(
            stringResource(R.string.results_route, shortCity(request.origin), stops ?: request.destinationName()),
            style = TravelTheme.typography.titleMedium,
        )
        val travellers = request.numTravelers.coerceAtLeast(1)
        val locale = currentLocale()
        val dates = listOfNotNull(formatDate(request.departureDate, locale), formatDate(request.returnDate, locale)).joinToString(" – ")
        Text(
            listOfNotNull(
                dates,
                pluralStringResource(R.plurals.results_travellers, travellers, travellers),
                request.budgetUsd?.takeIf { it > 0 }?.let { stringResource(R.string.results_budget_label, localPrice(it)) },
            ).joinToString(" · "),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        if (state.isStreaming) {
            val progress = state.readyCount.toFloat() / total
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.sm),
                color = TravelTheme.colors.primary,
                trackColor = TravelTheme.colors.surfaceVariant,
            )
        }
    }
}

/** Stream status: planning / ready / interrupted / failed / restored. */
@Composable
internal fun StreamBanner(state: ResultsUiState, onRetry: () -> Unit, onRunSaved: () -> Unit, modifier: Modifier = Modifier) {
    val total = SectionId.entries.size
    val live = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    when {
        state.canRunSavedSearch -> {
            Banner(stringResource(R.string.results_saved_no_results), PillTone.Info, modifier) {
                TpButton(stringResource(R.string.results_run_search), onClick = onRunSaved, variant = TpButtonVariant.Tonal)
            }
        }

        state.fromSnapshot -> {
            Banner(stringResource(R.string.results_restored), PillTone.Neutral, modifier) {
                TpButton(stringResource(R.string.results_search_again), onClick = onRetry, variant = TpButtonVariant.Text)
            }
        }

        state.phase == StreamPhase.STREAMING -> {
            val name = state.request?.destinationName().orEmpty()
            val sub =
                if (state.readyCount == 0) {
                    stringResource(R.string.results_planning_sub_start)
                } else {
                    stringResource(R.string.results_planning_sub_progress, state.readyCount, total)
                }
            Banner(stringResource(R.string.results_planning, name), PillTone.Info, modifier.then(live), sub)
        }

        state.phase == StreamPhase.DONE -> {
            Banner(stringResource(R.string.results_ready), PillTone.Positive, modifier.then(live))
        }

        state.phase == StreamPhase.INTERRUPTED || state.phase == StreamPhase.CANCELLED -> {
            Banner(stringResource(R.string.results_interrupted), PillTone.Warning, modifier.then(live)) {
                TpButton(stringResource(R.string.results_retry), onClick = onRetry, variant = TpButtonVariant.Tonal)
            }
        }

        state.phase == StreamPhase.FAILED -> {
            Banner(state.error?.message ?: stringResource(R.string.results_failed), PillTone.Warning, modifier.then(live)) {
                TpButton(stringResource(R.string.results_retry), onClick = onRetry, variant = TpButtonVariant.Tonal)
            }
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun Banner(title: String, tone: PillTone, modifier: Modifier = Modifier, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    val (container, content) =
        when (tone) {
            PillTone.Positive -> TravelTheme.extendedColors.successContainer to TravelTheme.extendedColors.onSuccessContainer
            PillTone.Warning -> TravelTheme.extendedColors.warningContainer to TravelTheme.extendedColors.onWarningContainer
            PillTone.Info -> TravelTheme.extendedColors.infoContainer to TravelTheme.extendedColors.info
            else -> TravelTheme.colors.surfaceVariant to TravelTheme.colors.onSurfaceVariant
        }
    Surface(modifier.fillMaxWidth(), shape = TravelShape.ItemCard, color = container, contentColor = content) {
        Row(Modifier.padding(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = TravelTheme.typography.titleSmall)
                subtitle?.let { Text(it, style = TravelTheme.typography.bodySmall) }
            }
            action?.invoke()
        }
    }
}

/** Travel Ease score (Phase-0 `confidence`): ring, sub-score pills, expandable notes. */
@Composable
internal fun TravelEaseBanner(data: ConfidenceData, modifier: Modifier = Modifier) {
    if (data.error != null || data.total == null) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val grade =
        data.grade ?: if (data.total!! >= GREEN_MIN) {
            "green"
        } else if (data.total!! >= AMBER_MIN) {
            "amber"
        } else {
            "red"
        }
    val tone: Color =
        when (grade) {
            "green" -> TravelTheme.extendedColors.success
            "red" -> TravelTheme.colors.error
            else -> TravelTheme.extendedColors.warning
        }
    val subScores = data.subScores.values.toList()
    Surface(
        modifier.fillMaxWidth(),
        shape = TravelShape.ItemCard,
        border = BorderStroke(1.dp, tone.copy(alpha = BORDER_ALPHA)),
        color = TravelTheme.colors.surface,
    ) {
        Column(Modifier.padding(TravelTheme.spacing.lg), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
                Box(
                    Modifier.size(SCORE_RING).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(Modifier.size(SCORE_RING), shape = CircleShape, border = BorderStroke(SCORE_BORDER, tone), color = TravelTheme.colors.surface) {}
                    Text("${data.total}", style = TravelTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tone)
                }
                Column(
                    Modifier.weight(1f).semantics(mergeDescendants = true) {},
                ) {
                    Text(stringResource(R.string.results_ease_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(
                        stringResource(R.string.results_ease_score_a11y, data.total!!),
                        style = TravelTheme.typography.labelSmall,
                        color = TravelTheme.colors.onSurfaceVariant,
                    )
                    data.destination?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
                }
                val stateText = stringResource(if (expanded) R.string.results_state_expanded else R.string.results_state_collapsed)
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.semantics { stateDescription = stateText },
                ) { Text(stringResource(if (expanded) R.string.results_less else R.string.results_details)) }
            }
            ChipRow {
                subScores.forEach { s -> Pill(stringResource(R.string.results_ease_sub, s.label.orEmpty(), s.score ?: 0, s.max ?: 0)) }
            }
            if (expanded) {
                subScores.forEach { s ->
                    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
                        Text(s.label.orEmpty(), style = TravelTheme.typography.labelMedium)
                        val max = (s.max ?: 0).coerceAtLeast(1)
                        LinearProgressIndicator(progress = { (s.score ?: 0).toFloat() / max }, modifier = Modifier.fillMaxWidth(), color = tone)
                        s.note?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

/** In-results search box ("easy to search and filter"): narrows cards across every section. */
@Composable
internal fun ResultsSearchField(query: String, hidden: Int, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = TravelShape.Input,
            label = { Text(stringResource(R.string.results_search_label)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.results_search_clear)) }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        if (query.isNotBlank() && hidden > 0) {
            Text(
                pluralStringResource(R.plurals.results_search_hidden, hidden, hidden),
                style = TravelTheme.typography.labelSmall,
                color = TravelTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(top = TravelTheme.spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Section quick-jump chips with each section's status (web AgentBadge strip). */
@Composable
internal fun QuickJumpRow(sections: List<SectionUi>, onJump: (SectionId) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        items(sections, key = { it.id.name }, contentType = { "jump" }) { s ->
            val title = stringResource(s.id.titleRes())
            TpChip(
                label = title,
                selected = false,
                onClick = { onJump(s.id) },
                enabled = s.status != SectionStatus.WAITING,
                modifier = Modifier.semantics { contentDescription = title },
            )
        }
    }
}

/** Compact status row used in the expanded (list | detail) layout. */
@Composable
internal fun SectionNavRow(section: SectionUi, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { this.selected = selected },
        shape = TravelShape.ItemCard,
        color = if (selected) TravelTheme.colors.secondaryContainer else TravelTheme.colors.surface,
    ) {
        Row(
            Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(TravelTheme.spacing.accentBar, TravelTheme.spacing.xl).background(TravelTheme.sectionAccents[section.id].bar, TravelShape.Pill))
            Text(
                stringResource(section.id.titleRes()),
                style = TravelTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(start = TravelTheme.spacing.sm),
            )
            if (!section.relevant) Pill(stringResource(R.string.results_section_more), tone = PillTone.Neutral)
            SectionStatusIndicator(section.status)
        }
    }
}
