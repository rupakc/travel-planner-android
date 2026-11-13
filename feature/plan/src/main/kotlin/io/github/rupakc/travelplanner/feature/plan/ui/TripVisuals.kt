package io.github.rupakc.travelplanner.feature.plan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.plan.R
import io.github.rupakc.travelplanner.feature.plan.model.TripSummary
import io.github.rupakc.travelplanner.feature.plan.model.TripTiming
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle

/** Width of the date badge (layout constant, defined once). */
private val DateBadgeWidth = 56.dp

/** Icon size inside a fact pill. */
private val PillIconSize = 16.dp

/** Localised medium date ("Apr 1, 2027" / "1 abr 2027"). */
internal fun LocalDate.medium(): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(this)

/** "Apr 1, 2027 → Apr 8, 2027", the start alone, or null without dates. */
@Composable
internal fun tripDates(start: LocalDate?, end: LocalDate?): String? =
    when {
        start == null -> null
        end == null || end == start -> start.medium()
        else -> stringResource(R.string.plan_dates_range, start.medium(), end.medium())
    }

/**
 * The trip's signature: a stacked month / day tile (Sora numerals) that makes dates scannable
 * down the list. Tinted by timing so past trips recede; the timing is also spoken.
 */
@Composable
internal fun DateBadge(date: LocalDate?, timing: TripTiming, modifier: Modifier = Modifier) {
    val past = timing == TripTiming.PAST
    val container = if (past) TravelTheme.colors.surfaceVariant else TravelTheme.colors.primaryContainer
    val content = if (past) TravelTheme.colors.onSurfaceVariant else TravelTheme.colors.onPrimaryContainer
    val label = date?.medium() ?: stringResource(R.string.plan_trip_undated)
    Surface(
        color = container,
        contentColor = content,
        shape = TravelShape.ItemCard,
        modifier = modifier.defaultMinSize(minWidth = DateBadgeWidth).clearAndSetSemantics { contentDescription = label },
    ) {
        Column(
            Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (date == null) {
                Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.padding(TravelTheme.spacing.xs))
            } else {
                Text(
                    date.month.getDisplayName(TextStyle.SHORT, LocalConfiguration.current.locales[0]).replace(".", ""),
                    style = TravelTheme.typography.labelMedium,
                )
                Text(date.dayOfMonth.toString(), style = TravelTheme.typography.headlineSmall)
            }
        }
    }
}

/** Small fact with an icon (dates, travellers, spend). Never colour-only: the text carries the meaning. */
@Composable
internal fun FactPill(icon: ImageVector?, text: String, modifier: Modifier = Modifier) {
    Surface(color = TravelTheme.colors.surfaceVariant, shape = TravelShape.Pill, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(PillIconSize))
            Text(text, style = TravelTheme.typography.labelLarge)
        }
    }
}

/** Timing word for a trip ("Upcoming", "On now", "Past trip", "No dates"). */
@Composable
internal fun timingLabel(timing: TripTiming): String =
    stringResource(
        when (timing) {
            TripTiming.UPCOMING -> R.string.plan_trip_upcoming
            TripTiming.ONGOING -> R.string.plan_trip_ongoing
            TripTiming.PAST -> R.string.plan_trip_past
            TripTiming.UNDATED -> R.string.plan_trip_undated
        },
    )

/** Travellers line, or null when the plan does not say. */
@Composable
internal fun travellersLabel(count: Int?): String? = count?.let { pluralStringResource(R.plurals.plan_travellers, it, it) }

/** Plan name, falling back to a neutral label (never an invented name). */
@Composable
internal fun TripSummary.displayName(): String = name.ifBlank { stringResource(R.string.plan_trip_unnamed) }

/** Section title with heading semantics. */
@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TravelTheme.typography.titleMedium,
        modifier = modifier.semantics { heading() },
    )
}

/** Confirmation dialog; [destructive] colours the confirm button as a destructive action. */
@Composable
internal fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, modifier = Modifier.semantics { heading() }) },
        text = { Text(message) },
        confirmButton = {
            TpButton(
                confirmLabel,
                onConfirm,
                variant = if (destructive) TpButtonVariant.Destructive else TpButtonVariant.Primary,
                tid = "plan.plan_confirm_cancel",
            )
        },
        dismissButton = {
            TpButton(stringResource(R.string.plan_confirm_cancel), onDismiss, variant = TpButtonVariant.Text, tid = "plan.plan_confirm_cancel_2")
        },
    )
}

/** Centered message used as the detail pane placeholder on wide screens. */
@Composable
internal fun PanePlaceholder(text: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(TravelTheme.spacing.xl), contentAlignment = Alignment.Center) {
        Text(text, style = TravelTheme.typography.bodyLarge, color = TravelTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}
