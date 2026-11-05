package io.github.rupakc.travelplanner.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.onboarding.R

/** Layout constants of the onboarding flow (defined once). */
internal object OnboardingLayout {
    /** Readable column on tablets / unfolded foldables. */
    val ContentMaxWidth = 640.dp

    /** Option grid: one column on phones, two from ~600 dp. */
    val OptionMinWidth = 280.dp
    val GlyphTile = 44.dp
    val ResultGlyph = 96.dp
    val HeroIcon = 56.dp
}

/**
 * Shared frame for every step: top row (back / close), [content] filling the middle (it scrolls itself),
 * and an optional bottom [actions] bar. Width is capped and centred on large screens.
 */
@Composable
internal fun OnboardingFrame(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    top: @Composable ColumnScope.() -> Unit = {},
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(TravelTheme.colors.background).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = OnboardingLayout.ContentMaxWidth).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = TravelTheme.spacing.minTouchTarget).padding(horizontal = TravelTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.onboarding_back))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.onboarding_close))
                    }
                }
            }
            Column(Modifier.padding(horizontal = TravelTheme.spacing.gutter), content = top)
            Column(Modifier.weight(1f).fillMaxWidth(), content = content)
            if (actions != null) {
                HorizontalDivider(color = TravelTheme.colors.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

/** Segmented progress (one segment per question) + "Step n of 6" — a real sequence, so the count is shown. */
@Composable
internal fun StepProgress(step: Int, total: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.onboarding_step, step, total)
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(step.toFloat(), 0f..total.toFloat(), total)
        },
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(TravelTheme.spacing.accentBar)
                        .clip(TravelShape.Pill)
                        .background(if (i < step) TravelTheme.colors.primary else TravelTheme.colors.surfaceVariant),
                )
            }
        }
        Text(label, style = TravelTheme.typography.labelLarge, color = TravelTheme.colors.onSurfaceVariant)
    }
}
