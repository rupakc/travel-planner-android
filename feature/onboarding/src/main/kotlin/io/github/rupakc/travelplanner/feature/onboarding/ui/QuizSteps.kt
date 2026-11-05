package io.github.rupakc.travelplanner.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.profile.QuizQuestion
import io.github.rupakc.travelplanner.core.domain.profile.TravellerQuiz
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingEvent
import io.github.rupakc.travelplanner.feature.onboarding.OnboardingUiState
import io.github.rupakc.travelplanner.feature.onboarding.R

/** Welcome: what the quiz is, the six topics in order, Get started. */
@Composable
internal fun WelcomeStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    OnboardingFrame(
        modifier = modifier,
        onClose = onClose.takeIf { state.fromSettings },
        actions = {
            TpButton(stringResource(R.string.onboarding_get_started), { onEvent(OnboardingEvent.Start) }, Modifier.fillMaxWidth())
        },
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg),
        ) {
            Text(
                stringResource(if (state.fromSettings) R.string.onboarding_retake_title else R.string.onboarding_title),
                style = TravelTheme.typography.displaySmall,
                color = TravelTheme.colors.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(if (state.fromSettings) R.string.onboarding_retake_message else R.string.onboarding_message),
                style = TravelTheme.typography.bodyLarge,
                color = TravelTheme.colors.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                TravellerQuiz.questions.forEachIndexed { i, q -> TopicRow(number = i + 1, title = stringResource(questionTitle(q.id))) }
            }
            Text(
                stringResource(R.string.onboarding_welcome_note),
                style = TravelTheme.typography.bodyMedium,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TopicRow(number: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
        Box(
            Modifier.size(OnboardingLayout.GlyphTile).clip(TravelShape.Pill).background(TravelTheme.colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                style = TravelTheme.typography.titleMedium,
                color = TravelTheme.colors.onPrimaryContainer,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
        Text(title, style = TravelTheme.typography.bodyLarge, color = TravelTheme.colors.onBackground, modifier = Modifier.weight(1f))
    }
}

/** One question per screen: progress, heading, option cards (radio or checkbox semantics), Skip / Next. */
@Composable
internal fun QuestionStep(state: OnboardingUiState, onEvent: (OnboardingEvent) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val question = TravellerQuiz.questions[state.questionIndex]
    val answer = state.currentAnswer
    val last = state.questionIndex == state.questionCount - 1
    OnboardingFrame(
        modifier = modifier,
        onBack = { onEvent(OnboardingEvent.Back) },
        onClose = onClose.takeIf { state.fromSettings },
        top = { StepProgress(step = state.questionIndex + 1, total = state.questionCount) },
        actions = {
            TpButton(stringResource(R.string.onboarding_skip), { onEvent(OnboardingEvent.Skip) }, variant = TpButtonVariant.Text)
            Spacer(Modifier.weight(1f))
            TpButton(
                stringResource(if (last) R.string.onboarding_see_result else R.string.onboarding_next),
                { onEvent(OnboardingEvent.Next) },
                enabled = answer.isNotEmpty(),
            )
        },
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(OnboardingLayout.OptionMinWidth),
            contentPadding = PaddingValues(TravelTheme.spacing.gutter),
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
            modifier = Modifier.fillMaxWidth().then(if (question.multiChoice) Modifier else Modifier.selectableGroup()),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { QuestionHeader(question) }
            items(question.options, key = { it.id }, contentType = { "option" }) { option ->
                OptionCard(
                    copy = optionCopy(question.id, option.id),
                    selected = option.id in answer,
                    multiChoice = question.multiChoice,
                    onClick = { onEvent(OnboardingEvent.Select(option.id)) },
                )
            }
        }
    }
}

@Composable
private fun QuestionHeader(question: QuizQuestion) {
    Column(Modifier.padding(bottom = TravelTheme.spacing.sm), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(
            stringResource(questionTitle(question.id)),
            style = TravelTheme.typography.headlineMedium,
            color = TravelTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            stringResource(if (question.multiChoice) R.string.onboarding_choose_many else R.string.onboarding_choose_one),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun OptionCard(copy: OptionCopy, selected: Boolean, multiChoice: Boolean, onClick: () -> Unit) {
    val state = stringResource(if (selected) R.string.onboarding_selected else R.string.onboarding_not_selected)
    val interaction =
        if (multiChoice) {
            Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
        } else {
            Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        }
    TpCard(
        Modifier
            .fillMaxWidth()
            .clip(TravelShape.ItemCard)
            .then(interaction)
            .semantics { stateDescription = state },
        selected = selected,
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = TravelTheme.spacing.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            Box(
                Modifier.size(OnboardingLayout.GlyphTile).clip(TravelShape.Input).background(TravelTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(copy.glyph, style = TravelTheme.typography.titleLarge, modifier = Modifier.clearAndSetSemantics { })
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(copy.label), style = TravelTheme.typography.titleMedium, color = TravelTheme.colors.onSurface)
                copy.hint?.let {
                    Text(stringResource(it), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = TravelTheme.colors.primary)
        }
    }
}
