package io.github.rupakc.travelplanner.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme

/** A labelled action for [FeaturePlaceholder]. */
data class PlaceholderAction(val label: String, val onClick: () -> Unit)

/**
 * Scaffold used by the foundation's placeholder screens until each feature team
 * replaces it: top bar, a short message, and navigation actions.
 */
@Composable
fun FeaturePlaceholder(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showTopBar: Boolean = true,
    actions: List<PlaceholderAction> = emptyList(),
) {
    Scaffold(
        modifier = modifier,
        topBar = { if (showTopBar) TpTopBar(title = title, onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            EmptyState(title = title, message = message)
            actions.forEach { TpButton(it.label, it.onClick, variant = TpButtonVariant.Secondary, tid = "ui.feature_placeholder_tp_button") }
        }
    }
}
