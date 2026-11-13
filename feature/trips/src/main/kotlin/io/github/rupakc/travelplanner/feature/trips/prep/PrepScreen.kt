package io.github.rupakc.travelplanner.feature.trips.prep

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.component.TpCheckbox
import io.github.rupakc.travelplanner.core.designsystem.component.tpClickable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.journey.PrepRules
import io.github.rupakc.travelplanner.core.model.trips.PrepItem
import io.github.rupakc.travelplanner.core.model.trips.PrepSummary
import io.github.rupakc.travelplanner.core.ui.journey.ProgressRing
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.feature.trips.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** The Prep tab of a trip (the trip id comes from the navigation arguments). The visa site opens in a Custom Tab. */
@Composable
fun PrepRoute(modifier: Modifier = Modifier, viewModel: PrepViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val open = rememberLinkOpener()
    PrepScreen(state, viewModel::onEvent, open, modifier)
}

/** Stateless prep checklist: ring bar, items in served order, a collapsed "Not needed" group. */
@Composable
fun PrepScreen(state: PrepUiState, onEvent: (PrepEvent) -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier = Modifier) {
    when {
        state.loading -> {
            SkeletonList(modifier.padding(TravelTheme.spacing.gutter), message = stringResource(R.string.prep_loading))
        }

        state.failed -> {
            ErrorState(
                message = state.errorMessage,
                modifier = modifier,
                title = stringResource(R.string.prep_error),
                onRetry = { onEvent(PrepEvent.Retry) },
            )
        }

        else -> {
            Checklist(state, onEvent, onOpenUrl, modifier)
        }
    }
}

