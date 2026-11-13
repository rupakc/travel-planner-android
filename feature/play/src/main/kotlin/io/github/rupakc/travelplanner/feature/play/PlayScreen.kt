package io.github.rupakc.travelplanner.feature.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.ui.game.badgeDescText
import io.github.rupakc.travelplanner.core.ui.game.badgeNameText
import io.github.rupakc.travelplanner.core.ui.game.levelNameText
import io.github.rupakc.travelplanner.core.ui.game.ruleNameText
import io.github.rupakc.travelplanner.feature.play.hub.PlayHubEvent
import io.github.rupakc.travelplanner.feature.play.hub.PlayHubPhase
import io.github.rupakc.travelplanner.feature.play.hub.PlayHubUiState
import io.github.rupakc.travelplanner.feature.play.hub.PlayHubViewModel
import io.github.rupakc.travelplanner.feature.play.hub.StartQuiz
import io.github.rupakc.travelplanner.feature.play.hub.playHubItems
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The Play tab. [onHidden] is called when the game is off (404 or `hide_game`) so the shell can leave the screen. */
@Composable
fun PlayRoute(
    onHidden: () -> Unit,
    quizEnabled: Boolean = false,
    onStartQuiz: StartQuiz = { _, _ -> },
    /** Head-to-head and board cards (T5.5), drawn after the quiz hub once it is loaded; gets the open match count. */
    extraItems: LazyListScope.(openH2h: Int) -> Unit = {},
    viewModel: PlayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.phase) { if (state.phase == PlayPhase.Hidden) onHidden() }
    // The quiz hub exists only with `features.play`; without it nothing calls /play/* and the tab is the progress screen alone.
    val hubModel = if (quizEnabled) hiltViewModel<PlayHubViewModel>() else null
    val hub = hubModel?.state?.collectAsStateWithLifecycle()?.value
    if (hubModel != null) {
        LifecycleResumeEffect(hubModel) {
            hubModel.onEvent(PlayHubEvent.Resumed)
            onPauseOrDispose {}
        }
    }
    LaunchedEffect(hub?.phase) { if (hub?.phase == PlayHubPhase.GameHidden) onHidden() }
    PlayScreen(
        state = state,
        onEvent = viewModel::onEvent,
        hub = hub,
        onHubEvent = { hubModel?.onEvent(it) },
        onStartQuiz = onStartQuiz,
        extraItems = { if (hub?.phase == PlayHubPhase.Loaded) extraItems(hub.openH2h) },
    )
}

