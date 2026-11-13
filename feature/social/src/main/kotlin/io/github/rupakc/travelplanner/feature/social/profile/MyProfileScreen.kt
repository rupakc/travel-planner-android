package io.github.rupakc.travelplanner.feature.social.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.component.tpSelectable
import io.github.rupakc.travelplanner.core.designsystem.component.tpToggleable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.social.HandleReason
import io.github.rupakc.travelplanner.core.model.social.ProfileVisibility
import io.github.rupakc.travelplanner.feature.social.R
import io.github.rupakc.travelplanner.feature.social.connections.ConnectionTab

/** Route: the caller's own profile. [onOpenProfile] shows it as others see it; [onOpenConnections] opens one list tab. */
@Composable
internal fun MyProfileRoute(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenConnections: (ConnectionTab) -> Unit,
    viewModel: MyProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MyProfileScreen(state, viewModel::onEvent, onBack, onOpenProfile, onOpenConnections)
}

/** Stateless create/edit profile screen: loading, error with retry, then the form. */
@Composable
internal fun MyProfileScreen(
    state: MyProfileUiState,
    onEvent: (MyProfileEvent) -> Unit,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenConnections: (ConnectionTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbar = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.social_profile_saved)
    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) snackbar.showSnackbar(savedText)
    }
    Scaffold(
        modifier = modifier,
        topBar = { TpTopBar(title = stringResource(R.string.social_profile_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val content = Modifier.padding(padding)
        when (state.phase) {
            ProfilePhase.Loading -> SkeletonList(content.padding(TravelTheme.spacing.gutter), lines = 5)
            ProfilePhase.Error -> ErrorState(errorText(state.error), content.fillMaxSize(), onRetry = { onEvent(MyProfileEvent.Retry) })
            ProfilePhase.Content -> ProfileForm(state, onEvent, onOpenProfile, onOpenConnections, content)
        }
    }
}

@Composable
private fun ProfileForm(
    state: MyProfileUiState,
    onEvent: (MyProfileEvent) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenConnections: (ConnectionTab) -> Unit,
    modifier: Modifier,
) {
    val form = state.form
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Text(
            stringResource(if (state.isNew) R.string.social_profile_intro_new else R.string.social_profile_intro),
            style = TravelTheme.typography.bodyMedium,
            color = TravelTheme.colors.onSurfaceVariant,
        )
        HandleField(state, onEvent)
        SectionTitle(R.string.social_visibility_title)
        Column(Modifier.selectableGroup()) {
            VISIBILITIES.forEach { (value, label, hint) ->
                RadioRow(selected = form.visibility == value, label = stringResource(label), hint = stringResource(hint)) {
                    onEvent(MyProfileEvent.VisibilityChanged(value))
                }
            }
        }
        TpOutlinedTextField(
            value = form.bio,
            onValueChange = { onEvent(MyProfileEvent.BioChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.social_bio_label)) },
            supportingText = {
                if (state.problem == FieldProblem.BioRejected) {
                    Text(stringResource(R.string.social_error_bio_rejected))
                } else {
                    Text(stringResource(R.string.social_bio_count, form.bio.length, BIO_MAX))
                }
            },
            isError = state.problem == FieldProblem.BioRejected,
            minLines = 2,
            maxLines = 4,
            tid = "social.social_bio_label",
        )
        TpOutlinedTextField(
            value = form.homeCountry,
            onValueChange = { onEvent(MyProfileEvent.CountryChanged(it)) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.social_country_label)) },
            supportingText = {
                val bad = state.problem == FieldProblem.CountryInvalid || !isCountryComplete(form.homeCountry)
                Text(stringResource(if (bad) R.string.social_error_country else R.string.social_country_hint))
            },
            isError = state.problem == FieldProblem.CountryInvalid || !isCountryComplete(form.homeCountry),
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            tid = "social.social_country_label",
        )
        SectionTitle(R.string.social_optins_title)
        Text(stringResource(R.string.social_optins_body), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        SwitchRow(R.string.social_show_avatar, form.showAvatar) { onEvent(MyProfileEvent.Toggled(ProfileToggle.Avatar, it)) }
        SwitchRow(R.string.social_show_home_country, form.showHomeCountry) { onEvent(MyProfileEvent.Toggled(ProfileToggle.HomeCountry, it)) }
        SwitchRow(R.string.social_show_countries, form.showCountries) { onEvent(MyProfileEvent.Toggled(ProfileToggle.Countries, it)) }
        if (!state.hideGame) {
            SwitchRow(R.string.social_show_level, form.showLevel) { onEvent(MyProfileEvent.Toggled(ProfileToggle.Level, it)) }
            SwitchRow(R.string.social_show_badges, form.showBadges) { onEvent(MyProfileEvent.Toggled(ProfileToggle.Badges, it)) }
            SwitchRow(R.string.social_leaderboard_opt_in, form.leaderboardOptIn) { onEvent(MyProfileEvent.Toggled(ProfileToggle.Leaderboard, it)) }
        }
        state.saveError?.let {
            Text(
                errorText(it),
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = TravelTheme.typography.bodyMedium,
                color = TravelTheme.colors.error,
            )
        }
        TpButton(
            stringResource(if (state.isNew) R.string.social_create_profile else R.string.social_save),
            { onEvent(MyProfileEvent.Save) },
            Modifier.fillMaxWidth(),
            enabled = state.canSave,
            loading = state.saving,
            tid = "social.profile.save",
        )
        if (!state.isNew) {
            HorizontalDivider()
            val handle = state.original.handle.orEmpty()
            TpButton(
                stringResource(R.string.social_view_profile),
                { onOpenProfile(handle) },
                Modifier.fillMaxWidth(),
                TpButtonVariant.Secondary,
                tid = "social.profile.view",
            )
            ConnectionRow(R.string.social_requests_title, ConnectionTab.Requests, onOpenConnections)
            ConnectionRow(R.string.social_followers_title, ConnectionTab.Followers, onOpenConnections)
            ConnectionRow(R.string.social_following_title, ConnectionTab.Following, onOpenConnections)
            ConnectionRow(R.string.social_blocked_title, ConnectionTab.Blocked, onOpenConnections)
        }
    }
}

