package io.github.rupakc.travelplanner.feature.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.rupakc.travelplanner.core.designsystem.component.ErrorState
import io.github.rupakc.travelplanner.core.designsystem.component.SkeletonList
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.component.TpCard
import io.github.rupakc.travelplanner.core.designsystem.component.TpDropdownMenuItem
import io.github.rupakc.travelplanner.core.designsystem.component.TpOutlinedTextField
import io.github.rupakc.travelplanner.core.designsystem.component.TpSwitch
import io.github.rupakc.travelplanner.core.designsystem.component.TpTextButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpTopBar
import io.github.rupakc.travelplanner.core.designsystem.component.tpClickable
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.model.auth.AccountSettings
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Providers that can be linked from here (R2: no Apple on Android). Ids are the contract's. */
internal val LinkableProviders = listOf("google" to "Google", "github" to "GitHub", "facebook" to "Facebook")

@Composable
internal fun AccountRoute(
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenSessions: () -> Unit,
    onSignedOut: () -> Unit,
    onLinkProvider: ((String) -> Unit)?,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AccountEffect.ShareExport -> shareExport(context, effect.file)
                is AccountEffect.OpenLink -> onLinkProvider?.invoke(effect.url)
                AccountEffect.SignedOut -> onSignedOut()
            }
        }
    }
    AccountScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onChangePassword = onChangePassword,
        onOpenSessions = onOpenSessions,
        canLink = onLinkProvider != null,
    )
}

/** Hands the cache file to the share sheet through this module's FileProvider (read grant only). */
private fun shareExport(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.account.exports", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("application/json")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching {
        context.startActivity(Intent.createChooser(send, context.getString(R.string.account_export_chooser)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
internal fun AccountScreen(
    state: AccountUiState,
    onEvent: (AccountEvent) -> Unit,
    onBack: () -> Unit,
    onChangePassword: () -> Unit,
    onOpenSessions: () -> Unit,
    canLink: Boolean,
) {
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.text()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onEvent(AccountEvent.MessageShown)
        }
    }
    Scaffold(
        topBar = { TpTopBar(title = stringResource(R.string.account_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        when (state.phase) {
            AccountPhase.Loading -> {
                SkeletonList(Modifier.padding(padding).padding(TravelTheme.spacing.gutter), lines = 5)
            }

            AccountPhase.Error -> {
                ErrorState(
                    message = state.error?.text(),
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    onRetry = { onEvent(AccountEvent.Refresh) },
                )
            }

            AccountPhase.Loaded -> {
                AccountContent(state, onEvent, onChangePassword, onOpenSessions, canLink, Modifier.padding(padding))
            }
        }
    }
    state.nameDraft?.let { NameDialog(it, state.nameError, onEvent) }
    state.unlinking?.let { UnlinkDialog(it, onEvent) }
    state.delete?.let { DeleteDialogView(it, state.user?.hasPassword ?: true, onEvent) }
}

@Composable
private fun AccountContent(
    state: AccountUiState,
    onEvent: (AccountEvent) -> Unit,
    onChangePassword: () -> Unit,
    onOpenSessions: () -> Unit,
    canLink: Boolean,
    modifier: Modifier,
) {
    val user = state.user ?: return
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(TravelTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md),
    ) {
        item(key = "profile") { ProfileCard(state, onEvent) }
        if (user.email != null || !user.emailVerified) item(key = "email") { EmailCard(state, onEvent) }
        item(key = "signin") { SignInCard(state, onEvent, onChangePassword, canLink) }
        item(key = "quota") { QuotaCard(state) }
        item(key = "settings") { SettingsCard(state.settings, onEvent) }
        item(key = "devices") {
            TpCard(onClick = onOpenSessions, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_sessions_title), style = TravelTheme.typography.titleSmall)
                Text(stringResource(R.string.account_sessions_sub), style = TravelTheme.typography.bodySmall)
            }
        }
        item(key = "data") { DataCard(state, onEvent) }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = TravelTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ProfileCard(state: AccountUiState, onEvent: (AccountEvent) -> Unit) {
    val user = state.user ?: return
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) { AvatarSource.compress(context, uri) }
                    onEvent(AccountEvent.AvatarPicked(bytes))
                }
            }
        }
    TpCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.md)) {
            Avatar(user.avatarUrl, AccountRules.initials(user.displayName, user.username))
            Column(Modifier.weight(1f)) {
                Text(user.displayName ?: user.username, style = TravelTheme.typography.titleMedium)
                Text("@${user.username}", style = TravelTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm), modifier = Modifier.padding(top = TravelTheme.spacing.sm)) {
            TpTextButton(onClick = {
                picker.launch("image/*")
            }, enabled = !state.avatarBusy, tid = "account.account_avatar_change") { Text(stringResource(R.string.account_avatar_change)) }
            if (user.avatarUrl != null) {
                TpTextButton(onClick = {
                    onEvent(AccountEvent.RemoveAvatar)
                }, enabled = !state.avatarBusy, tid = "account.account_avatar_remove") { Text(stringResource(R.string.account_avatar_remove)) }
            }
            TpTextButton(onClick = { onEvent(AccountEvent.EditName) }, tid = "account.account_name_edit") { Text(stringResource(R.string.account_name_edit)) }
        }
    }
}

