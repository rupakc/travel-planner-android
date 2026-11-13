package io.github.rupakc.travelplanner.core.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.tpClickable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.section.SectionId
import io.github.rupakc.travelplanner.core.model.section.SectionStatus

/** Web section labels as string resources. */
@StringRes
fun SectionId.titleRes(): Int =
    when (this) {
        SectionId.FLIGHTS -> R.string.section_flights
        SectionId.WEATHER -> R.string.section_weather
        SectionId.HOTELS -> R.string.section_hotels
        SectionId.ACTIVITIES -> R.string.section_activities
        SectionId.PLACES_TO_SEE -> R.string.section_places_to_see
        SectionId.WALKING_TOURS -> R.string.section_walking_tours
        SectionId.EVENTS -> R.string.section_events
        SectionId.VISA -> R.string.section_visa
        SectionId.SIM -> R.string.section_sim
        SectionId.TIPS -> R.string.section_tips
        SectionId.EMERGENCY_CARD -> R.string.section_emergency_card
        SectionId.GETTING_AROUND -> R.string.section_getting_around
        SectionId.FOREX -> R.string.section_forex
        SectionId.ITINERARY -> R.string.section_itinerary
        SectionId.STRESS_TEST -> R.string.section_stress_test
        SectionId.PACKING_LIST -> R.string.section_packing_list
    }

/**
 * A results section card: accent bar (spec §5 map), icon, title (semantic heading,
 * toggles collapse; expanded/collapsed exposed as state), optional [headerActions]
 * and [badge] (e.g. VerificationChip), status indicator, and [content] when expanded.
 */
@Composable
fun SectionScaffold(
    section: SectionId,
    status: SectionStatus,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    badge: (@Composable () -> Unit)? = null,
    headerActions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val accent = TravelTheme.sectionAccents[section]
    val title = stringResource(section.titleRes())
    val stateText = stringResource(if (expanded) R.string.section_collapse else R.string.section_expand, title)
    val barWidth = TravelTheme.spacing.accentBar
    // The accent bar is drawn, not measured: an IntrinsicSize.Min row would ask the expanded content for
    // intrinsic heights, which lazy lists / BoxWithConstraints inside a section cannot answer (crash).
    Box(
        modifier
            .fillMaxWidth()
            .clip(TravelShape.ItemCard)
            .background(TravelTheme.colors.surface)
            .drawBehind { drawRect(accent.bar, size = Size(barWidth.toPx(), size.height)) }
            .padding(start = barWidth),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TravelTheme.spacing.minTouchTarget)
                    .background(accent.tint)
                    .tpClickable(tid = "ui.section_scaffold_clickable", role = Role.Button, onClickLabel = stateText, onClick = onToggle)
                    .padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.sm)
                    .semantics { stateDescription = stateText },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = accent.icon, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(TravelTheme.spacing.sm))
                }
                Text(
                    title,
                    style = TravelTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                Spacer(Modifier.width(TravelTheme.spacing.sm))
                SectionStatusIndicator(status)
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            // Actions and the badge wrap on their own line under the title: in one row with the weighted title they
            // left it a few dp wide on phones ("Flights" one letter per line). Empty = zero height.
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .background(accent.tint)
                    .padding(horizontal = TravelTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
            ) {
                headerActions()
                badge?.invoke()
            }
            AnimatedVisibility(visible = expanded) {
                Box(Modifier.fillMaxWidth().padding(TravelTheme.spacing.lg)) { content() }
            }
        }
    }
}

@Composable
fun SectionStatusIndicator(status: SectionStatus, modifier: Modifier = Modifier) {
    val label =
        stringResource(
            when (status) {
                SectionStatus.LOADING -> R.string.section_status_loading
                SectionStatus.ENHANCING -> R.string.section_status_enhancing_hint
                SectionStatus.DONE -> R.string.section_status_done
                SectionStatus.WAITING -> R.string.section_status_waiting
            },
        )
    val m = modifier.size(18.dp).semantics { contentDescription = label }
    when (status) {
        SectionStatus.LOADING -> {
            CircularProgressIndicator(m, strokeWidth = 2.dp)
        }

        SectionStatus.ENHANCING -> {
            CircularProgressIndicator(m, strokeWidth = 2.dp, color = TravelTheme.extendedColors.warning)
        }

        SectionStatus.DONE -> {
            Icon(
                Icons.Filled.Check,
                contentDescription = label,
                tint = TravelTheme.colors.primary,
                modifier = modifier.size(18.dp),
            )
        }

        SectionStatus.WAITING -> {
            Box(m.padding(6.dp).clip(TravelShape.Pill).background(TravelTheme.colors.outline))
        }
    }
}
