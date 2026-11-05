package io.github.rupakc.travelplanner.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme

enum class TpCardSize { Item, Large }

/**
 * Card: [TpCardSize.Item] = 12 dp result item (flat, outlined), [TpCardSize.Large] = 24 dp form/summary card.
 * [selected] draws the web selected state (accent border + tint) using [accent] (defaults to primary).
 */
@Composable
fun TpCard(
    modifier: Modifier = Modifier,
    size: TpCardSize = TpCardSize.Item,
    selected: Boolean = false,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (size == TpCardSize.Large) TravelShape.LargeCard else TravelShape.ItemCard
    val highlight = accent ?: TravelTheme.colors.primary
    val border = if (selected) BorderStroke(2.dp, highlight) else BorderStroke(1.dp, TravelTheme.colors.outlineVariant)
    val container = if (selected) highlight.copy(alpha = SELECTED_TINT) else TravelTheme.colors.surface
    val colors = CardDefaults.outlinedCardColors(containerColor = container)
    val body: @Composable ColumnScope.() -> Unit = { Column(Modifier.padding(TravelTheme.spacing.lg), content = content) }
    if (onClick != null) {
        OutlinedCard(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border, content = body)
    } else if (size == TpCardSize.Large) {
        Card(modifier = modifier, shape = shape, colors = CardDefaults.cardColors(containerColor = container), content = body)
    } else {
        OutlinedCard(modifier = modifier, shape = shape, colors = colors, border = border, content = body)
    }
}

private const val SELECTED_TINT = 0.08f
