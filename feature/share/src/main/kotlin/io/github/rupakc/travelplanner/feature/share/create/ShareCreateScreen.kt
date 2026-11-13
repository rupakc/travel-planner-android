package io.github.rupakc.travelplanner.feature.share.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.component.LocalUiEventSink
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.UiEventKind
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.share.ShareDates
import io.github.rupakc.travelplanner.core.model.share.ShareErrorCode
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.share.shareErrorCode
import io.github.rupakc.travelplanner.feature.share.R

/** Dialog route: collects state and runs the platform effects (share sheet, clipboard). */
@Composable
internal fun ShareCreateDialogRoute(onClose: () -> Unit) {
    val viewModel: ShareCreateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooser = stringResource(R.string.share_create_chooser)
    val captions =
        mapOf(
            ShareKind.PLAN to stringResource(R.string.share_create_text_plan),
            ShareKind.RECAP to stringResource(R.string.share_create_text_recap),
            ShareKind.SAVINGS to stringResource(R.string.share_create_text_savings),
            ShareKind.QUIZ to stringResource(R.string.share_create_text_quiz),
            ShareKind.STATUS to stringResource(R.string.share_create_text_status),
        )
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ShareCreateEffect.CopyLink -> {
                    copyToClipboard(context, effect.url)
                }

                is ShareCreateEffect.Send -> {
                    val text = ShareSheet.text(captions[effect.kind] ?: captions.getValue(ShareKind.PLAN), effect.url)
                    if (!ShareSheet.start(context, text, effect.card, chooser)) viewModel.onEvent(ShareCreateEvent.SendFailed)
                }
            }
        }
    }
    ShareCreateScreen(state, viewModel::onEvent, onClose)
}

private fun copyToClipboard(context: android.content.Context, url: String) {
    val manager = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(android.content.ClipData.newPlainText("", url))
}

