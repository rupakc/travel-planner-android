package io.github.rupakc.travelplanner.core.ui.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpCardSize
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.journey.PlanCompleteness
import io.github.rupakc.travelplanner.core.domain.journey.PlanCompleteness.PartId
import io.github.rupakc.travelplanner.core.ui.R
import kotlin.math.roundToInt

/**
 * A static progress ring (no animation, so it needs no reduced-motion handling) with [text] in the middle.
 * [description] is what TalkBack reads; the visible [text] is hidden from it.
 */
@Composable
fun ProgressRing(percent: Int, text: String, description: String, modifier: Modifier = Modifier, diameter: androidx.compose.ui.unit.Dp = 56.dp) {
    val track = TravelTheme.colors.outlineVariant
    val arc = TravelTheme.colors.primary
    Box(modifier.size(diameter).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(diameter)) {
            val stroke = Stroke(width = size.minDimension * RING_STROKE, cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val box = Size(size.width - stroke.width, size.height - stroke.width)
            drawArc(track, 0f, FULL, false, Offset(inset, inset), box, style = stroke)
            drawArc(arc, -QUARTER, FULL * percent.coerceIn(0, MAX_PERCENT) / MAX_PERCENT, false, Offset(inset, inset), box, style = stroke)
        }
        Text(text, style = TravelTheme.typography.labelMedium, modifier = Modifier.clearAndSetSemantics {})
    }
}

/**
 * Plan completeness (web `CompletenessMeter`): a ring and the five parts as a checklist; a partly done part shows its
 * share. Stateless; telemetry (`completeness_view`) is sent by the host once per visit.
 */
@Composable
fun CompletenessMeter(result: PlanCompleteness.Result, modifier: Modifier = Modifier) {
    val percentText = stringResource(R.string.completeness_percent, result.percent)
    TpCard(modifier, size = TpCardSize.Item) {
        Row(Modifier.padding(TravelTheme.spacing.md), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalAlignment = Alignment.Top) {
            ProgressRing(result.percent, percentText, percentText)
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                Text(stringResource(R.string.completeness_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                result.parts.forEach { PartRow(it) }
            }
        }
    }
}

@Composable
private fun PartRow(part: PlanCompleteness.Part) {
    val label = stringResource(partLabel(part.id))
    val share = if (!part.done && part.fraction > 0) " ${(part.fraction * MAX_PERCENT).roundToInt()}%" else ""
    Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        if (part.done) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = TravelTheme.colors.primary)
        } else {
            val outline = TravelTheme.colors.onSurfaceVariant
            Canvas(Modifier.size(16.dp)) { drawCircle(outline, radius = size.minDimension / 2 - 2.dp.toPx(), style = Stroke(width = 1.5.dp.toPx())) }
        }
        Text(
            label + share,
            style = TravelTheme.typography.bodySmall,
            color = if (part.done) TravelTheme.colors.onSurface else TravelTheme.colors.onSurfaceVariant,
        )
    }
}

private fun partLabel(id: PartId): Int =
    when (id) {
        PartId.Flights -> R.string.completeness_part_flights
        PartId.Stay -> R.string.completeness_part_stay
        PartId.Days -> R.string.completeness_part_days
        PartId.Visa -> R.string.completeness_part_visa
        PartId.Packing -> R.string.completeness_part_packing
    }

private const val FULL = 360f
private const val QUARTER = 90f
private const val MAX_PERCENT = 100
private const val RING_STROKE = 0.1f
