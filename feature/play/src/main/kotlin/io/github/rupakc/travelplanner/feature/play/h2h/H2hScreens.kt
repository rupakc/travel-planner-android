package io.github.rupakc.travelplanner.feature.play.h2h

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import io.github.rupakc.travelplanner.core.model.play.H2hMatch
import io.github.rupakc.travelplanner.core.model.play.H2hState
import io.github.rupakc.travelplanner.core.model.play.H2hWinner
import io.github.rupakc.travelplanner.core.model.play.QuizPack
import io.github.rupakc.travelplanner.feature.play.PlayMapper
import io.github.rupakc.travelplanner.feature.play.R
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// ---- list ---------------------------------------------------------------------------------------------------------------

/** The head-to-head list. [onOpen] opens a match, [onNew] the create screen, [onHidden] leaves when the game is off. */
@Composable
fun H2hListRoute(onBack: () -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit, onHidden: () -> Unit, viewModel: H2hListViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.phase) { if (state.phase == H2hListPhase.Hidden) onHidden() }
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(H2hListEvent.Resumed)
        onPauseOrDispose {}
    }
    H2hListScreen(state, viewModel::onEvent, onBack, onOpen, onNew)
}

@Composable
fun H2hListScreen(
    state: H2hListUiState,
    onEvent: (H2hListEvent) -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, topBar = { TpTopBar(title = stringResource(R.string.play_h2h_title), onBack = onBack) }) { padding ->
        val body = Modifier.padding(padding).fillMaxSize()
        when (val phase = state.phase) {
            H2hListPhase.Loading -> {
                SkeletonList(body.padding(TravelTheme.spacing.gutter), lines = 4)
            }

            H2hListPhase.Hidden -> {
                Unit
            }

            is H2hListPhase.Error -> {
                ErrorState(phase.message ?: stringResource(R.string.play_h2h_error), body, onRetry = { onEvent(H2hListEvent.Retry) })
            }

            H2hListPhase.Loaded -> {
                val groups = groupMatches(state.matches)
                LazyColumn(
                    body,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(TravelTheme.spacing.gutter),
                    verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
                ) {
                    item(key = "new", contentType = "new") {
                        TpButton(stringResource(R.string.play_h2h_new), onNew, Modifier.fillMaxWidth(), tid = "play.h2h.new")
                    }
                    if (groups.isEmpty()) {
                        item(key = "empty", contentType = "empty") { EmptyState(title = stringResource(R.string.play_h2h_empty)) }
                    }
                    groups.forEach { (matchState, matches) ->
                        item(key = "h-${matchState.wire}", contentType = "heading") {
                            Text(
                                sectionTitle(matchState),
                                style = TravelTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        items(matches, key = { "m-${it.id}" }, contentType = { "match" }) { MatchCard(it, onClick = { onOpen(it.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun sectionTitle(state: H2hState): String =
    stringResource(
        when (state) {
            H2hState.WAITING_YOU -> R.string.play_h2h_section_waiting_you
            H2hState.WAITING_THEM -> R.string.play_h2h_section_waiting_them
            H2hState.DONE -> R.string.play_h2h_section_done
            H2hState.DECLINED -> R.string.play_h2h_section_declined
            else -> R.string.play_h2h_section_expired
        },
    )

@Composable
private fun packLabel(pack: String?): String =
    packNameRes(pack)?.let { stringResource(it) } ?: pack.orEmpty().replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun resultText(match: H2hMatch): String? =
    when (match.matchWinner) {
        H2hWinner.YOU -> stringResource(R.string.play_h2h_winner_you)
        H2hWinner.THEM -> stringResource(R.string.play_h2h_winner_them, match.opponent.handle)
        H2hWinner.DRAW -> stringResource(R.string.play_h2h_winner_draw)
        else -> null
    }

@Composable
private fun MatchCard(match: H2hMatch, onClick: () -> Unit) {
    TpCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Text(stringResource(R.string.play_h2h_vs, match.opponent.handle), style = TravelTheme.typography.titleSmall)
        Text(packLabel(match.pack), style = TravelTheme.typography.labelMedium)
        if (match.showsScores()) {
            Text(stringResource(R.string.play_h2h_score, match.you ?: 0, match.opponent.handle, match.them ?: 0), style = TravelTheme.typography.bodyMedium)
            resultText(match)?.let { Text(it, style = TravelTheme.typography.labelLarge) }
        }
    }
}

// ---- create -------------------------------------------------------------------------------------------------------------

/** New challenge. [onPlay] gets the session id of the created match: the creator plays at once. */
@Composable
fun H2hCreateRoute(onBack: () -> Unit, onPlay: (String) -> Unit, onHidden: () -> Unit, viewModel: H2hCreateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.sessions.collect(onPlay) }
    LaunchedEffect(state.hidden) { if (state.hidden) onHidden() }
    H2hCreateScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun H2hCreateScreen(state: H2hCreateUiState, onEvent: (H2hCreateEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, topBar = { TpTopBar(title = stringResource(R.string.play_h2h_create_title), onBack = onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            OutlinedTextField(
                value = state.handle,
                onValueChange = { onEvent(H2hCreateEvent.Handle(it)) },
                label = { Text(stringResource(R.string.play_h2h_handle_label)) },
                supportingText = { Text(stringResource(R.string.play_h2h_handle_help)) },
                prefix = { Text("@") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Done, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.play_h2h_pack_label), style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            QuizPack.entries.forEach { pack ->
                TpChip(
                    label = stringResource(packNameRes(pack.wire) ?: R.string.play_pack_flags),
                    selected = state.pack == pack,
                    onClick = { onEvent(H2hCreateEvent.Pack(pack)) },
                    tid = "play.h2h.pack.${pack.wire}",
                )
            }
            state.problem?.let { problem ->
                Text(
                    problemText(problem, state.problemMessage),
                    color = TravelTheme.colors.error,
                    style = TravelTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
            TpButton(
                stringResource(R.string.play_h2h_send),
                { onEvent(H2hCreateEvent.Submit) },
                Modifier.fillMaxWidth(),
                enabled = state.valid && !state.busy,
                loading = state.busy,
                tid = "play.h2h.send",
            )
        }
    }
}

@Composable
private fun problemText(problem: CreateProblem, backend: String?): String =
    when (problem) {
        CreateProblem.LIMIT -> stringResource(R.string.play_h2h_problem_limit)
        CreateProblem.CANNOT_CHALLENGE -> stringResource(R.string.play_h2h_problem_unavailable)
        CreateProblem.SELF -> stringResource(R.string.play_h2h_problem_self)
        CreateProblem.RESTRICTED -> stringResource(R.string.play_h2h_problem_restricted)
        CreateProblem.PROFILE_REQUIRED -> stringResource(R.string.play_h2h_problem_profile)
        CreateProblem.EMAIL_UNVERIFIED -> stringResource(R.string.play_h2h_problem_email)
        CreateProblem.OTHER -> backend ?: stringResource(R.string.play_h2h_problem_other)
    }

// ---- detail -------------------------------------------------------------------------------------------------------------

/** One match: accept or decline an invite, wait, or read the result. [onPlay] gets the session id after an accept. */
@Composable
fun H2hDetailRoute(onBack: () -> Unit, onPlay: (String) -> Unit, onHidden: () -> Unit, viewModel: H2hDetailViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.sessions.collect(onPlay) }
    LaunchedEffect(viewModel) { viewModel.closed.collect { onBack() } }
    LaunchedEffect(state.phase) { if (state.phase == H2hDetailPhase.Hidden) onHidden() }
    LifecycleResumeEffect(viewModel) {
        viewModel.onEvent(H2hDetailEvent.Resumed)
        onPauseOrDispose {}
    }
    H2hDetailScreen(state, viewModel::onEvent, onBack)
}

@Composable
fun H2hDetailScreen(state: H2hDetailUiState, onEvent: (H2hDetailEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier, topBar = { TpTopBar(title = stringResource(R.string.play_h2h_detail_title), onBack = onBack) }) { padding ->
        val body = Modifier.padding(padding).fillMaxSize()
        val match = state.match
        when (val phase = state.phase) {
            H2hDetailPhase.Loading -> {
                SkeletonList(body.padding(TravelTheme.spacing.gutter), lines = 4)
            }

            H2hDetailPhase.Hidden -> {
                Unit
            }

            H2hDetailPhase.Missing -> {
                EmptyState(
                    title = stringResource(R.string.play_h2h_missing),
                    modifier = body,
                    actionLabel = stringResource(R.string.play_h2h_back),
                    onAction = onBack,
                )
            }

            is H2hDetailPhase.Error -> {
                ErrorState(phase.message ?: stringResource(R.string.play_h2h_error), body, onRetry = { onEvent(H2hDetailEvent.Retry) })
            }

            H2hDetailPhase.Loaded -> {
                if (match != null) MatchBody(match, state, onEvent, body)
            }
        }
    }
}

@Composable
private fun MatchBody(match: H2hMatch, state: H2hDetailUiState, onEvent: (H2hDetailEvent) -> Unit, modifier: Modifier) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Text(
            stringResource(R.string.play_h2h_vs, match.opponent.handle),
            style = TravelTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(packLabel(match.pack), style = TravelTheme.typography.titleSmall)
        when (match.matchState) {
            H2hState.WAITING_YOU -> {
                Text(stringResource(R.string.play_h2h_invite, match.opponent.handle, packLabel(match.pack)), style = TravelTheme.typography.bodyLarge)
                TpButton(
                    stringResource(R.string.play_h2h_accept),
                    { onEvent(H2hDetailEvent.Accept) },
                    Modifier.fillMaxWidth(),
                    enabled = !state.busy,
                    loading = state.busy,
                    tid = "play.h2h.accept",
                )
                TpButton(
                    stringResource(R.string.play_h2h_decline),
                    { onEvent(H2hDetailEvent.Decline) },
                    Modifier.fillMaxWidth(),
                    variant = TpButtonVariant.Tonal,
                    enabled = !state.busy,
                    tid = "play.h2h.decline",
                )
            }

            H2hState.WAITING_THEM -> {
                Text(stringResource(R.string.play_h2h_waiting_them, match.opponent.handle), style = TravelTheme.typography.bodyLarge)
            }

            H2hState.DONE -> {
                if (match.showsScores()) {
                    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        resultText(match)?.let { Text(it, style = TravelTheme.typography.titleLarge) }
                        Text(
                            stringResource(R.string.play_h2h_score, match.you ?: 0, match.opponent.handle, match.them ?: 0),
                            style = TravelTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    Text(stringResource(R.string.play_h2h_finished), style = TravelTheme.typography.bodyLarge)
                }
            }

            H2hState.DECLINED -> {
                Text(stringResource(R.string.play_h2h_declined), style = TravelTheme.typography.bodyLarge)
            }

            else -> {
                Text(stringResource(R.string.play_h2h_expired), style = TravelTheme.typography.bodyLarge)
            }
        }
        if (match.matchState == H2hState.WAITING_YOU || match.matchState == H2hState.WAITING_THEM) {
            PlayMapper.dateOf(match.expiresAt)?.let {
                Text(
                    stringResource(R.string.play_h2h_expires, DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(it)),
                    style = TravelTheme.typography.labelMedium,
                )
            }
        }
        state.actionError?.let {
            Text(
                it.message ?: stringResource(R.string.play_h2h_action_failed),
                color = TravelTheme.colors.error,
                style = TravelTheme.typography.bodyMedium,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}
