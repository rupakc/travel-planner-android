package io.github.rupakc.travelplanner.feature.play.hub

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.play.Challenge
import io.github.rupakc.travelplanner.core.model.play.DailyState
import io.github.rupakc.travelplanner.core.model.play.HistoryEntry
import io.github.rupakc.travelplanner.core.model.play.PackInfo
import io.github.rupakc.travelplanner.core.model.play.QuizMode
import io.github.rupakc.travelplanner.core.model.play.QuizPack
import io.github.rupakc.travelplanner.feature.play.PlayMapper
import io.github.rupakc.travelplanner.feature.play.R
import io.github.rupakc.travelplanner.feature.play.quiz.QuizText
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Starts a quiz: the Play tab turns it into the quiz destination. [pack] is only set for [QuizMode.PACK]. */
typealias StartQuiz = (mode: QuizMode, pack: QuizPack?) -> Unit

/**
 * The quiz hub rows of the Play tab: daily card, weekly challenge card, packs and history. Nothing is drawn for [PlayHubPhase.Off]
 * and [PlayHubPhase.GameHidden] (the route leaves the tab). T5.5 adds head-to-head and boards after these rows (see `PlayExtensions`).
 */
fun LazyListScope.playHubItems(state: PlayHubUiState, onEvent: (PlayHubEvent) -> Unit, onStartQuiz: StartQuiz) {
    when (val phase = state.phase) {
        PlayHubPhase.Loading -> {
            item(key = "hub-loading", contentType = "skeleton") { SkeletonList(lines = 3) }
        }

        PlayHubPhase.Off, PlayHubPhase.GameHidden -> {
            Unit
        }

        is PlayHubPhase.Error -> {
            item(key = "hub-error", contentType = "error") { HubError(phase.message, onRetry = { onEvent(PlayHubEvent.Retry) }) }
        }

        PlayHubPhase.Loaded -> {
            item(key = "daily", contentType = "daily") { DailyCard(state.daily, onStart = { onStartQuiz(QuizMode.DAILY, null) }) }
            state.challenge?.let { challenge ->
                item(key = "challenge", contentType = "challenge") { ChallengeCard(challenge, onStart = { onStartQuiz(QuizMode.CHALLENGE, null) }) }
            }
            if (state.packs.isNotEmpty()) {
                item(key = "packs-h", contentType = "heading") { HubHeading(stringResource(R.string.play_packs_title)) }
                items(state.packs, key = { "pack-${it.id}" }, contentType = { "pack" }) { pack ->
                    PackCard(pack, onStart = { QuizPack.from(pack.id)?.let { onStartQuiz(QuizMode.PACK, it) } })
                }
            }
            item(key = "history-h", contentType = "heading") { HubHeading(stringResource(R.string.play_history_title)) }
            if (state.history.isEmpty()) {
                item(key = "history-empty", contentType = "empty") {
                    Text(stringResource(R.string.play_history_empty), style = TravelTheme.typography.bodyMedium)
                }
            } else {
                items(state.history, key = { "history-${it.sessionId}" }, contentType = { "history" }) { HistoryRow(it) }
            }
        }
    }
}

@Composable
private fun HubHeading(text: String) {
    Text(text, style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun HubError(message: String?, onRetry: () -> Unit) {
    TpCard(Modifier.fillMaxWidth()) {
        Text(message ?: stringResource(R.string.play_hub_error), style = TravelTheme.typography.bodyMedium)
        TpButton(stringResource(R.string.play_retry), onRetry, variant = TpButtonVariant.Tonal, tid = "play.hub.retry")
    }
}

@Composable
private fun DailyCard(daily: DailyState, onStart: () -> Unit) {
    TpCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.play_daily_title), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val streak = daily.streak.current ?: 0
        if (streak > 0) Text(pluralStringResource(R.plurals.play_daily_streak, streak, streak), style = TravelTheme.typography.bodyMedium)
        if (daily.streak.freezeAvailable) Text(stringResource(R.string.play_streak_freeze), style = TravelTheme.typography.labelMedium)
        when {
            daily.done -> {
                val score = daily.score ?: 0
                Text(pluralStringResource(R.plurals.play_daily_done, score, score), style = TravelTheme.typography.bodyLarge)
            }

            daily.available -> {
                Text(stringResource(R.string.play_daily_hint), style = TravelTheme.typography.bodyMedium)
                TpButton(stringResource(R.string.play_daily_start), onStart, tid = "play.daily.start")
            }

            else -> {
                Text(stringResource(R.string.play_daily_unavailable), style = TravelTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ChallengeCard(challenge: Challenge, onStart: () -> Unit) {
    TpCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.play_challenge_title), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        val country = QuizText.regionName(challenge.country, null, LocalLocale.current.platformLocale)
        val about =
            if (challenge.kind == "destination" && country != null) {
                stringResource(R.string.play_challenge_destination, country)
            } else {
                stringResource(R.string.play_challenge_featured)
            }
        Text(about, style = TravelTheme.typography.bodyMedium)
        if (challenge.done) {
            Text(stringResource(R.string.play_challenge_done), style = TravelTheme.typography.bodyLarge)
        } else {
            TpButton(stringResource(R.string.play_challenge_start), onStart, tid = "play.challenge.start")
        }
    }
}

@Composable
private fun PackCard(pack: PackInfo, onStart: () -> Unit) {
    val known = packName(pack.id)
    // A pack id this app does not know (a newer backend) cannot be started: the server would still serve it, but the app has no copy.
    TpCard(Modifier.fillMaxWidth(), onClick = if (pack.pack != null) onStart else null) {
        Text(known?.let { stringResource(it) } ?: words(pack.id), style = TravelTheme.typography.titleSmall)
    }
}

@Composable
private fun HistoryRow(entry: HistoryEntry) {
    val title = modeName(entry)
    val points = entry.score ?: 0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = TravelTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(pluralStringResource(R.plurals.play_history_points, points, points), style = TravelTheme.typography.labelLarge)
    }
    val date = PlayMapper.dateOf(entry.finishedAt)?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it) }
    val correct = entry.correct?.let { stringResource(R.string.play_history_correct, it) }
    listOfNotNull(correct, date).takeIf { it.isNotEmpty() }?.let {
        Text(it.joinToString(" · "), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun modeName(entry: HistoryEntry): String {
    val pack = QuizPack.from(entry.pack)
    return when (QuizMode.from(entry.mode)) {
        QuizMode.PACK -> pack?.let { stringResource(packNameRes(it)) } ?: stringResource(R.string.play_mode_pack)
        QuizMode.DAILY -> stringResource(R.string.play_mode_daily)
        QuizMode.CHALLENGE -> stringResource(R.string.play_mode_challenge)
        QuizMode.H2H -> stringResource(R.string.play_mode_h2h)
        QuizMode.UNKNOWN -> stringResource(R.string.play_quiz_prompt_generic)
    }
}

@StringRes
private fun packNameRes(pack: QuizPack): Int =
    when (pack) {
        QuizPack.FLAGS -> R.string.play_pack_flags
        QuizPack.CAPITALS -> R.string.play_pack_capitals
        QuizPack.LANDMARKS -> R.string.play_pack_landmarks
        QuizPack.CUISINE -> R.string.play_pack_cuisine
    }

@StringRes
private fun packName(id: String): Int? = QuizPack.from(id)?.let(::packNameRes)

private fun words(id: String): String = id.replace('_', ' ').replaceFirstChar { it.uppercase() }
