package io.github.rupakc.travelplanner.feature.social.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpDropdownMenuItem
import io.github.rupakc.travelplanner.core.designsystem.component.TpIconButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.social.ProfileBadge
import io.github.rupakc.travelplanner.core.model.social.PublicProfile
import io.github.rupakc.travelplanner.feature.social.R
import io.github.rupakc.travelplanner.feature.social.report.ReportDialog

/** Route: a public profile. [onBack] leaves it (also after a block). */
@Composable
internal fun PublicProfileRoute(onBack: () -> Unit, viewModel: PublicProfileViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it == PublicProfileEffect.Leave) onBack() }
    }
    PublicProfileScreen(state, viewModel::onEvent, onBack)
}

/** Stateless public profile: loading, error with retry, "not available", then the profile with its actions. */
@Composable
internal fun PublicProfileScreen(state: PublicProfileUiState, onEvent: (PublicProfileEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onEvent(PublicProfileEvent.MessageShown)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TpTopBar(
                title = if (state.handle.isEmpty()) stringResource(R.string.social_profile_title) else "@${state.handle}",
                onBack = onBack,
                actions = { if (state.phase == ViewPhase.Content && !state.isMe) MoreMenu(onEvent) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val content = Modifier.padding(padding)
        val profile = state.profile
        when {
            state.phase == ViewPhase.Loading -> {
                SkeletonList(content.padding(TravelTheme.spacing.gutter), lines = 4)
            }

            state.phase == ViewPhase.Unavailable -> {
                EmptyState(
                    stringResource(R.string.social_profile_unavailable),
                    content.fillMaxSize(),
                    message = stringResource(R.string.social_profile_unavailable_body),
                )
            }

            state.phase == ViewPhase.Error -> {
                ErrorState(errorText(state.error), content.fillMaxSize(), onRetry = { onEvent(PublicProfileEvent.Retry) })
            }

            profile != null -> {
                ProfileBody(profile, state, onEvent, content)
            }
        }
    }
    if (state.confirmBlock) BlockDialog(state.handle, onEvent)
    if (state.reporting) {
        ReportDialog(
            onDismiss = { onEvent(PublicProfileEvent.DismissReport) },
            onSubmit = { onEvent(PublicProfileEvent.SendReport(it)) },
            sending = state.reportSending,
            error = state.reportError?.let { errorText(it) },
        )
    }
}

@Composable
private fun MoreMenu(onEvent: (PublicProfileEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TpIconButton(onClick = { open = true }, tid = "social.social_more_actions") {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.social_more_actions))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            TpDropdownMenuItem(
                text = { Text(stringResource(R.string.social_report_profile)) },
                onClick = {
                    open = false
                    onEvent(PublicProfileEvent.AskReport)
                },
                tid = "social.social_report_profile",
            )
            TpDropdownMenuItem(
                text = { Text(stringResource(R.string.social_block)) },
                onClick = {
                    open = false
                    onEvent(PublicProfileEvent.AskBlock)
                },
                tid = "social.social_block",
            )
        }
    }
}

@Composable
private fun ProfileBody(profile: PublicProfile, state: PublicProfileUiState, onEvent: (PublicProfileEvent) -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Avatar(profile.handle, profile.avatarUrl)
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                Text("@${profile.handle}", style = TravelTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                if (profile.you.followsYou && !state.isMe) {
                    Text(stringResource(R.string.social_follows_you), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
        }
        Counts(profile)
        // Optional fields: shown only when the server sent them (the owner opted in). Plain Text only.
        profile.bio?.takeIf { it.isNotBlank() }?.let { Text(it, style = TravelTheme.typography.bodyLarge) }
        profile.homeCountry?.takeIf { it.isNotBlank() }?.let { Fact(R.string.social_home_country, countryName(it)) }
        profile.countries?.takeIf { it.isNotEmpty() }?.let { Fact(R.string.social_countries, it.joinToString(", ") { code -> countryName(code) }) }
        if (!state.hideGame) {
            profile.level?.let { Fact(R.string.social_level, stringResource(R.string.social_level_value, it)) }
            profile.badges?.takeIf { it.isNotEmpty() }?.let { Fact(R.string.social_badges, it.map { badge -> badgeText(badge) }.joinToString(", ")) }
        }
        if (state.isMe) {
            Text(stringResource(R.string.social_this_is_you), style = TravelTheme.typography.bodyMedium, color = TravelTheme.colors.onSurfaceVariant)
        } else {
            FollowButton(state, onEvent)
        }
    }
}

@Composable
private fun FollowButton(state: PublicProfileUiState, onEvent: (PublicProfileEvent) -> Unit) {
    val (label, variant) =
        when (state.follow) {
            FollowUi.None -> R.string.social_follow to TpButtonVariant.Primary
            FollowUi.Pending -> R.string.social_requested to TpButtonVariant.Secondary
            FollowUi.Following -> R.string.social_following_state to TpButtonVariant.Secondary
        }
    TpButton(
        stringResource(label),
        { onEvent(PublicProfileEvent.ToggleFollow) },
        Modifier.fillMaxWidth(),
        variant,
        loading = state.working,
        tid = "social.profile.follow",
    )
    if (state.follow == FollowUi.Pending) {
        Text(stringResource(R.string.social_requested_hint), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun Counts(profile: PublicProfile) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.lg)) {
        profile.ideasCount?.let { Count(it, R.string.social_ideas) }
        profile.followersCount?.let { Count(it, R.string.social_followers_title) }
        profile.followingCount?.let { Count(it, R.string.social_following_title) }
    }
}

@Composable
private fun Count(value: Int, label: Int) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(value.toString(), style = TravelTheme.typography.titleMedium)
        Text(stringResource(label), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun Fact(label: Int, value: String) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Text(stringResource(label), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
        Text(value, style = TravelTheme.typography.bodyMedium)
    }
}

@Composable
private fun Avatar(handle: String, url: String?) {
    Surface(shape = CircleShape, color = TravelTheme.colors.primaryContainer, modifier = Modifier.size(AVATAR_SIZE)) {
        Box(contentAlignment = Alignment.Center) {
            Text(handle.take(1).uppercase(), style = TravelTheme.typography.titleLarge)
            // Only an absolute http(s) address is loaded; the initial stays under it.
            if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
                AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private val AVATAR_SIZE = 72.dp

@Composable
private fun badgeText(badge: ProfileBadge): String {
    val name = badge.id.replace('_', ' ').replaceFirstChar { it.uppercase() }
    return badge.tier?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.social_badge_tier, name, it) } ?: name
}

@Composable
private fun BlockDialog(handle: String, onEvent: (PublicProfileEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(PublicProfileEvent.DismissBlock) },
        title = { Text(stringResource(R.string.social_block_title, handle)) },
        text = { Text(stringResource(R.string.social_block_body)) },
        confirmButton = {
            TpTextButton(onClick = { onEvent(PublicProfileEvent.ConfirmBlock) }, tid = "social.social_block_2") { Text(stringResource(R.string.social_block)) }
        },
        dismissButton = {
            TpTextButton(onClick = { onEvent(PublicProfileEvent.DismissBlock) }, tid = "social.social_cancel") { Text(stringResource(R.string.social_cancel)) }
        },
    )
}

@Composable
private fun messageText(message: ProfileMessage): String =
    when (message) {
        ProfileMessage.Blocked -> stringResource(R.string.social_blocked_done)
        ProfileMessage.Reported -> stringResource(R.string.social_report_done)
        is ProfileMessage.Failed -> errorText(message.error)
    }