/** Stateless dialog content: options, create / update, the link with share and copy, stop sharing. */
@Composable
internal fun ShareCreateScreen(state: ShareCreateUiState, onEvent: (ShareCreateEvent) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val planKind = state.kind == ShareKind.PLAN
    Surface(modifier.fillMaxWidth(), shape = TravelTheme.shapes.large, color = TravelTheme.colors.surface) {
        Column(
            Modifier.heightIn(max = MAX_HEIGHT).verticalScroll(rememberScrollState()).padding(TravelTheme.spacing.lg),
            verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
        ) {
            Text(
                stringResource(titleRes(state.kind)),
                style = TravelTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.share_create_intro), style = TravelTheme.typography.bodyMedium)
            if (planKind) {
                Text(stringResource(R.string.share_create_reveals), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
            }
            Text(
                stringResource(R.string.share_create_options_legend),
                style = TravelTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            if (planKind) {
                DatesGroup(state, onEvent)
                SwitchRow(R.string.share_create_show_origin, state.options.showOrigin, !state.busy, "share.origin") {
                    onEvent(ShareCreateEvent.SetShowOrigin(it))
                }
            }
            SwitchRow(R.string.share_create_show_name, state.options.showName, !state.busy, "share.name") { onEvent(ShareCreateEvent.SetShowName(it)) }

            val submitText =
                when {
                    state.working -> R.string.share_create_working
                    state.created != null -> R.string.share_create_update
                    else -> R.string.share_create_create
                }
            TpButton(
                stringResource(submitText),
                { onEvent(ShareCreateEvent.Submit) },
                Modifier.fillMaxWidth(),
                enabled = !state.busy && (state.created == null || state.dirty),
                loading = state.working,
                tid = "share.create",
            )
            state.created?.let { LinkSection(state, it.url, onEvent) }
            MessageLine(state.message)
            TpButton(
                stringResource(R.string.share_create_close),
                onClose,
                Modifier.fillMaxWidth(),
                TpButtonVariant.Text,
                tid = "share.close",
            )
        }
    }
}

@Composable
private fun DatesGroup(state: ShareCreateUiState, onEvent: (ShareCreateEvent) -> Unit) {
    val sink = LocalUiEventSink.current
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
        Text(stringResource(R.string.share_create_dates), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
        ShareDates.entries.forEach { mode ->
            val selected = state.options.datesMode == mode
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = TravelTheme.spacing.minTouchTarget)
                    .selectable(
                        selected = selected,
                        enabled = !state.busy,
                        role = Role.RadioButton,
                        onClick = {
                            runCatching { sink.onUiEvent(UiEventKind.TAP, "share.dates.${mode.wire}", null) }
                            onEvent(ShareCreateEvent.SetDates(mode))
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
            ) {
                RadioButton(selected = selected, onClick = null, enabled = !state.busy)
                Text(stringResource(datesRes(mode)), style = TravelTheme.typography.bodyMedium)
            }
        }
        Text(stringResource(R.string.share_create_dates_note), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, enabled: Boolean, tid: String, onChange: (Boolean) -> Unit) {
    val sink = LocalUiEventSink.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TravelTheme.spacing.minTouchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = {
                    runCatching { sink.onUiEvent(UiEventKind.TOGGLE, tid, it) }
                    onChange(it)
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        Text(stringResource(label), style = TravelTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun LinkSection(state: ShareCreateUiState, url: String, onEvent: (ShareCreateEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(stringResource(R.string.share_create_url_label), style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
        Text(url, style = TravelTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        if (state.dirty) {
            Text(stringResource(R.string.share_create_link_pending), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            TpButton(
                stringResource(R.string.share_create_action_native),
                { onEvent(ShareCreateEvent.Share) },
                Modifier.weight(1f),
                enabled = state.canShare,
                loading = state.sharing,
                tid = "share.native",
            )
            TpButton(
                stringResource(R.string.share_create_action_copy),
                { onEvent(ShareCreateEvent.Copy) },
                Modifier.weight(1f),
                TpButtonVariant.Secondary,
                enabled = state.canShare,
                tid = "share.copy",
            )
        }
        if (state.confirmRevoke) {
            RevokeConfirm(onEvent)
        } else {
            TpButton(
                stringResource(R.string.share_create_revoke),
                { onEvent(ShareCreateEvent.AskRevoke) },
                Modifier.fillMaxWidth(),
                TpButtonVariant.Destructive,
                enabled = !state.busy,
                loading = state.revoking,
                tid = "share.revoke",
            )
        }
    }
}

@Composable
private fun RevokeConfirm(onEvent: (ShareCreateEvent) -> Unit) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Assertive }, verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
        Text(stringResource(R.string.share_create_revoke_confirm), style = TravelTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            TpButton(
                stringResource(R.string.share_create_revoke_yes),
                { onEvent(ShareCreateEvent.ConfirmRevoke) },
                Modifier.weight(1f),
                TpButtonVariant.Destructive,
                tid = "share.revoke.yes",
            )
            TpButton(
                stringResource(R.string.share_create_revoke_no),
                { onEvent(ShareCreateEvent.CancelRevoke) },
                Modifier.weight(1f),
                TpButtonVariant.Secondary,
                tid = "share.revoke.no",
            )
        }
    }
}

/** One live-region line for the last result; errors read in the error colour and never by colour alone (they are words). */
@Composable
private fun MessageLine(message: ShareCreateMessage?) {
    val text = message?.let { messageText(it) } ?: ""
    val isError = message is ShareCreateMessage.Failed || message == ShareCreateMessage.ShareFailed || message == ShareCreateMessage.Offline
    Text(
        text,
        style = TravelTheme.typography.bodySmall,
        color = if (isError) TravelTheme.colors.error else TravelTheme.colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun messageText(message: ShareCreateMessage): String =
    when (message) {
        ShareCreateMessage.Created -> stringResource(R.string.share_create_status_created)
        ShareCreateMessage.Updated -> stringResource(R.string.share_create_status_updated)
        ShareCreateMessage.Copied -> stringResource(R.string.share_create_status_copy)
        ShareCreateMessage.Revoked -> stringResource(R.string.share_create_status_revoked)
        ShareCreateMessage.Offline -> stringResource(R.string.share_create_offline)
        ShareCreateMessage.CardUnavailable -> stringResource(R.string.share_create_card_unavailable)
        ShareCreateMessage.ShareFailed -> stringResource(R.string.share_create_error_share)
        is ShareCreateMessage.Failed -> failureText(message.error, message.revoke)
    }

@Composable
private fun failureText(error: AppError, revoke: Boolean): String =
    when {
        error.shareErrorCode() == ShareErrorCode.SHARING_DISABLED -> stringResource(R.string.share_create_error_unavailable)

        error.shareErrorCode() == ShareErrorCode.SHARE_NOT_FOUND && revoke -> stringResource(R.string.share_create_status_revoked)

        // 429 and anything else the server explained: its own text, as is.
        error.shareErrorCode() == ShareErrorCode.UNKNOWN && error.message?.isNotBlank() == true -> error.message.orEmpty()

        revoke -> stringResource(R.string.share_create_error_revoke)

        else -> stringResource(R.string.share_create_error_create)
    }

private fun titleRes(kind: ShareKind): Int =
    when (kind) {
        ShareKind.RECAP -> R.string.share_create_title_recap
        ShareKind.SAVINGS -> R.string.share_create_title_savings
        ShareKind.QUIZ -> R.string.share_create_title_quiz
        ShareKind.STATUS -> R.string.share_create_title_status
        else -> R.string.share_create_title_plan
    }

private fun datesRes(mode: ShareDates): Int =
    when (mode) {
        ShareDates.HIDDEN -> R.string.share_create_dates_hidden
        ShareDates.MONTH -> R.string.share_create_dates_month
        ShareDates.EXACT -> R.string.share_create_dates_exact
    }

private val MAX_HEIGHT = 640.dp
