package io.github.rupakc.travelplanner.feature.results.sections.commerce

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.results.R

/** Semantic tone of a small status pill (never the only signal: pills always carry text). */
enum class PillTone { Neutral, Positive, Warning, Info, Accent }

@Immutable
private data class PillColors(val container: Color, val content: Color, val border: Color)

@Composable
private fun PillTone.colors(): PillColors {
    val c = TravelTheme.colors
    val x = TravelTheme.extendedColors
    return when (this) {
        PillTone.Neutral -> PillColors(c.surfaceVariant, c.onSurfaceVariant, c.outlineVariant)
        PillTone.Positive -> PillColors(x.successContainer, x.onSuccessContainer, x.success.copy(alpha = BORDER_ALPHA))
        PillTone.Warning -> PillColors(x.warningContainer, x.onWarningContainer, x.warningBorder)
        PillTone.Info -> PillColors(x.infoContainer, x.info, x.info.copy(alpha = BORDER_ALPHA))
        PillTone.Accent -> PillColors(c.secondaryContainer, c.onSecondaryContainer, c.secondary.copy(alpha = BORDER_ALPHA))
    }
}

private const val BORDER_ALPHA = 0.35f

/** Small rounded label (web `rounded-full px-2 py-0.5 text-xs`). */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tone: PillTone = PillTone.Neutral, icon: ImageVector? = null) {
    val colors = tone.colors()
    Surface(
        modifier = modifier,
        shape = TravelShape.Pill,
        color = colors.container,
        contentColor = colors.content,
        border = BorderStroke(1.dp, colors.border),
    ) {
        Row(
            Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(PILL_ICON))
            Text(text, style = TravelTheme.typography.labelSmall)
        }
    }
}

private val PILL_ICON = 12.dp

/** "Rome, Italy" → "Rome" (web `cityName`). */
fun shortCity(city: String?): String = city.orEmpty().split(',').first().trim()

/** Multi-city tag on a card. */
@Composable
fun CityChip(city: String?, modifier: Modifier = Modifier) {
    val name = shortCity(city).takeIf(String::isNotEmpty) ?: return
    Pill(name, modifier, PillTone.Accent, Icons.Filled.LocationOn)
}

/** Web `FoundOnlineBadge`: only when the backend set `link_verified: true`. */
@Composable
fun FoundOnlineBadge(linkVerified: Boolean?, modifier: Modifier = Modifier) {
    if (linkVerified != true) return
    Pill(stringResource(R.string.results_found_online), modifier, PillTone.Positive, Icons.Filled.CheckCircle)
}

/** Wrapping row of pills/links with the card rhythm. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Budget planner: "N hidden — show all" dashed row. */
@Composable
fun HiddenByBudgetRow(count: Int, onShowAll: () -> Unit, modifier: Modifier = Modifier) {
    if (count <= 0) return
    OutlinedButton(
        onClick = onShowAll,
        modifier = modifier.fillMaxWidth().heightIn(min = TravelTheme.spacing.minTouchTarget),
        shape = TravelShape.ItemCard,
    ) {
        Text(pluralStringResource(R.plurals.results_budget_hidden, count, count), style = TravelTheme.typography.labelMedium)
    }
}

/** Space between leading content and trailing actions in a card row. */
@Composable
fun RowScope.Grow() = androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
