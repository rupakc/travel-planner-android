package io.github.rupakc.travelplanner.feature.play.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpChip
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.play.BoardRow
import io.github.rupakc.travelplanner.core.model.play.BoardScope
import io.github.rupakc.travelplanner.core.model.play.BoardWeek
import io.github.rupakc.travelplanner.feature.play.R

/**
 * Leaderboards. [onOpenProfile] opens the caller's profile (where the leaderboard opt-in lives); null hides that button
 * (social is off on this server).
 */
@Composable
fun BoardRoute(onBack: () -> Unit, onHidden: () -> Unit, onOpenProfile: (() -> Unit)?, viewModel: BoardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.phase) { if (state.phase == BoardPhase.Hidden) onHidden() }
    BoardScreen(state, viewModel::onEvent, onBack, onOpenProfile)
}

@Composable
fun BoardScreen(state: BoardUiState, onEvent: (BoardEvent) -> Unit, onBack: () -> Unit, onOpenProfile: (() -> Unit)?, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, topBar = { TpTopBar(title = stringResource(R.string.play_board_title), onBack = onBack) }) { padding ->
        val body = Modifier.padding(padding).fillMaxSize()
        LazyColumn(
            body,
            contentPadding = PaddingValues(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
        ) {
            item(key = "scope", contentType = "chips") {
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    TpChip(stringResource(R.string.play_board_global), state.scope == BoardScope.GLOBAL, {
                        onEvent(BoardEvent.Scope(BoardScope.GLOBAL))
                    }, tid = "play.board.global")
                    TpChip(stringResource(R.string.play_board_friends), state.scope == BoardScope.FRIENDS, {
                        onEvent(BoardEvent.Scope(BoardScope.FRIENDS))
                    }, tid = "play.board.friends")
                }
            }
            item(key = "week", contentType = "chips") {
                Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                    TpChip(stringResource(R.string.play_board_current), state.week == BoardWeek.CURRENT, {
                        onEvent(BoardEvent.Week(BoardWeek.CURRENT))
                    }, tid = "play.board.current")
                    TpChip(stringResource(R.string.play_board_previous), state.week == BoardWeek.PREVIOUS, {
                        onEvent(BoardEvent.Week(BoardWeek.PREVIOUS))
                    }, tid = "play.board.previous")
                }
            }
            when (val phase = state.phase) {
                BoardPhase.Loading -> {
                    item(key = "loading", contentType = "skeleton") { SkeletonList(lines = 5) }
                }

                BoardPhase.Hidden -> {
                    Unit
                }

                is BoardPhase.Error -> {
                    item(key = "error", contentType = "error") {
                        ErrorState(phase.message ?: stringResource(R.string.play_board_error), Modifier.fillMaxWidth(), onRetry = { onEvent(BoardEvent.Retry) })
                    }
                }

                BoardPhase.Loaded -> {
                    loaded(state, onOpenProfile)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.loaded(state: BoardUiState, onOpenProfile: (() -> Unit)?) {
    state.weekLabel?.let { week ->
        item(key = "week-label", contentType = "label") {
            Text(stringResource(R.string.play_board_week, week), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        }
    }
    item(key = "you", contentType = "you") { YourPlace(state, onOpenProfile) }
    if (state.showOptInPrompt) {
        item(key = "optin", contentType = "optin") { OptInCard(onOpenProfile) }
    }
    if (state.rows.isEmpty()) {
        item(key = "empty", contentType = "empty") {
            EmptyState(
                title = stringResource(if (state.scope == BoardScope.FRIENDS) R.string.play_board_empty_friends else R.string.play_board_empty_global),
            )
        }
    } else {
        items(state.rows, key = { "row-${it.handle}" }, contentType = { "row" }) { RowItem(it) }
    }
}

@Composable
private fun YourPlace(state: BoardUiState, onOpenProfile: (() -> Unit)?) {
    val you = state.you
    TpCard(Modifier.fillMaxWidth()) {
        when {
            you == null -> {
                Text(stringResource(R.string.play_board_noprofile), style = TravelTheme.typography.bodyMedium)
                onOpenProfile?.let {
                    TpButton(stringResource(R.string.play_board_profile_button), it, variant = TpButtonVariant.Tonal, tid = "play.board.profile")
                }
            }

            you.rank == null -> {
                Text(stringResource(R.string.play_board_unranked), style = TravelTheme.typography.bodyMedium)
            }

            else -> {
                Text(stringResource(R.string.play_board_your_rank, you.rank ?: 0), style = TravelTheme.typography.titleSmall)
                val score = you.score ?: 0
                Text(pluralStringResource(R.plurals.play_history_points, score, score), style = TravelTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun OptInCard(onOpenProfile: (() -> Unit)?) {
    TpCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.play_board_optin_title), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.play_board_optin_body), style = TravelTheme.typography.bodyMedium)
        onOpenProfile?.let { TpButton(stringResource(R.string.play_board_profile_button), it, variant = TpButtonVariant.Tonal, tid = "play.board.optin") }
    }
}

@Composable
private fun RowItem(row: BoardRow) {
    TpCard(Modifier.fillMaxWidth(), selected = row.you) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val name = stringResource(R.string.play_board_rank, row.rank ?: 0, row.handle)
            Text(
                if (row.you) "$name (${stringResource(R.string.play_board_you)})" else name,
                style = TravelTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            val score = row.score ?: 0
            Text(pluralStringResource(R.plurals.play_history_points, score, score), style = TravelTheme.typography.labelLarge)
        }
        // The level appears only when the player opted in to showing it.
        row.level?.let { Text(stringResource(R.string.play_level, it), style = TravelTheme.typography.labelMedium) }
    }
}
