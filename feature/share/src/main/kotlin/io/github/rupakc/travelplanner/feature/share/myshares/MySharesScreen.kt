package io.github.rupakc.travelplanner.feature.share.myshares

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.common.AppError
import io.github.rupakc.travelplanner.core.designsystem.component.EmptyState
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.share.ShareDates
import io.github.rupakc.travelplanner.core.model.share.ShareErrorCode
import io.github.rupakc.travelplanner.core.model.share.ShareKind
import io.github.rupakc.travelplanner.core.model.share.ShareOptions
import io.github.rupakc.travelplanner.core.model.share.ShareRow
import io.github.rupakc.travelplanner.core.model.share.shareErrorCode
import io.github.rupakc.travelplanner.feature.share.R
import io.github.rupakc.travelplanner.feature.share.create.ShareSheet

/** Route: collects state and runs the platform effects (clipboard, system share sheet). */
@Composable
internal fun MySharesRouteScreen(onBack: () -> Unit, viewModel: MySharesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val chooser = stringResource(R.string.share_create_chooser)
    val captions =
        mapOf(
            ShareKind.PLAN.wire to stringResource(R.string.share_create_text_plan),
            ShareKind.RECAP.wire to stringResource(R.string.share_create_text_recap),
            ShareKind.SAVINGS.wire to stringResource(R.string.share_create_text_savings),
            ShareKind.QUIZ.wire to stringResource(R.string.share_create_text_quiz),
            ShareKind.STATUS.wire to stringResource(R.string.share_create_text_status),
        )
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is MySharesEffect.CopyLink -> {
                    copyToClipboard(context, effect.url)
                }

                is MySharesEffect.Send -> {
                    val text = ShareSheet.text(captions[effect.kind] ?: captions.getValue(ShareKind.PLAN.wire), effect.url)
                    if (!ShareSheet.start(context, text, effect.card, chooser)) viewModel.onEvent(MySharesEvent.SendFailed)
                }
            }
        }
    }
    MySharesScreen(state, viewModel::onEvent, onBack)
}

private fun copyToClipboard(context: Context, url: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText("", url))
}

/** Stateless "My shares" screen: loading, error with retry, empty, the list and the legacy section. */
@Composable
internal fun MySharesScreen(state: MySharesUiState, onEvent: (MySharesEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onEvent(MySharesEvent.MessageShown)
        }
    }
    Scaffold(
        modifier = modifier,
        topBar = { TpTopBar(title = stringResource(R.string.myshares_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val content = Modifier.padding(padding)
        when {
            state.phase == MySharesPhase.Loading -> {
                SkeletonList(content.padding(TravelTheme.spacing.gutter), lines = 4)
            }

            state.phase == MySharesPhase.Error -> {
                ErrorState(errorText(state.error), content.fillMaxSize(), onRetry = { onEvent(MySharesEvent.Retry) })
            }

            state.isEmpty -> {
                EmptyState(
                    stringResource(R.string.myshares_empty_title),
                    content.fillMaxSize(),
                    message = stringResource(R.string.myshares_empty_body),
                )
            }

            else -> {
                SharesList(state, onEvent, content)
            }
        }
    }
    if (state.confirm != null) ConfirmDialog(onEvent)
}

@Composable
private fun SharesList(state: MySharesUiState, onEvent: (MySharesEvent) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        item(key = "note", contentType = "note") {
            Text(stringResource(R.string.myshares_revoke_note), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        }
        items(state.shares, key = { it.token }, contentType = { "share" }) { row ->
            val target = RevokeTarget.Share(row)
            ShareCard(
                title = row.title?.takeIf { it.isNotBlank() } ?: stringResource(kindLabel(row.shareKind)),
                kindLabel = stringResource(kindLabel(row.shareKind)),
                detail = optionsSummary(row),
                target = target,
                state = state,
                onEvent = onEvent,
            )
        }
        if (state.legacy.isNotEmpty()) {
            item(key = "legacy-head", contentType = "head") {
                Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.xs)) {
                    Text(
                        stringResource(R.string.myshares_legacy_title),
                        style = TravelTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(R.string.myshares_legacy_body), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
                }
            }
            items(state.legacy, key = { "legacy-${it.planId}" }, contentType = { "legacy" }) { legacy ->
                ShareCard(
                    title = legacy.name?.takeIf { it.isNotBlank() } ?: stringResource(kindLabel(ShareKind.PLAN)),
                    kindLabel = stringResource(kindLabel(ShareKind.PLAN)),
                    detail = null,
                    target = RevokeTarget.Legacy(legacy),
                    state = state,
                    onEvent = onEvent,
                )
            }
        }
    }
}

@Composable
private fun ShareCard(title: String, kindLabel: String, detail: String?, target: RevokeTarget, state: MySharesUiState, onEvent: (MySharesEvent) -> Unit) {
    val revoking = state.revokingKey == target.key
    TpCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
            Text(title, style = TravelTheme.typography.titleSmall)
            Text(kindLabel, style = TravelTheme.typography.labelMedium, color = TravelTheme.colors.onSurfaceVariant)
            detail?.let { Text(it, style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant) }
            Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), modifier = Modifier.fillMaxWidth()) {
                TpButton(
                    stringResource(R.string.myshares_share),
                    { onEvent(MySharesEvent.ShareAgain(target)) },
                    Modifier.weight(1f),
                    enabled = !state.busy,
                    loading = state.sharingToken == target.key,
                    tid = "myshares.share",
                )
                TpButton(
                    stringResource(R.string.myshares_copy),
                    { onEvent(MySharesEvent.Copy(target.url, target.kindWire)) },
                    Modifier.weight(1f),
                    TpButtonVariant.Secondary,
                    tid = "myshares.copy",
                )
            }
            TpButton(
                stringResource(R.string.myshares_revoke),
                { onEvent(MySharesEvent.AskRevoke(target)) },
                Modifier.fillMaxWidth(),
                TpButtonVariant.Text,
                enabled = !state.busy,
                loading = revoking,
                tid = "myshares.revoke",
            )
        }
    }
}