@Composable
private fun Checklist(state: PrepUiState, onEvent: (PrepEvent) -> Unit, onOpenUrl: (String) -> Unit, modifier: Modifier) {
    val (active, notNeeded) = PrepRules.partition(state.items)
    var showNotNeeded by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        item(key = "heading", contentType = "heading") {
            Text(stringResource(R.string.prep_title), style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        }
        item(key = "bar", contentType = "bar") { ReadyBar(state.summary) }
        state.notice?.let { notice ->
            item(key = "notice", contentType = "notice") { Notice(notice, onDismiss = { onEvent(PrepEvent.DismissNotice) }) }
        }
        items(active, key = { it.id }, contentType = { "item" }) { item ->
            PrepRow(item, busy = state.busyId == item.id, onToggle = { onEvent(PrepEvent.Toggle(item.id, it)) }, onOpenUrl = onOpenUrl)
        }
        if (notNeeded.isNotEmpty()) {
            item(key = "na-header", contentType = "na-header") {
                Row(
                    Modifier.fillMaxWidth().defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget)
                        .tpClickable(tid = "trips.prep_screen_clickable") { showNotNeeded = !showNotNeeded }
                        .semantics { stateDescription = if (showNotNeeded) "expanded" else "collapsed" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${stringResource(R.string.prep_not_needed)} (${notNeeded.size})",
                        style = TravelTheme.typography.titleSmall,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            if (showNotNeeded) {
                items(notNeeded, key = { "na-${it.id}" }, contentType = { "item" }) { item ->
                    PrepRow(item, busy = false, onToggle = {}, onOpenUrl = onOpenUrl)
                }
            }
        }
    }
}

@Composable
private fun ReadyBar(summary: PrepSummary) {
    val ready = stringResource(R.string.prep_ready, summary.percent)
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item, selected = summary.complete) {
        Row(
            Modifier.padding(TravelTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(summary.percent, "${summary.percent}%", ready)
            Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }) {
                Text(
                    if (summary.complete) stringResource(R.string.prep_go) else ready,
                    style = TravelTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.prep_progress, summary.done, summary.total),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Notice(notice: PrepNotice, onDismiss: () -> Unit) {
    val text =
        when (notice) {
            PrepNotice.ToggleFailed -> stringResource(R.string.prep_toggle_failed)
            PrepNotice.Auto -> stringResource(R.string.prep_auto_note)
            PrepNotice.NotNeeded -> stringResource(R.string.prep_na_message)
            PrepNotice.Unknown -> stringResource(R.string.prep_unknown_message)
            is PrepNotice.Backend -> notice.message
        }
    Surface(color = TravelTheme.colors.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                color = TravelTheme.colors.onErrorContainer,
                style = TravelTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TpButton(stringResource(R.string.trip_cancel), onDismiss, variant = TpButtonVariant.Text, tid = "prep.notice.dismiss")
        }
    }
}

@Composable
private fun PrepRow(item: PrepItem, busy: Boolean, onToggle: (Boolean) -> Unit, onOpenUrl: (String) -> Unit) {
    val done = item.status == PrepRules.STATUS_DONE
    val overdue = PrepRules.dueState(item) == PrepRules.Due.Overdue
    val title = PrepCopy.titles[item.id]?.let { stringResource(it) } ?: item.id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    TpCard(Modifier.fillMaxWidth(), size = TpCardSize.Item, accent = if (overdue) TravelTheme.extendedColors.warning else null) {
        Row(Modifier.padding(TravelTheme.spacing.sm), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.Top) {
            if (PrepRules.canToggle(item)) {
                TpCheckbox(
                    checked = done,
                    onCheckedChange = { onToggle(it) },
                    enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = title },
                    tid = "trips.prep_screen_checkbox",
                )
            } else {
                AutoMark(done)
            }
            Column(Modifier.weight(1f).padding(top = TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xxs)) {
                Text(title, style = TravelTheme.typography.titleSmall)
                PrepCopy.bodies[item.id]?.let {
                    Text(stringResource(it), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
                Facts(item, onOpenUrl)
                if (item.auto) {
                    Text(stringResource(R.string.prep_auto_note), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
                DueLine(item)
            }
        }
    }
}

@Composable
private fun AutoMark(done: Boolean) {
    Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (done) TravelTheme.colors.primary else TravelTheme.colors.surface,
        border = if (done) null else androidx.compose.foundation.BorderStroke(1.dp, TravelTheme.colors.outline),
        modifier = Modifier.padding(TravelTheme.spacing.sm).size(24.dp).clearAndSetSemantics {},
    ) {
        if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = TravelTheme.colors.onPrimary, modifier = Modifier.padding(4.dp))
    }
}

@Composable
private fun DueLine(item: PrepItem) {
    val due = item.due?.let(::shortDate) ?: return
    when (PrepRules.dueState(item)) {
        PrepRules.Due.None -> {
            Unit
        }

        PrepRules.Due.Upcoming -> {
            Text(stringResource(R.string.prep_due, due), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
        }

        PrepRules.Due.Overdue -> {
            Text(
                "${stringResource(R.string.prep_overdue)} · ${stringResource(R.string.prep_due, due)}",
                style = TravelTheme.typography.labelMedium,
                color = TravelTheme.extendedColors.warning,
            )
        }
    }
}

/** Per-item facts (Trips-API "Prep checklist"): visa type with the apply-by date and official site, bookings, SIM plan, currency, packing count. */
@Composable
private fun Facts(item: PrepItem, onOpenUrl: (String) -> Unit) {
    val lines = factLines(item)
    val url = if (item.id == "visa") PrepRules.officialUrl(item.facts) else null
    lines.forEach { Text(it, style = TravelTheme.typography.bodySmall) }
    if (url != null) TpButton(stringResource(R.string.prep_fact_official), { onOpenUrl(url) }, variant = TpButtonVariant.Text, tid = "prep.visa.official")
}

@Composable
private fun factLines(item: PrepItem): List<String> {
    val f = item.facts
    return when (item.id) {
        "visa" -> {
            visaLines(item)
        }

        "bookings" -> {
            if (f.containsKey("flight")) listOf(bookingsLine(f)) else emptyList()
        }

        "stress_test" -> {
            listOfNotNull(f.int("score")?.let { stringResource(R.string.prep_fact_score, it) })
        }

        "sim" -> {
            listOfNotNull(f.text("plan_name")?.let { stringResource(R.string.prep_fact_sim, it) })
        }

        "money" -> {
            listOfNotNull(f.text("currency")?.let { stringResource(R.string.prep_fact_money, it) })
        }

        "packing" -> {
            listOfNotNull(f.int("items")?.let { stringResource(R.string.prep_fact_packing, it) })
        }

        "offline_pack" -> {
            listOf(
                f.text("saved_at")?.let { stringResource(R.string.prep_fact_saved, shortDate(it.take(ISO_DAY))) }
                    ?: stringResource(R.string.prep_fact_not_saved),
            )
        }

        else -> {
            emptyList()
        }
    }
}

@Composable
private fun visaLines(item: PrepItem): List<String> {
    val f = item.facts
    return listOfNotNull(
        f.text("visa_type")?.let { type -> item.due?.let { stringResource(R.string.prep_fact_visa, type, shortDate(it)) } ?: type },
        f.int("max_stay_days")?.let { stringResource(R.string.prep_fact_stay, it) },
        f.text("processing_time")?.let { stringResource(R.string.prep_fact_processing, it) },
    )
}

@Composable
private fun bookingsLine(f: JsonObject): String {
    val yes = stringResource(R.string.prep_fact_yes)
    val no = stringResource(R.string.prep_fact_no)
    return stringResource(R.string.prep_fact_bookings, if (f.bool("flight")) yes else no, if (f.bool("hotel")) yes else no)
}

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull

private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.contentOrNull == "true"

private fun shortDate(iso: String): String =
    try {
        LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    } catch (_: DateTimeParseException) {
        iso
    }

private const val ISO_DAY = 10
