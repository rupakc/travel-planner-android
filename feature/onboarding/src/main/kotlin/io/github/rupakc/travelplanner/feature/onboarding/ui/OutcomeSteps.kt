package io.github.rupakc.travelplanner.feature.onboarding.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.rememberReduceMotion
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.feature.FeatureId
import io.github.rupakc.travelplanner.core.domain.profile.DefaultFeatureCatalogue
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.ui.profile.descriptionRes
import io.github.rupakc.travelplanner.core.ui.profile.featureIcon
import io.github.rupakc.travelplanner.core.ui.profile.titleRes
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingEvent
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingUiState
import io.github.rupakc.travelplanner.feature.onboarding.R

/** Result: the one memorable moment — the type name set large, with secondaries and "Not you? Change answers". */
@Composable
internal fun ResultStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val classification = state.classification ?: return
    val primary = classification.primary
    val nothingScored = TravellerQuiz.chosen(state.answers).none { it.weights.isNotEmpty() }
    OnboardingFrame(
        modifier = modifier,
        onBack = { onEvent(OnboardingEvent.Back) },
        onClose = onClose.takeIf { state.fromSettings },
        actions = {
            TpButton(stringResource(R.string.onboarding_continue), { onEvent(OnboardingEvent.ConfirmResult) }, Modifier.fillMaxWidth())
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
        ) {
            ResultHero(primary)
            Text(stringResource(primary.descriptionRes()), style = TravelTheme.typography.bodyLarge, color = TravelTheme.colors.onSurfaceVariant)
            if (classification.secondary.isNotEmpty()) {
                Text(
                    stringResource(R.string.onboarding_result_also),
                    style = TravelTheme.typography.titleSmall,
                    color = TravelTheme.colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
                classification.secondary.forEach { SecondaryType(it) }
            }
            if (nothingScored) {
                Text(stringResource(R.string.onboarding_result_skipped), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
            }
            TpButton(
                stringResource(R.string.onboarding_change_answers),
                { onEvent(OnboardingEvent.ChangeAnswers) },
                variant = TpButtonVariant.Text,
            )
        }
    }
}

@Composable
private fun ResultHero(primary: TravellerType) {
    val name = stringResource(primary.titleRes())
    val announcement = stringResource(R.string.onboarding_result_a11y, name)
    val reduceMotion = rememberReduceMotion()
    val visible = remember(primary) { MutableTransitionState(reduceMotion) }.apply { targetState = true }
    Column(
        Modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = announcement
            heading()
            liveRegion = LiveRegionMode.Polite
        },
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        AnimatedVisibility(visibleState = visible, enter = fadeIn() + scaleIn(initialScale = HERO_INITIAL_SCALE)) {
            Box(
                Modifier.size(OnboardingLayout.ResultGlyph).clip(TravelShape.LargeCard).background(TravelTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(primary.glyph(), style = TravelTheme.typography.displayMedium)
            }
        }
        Text(stringResource(R.string.onboarding_result_heading), style = TravelTheme.typography.titleLarge, color = TravelTheme.colors.onSurfaceVariant)
        Text(name, style = TravelTheme.typography.displaySmall, color = TravelTheme.colors.primary)
    }
}

private const val HERO_INITIAL_SCALE = 0.85f

/** Index of the sync-error item (right after the intro). */
private const val SYNC_ERROR_INDEX = 1

@Composable
private fun SecondaryType(type: TravellerType) {
    TpCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            Text(type.glyph(), style = TravelTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
            Column(Modifier.weight(1f)) {
                Text(stringResource(type.titleRes()), style = TravelTheme.typography.titleMedium)
                Text(stringResource(type.descriptionRes()), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
        }
    }
}

/** Feature picker: recommended (pre-checked by type) then the rest; every row is a labelled switch. */
@Composable
internal fun FeaturesStep(
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
    onFinish: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reduceMotion = rememberReduceMotion()
    // The sync error sits at the top of the list; bring it into view (and so announce it) when it appears.
    LaunchedEffect(state.syncError) {
        if (state.syncError != null) {
            if (reduceMotion) listState.scrollToItem(SYNC_ERROR_INDEX) else listState.animateScrollToItem(SYNC_ERROR_INDEX)
        }
    }
    OnboardingFrame(
        modifier = modifier,
        onBack = { onEvent(OnboardingEvent.Back) }.takeUnless { state.saving },
        onClose = onClose.takeIf { state.fromSettings },
        actions = {
            Text(
                pluralStringResource(R.plurals.onboarding_features_count, state.selectedFeatures.size, state.selectedFeatures.size),
                style = TravelTheme.typography.bodyMedium,
                color = TravelTheme.colors.onSurfaceVariant,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TpButton(
                stringResource(
                    when {
                        state.saving -> R.string.onboarding_saving
                        state.fromSettings -> R.string.onboarding_save_changes
                        else -> R.string.onboarding_finish
                    },
                ),
                onFinish,
                loading = state.saving,
            )
        },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item(key = "intro", contentType = "intro") { FeaturesIntro() }
            state.syncError?.let { error -> item(key = "sync", contentType = "sync") { SyncErrorCard(error.message, onEvent) } }
            featureGroup(R.string.onboarding_features_recommended, "recommended", state.recommended, state, onEvent)
            featureGroup(R.string.onboarding_features_more, "more", state.others, state, onEvent)
            item(key = "always", contentType = "note") {
                Text(
                    stringResource(R.string.onboarding_features_always),
                    style = TravelTheme.typography.bodySmall,
                    color = TravelTheme.colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = TravelTheme.spacing.md),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.featureGroup(
    title: Int,
    key: String,
    features: List<FeatureId>,
    state: OnboardingUiState,
    onEvent: (OnboardingEvent) -> Unit,
) {
    if (features.isEmpty()) return
    item(key = "header-$key", contentType = "header") {
        Text(
            stringResource(title),
            style = TravelTheme.typography.titleMedium,
            color = TravelTheme.colors.onBackground,
            modifier = Modifier.padding(top = TravelTheme.spacing.lg, bottom = TravelTheme.spacing.xs).semantics { heading() },
        )
    }
    items(features, key = { it.id }, contentType = { "feature" }) { id ->
        FeatureRow(id, checked = id in state.selectedFeatures, enabled = !state.saving) { onEvent(OnboardingEvent.ToggleFeature(id)) }
    }
}

@Composable
private fun FeaturesIntro() {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(
            stringResource(R.string.onboarding_features_title),
            style = TravelTheme.typography.headlineMedium,
            color = TravelTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.onboarding_features_message), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun FeatureRow(id: FeatureId, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TravelShape.ItemCard)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
            .padding(vertical = TravelTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Box(
            Modifier.size(OnboardingLayout.GlyphTile).clip(TravelShape.Pill).background(TravelTheme.colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(featureIcon(DefaultFeatureCatalogue.descriptor(id).iconName), contentDescription = null, tint = TravelTheme.colors.onPrimaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(id.titleRes()), style = TravelTheme.typography.titleMedium, color = TravelTheme.colors.onSurface)
            Text(stringResource(id.descriptionRes()), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SyncErrorCard(message: String?, onEvent: (OnboardingEvent) -> Unit) {
    TpCard(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, accent = TravelTheme.colors.error, selected = true) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(
                stringResource(R.string.onboarding_sync_failed_title),
                style = TravelTheme.typography.titleSmall,
                color = TravelTheme.colors.error,
                modifier = Modifier.semantics { heading() },
            )
            Text(message ?: stringResource(R.string.onboarding_sync_failed_message), style = TravelTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                TpButton(stringResource(R.string.onboarding_retry), { onEvent(OnboardingEvent.RetrySync) }, variant = TpButtonVariant.Tonal)
                TpButton(stringResource(R.string.onboarding_continue_anyway), { onEvent(OnboardingEvent.ContinueWithoutSync) }, variant = TpButtonVariant.Text)
            }
        }
    }
}

/** In-context POST_NOTIFICATIONS rationale (Android 13+, first run only). */
@Composable
internal fun NotificationsStep(onAllow: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingFrame(
        modifier = modifier,
        actions = {
            TpButton(stringResource(R.string.onboarding_notifications_later), onLater, variant = TpButtonVariant.Text)
            Spacer(Modifier.weight(1f))
            TpButton(stringResource(R.string.onboarding_notifications_allow), onAllow)
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
        ) {
            Box(
                Modifier.size(OnboardingLayout.ResultGlyph).clip(TravelShape.LargeCard).background(TravelTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = TravelTheme.colors.onPrimaryContainer,
                    modifier = Modifier.size(OnboardingLayout.HeroIcon),
                )
            }
            Text(
                stringResource(R.string.onboarding_notifications_title),
                style = TravelTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.onboarding_notifications_message),
                style = TravelTheme.typography.bodyLarge,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
}
