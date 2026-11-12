package io.github.rupakc.travelplanner.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.R
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme

/** The You tab (journey on): account, saved plans and settings, which the classic shell reaches from the top bar / My trips. */
@Composable
fun YouScreen(
    onOpenAccount: () -> Unit,
    onOpenSavedPlans: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenShares: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        YouRow(Icons.Filled.AccountCircle, R.string.you_account, onOpenAccount)
        HorizontalDivider()
        YouRow(Icons.AutoMirrored.Filled.List, R.string.you_saved_plans, onOpenSavedPlans)
        HorizontalDivider()
        // Share links 2.0 (`features.sharing`): without the flag there is nothing to manage.
        if (onOpenShares != null) {
            YouRow(Icons.Filled.Share, R.string.you_my_shares, onOpenShares)
            HorizontalDivider()
        }
        YouRow(Icons.Filled.Settings, R.string.nav_settings, onOpenSettings)
    }
}

@Composable
private fun YouRow(icon: ImageVector, label: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(label)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Explore is Search with Chat and Near me one tap away (R4). */
@Composable
fun ExploreEntries(onOpenChat: () -> Unit, onOpenNearby: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = TravelTheme.spacing.md, vertical = TravelTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        AssistChip(
            onClick = onOpenChat,
            label = { Text(stringResource(R.string.explore_chat)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
        )
        AssistChip(
            onClick = onOpenNearby,
            label = { Text(stringResource(R.string.explore_near_me)) },
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
        )
    }
}

/** One coach mark: a tip with "Got it" (this tip) and "Skip tips" (all three). */
@Composable
fun CoachMarkCard(tip: CoachTip, onGotIt: () -> Unit, onSkipAll: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().padding(TravelTheme.spacing.md)) {
        Column(Modifier.padding(TravelTheme.spacing.md), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
            Text(stringResource(tip.title), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(stringResource(tip.body), style = TravelTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkipAll) { Text(stringResource(R.string.coach_skip)) }
                TextButton(onClick = onGotIt) { Text(stringResource(R.string.coach_got_it)) }
            }
        }
    }
}