@Composable
private fun HandleField(state: MyProfileUiState, onEvent: (MyProfileEvent) -> Unit) {
    val problem = state.problem
    val unavailable = state.handleStatus as? HandleStatus.Unavailable
    val isError =
        state.handleStatus == HandleStatus.BadSyntax || unavailable != null ||
            problem == FieldProblem.HandleTaken || problem == FieldProblem.HandleInvalid ||
            problem == FieldProblem.HandleReserved || problem == FieldProblem.HandleRateLimited
    val message =
        when {
            problem == FieldProblem.HandleRateLimited -> {
                state.retryDays?.let { stringResource(R.string.social_error_handle_rate_limited_days, it) }
                    ?: stringResource(R.string.social_error_handle_rate_limited)
            }

            unavailable?.reason == HandleReason.RESERVED || problem == FieldProblem.HandleReserved -> {
                stringResource(R.string.social_handle_reserved)
            }

            unavailable != null || problem == FieldProblem.HandleTaken -> {
                stringResource(R.string.social_handle_taken)
            }

            state.handleStatus == HandleStatus.BadSyntax || problem == FieldProblem.HandleInvalid -> {
                stringResource(R.string.social_handle_invalid)
            }

            state.handleStatus == HandleStatus.Checking -> {
                stringResource(R.string.social_handle_checking)
            }

            state.handleStatus == HandleStatus.Available -> {
                stringResource(R.string.social_handle_available)
            }

            else -> {
                stringResource(R.string.social_handle_hint)
            }
        }
    TpOutlinedTextField(
        value = state.form.handle,
        onValueChange = { onEvent(MyProfileEvent.HandleChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.social_handle_label)) },
        prefix = { Text("@") },
        supportingText = { Text(message, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Next),
        tid = "social.social_handle_label",
    )
}

private val VISIBILITIES =
    listOf(
        Triple(ProfileVisibility.PRIVATE, R.string.social_visibility_private, R.string.social_visibility_private_hint),
        Triple(ProfileVisibility.FOLLOWERS, R.string.social_visibility_followers, R.string.social_visibility_followers_hint),
        Triple(ProfileVisibility.PUBLIC, R.string.social_visibility_public, R.string.social_visibility_public_hint),
    )

@Composable
private fun SectionTitle(text: Int) {
    Text(stringResource(text), style = TravelTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

@Composable
private fun RadioRow(selected: Boolean, label: String, hint: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(
            min = TravelTheme.spacing.minTouchTarget,
        ).tpSelectable(tid = "social.my_profile_screen_selectable", selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = TravelTheme.spacing.sm)) {
            Text(label, style = TravelTheme.typography.bodyLarge)
            Text(hint, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(
            min = TravelTheme.spacing.minTouchTarget,
        ).tpToggleable(tid = "social.my_profile_screen_toggleable", value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(label), Modifier.weight(1f), style = TravelTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ConnectionRow(label: Int, tab: ConnectionTab, onOpen: (ConnectionTab) -> Unit) {
    TpButton(stringResource(label), { onOpen(tab) }, Modifier.fillMaxWidth(), TpButtonVariant.Text, tid = "social.profile.${tab.wire}")
}

@Composable
internal fun errorText(error: AppError?): String =
    when {
        error is AppError.Network -> stringResource(R.string.social_offline)
        error?.message?.isNotBlank() == true -> error.message.orEmpty()
        else -> stringResource(R.string.social_error_generic)
    }
