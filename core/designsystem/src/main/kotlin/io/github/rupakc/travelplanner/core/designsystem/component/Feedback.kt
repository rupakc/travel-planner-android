package io.github.rupakc.travelplanner.core.designsystem.component

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.R
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme

/** True when the user turned animations off (Developer options / Accessibility "Remove animations"). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/** Shimmer placeholder (web shimmer skeleton #f1f5f9/#e2e8f0); static when reduce-motion is on. */
fun Modifier.shimmer(): Modifier =
    composed {
        val base = TravelTheme.extendedColors.skeletonBase
        val highlight = TravelTheme.extendedColors.skeletonHighlight
        if (rememberReduceMotion()) {
            background(base)
        } else {
            val progress =
                rememberInfiniteTransition(label = "shimmer").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(SHIMMER_MILLIS, easing = LinearEasing), RepeatMode.Restart),
                    label = "shimmerProgress",
                ).value
            val x = SHIMMER_WIDTH * (progress * 2 - 1)
            background(Brush.linearGradient(listOf(base, highlight, base), start = Offset(x, 0f), end = Offset(x + SHIMMER_WIDTH, 0f)))
        }
    }

private const val SHIMMER_MILLIS = 1200
private const val SHIMMER_WIDTH = 600f

/** One skeleton bar. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 14.dp) {
    Box(modifier.height(height).clip(TravelShape.Input).shimmer())
}

/** Section skeleton: optional rotating [message] + 3 shimmer bars (web loading state). Announces "Loading" once. */
@Composable
fun SkeletonList(modifier: Modifier = Modifier, message: String? = null, lines: Int = 3) {
    val loading = stringResource(R.string.ds_loading)
    Column(
        modifier.semantics { contentDescription = message ?: loading },
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        if (message != null) Text(message, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        repeat(lines) { i -> SkeletonBlock(Modifier.fillMaxWidth(if (i == lines - 1) LAST_LINE_FRACTION else 1f)) }
    }
}

private const val LAST_LINE_FRACTION = 0.6f

/** Centered spinner with an accessible label. */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, label: String = stringResource(R.string.ds_loading)) {
    Box(modifier.fillMaxWidth().padding(TravelTheme.spacing.xl), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = label })
    }
}

/** Empty state: icon, heading, message, optional action. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector = Icons.Filled.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    StateLayout(modifier, icon, TravelTheme.colors.onSurfaceVariant, title, message, actionLabel, onAction, TpButtonVariant.Tonal)
}

/**
 * Error state with retry; [message] is user copy (backend detail or a string resource).
 * Announced politely (live region), web parity role=alert.
 */
@Composable
fun ErrorState(
    message: String?,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ds_error_generic),
    onRetry: (() -> Unit)? = null,
) {
    StateLayout(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        Icons.Filled.Warning,
        TravelTheme.colors.error,
        title,
        message,
        stringResource(R.string.ds_retry).takeIf { onRetry != null },
        onRetry,
        TpButtonVariant.Primary,
    )
}

@Composable
private fun StateLayout(
    modifier: Modifier,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    actionVariant: TpButtonVariant,
) {
    Column(
        modifier.fillMaxWidth().padding(TravelTheme.spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(40.dp))
        Text(title, style = TravelTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (message != null) {
            Text(
                message,
                style = TravelTheme.typography.bodyMedium,
                color = TravelTheme.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) TpButton(actionLabel, onAction, variant = actionVariant)
    }
}