/** Stateless Play screen. Nothing is drawn while the game is hidden. */
@Composable
fun PlayScreen(
    state: PlayUiState,
    onEvent: (PlayEvent) -> Unit,
    modifier: Modifier = Modifier,
    hub: PlayHubUiState? = null,
    onHubEvent: (PlayHubEvent) -> Unit = {},
    onStartQuiz: StartQuiz = { _, _ -> },
    /** Extension point for T5.5: head-to-head, boards and the challenge detail rows, drawn after the quiz hub. */
    extraItems: LazyListScope.() -> Unit = {},
) {
    Scaffold(modifier = modifier, topBar = { TpTopBar(title = stringResource(R.string.play_title)) }) { padding ->
        when (val phase = state.phase) {
            PlayPhase.Loading -> {
                SkeletonList(Modifier.padding(padding).padding(TravelTheme.spacing.gutter), lines = 5)
            }

            PlayPhase.Hidden -> {
                Unit
            }

            is PlayPhase.Error -> {
                ErrorState(
                    message = phase.message ?: stringResource(R.string.play_error),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    onRetry = { onEvent(PlayEvent.Retry) },
                )
            }

            PlayPhase.Loaded -> {
                PlayContent(state, hub, onHubEvent, onStartQuiz, extraItems, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun PlayContent(
    state: PlayUiState,
    hub: PlayHubUiState?,
    onHubEvent: (PlayHubEvent) -> Unit,
    onStartQuiz: StartQuiz,
    extraItems: LazyListScope.() -> Unit,
    modifier: Modifier,
) {
    val gap = TravelTheme.spacing.md
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (hub != null) {
            playHubItems(hub, onHubEvent, onStartQuiz)
            extraItems()
        }
        item(key = "level", contentType = "level") { LevelCard(state) }
        if (state.streaks.isNotEmpty()) {
            item(key = "streaks-h", contentType = "heading") { Heading(stringResource(R.string.play_streaks)) }
            items(state.streaks, key = { "streak-${it.id}" }, contentType = { "streak" }) { StreakCard(it) }
        }
        item(key = "badges-h", contentType = "heading") { Heading(stringResource(R.string.play_badges)) }
        items(state.badges, key = { "badge-${it.id}" }, contentType = { "badge" }) { BadgeCard(it) }
        item(key = "recent-h", contentType = "heading") { Heading(stringResource(R.string.play_recent)) }
        if (state.recent.isEmpty()) {
            item(key = "recent-empty", contentType = "empty") { Text(stringResource(R.string.play_recent_empty), style = TravelTheme.typography.bodyMedium) }
        } else {
            items(state.recent, key = { "recent-${it.rule}-${it.createdAt}" }, contentType = { "recent" }) { RecentRow(it) }
        }
        if (state.tripsVisible) {
            item(key = "countries-h", contentType = "heading") { Heading(stringResource(R.string.play_countries)) }
            item(key = "goal", contentType = "goal") { GoalCard(state) }
            if (state.countries.isEmpty()) {
                item(key = "countries-empty", contentType = "empty") {
                    EmptyState(title = stringResource(R.string.play_countries_empty))
                }
            } else {
                items(state.countries, key = { "country-${it.code}" }, contentType = { "country" }) { CountryRow(it) }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun LevelCard(state: PlayUiState) {
    TpCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.play_level, state.level), style = TravelTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        levelNameText(state.level)?.let { Text(it, style = TravelTheme.typography.titleSmall) }
        Text(stringResource(R.string.play_xp, state.xp), style = TravelTheme.typography.bodyLarge)
        val label = stringResource(R.string.play_progress_label)
        val description =
            state.remainingXp?.let { stringResource(R.string.play_to_next, it, state.level + 1) } ?: stringResource(R.string.play_max_level)
        LinearProgressIndicator(
            progress = { state.progress },
            modifier =
                Modifier.fillMaxWidth().padding(vertical = TravelTheme.spacing.sm).semantics {
                    this.contentDescription = label
                    stateDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(state.progress, 0f..1f)
                },
        )
        Text(description, style = TravelTheme.typography.bodyMedium)
    }
}

@Composable
private fun StreakCard(streak: StreakUi) {
    val title = if (streak.id == "daily") R.string.play_streak_daily else R.string.play_streak_trip_checkin
    TpCard(Modifier.fillMaxWidth()) {
        Text(stringResource(title), style = TravelTheme.typography.titleSmall)
        Text(stringResource(R.string.play_streak_values, streak.current, streak.longest), style = TravelTheme.typography.bodyMedium)
    }
}

@Composable
private fun BadgeCard(badge: BadgeUi) {
    val accent = badge.tier?.let { tierColor(it) }
    TpCard(Modifier.fillMaxWidth(), selected = badge.earned, accent = accent) {
        Text(badgeNameText(badge.id), style = TravelTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        badgeDescText(badge.id)?.let { Text(it, style = TravelTheme.typography.bodyMedium) }
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            badge.tier?.let { Text(tierLabel(it), style = TravelTheme.typography.labelMedium, color = tierColor(it)) }
            Text(
                stringResource(R.string.play_badge_progress, badge.progress.coerceAtMost(badge.target), badge.target),
                style = TravelTheme.typography.labelMedium,
            )
        }
        val date = PlayMapper.dateOf(badge.earnedAt)
        if (badge.earned) {
            val text =
                date?.let { stringResource(R.string.play_badge_earned_on, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it)) }
                    ?: stringResource(R.string.play_earned)
            Text(text, style = TravelTheme.typography.labelMedium)
        } else {
            Text(stringResource(R.string.play_badge_locked), style = TravelTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun tierColor(tier: BadgeTier): Color =
    when (tier) {
        BadgeTier.BRONZE -> TravelTheme.colors.tertiary
        BadgeTier.SILVER -> TravelTheme.colors.secondary
        BadgeTier.GOLD -> TravelTheme.colors.primary
    }

@Composable
private fun tierLabel(tier: BadgeTier): String =
    stringResource(
        when (tier) {
            BadgeTier.BRONZE -> R.string.play_tier_bronze
            BadgeTier.SILVER -> R.string.play_tier_silver
            BadgeTier.GOLD -> R.string.play_tier_gold
        },
    )

@Composable
private fun RecentRow(item: RecentUi) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(ruleNameText(item.rule), style = TravelTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.play_points, item.points), style = TravelTheme.typography.labelLarge)
    }
}

@Composable
private fun GoalCard(state: PlayUiState) {
    val goal = state.bucketGoal ?: return
    val visited = state.countries.size
    TpCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.play_goal), style = TravelTheme.typography.titleSmall)
        Text(pluralStringResource(R.plurals.play_goal_progress, goal, visited, goal), style = TravelTheme.typography.bodyMedium)
        LinearProgressIndicator(progress = { (visited.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CountryRow(country: CountryUi) {
    Text(country.name, style = TravelTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
}
