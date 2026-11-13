package io.github.rupakc.travelplanner.core.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.rupakc.travelplanner.core.designsystem.component.LocalUiEventSink
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventKind
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.R
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener

object PrivacyLinks {
    const val POLICY_URL = "https://github.com/rupakc/travel-planner/blob/main/docs/Privacy.md"
}

/**
 * Non-modal consent card for the bottom of the screen (web ConsentPrompt). [currentAllowed] null means first run:
 * no close button and no pressed state. When reopened it is the choice in force, shown as `selected`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConsentPrompt(
    currentAllowed: Boolean?,
    onAllow: () -> Unit,
    onDeny: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TravelTheme.spacing
    val title = stringResource(R.string.privacy_prompt_title, stringResource(R.string.brand_name))
    val openLink = rememberLinkOpener()
    Card(
        modifier =
            modifier.semantics {
                paneTitle = title
                liveRegion = LiveRegionMode.Polite
            },
        shape = TravelShape.ItemCard,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    title,
                    style = TravelTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (currentAllowed != null) {
                    TpIconButton(onClick = onClose, tid = "privacy.close") {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.privacy_close))
                    }
                }
            }
            Text(stringResource(R.string.privacy_prompt_body), style = TravelTheme.typography.bodySmall)
            TpButton(
                stringResource(R.string.privacy_policy),
                onClick = { openLink(PrivacyLinks.POLICY_URL) },
                variant = TpButtonVariant.Text,
                tid = "privacy.prompt-policy",
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                TpButton(
                    stringResource(R.string.privacy_allow),
                    onClick = onAllow,
                    variant = if (currentAllowed == true) TpButtonVariant.Tonal else TpButtonVariant.Secondary,
                    modifier = Modifier.semantics { if (currentAllowed != null) selected = currentAllowed },
                    tid = "privacy.allow",
                )
                TpButton(
                    stringResource(R.string.privacy_deny),
                    onClick = onDeny,
                    variant = if (currentAllowed == false) TpButtonVariant.Tonal else TpButtonVariant.Secondary,
                    modifier = Modifier.semantics { if (currentAllowed != null) selected = !currentAllowed },
                    tid = "privacy.deny",
                )
            }
        }
    }
}

@Composable
private fun PreviewPrompt(mode: ThemeMode) {
    TravelTheme(themeMode = mode) { ConsentPrompt(currentAllowed = null, onAllow = {}, onDeny = {}, onClose = {}) }
}

@Preview(name = "Consent – light")
@Composable
private fun ConsentLightPreview() = PreviewPrompt(ThemeMode.LIGHT)

@Preview(name = "Consent – dark")
@Composable
private fun ConsentDarkPreview() = PreviewPrompt(ThemeMode.DARK)
