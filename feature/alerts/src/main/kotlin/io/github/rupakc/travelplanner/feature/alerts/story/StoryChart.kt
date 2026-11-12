package io.github.rupakc.travelplanner.feature.alerts.story

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.ui.format.localPrice
import io.github.rupakc.travelplanner.core.ui.watch.currentLocale
import io.github.rupakc.travelplanner.feature.alerts.R

private val CHART_HEIGHT = 160.dp
private val PAD = 12.dp
private val LINE = 2.dp
private val DOT = 3.dp
private val DOT_SELECTED = 6.dp
private val DASH = 6.dp
private const val BAND_ALPHA = 0.18f
private const val GUIDE_ALPHA = 0.5f

/**
 * Price history of one watch drawn with Canvas (ruling R2): the observed line, the typical band, the target and the
 * lowest price. A touch or drag picks the nearest check; TalkBack steps with custom actions (previous, next, first,
 * latest) and the picked check is announced as the state. With fewer than two checks a count line replaces it.
 */
@Composable
internal fun StoryChart(model: ChartModel?, observed: Int, currency: String?, modifier: Modifier = Modifier) {
    if (model == null) {
        Text(
            if (observed == 0) stringResource(R.string.alerts_story_chart_none) else pluralStringResource(R.plurals.alerts_story_chart_few, observed, observed),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    val size = model.points.size
    var selected by rememberSaveable(size) { mutableIntStateOf(size - 1) }
    val idx = selected.coerceIn(0, size - 1)
    val locale = currentLocale()
    val first = model.points.first()
    val last = model.points.last()
    val label =
        pluralStringResource(R.plurals.alerts_story_chart_label, size, size, formatPointDate(first.at, locale), formatPointDate(last.at, locale))
    val point = model.points[idx]
    val pointText = stringResource(R.string.alerts_story_chart_point, localPrice(point.price, currency), formatPointDate(point.at, locale))
    val aPrev = stringResource(R.string.alerts_story_chart_previous)
    val aNext = stringResource(R.string.alerts_story_chart_next)
    val aFirst = stringResource(R.string.alerts_story_chart_first)
    val aLatest = stringResource(R.string.alerts_story_chart_latest)
    val help = stringResource(R.string.alerts_story_chart_help)
    val line = TravelTheme.colors.primary
    val band = TravelTheme.extendedColors.info
    val guide = TravelTheme.colors.outline
    val lowestColor = TravelTheme.extendedColors.success
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT)
                .pointerInput(size) { detectTapGestures { selected = indexAt(it.x, this.size.width.toFloat(), size) } }
                .pointerInput(size) { detectHorizontalDragGestures { change, _ -> selected = indexAt(change.position.x, this.size.width.toFloat(), size) } }
                .semantics {
                    contentDescription = "$label. $help"
                    stateDescription = pointText
                    customActions =
                        listOf(
                            CustomAccessibilityAction(aPrev) {
                                selected = stepIndex(idx, -1, size)
                                true
                            },
                            CustomAccessibilityAction(aNext) {
                                selected = stepIndex(idx, 1, size)
                                true
                            },
                            CustomAccessibilityAction(aFirst) {
                                selected = 0
                                true
                            },
                            CustomAccessibilityAction(aLatest) {
                                selected = size - 1
                                true
                            },
                        )
                },
        ) {
            val pad = PAD.toPx()
            val w = this.size.width - 2 * pad
            val h = this.size.height - 2 * pad

            fun x(i: Int) = pad + model.xFraction(i) * w

            fun y(v: Double) = pad + (1f - model.fraction(v)) * h
            if (model.typicalLow != null && model.typicalHigh != null) {
                val top = y(model.typicalHigh)
                drawRect(band.copy(alpha = BAND_ALPHA), Offset(pad, top), Size(w, y(model.typicalLow) - top))
            }
            val dash = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx()))
            model.target?.let { drawLine(guide, Offset(pad, y(it)), Offset(pad + w, y(it)), LINE.toPx() / 2, pathEffect = dash) }
            model.lowest?.let { drawLine(lowestColor.copy(alpha = GUIDE_ALPHA), Offset(pad, y(it)), Offset(pad + w, y(it)), LINE.toPx() / 2) }
            val path = Path()
            model.points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.price)) else path.lineTo(x(i), y(p.price)) }
            drawPath(path, line, style = Stroke(width = LINE.toPx()))
            model.points.forEachIndexed { i, p -> drawCircle(line, DOT.toPx(), Offset(x(i), y(p.price))) }
            drawLine(guide, Offset(x(idx), pad), Offset(x(idx), pad + h), LINE.toPx() / 2)
            drawCircle(line, DOT_SELECTED.toPx(), Offset(x(idx), y(point.price)))
        }
        Text(pointText, style = TravelTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            TpButton(aFirst, { selected = 0 }, variant = TpButtonVariant.Text, enabled = idx != 0)
            TpButton(aLatest, { selected = size - 1 }, variant = TpButtonVariant.Text, enabled = idx != size - 1)
        }
    }
}
