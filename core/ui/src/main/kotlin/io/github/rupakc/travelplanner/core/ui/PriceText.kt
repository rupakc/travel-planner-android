package io.github.rupakc.travelplanner.core.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.ui.format.localPrice

/**
 * A price from the API, formatted like the web (`formatPrice`). Never invents a
 * number: null → "Price not available" (or nothing when [hideWhenUnknown]).
 * [isEstimate] adds the amber "Estimate" label (spec §3 anti-goal: estimates stay labelled).
 */
@Composable
fun PriceText(
    amount: Double?,
    modifier: Modifier = Modifier,
    currency: String? = "USD",
    isEstimate: Boolean = false,
    cents: Boolean = false,
    style: TextStyle = LocalTextStyle.current,
    hideWhenUnknown: Boolean = false,
) {
    val formatted = localPrice(amount, currency, cents)
    if (formatted.isEmpty()) {
        if (!hideWhenUnknown) {
            Text(stringResource(R.string.price_unknown), modifier, style = style, color = TravelTheme.colors.onSurfaceVariant)
        }
        return
    }
    val estimate = stringResource(R.string.price_estimate)
    val a11y = if (isEstimate) stringResource(R.string.price_estimate_a11y, formatted) else formatted
    Row(modifier.clearAndSetSemantics { contentDescription = a11y }, verticalAlignment = Alignment.CenterVertically) {
        Text(formatted, style = style)
        if (isEstimate) {
            Spacer(Modifier.width(TravelTheme.spacing.xs))
            Text(estimate, style = TravelTheme.typography.labelSmall, color = TravelTheme.extendedColors.warning)
        }
    }
}
