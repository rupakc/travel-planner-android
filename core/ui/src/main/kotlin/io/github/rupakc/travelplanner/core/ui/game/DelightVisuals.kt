package io.github.rupakc.travelplanner.core.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate as rotateScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.rememberReduceMotion
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.game.DealHunterState
import io.github.rupakc.travelplanner.core.domain.game.Delight
import io.github.rupakc.travelplanner.core.ui.R
import io.github.rupakc.travelplanner.core.ui.journey.ProgressRing
import kotlin.math.cos
import kotlin.math.sin

/** Web `DropMoment` / `GameCelebration` confetti: a short radial burst, theme colours. */
private const val PIECES = 14
private const val BURST_MS = 1100
private const val FULL_TURN = 6.2831855f
private const val ROTATION_STEP = 67

/**
 * A one-shot confetti burst filling its parent. Draws nothing under "remove animations" (static end state
 * is simply no confetti) and is invisible to TalkBack.
 */
@Composable
fun ConfettiBurst(modifier: Modifier = Modifier) {
    val reduced = rememberReduceMotion()
    if (!Delight.confetti(reduced, celebrate = true)) return
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(BURST_MS, easing = LinearEasing)) }
    val colors =
        listOf(
            TravelTheme.colors.primary,
            TravelTheme.colors.inversePrimary,
            TravelTheme.colors.outline,
            TravelTheme.colors.error,
            TravelTheme.colors.secondary,
            TravelTheme.colors.tertiary,
        )
    Canvas(modifier.clearAndSetSemantics {}) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val eased = FastOutSlowInEasing.transform(p)
        val origin = Offset(size.width / 2, size.height * 0.4f)
        repeat(PIECES) { i ->
            val angle = i.toFloat() / PIECES * FULL_TURN
            val reach = (70 + (i % 3) * 22).dp.toPx() * eased
            val c = Offset(origin.x + cos(angle) * reach, origin.y + sin(angle) * reach)
            val w = 6.dp.toPx()
            val h = 10.dp.toPx()
            rotateScope(Math.toDegrees(angle.toDouble()).toFloat() + i * ROTATION_STEP * eased, c) {
                drawRect(colors[i % colors.size].copy(alpha = 1f - p), Offset(c.x - w / 2, c.y - h / 2), Size(w, h))
            }
        }
    }
}

/**
 * Web `PassportStamp`: a double-bordered "New country" stamp that thumps in (scale 1.8 to 1, tilt -18 to -8 degrees);
 * static at its end state under "remove animations".
 */
@Composable
fun PassportStamp(code: String, city: String?, modifier: Modifier = Modifier) {
    val reduced = rememberReduceMotion()
    val progress = remember { Animatable(if (Delight.animate(reduced)) 0f else 1f) }
    LaunchedEffect(reduced) { if (Delight.animate(reduced)) progress.animateTo(1f, tween(STAMP_MS, easing = FastOutSlowInEasing)) }
    val p = progress.value
    val ink = TravelTheme.colors.error
    val description = stringResource(R.string.delight_stamp_desc, code)
    Surface(
        modifier =
            modifier
                .graphicsLayer { alpha = p }
                .scale(STAMP_FROM_SCALE - (STAMP_FROM_SCALE - 1f) * p)
                .rotate(STAMP_FROM_TILT + (STAMP_TO_TILT - STAMP_FROM_TILT) * p)
                .semantics { contentDescription = description },
        shape = RoundedCornerShape(6.dp),
        color = Color.Transparent,
        border = BorderStroke(4.dp, ink),
    ) {
        Column(
            Modifier.padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.sm).clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.delight_stamp_title).uppercase(), style = TravelTheme.typography.labelSmall, color = ink, fontWeight = FontWeight.Bold)
            Text(code, style = TravelTheme.typography.headlineSmall, color = ink, fontWeight = FontWeight.ExtraBold)
            city?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.labelSmall, color = ink) }
        }
    }
}

private const val STAMP_MS = 500
private const val STAMP_FROM_SCALE = 1.8f
private const val STAMP_FROM_TILT = -18f
private const val STAMP_TO_TILT = -8f

/** Web `BucketRing`: countries visited against the bucket-list goal. Pass the result of [Delight.bucket] (null renders nothing). */
@Composable
fun BucketRing(bucket: Delight.Bucket?, modifier: Modifier = Modifier) {
    if (bucket == null) return
    val label = stringResource(R.string.delight_bucket_label, bucket.count, bucket.goal)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
        ProgressRing(Math.round(bucket.fraction * PERCENT), "${bucket.count}", label)
        Column {
            Text("${bucket.count} / ${bucket.goal}", style = TravelTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.delight_bucket_title), style = TravelTheme.typography.bodyMedium)
        }
    }
}

/** Web `DealHunterTrack`: the next Deal-hunter badge with a progress bar, or the all-done line; nothing for [DealHunterState.Hidden]. */
@Composable
fun DealHunterTrack(state: DealHunterState, modifier: Modifier = Modifier) {
    when (state) {
        DealHunterState.Hidden -> {
            Unit
        }

        DealHunterState.AllDone -> {
            Text(stringResource(R.string.delight_deal_all_done), modifier, style = TravelTheme.typography.labelLarge)
        }

        is DealHunterState.Next -> {
            val name = badgeNameText(state.badgeId)
            val title = stringResource(R.string.delight_deal_next, name)
            Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.delight_deal_title) + ": " + title,
                        style = TravelTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stringResource(R.string.delight_deal_progress, state.progress, state.target),
                        style = TravelTheme.typography.labelMedium,
                        textAlign = TextAlign.End,
                    )
                }
                LinearProgressIndicator(
                    progress = { state.percent / PERCENT },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .semantics {
                                contentDescription = title
                                progressBarRangeInfo = ProgressBarRangeInfo(state.progress.toFloat(), 0f..state.target.toFloat())
                            },
                )
            }
        }
    }
}

private const val PERCENT = 100f
