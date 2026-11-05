package io.github.rupakc.travelplanner.feature.settings.root

import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rupakc.travelplanner.core.designsystem.component.TpButton
import io.github.rupakc.travelplanner.core.designsystem.component.TpButtonVariant
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelShape
import io.github.rupakc.travelplanner.core.designsystem.theme.TravelTheme
import io.github.rupakc.travelplanner.core.domain.globalsearch.SettingsEntry
import io.github.rupakc.travelplanner.core.domain.traveller.TravellerType
import io.github.rupakc.travelplanner.core.model.auth.User
import io.github.rupakc.travelplanner.core.model.navigation.AppRoute
import io.github.rupakc.travelplanner.core.model.navigation.AppearanceRoute
import io.github.rupakc.travelplanner.core.model.navigation.FeaturesRoute
import io.github.rupakc.travelplanner.core.model.navigation.NotificationSettingsRoute
import io.github.rupakc.travelplanner.core.model.navigation.PreferencesRoute
import io.github.rupakc.travelplanner.core.model.navigation.ServerRoute
import io.github.rupakc.travelplanner.core.model.navigation.TasteProfileRoute
import io.github.rupakc.travelplanner.core.model.settings.ThemeMode
import io.github.rupakc.travelplanner.core.ui.profile.titleRes
import io.github.rupakc.travelplanner.core.ui.rememberLinkOpener
import io.github.rupakc.travelplanner.feature.settings.R
import io.github.rupakc.travelplanner.feature.settings.common.KeyedSettingsList
import io.github.rupakc.travelplanner.feature.settings.common.SettingsHeading
import io.github.rupakc.travelplanner.feature.settings.common.SettingsNote
import io.github.rupakc.travelplanner.feature.settings.common.SettingsRow
import io.github.rupakc.travelplanner.feature.settings.common.SettingsScaffold
import io.github.rupakc.travelplanner.feature.settings.common.highlighted

/** Root navigation actions (everything leaving this screen). */
sealed interface SettingsNav {
    data class Open(val route: AppRoute) : SettingsNav

    data object RetakeQuiz : SettingsNav

    data class OpenLink(val url: String) : SettingsNav

    data object SystemLanguageSettings : SettingsNav

    data object ChangePassword : SettingsNav
}

private object RootDimens {
    val Avatar = 48.dp
}

private const val LICENCE_URL = "https://github.com/rupakc/travel-planner-android/blob/main/LICENSE"
private const val GEONAMES_URL = "https://www.geonames.org/"
private const val CC_BY_URL = "https://creativecommons.org/licenses/by/4.0/"
private const val OURAIRPORTS_URL = "https://ourairports.com/"

/** Row keys in display order (also the global-search scroll targets). */
private val RootKeys =
    listOf(
        "account",
        "h_preferences",
        SettingsEntry.TRAVEL_PREFERENCES.key,
        SettingsEntry.TASTE_GRAPH.key,
        "h_type",
        SettingsEntry.TRAVELLER_TYPE.key,
        "h_app",
        SettingsEntry.FEATURES.key,
        SettingsEntry.THEME.key,
        SettingsEntry.LANGUAGE.key,
        SettingsEntry.NOTIFICATIONS.key,
        SettingsEntry.SERVER.key,
        "h_account",
        SettingsEntry.CHANGE_PASSWORD.key,
        SettingsEntry.SIGN_OUT.key,
        "h_about",
        SettingsEntry.ABOUT.key,
    )