@Composable
private fun Avatar(url: String?, initials: String) {
    val description = stringResource(R.string.account_avatar_desc)
    Box(
        Modifier.size(64.dp).clip(CircleShape).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Surface(color = TravelTheme.colors.primaryContainer, modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center) { Text(initials, style = TravelTheme.typography.titleMedium) }
        }
        if (url != null) AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun EmailCard(state: AccountUiState, onEvent: (AccountEvent) -> Unit) {
    val user = state.user ?: return
    TpCard(modifier = Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.account_email))
        Text(user.email ?: stringResource(R.string.account_email_none), style = TravelTheme.typography.bodyMedium)
        if (user.emailVerified) {
            Text(stringResource(R.string.account_email_verified), style = TravelTheme.typography.bodySmall)
        } else {
            Text(stringResource(R.string.account_email_unverified), style = TravelTheme.typography.bodySmall)
            TpButton(
                text = stringResource(R.string.account_email_verify),
                onClick = { onEvent(AccountEvent.VerifyEmail) },
                loading = state.verifyBusy,
                variant = TpButtonVariant.Secondary,
                modifier = Modifier.padding(top = TravelTheme.spacing.sm),
                tid = "account.verify_email",
            )
        }
    }
}

@Composable
private fun SignInCard(
    state: AccountUiState,
    onEvent: (AccountEvent) -> Unit,
    onChangePassword: () -> Unit,
    canLink: Boolean,
) {
    val linked = state.identities.mapNotNull { it.provider }.toSet()
    TpCard(modifier = Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.account_signin_methods))
        TpTextButton(onClick = onChangePassword, tid = "account.account_password_set") {
            Text(stringResource(if (state.user?.hasPassword == false) R.string.account_password_set else R.string.account_password_change))
        }
        LinkableProviders.forEach { (id, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = TravelTheme.typography.bodyMedium)
                    Text(
                        stringResource(if (id in linked) R.string.account_provider_linked else R.string.account_provider_not_linked),
                        style = TravelTheme.typography.bodySmall,
                    )
                }
                if (id in linked) {
                    TpTextButton(onClick = {
                        onEvent(AccountEvent.AskUnlink(id))
                    }, tid = "account.account_unlink") { Text(stringResource(R.string.account_unlink)) }
                } else if (canLink) {
                    TpButton(
                        stringResource(R.string.account_link),
                        onClick = { onEvent(AccountEvent.LinkRequested(id)) },
                        variant = TpButtonVariant.Text,
                        tid = "account.link_$id",
                    )
                } else {
                    Text(stringResource(R.string.account_coming_soon), style = TravelTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun QuotaCard(state: AccountUiState) {
    val quota = state.quota ?: return
    if (quota.buckets.isEmpty()) return
    TpCard(modifier = Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.account_quota_title))
        Text(stringResource(R.string.account_quota_sub), style = TravelTheme.typography.bodySmall)
        quota.buckets.entries.sortedBy { it.key }.forEach { (bucket, value) ->
            Row(Modifier.fillMaxWidth().padding(top = TravelTheme.spacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(bucketLabel(bucket), style = TravelTheme.typography.bodyMedium)
                Text(
                    value.limit?.let { stringResource(R.string.account_quota_used, value.used, it) } ?: stringResource(R.string.account_quota_unlimited),
                    style = TravelTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun bucketLabel(bucket: String): String =
    when (bucket) {
        "search" -> stringResource(R.string.account_bucket_search)
        "chat" -> stringResource(R.string.account_bucket_chat)
        "compare" -> stringResource(R.string.account_bucket_compare)
        "discover" -> stringResource(R.string.account_bucket_discover)
        "light_ai" -> stringResource(R.string.account_bucket_light_ai)
        "import" -> stringResource(R.string.account_bucket_import)
        else -> bucket.replace('_', ' ')
    }

private val LanguageNames = mapOf("en" to "English", "es" to "Español", "de" to "Deutsch", "fr" to "Français", "hi" to "हिन्दी", "zh" to "中文", "bn" to "বাংলা")
private val GoalPresets = listOf(5, 10, 20, 30, 50, 100, 150, 200, 250)

@Composable
private fun SettingsCard(settings: AccountSettings, onEvent: (AccountEvent) -> Unit) {
    val change = { f: (AccountSettings) -> AccountSettings -> onEvent(AccountEvent.Change(f)) }
    TpCard(modifier = Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.account_settings_title))
        val system = stringResource(R.string.account_value_system)
        ChoiceRow(
            stringResource(R.string.account_setting_language),
            settings.language?.let { LanguageNames[it] ?: it } ?: system,
            listOf<Pair<String?, String>>(null to system) + AccountRules.Languages.map { it to (LanguageNames[it] ?: it) },
        ) { v -> change { it.copy(language = v) } }
        ChoiceRow(
            stringResource(R.string.account_setting_theme),
            themeLabel(settings.theme),
            AccountRules.Themes.map { it to themeLabel(it) },
        ) { v -> change { it.copy(theme = v ?: "system") } }
        ChoiceRow(stringResource(R.string.account_setting_currency), settings.currency, AccountRules.Currencies.map { it to it }) { v ->
            change {
                it.copy(
                    currency =
                        v ?: "USD",
                )
            }
        }
        ChoiceRow(
            stringResource(R.string.account_setting_units),
            unitsLabel(settings.units),
            AccountRules.Units.map { it to unitsLabel(it) },
        ) { v -> change { it.copy(units = v ?: "metric") } }
        ChoiceRow(
            stringResource(R.string.account_setting_visibility),
            visibilityLabel(settings.profileVisibility),
            AccountRules.Visibilities.map { it to visibilityLabel(it) },
        ) { v -> change { it.copy(profileVisibility = v ?: "public") } }
        val notSet = stringResource(R.string.account_value_not_set)
        ChoiceRow(
            stringResource(R.string.account_setting_goal),
            settings.bucketListGoal?.toString() ?: notSet,
            listOf<Pair<String?, String>>(null to notSet) + GoalPresets.map { it.toString() to it.toString() },
        ) { v -> change { it.copy(bucketListGoal = v?.toIntOrNull()) } }
        SwitchRow(stringResource(R.string.account_setting_hide_game), settings.hideGame) { v -> change { it.copy(hideGame = v) } }
        SwitchRow(stringResource(R.string.account_setting_hide_community), settings.hideCommunity) { v -> change { it.copy(hideCommunity = v) } }
        SwitchRow(stringResource(R.string.account_setting_telemetry), !settings.telemetryOptOut) { v -> change { it.copy(telemetryOptOut = !v) } }
    }
}

@Composable
private fun themeLabel(value: String): String =
    stringResource(
        when (value) {
            "light" -> R.string.account_theme_light
            "dark" -> R.string.account_theme_dark
            else -> R.string.account_value_system
        },
    )

@Composable
private fun unitsLabel(value: String): String = stringResource(if (value == "imperial") R.string.account_units_imperial else R.string.account_units_metric)

@Composable
private fun visibilityLabel(value: String): String =
    stringResource(
        when (value) {
            "private" -> R.string.account_visibility_private
            "followers" -> R.string.account_visibility_followers
            else -> R.string.account_visibility_public
        },
    )

@Composable
private fun ChoiceRow(label: String, value: String, options: List<Pair<String?, String>>, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().tpClickable(tid = "account.account_screen_clickable", role = Role.DropdownList) {
                open = true
            }.padding(vertical = TravelTheme.spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = TravelTheme.typography.bodyMedium)
            Text(value, style = TravelTheme.typography.bodyMedium)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (key, text) ->
                TpDropdownMenuItem(text = { Text(text) }, onClick = {
                    open = false
                    onSelect(key)
                }, tid = "account.account_screen_dropdown_menu_item")
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = TravelTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TpSwitch(checked = checked, onCheckedChange = onChange, tid = "account.account_screen_switch")
    }
}

@Composable
private fun DataCard(state: AccountUiState, onEvent: (AccountEvent) -> Unit) {
    TpCard(modifier = Modifier.fillMaxWidth()) {
        Heading(stringResource(R.string.account_data_title))
        Text(stringResource(R.string.account_export_sub), style = TravelTheme.typography.bodySmall)
        TpButton(
            text = stringResource(R.string.account_export),
            onClick = { onEvent(AccountEvent.Export) },
            loading = state.exporting,
            variant = TpButtonVariant.Secondary,
            modifier = Modifier.padding(vertical = TravelTheme.spacing.sm),
            tid = "account.export",
        )
        TpButton(
            text = stringResource(R.string.account_delete),
            onClick = { onEvent(AccountEvent.OpenDelete) },
            variant = TpButtonVariant.Destructive,
            tid = "account.delete",
        )
    }
}

@Composable
private fun NameDialog(draft: String, error: AccountMessage?, onEvent: (AccountEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(AccountEvent.DismissName) },
        title = { Text(stringResource(R.string.account_name_title)) },
        text = {
            TpOutlinedTextField(
                value = draft,
                onValueChange = { onEvent(AccountEvent.NameChanged(it)) },
                singleLine = true,
                isError = error != null,
                supportingText = { error?.let { Text(it.text()) } },
                tid = "account.account_screen_outlined_text_field",
            )
        },
        confirmButton = {
            TpTextButton(onClick = { onEvent(AccountEvent.SaveName) }, tid = "account.account_save") { Text(stringResource(R.string.account_save)) }
        },
        dismissButton = {
            TpTextButton(onClick = { onEvent(AccountEvent.DismissName) }, tid = "account.account_cancel") { Text(stringResource(R.string.account_cancel)) }
        },
    )
}

@Composable
private fun UnlinkDialog(provider: String, onEvent: (AccountEvent) -> Unit) {
    val label = LinkableProviders.firstOrNull { it.first == provider }?.second ?: provider
    AlertDialog(
        onDismissRequest = { onEvent(AccountEvent.DismissUnlink) },
        title = { Text(stringResource(R.string.account_unlink_title, label)) },
        text = { Text(stringResource(R.string.account_unlink_body)) },
        confirmButton = {
            TpTextButton(onClick = { onEvent(AccountEvent.ConfirmUnlink) }, tid = "account.account_unlink_2") { Text(stringResource(R.string.account_unlink)) }
        },
        dismissButton = {
            TpTextButton(onClick = { onEvent(AccountEvent.DismissUnlink) }, tid = "account.account_cancel_2") { Text(stringResource(R.string.account_cancel)) }
        },
    )
}

@Composable
private fun DeleteDialogView(dialog: DeleteDialog, hasPassword: Boolean, onEvent: (AccountEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!dialog.busy) onEvent(AccountEvent.DismissDelete) },
        title = { Text(stringResource(R.string.account_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm)) {
                Text(stringResource(R.string.account_delete_body))
                TpOutlinedTextField(
                    value = dialog.input,
                    onValueChange = { onEvent(AccountEvent.DeleteInput(it)) },
                    label = { Text(stringResource(if (hasPassword) R.string.account_delete_password else R.string.account_delete_username)) },
                    singleLine = true,
                    visualTransformation = if (hasPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = if (hasPassword) KeyboardType.Password else KeyboardType.Text,
                        ),
                    isError = dialog.error != null,
                    supportingText = { dialog.error?.let { Text(it.text()) } },
                    tid = "account.account_delete_password",
                )
            }
        },
        confirmButton = {
            TpTextButton(onClick = {
                onEvent(AccountEvent.ConfirmDelete)
            }, enabled = !dialog.busy, tid = "account.account_delete_confirm") { Text(stringResource(R.string.account_delete_confirm)) }
        },
        dismissButton = {
            TpTextButton(onClick = {
                onEvent(AccountEvent.DismissDelete)
            }, enabled = !dialog.busy, tid = "account.account_cancel_3") { Text(stringResource(R.string.account_cancel)) }
        },
    )
}
