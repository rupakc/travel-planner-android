package io.github.rupakc.travelplanner.core.ui.carbon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.component.tpClickable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.carbon.CarbonView
import io.github.rupakc.travelplanner.core.model.carbon.FlightEmissions
import io.github.rupakc.travelplanner.core.ui.R
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private const val TIM_URL = "https://travelimpactmodel.org"
private const val BORDER_ALPHA = 0.35f

/** TIM class keys in the order shown, with their labels. */
private val CLASSES =
    listOf(
        "economy" to R.string.carbon_emissions_class_economy,
        "premiumEconomy" to R.string.carbon_emissions_class_premium_economy,
        "business" to R.string.carbon_emissions_class_business,
        "first" to R.string.carbon_emissions_class_first,
    )

/**
 * "≈ N kg CO₂e" for one flight (web `EmissionsChip`, Google's Travel Impact Model). A tap shows the per-passenger
 * breakdown by class and the CC BY-SA credit, linked to TIM (the web's tooltip). Renders nothing without a figure.
 */
@Composable
fun EmissionsChip(emissions: FlightEmissions?, modifier: Modifier = Modifier) {
    val kg = emissions?.kgPerPassenger?.takeIf { it.isFinite() } ?: return
    val locale = currentCarbonLocale()
    var open by rememberSaveable { mutableStateOf(false) }
    val chip = stringResource(R.string.carbon_emissions_chip, CarbonFormat.number(kg, locale, 0))
    val perPassenger = stringResource(R.string.carbon_emissions_per_passenger)
    val lines =
        CLASSES.mapNotNull { (key, res) ->
            val v = (emissions.byClass?.get(key) as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: return@mapNotNull null
            stringResource(R.string.carbon_emissions_class_line, stringResource(res), CarbonFormat.number(v, locale, 0))
        }
    val attribution = stringResource(R.string.carbon_emissions_attribution)
    val stateText = stringResource(if (open) R.string.watch_expanded else R.string.watch_collapsed)
    val x = TravelTheme.extendedColors
    Column(modifier) {
        Surface(
            shape = TravelShape.Pill,
            color = x.successContainer,
            contentColor = x.onSuccessContainer,
            border = BorderStroke(1.dp, x.success.copy(alpha = BORDER_ALPHA)),
            modifier =
                Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("flight_emissions")
                    .tpClickable(tid = "results.flight.emissions", role = Role.Button) { open = !open }
                    .semantics {
                        contentDescription = (listOf(chip, perPassenger) + lines + attribution).joinToString(". ")
                        stateDescription = stateText
                    },
        ) {
            Text(
                chip,
                style = TravelTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = TravelTheme.spacing.sm, vertical = TravelTheme.spacing.xxs),
            )
        }
        if (open) EmissionsDetails(perPassenger, lines, attribution, CarbonView.safeUrl(emissions.attributionUrl) ?: TIM_URL)
    }
}

@Composable
private fun EmissionsDetails(perPassenger: String, lines: List<String>, attribution: String, url: String) {
    val openLink = rememberLinkOpener()
    val openA11y = stringResource(R.string.carbon_emissions_open_a11y)
    Column(Modifier.padding(top = TravelTheme.spacing.xs).testTag("flight_emissions_details")) {
        Text(perPassenger, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        lines.forEach { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
        TpTextButton(
            onClick = { openLink(url) },
            tid = "results.flight.emissions_source",
            modifier = Modifier.semantics { contentDescription = "$attribution. $openA11y" },
        ) { Text(attribution, style = TravelTheme.typography.labelSmall) }
    }
}
