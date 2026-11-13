package io.github.rupakc.travelplanner.feature.play.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.play.QuizOption
import io.github.rupakc.travelplanner.core.model.play.QuizQuestion
import io.github.rupakc.travelplanner.feature.play.R
import java.util.Locale

/** The quiz session screen. [onHidden] fires when the game is hidden or Play is off; [onBack] leaves (the session resumes later). */
@Composable
fun QuizRoute(onBack: () -> Unit, onHidden: () -> Unit, viewModel: QuizViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.phase) { if (state.phase == QuizPhase.Hidden) onHidden() }
    QuizScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack)
}

/** Stateless quiz screen. */
@Composable
fun QuizScreen(state: QuizUiState, onEvent: (QuizEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, topBar = { TpTopBar(title = stringResource(R.string.play_quiz_title), onBack = onBack) }) { padding ->
        val body = Modifier.padding(padding).fillMaxSize()
        when (val phase = state.phase) {
            QuizPhase.Loading -> {
                SkeletonList(body.padding(TravelTheme.spacing.gutter), lines = 5)
            }

            QuizPhase.Hidden -> {
                Unit
            }

            QuizPhase.AlreadyDone -> {
                EmptyState(
                    title = stringResource(R.string.play_quiz_already_done),
                    modifier = body,
                    actionLabel = stringResource(R.string.play_result_back),
                    onAction = onBack,
                )
            }

            QuizPhase.Expired -> {
                EmptyState(
                    title = stringResource(R.string.play_quiz_expired),
                    modifier = body,
                    actionLabel = stringResource(R.string.play_quiz_start_again),
                    onAction = { onEvent(QuizEvent.Retry) },
                )
            }

            is QuizPhase.Error -> {
                ErrorState(
                    message = phase.message ?: stringResource(R.string.play_quiz_error),
                    modifier = body,
                    onRetry = { onEvent(QuizEvent.Retry) },
                )
            }

            QuizPhase.Running -> {
                QuestionContent(state, onEvent, body)
            }

            QuizPhase.Finished -> {
                QuizSummaryContent(state, onBack, body)
            }
        }
    }
}

@Composable
private fun QuestionContent(state: QuizUiState, onEvent: (QuizEvent) -> Unit, modifier: Modifier) {
    val question = state.current ?: return
    val locale = currentLocale()
    val gap = TravelTheme.spacing.md
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (state.resumed) Text(stringResource(R.string.play_quiz_resumed), style = TravelTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(R.string.play_quiz_progress, state.position + 1, state.questions.size),
                style = TravelTheme.typography.labelLarge,
            )
            // Display only: the server times the answer. Not a live region, so it never chatters at a screen reader.
            Text(
                pluralStringResource(R.plurals.play_quiz_timer, state.secondsLeft, state.secondsLeft),
                style = TravelTheme.typography.labelLarge,
            )
        }
        LinearProgressIndicator(
            progress = { if (state.reveal == null) state.secondsLeft.toFloat() / QUESTION_SECONDS else 0f },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        )
        QuizText.flag(question)?.let { flag ->
            val alt = stringResource(R.string.play_quiz_flag_alt)
            Text(
                flag,
                style = TravelTheme.typography.displayLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = alt },
            )
        }
        PromptText(question, locale)
        question.options.forEach { option -> OptionRow(option, state, locale, onEvent) }
        state.answerError?.let { error ->
            Text(
                error.message ?: stringResource(R.string.play_quiz_answer_failed),
                color = TravelTheme.colors.error,
                style = TravelTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
        state.reveal?.let { reveal -> RevealBar(reveal, state.isLast, onEvent) }
    }
}

@Composable
private fun PromptText(question: QuizQuestion, locale: Locale) {
    val copy = QuizText.prompt(question, locale)
    val text = if (copy.arg != null) stringResource(copy.res, copy.arg) else stringResource(copy.res)
    Text(
        text,
        style = TravelTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
private fun OptionRow(option: QuizOption, state: QuizUiState, locale: Locale, onEvent: (QuizEvent) -> Unit) {
    val reveal = state.reveal
    val isRight = reveal != null && reveal.correctChoice == option.id
    val isWrong = reveal != null && !reveal.correct && reveal.choice == option.id
    val accent =
        when {
            isRight -> TravelTheme.extendedColors.success
            isWrong -> TravelTheme.colors.error
            else -> null
        }
    val mark =
        when {
            isRight -> stringResource(R.string.play_quiz_correct)
            isWrong -> stringResource(R.string.play_quiz_wrong)
            else -> null
        }
    val locked = state.busy || reveal != null
    TpCard(
        modifier = Modifier.fillMaxWidth().semantics { if (mark != null) stateDescription = mark },
        selected = isRight || isWrong,
        accent = accent,
        onClick = if (locked) null else ({ onEvent(QuizEvent.Pick(option.id)) }),
    ) {
        Text(QuizText.optionLabel(option, locale), style = TravelTheme.typography.titleMedium)
        // Shown as text too, so the verdict is never colour-only.
        mark?.let { Text(it, style = TravelTheme.typography.labelMedium, color = accent ?: TravelTheme.colors.onSurface) }
    }
}

@Composable
private fun RevealBar(reveal: Reveal, last: Boolean, onEvent: (QuizEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(
            if (reveal.correct) {
                pluralStringResource(R.plurals.play_quiz_correct_points, reveal.points, reveal.points)
            } else {
                stringResource(R.string.play_quiz_wrong_note)
            },
            style = TravelTheme.typography.titleSmall,
        )
        TpButton(
            text = stringResource(if (last) R.string.play_quiz_finish else R.string.play_quiz_next),
            onClick = { onEvent(QuizEvent.Next) },
            modifier = Modifier.fillMaxWidth(),
            tid = "play.quiz.next",
        )
    }
}

@Composable
private fun QuizSummaryContent(state: QuizUiState, onBack: () -> Unit, modifier: Modifier) {
    val summary = state.summary
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.play_result_title), style = TravelTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (summary != null) {
            val score = summary.score
            val correct = summary.correct
            val total = summary.total ?: state.questions.size
            TpCard(Modifier.fillMaxWidth()) {
                if (correct != null) Text(stringResource(R.string.play_result_correct, correct, total), style = TravelTheme.typography.titleLarge)
                if (score != null) {
                    Text(pluralStringResource(R.plurals.play_history_points, score, score), style = TravelTheme.typography.bodyLarge)
                }
                if (state.gameVisible) GameLines(summary)
            }
        }
        TpButton(
            text = stringResource(R.string.play_result_back),
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            variant = TpButtonVariant.Tonal,
            tid = "play.quiz.back",
        )
    }
}

@Composable
private fun GameLines(summary: io.github.rupakc.travelplanner.core.model.play.QuizSummary) {
    val xp = summary.xp?.xp ?: 0
    if (xp > 0) Text(pluralStringResource(R.plurals.play_result_xp, xp, xp), style = TravelTheme.typography.bodyLarge)
    val streak = summary.streak?.current
    if (streak != null && streak > 0) {
        Text(pluralStringResource(R.plurals.play_result_streak, streak, streak), style = TravelTheme.typography.bodyLarge)
    }
}

@Composable
private fun currentLocale(): Locale = LocalLocale.current.platformLocale