@Composable
private fun ConfirmDialog(onEvent: (MySharesEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(MySharesEvent.Dismiss) },
        title = { Text(stringResource(R.string.myshares_confirm_title)) },
        text = { Text(stringResource(R.string.myshares_confirm_body)) },
        confirmButton = { TextButton(onClick = { onEvent(MySharesEvent.ConfirmRevoke) }) { Text(stringResource(R.string.myshares_confirm_yes)) } },
        dismissButton = { TextButton(onClick = { onEvent(MySharesEvent.Dismiss) }) { Text(stringResource(R.string.myshares_confirm_no)) } },
    )
}

/** "Hide dates, no origin, no name" plus the creation day, built from the row's options. */
@Composable
private fun optionsSummary(row: ShareRow): String {
    val options: ShareOptions = row.options
    val parts = mutableListOf<String>()
    if (row.shareKind == ShareKind.PLAN) {
        parts += stringResource(datesRes(options.datesMode))
        if (options.showOrigin) parts += stringResource(R.string.myshares_opt_origin)
    }
    if (options.showName) parts += stringResource(R.string.myshares_opt_name)
    createdDay(row.createdAt)?.let { parts += stringResource(R.string.myshares_created, it) }
    return parts.joinToString(" · ")
}

private fun datesRes(mode: ShareDates): Int =
    when (mode) {
        ShareDates.HIDDEN -> R.string.share_create_dates_hidden
        ShareDates.MONTH -> R.string.share_create_dates_month
        ShareDates.EXACT -> R.string.share_create_dates_exact
    }

private fun kindLabel(kind: ShareKind): Int =
    when (kind) {
        ShareKind.PLAN -> R.string.myshares_kind_plan
        ShareKind.RECAP -> R.string.myshares_kind_recap
        ShareKind.SAVINGS -> R.string.myshares_kind_savings
        ShareKind.QUIZ -> R.string.myshares_kind_quiz
        ShareKind.STATUS -> R.string.myshares_kind_status
        ShareKind.UNKNOWN -> R.string.myshares_kind_other
    }

@Composable
private fun errorText(error: AppError?): String =
    when {
        error is AppError.Network -> stringResource(R.string.myshares_offline)
        error?.shareErrorCode() == ShareErrorCode.SHARING_DISABLED -> stringResource(R.string.share_create_error_unavailable)
        error?.message?.isNotBlank() == true -> error.message.orEmpty()
        else -> stringResource(R.string.myshares_error_load)
    }

@Composable
private fun messageText(message: MySharesMessage): String =
    when (message) {
        MySharesMessage.Copied -> {
            stringResource(R.string.share_create_status_copy)
        }

        MySharesMessage.Revoked -> {
            stringResource(R.string.share_create_status_revoked)
        }

        MySharesMessage.Offline -> {
            stringResource(R.string.myshares_offline)
        }

        MySharesMessage.CardUnavailable -> {
            stringResource(R.string.share_create_card_unavailable)
        }

        MySharesMessage.ShareFailed -> {
            stringResource(R.string.share_create_error_share)
        }

        is MySharesMessage.Failed -> {
            message.error.message?.takeIf { it.isNotBlank() && message.error.shareErrorCode() == ShareErrorCode.UNKNOWN }
                ?: stringResource(R.string.share_create_error_revoke)
        }
    }
