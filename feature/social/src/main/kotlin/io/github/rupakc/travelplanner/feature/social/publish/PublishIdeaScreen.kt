package io.github.rupakc.travelplanner.feature.social.publish

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.feature.social.R
import io.github.rupakc.travelplanner.feature.social.feed.CAPTION_MAX
import io.github.rupakc.travelplanner.feature.social.feed.PublishProblem
import io.github.rupakc.travelplanner.feature.social.feed.TITLE_MAX
import io.github.rupakc.travelplanner.feature.social.profile.errorText

/** Route: "Share as an idea" for one plan. [onOpenProfile] leads to profile set-up, [onOpenIdea] to the published idea. */
@Composable
internal fun PublishIdeaRoute(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenIdea: (Long) -> Unit,
    viewModel: PublishIdeaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PublishIdeaScreen(state, viewModel::onEvent, onBack, onOpenProfile, onOpenIdea)
}

/** Stateless publish screen: title and caption, guiding messages for refusals, then a result notice (live or held). */
@Composable
internal fun PublishIdeaScreen(
    state: PublishIdeaUiState,
    onEvent: (PublishIdeaEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenIdea: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, topBar = { TpTopBar(title = stringResource(R.string.social_publish_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            val published = state.published
            if (published != null) {
                PublishedNotice(published, onBack, onOpenIdea)
            } else {
                Form(state, onEvent, onOpenProfile)
            }
        }
    }
}

@Composable
private fun Form(state: PublishIdeaUiState, onEvent: (PublishIdeaEvent) -> Unit, onOpenProfile: () -> Unit) {
    Text(stringResource(R.string.social_publish_intro), style = TravelTheme.typography.bodyMedium)
    OutlinedTextField(
        value = state.title,
        onValueChange = { onEvent(PublishIdeaEvent.TitleChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.social_publish_title_label)) },
        supportingText = { Text(stringResource(R.string.social_bio_count, state.title.length, TITLE_MAX)) },
        singleLine = true,
        enabled = !state.publishing,
        isError = state.problem == PublishProblem.TitleInvalid,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    OutlinedTextField(
        value = state.caption,
        onValueChange = { onEvent(PublishIdeaEvent.CaptionChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.social_publish_caption_label)) },
        supportingText = { Text(stringResource(R.string.social_bio_count, state.caption.length, CAPTION_MAX)) },
        minLines = 3,
        maxLines = 6,
        enabled = !state.publishing,
        isError = state.problem == PublishProblem.ContentRejected,
    )
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        state.problem?.let { problem ->
            Text(stringResource(problem.messageRes()), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.error)
            if (problem == PublishProblem.ProfileRequired) {
                TpButton(stringResource(R.string.social_problem_profile_action), onOpenProfile, variant = TpButtonVariant.Tonal, tid = "social.publish.profile")
            }
        }
        state.error?.let { Text(errorText(it), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.error) }
    }
    TpButton(
        stringResource(R.string.social_publish_action),
        { onEvent(PublishIdeaEvent.Publish) },
        Modifier.fillMaxWidth(),
        enabled = state.canPublish,
        loading = state.publishing,
        tid = "social.publish.send",
    )
}

@Composable
private fun PublishedNotice(published: Published, onDone: () -> Unit, onOpenIdea: (Long) -> Unit) {
    Surface(
        color = TravelTheme.colors.secondaryContainer,
        shape = TravelShape.ItemCard,
        modifier =
            Modifier.fillMaxWidth().semantics {
                liveRegion =
                    LiveRegionMode.Polite
            },
    ) {
        Text(
            stringResource(if (published.held) R.string.social_publish_held else R.string.social_publish_done),
            Modifier.padding(TravelTheme.spacing.md),
            style = TravelTheme.typography.bodyLarge,
            color = TravelTheme.colors.onSecondaryContainer,
        )
    }
    TpButton(stringResource(R.string.social_publish_view), { onOpenIdea(published.ideaId) }, Modifier.fillMaxWidth(), tid = "social.publish.view")
    TpButton(stringResource(R.string.social_publish_close), onDone, Modifier.fillMaxWidth(), TpButtonVariant.Secondary, tid = "social.publish.done")
}

private fun PublishProblem.messageRes(): Int =
    when (this) {
        PublishProblem.ProfileRequired -> R.string.social_problem_profile
        PublishProblem.EmailUnverified -> R.string.social_problem_email
        PublishProblem.LimitReached -> R.string.social_problem_limit
        PublishProblem.ContentRejected -> R.string.social_problem_content
        PublishProblem.Restricted -> R.string.social_problem_restricted
        PublishProblem.TitleInvalid -> R.string.social_problem_title
        PublishProblem.TooLarge -> R.string.social_problem_too_large
        PublishProblem.PlanMissing -> R.string.social_problem_plan
    }