@Composable
internal fun SettingsRoute(
    highlightKey: String?,
    onBack: () -> Unit,
    onRetakeQuiz: () -> Unit,
    onChangePassword: () -> Unit,
    onNavigate: (AppRoute) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    LaunchedEffect(signedOut) { if (signedOut) onSignedOut() }
    val openLink = rememberLinkOpener()
    val context = LocalContext.current
    SettingsScreen(
        state = state,
        highlightKey = highlightKey,
        onEvent = viewModel::onEvent,
        onNav = { nav ->
            when (nav) {
                is SettingsNav.Open -> {
                    onNavigate(nav.route)
                }

                SettingsNav.RetakeQuiz -> {
                    onRetakeQuiz()
                }

                SettingsNav.ChangePassword -> {
                    onChangePassword()
                }

                is SettingsNav.OpenLink -> {
                    openLink(nav.url)
                }

                SettingsNav.SystemLanguageSettings -> {
                    runCatching { context.startActivity(Intent(Settings.ACTION_LOCALE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        },
        onBack = onBack,
    )
}

/** Settings root (stateless). */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onNav: (SettingsNav) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    highlightKey: String? = null,
) {
    SettingsScaffold(title = stringResource(R.string.settings_title), onBack = onBack, modifier = modifier) { padding ->
        KeyedSettingsList(keys = RootKeys, highlightKey = highlightKey, contentPadding = padding) {
            items(RootKeys.size, key = { RootKeys[it] }, contentType = { if (RootKeys[it].startsWith("h_")) "heading" else RootKeys[it] }) { index ->
                RootItem(RootKeys[index], state, highlightKey, onEvent, onNav)
            }
        }
    }
    if (state.choosingLanguage) LanguageDialog(state.languageTag, state.perAppLanguage, onEvent, onNav)
    if (state.confirmingSignOut) SignOutDialog(onEvent)
}

@Composable
private fun RootItem(key: String, state: SettingsUiState, highlightKey: String?, onEvent: (SettingsEvent) -> Unit, onNav: (SettingsNav) -> Unit) {
    val lit = key == highlightKey
    when (key) {
        "account" -> {
            AccountHeader(state.user)
        }

        "h_preferences" -> {
            SettingsHeading(stringResource(R.string.settings_section_preferences))
        }

        SettingsEntry.TRAVEL_PREFERENCES.key -> {
            SettingsRow(
                stringResource(R.string.settings_travel_preferences),
                subtitle = stringResource(R.string.settings_travel_preferences_sub),
                icon = Icons.Filled.Edit,
                highlighted = lit,
                onClick = { onNav(SettingsNav.Open(PreferencesRoute)) },
            )
        }

        SettingsEntry.TASTE_GRAPH.key -> {
            SettingsRow(
                stringResource(R.string.settings_taste_title),
                subtitle = stringResource(R.string.settings_taste_heading),
                icon = Icons.Filled.Favorite,
                highlighted = lit,
                onClick = { onNav(SettingsNav.Open(TasteProfileRoute)) },
            )
        }

        "h_type" -> {
            SettingsHeading(stringResource(R.string.settings_section_type))
        }

        SettingsEntry.TRAVELLER_TYPE.key -> {
            TravellerTypeCard(state.travellerTypes, lit, onNav)
        }

        "h_app" -> {
            SettingsHeading(stringResource(R.string.settings_section_app))
        }

        SettingsEntry.FEATURES.key -> {
            SettingsRow(
                stringResource(R.string.settings_features),
                subtitle = pluralStringResource(R.plurals.settings_features_on, state.featuresOn, state.featuresOn, state.featuresTotal),
                icon = Icons.Filled.Star,
                highlighted = lit,
                onClick = { onNav(SettingsNav.Open(FeaturesRoute)) },
            )
        }

        SettingsEntry.THEME.key -> {
            SettingsRow(
                stringResource(R.string.settings_appearance),
                subtitle = stringResource(state.settings.themeMode.labelRes()),
                icon = Icons.Filled.Settings,
                highlighted = lit,
                onClick = { onNav(SettingsNav.Open(AppearanceRoute)) },
            )
        }

        SettingsEntry.LANGUAGE.key -> {
            SettingsRow(
                stringResource(R.string.settings_language),
                subtitle = stringResource(languageLabelRes(state.languageTag)),
                icon = Icons.AutoMirrored.Filled.List,
                highlighted = lit,
                onClick = { onEvent(SettingsEvent.ChooseLanguage) },
            )
        }

        SettingsEntry.NOTIFICATIONS.key -> {
            SettingsRow(
                stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_sub),
                icon = Icons.Filled.Notifications,
                highlighted = lit,
                onClick = { onNav(SettingsNav.Open(NotificationSettingsRoute)) },
            )
        }

        SettingsEntry.SERVER.key -> {
            SettingsRow(
                stringResource(R.string.settings_server),
                subtitle = state.effectiveBaseUrl.ifEmpty { stringResource(R.string.settings_server_none) },
                icon = Icons.Filled.Build,
                highlighted = lit,
                onClick = { onNav(SettingsNav.Open(ServerRoute)) },
            )
        }

        "h_account" -> {
            SettingsHeading(stringResource(R.string.settings_section_account))
        }

        SettingsEntry.CHANGE_PASSWORD.key -> {
            SettingsRow(
                stringResource(R.string.settings_change_password),
                icon = Icons.Filled.Lock,
                highlighted = lit,
                onClick = { onNav(SettingsNav.ChangePassword) },
            )
        }

        SettingsEntry.SIGN_OUT.key -> {
            SignOutRow(state.signingOut, lit, onEvent)
        }

        "h_about" -> {
            SettingsHeading(stringResource(R.string.settings_section_about))
        }

        SettingsEntry.ABOUT.key -> {
            AboutBlock(state.versionName, lit, onNav)
        }
    }
}

@Composable
private fun AccountHeader(user: User?) {
    Row(
        Modifier.fillMaxWidth().padding(TravelTheme.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(RootDimens.Avatar).clip(TravelShape.Pill).background(TravelTheme.colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val initial = user?.username?.firstOrNull()?.uppercase()
            if (initial != null) {
                Text(initial, style = TravelTheme.typography.titleLarge, color = TravelTheme.colors.onPrimaryContainer)
            } else {
                androidx.compose.material3.Icon(Icons.Filled.Person, contentDescription = null, tint = TravelTheme.colors.onPrimaryContainer)
            }
        }
        Spacer(Modifier.width(TravelTheme.spacing.md))
        Column {
            Text(
                user?.username ?: stringResource(R.string.settings_signed_out),
                style = TravelTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                user?.email?.takeIf(String::isNotBlank) ?: stringResource(R.string.settings_no_email),
                style = TravelTheme.typography.bodyMedium,
                color = TravelTheme.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TravellerTypeCard(types: List<TravellerType>, highlighted: Boolean, onNav: (SettingsNav) -> Unit) {
    Column(
        Modifier.fillMaxWidth().highlighted(highlighted).padding(horizontal = TravelTheme.spacing.gutter, vertical = TravelTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(TravelTheme.spacing.sm),
    ) {
        if (types.isEmpty()) {
            Text(stringResource(R.string.settings_type_unset), style = TravelTheme.typography.bodyLarge)
        } else {
            Text(stringResource(types.first().titleRes()), style = TravelTheme.typography.headlineSmall)
            if (types.size > 1) {
                Text(
                    stringResource(R.string.settings_type_also, types.drop(1).map { stringResource(it.titleRes()) }.joinToString(", ")),
                    style = TravelTheme.typography.bodyMedium,
                    color = TravelTheme.colors.onSurfaceVariant,
                )
            }
        }
        Text(stringResource(R.string.settings_type_note), style = TravelTheme.typography.bodySmall, color = TravelTheme.colors.onSurfaceVariant)
        TpButton(
            stringResource(R.string.settings_retake_quiz),
            onClick = { onNav(SettingsNav.RetakeQuiz) },
            variant = TpButtonVariant.Tonal,
            leadingIcon = Icons.Filled.Refresh,
        )
    }
}

@Composable
private fun SignOutRow(signingOut: Boolean, highlighted: Boolean, onEvent: (SettingsEvent) -> Unit) {
    if (signingOut) {
        Row(Modifier.fillMaxWidth().padding(TravelTheme.spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(TravelTheme.spacing.xl))
            Spacer(Modifier.width(TravelTheme.spacing.md))
            Text(stringResource(R.string.settings_signing_out), style = TravelTheme.typography.bodyLarge)
        }
    } else {
        SettingsRow(
            stringResource(R.string.settings_sign_out),
            icon = Icons.AutoMirrored.Filled.ExitToApp,
            destructive = true,
            highlighted = highlighted,
            onClick = { onEvent(SettingsEvent.AskSignOut) },
        )
    }
}

@Composable
private fun AboutBlock(versionName: String, highlighted: Boolean, onNav: (SettingsNav) -> Unit) {
    Column(Modifier.fillMaxWidth().highlighted(highlighted)) {
        SettingsRow(stringResource(R.string.settings_version, versionName), icon = Icons.Filled.Info)
        SettingsRow(stringResource(R.string.settings_licences), onClick = { onNav(SettingsNav.OpenLink(LICENCE_URL)) })
        SettingsNote(stringResource(R.string.settings_geonames_attribution))
        FlowRow(Modifier.padding(horizontal = TravelTheme.spacing.sm)) {
            TpButton(stringResource(R.string.settings_link_geonames), onClick = { onNav(SettingsNav.OpenLink(GEONAMES_URL)) }, variant = TpButtonVariant.Text)
            TpButton(stringResource(R.string.settings_link_cc_by), onClick = { onNav(SettingsNav.OpenLink(CC_BY_URL)) }, variant = TpButtonVariant.Text)
            TpButton(stringResource(R.string.settings_link_ourairports), onClick = {
                onNav(SettingsNav.OpenLink(OURAIRPORTS_URL))
            }, variant = TpButtonVariant.Text)
        }
        SettingsNote(stringResource(R.string.settings_ourairports_attribution))
    }
}

internal fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    }

internal fun languageLabelRes(tag: String?): Int =
    when (tag) {
        "en" -> R.string.settings_language_en
        "es" -> R.string.settings_language_es
        else -> R.string.settings_language_system
    }

@Composable
private fun LanguageDialog(current: String?, perApp: Boolean, onEvent: (SettingsEvent) -> Unit, onNav: (SettingsNav) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.DismissDialog) },
        title = { Text(stringResource(R.string.settings_language)) },
        text = {
            Column(Modifier.selectableGroup()) {
                if (perApp) {
                    SupportedLanguageTags.forEach { tag ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = TravelTheme.spacing.minTouchTarget)
                                .selectable(selected = tag == current, role = Role.RadioButton, onClick = { onEvent(SettingsEvent.SetLanguage(tag)) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = tag == current, onClick = null)
                            Spacer(Modifier.width(TravelTheme.spacing.md))
                            Text(stringResource(languageLabelRes(tag)))
                        }
                    }
                } else {
                    Text(stringResource(R.string.settings_language_system_only))
                }
            }
        },
        confirmButton = {
            if (!perApp) {
                TextButton(onClick = {
                    onEvent(SettingsEvent.DismissDialog)
                    onNav(SettingsNav.SystemLanguageSettings)
                }) { Text(stringResource(R.string.settings_language_open_system)) }
            }
        },
        dismissButton = { TextButton(onClick = { onEvent(SettingsEvent.DismissDialog) }) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

@Composable
private fun SignOutDialog(onEvent: (SettingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.DismissDialog) },
        title = { Text(stringResource(R.string.settings_sign_out_confirm_title)) },
        text = { Text(stringResource(R.string.settings_sign_out_confirm_message)) },
        confirmButton = { TextButton(onClick = { onEvent(SettingsEvent.ConfirmSignOut) }) { Text(stringResource(R.string.settings_sign_out)) } },
        dismissButton = { TextButton(onClick = { onEvent(SettingsEvent.DismissDialog) }) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

private val previewState =
    SettingsUiState(
        loading = false,
        user = User(username = "alice", email = "alice@example.com"),
        travellerTypes = listOf(TravellerType.CULTURE_FOODIE, TravellerType.ACCESSIBLE),
        featuresOn = 9,
        effectiveBaseUrl = "http://10.0.2.2:8001",
        versionName = "1.0.0",
    )

@Preview(name = "Settings light", widthDp = 380, heightDp = 1400)
@Composable
private fun SettingsPreviewLight() {
    TravelTheme(themeMode = ThemeMode.LIGHT) { SettingsScreen(previewState, onEvent = {}, onNav = {}, onBack = {}, highlightKey = "theme") }
}

@Preview(name = "Settings dark", widthDp = 380, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsPreviewDark() {
    TravelTheme(themeMode = ThemeMode.DARK) { SettingsScreen(previewState.copy(travellerTypes = emptyList()), onEvent = {}, onNav = {}, onBack = {}) }
}
